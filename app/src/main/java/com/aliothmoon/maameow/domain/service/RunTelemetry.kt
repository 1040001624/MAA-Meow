package com.aliothmoon.maameow.domain.service

import com.alibaba.fastjson2.JSONObject
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.maa.AsstMsg
import com.aliothmoon.maameow.maa.task.MaaTaskParams

/** 全部方法可在任意线程调用且不阻塞；开关关着或构建没带 DSN 时空转 */
interface RunTelemetry {

    /** 会话开启之后、追加任务之前调用；返回本轮的关联 ID，未启用时为 null */
    fun onRunStarted(kind: RunKind, tasks: List<MaaTaskParams>): String?

    fun onTaskRegistered(taskId: Int, params: String)

    fun onCallback(message: AsstMsg, details: JSONObject?)

    /** [code] 是失败环节的状态码；哪些算故障由实现筛 */
    fun onStartFailed(code: String, cause: Throwable? = null)

    /** [state] 须是置 ERROR 之前的执行状态 */
    fun onServiceDied(state: MaaExecutionState)
}

enum class RunKind(val value: String) {
    CHAIN("chain"),

    /** 作业、工具箱、小游戏 */
    AUX("aux"),
}
