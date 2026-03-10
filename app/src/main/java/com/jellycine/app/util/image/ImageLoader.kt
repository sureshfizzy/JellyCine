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
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.*
import android.content.Context
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jellycine.app.preferences.Preferences
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import com.jellycine.app.ui.components.common.ShimmerEffect

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
    alignment: Alignment = Alignment.Center,
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
        alignment = alignment,
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
        quality: Int? = 90,
        enableImageEnhancers: Boolean = true
    ): Flow<String?> {
        return mediaRepository.getImageUrl(
            itemId = itemId,
            imageType = imageType,
            width = width,
            height = height,
            quality = quality,
            enableImageEnhancers = enableImageEnhancers
        )
    }
}

// Composable functions
@Composable
fun disableEmbyPosterEnhancers(): Boolean {
    val context = LocalContext.current
    val preferences = remember(context) { Preferences(context) }
    val authRepository = remember(context) { AuthRepositoryProvider.getInstance(context) }
    val currentServerType by authRepository.getServerType().collectAsStateWithLifecycle(initialValue = null)
    val disablePosterEnhancersToggleEnabled by preferences.PosterEnhancersEnabled()
        .collectAsStateWithLifecycle(initialValue = preferences.isPosterEnhancersEnabled())

    return currentServerType.equals("EMBY", ignoreCase = true) && disablePosterEnhancersToggleEnabled
}

@Composable
fun DisableEmbyPosterEnhancers(): Boolean = disableEmbyPosterEnhancers()

@Composable
fun rememberImageUrl(
    itemId: String?,
    imageType: String = "Primary",
    width: Int? = null,
    height: Int? = null,
    quality: Int? = 90,
    enableImageEnhancers: Boolean = true,
    mediaRepository: MediaRepository
): String? {
    val disablePosterEnhancers = disableEmbyPosterEnhancers()
    val context = LocalContext.current
    val authRepository = remember(context) { AuthRepositoryProvider.getInstance(context) }
    val currentServerType by authRepository.getServerType().collectAsStateWithLifecycle(initialValue = null)
    val effectiveEnhancersEnabled = enableImageEnhancers && !disablePosterEnhancers
    val imageUrlFlow = remember(
        itemId,
        imageType,
        width,
        height,
        quality,
        effectiveEnhancersEnabled,
        currentServerType
    ) {
        if (itemId.isNullOrBlank() || currentServerType == null) {
            flowOf(null)
        } else {
            mediaRepository.getImageUrl(
                itemId = itemId,
                imageType = imageType,
                width = width,
                height = height,
                quality = quality,
                enableImageEnhancers = effectiveEnhancersEnabled
            )
        }
    }
    val imageUrl by imageUrlFlow.collectAsStateWithLifecycle(initialValue = null)

    return imageUrl
}

@Composable
fun rememberImageUrl(
    itemId: String?,
    imageType: String = "Primary",
    width: Int? = null,
    height: Int? = null,
    quality: Int? = 90,
    enableImageEnhancers: Boolean = true,
    viewModel: ImageUrlViewModel = hiltViewModel()
): String? {
    val disablePosterEnhancers = disableEmbyPosterEnhancers()
    val context = LocalContext.current
    val authRepository = remember(context) { AuthRepositoryProvider.getInstance(context) }
    val currentServerType by authRepository.getServerType().collectAsStateWithLifecycle(initialValue = null)
    val effectiveEnhancersEnabled = enableImageEnhancers && !disablePosterEnhancers
    val imageUrlFlow = remember(
        itemId,
        imageType,
        width,
        height,
        quality,
        effectiveEnhancersEnabled,
        currentServerType
    ) {
        if (itemId.isNullOrBlank() || currentServerType == null) {
            flowOf(null)
        } else {
            viewModel.getImageUrl(
                itemId = itemId,
                imageType = imageType,
                width = width,
                height = height,
                quality = quality,
                enableImageEnhancers = effectiveEnhancersEnabled
            )
        }
    }
    val imageUrl by imageUrlFlow.collectAsStateWithLifecycle(initialValue = null)

    return imageUrl
}

