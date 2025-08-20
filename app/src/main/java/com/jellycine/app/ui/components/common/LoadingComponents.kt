package com.jellycine.app.ui.components.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerEffect(
    modifier: Modifier = Modifier,
    cornerRadius: Float = 8f,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(cornerRadius.dp)
) {
    val alpha by rememberInfiniteTransition(label = "shimmer_alpha").animateFloat(
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

fun Modifier.shimmer(): Modifier = composed {
    val alpha by rememberInfiniteTransition(label = "shimmer_alpha").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    background(Color(0xFF1A1A1A).copy(alpha = alpha))
}

@Composable
fun FeatureItemSkeleton(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(300.dp)
            .height(170.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shimmer()
        )
    }
}

@Composable
fun MediaItemSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(120.dp)
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(8.dp))
                .shimmer()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmer()
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmer()
        )
    }
}

@Composable
fun ContinueWatchingSkeleton(
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(5) {
            MediaItemSkeleton()
        }
    }
}

@Composable
fun FeaturedContentSkeleton(
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(3) {
            FeatureItemSkeleton()
        }
    }
}

@Composable
fun GenreRowSkeleton(
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(6) {
            MediaItemSkeleton()
        }
    }
}

@Composable
fun DashboardSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Featured content skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .shimmer()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Continue watching skeleton
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(20.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmer()
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        ContinueWatchingSkeleton()
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Genre sections skeleton
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(20.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            GenreRowSkeleton()
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
