package com.aliothmoon.maameow.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryUserIdTest {

    @Test
    fun `哈希稳定且看不出原值`() {
        val id = TelemetryUserId.hash("a1b2c3d4e5f60718")

        assertEquals(id, TelemetryUserId.hash("a1b2c3d4e5f60718"))
        assertNotEquals(id, TelemetryUserId.hash("a1b2c3d4e5f60719"))
        assertEquals(64, id.length)
        assertTrue(id.all { it in "0123456789abcdef" })
        assertTrue("a1b2c3d4e5f60718" !in id)
    }

    @Test
    fun `取不到或已知坏值的 ANDROID_ID 不用`() {
        assertNull(TelemetryUserId.usableAndroidId(null))
        assertNull(TelemetryUserId.usableAndroidId("  "))
        assertNull(TelemetryUserId.usableAndroidId("9774D56D682E549C"))
        assertEquals("a1b2", TelemetryUserId.usableAndroidId(" a1b2 "))
    }
}
