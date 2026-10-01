package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.data.resource.OperAvatarLoader
import org.koin.compose.koinInject

// 未拥有干员的色彩保留比例，对齐 WPF OperAvatarHelper.DesaturatedColorKeep
private val DesaturatedFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.35f) })

/**
 * 干员头像；加载中或资源缺失时留同尺寸空位，避免列表抖动
 *
 * @param operId 干员 id，如 char_002_amiya
 * @param desaturated 降低饱和度，用于未拥有的干员
 */
@Composable
fun OperAvatar(
    operId: String,
    modifier: Modifier = Modifier,
    desaturated: Boolean = false,
    loader: OperAvatarLoader = koinInject(),
) {
    // 首帧直接用缓存，免得滚回来先空一帧
    val avatar by produceState(initialValue = loader.peek(operId), operId) {
        if (value == null) value = loader.load(operId)
    }
    val shaped = modifier.clip(RoundedCornerShape(4.dp))
    val bitmap = avatar
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = shaped,
            colorFilter = if (desaturated) DesaturatedFilter else null,
        )
    } else {
        Box(modifier = shaped)
    }
}
