package com.aliothmoon.maameow.constant


object LogConfig {
    /** 内存中最大运行时日志条数 */
    const val MAX_RUNTIME_LOG_COUNT = 750

    /** 任务日志保留天数 */
    const val MAX_TASK_LOG_DAYS = 30

    /** 错误日志单文件最大大小 */
    const val MAX_ERROR_LOG_SIZE = 2L * 1024 * 1024 // 2MB

    /** 错误日志最大文件数 */
    const val MAX_ERROR_LOG_FILES = 5

    /** 崩溃日志最大文件数 */
    const val MAX_CRASH_LOG_FILES = 10

    /** 日志批量刷新间隔（毫秒） */
    const val LOG_FLUSH_INTERVAL_MS = 75L

    /** 导出时 gui / schedule / error_logs / crash_logs / logcat 仅保留近 N 天 */
    const val EXPORT_ROLLING_LOG_DAYS = 7

    /** 导出时截图仅保留近 N 天，再受 [EXPORT_MAX_ZIP_BYTES] 约束 */
    const val EXPORT_SCREENSHOT_DAYS = 14

    /** 导出包体积预算，GitHub issue 附件上限 25MB */
    const val EXPORT_MAX_ZIP_BYTES = 24L * 1024 * 1024

    /** logcat 抓取文件保留天数 */
    const val LOGCAT_KEEP_DAYS = 7

    /** logcat 抓取目录总量上限 */
    const val LOGCAT_MAX_TOTAL_BYTES = 64L * 1024 * 1024

    /** 单次 logcat 抓取上限，分两段滚动，只留最新 */
    const val LOGCAT_MAX_CAPTURE_BYTES = 16L * 1024 * 1024
}
