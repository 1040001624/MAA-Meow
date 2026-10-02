package com.aliothmoon.maameow.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskParamSummaryTest {

    @Test
    fun `布尔与数值报原值，枚举类字符串报原值`() {
        assertEquals(
            mapOf(
                "client_type" to "Official",
                "start_game_enabled" to "true",
                "medicine" to "3",
                "stage" to "1-7",
                "series" to "-1",
            ),
            TaskParamSummary.summarize(
                """{"client_type":"Official","start_game_enabled":true,"medicine":3,"stage":"1-7","series":-1}"""
            ),
        )
    }

    @Test
    fun `其余字符串只报填没填`() {
        val summary = TaskParamSummary.summarize(
            """{"account_name":"13800000000","penguin_id":"12345678","filename":"/sdcard/a.json","name":""}"""
        )

        assertEquals(
            mapOf("account_name" to "filled", "penguin_id" to "filled", "filename" to "filled", "name" to "empty"),
            summary,
        )
        assertFalse(summary.values.any { "138" in it || "sdcard" in it })
    }

    @Test
    fun `数组只报长度，对象往下展开`() {
        assertEquals(
            mapOf(
                "facility.count" to "3",
                "drops.count" to "0",
                "report.server" to "CN",
                "report.penguin_id" to "filled",
                "report.enabled" to "true",
            ),
            TaskParamSummary.summarize(
                """{"facility":["Mfg","Trade","Dorm"],"drops":[],""" +
                    """"report":{"server":"CN","penguin_id":"1","enabled":true},"skip":null}"""
            ),
        )
    }

    @Test
    fun `嵌套太深的对象只报大小`() {
        assertEquals(
            mapOf("a.b.c.count" to "2"),
            TaskParamSummary.summarize("""{"a":{"b":{"c":{"x":1,"y":2}}}}"""),
        )
    }

    @Test
    fun `不是 JSON 对象就没有摘要`() {
        assertTrue(TaskParamSummary.summarize("").isEmpty())
        assertTrue(TaskParamSummary.summarize("[1,2]").isEmpty())
        assertTrue(TaskParamSummary.summarize("{broken").isEmpty())
    }

    @Test
    fun `条目数有上限`() {
        val params = (1..150).joinToString(",", "{", "}") { "\"k$it\":$it" }

        assertEquals(100, TaskParamSummary.summarize(params).size)
    }
}
