package com.aliothmoon.maameow.remote.internal

import com.aliothmoon.maameow.constant.LogConfig
import java.io.File

/** logcat 抓取目录的保留策略：先按天数，再按总量从旧到新删 */
internal object LogcatRetention {

    private const val MS_PER_DAY = 24L * 60 * 60 * 1000

    data class Entry(val file: File, val lastModified: Long, val size: Long)

    fun selectExpired(
        entries: List<Entry>,
        now: Long,
        keepDays: Int = LogConfig.LOGCAT_KEEP_DAYS,
        maxTotalBytes: Long = LogConfig.LOGCAT_MAX_TOTAL_BYTES,
    ): List<File> {
        val cutoff = now - keepDays * MS_PER_DAY
        val (expired, kept) = entries.partition { it.lastModified < cutoff }
        val doomed = expired.mapTo(ArrayList()) { it.file }
        var total = kept.sumOf { it.size }
        for (entry in kept.sortedBy { it.lastModified }) {
            if (total <= maxTotalBytes) break
            doomed += entry.file
            total -= entry.size
        }
        return doomed
    }

    fun prune(logcatDir: File, now: Long = System.currentTimeMillis()): Int {
        if (!logcatDir.isDirectory) return 0
        val entries = logcatDir.walkTopDown()
            .filter { it.isFile }
            .map { Entry(it, it.lastModified(), it.length()) }
            .toList()
        return selectExpired(entries, now).count { it.delete() }
    }
}
