package com.aliothmoon.maameow.remote.internal

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import com.aliothmoon.maameow.domain.models.UnlockGesture
import com.aliothmoon.maameow.domain.models.UnlockStep
import com.aliothmoon.maameow.third.wrappers.InputManager
import com.aliothmoon.maameow.third.wrappers.ServiceManager
import kotlin.math.roundToInt

internal sealed interface InjectAction {
    data class Sleep(val ms: Long) : InjectAction
    data class Down(val x: Int, val y: Int) : InjectAction
    data class Move(val x: Int, val y: Int) : InjectAction
    data class Up(val x: Int, val y: Int) : InjectAction
}

/** 步骤序列 → 注入时间轴；分辨率有出入时按比例缩放 */
internal object UnlockGestureReplay {

    const val TAP_HOLD_MS = 60

    fun timeline(gesture: UnlockGesture, targetWidth: Int, targetHeight: Int): List<InjectAction> {
        if (gesture.screenWidth <= 0 || gesture.screenHeight <= 0) return emptyList()
        val scaleX = targetWidth.toDouble() / gesture.screenWidth
        val scaleY = targetHeight.toDouble() / gesture.screenHeight

        fun mapX(x: Int) = (x * scaleX).roundToInt().coerceIn(0, targetWidth - 1)
        fun mapY(y: Int) = (y * scaleY).roundToInt().coerceIn(0, targetHeight - 1)

        val out = mutableListOf<InjectAction>()
        for (step in gesture.steps) {
            if (step.delayBeforeMs > 0) out += InjectAction.Sleep(step.delayBeforeMs.toLong())
            when (step) {
                is UnlockStep.Tap -> out += press(mapX(step.x), mapY(step.y), TAP_HOLD_MS.toLong())

                is UnlockStep.LongPress ->
                    out += press(mapX(step.x), mapY(step.y), step.holdMs.toLong())

                is UnlockStep.Swipe -> {
                    // 轨迹来自持久化文件，少于两点按点击兜底
                    val points = step.points
                    if (points.size < 2) {
                        points.firstOrNull()?.let {
                            out += press(mapX(it.x), mapY(it.y), TAP_HOLD_MS.toLong())
                        }
                    } else {
                        out += InjectAction.Down(mapX(points[0].x), mapY(points[0].y))
                        for (i in 1 until points.size) {
                            val dt = (points[i].tMs - points[i - 1].tMs).toLong()
                            if (dt > 0) out += InjectAction.Sleep(dt)
                            val x = mapX(points[i].x)
                            val y = mapY(points[i].y)
                            out += if (i == points.lastIndex) {
                                InjectAction.Up(x, y)
                            } else {
                                InjectAction.Move(x, y)
                            }
                        }
                    }
                }
            }
        }
        return out
    }

    /**
     * 按时间轴注入到主屏
     * 不走 InputControlUtils：那边的触点状态与 MAA 任务共用，后台模式跑任务时测解锁会互相打乱，
     * 且会推给触控预览，等于把解锁轨迹广播出去
     */
    fun execute(actions: List<InjectAction>) {
        val touch = SingleTouch()
        try {
            for (action in actions) {
                when (action) {
                    is InjectAction.Sleep -> Thread.sleep(action.ms)
                    is InjectAction.Down -> touch.down(action.x, action.y)
                    is InjectAction.Move -> touch.move(action.x, action.y)
                    is InjectAction.Up -> touch.up(action.x, action.y)
                }
            }
        } finally {
            // 中途被打断也别把手指留在屏上
            touch.cancelIfPressed()
        }
    }

    private fun press(x: Int, y: Int, holdMs: Long): List<InjectAction> = listOf(
        InjectAction.Down(x, y),
        InjectAction.Sleep(holdMs.coerceAtLeast(0)),
        InjectAction.Up(x, y),
    )
}

/** 回放专用单指注入，状态只活在一次回放里 */
private class SingleTouch {
    private val input = ServiceManager.getInputManager()
    private var downTime = 0L
    private var pressed = false
    private var lastX = 0
    private var lastY = 0

    fun down(x: Int, y: Int) {
        if (pressed) cancelIfPressed()
        downTime = SystemClock.uptimeMillis()
        // DOWN 等系统收下再继续，与 InputControlUtils 一致
        pressed = inject(MotionEvent.ACTION_DOWN, x, y, InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH)
    }

    fun move(x: Int, y: Int) {
        if (pressed) inject(MotionEvent.ACTION_MOVE, x, y, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)
    }

    fun up(x: Int, y: Int) {
        if (!pressed) return
        inject(MotionEvent.ACTION_UP, x, y, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)
        pressed = false
    }

    fun cancelIfPressed() {
        if (!pressed) return
        inject(MotionEvent.ACTION_CANCEL, lastX, lastY, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)
        pressed = false
    }

    private fun inject(action: Int, x: Int, y: Int, mode: Int): Boolean {
        lastX = x
        lastY = y
        val props = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        }
        val lifting = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x.toFloat()
            this.y = y.toFloat()
            pressure = if (lifting) 0f else 1f
            size = 1f
        }
        val event = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), action,
            1, arrayOf(props), arrayOf(coords),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        return try {
            input.injectInputEvent(event, mode)
        } finally {
            event.recycle()
        }
    }
}
