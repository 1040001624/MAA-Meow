package com.aliothmoon.maameow.telemetry

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.data.resource.MaaCoreVersion
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.service.RunKind
import com.aliothmoon.maameow.domain.service.RunTelemetry
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.maa.AsstMsg
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.manager.RemoteAccessCoordinator
import com.aliothmoon.maameow.manager.RemoteServiceManager
import io.sentry.Attachment
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryAttributes
import io.sentry.SentryOptions
import io.sentry.SpanStatus
import io.sentry.android.core.SentryAndroid
import io.sentry.logger.SentryLogParameters
import io.sentry.protocol.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Sentry 遥测，沿用 MaaFwApp `TelemetryController`，事件模型按 MaaCore 的回调重写
 *
 * 构建没带 DSN 或用户开关关着就不初始化
 * 上报面见 docs/zh-cn/develop/TELEMETRY.md，改动时两边一起改
 */
class TelemetryController(
    private val context: Context,
    private val settings: AppSettingsManager,
    private val notificationSettings: NotificationSettingsManager,
    private val pathConfig: MaaPathConfig,
    private val taskChainState: TaskChainState,
) : RunTelemetry {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    /** 只在 [lock] 里改；回调热路径上先不加锁看一眼，没开就什么都不做 */
    @Volatile
    private var active = false

    /** 一轮只报一次整轮级别的故障：连接层报错与随后的启动失败往往是同一件事 */
    private var runIncidentReported = false

    private val tracer = RunTracer(
        startTransaction = { name, op -> Sentry.startTransaction(name, op) },
        onTaskStarted = { reporter.onTaskStarted() },
        onTaskFailure = { reporter.report(it) },
    )

    private val reporter = IncidentReporter(
        scope = scope,
        io = Dispatchers.IO,
        store = { evidenceStore },
        secrets = ::secrets,
        encodeImage = ::encodeJpeg,
        imagesAllowed = { settings.runMode.value == RunMode.BACKGROUND },
        attachmentSampleRate = FAILURE_ATTACHMENT_SAMPLE_RATE,
        send = ::send,
    )

    private val evidenceStore: EvidenceStore by lazy {
        RoutingEvidenceStore(
            local = LocalEvidenceStore(File(pathConfig.debugDir), File(pathConfig.coreDebugDir)),
            remote = RemoteEvidenceStore { RemoteServiceManager.getInstanceOrNull() },
            coreSeparated = pathConfig.isCoreSeparated,
        )
    }

    /** 取值不随开关变，重新初始化不必再查一遍 ActivityManager */
    private val hardware by lazy { TelemetryHardware.collect(context) }

    /** 须在设置读完盘之后调：读盘前开关还是默认值，照着初始化会绕过用户的关闭 */
    fun setup() {
        if (BuildConfig.SENTRY_DSN.isBlank()) {
            Timber.i("[telemetry] 构建未带 DSN，遥测不启用")
            return
        }
        scope.launch {
            // 开关关着也记：用户反馈问题时可凭这行在后台按 user.id 定位
            Timber.i("[telemetry] 匿名设备 ID (Sentry user.id) = %s", TelemetryUserId.get(context))
            combine(settings.telemetryEnabled, settings.updateChannel) { enabled, channel ->
                if (enabled) environmentOf(channel) else null
            }.distinctUntilChanged().collect(::reconfigure)
        }
    }

    override fun onRunStarted(kind: RunKind, tasks: List<MaaTaskParams>): String? {
        if (!active) return null
        val runId = UUID.randomUUID().toString()
        val begun = guarded {
            if (!active) return@guarded false
            runIncidentReported = false
            tracer.begin(runId, kind.value, tasks.map { it.type.value })
            reporter.onRunStarted()
            true
        }
        if (begun != true) return null
        // 读资源版本要碰盘，调用方可能在主线程
        scope.launch { runCatching { refreshRunTags() } }
        return runId
    }

    override fun onTaskRegistered(taskId: Int, params: String) {
        if (!active) return
        val summary = TaskParamSummary.summarize(params)
        guarded { if (active) tracer.register(taskId, summary) }
    }

    override fun onCallback(message: AsstMsg, details: JSONObject?) {
        // SubTaskStart / SubTaskExtraInfo 是高频回调，先比消息类型再谈别的
        if (!active || message !in TRACKED_MESSAGES) return
        guarded { if (active) tracer.onCallback(message, details) }
    }

    override fun onStartFailed(code: String, cause: Throwable?) {
        if (!active || code !in REPORTED_START_FAILURES) return
        reportRunIncident(SpanStatus.INTERNAL_ERROR) {
            StartFailure(
                runId = tracer.runId,
                code = code,
                what = cause?.javaClass?.simpleName,
                cause = cause?.toString(),
                tags = tracer.tags,
            )
        }
    }

    override fun onServiceDied(state: MaaExecutionState) {
        // 空闲期死掉多半是用户停了 Shizuku，不算故障
        if (!active || state !in RUNNING_STATES) return
        val serviceState = when (RemoteServiceManager.state.value) {
            is RemoteServiceManager.ServiceState.Died -> "died"
            is RemoteServiceManager.ServiceState.Error -> "error"
            else -> "unknown"
        }
        reportRunIncident(SpanStatus.ABORTED) {
            ServiceDeath(
                runId = tracer.runId,
                state = state.name.lowercase(),
                serviceState = serviceState,
                backend = RemoteAccessCoordinator.configuredBackend().name.lowercase(),
                taskChain = tracer.currentTaskChain,
                tags = tracer.tags,
            )
        }
    }

    /** [incident] 要在收掉这一轮之前构造，它取的是当前这轮的 ID 与 tag */
    private fun reportRunIncident(status: SpanStatus, incident: () -> Incident) {
        guarded {
            if (!active || runIncidentReported) return@guarded
            runIncidentReported = true
            val built = incident()
            tracer.abort(status)
            reporter.report(built)
        }
    }

    /** 在锁里改追踪状态；遥测自己出错不能连累启动流程和回调分发 */
    private fun <T> guarded(block: () -> T): T? = try {
        synchronized(lock, block)
    } catch (e: Throwable) {
        if (e is VirtualMachineError) throw e
        Timber.w(e, "[telemetry] 处理失败")
        null
    }

    /** 由 [reporter] 在后台取完证之后调用，不在锁里 */
    private fun send(incident: Incident, evidence: Evidence) {
        val event = incident.toSentryEvent().apply { setEvidence(evidence) }
        evidence.logs?.let { logs ->
            val logContext = event.diagnosticLogContext(incident)
            logs.entries.asSequence().flatMap { it.toRecords(logContext) }.forEach { record ->
                // 不传 args：日志正文里的 % 不能被当成格式串
                Sentry.logger().log(
                    record.level,
                    SentryLogParameters.create(SentryAttributes.fromMap(record.attributes)),
                    record.body,
                )
            }
        }
        val attachments = (evidence.attachment as? AttachmentOutcome.Attached)?.images.orEmpty()
            .map { Attachment(it.bytes, it.filename, JPEG_CONTENT_TYPE) }
        val eventId = if (attachments.isEmpty()) {
            Sentry.captureEvent(event)
        } else {
            Sentry.captureEvent(event, Hint.withAttachments(attachments))
        }
        Timber.i("[telemetry] %s event_id=%s run_id=%s", incident.reason, eventId, incident.runId)
    }

    /**
     * 锁只护 [active] 与追踪状态的切换，Sentry 的收尾与初始化放在锁外，不让回调线程跟着等
     *
     * 只由 [setup] 里那条 collect 串行调用，不会并发
     */
    private fun reconfigure(environment: String?) {
        val wasActive = synchronized(lock) {
            tracer.reset()
            reporter.cancelAll()
            active.also { active = false }
        }
        if (wasActive) {
            // 先正常结束 Session，否则它会被判为 abnormal，拉低 crash-free 率
            Sentry.endSession()
            Sentry.close()
        }
        if (environment == null) return
        runCatching { init(environment) }
            .onFailure { Timber.w(it, "[telemetry] 初始化失败") }
            .onSuccess { synchronized(lock) { active = true } }
    }

    private fun init(environment: String) {
        SentryAndroid.init(context) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.environment = environment
            options.tracesSampleRate = TRACES_SAMPLE_RATE
            options.isSendDefaultPii = false
            // 只有出事时那份日志尾巴走 Sentry Logs，App 平时的日志不往这里写
            options.logs.isEnabled = true
            options.logs.beforeSend = SentryOptions.Logs.BeforeSendLogCallback { it.apply { bindDiagnosticTrace() } }
            // Session（Release Health）开着，日活与 crash-free 率靠它
            options.isEnableAutoSessionTracking = true
            // 其余自动采集面全部关掉，只留本类显式发出的事件与未捕获异常
            options.isAnrEnabled = false
            // 不关的话每条消息事件都会附上发送线程的调用栈
            options.isAttachStacktrace = false
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.isEnableUserInteractionBreadcrumbs = false
            options.isEnableUserInteractionTracing = false
            options.isEnableActivityLifecycleBreadcrumbs = false
            options.isEnableAutoActivityLifecycleTracing = false
            options.isEnableAppLifecycleBreadcrumbs = false
            options.isEnableAppComponentBreadcrumbs = false
            options.isEnableSystemEventBreadcrumbs = false
            options.isEnableNetworkEventBreadcrumbs = false
        }
        Sentry.setUser(User().apply { id = TelemetryUserId.get(context) })
        Sentry.setTag("build_type", BuildConfig.BUILD_TYPE)
        Sentry.setTag("maacore.version", MaaCoreVersion.current.ifBlank { "unknown" })
        Sentry.configureScope { it.setContexts("hardware", hardware) }
        refreshRunTags()
    }

    /** 会随用户设置变的那几项，每轮开跑时重读 */
    private fun refreshRunTags() {
        Sentry.setTag("client_type", taskChainState.clientType)
        Sentry.setTag("run_mode", settings.runMode.value.name.lowercase())
        Sentry.setTag("backend", RemoteAccessCoordinator.configuredBackend().name.lowercase())
        Sentry.setTag("core_location", pathConfig.coreLocation.name.lowercase())
        pathConfig.readDiskResourceVersion()?.let { Sentry.setTag("resource.version", it) }
    }

    private suspend fun secrets(): Collection<String> = TelemetrySecrets.collect(
        app = settings.settings.first(),
        notification = notificationSettings.settings.first(),
        // 当前配置档的链以内存里的为准，存盘的那份可能还没跟上
        chains = taskChainState.profiles.value.map { it.chain } + listOf(taskChainState.chain.value),
    )

    /** Core 存的是 PNG，一张一兆上下，压成 JPEG 再带走 */
    private fun encodeJpeg(bytes: ByteArray): ByteArray? = runCatching {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        try {
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                .toByteArray()
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()

    private fun environmentOf(channel: UpdateChannel): String =
        if (BuildConfig.DEBUG) DEV_ENVIRONMENT else channel.value

    private companion object {
        const val DEV_ENVIRONMENT = "dev"
        const val JPEG_CONTENT_TYPE = "image/jpeg"
        const val JPEG_QUALITY = 80

        /** 一轮一条事务、每条任务链一条 Span，量不大，全采 */
        const val TRACES_SAMPLE_RATE = 1.0

        /** 失败事件带出错截图的比例；事件本身与日志不受它影响 */
        const val FAILURE_ATTACHMENT_SAMPLE_RATE = 1.0

        val TRACKED_MESSAGES = setOf(
            AsstMsg.TaskChainStart,
            AsstMsg.TaskChainError,
            AsstMsg.TaskChainCompleted,
            AsstMsg.TaskChainStopped,
            AsstMsg.SubTaskError,
            AsstMsg.AllTasksCompleted,
            AsstMsg.Destroyed,
        )

        val RUNNING_STATES = setOf(
            MaaExecutionState.STARTING,
            MaaExecutionState.RUNNING,
            MaaExecutionState.STOPPING,
        )

        /**
         * 算故障的启动失败，取值同 MaaCompositionService 里的会话状态码
         *
         * 横屏、分辨率、后端没授权、服务还在连这几种是用户侧条件，不报
         */
        val REPORTED_START_FAILURES = setOf(
            "RESOURCE_ERROR",
            "CORE_DATA_PUSH_ERROR",
            "REMOTE_ACCESS_UNAVAILABLE",
            "CREATE_INSTANCE_ERROR",
            "SET_TOUCH_MODE_ERROR",
            "DISPLAY_MODE_ERROR",
            "VIRTUAL_DISPLAY_ERROR",
            "MAA_CONNECT_ERROR",
            "START_ERROR",
        )
    }
}
