package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.constant.LogConfig
import java.io.File

/** 日志导出筛选：不删源、不写盘。 */
object LogExportCollector {

    const val EXPORT_DIR_NAME = "export"

    private const val MS_PER_DAY = 24L * 60 * 60 * 1000

    private val ROLLING_DIR_MARKERS = listOf(
        "/gui/",
        "/schedule/",
        "/error_logs/",
        "/crash_logs/",
        "/logcat/",
    )

    private val SCREENSHOT_EXTENSIONS = setOf("png", "jpg", "jpeg")

    fun collect(debugDir: File): List<File> {
        if (!debugDir.isDirectory) return emptyList()
        val exportPrefix = File(debugDir, EXPORT_DIR_NAME).invariantSeparatorsPath
        return select(
            debugDir.walkTopDown()
                .filter { file ->
                    file.isFile && !file.invariantSeparatorsPath.startsWith(exportPrefix)
                }
                .toList(),
        )
    }

    fun select(files: Iterable<File>, now: Long = System.currentTimeMillis()): List<File> =
        files.filter { it.isFile && isFresh(it.invariantSeparatorsPath, it.lastModified(), now) }

    /** [path] 以 / 分隔，绝对路径或相对 debug 目录都行 */
    fun isFresh(path: String, lastModified: Long, now: Long): Boolean {
        val days = when {
            isScreenshot(path) -> LogConfig.EXPORT_SCREENSHOT_DAYS
            isUnderRollingDir(path) -> LogConfig.EXPORT_ROLLING_LOG_DAYS
            else -> return true
        }
        return lastModified >= now - days * MS_PER_DAY
    }

    /** 不看修改时间就能定去留 */
    fun isAlwaysExported(path: String): Boolean = !isScreenshot(path) && !isUnderRollingDir(path)

    /** 已是压缩格式，进 zip 不会再变小 */
    fun isScreenshot(path: String): Boolean =
        path.substringAfterLast('.', "").lowercase() in SCREENSHOT_EXTENSIONS

    /** 各目录轮流出最新一张，免得一个任务连存的十几张挤掉别的目录；返回装得下的与被挤掉的 */
    fun <T> fitScreenshots(
        shots: List<T>,
        usedBytes: Long,
        pathOf: (T) -> String,
        sizeOf: (T) -> Long,
        lastModifiedOf: (T) -> Long,
        budgetBytes: Long = LogConfig.EXPORT_MAX_ZIP_BYTES,
    ): Pair<List<T>, List<T>> {
        val ordered = shots
            .groupBy { pathOf(it).substringBeforeLast('/', "") }
            .values
            .flatMap { dir -> dir.sortedByDescending(lastModifiedOf).mapIndexed { rank, shot -> rank to shot } }
            .sortedWith(compareBy<Pair<Int, T>> { it.first }.thenByDescending { lastModifiedOf(it.second) })
            .map { it.second }
        var used = usedBytes
        return ordered.partition { shot ->
            val size = sizeOf(shot)
            (used + size <= budgetBytes).also { fits -> if (fits) used += size }
        }
    }

    private fun isUnderRollingDir(path: String): Boolean {
        val anchored = "/$path"
        return ROLLING_DIR_MARKERS.any { anchored.contains(it) }
    }
}
