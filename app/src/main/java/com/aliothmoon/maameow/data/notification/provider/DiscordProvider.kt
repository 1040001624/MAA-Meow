package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.domain.models.NotificationImage
import com.aliothmoon.maameow.utils.JsonUtils
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber

class DiscordProvider(
    private val httpClient: HttpClientHelper,
    private val settingsManager: NotificationSettingsManager
) : NotificationProvider {

    override val id = "Discord"

    override val supportsImage = true

    override suspend fun send(title: String, content: String): NotificationSendResult =
        sendInternal(content, image = null)

    override suspend fun send(
        title: String,
        content: String,
        image: NotificationImage,
    ): NotificationSendResult = sendWithTextFallback(title, content) { sendInternal(content, image) }

    private suspend fun sendInternal(content: String, image: NotificationImage?): NotificationSendResult {
        val settings = settingsManager.settings.first()
        val botToken = settings.discordBotToken.takeIf { it.isNotBlank() }
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_discord_token_empty)
            )
        val userId = settings.discordUserId.takeIf { it.isNotBlank() }
            ?: return NotificationSendResult.Failed(
                uiTextOf(R.string.notification_err_discord_user_empty)
            )

        val channelId = when (val dm = createDmChannel(botToken, userId)) {
            is DmChannelResult.Success -> dm.channelId
            is DmChannelResult.Rejected ->
                return NotificationSendResult.Failed(
                    uiTextOf(R.string.notification_err_discord_dm_failed)
                )

            is DmChannelResult.NetworkError ->
                return NotificationSendResult.Transient(uiTextOf(R.string.notification_err_network))
        }

        val url = "https://discord.com/api/v9/channels/$channelId/messages"
        val text = content.keepTail(MAX_CONTENT_LENGTH)
        return runCatching {
            if (image == null) {
                httpClient.postForm(url, mapOf("content" to text), discordHeaders(botToken))
            } else {
                httpClient.postMultipart(url, discordMultipart(text, image), discordHeaders(botToken))
            }.use { response ->
                if (response.isSuccessful) {
                    NotificationSendResult.Success
                } else {
                    val responseBody = response.body.string()
                    Timber.w("Discord rejected: HTTP %d, body=%s", response.code, responseBody)
                    NotificationSendResult.Failed(
                        uiTextOf(R.string.notification_err_http_status, response.code),
                    )
                }
            }
        }.getOrElse {
            Timber.e(it, "Discord send failed")
            NotificationSendResult.Transient(uiTextOf(R.string.notification_err_network))
        }
    }

    private suspend fun createDmChannel(botToken: String, userId: String): DmChannelResult {
        val body = JsonUtils.common.encodeToString(
            DiscordCreateChannelRequest(recipientId = userId)
        )

        return runCatching {
            httpClient.post(
                url = "https://discord.com/api/v9/users/@me/channels",
                body = body,
                headers = discordHeaders(botToken)
            ).use { response ->
                if (!response.isSuccessful) {
                    return@use DmChannelResult.Rejected
                }

                val responseBody = response.body.string()
                val id = JsonUtils.common
                    .decodeFromString<DiscordCreateChannelResponse>(responseBody).id
                if (id != null) DmChannelResult.Success(id) else DmChannelResult.Rejected
            }
        }.getOrElse {
            Timber.e(it, "Discord create DM channel failed")
            DmChannelResult.NetworkError
        }
    }

    private fun discordHeaders(botToken: String): Map<String, String> = mapOf(
        "Authorization" to "Bot $botToken",
        "User-Agent" to "DiscordBot"
    )

    private sealed interface DmChannelResult {
        data class Success(val channelId: String) : DmChannelResult
        data object Rejected : DmChannelResult
        data object NetworkError : DmChannelResult
    }

    @Serializable
    private data class DiscordCreateChannelRequest(
        @SerialName("recipient_id") val recipientId: String,
    )

    @Serializable
    private data class DiscordCreateChannelResponse(
        val id: String? = null,
    )

    private companion object {
        /** 消息 content 上限，超出时接口返回 400 */
        const val MAX_CONTENT_LENGTH = 2000
    }
}
