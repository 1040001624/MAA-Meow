package com.aliothmoon.maameow.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryRunTagsTest {

    @Test
    fun `Shizuku 身份按可用、授权、uid 依次判`() {
        assertEquals("unavailable", TelemetryRunTags.shizukuIdentity(available = false, granted = false, uid = null))
        assertEquals("not_granted", TelemetryRunTags.shizukuIdentity(available = true, granted = false, uid = 2000))
        assertEquals("unknown", TelemetryRunTags.shizukuIdentity(available = true, granted = true, uid = null))
        assertEquals("root", TelemetryRunTags.shizukuIdentity(available = true, granted = true, uid = 0))
        assertEquals("adb", TelemetryRunTags.shizukuIdentity(available = true, granted = true, uid = 2000))
    }
}
