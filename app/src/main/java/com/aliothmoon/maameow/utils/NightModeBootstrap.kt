package com.aliothmoon.maameow.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 深浅色在 Activity 创建前定下：设置异步读盘，读完再套会让主题与系统不一致时每次冷启动都重建一次 */
object NightModeBootstrap {

    private const val PREFS_NAME = "night_mode_cache"
    private const val KEY_MODE = "mode"

    fun applyCached(context: Context) {
        val mode = prefs(context).getInt(KEY_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /** 设置已读盘后调用，之后主题变化照常重建 Activity */
    fun follow(context: Context, scope: CoroutineScope, manager: AppSettingsManager) {
        val prefs = prefs(context)
        scope.launch(Dispatchers.Main) {
            manager.themeMode.collect { theme ->
                val mode = theme.toNightMode()
                if (AppCompatDelegate.getDefaultNightMode() != mode) {
                    AppCompatDelegate.setDefaultNightMode(mode)
                }
                if (prefs.getInt(KEY_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) != mode) {
                    prefs.edit { putInt(KEY_MODE, mode) }
                }
            }
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun AppSettingsManager.ThemeMode.toNightMode(): Int = when (this) {
        AppSettingsManager.ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        AppSettingsManager.ThemeMode.WHITE -> AppCompatDelegate.MODE_NIGHT_NO
        AppSettingsManager.ThemeMode.DARK,
        AppSettingsManager.ThemeMode.PURE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
    }
}
