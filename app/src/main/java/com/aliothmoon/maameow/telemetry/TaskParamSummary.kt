package com.aliothmoon.maameow.telemetry

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 任务参数的摘要，写到任务 Span 与失败事件的 `option.<key>` 上
 *
 * 白名单：布尔与数值报原值；枚举类字符串报原值；其余字符串只报填没填——
 * 那里装的是账号、汇报 ID、文件路径，出不得设备；数组只报长度
 */
internal object TaskParamSummary {

    private const val MAX_ENTRIES = 100
    private const val MAX_VALUE_LENGTH = 64
    private const val MAX_DEPTH = 2
    private const val FILLED = "filled"
    private const val EMPTY = "empty"

    /** 取值域由游戏或 Core 定死的字符串键，带不出隐私 */
    private val ENUM_KEYS = setOf(
        "client_type", "server", "stage", "theme", "squad", "roles", "mode", "product",
    )

    fun summarize(params: String): Map<String, String> {
        val root = runCatching { Json.parseToJsonElement(params) }.getOrNull() as? JsonObject
            ?: return emptyMap()
        return buildMap { collect(root, prefix = "", depth = 0, into = this) }
    }

    private fun collect(obj: JsonObject, prefix: String, depth: Int, into: MutableMap<String, String>) {
        for ((key, value) in obj) {
            if (into.size >= MAX_ENTRIES) return
            val path = prefix + key
            when (value) {
                is JsonNull -> Unit
                is JsonPrimitive -> into[path] = primitive(key, value)
                is JsonArray -> into["$path.count"] = value.size.toString()
                is JsonObject -> if (depth < MAX_DEPTH) {
                    collect(value, "$path.", depth + 1, into)
                } else {
                    into["$path.count"] = value.size.toString()
                }
            }
        }
    }

    private fun primitive(key: String, value: JsonPrimitive): String = when {
        !value.isString -> value.content.take(MAX_VALUE_LENGTH)
        key in ENUM_KEYS -> value.content.take(MAX_VALUE_LENGTH).ifEmpty { EMPTY }
        else -> if (value.content.isBlank()) EMPTY else FILLED
    }
}
