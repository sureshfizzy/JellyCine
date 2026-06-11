package com.jellycine.shared.ui.components.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerEffect(
    modifier: Modifier = Modifier,
    cornerRadius: Float = 12f,
    shape: Shape = RoundedCornerShape(cornerRadius.dp)
) {
    val alpha by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Canvas(
        modifier = modifier.graphicsLayer(
            compositingStrategy = CompositingStrategy.Offscreen
        )
    ) {
        drawRoundRect(
            color = Color(0xFF1A1A1A).copy(alpha = alpha),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )
    }
}

@Composable
fun ShimmerPosterCard(
    modifier: Modifier = Modifier,
    width: Dp = 112.dp,
    height: Dp = 214.dp,
    posterHeight: Dp = 166.dp
) {
    Column(
        modifier = modifier
            .width(width)
            .height(height)
    ) {
        ShimmerEffect(
            modifier = Modifier
                .fillMaxWidth()
                .height(posterHeight),
            cornerRadius = 16f
        )
        Spacer(modifier = Modifier.height(8.dp))
        ShimmerEffect(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(13.dp),
            cornerRadius = 6f
        )
        Spacer(modifier = Modifier.height(6.dp))
        ShimmerEffect(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(11.dp),
            cornerRadius = 5f
        )
    }
}

@Composable
fun ShimmerPosterRail(
    modifier: Modifier = Modifier,
    itemCount: Int = 5
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        ShimmerEffect(
            modifier = Modifier
                .padding(start = 16.dp)
                .width(160.dp)
                .height(20.dp),
            cornerRadius = 6f
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            userScrollEnabled = false
        ) {
            items(itemCount) { ShimmerPosterCard() }
        }
    }
}