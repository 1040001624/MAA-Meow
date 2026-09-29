package com.aliothmoon.maameow.domain.models

/** 外部通知附带的图片，目前只有任务结束截图 */
class NotificationImage(val bytes: ByteArray) {
    val mimeType: String get() = "image/jpeg"
    val fileName: String get() = "screenshot.jpg"
}
