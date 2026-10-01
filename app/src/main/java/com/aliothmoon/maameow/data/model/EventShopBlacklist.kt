package com.aliothmoon.maameow.data.model

import androidx.annotation.StringRes
import com.aliothmoon.maameow.R

/**
 * 牛杂「活动商店」的常用黑名单商品，对应 WPF ToolboxViewModel.EventShopBlackListItems
 *
 * [itemId] 只用于落盘；下发给 Core 的是按游戏客户端语言取的商品名关键词
 */
enum class EventShopPreset(
    val itemId: String,
    @param:StringRes val labelRes: Int,
) {
    DATA_SUPPLEMENT_INSTRUMENT("mod_update_token_2", R.string.panel_mini_game_event_shop_data_supplement_instrument),
    DATA_SUPPLEMENT_STICK("mod_update_token_1", R.string.panel_mini_game_event_shop_data_supplement_stick),
    LMD("4001", R.string.panel_mini_game_event_shop_lmd),
    FURNITURE_PART("3401", R.string.panel_mini_game_event_shop_furniture_part),
    ;

    companion object {
        fun fromItemId(itemId: String): EventShopPreset? = entries.firstOrNull { it.itemId == itemId }
    }
}

object EventShopBlacklist {

    /** 自定义商品关键词的分隔符，与信用商店黑名单一致 */
    const val CUSTOM_SEPARATOR = ';'

    // 与信用商店一样直接提交关键词，匹配语言由游戏客户端而非界面语言决定
    private val KEYWORDS_BY_CLIENT = mapOf(
        "YoStarEN" to listOf("Data Supplement Instrument", "Data Supplement Stick", "LMD", "Furniture Part"),
        "YoStarJP" to listOf("データ補完マシン", "データ補完チップ", "龍門幣", "家具"),
        "YoStarKR" to listOf("데이터 리더기", "데이터 메모리", "용문폐", "가구 부품"),
        "txwy" to listOf("數據增補儀", "數據增補條", "龍門幣", "傢俱"),
    )
    private val DEFAULT_KEYWORDS = listOf("数据增补仪", "数据增补条", "龙门币", "家具")

    fun parsePresets(stored: String): Set<EventShopPreset> =
        stored.split(',').mapNotNullTo(linkedSetOf()) { EventShopPreset.fromItemId(it.trim()) }

    fun formatPresets(presets: Set<EventShopPreset>): String =
        EventShopPreset.entries.filter { it in presets }.joinToString(",") { it.itemId }

    /** 下发给 Core 的关键词：自定义在前，常用商品按固定顺序在后，去重；为空表示不启用黑名单 */
    fun keywords(presets: Set<EventShopPreset>, custom: String, clientType: String): List<String> {
        val presetKeywords = KEYWORDS_BY_CLIENT[clientType] ?: DEFAULT_KEYWORDS
        return buildList {
            custom.split(CUSTOM_SEPARATOR).mapNotNullTo(this) { it.trim().ifEmpty { null } }
            EventShopPreset.entries.filter { it in presets }.mapTo(this) { presetKeywords[it.ordinal] }
        }.distinct()
    }
}
