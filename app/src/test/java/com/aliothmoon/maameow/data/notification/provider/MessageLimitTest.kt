package com.aliothmoon.maameow.data.notification.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageLimitTest {

    @Test
    fun keepTailLeavesShortTextAlone() {
        assertEquals("abc", "abc".keepTail(10))
    }

    @Test
    fun keepTailDropsDanglingLowSurrogate() {
        // 😀 是一对代理项，裁剪起点落在两者之间
        val text = "x".repeat(20) + "😀" + "y".repeat(8)
        val cut = text.keepTail(15)
        assertTrue(cut.length <= 15)
        assertFalse(cut.any { it.isSurrogate() })
        assertTrue(cut.endsWith("y".repeat(8)))
    }

    @Test
    fun keepTailUtf8LeavesShortTextAlone() {
        assertEquals("日志", "日志".keepTailUtf8(6))
    }

    @Test
    fun keepTailUtf8FitsByteBudget() {
        val text = "日志".repeat(2000) + "结尾摘要"
        val cut = text.keepTailUtf8(3000)
        assertTrue(cut.encodeToByteArray().size <= 3000)
        assertTrue(cut.endsWith("结尾摘要"))
    }

    @Test
    fun keepTailUtf8KeepsSurrogatePairsWhole() {
        val text = "😀".repeat(100)
        val cut = text.keepTailUtf8(50)
        assertTrue(cut.encodeToByteArray().size <= 50)
        val tail = cut.substringAfter("\n")
        assertTrue(tail.isNotEmpty())
        assertEquals(tail, "😀".repeat(tail.length / 2))
    }
}
