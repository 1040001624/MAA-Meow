package com.aliothmoon.maameow.telemetry

import io.sentry.SentryLogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticLogRecordsTest {

    private val context = DiagnosticLogContext(
        eventId = "0123456789abcdef0123456789abcdef",
        reason = "task_failure",
        runId = "r1",
        attributes = mapOf("task.chain" to "Depot", "failure.subtask" to "DepotRecognitionTask", "failure.node" to ""),
        traceId = "fedcba9876543210fedcba9876543210",
        spanId = "0123456789abcdef",
    )

    private fun log(content: String) = DiagnosticLog("asst.log", "maacore", content, content.length.toLong())

    @Test
    fun `每条记录带着找回事件与拼回原文所需的属性`() {
        val record = log("[INF] started\n").toRecords(context).single()

        assertEquals("[INF] started\n", record.body)
        assertEquals(SentryLogLevel.INFO, record.level)
        assertEquals(
            mapOf(
                "diagnostic.reason" to "task_failure",
                "diagnostic.source" to "asst.log",
                "diagnostic.kind" to "maacore",
                "diagnostic.event_id" to context.eventId,
                "run.id" to "r1",
                "task.chain" to "Depot",
                "failure.subtask" to "DepotRecognitionTask",
                "log.raw_bytes" to 14L,
                "trace.id" to context.traceId,
                "span.id" to context.spanId,
                "log.chunk_index" to 0,
                "log.chunk_count" to 1,
            ),
            record.attributes,
        )
    }

    @Test
    fun `长日志切成有上限的若干条，拼起来还是原文`() {
        val content = "[ERR] 识别失败 \"node\"\n".repeat(2_000)
        val records = log(content).toRecords(context)

        assertTrue(records.size > 1)
        assertEquals(content, records.joinToString("") { it.body })
        assertEquals(records.indices.toList(), records.map { it.attributes["log.chunk_index"] })
        assertTrue(records.all { it.attributes["log.chunk_count"] == records.size })
        assertTrue(records.all { it.body.toByteArray().size <= 7 * 1024 })
        assertEquals(SentryLogLevel.ERROR, records.first().level)
    }

    @Test
    fun `按转义后的字节数切，不在字符中间下刀`() {
        // 引号转义后占 2 字节、汉字 3 字节、emoji 是一对代理项占 4 字节
        assertEquals(listOf("\"\"", "\"\""), chunkLogBody("\"\"\"\"", budget = 4))
        assertEquals(listOf("中", "文"), chunkLogBody("中文", budget = 5))
        assertEquals(listOf("😀", "😀"), chunkLogBody("😀😀", budget = 6))
        assertTrue(chunkLogBody("", budget = 4).isEmpty())
    }

    @Test
    fun `会话日志的级别从 JSON 字段里认`() {
        val session = DiagnosticLog("gui/meow_log.log", "session", "", 0)
        fun level(content: String) =
            DiagnosticLog(session.source, session.kind, content, 0).toRecords(context).single().level

        assertEquals(SentryLogLevel.ERROR, level("""{"type":"log","level":"ERROR","content":"任务出错"}"""))
        assertEquals(SentryLogLevel.WARN, level("""{"type":"log","level":"WARNING","content":"x"}"""))
        assertEquals(SentryLogLevel.INFO, level("""{"type":"log","level":"TRACE","content":"x"}"""))
    }

    @Test
    fun `过长或带控制字符的属性值截断并留标记`() {
        val long = DiagnosticLogContext(
            eventId = context.eventId,
            reason = "start_failure",
            runId = null,
            attributes = mapOf("failure.code" to "a".repeat(300), "failure.what" to "line\nbreak"),
            traceId = null,
            spanId = null,
        )
        val attributes = log("body").toRecords(long).single().attributes

        assertEquals(200, (attributes["failure.code"] as String).length)
        assertEquals("line�break", attributes["failure.what"])
        assertEquals(true, attributes["diagnostic.attributes_truncated"])
        assertFalse("run.id" in attributes)
        assertFalse("trace.id" in attributes)
    }
}
