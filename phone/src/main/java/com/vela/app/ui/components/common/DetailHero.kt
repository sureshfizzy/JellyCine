package com.vela.app.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vela.shared.ui.components.common.WatchedActionButton
import com.vela.shared.util.image.JellyfinPosterImage

enum class DetailBackdropHeroStyle {
    Default,
    TabletBackdrop
}

private data class DetailBackdropHeroStyleSpec(
    val imageAlignment: Alignment,
    val bottomFadeHeight: Dp,
    val overlayGradient: Array<Pair<Float, Color>>,
    val bottomFadeGradient: Array<Pair<Float, Color>>
)

private val detailHeroOverlayGradient = arrayOf(
    0.0f to Color.Transparent,
    0.74f to Color.Transparent,
    0.84f to Color.Black.copy(alpha = 0.04f),
    0.91f to Color.Black.copy(alpha = 0.10f),
    0.96f to Color.Black.copy(alpha = 0.18f),
    1.0f to Color.Black.copy(alpha = 0.28f)
)

private val detailHeroBottomFadeGradient = arrayOf(
    0.0f to Color.Transparent,
    0.28f to Color.Transparent,
    0.52f to Color.Black.copy(alpha = 0.03f),
    0.70f to Color.Black.copy(alpha = 0.09f),
    0.84f to Color.Black.copy(alpha = 0.18f),
    0.93f to Color.Black.copy(alpha = 0.34f),
    1.0f to Color.Black.copy(alpha = 0.58f)
)

private val tabletDetailHeroOverlayGradient = arrayOf(
    0.0f to Color.Black.copy(alpha = 0.28f),
    0.22f to Color.Black.copy(alpha = 0.22f),
    0.52f to Color.Black.copy(alpha = 0.38f),
    0.78f to Color.Black.copy(alpha = 0.58f),
    1.0f to Color.Black.copy(alpha = 0.82f)
)

private val tabletDetailHeroBottomFadeGradient = arrayOf(
    0.0f to Color.Transparent,
    0.14f to Color.Black.copy(alpha = 0.18f),
    0.38f to Color.Black.copy(alpha = 0.40f),
    0.68f to Color.Black.copy(alpha = 0.68f),
    1.0f to Color.Black.copy(alpha = 0.92f)
)

private val tabletDetailHeroSideScrim = arrayOf(
    0.0f to Color.Black.copy(alpha = 0.58f),
    0.38f to Color.Black.copy(alpha = 0.22f),
    0.68f to Color.Black.copy(alpha = 0.06f),
    1.0f to Color.Transparent
)

private fun overlayStartPos(startFraction: Float): Array<Pair<Float, Color>> {
    val clampedStartFraction = startFraction.coerceIn(0f, 1f)
    return arrayOf(
        0.0f to Color.Transparent,
        clampedStartFraction to Color.Transparent,
        (clampedStartFraction + 0.06f).coerceIn(0f, 1f) to Color.Black.copy(alpha = 0.08f),
        (clampedStartFraction + 0.14f).coerceIn(0f, 1f) to Color.Black.copy(alpha = 0.18f),
        (clampedStartFraction + 0.24f).coerceIn(0f, 1f) to Color.Black.copy(alpha = 0.28f),
        1.0f to Color.Black.copy(alpha = 0.34f)
    )
}

private fun detailBackdropHeroStyleSpec(style: DetailBackdropHeroStyle): DetailBackdropHeroStyleSpec {
    return when (style) {
        DetailBackdropHeroStyle.Default -> DetailBackdropHeroStyleSpec(
            imageAlignment = Alignment.Center,
            bottomFadeHeight = 124.dp,
            overlayGradient = detailHeroOverlayGradient,
            bottomFadeGradient = detailHeroBottomFadeGradient
        )
        DetailBackdropHeroStyle.TabletBackdrop -> DetailBackdropHeroStyleSpec(
            imageAlignment = BiasAlignment(0f, 0.06f),
            bottomFadeHeight = 280.dp,
            overlayGradient = tabletDetailHeroOverlayGradient,
            bottomFadeGradient = tabletDetailHeroBottomFadeGradient
        )
    }
}

@Composable
fun DetailBackdropHero(
    imageUrl: String?,
    contentDescription: String?,
    heroHeight: Dp,
    modifier: Modifier = Modifier,
    style: DetailBackdropHeroStyle = DetailBackdropHeroStyle.Default,
    bottomFadeHeight: Dp? = null,
    fallbackColor: Color = Color(0xFF20202A),
    imageAlignment: Alignment? = null,
    contentFadeStartFraction: Float? = null,
    overlayGradient: Array<Pair<Float, Color>>? = null,
    bottomFadeGradient: Array<Pair<Float, Color>>? = null,
    onErrorStateChange: (Boolean) -> Unit = {},
    overlayContent: @Composable BoxScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val styleSpec = detailBackdropHeroStyleSpec(style)
    val resolvedBottomFadeHeight = bottomFadeHeight ?: styleSpec.bottomFadeHeight
    val resolvedImageAlignment = imageAlignment ?: styleSpec.imageAlignment
    val overlayGrad = when {
        contentFadeStartFraction != null -> overlayStartPos(contentFadeStartFraction)
        overlayGradient != null -> overlayGradient
        else -> styleSpec.overlayGradient
    }
    val resolvedBottomFadeGradient = bottomFadeGradient ?: styleSpec.bottomFadeGradient

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
    ) {
        if (imageUrl != null) {
            JellyfinPosterImage(
                imageUrl = imageUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                context = context,
                contentScale = ContentScale.Crop,
                alignment = resolvedImageAlignment,
                onErrorStateChange = onErrorStateChange
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(fallbackColor)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = overlayGrad
                    )
                )
        )

        if (style == DetailBackdropHeroStyle.TabletBackdrop) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = tabletDetailHeroSideScrim
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(resolvedBottomFadeHeight)
                .background(
                    Brush.verticalGradient(
                        colorStops = resolvedBottomFadeGradient
                    )
                )
        )

        overlayContent()
    }
}

@Composable
fun BoxScope.DetailHeroCastButtonOverlay(
    showWatchedButton: Boolean,
    isWatched: Boolean,
    onWatchedClick: () -> Unit,
    onCastButtonClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(top = 12.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showWatchedButton) {
            WatchedActionButton(
                isWatched = isWatched,
                onClick = onWatchedClick
            )
        }
        ScreenCastButton(onConnectedClick = onCastButtonClick)
    }
}