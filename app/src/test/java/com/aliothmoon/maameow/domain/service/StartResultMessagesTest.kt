package com.aliothmoon.maameow.domain.service

import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.utils.i18n.uiTextOf
import org.junit.Assert.assertEquals
import org.junit.Test

class StartResultMessagesTest {

    @Test
    fun notGranted_tellsUserToAuthorizeInsteadOfReinitResources() {
        val message = resolveStartResultMessage(
            MaaCompositionService.StartResult.RemoteAccessNotGranted(RemoteBackend.SHIZUKU)
        )

        assertEquals(uiTextOf(R.string.task_start_error_backend_not_granted, "Shizuku"), message)
    }

    @Test
    fun unavailable_keepsItsOwnMessage() {
        val message = resolveStartResultMessage(
            MaaCompositionService.StartResult.RemoteAccessUnavailable(RemoteBackend.ROOT)
        )

        assertEquals(uiTextOf(R.string.task_start_error_backend_unavailable, "Root"), message)
    }
}
