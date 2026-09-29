package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettings
import com.aliothmoon.maameow.utils.i18n.UiText
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QmsgV3ProviderTest {

    private val httpClient = mockk<HttpClientHelper>()
    private val url = slot<String>()
    private val body = slot<String>()

    private val configured = NotificationSettings(qmsgV3Key = "k")

    private fun send(
        settings: NotificationSettings = configured,
        content: String = "c",
        response: String = """{"success":true,"message":"操作成功","code":0,"data":42}""",
    ): NotificationSendResult {
        coEvery {
            httpClient.post(capture(url), capture(body), any(), any())
        } returns buildResponse(body = response)
        return runBlocking { QmsgV3Provider(httpClient, settingsManagerOf(settings)).send("t", content) }
    }

    @Test
    fun emptyKeyFails() {
        val result = send(settings = NotificationSettings())
        val text = (result as NotificationSendResult.Failed).message as UiText.Resource
        assertEquals(R.string.notification_err_qmsg_key_empty, text.resId)
    }

    @Test
    fun postsToV3JsonEndpoint() {
        assertEquals(NotificationSendResult.Success, send())
        assertEquals("https://qmsg.zendee.cn/v3/jsend/k", url.captured)
    }

    @Test
    fun omitsGroupWhenBlank() {
        send(settings = configured.copy(qmsgV3Group = " "))
        val json = Json.parseToJsonElement(body.captured).jsonObject
        assertEquals(setOf("msg"), json.keys)
    }

    @Test
    fun sendsGroupWhenSet() {
        send(settings = configured.copy(qmsgV3Group = "123456789"))
        val json = Json.parseToJsonElement(body.captured).jsonObject
        assertEquals("123456789", json["group"]!!.jsonPrimitive.content)
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
        val result = send(response = """{"success":false,"message":"错误描述","code":500,"data":null}""")
        val text = (result as NotificationSendResult.Failed).message as UiText.Resource
        assertEquals(R.string.notification_err_rejected, text.resId)
        assertEquals(listOf("错误描述"), text.args)
    }
}
