package com.jellycine.ui.screens.dashboard.home

import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.util.ioDispatcher
import com.jellycine.util.ConcurrentCache
import com.jellycine.util.platformBitmapConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object ImagePreloader {
    private val preloadedUrls = ConcurrentCache<String, Boolean>()
    private val imageUrlCache = ConcurrentCache<String, String>()
    private val preferredImageTypeCache = ConcurrentCache<String, String>()
    private val lastRenderedImageUrlCache = ConcurrentCache<String, String>()
    private val prefetchSemaphore = Semaphore(64)
    private const val posterWidth = 240
    private const val posterHeight = 360
    private const val posterQuality = 80
    private const val continueWatchingWidth = 640
    private const val continueWatchingHeight = 360
    private const val continueWatchingQuality = 92
    private const val libraryLandscapeWidth = 640
    private const val libraryLandscapeHeight = 360
    private const val libraryLandscapeQuality = 92

    private fun imageCacheKey(
        itemId: String,
        imageType: String,
        width: Int,
        height: Int,
        quality: Int,
        hasImageEnhancers: Boolean = true,
        imageTag: String? = null
    ): String = "$itemId|$imageType|$width|$height|$quality|enhancers=$hasImageEnhancers|tag=${imageTag.orEmpty()}"

    fun getCachedImageUrl(
        itemId: String,
        imageType: String,
        width: Int,
        height: Int,
        quality: Int,
        hasImageEnhancers: Boolean = true,
        imageTag: String? = null
    ): String? {
        return imageUrlCache[
            imageCacheKey(
                itemId = itemId,
                imageType = imageType,
                width = width,
                height = height,
                quality = quality,
                hasImageEnhancers = hasImageEnhancers,
                imageTag = imageTag
            )
        ]
    }

    fun putCachedImageUrl(
        itemId: String,
        imageType: String,
        width: Int,
        height: Int,
        quality: Int,
        hasImageEnhancers: Boolean = true,
        imageTag: String? = null,
        imageUrl: String
    ) {
        imageUrlCache[
            imageCacheKey(
                itemId = itemId,
                imageType = imageType,
                width = width,
                height = height,
                quality = quality,
                hasImageEnhancers = hasImageEnhancers,
                imageTag = imageTag
            )
        ] = imageUrl
    }

    fun getLastRenderedImageUrl(
        itemId: String,
        imageType: String,
        width: Int,
        height: Int,
        quality: Int,
        hasImageEnhancers: Boolean = true,
        imageTag: String? = null
    ): String? {
        return lastRenderedImageUrlCache[
            imageCacheKey(
                itemId = itemId,
                imageType = imageType,
                width = width,
                height = height,
                quality = quality,
                hasImageEnhancers = hasImageEnhancers,
                imageTag = imageTag
            )
        ]
    }

    fun putLastRenderedImageUrl(
        itemId: String,
        imageType: String,
        width: Int,
        height: Int,
        quality: Int,
        hasImageEnhancers: Boolean = true,
        imageTag: String? = null,
        imageUrl: String
    ) {
        lastRenderedImageUrlCache[
            imageCacheKey(
                itemId = itemId,
                imageType = imageType,
                width = width,
                height = height,
                quality = quality,
                hasImageEnhancers = hasImageEnhancers,
                imageTag = imageTag
            )
        ] = imageUrl
    }

    private fun imageTypePreferenceKey(
        itemId: String?,
        seriesId: String?,
        itemType: String?,
        imageType: String,
        fallbackImageType: String?
    ): String {
        return "${itemId.orEmpty()}|${seriesId.orEmpty()}|${itemType.orEmpty()}|$imageType|${fallbackImageType.orEmpty()}"
    }

    fun getPreferredImageType(
        itemId: String?,
        seriesId: String?,
        itemType: String?,
        imageType: String,
        fallbackImageType: String?
    ): String? {
        return preferredImageTypeCache[
            imageTypePreferenceKey(
                itemId = itemId,
                seriesId = seriesId,
                itemType = itemType,
                imageType = imageType,
                fallbackImageType = fallbackImageType
            )
        ]
    }

    fun setPreferredImageType(
        itemId: String?,
        seriesId: String?,
        itemType: String?,
        imageType: String,
        fallbackImageType: String?,
        preferredImageType: String
    ) {
        preferredImageTypeCache[
            imageTypePreferenceKey(
                itemId = itemId,
                seriesId = seriesId,
                itemType = itemType,
                imageType = imageType,
                fallbackImageType = fallbackImageType
            )
        ] = preferredImageType
    }

    suspend fun preloadCriticalImages(
        items: List<BaseItemDto>,
        mediaRepository: MediaRepository,
        context: PlatformContext,
        maxItems: Int = 8
    ) = coroutineScope {
        val imageLoader = SingletonImageLoader.get(context)
        items
            .asSequence()
            .mapNotNull { it.id }
            .distinct()
            .take(maxItems)
            .map { itemId ->
                async(ioDispatcher) {
                    prefetchSemaphore.withPermit {
                        val key = "preload_$itemId"
                        if (preloadedUrls.putIfAbsent(key, true) != null) return@withPermit

                        try {
                            val imageUrl = mediaRepository.getImageUrlString(
                                itemId = itemId,
                                imageType = "Primary",
                                width = posterWidth,
                                height = posterHeight,
                                quality = posterQuality
                            )
                            if (!imageUrl.isNullOrBlank()) {
                                imageUrlCache[
                                    imageCacheKey(
                                        itemId = itemId,
                                        imageType = "Primary",
                                        width = posterWidth,
                                        height = posterHeight,
                                        quality = posterQuality
                                    )
                                ] = imageUrl
                                imageLoader.enqueue(
                                    ImageRequest.Builder(context)
                                        .data(imageUrl)
                                        .memoryCachePolicy(CachePolicy.ENABLED)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .networkCachePolicy(CachePolicy.ENABLED)
                                        .precision(Precision.INEXACT)
                                        .platformBitmapConfig(true)
                                        .crossfade(false)
                                        .build()
                                )
                            }
                        } catch (e: Exception) {
                            preloadedUrls.remove(key)
                        }
                    }
                }
            }
            .toList()
            .awaitAll()
    }

    fun preloadRemainingImages(
        items: List<BaseItemDto>,
        mediaRepository: MediaRepository,
        context: PlatformContext,
        scope: CoroutineScope,
        skipFirst: Int = 8,
        maxItems: Int = 20
    ) {
        val remainingItems = items.drop(skipFirst).take(maxItems)
        if (remainingItems.isEmpty()) return
        scope.launch(ioDispatcher) {
            preloadCriticalImages(
                items = remainingItems,
                mediaRepository = mediaRepository,
                context = context,
                maxItems = remainingItems.size
            )
        }
    }

    suspend fun continueWatchingImages(
        items: List<BaseItemDto>,
        mediaRepository: MediaRepository,
        context: PlatformContext,
        maxItems: Int = 24,
        priorityCount: Int = 8
    ) = coroutineScope {
        val imageLoader = SingletonImageLoader.get(context)
        val orderedItems = items
            .asSequence()
            .distinctBy { it.id }
            .take(maxItems)
            .toList()

        if (orderedItems.isEmpty()) return@coroutineScope

        val prioritized = orderedItems.take(priorityCount.coerceAtLeast(0))
        val remaining = orderedItems.drop(prioritized.size)

        // Prioritize first visible cards in order for faster perceived load.
        for (item in prioritized) {
            preloadContinueWatchingItem(
                item = item,
                mediaRepository = mediaRepository,
                imageLoader = imageLoader,
                context = context
            )
        }

        remaining
            .map { item ->
                async(ioDispatcher) {
                    prefetchSemaphore.withPermit {
                        preloadContinueWatchingItem(
                            item = item,
                            mediaRepository = mediaRepository,
                            imageLoader = imageLoader,
                            context = context
                        )
                    }
                }
            }
            .toList()
            .awaitAll()
    }

    suspend fun MyMedia(
        libraries: List<BaseItemDto>,
        mediaRepository: MediaRepository,
        context: PlatformContext,
        maxItems: Int = 32
    ) = coroutineScope {
        val imageLoader = SingletonImageLoader.get(context)
        libraries
            .asSequence()
            .distinctBy { it.id }
            .mapNotNull { it.id?.let { id -> id to it } }
            .take(maxItems)
            .map { (itemId, item) ->
                async(ioDispatcher) {
                    prefetchSemaphore.withPermit {
                        val preloadKey = "my_media_landscape_preload_$itemId"
                        if (preloadedUrls.putIfAbsent(preloadKey, true) != null) return@withPermit
                        try {
                            val imageTypes = listOf("Thumb", "Backdrop", "Primary")
                            var selectedType: String? = null
                            var selectedUrl: String? = null

                            for (type in imageTypes) {
                                val cached = getCachedImageUrl(
                                    itemId = itemId,
                                    imageType = type,
                                    width = libraryLandscapeWidth,
                                    height = libraryLandscapeHeight,
                                    quality = libraryLandscapeQuality
                                )
                                if (!cached.isNullOrBlank()) {
                                    selectedType = type
                                    selectedUrl = cached
                                    break
                                }

                                val Url = mediaRepository.getImageUrlString(
                                    itemId = itemId,
                                    imageType = type,
                                    width = libraryLandscapeWidth,
                                    height = libraryLandscapeHeight,
                                    quality = libraryLandscapeQuality
                                )
                                if (!Url.isNullOrBlank()) {
                                    putCachedImageUrl(
                                        itemId = itemId,
                                        imageType = type,
                                        width = libraryLandscapeWidth,
                                        height = libraryLandscapeHeight,
                                        quality = libraryLandscapeQuality,
                                        imageUrl = Url
                                    )
                                    selectedType = type
                                    selectedUrl = Url
                                    break
                                }
                            }

                            if (!selectedType.isNullOrBlank() && !selectedUrl.isNullOrBlank()) {
                                setPreferredImageType(
                                    itemId = item.id,
                                    seriesId = item.seriesId,
                                    itemType = item.type,
                                    imageType = "Thumb",
                                    fallbackImageType = "Backdrop",
                                    preferredImageType = selectedType
                                )
                                imageLoader.enqueue(
                                    ImageRequest.Builder(context)
                                        .data(selectedUrl)
                                        .memoryCachePolicy(CachePolicy.ENABLED)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .networkCachePolicy(CachePolicy.ENABLED)
                                        .precision(Precision.INEXACT)
                                        .platformBitmapConfig(false)
                                        .crossfade(false)
                                        .build()
                                )
                            }
                        } catch (_: Exception) {
                            preloadedUrls.remove(preloadKey)
                        }
                    }
                }
            }
            .toList()
            .awaitAll()
    }

    private suspend fun preloadContinueWatchingItem(
        item: BaseItemDto,
        mediaRepository: MediaRepository,
        imageLoader: coil3.ImageLoader,
        context: PlatformContext
    ) {
        val itemId = item.id ?: return
        val itemType = item.type
        val requestIds = listOfNotNull(itemId, item.seriesId).distinct()
        val preloadKey = "cw_preload_${itemId}_${item.seriesId.orEmpty()}"
        if (preloadedUrls.putIfAbsent(preloadKey, true) != null) return

        try {
            val imageTypes = listOf("Thumb", "Backdrop", "Primary")
            var selectedType: String? = null
            var selectedUrl: String? = null

            for (type in imageTypes) {
                val requestIdsForType = when {
                    itemType == "Episode" &&
                        (type == "Thumb" || type == "Backdrop" || type == "Primary") -> {
                        listOfNotNull(item.seriesId, itemId).distinct()
                    }
                    else -> requestIds
                }
                for (requestId in requestIdsForType) {
                    val cached = getCachedImageUrl(
                        itemId = requestId,
                        imageType = type,
                        width = continueWatchingWidth,
                        height = continueWatchingHeight,
                        quality = continueWatchingQuality
                    )
                    if (!cached.isNullOrBlank()) {
                        selectedType = type
                        selectedUrl = cached
                        break
                    }

                    val Url = mediaRepository.getImageUrlString(
                        itemId = requestId,
                        imageType = type,
                        width = continueWatchingWidth,
                        height = continueWatchingHeight,
                        quality = continueWatchingQuality
                    )
                    if (!Url.isNullOrBlank()) {
                        putCachedImageUrl(
                            itemId = requestId,
                            imageType = type,
                            width = continueWatchingWidth,
                            height = continueWatchingHeight,
                            quality = continueWatchingQuality,
                            imageUrl = Url
                        )
                        selectedType = type
                        selectedUrl = Url
                        break
                    }
                }
                if (!selectedType.isNullOrBlank()) break
            }

            if (!selectedType.isNullOrBlank() && !selectedUrl.isNullOrBlank()) {
                setPreferredImageType(
                    itemId = item.id,
                    seriesId = item.seriesId,
                    itemType = itemType,
                    imageType = "Thumb",
                    fallbackImageType = "Backdrop",
                    preferredImageType = selectedType
                )
                imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(selectedUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .precision(Precision.INEXACT)
                        .platformBitmapConfig(false)
                        .crossfade(false)
                        .build()
                )
            }
        } catch (_: Exception) {
            preloadedUrls.remove(preloadKey)
        }
    }

}
