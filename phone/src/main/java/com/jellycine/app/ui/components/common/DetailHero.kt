package com.jellycine.app.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.jellycine.shared.util.image.JellyfinPosterImage

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
    0.0f to Color.Black.copy(alpha = 0.12f),
    0.24f to Color.Black.copy(alpha = 0.18f),
    0.58f to Color.Black.copy(alpha = 0.30f),
    0.84f to Color.Black.copy(alpha = 0.52f),
    1.0f to Color.Black.copy(alpha = 0.74f)
)

private val tabletDetailHeroBottomFadeGradient = arrayOf(
    0.0f to Color.Transparent,
    0.18f to Color.Black.copy(alpha = 0.14f),
    0.46f to Color.Black.copy(alpha = 0.32f),
    0.74f to Color.Black.copy(alpha = 0.56f),
    1.0f to Color.Black.copy(alpha = 0.82f)
)

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
            bottomFadeHeight = 220.dp,
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
    overlayGradient: Array<Pair<Float, Color>>? = null,
    bottomFadeGradient: Array<Pair<Float, Color>>? = null,
    onErrorStateChange: (Boolean) -> Unit = {},
    overlayContent: @Composable BoxScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val styleSpec = detailBackdropHeroStyleSpec(style)
    val resolvedBottomFadeHeight = bottomFadeHeight ?: styleSpec.bottomFadeHeight
    val resolvedImageAlignment = imageAlignment ?: styleSpec.imageAlignment
    val resolvedOverlayGradient = overlayGradient ?: styleSpec.overlayGradient
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
                        colorStops = resolvedOverlayGradient
                    )
                )
        )

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