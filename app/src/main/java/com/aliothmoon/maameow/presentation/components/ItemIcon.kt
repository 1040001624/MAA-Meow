package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import com.aliothmoon.maameow.data.resource.ItemIconLoader
import org.koin.compose.koinInject

/**
 * 物品图标，对应 WPF ItemImageConverter；加载中或资源缺失时留同尺寸空位，避免布局抖动
 *
 * @param itemId 物品 id，如 30012
 */
@Composable
fun ItemIcon(
    itemId: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    loader: ItemIconLoader = koinInject(),
) {
    // 首帧直接用缓存，免得滚回来先空一帧
    val icon by produceState(initialValue = loader.peek(itemId), itemId) {
        if (value == null) value = loader.load(itemId)
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = contentDescription, modifier = modifier)
    } else {
        Box(modifier = modifier)
    }
}
