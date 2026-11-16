package com.jellycine.shared.ui.screens.dashboard.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.util.ioDispatcher
import com.jellycine.shared.util.image.WarmImageUrl
import com.jellycine.shared.util.image.imageTagFor
import com.jellycine.shared.util.platformBitmapConfig
import kotlinx.coroutines.withContext

@Composable
fun ImageLoader(
    itemId: String?,
    seriesId: String? = null,
    imageType: String = "Primary",
    fallbackImageType: String? = null,
    extraFallbackImageTypes: List<String> = emptyList(),
    preferSeriesIdForThumbBackdrop: Boolean = true,
    preferSeriesIdForEpisodePrimary: Boolean = true,
    allowRgb565: Boolean = true,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Int = 8,
    crossfadeMillis: Int = 60,
    mediaRepository: MediaRepository,
    imageMetadata: BaseItemDto? = null,
    itemType: String? = null, // Add item type to handle episodes properly
    hasImageEnhancers: Boolean = true,
    imageTag: String? = null,
    imageHeaders: Map<String, String> = emptyMap()
) {
    val imageTypes = remember(imageType, fallbackImageType, extraFallbackImageTypes) {
        buildList {
            add(imageType)
            if (!fallbackImageType.isNullOrBlank() && !fallbackImageType.equals(imageType, ignoreCase = true)) {
                add(fallbackImageType)
            }
            extraFallbackImageTypes
                .filter { it.isNotBlank() }
                .forEach { fallbackType ->
                    if (none { it.equals(fallbackType, ignoreCase = true) }) {
                        add(fallbackType)
                    }
                }
        }
    }
    val preferredImageType = remember(itemId, seriesId, itemType, imageType, fallbackImageType) {
        ImagePreloader.getPreferredImageType(
            itemId = itemId,
            seriesId = seriesId,
            itemType = itemType,
            imageType = imageType,
            fallbackImageType = fallbackImageType
        )
    }
    var imageTypeIndex by remember(itemId, seriesId, itemType, imageType, fallbackImageType) {
        val preferredIndex = imageTypes.indexOfFirst { it.equals(preferredImageType, ignoreCase = true) }
        mutableStateOf(if (preferredIndex >= 0) preferredIndex else 0)
    }
    val currentImageType = imageTypes.getOrElse(imageTypeIndex) { imageType }
    val hasMoreFallbacks = imageTypeIndex < imageTypes.lastIndex

    val actualItemId = remember(itemId, seriesId, itemType, currentImageType, preferSeriesIdForThumbBackdrop, preferSeriesIdForEpisodePrimary) {
        when {
            itemType == "Episode" &&
                currentImageType == "Primary" &&
                preferSeriesIdForEpisodePrimary &&
                !seriesId.isNullOrBlank() -> seriesId
            (currentImageType == "Thumb" || currentImageType == "Backdrop") &&
                preferSeriesIdForThumbBackdrop &&
                !seriesId.isNullOrBlank() -> seriesId
            else -> itemId
        }
    }

    val (width, height, quality) = remember(currentImageType) {
        when (currentImageType) {
            "Thumb", "Backdrop" -> Triple(640, 360, 92)
            "Primary" -> {
                if (imageType == "Thumb" || imageType == "Backdrop") {
                    Triple(640, 360, 92)
                } else {
                    Triple(240, 360, 80)
                }
            }
            else -> Triple(240, 360, 80)
        }
    }
    val selectedImageTag = remember(imageMetadata, actualItemId, currentImageType, imageTag) {
        imageMetadata?.imageTagFor(
            imageType = currentImageType,
            targetItemId = actualItemId
        ) ?: imageTag
    }
    val initialCachedUrl = remember(actualItemId, currentImageType, width, height, hasImageEnhancers, selectedImageTag) {
        if (actualItemId.isNullOrBlank()) {
            null
        } else {
            ImagePreloader.getCachedImageUrl(
                itemId = actualItemId,
                imageType = currentImageType,
                width = width,
                height = height,
                quality = quality,
                hasImageEnhancers = hasImageEnhancers,
                imageTag = selectedImageTag
            )
        }
    }
    val initialRenderedUrl = remember(actualItemId, currentImageType, width, height, hasImageEnhancers, selectedImageTag) {
        if (actualItemId.isNullOrBlank()) {
            null
        } else {
            ImagePreloader.getLastRenderedImageUrl(
                itemId = actualItemId,
                imageType = currentImageType,
                width = width,
                height = height,
                quality = quality,
                hasImageEnhancers = hasImageEnhancers,
                imageTag = selectedImageTag
            )
        }
    }
    val initialUrl = remember(initialRenderedUrl, initialCachedUrl) {
        initialRenderedUrl ?: initialCachedUrl
    }
    var imageUrl by remember(actualItemId, currentImageType, hasImageEnhancers, selectedImageTag) { mutableStateOf(initialUrl) }
    var hasError by remember(actualItemId, currentImageType, hasImageEnhancers, selectedImageTag) { mutableStateOf(false) }
    var isImageLoaded by remember(actualItemId, currentImageType, hasImageEnhancers, selectedImageTag) {
        mutableStateOf(!initialUrl.isNullOrBlank())
    }
    var hasRenderedSuccess by remember(actualItemId, currentImageType, hasImageEnhancers, selectedImageTag) {
        mutableStateOf(!initialUrl.isNullOrBlank())
    }
    val context = LocalPlatformContext.current
    WarmImageUrl(imageUrl = imageUrl, allowRgb565 = allowRgb565)

    LaunchedEffect(actualItemId, currentImageType, hasImageEnhancers, selectedImageTag) {
        if (actualItemId != null) {
            hasError = false
            if (!imageUrl.isNullOrBlank()) {
                isImageLoaded = true
                return@LaunchedEffect
            }
            isImageLoaded = false
            try {
                val cachedUrl = ImagePreloader.getCachedImageUrl(
                    itemId = actualItemId,
                    imageType = currentImageType,
                    width = width,
                    height = height,
                    quality = quality,
                    hasImageEnhancers = hasImageEnhancers,
                    imageTag = selectedImageTag
                )
                if (!cachedUrl.isNullOrBlank()) {
                    imageUrl = cachedUrl
                    ImagePreloader.setPreferredImageType(
                        itemId = itemId,
                        seriesId = seriesId,
                        itemType = itemType,
                        imageType = imageType,
                        fallbackImageType = fallbackImageType,
                        preferredImageType = currentImageType
                    )
                    return@LaunchedEffect
                }
                val Url = withContext(ioDispatcher) {
                    mediaRepository.getImageUrlString(
                        itemId = actualItemId,
                        imageType = currentImageType,
                        width = width,
                        height = height,
                        quality = quality,
                        enableImageEnhancers = hasImageEnhancers,
                        imageTag = selectedImageTag
                    )
                }
                if (!Url.isNullOrBlank()) {
                    imageUrl = Url
                    ImagePreloader.putCachedImageUrl(
                        itemId = actualItemId,
                        imageType = currentImageType,
                        width = width,
                        height = height,
                        quality = quality,
                        hasImageEnhancers = hasImageEnhancers,
                        imageTag = selectedImageTag,
                        imageUrl = Url
                    )
                    ImagePreloader.setPreferredImageType(
                        itemId = itemId,
                        seriesId = seriesId,
                        itemType = itemType,
                        imageType = imageType,
                        fallbackImageType = fallbackImageType,
                        preferredImageType = currentImageType
                    )
                } else if (hasMoreFallbacks) {
                    imageTypeIndex += 1
                } else {
                    hasError = true
                }
            } catch (e: Exception) {
                if (hasMoreFallbacks) {
                    imageTypeIndex += 1
                } else {
                    hasError = true
                }
            }
        } else {
            if (hasMoreFallbacks) {
                imageTypeIndex += 1
            } else {
                imageUrl = null
                hasError = true
            }
        }
    }

    Box(modifier = modifier) {
        if (!imageUrl.isNullOrEmpty() && !hasError) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .httpHeaders(
                        NetworkHeaders.Builder().apply {
                            imageHeaders.forEach { (name, value) -> set(name, value) }
                        }.build()
                    )
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .precision(Precision.INEXACT)
                    .platformBitmapConfig(allowRgb565)
                    .crossfade(crossfadeMillis)
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius.dp)),
                contentScale = contentScale,
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> {
                            hasError = false
                            isImageLoaded = true
                            hasRenderedSuccess = true
                            val renderedItemId = actualItemId
                            val renderedUrl = imageUrl
                            if (!renderedItemId.isNullOrBlank() && !renderedUrl.isNullOrBlank()) {
                                ImagePreloader.putLastRenderedImageUrl(
                                    itemId = renderedItemId,
                                    imageType = currentImageType,
                                    width = width,
                                    height = height,
                                    quality = quality,
                                    hasImageEnhancers = hasImageEnhancers,
                                    imageTag = selectedImageTag,
                                    imageUrl = renderedUrl
                                )
                            }
                        }
                        is AsyncImagePainter.State.Error -> {
                            if (hasMoreFallbacks) {
                                imageUrl = null
                                imageTypeIndex += 1
                                hasError = false
                                isImageLoaded = false
                                hasRenderedSuccess = false
                            } else {
                                hasError = true
                                isImageLoaded = false
                                hasRenderedSuccess = false
                            }
                        }
                        is AsyncImagePainter.State.Loading,
                        is AsyncImagePainter.State.Empty -> {
                            if (!hasRenderedSuccess) {
                                isImageLoaded = false
                            }
                        }
                    }
                }
            )
        }

    }
}
