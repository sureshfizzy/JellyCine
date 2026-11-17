package com.jellycine.util.image

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.util.platformBitmapConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/** Set by the host when Emby poster enhancers must be skipped. */
val LocalDisablePosterEnhancers = compositionLocalOf { false }

@Composable
fun JellyfinPosterImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    onLoadingStateChange: (Boolean) -> Unit = {},
    onErrorStateChange: (Boolean) -> Unit = {}
) {
    val context = LocalPlatformContext.current
    var imageState by remember(imageUrl) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    WarmImageUrl(imageUrl = imageUrl, allowRgb565 = true)

    LaunchedEffect(imageState) {
        when (imageState) {
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
            .platformBitmapConfig(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            renderEffect = null
        },
        contentScale = contentScale,
        alignment = alignment,
        onState = { state -> imageState = state }
    )
}

suspend fun getBackdrop(
    episode: BaseItemDto,
    mediaRepository: MediaRepository,
    width: Int,
    height: Int,
    quality: Int
): String? {
    val episodeId = episode.id ?: return null
    return mediaRepository.getImageUrl(
        itemId = episodeId,
        imageType = "Primary",
        width = width,
        height = height,
        quality = quality,
        enableImageEnhancers = false,
        imageTag = episode.imageTagFor(
            imageType = "Primary",
            targetItemId = episodeId
        )
    ).first() ?: episode.seriesId?.let { seriesId ->
        mediaRepository.getBackdropImageUrl(
            itemId = seriesId,
            imageIndex = 0,
            width = width,
            height = height,
            quality = quality,
            enableImageEnhancers = false,
            imageTag = episode.imageTagFor(
                imageType = "Backdrop",
                targetItemId = seriesId
            )
        ).first()
    }
}

@Composable
fun rememberImageUrl(
    itemId: String?,
    imageType: String = "Primary",
    width: Int? = null,
    height: Int? = null,
    quality: Int? = 90,
    enableImageEnhancers: Boolean = true,
    imageTag: String? = null,
    mediaRepository: MediaRepository
): String? {
    val disablePosterEnhancers = LocalDisablePosterEnhancers.current
    val effectiveEnhancersEnabled = enableImageEnhancers && !disablePosterEnhancers
    val imageUrlFlow = remember(
        itemId, imageType, width, height, quality, effectiveEnhancersEnabled, imageTag
    ) {
        if (itemId.isNullOrBlank()) {
            flowOf(null)
        } else {
            mediaRepository.getImageUrl(
                itemId = itemId,
                imageType = imageType,
                width = width,
                height = height,
                quality = quality,
                enableImageEnhancers = effectiveEnhancersEnabled,
                imageTag = imageTag
            )
        }
    }
    val imageUrl by imageUrlFlow.collectAsState(initial = null)
    return imageUrl
}
