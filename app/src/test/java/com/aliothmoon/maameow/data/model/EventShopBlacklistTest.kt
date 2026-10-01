package com.aliothmoon.maameow.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EventShopBlacklistTest {

    @Test
    fun presets_roundTripThroughStoredIds_andDropUnknownIds() {
        val stored = EventShopBlacklist.formatPresets(setOf(EventShopPreset.LMD, EventShopPreset.DATA_SUPPLEMENT_STICK))

        assertEquals("mod_update_token_1,4001", stored)
        assertEquals(
            setOf(EventShopPreset.DATA_SUPPLEMENT_STICK, EventShopPreset.LMD),
            EventShopBlacklist.parsePresets("$stored,removed_item"),
        )
        assertEquals(emptySet<EventShopPreset>(), EventShopBlacklist.parsePresets(""))
    }

    @Test
    fun custom_acceptsFullWidthSemicolon() {
        assertEquals(
            listOf("碳", "家具", "技巧概要"),
            EventShopBlacklist.keywords(emptySet(), "碳；家具;技巧概要；", "Official"),
        )
    }
}
