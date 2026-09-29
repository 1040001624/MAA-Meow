package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.manager.RemoteServiceManager
import timber.log.Timber

/** 抓当前游戏画面，给外部通知附图 */
fun interface FrameSnapshotter {
    /** 同步跨进程调用，无帧或服务不在时返回 null */
    fun captureJpeg(): ByteArray?
}

class RemoteFrameSnapshotter : FrameSnapshotter {
    override fun captureJpeg(): ByteArray? = runCatching {
        RemoteServiceManager.getInstanceOrNull()?.captureFrameJpeg(JPEG_QUALITY)
    }.onFailure { Timber.w(it, "captureFrameJpeg failed") }
        .getOrNull()
        ?.takeIf { it.isNotEmpty() }

    private companion object {
        /** 远端先缩到长边 1280，编码后约 150~250KB */
        const val JPEG_QUALITY = 85
    }
}
