package com.aliothmoon.maameow.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.manager.ShizukuInstallHelper
import com.aliothmoon.maameow.manager.ShizukuReadiness
import com.aliothmoon.maameow.manager.ShizukuReadinessProvider
import kotlinx.coroutines.launch
import org.koin.compose.koinInject


/** 常驻引导：跟着 [ShizukuReadinessProvider.state] 走，关掉即写 skipShizukuCheck 全局不再提醒 */
@Composable
fun ShizukuReadinessGate(
    readinessProvider: ShizukuReadinessProvider = koinInject(),
) {
    val readiness by readinessProvider.state.collectAsStateWithLifecycle()
    ShizukuReadinessGuide(readiness = readiness)
}

/**
 * 按给定判定渲染引导并接好各按钮动作，常驻引导与一次性引导共用
 *
 * @param onDismiss   默认写 skipShizukuCheck 全局不再提醒；一次性引导传只收弹窗的实现
 * @param dismissText 覆盖否定按钮文案，配合一次性语义使用
 */
@Composable
fun ShizukuReadinessGuide(
    readiness: ShizukuReadiness,
    onDismiss: (() -> Unit)? = null,
    dismissText: String? = null,
    permissionManager: PermissionManager = koinInject(),
    appSettingsManager: AppSettingsManager = koinInject(),
) {
    val launchPackage by appSettingsManager.shizukuLaunchPackage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRequesting by remember { mutableStateOf(false) }

    ShizukuReadinessDialog(
        readiness = readiness,
        onInstall = { ShizukuInstallHelper.installShizuku(context) },
        onOpenApp = { ShizukuInstallHelper.openShizuku(context, launchPackage) },
        onRequestAuth = {
            scope.launch {
                isRequesting = true
                permissionManager.requestRemoteAccess()
                isRequesting = false
            }
        },
        onDismiss = onDismiss ?: {
            scope.launch { appSettingsManager.setSkipShizukuCheck(true) }
            Unit
        },
        onSwitchToRoot = {
            scope.launch { permissionManager.setStartupBackend(RemoteBackend.ROOT) }
        },
        isRequesting = isRequesting,
        dismissText = dismissText,
    )
}
