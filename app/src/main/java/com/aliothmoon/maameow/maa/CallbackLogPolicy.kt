package com.aliothmoon.maameow.maa

/** SubTask 起止占回调日志九成，asst.log 里已有，两侧都不再重复记 */
object CallbackLogPolicy {

    fun shouldLog(msg: Int): Boolean =
        msg != AsstMsg.SubTaskStart.value && msg != AsstMsg.SubTaskCompleted.value
}
