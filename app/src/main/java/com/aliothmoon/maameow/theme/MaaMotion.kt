package com.aliothmoon.maameow.theme

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * App 动效语言：短、先快后稳、展开用纵向、换页用共享轴
 * 系统关闭动画时全部瞬间到位
 */
object MaaMotion {

    const val Fast = 160
    const val Medium = 220
    const val Page = 300

    val Emphasized: Easing = CubicBezierEasing(0.32f, 0.72f, 0.0f, 1.0f)
    val Linear: Easing = LinearEasing

    fun reduceMotion(context: android.content.Context): Boolean {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        return scale < 0.01f
    }

    fun duration(reduceMotion: Boolean, millis: Int): Int =
        if (reduceMotion) 0 else millis

    fun <T> spec(
        reduceMotion: Boolean,
        millis: Int = Medium,
        easing: Easing = Emphasized,
    ): FiniteAnimationSpec<T> {
        val d = duration(reduceMotion, millis)
        return if (d <= 0) snap() else tween(d, easing = easing)
    }

    fun pagerDuration(pageDistance: Int, reduceMotion: Boolean): Int {
        val distance = pageDistance.coerceAtLeast(1)
        return duration(reduceMotion, Medium + Fast * (distance - 1))
    }

    fun expandIn(reduceMotion: Boolean): EnterTransition {
        if (reduceMotion) return EnterTransition.None
        return fadeIn(spec(false, Fast, Linear)) +
                expandVertically(
                    animationSpec = spec(false, Medium),
                    expandFrom = Alignment.Top,
                )
    }

    fun expandOut(reduceMotion: Boolean): ExitTransition {
        if (reduceMotion) return ExitTransition.None
        return fadeOut(spec(false, Fast, Linear)) +
                shrinkVertically(
                    animationSpec = spec(false, Medium),
                    shrinkTowards = Alignment.Top,
                )
    }

    fun fadeIn(reduceMotion: Boolean): EnterTransition {
        if (reduceMotion) return EnterTransition.None
        return fadeIn(spec(false, Fast, Linear))
    }

    fun fadeOut(reduceMotion: Boolean): ExitTransition {
        if (reduceMotion) return ExitTransition.None
        return fadeOut(spec(false, Fast, Linear))
    }

    fun dialogIn(reduceMotion: Boolean): EnterTransition {
        if (reduceMotion) return EnterTransition.None
        return fadeIn(spec(false, Fast, Linear)) +
                scaleIn(initialScale = 0.94f, animationSpec = spec(false, Medium))
    }

    fun dialogOut(reduceMotion: Boolean): ExitTransition {
        if (reduceMotion) return ExitTransition.None
        return fadeOut(spec(false, Fast, Linear)) +
                scaleOut(targetScale = 0.94f, animationSpec = spec(false, Fast))
    }

    fun pageEnter(forward: Boolean, reduceMotion: Boolean): EnterTransition {
        if (reduceMotion) return EnterTransition.None
        val from = if (forward) { w: Int -> w } else { w: Int -> -w / 2 }
        return slideInHorizontally(spec(false, Page), from) +
                fadeIn(spec(false, Page, Linear))
    }

    fun pageExit(forward: Boolean, reduceMotion: Boolean): ExitTransition {
        if (reduceMotion) return ExitTransition.None
        val to = if (forward) { w: Int -> -w / 2 } else { w: Int -> w }
        return slideOutHorizontally(spec(false, Page), to) +
                fadeOut(spec(false, Page, Linear))
    }
}

val LocalReduceMotion = staticCompositionLocalOf { false }

/** 分页之间留缝，滑动时两页不连成一片 */
val PagerPageSpacing = 12.dp

/**
 * 分页吸附：拖过 35% 即翻页，落位带一点回弹
 *
 * 默认要过半屏且无回弹，翻页显得不果断
 */
@Composable
fun rememberMaaPagerFling(state: PagerState): TargetedFlingBehavior {
    val reduceMotion = LocalReduceMotion.current
    return PagerDefaults.flingBehavior(
        state = state,
        snapPositionalThreshold = 0.35f,
        snapAnimationSpec = if (reduceMotion) {
            snap()
        } else {
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
        },
    )
}

/**
 * 外层分页嵌着同向内层分页时用
 *
 * 默认实现把甩动速度留给内层吃掉，外层只能按位置回弹，容易停在半截
 * 外层已被拖离整页时，改由外层接住速度翻页
 */
@Composable
fun rememberOuterPagerNestedScroll(
    state: PagerState,
    fling: TargetedFlingBehavior,
): NestedScrollConnection {
    val default = PagerDefaults.pageNestedScrollConnection(state, Orientation.Horizontal)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(default, state, fling, rtl) {
        object : NestedScrollConnection by default {
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.x == 0f || abs(state.currentPageOffsetFraction) < 1e-3f) {
                    return Velocity.Zero
                }
                // 手指右移是往前翻，与分页滚动方向相反
                val velocity = if (rtl) available.x else -available.x
                state.scroll { with(fling) { performFling(velocity) } }
                return available.copy(y = 0f)
            }
        }
    }
}

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { MaaMotion.reduceMotion(context) }
}

@Composable
fun ProvideMaaMotion(content: @Composable () -> Unit) {
    val reduce = rememberReduceMotion()
    CompositionLocalProvider(LocalReduceMotion provides reduce, content = content)
}

@Composable
fun MaaAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition? = null,
    exit: ExitTransition? = null,
    label: String = "MaaAnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val reduce = LocalReduceMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter ?: MaaMotion.expandIn(reduce),
        exit = exit ?: MaaMotion.expandOut(reduce),
        label = label,
        content = content,
    )
}
