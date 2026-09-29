package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettings
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.utils.i18n.UiText
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QmsgProviderTest {

    private val httpClient = mockk<HttpClientHelper>()
    private val url = slot<String>()
    private val body = slot<String>()

    private val configured = NotificationSettings(
        qmsgServer = "https://qmsg.zendee.cn/",
        qmsgKey = "k",
        qmsgUser = "10001",
        qmsgBot = "10002",
    )

    private fun send(
        content: String = "c",
        response: String = """{"success":true,"reason":"操作成功","code":0}""",
    ): NotificationSendResult {
        coEvery {
            httpClient.post(capture(url), capture(body), any(), any())
        } returns buildQmsgResponse(response)
        val manager = mockk<NotificationSettingsManager> {
            every { this@mockk.settings } returns flowOf(configured)
        }
        return runBlocking { QmsgProvider(httpClient, manager).send("t", content) }
    }

    @Test
    fun postsToLegacyJsonEndpoint() {
        assertEquals(NotificationSendResult.Success, send())
        assertEquals("https://qmsg.zendee.cn/jsend/k", url.captured)
        val json = Json.parseToJsonElement(body.captured).jsonObject
        assertEquals("10001", json["qq"]!!.jsonPrimitive.content)
        assertEquals("10002", json["bot"]!!.jsonPrimitive.content)
    }

    @Test
    fun longContentKeepsTailWithinLimit() {
        send(content = "日志".repeat(2000) + "结尾摘要")
        val msg = Json.parseToJsonElement(body.captured).jsonObject["msg"]!!.jsonPrimitive.content
        assertTrue(msg.length <= 1800)
        assertTrue(msg.endsWith("结尾摘要"))
    }

    @Test
    fun businessFailureCarriesServerMessage() {
        // #261 实际收到的响应
        val result = send(
            response = """{"success":false,"message":"消息长度不能超过1800个字","code":500,"data":null,"info":{},"reason":"消息长度不能超过1800个字"}""",
        )
        assertTrue("expected Failed but was $result", result is NotificationSendResult.Failed)
        val text = (result as NotificationSendResult.Failed).message as UiText.Resource
        assertEquals(R.string.notification_err_rejected, text.resId)
        assertEquals(listOf("消息长度不能超过1800个字"), text.args)
    }

    @Test
    fun unparsableBodyFallsBackToHttpStatus() {
        val result = send(response = "<html>bad gateway</html>")
        val text = (result as NotificationSendResult.Failed).message as UiText.Resource
        assertEquals(R.string.notification_err_http_status, text.resId)
    }
}

internal fun buildQmsgResponse(body: String): Response =
    Response.Builder()
        .request(Request.Builder().url("http://localhost").build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("test")
        .body(body.toResponseBody(null))
        .build()
