package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

/** Qmsg 3.0：https://qmsg.zendee.cn/docs */
class QmsgV3Provider(
    private val httpClient: HttpClientHelper,
    private val settingsManager: NotificationSettingsManager
) : NotificationProvider {

    override val id = "QmsgV3"

    override suspend fun send(title: String, content: String): NotificationSendResult {
        val settings = settingsManager.settings.first()
        val key = settings.qmsgV3Key.trim().takeIf { it.isNotEmpty() }
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_qmsg_key_empty)
            )
        // 不填群号时省略 group，推到 Key 绑定的 QQ 单聊
        val body = buildJsonObject {
            put("msg", content.keepTail(QMSG_MAX_MSG_LENGTH))
            settings.qmsgV3Group.trim().takeIf { it.isNotEmpty() }?.let { put("group", it) }
        }.toString()

        return runCatching {
            httpClient.post("$BASE_URL/v3/jsend/$key", body).use { it.toQmsgResult() }
        }.getOrElse {
            Timber.e(it, "QmsgV3 send failed")
            NotificationSendResult.Transient(uiTextOf(R.string.notification_err_network))
        }
    }

    private companion object {
        const val BASE_URL = "https://qmsg.zendee.cn"
    }
}
