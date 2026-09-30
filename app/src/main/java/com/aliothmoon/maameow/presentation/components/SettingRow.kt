package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.aliothmoon.maameow.theme.MaaDesignTokens

/** 卡片水平内边距：行按它外扩，涟漪铺满卡片 */
val LocalSettingRowBleed = staticCompositionLocalOf { 0.dp }

/** 横向外扩 [bleed]，占位宽度不变 */
fun Modifier.horizontalBleed(bleed: Dp): Modifier =
    if (bleed == 0.dp) this else layout { measurable, constraints ->
        val extra = (bleed * 2).roundToPx()
        val placeable = measurable.measure(constraints.offset(horizontal = extra))
        layout((placeable.width - extra).coerceAtLeast(0), placeable.height) {
            placeable.place(-extra / 2, 0)
        }
    }

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    verticalPadding: Dp = MaaDesignTokens.Spacing.listItemVertical,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val bleed = LocalSettingRowBleed.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalBleed(bleed)
            .then(
                if (onClick != null) Modifier.clickable(
                    enabled = enabled, onClick = onClick
                ) else Modifier
            )
            .padding(horizontal = bleed, vertical = verticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = if (trailing != null) {
                Modifier
                    .weight(1f)
                    .padding(end = MaaDesignTokens.Spacing.lg)
            } else {
                Modifier
            },
            verticalArrangement = Arrangement.spacedBy(MaaDesignTokens.Spacing.rowTitleGap),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) titleColor else titleColor.copy(alpha = 0.6f),
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) descriptionColor else descriptionColor.copy(alpha = 0.6f),
                )
            }
        }
        trailing?.invoke()
    }
}
