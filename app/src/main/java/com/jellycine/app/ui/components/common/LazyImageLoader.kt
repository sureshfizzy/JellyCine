package com.jellycine.app.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.request.CachePolicy

@Composable
fun LazyImageLoader(
    imageUrl: String?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Int = 8,
    showShimmer: Boolean = true
) {
    var isLoading by remember(imageUrl) { mutableStateOf(true) }
    var hasError by remember(imageUrl) { mutableStateOf(false) }
    var isSuccess by remember(imageUrl) { mutableStateOf(false) }

    val context = LocalContext.current

    Box(modifier = modifier) {
        if (imageUrl != null && !hasError) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .crossfade(200)
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

        // Show shimmer while loading (only if not successful)
        if (isLoading && showShimmer && !isSuccess) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius.dp))
                    .shimmer()
            )
        }

        // Show placeholder on error or null URL
        if (hasError || imageUrl == null) {
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
