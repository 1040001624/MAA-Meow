package com.aliothmoon.maameow.telemetry

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/** Sentry `hardware` context，键名与 MaaFwApp 一致 */
internal object TelemetryHardware {

    fun collect(context: Context): Map<String, Any> = buildMap {
        put("cpu", cpu())
        put("cpu_cores", Runtime.getRuntime().availableProcessors())
        memoryTotalMb(context)?.let { put("memory_total_mb", it) }
        put("os", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        put("device", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
        put("abi", Build.SUPPORTED_ABIS.joinToString(","))
    }

    private fun cpu(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val soc = "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim()
            if (soc.isNotBlank() && !soc.equals("unknown unknown", ignoreCase = true)) return soc
        }
        return Build.HARDWARE.orEmpty()
    }

    private fun memoryTotalMb(context: Context): Long? = runCatching {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return null
        val info = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        info.totalMem / 1024 / 1024
    }.getOrNull()
}
