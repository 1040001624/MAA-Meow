package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettings
import com.aliothmoon.maameow.domain.models.NotificationImage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ImageProviderTest {

    private val httpClient = mockk<HttpClientHelper>()
    private val image = NotificationImage(byteArrayOf(1, 2, 3))

    private val webhook = NotificationSettings(discordWebhookUrl = "http://hook")
    private val telegram = NotificationSettings(telegramBotToken = "tok", telegramChatId = "42")

    private fun MultipartBody.partNames(): List<String> = parts.map { part ->
        part.headers!!["Content-Disposition"]!!.substringAfter("name=\"").substringBefore('"')
    }

    private fun MultipartBody.partText(name: String): String {
        val index = partNames().indexOf(name)
        return Buffer().also { part(index).body.writeTo(it) }.readUtf8()
    }

    @Test
    fun discordWebhookSendsImageAsFilesPart() = runBlocking {
        val body = slot<MultipartBody>()
        coEvery { httpClient.postMultipart(any(), capture(body), any()) } returns buildResponse(204)

        val result = DiscordWebhookProvider(httpClient, settingsManagerOf(webhook)).send("t", "c", image)

        assertEquals(NotificationSendResult.Success, result)
        assertEquals(listOf("payload_json", "files[0]"), body.captured.partNames())
        assertTrue(body.captured.partText("payload_json").contains("\"content\":\"c\""))
    }

    @Test
    fun discordWebhookFallsBackToTextWhenImageRejected() = runBlocking {
        coEvery { httpClient.postMultipart(any(), any(), any()) } returns buildResponse(413, """{"message":"too large"}""")
        coEvery { httpClient.post(any(), any(), any(), any()) } returns buildResponse(204)

        val result = DiscordWebhookProvider(httpClient, settingsManagerOf(webhook)).send("t", "c", image)

        assertEquals(NotificationSendResult.Success, result)
        coVerify(exactly = 1) { httpClient.post("http://hook", any(), any(), any()) }
    }

    @Test
    fun discordWebhookDoesNotResendOnNetworkError() = runBlocking {
        // 超时时对端可能已经收到，重发会出两条
        coEvery { httpClient.postMultipart(any(), any(), any()) } throws IOException("timeout")

        val result = DiscordWebhookProvider(httpClient, settingsManagerOf(webhook)).send("t", "c", image)

        assertTrue(result is NotificationSendResult.Transient)
        coVerify(exactly = 0) { httpClient.post(any(), any(), any(), any()) }
    }

    @Test
    fun discordBotSendsImageToDmChannel() = runBlocking {
        val body = slot<MultipartBody>()
        coEvery { httpClient.post(any(), any(), any(), any()) } returns buildResponse(body = """{"id":"dm1"}""")
        coEvery { httpClient.postMultipart(any(), capture(body), any()) } returns buildResponse()

        val result = DiscordProvider(
            httpClient,
            settingsManagerOf(NotificationSettings(discordBotToken = "tok", discordUserId = "u1")),
        ).send("t", "c", image)

        assertEquals(NotificationSendResult.Success, result)
        coVerify { httpClient.postMultipart("https://discord.com/api/v9/channels/dm1/messages", any(), any()) }
        assertEquals(listOf("payload_json", "files[0]"), body.captured.partNames())
    }

    @Test
    fun telegramSendsTextThenPhoto() = runBlocking {
        val photo = slot<MultipartBody>()
        coEvery { httpClient.post(any(), any(), any(), any()) } returns buildResponse(body = """{"ok":true}""")
        coEvery { httpClient.postMultipart(any(), capture(photo), any()) } returns buildResponse(body = """{"ok":true}""")

        val result = TelegramProvider(httpClient, settingsManagerOf(telegram)).send("t", "c", image)

        assertEquals(NotificationSendResult.Success, result)
        coVerify { httpClient.postMultipart("https://api.telegram.org/bottok/sendPhoto", any(), any()) }
        assertEquals(listOf("chat_id", "photo"), photo.captured.partNames())
        assertEquals("42", photo.captured.partText("chat_id"))
    }

    @Test
    fun telegramPhotoFailureKeepsTextResult() = runBlocking {
        coEvery { httpClient.post(any(), any(), any(), any()) } returns buildResponse(body = """{"ok":true}""")
        coEvery { httpClient.postMultipart(any(), any(), any()) } throws IOException("boom")

        val result = TelegramProvider(httpClient, settingsManagerOf(telegram)).send("t", "c", image)

        assertEquals(NotificationSendResult.Success, result)
    }

    @Test
    fun telegramSkipsPhotoWhenTextFails() = runBlocking {
        coEvery { httpClient.post(any(), any(), any(), any()) } returns buildResponse(400, """{"ok":false}""")

        TelegramProvider(httpClient, settingsManagerOf(telegram)).send("t", "c", image)

        coVerify(exactly = 0) { httpClient.postMultipart(any(), any(), any()) }
    }

    @Test
    fun kookUploadsAssetThenSendsImageMessage() = runBlocking {
        val bodies = mutableListOf<String>()
        coEvery { httpClient.post(any(), capture(bodies), any(), any()) } returns buildResponse(body = """{"code":0}""")
        coEvery { httpClient.postMultipart(any(), any(), any()) } returns
            buildResponse(body = """{"code":0,"data":{"url":"https://img.kookapp.cn/a.jpg"}}""")

        val result = KookProvider(
            httpClient,
            settingsManagerOf(NotificationSettings(kookBotToken = "tok", kookTargetId = "ch")),
        ).send("t", "c", image)

        assertEquals(NotificationSendResult.Success, result)
        coVerify { httpClient.postMultipart("https://www.kookapp.cn/api/v3/asset/create", any(), any()) }
        assertEquals(2, bodies.size)
        assertTrue(bodies[1], bodies[1].contains("\"type\":2"))
        assertTrue(bodies[1], bodies[1].contains("https://img.kookapp.cn/a.jpg"))
    }

    @Test
    fun providerWithoutImageSupportSendsTextOnly() = runBlocking {
        coEvery { httpClient.post(any(), any(), any(), any()) } returns buildResponse(body = """{"success":true}""")

        val qmsg = QmsgV3Provider(httpClient, settingsManagerOf(NotificationSettings(qmsgV3Key = "k")))
        val result = qmsg.send("t", "c", image)

        assertFalse(qmsg.supportsImage)
        assertEquals(NotificationSendResult.Success, result)
        coVerify(exactly = 0) { httpClient.postMultipart(any(), any(), any()) }
    }
}
