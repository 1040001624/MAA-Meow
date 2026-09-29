package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.utils.JsonUtils
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import okhttp3.Response
import timber.log.Timber

class QmsgProvider(
    private val httpClient: HttpClientHelper,
    private val settingsManager: NotificationSettingsManager
) : NotificationProvider {

    override val id = "Qmsg"

    override suspend fun send(title: String, content: String): NotificationSendResult {
        val settings = settingsManager.settings.first()
        val server = settings.qmsgServer.takeIf { it.isNotBlank() }?.trimEnd('/')
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_qmsg_server_empty)
            )
        val key = settings.qmsgKey.takeIf { it.isNotBlank() }
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_qmsg_key_empty)
            )
        val body = JsonUtils.common.encodeToString(
            QmsgRequest(
                msg = content.keepTail(QMSG_MAX_MSG_LENGTH),
                qq = settings.qmsgUser,
                bot = settings.qmsgBot,
            )
        )

        return runCatching {
            httpClient.post("$server/jsend/$key", body).use { it.toQmsgResult() }
        }.getOrElse {
            Timber.e(it, "Qmsg send failed")
            NotificationSendResult.Transient(uiTextOf(R.string.notification_err_network))
        }
    }

    @Serializable
    private data class QmsgRequest(
        val msg: String,
        val qq: String,
        val bot: String,
    )
}

/** 超出时接口返回「消息长度不能超过1800个字」 */
internal const val QMSG_MAX_MSG_LENGTH = 1800

/** 业务失败也是 HTTP 200，原因在 message 里 */
internal fun Response.toQmsgResult(): NotificationSendResult {
    val responseBody = body.string()
    val result = runCatching {
        JsonUtils.common.decodeFromString<QmsgResponse>(responseBody)
    }.getOrNull()
    if (isSuccessful && result?.success == true) return NotificationSendResult.Success

    Timber.w("Qmsg rejected: HTTP %d, body=%s", code, responseBody)
    val reason = result?.message?.takeIf { it.isNotBlank() }
    return NotificationSendResult.Failed(
        if (reason != null) {
            uiTextOf(R.string.notification_err_rejected, reason)
        } else {
            uiTextOf(R.string.notification_err_http_status, code)
        }
    )
}

@Serializable
private data class QmsgResponse(
    val success: Boolean = false,
    val message: String? = null,
)
