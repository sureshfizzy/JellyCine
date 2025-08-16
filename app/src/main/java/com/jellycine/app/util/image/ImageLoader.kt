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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jellycine.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import com.jellycine.app.ui.screens.dashboard.ShimmerEffect

// Skeleton loading animation with optional placeholder
@Composable
fun ImageSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    showPlaceholder: Boolean = false,
    placeholderRes: Int? = null
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        ShimmerEffect(
            modifier = Modifier.fillMaxSize(),
            shape = shape
        )

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
            .crossfade(50)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .allowRgb565(true)
            .placeholder(null)
            .error(null)
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

// ImageUrlViewModel
@HiltViewModel
class ImageUrlViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    fun getImageUrl(
        itemId: String,
        imageType: String = "Primary",
        width: Int? = null,
        height: Int? = null,
        quality: Int? = 90
    ): Flow<String?> {
        return mediaRepository.getImageUrl(
            itemId = itemId,
            imageType = imageType,
            width = width,
            height = height,
            quality = quality
        )
    }
}

// Composable functions
@Composable
fun rememberImageUrl(
    itemId: String?,
    imageType: String = "Primary",
    mediaRepository: MediaRepository
): String? {
    val imageUrl by mediaRepository.getImageUrl(
        itemId = itemId ?: "",
        imageType = imageType
    ).collectAsStateWithLifecycle(initialValue = null)

    return imageUrl
}

@Composable
fun rememberImageUrl(
    itemId: String?,
    imageType: String = "Primary",
    viewModel: ImageUrlViewModel = hiltViewModel()
): String? {
    val imageUrl by viewModel.getImageUrl(
        itemId = itemId ?: "",
        imageType = imageType
    ).collectAsStateWithLifecycle(initialValue = null)

    return imageUrl
}
