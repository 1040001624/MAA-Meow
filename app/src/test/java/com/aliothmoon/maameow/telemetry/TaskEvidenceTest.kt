package com.aliothmoon.maameow.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TaskEvidenceTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val debugDir by lazy { temp.newFolder("debug") }
    private val store by lazy { LocalEvidenceStore(debugDir) }

    private val coreLog = EvidenceFile("asst.log", kind = "maacore", core = true)
    private val appLog = EvidenceFile("error_logs/error.log", kind = "app", core = false)

    private fun write(path: String, content: String) = File(debugDir, path).apply {
        parentFile?.mkdirs()
        writeText(content)
    }

    private fun write(path: String, bytes: ByteArray) = File(debugDir, path).apply {
        parentFile?.mkdirs()
        writeBytes(bytes)
    }

    private fun start(vararg files: EvidenceFile) = TaskEvidence.captureStart(store, files.toList())

    @Test
    fun `只取开跑之后新写的部分`() {
        write("asst.log", "before\n")
        val start = start(coreLog)
        write("asst.log", "before\nfailed here\n")

        val logs = TaskEvidence.collectLogs(store, start)

        val entry = logs.entries.single()
        assertEquals("asst.log", entry.source)
        assertEquals("maacore", entry.kind)
        assertEquals("before\nfailed here\n", entry.content)
        assertFalse(logs.truncated)
        assertTrue(logs.warnings.isEmpty())
    }

    @Test
    fun `没动过的文件不带`() {
        write("asst.log", "same\n")
        write("error_logs/error.log", "old\n")
        val start = start(coreLog, appLog)
        write("error_logs/error.log", "old\nnew\n")

        val logs = TaskEvidence.collectLogs(store, start)

        assertEquals(listOf("error_logs/error.log"), logs.entries.map { it.source })
    }

    @Test
    fun `开跑之前的内容只往回带一小段，半截的首行丢掉`() {
        val old = "x".repeat(100) + "\n"
        write("asst.log", old.repeat(2_000))
        val start = start(coreLog)
        File(debugDir, "asst.log").appendText("the failure\n")

        val content = TaskEvidence.collectLogs(store, start).entries.single().content

        assertTrue(content.endsWith("the failure\n"))
        assertTrue(content.length < 70 * 1024)
        assertTrue(content.lineSequence().first().length == 100)
    }

    /** App 侧日志稀疏，往回带得多了会带出几天前的无关报错 */
    @Test
    fun `App 侧日志往回带得比 Core 日志少`() {
        val old = "x".repeat(100) + "\n"
        write("error_logs/error.log", old.repeat(2_000))
        val start = start(appLog)
        File(debugDir, "error_logs/error.log").appendText("the warning\n")

        val content = TaskEvidence.collectLogs(store, start).entries.single().content

        assertTrue(content.endsWith("the warning\n"))
        assertTrue(content.length < 5 * 1024)
    }

    @Test
    fun `单文件超过上限时取尾巴并标记截断`() {
        write("asst.log", "")
        val start = start(coreLog)
        write("asst.log", "a".repeat(600 * 1024) + "\ntail\n")

        val logs = TaskEvidence.collectLogs(store, start)

        assertTrue(logs.truncated)
        assertTrue(logs.selectedRawBytes <= 512L * 1024)
        assertTrue(logs.entries.single().content.endsWith("tail\n"))
    }

    @Test
    fun `合计预算用完后面的文件不带`() {
        write("asst.log", "")
        write("error_logs/error.log", "")
        val third = EvidenceFile("third.log", kind = "boot", core = false)
        write("third.log", "")
        val start = start(coreLog, appLog, third)
        write("asst.log", "a".repeat(600 * 1024))
        write("error_logs/error.log", "b".repeat(600 * 1024))
        write("third.log", "never\n")

        val logs = TaskEvidence.collectLogs(store, start)

        assertEquals(listOf("asst.log", "error_logs/error.log"), logs.entries.map { it.source })
        assertTrue(logs.truncated)
        assertTrue(logs.selectedRawBytes <= 1024L * 1024)
    }

    @Test
    fun `轮转过的日志整份算新的`() {
        write("asst.log", "a".repeat(5_000))
        val start = start(coreLog)
        write("asst.log", "fresh after rotation\n")

        val logs = TaskEvidence.collectLogs(store, start)

        assertEquals("fresh after rotation\n", logs.entries.single().content)
        assertEquals(listOf("rotated_log_included_whole:asst.log"), logs.warnings)
    }

    @Test
    fun `开跑时还不存在的小日志整份带上`() {
        val start = start(coreLog)
        write("asst.log", "created later\n")

        val logs = TaskEvidence.collectLogs(store, start)

        assertEquals("created later\n", logs.entries.single().content)
        assertEquals(listOf("no_baseline_tail:asst.log"), logs.warnings)
    }

    /** 数据目录独立时开跑那一刻服务可能还没连上，量不到长度不等于文件是新的 */
    @Test
    fun `开跑时没量到长度的大日志只带一小截`() {
        val start = start(coreLog)
        write("asst.log", ("history line\n").repeat(40_000) + "the failure\n")

        val logs = TaskEvidence.collectLogs(store, start)

        assertTrue(logs.selectedRawBytes <= 128L * 1024)
        assertTrue(logs.entries.single().content.endsWith("the failure\n"))
    }

    @Test
    fun `开跑时在、出事后读不了的日志留一条 warning`() {
        write("asst.log", "x\n")
        val start = start(coreLog)
        File(debugDir, "asst.log").delete()

        val logs = TaskEvidence.collectLogs(store, start)

        assertTrue(logs.entries.isEmpty())
        assertEquals(listOf("open_log_failed:asst.log"), logs.warnings)
    }

    @Test
    fun `只带开跑之后新出现的出错截图`() {
        write("interface/2026.10.02-07.33.58.481_raw.png", byteArrayOf(1))
        val start = start(coreLog)
        write("interface/2026.10.02-08.23.43.20_raw.png", byteArrayOf(2, 3))
        write("interface/notes.txt", byteArrayOf(9))

        val outcome = TaskEvidence.collectImages(store, start) { it + 0 }

        val image = (outcome as AttachmentOutcome.Attached).images.single()
        assertEquals("2026.10.02-08.23.43.20_raw.jpg", image.filename)
        assertEquals(listOf<Byte>(2, 3, 0), image.bytes.toList())
    }

    @Test
    fun `没有新截图时记下原因`() {
        write("interface/old.png", byteArrayOf(1))
        val start = start(coreLog)

        val outcome = TaskEvidence.collectImages(store, start) { it }

        assertEquals("no_evidence", (outcome as AttachmentOutcome.Omitted).status)
    }

    @Test
    fun `截图压不了就不带`() {
        val start = start(coreLog)
        write("interface/broken.png", byteArrayOf(1))

        val outcome = TaskEvidence.collectImages(store, start) { null }

        assertEquals("build_failed", (outcome as AttachmentOutcome.Omitted).status)
    }

    @Test
    fun `截图合计超限整份不带`() {
        val start = start(coreLog)
        write("interface/a.png", byteArrayOf(1))
        write("interface/b.png", byteArrayOf(1))

        val outcome = TaskEvidence.collectImages(store, start) { ByteArray(1_500_000) }

        assertEquals("too_large", (outcome as AttachmentOutcome.Omitted).status)
    }

    /** 分不出新旧时宁可不带，免得把以前的截图算到这次失败头上 */
    @Test
    fun `开跑时没列到截图目录就不带截图`() {
        val unreadable = object : EvidenceStore by store {
            override fun list(dir: String, core: Boolean): List<String>? = null
        }
        write("interface/old.png", byteArrayOf(1))
        val start = TaskEvidence.captureStart(unreadable, listOf(coreLog))

        val outcome = TaskEvidence.collectImages(store, start) { it }

        assertEquals("no_baseline", (outcome as AttachmentOutcome.Omitted).status)
    }

    @Test
    fun `Core 侧文件在数据目录独立时改走远端`() {
        val remoteDir = temp.newFolder("remote")
        File(remoteDir, "asst.log").writeText("remote\n")
        File(remoteDir, "interface").mkdirs()
        File(remoteDir, "interface/shot.png").writeBytes(byteArrayOf(1))
        write("error_logs/error.log", "local\n")
        val routing = RoutingEvidenceStore(
            local = store,
            remote = LocalEvidenceStore(remoteDir),
            coreSeparated = true,
        )

        assertEquals(7L, routing.length(coreLog))
        assertEquals("remote\n", routing.read(coreLog, 0, 7).decodeToString())
        assertEquals(6L, routing.length(appLog))
        assertEquals(listOf("shot.png"), routing.list("interface", core = true))
    }

    /** 提权进程那条路只认独立数据目录，拿它当回退会读到别处的残留 */
    @Test
    fun `数据目录没独立时不碰远端`() {
        val remoteDir = temp.newFolder("remote")
        File(remoteDir, "asst.log").writeText("stale\n")
        File(remoteDir, "interface").mkdirs()
        File(remoteDir, "interface/stale.png").writeBytes(byteArrayOf(1))
        val routing = RoutingEvidenceStore(local = store, remote = LocalEvidenceStore(remoteDir), coreSeparated = false)

        assertNull(routing.length(coreLog))
        assertEquals(emptyList<String>(), routing.list("interface", core = true))

        write("asst.log", "local!\n")
        assertEquals("local!\n", routing.read(coreLog, 0, 7).decodeToString())
    }
}
