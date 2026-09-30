package com.aliothmoon.maameow.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.ripple
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val IconRippleRadius = 16.dp

/** 小图标点击：圆形涟漪略大于图标，同 IconButton 手感 */
fun Modifier.iconClickable(onClickLabel: String? = null, onClick: () -> Unit): Modifier =
    clickable(
        interactionSource = null,
        indication = ripple(bounded = false, radius = IconRippleRadius),
        onClickLabel = onClickLabel,
        role = Role.Button,
        onClick = onClick,
    )
