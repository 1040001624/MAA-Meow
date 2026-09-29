package com.aliothmoon.maameow.schedule.model

/**
 * 定时任务「调度环境检查」的纯推导逻辑，便于单测
 *
 * 列表页健康卡与编辑页保存向导共用这一套判定
 */

/** 检查项，枚举顺序即展示/引导顺序（仅未通过项会出现在结果里） */
enum class ScheduleHealthIssue {
    /** 当前启动模式(Shizuku/Root)未授权：定时触发的唤醒、拉起界面、执行都依赖它 */
    BACKEND,

    /** 未忽略电池优化：后台触发可能被系统延迟或拦截 */
    BATTERY,

    /** 精确闹钟未允许，授权后恢复调度 */
    EXACT_ALARM,

    /** 通知权限未授予：定时执行与失败提醒的通知不可见 */
    NOTIFICATION,

    /** 启用的策略勾选了屏保，但悬浮窗未授予 */
    OVERLAY,

    /** 设备有安全锁屏但没配 PIN/手势：触发时若已锁屏会跳过执行 */
    UNLOCK_CREDENTIAL,
}

/**
 * 权限/状态快照，全部为「是否通过」语义
 *
 * [backendGranted] 只是已授权，不代表服务已连接 —— 环境检查发生在任务开跑前，
 * 此时服务本就未必绑定，用连接状态判断会误报
 */
data class ScheduleHealthSnapshot(
    val backendGranted: Boolean,
    val batteryWhitelist: Boolean,
    val notification: Boolean,
    val exactAlarmAllowed: Boolean,
    val overlayGranted: Boolean,
    /** 任一启用策略勾选了屏保选项 */
    val overlayNeeded: Boolean,
    /** 设备设了 PIN/图案/密码锁屏 */
    val deviceSecure: Boolean,
    /** 当前解锁方式的凭证已配好，同 [com.aliothmoon.maameow.domain.models.UnlockCredential.isReady] */
    val unlockCredentialReady: Boolean,
    /** 存在启用的策略；没有策略时不提醒 */
    val unlockNeeded: Boolean,
)

object ScheduleHealthLogic {

    /** 是否需要悬浮窗：存在启用且勾选屏保的策略 */
    fun overlayNeeded(strategies: List<ScheduleStrategy>): Boolean =
        strategies.any { it.enabled && it.autoScreenSaver }

    /** 推导未通过项；空列表 = 全部通过（健康卡应隐藏、向导无需弹出） */
    fun failingIssues(snapshot: ScheduleHealthSnapshot): List<ScheduleHealthIssue> = buildList {
        if (!snapshot.backendGranted) add(ScheduleHealthIssue.BACKEND)
        if (!snapshot.batteryWhitelist) add(ScheduleHealthIssue.BATTERY)
        if (!snapshot.exactAlarmAllowed) add(ScheduleHealthIssue.EXACT_ALARM)
        if (!snapshot.notification) add(ScheduleHealthIssue.NOTIFICATION)
        if (snapshot.overlayNeeded && !snapshot.overlayGranted) add(ScheduleHealthIssue.OVERLAY)
        if (snapshot.unlockNeeded && snapshot.deviceSecure && !snapshot.unlockCredentialReady) {
            add(ScheduleHealthIssue.UNLOCK_CREDENTIAL)
        }
    }

    /** 后端授权、解锁凭证都当场处理不完，留给健康卡 */
    fun wizardItems(snapshot: ScheduleHealthSnapshot): List<ScheduleHealthIssue> =
        failingIssues(snapshot).filterNot {
            it == ScheduleHealthIssue.BACKEND || it == ScheduleHealthIssue.UNLOCK_CREDENTIAL
        }
}
