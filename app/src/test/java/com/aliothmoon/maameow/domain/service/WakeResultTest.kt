package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.constant.WakeUnlockResult
import com.aliothmoon.maameow.domain.service.WakeUnlockEngine.WakeResult
import org.junit.Assert.assertEquals
import org.junit.Test

class WakeResultTest {

    @Test
    fun knownRemoteCodesMapToThemselves() {
        assertEquals(WakeResult.OK, WakeResult.fromCode(WakeUnlockResult.OK))
        assertEquals(WakeResult.RECORD_BUSY, WakeResult.fromCode(WakeUnlockResult.RECORD_BUSY))
    }

    @Test
    fun unknownCodeIsNotReportedAsIpcFailure() {
        // 两端版本不一致时提权侧可能返回新码，不能说成「服务未连接」
        assertEquals(WakeResult.UNKNOWN, WakeResult.fromCode(99))
        // 本端专用的负码不是提权侧能返回的
        assertEquals(WakeResult.UNKNOWN, WakeResult.fromCode(WakeResult.IPC_FAILED.code))
        assertEquals(WakeResult.UNKNOWN, WakeResult.fromCode(WakeResult.TASK_RUNNING.code))
    }
}
