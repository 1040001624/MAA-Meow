package com.aliothmoon.maameow.data.notification.provider

private const val TRUNCATED_MARK = "[...]\n"

/**
 * 裁到 [maxLength] 个字符以内
 * 开「通知含详细日志」后完成通知会带上本轮全部日志，超出渠道上限时整条被拒
 * 保留末尾：用时、配置与出错清单等正文排在日志之后
 */
internal fun String.keepTail(maxLength: Int): String {
    if (length <= maxLength) return this

    // 起点落在代理项对（如 emoji）中间时前半已被裁掉，剩下的低位代理项要一并丢掉，否则序列化成 JSON 会变成替换字符
    return TRUNCATED_MARK + takeLast(maxLength - TRUNCATED_MARK.length)
        .trimStart { it.isLowSurrogate() }
}

/** 同 [keepTail]，按 UTF-8 字节计 */
internal fun String.keepTailUtf8(maxBytes: Int): String {
    if (encodeToByteArray().size <= maxBytes) return this

    val budget = maxBytes - TRUNCATED_MARK.encodeToByteArray().size
    var bytes = 0
    var start = length
    while (start > 0) {
        val c = this[start - 1]
        val pair = c.isLowSurrogate() && start >= 2 && this[start - 2].isHighSurrogate()
        val size = when {
            pair -> 4
            c.code < 0x80 -> 1
            c.code < 0x800 -> 2
            else -> 3
        }
        if (bytes + size > budget) break
        bytes += size
        start -= if (pair) 2 else 1
    }
    return TRUNCATED_MARK + substring(start)
}
