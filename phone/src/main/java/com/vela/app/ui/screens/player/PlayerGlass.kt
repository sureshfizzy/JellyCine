package com.vela.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val PlayerChromeInset = 18.dp
internal val PlayerChromeTopGap = 8.dp
internal val PlayerChromeBottomGap = 12.dp
internal val PlayerGlassButtonSize = 44.dp
internal val PlayerGlassSeekSize = 48.dp
internal val PlayerGlassPlaySize = 68.dp

private val GlassFill = Color(0xCC18181A)
private val GlassBorder = Color.White.copy(alpha = 0.16f)
private val GlassSheen = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.14f),
        Color.White.copy(alpha = 0.04f),
        Color.Transparent
    )
)

@Composable
internal fun PlayerGlass(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .border(0.6.dp, GlassBorder, shape),
        contentAlignment = contentAlignment
    ) {
        Box(
            Modifier
                .matchParentSize()
                // 静态高光保留玻璃层次，同时避免每个控件逐帧执行独立 blur 离屏渲染。
                .background(GlassSheen)
        )
        Box(
            Modifier
                .matchParentSize()
                .background(GlassFill)
        )
        content()
    }
}

@Composable
internal fun PlayerGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = PlayerGlassButtonSize,
    shape: Shape = CircleShape,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    PlayerGlass(
        modifier = modifier.size(size),
        shape = shape
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = Color.White),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
internal fun PlayerChromeIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(40.dp),
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.clickable(
            enabled = enabled,
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = true, color = Color.White),
            onClick = onClick
        ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
