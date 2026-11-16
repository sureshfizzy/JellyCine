package com.jellycine.ui.components.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.jellycine.resources.Res
import com.jellycine.resources.cd_back_button
import org.jetbrains.compose.resources.stringResource

private fun compactProgress(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    compactDistancePx: Float
): Float {
    if (firstVisibleItemIndex > 0) return 1f
    if (compactDistancePx <= 0f) return 0f
    return (firstVisibleItemScrollOffset / compactDistancePx).coerceIn(0f, 1f)
}

@Composable
fun rememberCompactProgress(
    state: LazyListState,
    compactDistance: Dp
): Float {
    val density = LocalDensity.current
    val compactDistancePx = with(density) { compactDistance.toPx() }
    return remember(state, compactDistancePx) {
        derivedStateOf {
            compactProgress(
                firstVisibleItemIndex = state.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = state.firstVisibleItemScrollOffset,
                compactDistancePx = compactDistancePx
            )
        }
    }.value
}

@Composable
fun rememberCompactProgress(
    state: LazyGridState,
    compactDistance: Dp
): Float {
    val density = LocalDensity.current
    val compactDistancePx = with(density) { compactDistance.toPx() }
    return remember(state, compactDistancePx) {
        derivedStateOf {
            compactProgress(
                firstVisibleItemIndex = state.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = state.firstVisibleItemScrollOffset,
                compactDistancePx = compactDistancePx
            )
        }
    }.value
}

private fun compactChromeProgress(progress: Float): Float =
    ((progress - 0.08f) / 0.72f).coerceIn(0f, 1f)

fun Modifier.compactHeaderLogo(progress: Float): Modifier =
    graphicsLayer {
        alpha = 1f - (progress * 0.72f)
        translationY = 24.dp.toPx() * progress
        val scale = 1f - (progress * 0.08f)
        scaleX = scale
        scaleY = scale
    }

@Composable
fun CompactPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    includeStatusBarsPadding: Boolean = true,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 16.dp,
    titleFontSize: TextUnit = 24.sp,
    titleFontWeight: FontWeight = FontWeight.SemiBold,
    subtitleFontSize: TextUnit = 14.sp,
    centered: Boolean = false
) {
    val horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (includeStatusBarsPadding) Modifier.statusBarsPadding() else Modifier),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = horizontalAlignment
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = titleFontSize,
                fontWeight = titleFontWeight,
                textAlign = textAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            subtitle?.takeIf { it.isNotBlank() }?.let { subtitleText ->
                Text(
                    text = subtitleText,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = subtitleFontSize,
                    textAlign = textAlign,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun Modifier.compactChromeMotion(visibleProgress: Float): Modifier =
    graphicsLayer {
        alpha = visibleProgress
        translationY = -10.dp.toPx() * (1f - visibleProgress)
        val scale = 0.88f + (visibleProgress * 0.12f)
        scaleX = scale
        scaleY = scale
    }

@Composable
fun CompactTopChip(
    progress: Float,
    height: Dp,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    containerColor: Color = Color.Black.copy(alpha = 0.62f),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val visibleProgress = compactChromeProgress(progress)
    if (visibleProgress <= 0f) return

    Surface(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(height)
            .compactChromeMotion(visibleProgress)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = if (width != null) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxHeight()
            },
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

@Composable
fun CompactTopText(
    text: String,
    progress: Float,
    isTablet: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    alignEnd: Boolean = false,
    maxWidth: Dp? = if (alignEnd) null else if (isTablet) 220.dp else 148.dp,
    fontSize: TextUnit = if (isTablet) 14.sp else 12.sp,
    color: Color = Color.White.copy(alpha = if (alignEnd) 0.86f else 0.82f),
    fontWeight: FontWeight = FontWeight.SemiBold
) {
    val chipModifier = modifier
        .then(
            if (alignEnd) {
                Modifier.statusBarsPadding().padding(end = 16.dp, top = 10.dp)
            } else {
                Modifier.statusBarsPadding().padding(start = 16.dp, top = 10.dp)
            }
        )
        .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier)

    CompactTopChip(
        modifier = chipModifier,
        progress = progress,
        width = null,
        height = if (isTablet) 48.dp else 36.dp,
        shape = RoundedCornerShape(999.dp),
        onClick = onClick
    ) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = if (isTablet) 16.dp else 12.dp)
        )
    }
}

@Composable
fun AwardsCompactHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    Box(modifier = modifier.fillMaxWidth()) {
        CompactPageHeader(
            title = title,
            centered = true,
            titleFontSize = 20.sp,
            horizontalPadding = 52.dp,
            verticalPadding = 14.dp
        )
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 4.dp, top = 6.dp)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.cd_back_button),
                    tint = Color.White
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 16.dp, top = 10.dp)
        ) {
            trailing()
        }
    }
}