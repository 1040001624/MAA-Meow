package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettings
import com.aliothmoon.maameow.utils.i18n.UiText
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationProviderConfigContractTest {

    private val httpClient = mockk<HttpClientHelper>()

    private val UiText.resIdValue: Int
        get() = (this as UiText.Resource).resId

    private fun assertFailed(result: NotificationSendResult, expectedResId: Int) {
        assertTrue("expected Failed but was $result", result is NotificationSendResult.Failed)
        assertEquals(expectedResId, (result as NotificationSendResult.Failed).message.resIdValue)
    }

    // --- CustomWebhook ---
    @Test fun webhookEmptyUrl() = runBlocking {
        assertFailed(
            CustomWebhookProvider(httpClient, settingsManagerOf(NotificationSettings(customWebhookBody = "{t}"))).send("t", "c"),
            R.string.notification_err_webhook_url_empty,
        )
    }

    // --- Bark ---
    @Test fun barkEmptyKey() = runBlocking {
        // barkServer 有默认值，全空配置下首个失败是 SendKey。
        assertFailed(
            BarkProvider(httpClient, settingsManagerOf()).send("t", "c"),
            R.string.notification_err_bark_key_empty,
        )
    }

    @Test fun barkEmptyServer() = runBlocking {
        assertFailed(
            BarkProvider(httpClient, settingsManagerOf(NotificationSettings(barkServer = "", barkSendKey = "k"))).send("t", "c"),
            R.string.notification_err_bark_server_empty,
        )
    }

    // --- DingTalk ---
    @Test fun dingTalkEmptyToken() = runBlocking {
        assertFailed(
            DingTalkProvider(httpClient, settingsManagerOf()).send("t", "c"),
            R.string.notification_err_dingtalk_token_empty,
        )
    }

    // --- Discord ---
    @Test fun discordEmptyToken() = runBlocking {
        assertFailed(
            DiscordProvider(httpClient, settingsManagerOf()).send("t", "c"),
            R.string.notification_err_discord_token_empty,
        )
    }

    @Test fun discordEmptyUser() = runBlocking {
        assertFailed(
            DiscordProvider(httpClient, settingsManagerOf(NotificationSettings(discordBotToken = "tok"))).send("t", "c"),
            R.string.notification_err_discord_user_empty,
        )
    }

    // --- DiscordWebhook ---
    @Test fun discordWebhookEmptyUrl() = runBlocking {
        assertFailed(
            DiscordWebhookProvider(httpClient, settingsManagerOf()).send("t", "c"),
            R.string.notification_err_discord_webhook_empty,
        )
    }

    // --- Gotify ---
    @Test fun gotifyEmptyServer() = runBlocking {
        assertFailed(
            GotifyProvider(httpClient, settingsManagerOf()).send("t", "c"),
            R.string.notification_err_gotify_server_empty,
        )
    }

    @Test fun gotifyEmptyToken() = runBlocking {
        assertFailed(
            GotifyProvider(httpClient, settingsManagerOf(NotificationSettings(gotifyServer = "http://x"))).send("t", "c"),
            R.string.notification_err_gotify_token_empty,
        )
    }

    @Test fun gotifyNonHttpScheme_invalidConfig() = runBlocking {
        assertFailed(
            GotifyProvider(
                httpClient,
                settingsManagerOf(NotificationSettings(gotifyServer = "ftp://x", gotifyToken = "t"))
            ).send("t", "c"),
            R.string.notification_err_gotify_scheme,
        )
    }

    // --- Qmsg ---
    @Test fun qmsgEmptyServer() = runBlocking {
        assertFailed(
            QmsgProvider(httpClient, settingsManagerOf()).send("t", "c"),
            R.string.notification_err_qmsg_server_empty,
        )
    }

    @Test fun qmsgEmptyKey() = runBlocking {
        assertFailed(
            QmsgProvider(httpClient, settingsManagerOf(NotificationSettings(qmsgServer = "http://x"))).send("t", "c"),
            R.string.notification_err_qmsg_key_empty,
        )
    }

    // --- ServerChan ---
    @Test fun serverChanEmptyKey() = runBlocking {
        assertFailed(
            ServerChanProvider(httpClient, settingsManagerOf()).send("t", "c"),
            R.string.notification_err_serverchan_key_empty,
        )
    }

    @Test fun serverChanSctpBadFormat_invalidConfig() = runBlocking {
        assertFailed(
            ServerChanProvider(
                httpClient,
                settingsManagerOf(NotificationSettings(serverChanSendKey = "sctpNOTNUM"))
            ).send("t", "c"),
            R.string.notification_err_serverchan_sctp_fmt,
        )
    }

    // --- Smtp ---
    @Test fun smtpEmptyServer() = runBlocking {
        assertFailed(
            SmtpProvider(settingsManagerOf()).send("t", "c"),
            R.string.notification_err_smtp_server_empty,
        )
    }

    @Test fun smtpInvalidPort() = runBlocking {
        assertFailed(
            SmtpProvider(
                settingsManagerOf(NotificationSettings(smtpServer = "smtp.x", smtpPort = "abc"))
            ).send("t", "c"),
            R.string.notification_err_smtp_port_invalid,
        )
    }

    @Test fun smtpRequireAuthMissingCredentials() = runBlocking {
        assertFailed(
            SmtpProvider(
                settingsManagerOf(
                    NotificationSettings(
                        smtpServer = "smtp.x", smtpPort = "465",
                        smtpFrom = "a@x", smtpTo = "b@x",
                        smtpRequireAuthentication = "true", smtpUser = "", smtpPassword = "",
                    )
                )
            ).send("t", "c"),
            R.string.notification_err_smtp_auth_empty,
        )
    }

    // --- Telegram ---
    @Test fun telegramEmptyToken() = runBlocking {
        assertFailed(
            TelegramProvider(httpClient, settingsManagerOf()).send("t", "c"),
            R.string.notification_err_telegram_token_empty,
        )
    }

    @Test fun telegramEmptyChat() = runBlocking {
        assertFailed(
            TelegramProvider(
                httpClient, settingsManagerOf(NotificationSettings(telegramBotToken = "tok"))
            ).send("t", "c"),
            R.string.notification_err_telegram_chat_empty,
        )
    }
}
