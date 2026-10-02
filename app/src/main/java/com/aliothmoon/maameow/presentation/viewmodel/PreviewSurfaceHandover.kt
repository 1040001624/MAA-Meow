package com.aliothmoon.maameow.presentation.viewmodel

import android.os.IBinder
import android.view.Surface
import com.aliothmoon.maameow.RemoteService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

/**
 * 预览 Surface 交给了哪个特权进程
 *
 * 一块 Surface 只交给一个特权进程：SurfaceView 的缓冲队列认不出 producer 进程死了，
 * 进程换了再把同一块交过去，`eglCreateWindowSurface` 报 already connected，预览就此不动
 *
 * 只在主线程调
 */
class PreviewSurfaceHandover {

    private var surface: Surface? = null

    /** 拿到过这块 Surface 的那个进程；null = 还没交出去 */
    private var handedTo: IBinder? = null

    private val _surfaceEpoch = MutableStateFlow(0)

    /** 变了就该丢掉当前 SurfaceView 重建一个 */
    val surfaceEpoch: StateFlow<Int> = _surfaceEpoch.asStateFlow()

    fun attach(surface: Surface, service: RemoteService?) {
        this.surface = surface
        handedTo = null
        if (service != null) hand(surface, service)
    }

    /** @return 刚摘下的 Surface，由调用方释放 */
    fun detach(service: RemoteService?): Surface? {
        val detached = surface
        surface = null
        handedTo = null
        if (service != null) send(service, null)
        return detached
    }

    fun onServiceConnected(service: RemoteService) {
        val current = surface ?: return
        when (handedTo) {
            // 断线期间才到的 Surface，没人接过
            null -> hand(current, service)
            service.asBinder() -> Unit
            // 旧进程接过，重建后的新 Surface 走 attach 交给新进程
            else -> _surfaceEpoch.update { it + 1 }
        }
    }

    private fun hand(surface: Surface, service: RemoteService) {
        if (send(service, surface)) handedTo = service.asBinder()
    }

    private fun send(service: RemoteService, surface: Surface?): Boolean =
        runCatching { service.setMonitorSurface(surface) }
            .onFailure { Timber.w(it, "setMonitorSurface failed") }
            .isSuccess
}
