package com.jellycine.app.util.image

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.request.CachePolicy
import android.content.Context
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy

// Skeleton loading animation with optional placeholder
@Composable
fun ImageSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    showPlaceholder: Boolean = false,
    placeholderRes: Int? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .background(
                color = Color(0xFF1A1A1A).copy(alpha = alpha),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (showPlaceholder && placeholderRes != null) {
            Icon(
                painter = painterResource(id = placeholderRes),
                contentDescription = "Placeholder",
                modifier = Modifier.fillMaxSize(0.8f),
                tint = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
fun JellyfinPosterImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    context: Context,
    onLoadingStateChange: (Boolean) -> Unit = {},
    onErrorStateChange: (Boolean) -> Unit = {}
) {
    var imageState by remember(imageUrl) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    // Notify parent about loading and error state changes
    LaunchedEffect(imageState) {
        when (imageState) {
            is AsyncImagePainter.State.Loading -> {
                onLoadingStateChange(true)
                onErrorStateChange(false)
            }
            is AsyncImagePainter.State.Success -> {
                onLoadingStateChange(false)
                onErrorStateChange(false)
            }
            is AsyncImagePainter.State.Error -> {
                onLoadingStateChange(false)
                onErrorStateChange(true)
            }
            else -> {
                onLoadingStateChange(true)
                onErrorStateChange(false)
            }
        }
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(100)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .allowRgb565(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            renderEffect = null
        },
        contentScale = contentScale,
        onState = { state ->
            imageState = state
        }
    )
}
