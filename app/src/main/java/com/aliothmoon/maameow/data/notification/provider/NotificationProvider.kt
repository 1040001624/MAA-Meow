package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.domain.models.NotificationImage

interface NotificationProvider {
    val id: String

    /** 能随文字附带图片的渠道置真，其余渠道收到图片时只发文字 */
    val supportsImage: Boolean get() = false

    suspend fun send(title: String, content: String): NotificationSendResult

    /** 图片发送失败不影响文字，结果以文字为准 */
    suspend fun send(title: String, content: String, image: NotificationImage): NotificationSendResult =
        send(title, content)
}
