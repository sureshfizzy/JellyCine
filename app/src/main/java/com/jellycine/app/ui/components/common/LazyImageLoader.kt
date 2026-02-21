package com.jellycine.app.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.*

@Composable
fun LazyImageLoader(
    imageUrl: String?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Int = 8,
    showShimmer: Boolean = true,
    enablePreloading: Boolean = true,
    blurImageUrl: String? = null
) {
    var isLoading by remember(imageUrl) { mutableStateOf(true) }
    var hasError by remember(imageUrl) { mutableStateOf(false) }
    var isSuccess by remember(imageUrl) { mutableStateOf(false) }

    val context = LocalContext.current

    Box(modifier = modifier) {
        if (!blurImageUrl.isNullOrEmpty() && !isSuccess && !hasError) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(blurImageUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .allowHardware(true)
                    .allowRgb565(true)
                    .crossfade(0)
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius.dp))
                    .blur(radius = 8.dp),
                contentScale = contentScale
            )
        }

        if (imageUrl != null && !hasError) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .allowHardware(true)
                    .allowRgb565(true)
                    .crossfade(if (blurImageUrl != null) 300 else 150)
                    .size(coil3.size.Size.ORIGINAL)
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius.dp)),
                contentScale = contentScale,
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Loading -> {
                            isLoading = true
                            hasError = false
                            isSuccess = false
                        }
                        is AsyncImagePainter.State.Success -> {
                            isLoading = false
                            hasError = false
                            isSuccess = true
                        }
                        is AsyncImagePainter.State.Error -> {
                            isLoading = false
                            hasError = true
                            isSuccess = false
                        }
                        else -> {
                            isLoading = true
                            hasError = false
                            isSuccess = false
                        }
                    }
                }
            )
        }

        // Show shimmer while loading
        if (isLoading && showShimmer && !isSuccess && blurImageUrl.isNullOrEmpty()) {
            ShimmerEffect(
                modifier = Modifier.fillMaxSize(),
                cornerRadius = cornerRadius.toFloat(),
                shape = RoundedCornerShape(cornerRadius.dp)
            )
        }

        // Show placeholder on error or null URL
        if ((hasError || imageUrl == null) && blurImageUrl.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

@Composable
fun OptimizedPosterImage(
    imageUrl: String?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    LazyImageLoader(
        imageUrl = imageUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        cornerRadius = 8,
        showShimmer = true
    )
}

@Composable
fun OptimizedBackdropImage(
    imageUrl: String?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    LazyImageLoader(
        imageUrl = imageUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        cornerRadius = 12,
        showShimmer = true
    )
}

@Composable
fun ProgressiveImageLoader(
    imageUrl: String?,
    lowQualityImageUrl: String? = null,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Int = 8
) {
    var isHighQualityLoaded by remember(imageUrl) { mutableStateOf(false) }
    var hasError by remember(imageUrl) { mutableStateOf(false) }

    val context = LocalContext.current

    Box(modifier = modifier) {
        // Low quality image (loads first)
        if (lowQualityImageUrl != null && !isHighQualityLoaded && !hasError) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(lowQualityImageUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .allowHardware(true)
                    .allowRgb565(true)
                    .size(coil3.size.Size(100, 150))
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius.dp)),
                contentScale = contentScale,
                alpha = 0.8f
            )
        }

        if (imageUrl != null && !hasError) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .allowHardware(true)
                    .allowRgb565(true)
                    .crossfade(300)
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius.dp)),
                contentScale = contentScale,
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> {
                            isHighQualityLoaded = true
                            hasError = false
                        }
                        is AsyncImagePainter.State.Error -> {
                            hasError = true
                        }
                        else -> { /* Loading states */ }
                    }
                }
            )
        }

        if (!isHighQualityLoaded && lowQualityImageUrl == null && !hasError) {
            ShimmerEffect(
                modifier = Modifier.fillMaxSize(),
                cornerRadius = cornerRadius.toFloat(),
                shape = RoundedCornerShape(cornerRadius.dp)
            )
        }

        // Show placeholder on error or null URL
        if (hasError || (imageUrl == null && lowQualityImageUrl == null)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

