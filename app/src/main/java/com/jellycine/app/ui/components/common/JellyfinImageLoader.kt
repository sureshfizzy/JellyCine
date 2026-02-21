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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.*
import com.jellycine.app.util.image.ImageUrlViewModel

@Composable
fun JellyfinImageLoader(
    itemId: String?,
    imageType: String = "Primary",
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Int = 8,
    showShimmer: Boolean = true,
    viewModel: ImageUrlViewModel = hiltViewModel()
) {
    var isLoading by remember(itemId) { mutableStateOf(true) }
    var hasError by remember(itemId) { mutableStateOf(false) }
    var isSuccess by remember(itemId) { mutableStateOf(false) }

    val imageUrl by viewModel.getImageUrl(itemId ?: "", imageType).collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current

    Box(modifier = modifier) {
        if (!imageUrl.isNullOrEmpty() && !hasError) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .allowHardware(true)
                    .allowRgb565(true)
                    .crossfade(100)
                    .placeholder(null)
                    .error(null)
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
        if ((isLoading || imageUrl == null) && showShimmer && !isSuccess) {
            ShimmerEffect(
                modifier = Modifier.fillMaxSize(),
                cornerRadius = cornerRadius.toFloat(),
                shape = RoundedCornerShape(cornerRadius.dp)
            )
        }

        // Show placeholder on error or null URL
        if (hasError || imageUrl.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

