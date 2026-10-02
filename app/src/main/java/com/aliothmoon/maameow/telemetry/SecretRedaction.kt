package com.aliothmoon.maameow.telemetry

/**
 * 日志离开设备前打码
 *
 * MaaCore 把任务参数原样写进 asst.log，会话日志也记了一份，拦不住，只能在上传这一步补
 */
internal object SecretRedaction {

    const val MASK = "***"

    /** 一两个字符的串在日志里到处都是，替换掉会把整份日志毁了 */
    private const val MIN_REDACT_LENGTH = 4

    /**
     * 账号片段可能只有两三位，按值匹配会漏，这几个键整段换掉
     * 会话日志把参数存成 JSON 字符串，引号前带反斜杠，一并兼容
     */
    private val KEYED = Regex("""(\\*"(?:account_name|penguin_id|yituliu_id)\\*"\s*:\s*\\*")[^"\\]+""")

    /** 长的先换：一个密钥是另一个的子串时，先换短的会留下长的那截尾巴 */
    fun redactable(secrets: Collection<String>): List<String> =
        secrets.map(String::trim).filter { it.length >= MIN_REDACT_LENGTH }.distinct().sortedByDescending { it.length }

    /** [secrets] 须先过 [redactable] */
    fun redact(text: String, secrets: List<String>): String =
        secrets.fold(KEYED.replace(text, "\$1$MASK")) { current, secret -> current.replace(secret, MASK) }
}
