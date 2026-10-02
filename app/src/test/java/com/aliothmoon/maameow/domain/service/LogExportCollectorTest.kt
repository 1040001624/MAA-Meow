package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.constant.LogConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

/**
 * 锁住日志导出挑选契约：
 * - gui / schedule / error_logs / crash_logs / logcat：仅近 [LogConfig.EXPORT_ROLLING_LOG_DAYS] 天
 * - 截图：仅近 [LogConfig.EXPORT_SCREENSHOT_DAYS] 天，再按包体积预算从新到旧装
 * - asst / 其它：完整入选
 * - export/ 排除
 */
class LogExportCollectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var debugDir: File

    private val dayMs = 24L * 60 * 60 * 1000

    @Before
    fun setUp() {
        debugDir = tempFolder.newFolder("debug")
    }

    private fun fileAt(
        relativePath: String,
        sizeBytes: Long = 16,
        lastModified: Long = System.currentTimeMillis(),
    ): File {
        val f = File(debugDir, relativePath)
        f.parentFile?.mkdirs()
        if (sizeBytes <= 0L) {
            f.writeText("")
        } else {
            RandomAccessFile(f, "rw").use { raf ->
                raf.setLength(sizeBytes)
            }
        }
        f.setLastModified(lastModified)
        return f
    }

    @Test
    fun select_rollingDirs_onlyKeepRecentDays() {
        val now = System.currentTimeMillis()
        val recentGui = fileAt("gui/meow_recent.log", lastModified = now - dayMs)
        val oldGui = fileAt(
            "gui/meow_old.log",
            lastModified = now - (LogConfig.EXPORT_ROLLING_LOG_DAYS + 2) * dayMs,
        )
        val recentErr = fileAt("error_logs/error.log", lastModified = now - 2 * dayMs)
        val oldCrash = fileAt(
            "crash_logs/crash_1.log",
            lastModified = now - (LogConfig.EXPORT_ROLLING_LOG_DAYS + 1) * dayMs,
        )
        val recentSched = fileAt("schedule/s.log", lastModified = now)

        val selected = LogExportCollector.select(
            listOf(recentGui, oldGui, recentErr, oldCrash, recentSched),
        )

        assertEquals(setOf(recentGui, recentErr, recentSched), selected.toSet())
    }

    @Test
    fun select_rollingDirs_noSizeOrCountCap() {
        val now = System.currentTimeMillis()
        val big = fileAt("gui/big.log", sizeBytes = 20L * 1024 * 1024, lastModified = now)
        val many = (1..60).map { i ->
            fileAt("gui/meow_$i.log", sizeBytes = 8, lastModified = now - i)
        }

        val selected = LogExportCollector.select(many + big)

        assertEquals(61, selected.size)
        assertTrue(selected.contains(big))
    }

    @Test
    fun select_asst_fullNoAgeOrSizeCap() {
        val now = System.currentTimeMillis()
        val asst = fileAt("asst.log", sizeBytes = 80L * 1024 * 1024, lastModified = now - 30 * dayMs)
        val bak = fileAt("asst.bak.log", sizeBytes = 60L * 1024 * 1024, lastModified = now - 60 * dayMs)

        val selected = LogExportCollector.select(listOf(asst, bak))

        assertEquals(setOf(asst, bak), selected.toSet())
    }

    @Test
    fun select_logcat_onlyKeepRecentDays() {
        val now = System.currentTimeMillis()
        val recent = fileAt("logcat/core/recent.log", lastModified = now - dayMs)
        val old = fileAt(
            "logcat/app/old.log",
            lastModified = now - (LogConfig.EXPORT_ROLLING_LOG_DAYS + 1) * dayMs,
        )

        assertEquals(listOf(recent), LogExportCollector.select(listOf(recent, old)))
    }

    @Test
    fun select_screenshots_keepLongerThanLogsButNotForever() {
        val now = System.currentTimeMillis()
        val pastLogWindow = fileAt(
            "interface/a_raw.png",
            lastModified = now - (LogConfig.EXPORT_ROLLING_LOG_DAYS + 2) * dayMs,
        )
        val pastShotWindow = fileAt(
            "infrast/enter_facility/b_raw.PNG",
            lastModified = now - (LogConfig.EXPORT_SCREENSHOT_DAYS + 1) * dayMs,
        )

        assertEquals(
            listOf(pastLogWindow),
            LogExportCollector.select(listOf(pastLogWindow, pastShotWindow)),
        )
    }

    @Test
    fun isFresh_relativePathsFromCoreSide() {
        val now = System.currentTimeMillis()
        val old = now - 30 * dayMs

        assertTrue(LogExportCollector.isFresh("asst.log", old, now))
        assertTrue(LogExportCollector.isAlwaysExported("asst.log"))
        assertTrue(!LogExportCollector.isFresh("logcat/core/c.log", old, now))
        assertTrue(!LogExportCollector.isAlwaysExported("logcat/core/c.log"))
        assertTrue(!LogExportCollector.isFresh("interface/x_raw.png", old, now))
        assertTrue(!LogExportCollector.isAlwaysExported("interface/x_raw.png"))
    }

    /** path to (size, lastModified) */
    private fun fit(shots: List<Triple<String, Long, Long>>, usedBytes: Long, budgetBytes: Long = 100) =
        LogExportCollector.fitScreenshots(
            shots, usedBytes,
            pathOf = { it.first }, sizeOf = { it.second }, lastModifiedOf = { it.third },
            budgetBytes = budgetBytes,
        ).let { (fit, dropped) -> fit.map { it.first } to dropped.map { it.first } }

    @Test
    fun fitScreenshots_newestFirstWithinBudget() {
        val shots = listOf(
            Triple("interface/old.png", 40L, 1L),
            Triple("interface/mid.png", 40L, 2L),
            Triple("interface/new.png", 40L, 3L),
        )

        val (fit, dropped) = fit(shots, usedBytes = 10)

        assertEquals(listOf("interface/new.png", "interface/mid.png"), fit)
        assertEquals(listOf("interface/old.png"), dropped)
    }

    @Test
    fun fitScreenshots_dirsTakeTurnsSoOneBurstCannotCrowdOutOthers() {
        val shots = listOf(
            Triple("infrast/3.png", 30L, 30L),
            Triple("infrast/2.png", 30L, 20L),
            Triple("infrast/1.png", 30L, 10L),
            Triple("interface/fail.png", 30L, 5L),
            Triple("remote/interface/fail.png", 30L, 4L),
        )

        val (fit, dropped) = fit(shots, usedBytes = 0)

        assertEquals(listOf("infrast/3.png", "interface/fail.png", "remote/interface/fail.png"), fit)
        assertEquals(listOf("infrast/2.png", "infrast/1.png"), dropped)
    }

    @Test
    fun fitScreenshots_skipsTooBigAndKeepsTryingSmaller() {
        val shots = listOf(
            Triple("a/small-old.png", 5L, 1L),
            Triple("a/huge.png", 500L, 2L),
            Triple("a/new.png", 40L, 3L),
        )

        val (fit, dropped) = fit(shots, usedBytes = 50)

        assertEquals(listOf("a/new.png", "a/small-old.png"), fit)
        assertEquals(listOf("a/huge.png"), dropped)
    }

    @Test
    fun fitScreenshots_requiredAlreadyOverBudget_dropsAll() {
        val (fit, dropped) = fit(listOf(Triple("a.png", 1L, 1L)), usedBytes = 200)

        assertTrue(fit.isEmpty())
        assertEquals(1, dropped.size)
    }

    @Test
    fun select_debugRootMisc_fullPack() {
        val now = System.currentTimeMillis()
        val files = (1..40).map { i ->
            fileAt("misc_$i.txt", sizeBytes = 10, lastModified = now + i)
        }

        assertEquals(40, LogExportCollector.select(files).size)
    }

    @Test
    fun collect_skipsExportDirAndWalksTree() {
        val asst = fileAt("asst.log", sizeBytes = 50)
        fileAt("${LogExportCollector.EXPORT_DIR_NAME}/maa_logs_old.zip", sizeBytes = 200)
        val gui = fileAt("gui/meow_log_1.log", sizeBytes = 30)

        val collected = LogExportCollector.collect(debugDir)

        assertEquals(setOf(asst, gui), collected.toSet())
    }

    @Test
    fun collect_emptyWhenDebugMissing() {
        val missing = File(tempFolder.root, "no_such_debug")
        assertTrue(LogExportCollector.collect(missing).isEmpty())
    }
}
