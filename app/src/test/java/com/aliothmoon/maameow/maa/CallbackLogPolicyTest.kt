package com.aliothmoon.maameow.maa

import org.junit.Assert.assertEquals
import org.junit.Test

class CallbackLogPolicyTest {

    @Test
    fun onlySubTaskStartAndCompletedAreSkipped() {
        val skipped = AsstMsg.entries.filterNot { CallbackLogPolicy.shouldLog(it.value) }

        assertEquals(listOf(AsstMsg.SubTaskStart, AsstMsg.SubTaskCompleted), skipped)
    }
}
