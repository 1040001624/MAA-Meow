package com.aliothmoon.maameow.telemetry

import io.mockk.mockk
import io.sentry.ISpan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class IncidentReporterTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val debugDir by lazy { temp.newFolder("debug") }
    private val sent = mutableListOf<Pair<Incident, Evidence>>()

    private val failure = TaskFailure(
        runId = "r1",
        taskChain = "Depot",
        taskId = 1,
        startedAtMs = 1_000,
        durationMs = 300,
        failedSubTasks = 0,
        terminal = null,
        node = null,
        exception = null,
        recent = emptyList(),
        options = emptyMap(),
        tags = emptyMap(),
        span = mockk<ISpan>(relaxed = true),
    )

    private fun TestScope.reporter(
        secrets: List<String> = emptyList(),
        sampleRate: Double = 1.0,
        imagesAllowed: Boolean = true,
    ) = IncidentReporter(
        scope = this,
        io = dispatcher,
        store = { LocalEvidenceStore(debugDir) },
        secrets = { secrets },
        encodeImage = { it },
        imagesAllowed = { imagesAllowed },
        attachmentSampleRate = sampleRate,
        send = { incident, evidence -> sent += incident to evidence },
    )

    private fun write(path: String, content: String) = File(debugDir, path).apply {
        parentFile?.mkdirs()
        writeText(content)
    }

    private val evidence get() = sent.single().second

    private fun sources() = evidence.logs?.entries?.map { it.source }

    @Test
    fun `任务失败带上这个任务期间的日志与截图，密钥先打码`() = runTest(dispatcher) {
        val reporter = reporter(secrets = listOf("hunter2-secret"))
        write("asst.log", "before\n")
        write("gui/meow_log_20261002_082323_1.log", "{\"type\":\"header\"}\n")
        reporter.onRunStarted()
        reporter.onTaskStarted()
        advanceUntilIdle()

        write("asst.log", "before\n{\"account_name\":\"138\",\"penguin_id\":\"hunter2-secret\"} failed\n")
        write("gui/meow_log_20261002_082323_1.log", "{\"type\":\"header\"}\n{\"content\":\"任务出错\"}\n")
        write("interface/2026.10.02-08.23.43.20_raw.png", "image")
        reporter.report(failure)
        advanceUntilIdle()

        assertEquals(failure, sent.single().first)
        assertEquals(listOf("asst.log", "gui/meow_log_20261002_082323_1.log"), sources())
        assertEquals(
            "before\n{\"account_name\":\"***\",\"penguin_id\":\"***\"} failed\n",
            evidence.logs?.entries?.first()?.content,
        )
        assertEquals("session", evidence.logs?.entries?.last()?.kind)
        val images = (evidence.attachment as AttachmentOutcome.Attached).images
        assertEquals(listOf("2026.10.02-08.23.43.20_raw.jpg"), images.map { it.filename })
    }

    /** 上一轮的会话日志不能混进来 */
    @Test
    fun `会话日志认最新的那一份`() = runTest(dispatcher) {
        val reporter = reporter()
        write("gui/meow_log_20261001_090000_3.log", "old\n")
        write("gui/meow_log_20261002_082323_1.log", "new\n")
        reporter.onRunStarted()
        reporter.onTaskStarted()
        advanceUntilIdle()

        File(debugDir, "gui/meow_log_20261001_090000_3.log").appendText("old grows\n")
        File(debugDir, "gui/meow_log_20261002_082323_1.log").appendText("new grows\n")
        reporter.report(failure)
        advanceUntilIdle()

        assertEquals(listOf("gui/meow_log_20261002_082323_1.log"), sources())
    }

    @Test
    fun `没被采样到时不带截图，日志照带`() = runTest(dispatcher) {
        val reporter = reporter(sampleRate = 0.0)
        write("asst.log", "")
        reporter.onTaskStarted()
        advanceUntilIdle()

        write("asst.log", "failed\n")
        write("interface/shot.png", "image")
        reporter.report(failure)
        advanceUntilIdle()

        assertEquals(AttachmentOutcome.NotSelected, evidence.attachment)
        assertEquals("failed\n", evidence.logs?.entries?.single()?.content)
    }

    /** 前台模式下 Core 截的是主屏，可能拍到别的应用 */
    @Test
    fun `不许带截图时只带日志`() = runTest(dispatcher) {
        val reporter = reporter(imagesAllowed = false)
        write("asst.log", "")
        reporter.onTaskStarted()
        advanceUntilIdle()

        write("asst.log", "failed\n")
        write("interface/shot.png", "image")
        reporter.report(failure)
        advanceUntilIdle()

        assertEquals(AttachmentOutcome.NotSelected, evidence.attachment)
        assertEquals("failed\n", evidence.logs?.entries?.single()?.content)
    }

    @Test
    fun `没有开跑快照时只发事件`() = runTest(dispatcher) {
        write("asst.log", "failed\n")
        reporter().report(failure)
        advanceUntilIdle()

        assertNull(evidence.logs)
        assertEquals(AttachmentOutcome.NotSelected, evidence.attachment)
    }

    @Test
    fun `整轮级别的事件取整轮快照`() = runTest(dispatcher) {
        val reporter = reporter()
        write("asst.log", "boot\n")
        write("service_bind_debug.log", "bind\n")
        reporter.onRunStarted()
        advanceUntilIdle()

        write("asst.log", "boot\nrunning\n")
        reporter.onTaskStarted()
        advanceUntilIdle()
        write("asst.log", "boot\nrunning\ndied\n")
        write("crash.log", "SIGSEGV\n")
        write("service_bind_debug.log", "bind\nbinder died\n")
        write("interface/shot.png", "image")
        reporter.report(
            ServiceDeath(
                runId = "r1",
                state = "running",
                serviceState = "died",
                backend = "shizuku",
                taskChain = "Fight",
                tags = emptyMap(),
            )
        )
        advanceUntilIdle()

        // 崩溃现场与启动诊断排在前面，免得被大日志挤掉
        assertEquals(listOf("crash.log", "service_bind_debug.log", "asst.log"), sources())
        assertEquals("boot\nrunning\ndied\n", evidence.logs?.entries?.last()?.content)
        assertEquals(AttachmentOutcome.NotSelected, evidence.attachment)
    }

    @Test
    fun `一次任务失败用掉这个任务的快照`() = runTest(dispatcher) {
        val reporter = reporter()
        write("asst.log", "")
        reporter.onTaskStarted()
        advanceUntilIdle()
        write("asst.log", "first failure\n")
        reporter.report(failure)
        reporter.report(failure.copy(taskId = 2))
        advanceUntilIdle()

        // 没快照的那条不用等日志落盘，会先发出去
        val logsByTask = sent.associate { (incident, evidence) -> (incident as TaskFailure).taskId to evidence.logs }
        assertEquals(setOf(1, 2), logsByTask.keys)
        assertEquals("first failure\n", logsByTask[1]?.entries?.single()?.content)
        assertNull(logsByTask[2])
    }

    @Test
    fun `遥测关掉后排着队的事件不发`() = runTest(dispatcher) {
        val reporter = reporter()
        reporter.onTaskStarted()
        advanceUntilIdle()

        reporter.report(failure)
        reporter.cancelAll()
        advanceUntilIdle()

        assertTrue(sent.isEmpty())
    }

    @Test
    fun `附件采样对同一个失败结论固定，比例大致对得上`() {
        assertFalse(shouldSampleAttachment("r1", 7, 0.0))
        assertTrue(shouldSampleAttachment("r1", 7, 1.0))
        assertEquals(shouldSampleAttachment("r1", 7, 0.5), shouldSampleAttachment("r1", 7, 0.5))

        val sampled = (1..2_000).count { shouldSampleAttachment("run-$it", it.toLong(), 0.25) }
        assertTrue("sampled=$sampled", sampled in 400..600)
    }
}
