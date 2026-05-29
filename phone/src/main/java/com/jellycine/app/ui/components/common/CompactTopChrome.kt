package com.jellycine.app.ui.components.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jellycine.shared.util.image.JellyfinPosterImage

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
fun CompactTopLogo(
    imageUrl: String,
    contentDescription: String?,
    progress: Float,
    isTablet: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    CompactTopChip(
        modifier = modifier
            .statusBarsPadding()
            .padding(start = 16.dp, top = 10.dp),
        progress = progress,
        width = if (isTablet) 122.dp else 92.dp,
        height = if (isTablet) 48.dp else 36.dp,
        onClick = onClick
    ) {
        JellyfinPosterImage(
            imageUrl = imageUrl,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            context = context,
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center
        )
    }
}