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
    private val keyword: String,
    private val keywordByClient: Map<String, String>,
) {
    DATA_SUPPLEMENT_INSTRUMENT(
        "mod_update_token_2",
        R.string.panel_mini_game_event_shop_data_supplement_instrument,
        "数据增补仪",
        mapOf(
            "YoStarEN" to "Data Supplement Instrument",
            "YoStarJP" to "データ補完マシン",
            "YoStarKR" to "데이터 리더기",
            "txwy" to "數據增補儀",
        ),
    ),
    DATA_SUPPLEMENT_STICK(
        "mod_update_token_1",
        R.string.panel_mini_game_event_shop_data_supplement_stick,
        "数据增补条",
        mapOf(
            "YoStarEN" to "Data Supplement Stick",
            "YoStarJP" to "データ補完チップ",
            "YoStarKR" to "데이터 메모리",
            "txwy" to "數據增補條",
        ),
    ),
    LMD(
        "4001",
        R.string.panel_mini_game_event_shop_lmd,
        "龙门币",
        mapOf(
            "YoStarEN" to "LMD",
            "YoStarJP" to "龍門幣",
            "YoStarKR" to "용문폐",
            "txwy" to "龍門幣",
        ),
    ),
    FURNITURE_PART(
        "3401",
        R.string.panel_mini_game_event_shop_furniture_part,
        "家具",
        mapOf(
            "YoStarEN" to "Furniture Part",
            "YoStarJP" to "家具",
            "YoStarKR" to "가구 부품",
            "txwy" to "傢俱",
        ),
    ),
    ;

    fun keyword(clientType: String): String = keywordByClient[clientType] ?: keyword
}

object EventShopBlacklist {

    private const val CUSTOM_SEPARATOR = ';'

    fun parsePresets(stored: String): Set<EventShopPreset> {
        val ids = stored.split(',').map { it.trim() }
        return EventShopPreset.entries.filterTo(linkedSetOf()) { it.itemId in ids }
    }

    fun formatPresets(presets: Set<EventShopPreset>): String =
        EventShopPreset.entries.filter { it in presets }.joinToString(",") { it.itemId }

    /** 下发给 Core 的关键词：自定义在前，常用商品按固定顺序在后，去重；为空表示不启用黑名单 */
    fun keywords(presets: Set<EventShopPreset>, custom: String, clientType: String): List<String> =
        buildList {
            // 中文输入法打出的全角分号也认，对齐 WPF EventShopBlackList
            custom.replace('；', CUSTOM_SEPARATOR).split(CUSTOM_SEPARATOR)
                .mapNotNullTo(this) { it.trim().ifEmpty { null } }
            EventShopPreset.entries.filter { it in presets }.mapTo(this) { it.keyword(clientType) }
        }.distinct()
}
