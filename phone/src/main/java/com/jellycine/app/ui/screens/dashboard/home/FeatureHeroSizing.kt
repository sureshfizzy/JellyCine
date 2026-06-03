package com.jellycine.app.ui.screens.dashboard.home

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jellycine.shared.preferences.Preferences

internal data class FeatureHeroSizing(
    val height: Dp,
    val logoHeight: Dp,
    val logoWidthFraction: Float,
    val logoFallbackHeight: Dp,
    val contentHorizontalPadding: Dp,
    val contentVerticalPadding: Dp,
    val contentSpacing: Dp,
    val bottomFadeHeight: Dp,
    val lowerGradientAlpha: Float,
    val bottomGradientAlpha: Float,
    val bottomEdgeHeight: Dp,
    val bottomEdgeAlpha: Float,
    val vignetteAlpha: Float,
    val backdropParallaxMultiplier: Float,
    val backdropScale: Float,
    val cardWidthFraction: Float,
    val cardCornerRadius: Dp,
    val carouselTopPadding: Dp,
    val carouselHorizontalPadding: Dp,
    val carouselItemSpacing: Dp
)

internal val FeatureHeroSizing.isCompact: Boolean
    get() = cardWidthFraction < 1f

private fun FeatureHeroSizing.viewportWidth(screenWidth: Dp): Dp {
    return screenWidth - (carouselHorizontalPadding * 2f)
}

internal fun FeatureHeroSizing.compactCardWidth(screenWidth: Dp): Dp? {
    if (!isCompact) return null

    val viewportWidth = viewportWidth(screenWidth)
    return (viewportWidth * cardWidthFraction)
        .coerceAtMost(height * 2.4f)
        .coerceAtMost(560.dp)
}

internal fun FeatureHeroSizing.initialCarouselIndex(infiniteStartIndex: Int): Int {
    return if (isCompact && infiniteStartIndex > 0) infiniteStartIndex - 1 else infiniteStartIndex
}

internal fun FeatureHeroSizing.initialCarouselScrollOffsetPx(
    screenWidth: Dp,
    cardWidth: Dp?,
    density: Density
): Int {
    if (!isCompact || cardWidth == null) return 0

    return with(density) {
        val availableWidth = viewportWidth(screenWidth)
        val activeCardStart = if (availableWidth >= 520.dp) {
            ((availableWidth - cardWidth) / 2f).coerceAtLeast(32.dp)
        } else {
            32.dp
        }

        (cardWidth + carouselItemSpacing - activeCardStart)
            .roundToPx()
            .coerceAtLeast(0)
    }
}

internal fun featureHeroSizing(
    heightMode: String,
    screenHeight: Dp
): FeatureHeroSizing {
    return when (heightMode) {
        Preferences.FEATURE_CAROUSEL_HEIGHT_SMALL -> FeatureHeroSizing(
            height = (screenHeight * 0.25f).coerceIn(180.dp, 260.dp),
            logoHeight = 48.dp,
            logoWidthFraction = 0.56f,
            logoFallbackHeight = 22.dp,
            contentHorizontalPadding = 16.dp,
            contentVerticalPadding = 12.dp,
            contentSpacing = 5.dp,
            bottomFadeHeight = 24.dp,
            lowerGradientAlpha = 0.24f,
            bottomGradientAlpha = 0.38f,
            bottomEdgeHeight = 16.dp,
            bottomEdgeAlpha = 0.58f,
            vignetteAlpha = 0.10f,
            backdropParallaxMultiplier = 0f,
            backdropScale = 1.03f,
            cardWidthFraction = 0.80f,
            cardCornerRadius = 28.dp,
            carouselTopPadding = 108.dp,
            carouselHorizontalPadding = 0.dp,
            carouselItemSpacing = 10.dp
        )
        Preferences.FEATURE_CAROUSEL_HEIGHT_MEDIUM -> FeatureHeroSizing(
            height = (screenHeight * 0.48f).coerceIn(320.dp, 520.dp),
            logoHeight = 76.dp,
            logoWidthFraction = 0.72f,
            logoFallbackHeight = 26.dp,
            contentHorizontalPadding = 16.dp,
            contentVerticalPadding = 16.dp,
            contentSpacing = 6.dp,
            bottomFadeHeight = 46.dp,
            lowerGradientAlpha = 0.28f,
            bottomGradientAlpha = 0.46f,
            bottomEdgeHeight = 22.dp,
            bottomEdgeAlpha = 0.64f,
            vignetteAlpha = 0.22f,
            backdropParallaxMultiplier = 0.58f,
            backdropScale = 1.0f,
            cardWidthFraction = 1f,
            cardCornerRadius = 0.dp,
            carouselTopPadding = 0.dp,
            carouselHorizontalPadding = 0.dp,
            carouselItemSpacing = 0.dp
        )
        else -> FeatureHeroSizing(
            height = (screenHeight * 0.76f).coerceIn(520.dp, 820.dp),
            logoHeight = 96.dp,
            logoWidthFraction = 0.82f,
            logoFallbackHeight = 30.dp,
            contentHorizontalPadding = 18.dp,
            contentVerticalPadding = 18.dp,
            contentSpacing = 8.dp,
            bottomFadeHeight = 64.dp,
            lowerGradientAlpha = 0.48f,
            bottomGradientAlpha = 0.72f,
            bottomEdgeHeight = 18.dp,
            bottomEdgeAlpha = 0.72f,
            vignetteAlpha = 0.32f,
            backdropParallaxMultiplier = 0.40f,
            backdropScale = 1.14f,
            cardWidthFraction = 1f,
            cardCornerRadius = 0.dp,
            carouselTopPadding = 0.dp,
            carouselHorizontalPadding = 0.dp,
            carouselItemSpacing = 0.dp
        )
    }
}