package com.jellycine.app.ui.screens.dashboard.home
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.download.DownloadRepositoryProvider
import com.jellycine.shared.preferences.Preferences
import com.jellycine.shared.util.image.JellyfinPosterImage
import com.jellycine.shared.ui.components.common.*
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.HomeLibrarySectionData
import com.jellycine.data.model.PersistedHomeSnapshot
import com.jellycine.data.model.UserItemDataDto
import com.jellycine.data.network.NetworkModule
import com.jellycine.data.network.sameServerUrl
import com.jellycine.data.network.trimTrailingSlash
import com.jellycine.data.preferences.NetworkPreferences
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.app.ui.screens.auth.ServerSwitchDialogsHost
import com.jellycine.app.ui.screens.auth.ServerSwitchViewModel
import com.jellycine.app.ui.screens.auth.ProfileImageLoader
import com.jellycine.app.ui.screens.auth.rememberServerSwitchDialogsState
import com.jellycine.app.ui.screens.dashboard.settings.DownloadsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.jellycine.app.R
import com.jellycine.app.ui.screens.dashboard.PosterSkeleton
import com.jellycine.app.ui.screens.dashboard.GenreSectionSkeleton
import com.jellycine.detail.CodecUtils
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Immutable
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.draw.drawWithCache
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collect
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.imageLoader
import coil3.request.*
import coil3.size.Precision
import com.jellycine.shared.util.image.imageTagFor
import com.jellycine.shared.util.image.WarmImageUrl
import com.jellycine.shared.playback.PlaybackRefreshSignals
import java.util.concurrent.ConcurrentHashMap

@Stable
@Immutable
data class QueryState<T>(
    val data: T? = null,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val error: String? = null,
    val isStale: Boolean = false,
    val lastFetched: Long = 0L
) {
    val isSuccess: Boolean get() = data != null && !isError
    val isEmpty: Boolean get() = data == null && !isLoading && !isError
}

@Stable
@Immutable
data class QueryConfig(
    val staleTime: Long = 300_000L,
    val retryCount: Int = 1,
    val retryDelay: Long = 250L,
    val enabled: Boolean = true,
    val requestTimeoutMs: Long = NetworkPreferences.DEFAULT_REQUEST_TIMEOUT_MS.toLong()
)

class QueryManager(private val scope: CoroutineScope) {
    private val queries = mutableMapOf<String, QueryState<*>>()
    private val jobs = mutableMapOf<String, Job>()

    @Suppress("UNCHECKED_CAST")
    fun <T> getQuery(key: String): QueryState<T> {
        return queries[key] as? QueryState<T> ?: QueryState()
    }

    fun <T> setQuery(key: String, state: QueryState<T>) {
        queries[key] = state
    }

    fun <T> executeQuery(
        key: String,
        config: QueryConfig = QueryConfig(),
        fetcher: suspend () -> T
    ): QueryState<T> {
        val currentState = getQuery<T>(key)

        if (!config.enabled) {
            return currentState
        }

        val isStale = System.currentTimeMillis() - currentState.lastFetched > config.staleTime
        val hasNonEmptyData = when (val data = currentState.data) {
            is Collection<*> -> data.isNotEmpty()
            is Map<*, *> -> data.isNotEmpty()
            else -> data != null
        }
        if (hasNonEmptyData && !isStale) {
            return currentState
        }

        val isAlreadyFetching = jobs[key]?.isActive == true
        if (isAlreadyFetching) {
            return currentState
        }

        val newState = currentState.copy(isLoading = true, isError = false, error = null)
        setQuery(key, newState)

        jobs[key] = scope.launch {
            var lastException: Exception? = null
            var retryCount = 0

            while (retryCount <= config.retryCount) {
                try {
                    val result = withTimeout(config.requestTimeoutMs) {
                        withContext(Dispatchers.IO) { fetcher() }
                    }
                    setQuery(key, QueryState(
                        data = result,
                        isLoading = false,
                        isError = false,
                        error = null,
                        lastFetched = System.currentTimeMillis()
                    ))
                    break
                } catch (e: Exception) {
                    lastException = e
                    retryCount++

                    val Retry = when {
                        e.message?.contains("timeout", ignoreCase = true) == true -> true
                        e.message?.contains("connection", ignoreCase = true) == true -> true
                        e.message?.contains("network", ignoreCase = true) == true -> true
                        e.message?.contains("failed to connect", ignoreCase = true) == true -> true
                        e.message?.contains("unable to resolve host", ignoreCase = true) == true -> true
                        e is TimeoutCancellationException -> false
                        retryCount <= config.retryCount -> true
                        else -> false
                    }

                    if (Retry && retryCount <= config.retryCount) {
                        delay(config.retryDelay * retryCount)
                    } else {
                        setQuery(key, QueryState<T>(
                            data = currentState.data,
                            isLoading = false,
                            isError = true,
                            error = lastException.message ?: "Unknown error",
                            lastFetched = currentState.lastFetched
                        ))
                        break
                    }
                }
            }
        }

        return newState
    }

    fun invalidateQuery(key: String) {
        val currentState = queries[key]
        if (currentState != null) {
            queries[key] = currentState.copy(isStale = true, lastFetched = 0L)
        }
    }

    fun cancelQuery(key: String) {
        jobs[key]?.cancel()
        jobs.remove(key)
    }

    fun refreshStaleQueries() {
        val currentTime = System.currentTimeMillis()
        queries.forEach { (key, state) ->
            val isStale = currentTime - state.lastFetched > 300_000L
            if (isStale && !state.isLoading) {
                invalidateQuery(key)
            }
        }
    }

    fun cleanup() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        queries.clear()
    }
}

@Composable
fun <T> useQuery(
    key: String,
    config: QueryConfig = QueryConfig(),
    fetcher: suspend () -> T
): QueryState<T> {
    val queryManager = LocalQueryManager.current
    var state by remember(key, queryManager) { mutableStateOf(queryManager.getQuery<T>(key)) }
    var hasInitiated by remember(key, queryManager) { mutableStateOf(false) }

    LaunchedEffect(key, config.enabled, queryManager) {
        if (!config.enabled) return@LaunchedEffect

        hasInitiated = true
        state = queryManager.executeQuery(key, config, fetcher)

        while (true) {
            delay(24)

            var currentState = queryManager.getQuery<T>(key)
            if (currentState.isStale && !currentState.isLoading) {
                currentState = queryManager.executeQuery(key, config, fetcher)
            }

            if (currentState != state) {
                state = currentState
            }
        }
    }

    return if (!hasInitiated && state.data == null && !state.isError) {
        state.copy(isLoading = true)
    } else {
        state
    }
}

object ImagePreloader {
    private val preloadedUrls = ConcurrentHashMap.newKeySet<String>()
    private val imageUrlCache = ConcurrentHashMap<String, String>()
    private val preferredImageTypeCache = ConcurrentHashMap<String, String>()
    private val lastRenderedImageUrlCache = ConcurrentHashMap<String, String>()
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
        context: android.content.Context,
        maxItems: Int = 8
    ) = coroutineScope {
        val imageLoader = context.imageLoader
        items
            .asSequence()
            .mapNotNull { it.id }
            .distinct()
            .take(maxItems)
            .map { itemId ->
                async(Dispatchers.IO) {
                    prefetchSemaphore.withPermit {
                        val key = "preload_$itemId"
                        if (!preloadedUrls.add(key)) return@withPermit

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
                                        .allowHardware(true)
                                        .allowRgb565(true)
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
        context: android.content.Context,
        scope: CoroutineScope,
        skipFirst: Int = 8,
        maxItems: Int = 20
    ) {
        val remainingItems = items.drop(skipFirst).take(maxItems)
        if (remainingItems.isEmpty()) return
        scope.launch(Dispatchers.IO) {
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
        context: android.content.Context,
        maxItems: Int = 24,
        priorityCount: Int = 8
    ) = coroutineScope {
        val imageLoader = context.imageLoader
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
                async(Dispatchers.IO) {
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
        context: android.content.Context,
        maxItems: Int = 32
    ) = coroutineScope {
        val imageLoader = context.imageLoader
        libraries
            .asSequence()
            .distinctBy { it.id }
            .mapNotNull { it.id?.let { id -> id to it } }
            .take(maxItems)
            .map { (itemId, item) ->
                async(Dispatchers.IO) {
                    prefetchSemaphore.withPermit {
                        val preloadKey = "my_media_landscape_preload_$itemId"
                        if (!preloadedUrls.add(preloadKey)) return@withPermit
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
                                        .allowHardware(true)
                                        .allowRgb565(false)
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
        context: android.content.Context
    ) {
        val itemId = item.id ?: return
        val itemType = item.type
        val requestIds = listOfNotNull(itemId, item.seriesId).distinct()
        val preloadKey = "cw_preload_${itemId}_${item.seriesId.orEmpty()}"
        if (!preloadedUrls.add(preloadKey)) return

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
                        .allowHardware(true)
                        .allowRgb565(false)
                        .crossfade(false)
                        .build()
                )
            }
        } catch (_: Exception) {
            preloadedUrls.remove(preloadKey)
        }
    }

}
@Composable
fun ImageLoader(
    itemId: String?,
    seriesId: String? = null,
    imageType: String = "Primary",
    fallbackImageType: String? = null,
    extraFallbackImageTypes: List<String> = emptyList(),
    preferSeriesIdForThumbBackdrop: Boolean = true,
    allowRgb565: Boolean = true,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Int = 8,
    crossfadeMillis: Int = 60,
    mediaRepository: com.jellycine.data.repository.MediaRepository,
    imageMetadata: BaseItemDto? = null,
    itemType: String? = null, // Add item type to handle episodes properly
    hasImageEnhancers: Boolean = true,
    imageTag: String? = null
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

    val actualItemId = remember(itemId, seriesId, itemType, currentImageType, preferSeriesIdForThumbBackdrop) {
        when {
            itemType == "Episode" && currentImageType == "Primary" && !seriesId.isNullOrBlank() -> seriesId
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
    val context = LocalContext.current
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
                val Url = withContext(Dispatchers.IO) {
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
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .precision(Precision.INEXACT)
                    .allowHardware(true)
                    .allowRgb565(allowRgb565)
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

val LocalQueryManager = compositionLocalOf<QueryManager> {
    error("QueryManager not provided")
}

private object DashboardHomeQueryStore {
    private var ownerSessionKey: String? = null
    private var manager: QueryManager? = null

    @Synchronized
    fun get(sessionKey: String?): QueryManager {
        val existing = manager
        if (existing != null) {
            if (sessionKey == null) {
                return existing
            }
            if (ownerSessionKey == sessionKey) {
                return existing
            }
        }
        manager?.cleanup()
        ownerSessionKey = sessionKey
        manager = QueryManager(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        return manager!!
    }

}

@Stable
object ScrollOptimization {
    @Composable
    fun rememberUltraSmoothFlingBehavior(): FlingBehavior {
        return ScrollableDefaults.flingBehavior()
    }

    val optimizedContentPadding = PaddingValues(horizontal = 16.dp)

    val optimizedSpacing = 12.dp

    val listItemSpacing = 8.dp

    fun getScrollContainerModifier(): Modifier = Modifier
        .fillMaxWidth()
}

data class Movie(
    val id: String,
    val title: String,
    val year: String,
    val posterUrl: String? = null
)

@Stable
@Immutable
data class StableBaseItem(
    val id: String?,
    val name: String?,
    val type: String?,
    val seriesId: String?,
    val seriesName: String?,
    val productionYear: Int?,
    val userData: UserItemDataDto?,
    val episodeCount: Int?,
    val recursiveItemCount: Int?,
    val collectionType: String?
) {
    val displayName: String by lazy {
        name ?: "Unknown Title"
    }

    val hasProgress: Boolean by lazy {
        userData?.playedPercentage != null && userData.playedPercentage!! > 0
    }

    val progressPercentage: Float by lazy {
        (userData?.playedPercentage?.toFloat() ?: 0f) / 100f
    }

    companion object {
        fun from(item: BaseItemDto): StableBaseItem {
            return StableBaseItem(
                id = item.id,
                name = item.name,
                type = item.type,
                seriesId = item.seriesId,
                seriesName = item.seriesName,
                productionYear = item.productionYear,
                userData = item.userData,
                episodeCount = item.episodeCount,
                recursiveItemCount = item.recursiveItemCount,
                collectionType = item.collectionType
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Dashboard(
    onLogout: () -> Unit = {},
    onNavigateToDetail: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> },
    onNavigateToPlayer: (String) -> Unit = {},
    onAddServer: () -> Unit = {},
    onAddUser: (serverUrl: String, serverName: String?) -> Unit = { _, _ -> },
    isTabActive: Boolean = true,
    dashboardScrollState: LazyListState? = null,
    sidebarFocusRequester: FocusRequester? = null,
    headerFocusRequester: FocusRequester? = null,
    headerEndFocusRequester: FocusRequester? = null,
    contentFocusRequester: FocusRequester? = null,
    onHomeReturnTargetChanged: ((FocusRequester) -> Unit)? = null
) {
    var selectedCategory by rememberSaveable { mutableStateOf(HomeCategory.HOME) }
    var showAccountOverview by rememberSaveable { mutableStateOf(false) }
    val contentEntryFocusRequester = contentFocusRequester ?: remember(selectedCategory) { FocusRequester() }
    val heroActionEntryFocusRequester = remember(selectedCategory) { FocusRequester() }
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val mediaRepository = remember { com.jellycine.data.repository.MediaRepositoryProvider.getInstance(context) }
    val downloadRepository = remember { DownloadRepositoryProvider.getInstance(context) }
    val preferences = remember { Preferences(context) }
    val authRepository = remember { com.jellycine.data.repository.AuthRepositoryProvider.getInstance(context) }
    val scope = rememberCoroutineScope()
    val serverSwitchViewModel: ServerSwitchViewModel = viewModel {
        ServerSwitchViewModel(context.applicationContext as android.app.Application)
    }
    val initialSessionSnapshot = remember { authRepository.getActiveSessionSnapshot() }
    val serverSwitchUiState by serverSwitchViewModel.uiState.collectAsStateWithLifecycle()
    val networkRequestTimeoutMs = NetworkPreferences(context).getTimeoutConfig().requestTimeoutMs.toLong()
    val networkAvailabilityFlow = remember(appContext) {
        NetworkModule.observeNetworkAvailability(appContext)
    }
    val isNetworkAvailable by networkAvailabilityFlow.collectAsStateWithLifecycle(
        initialValue = NetworkModule.isInternetAvailable(appContext)
    )
    val featureCarouselEnabled by preferences.FeatureCarouselEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isFeatureCarouselEnabled()
        )
    val continueWatchingEnabled by preferences.ContinueWatchingEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isContinueWatchingEnabled()
        )
    val nextUpEnabled by preferences.NextUpEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isNextUpEnabled()
        )
    val posterEnhancersEnabled by preferences.PosterEnhancersEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isPosterEnhancersEnabled()
        )
    val trackedDownloads by downloadRepository.observeTrackedDownloads().collectAsState(initial = emptyList())
    val serverSwitchDialogsState = rememberServerSwitchDialogsState()
    val latestPlaybackStopEvent by PlaybackRefreshSignals.latestStopEvent.collectAsState()

    LaunchedEffect(featureCarouselEnabled) {
        if (!featureCarouselEnabled && selectedCategory != HomeCategory.HOME) {
            selectedCategory = HomeCategory.HOME
        }
    }

    var persistedHomeSnapshot by remember {
        mutableStateOf<PersistedHomeSnapshot?>(mediaRepository.getPersistedHomeSnapshot())
    }
    val sessionSnapshot by authRepository.observeActiveSession().collectAsState(
        initial = initialSessionSnapshot
    )
    val activeSavedServer = remember(sessionSnapshot.savedServers, sessionSnapshot.activeServerId) {
        sessionSnapshot.savedServers.firstOrNull { savedServer ->
            savedServer.id == sessionSnapshot.activeServerId
        }
    }
    val currentUsername = sessionSnapshot.username ?: persistedHomeSnapshot?.username
    val currentServerName = sessionSnapshot.serverName ?: persistedHomeSnapshot?.serverName
    val currentServerUrl = sessionSnapshot.serverUrl ?: persistedHomeSnapshot?.serverUrl
    val currentServerType = sessionSnapshot.serverType
    val currentProfileImageUrl = activeSavedServer?.profileImageUrl ?: persistedHomeSnapshot?.profileImageUrl
    val isEmbyServer = currentServerType.equals("EMBY", ignoreCase = true)
    val disablePosterEnhancers = isEmbyServer && posterEnhancersEnabled
    val dashboardSessionKey = remember(currentServerUrl, currentUsername) {
        "${currentServerUrl?.let(::trimTrailingSlash).orEmpty()}|${currentUsername.orEmpty()}"
    }
    val HeaderUserName = currentUsername
        ?.takeIf { it.isNotBlank() }
        ?: "User"
    var noCarouselProfileImageUrl by remember(dashboardSessionKey) {
        mutableStateOf(persistedHomeSnapshot?.profileImageUrl)
    }
    val usersForCurrentServer = remember(
        sessionSnapshot.savedServers,
        currentServerUrl,
        sessionSnapshot.activeServerId
    ) {
        sessionSnapshot.savedServers
            .filter { savedServer ->
                currentServerUrl != null &&
                    sameServerUrl(savedServer.serverUrl, currentServerUrl)
            }
            .sortedWith(
                compareByDescending<com.jellycine.data.repository.AuthRepository.SavedServer> {
                    it.id == sessionSnapshot.activeServerId
                }.thenBy { it.username.lowercase() }
            )
    }
    LaunchedEffect(featureCarouselEnabled, dashboardSessionKey, HeaderUserName) {
        if (featureCarouselEnabled) return@LaunchedEffect

        if (!noCarouselProfileImageUrl.isNullOrBlank()) return@LaunchedEffect

        val user = withContext(Dispatchers.IO) {
            try {
                mediaRepository.getCurrentUser().getOrNull()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
        currentCoroutineContext().ensureActive()
        val profileUrl = withContext(Dispatchers.IO) {
            try {
                mediaRepository.getUserProfileImageUrl(user?.primaryImageTag)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
        currentCoroutineContext().ensureActive()
        noCarouselProfileImageUrl = profileUrl
    }
    val queryManager = remember(dashboardSessionKey) {
        DashboardHomeQueryStore.get(dashboardSessionKey)
    }
    var previousSessionKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dashboardSessionKey, isNetworkAvailable) {
        if (previousSessionKey != null && previousSessionKey != dashboardSessionKey) {
            Cache.clear()
        }
        previousSessionKey = dashboardSessionKey
        persistedHomeSnapshot = mediaRepository.loadPersistedHomeSnapshot()
    }

    LaunchedEffect(dashboardSessionKey, persistedHomeSnapshot) {
        val snapshot = persistedHomeSnapshot ?: return@LaunchedEffect
        val persistedProfileUrl = snapshot.profileImageUrl
        if (!persistedProfileUrl.isNullOrBlank() && noCarouselProfileImageUrl.isNullOrBlank()) {
            noCarouselProfileImageUrl = persistedProfileUrl
        }
    }

    LaunchedEffect(dashboardSessionKey, isNetworkAvailable) {
        if (!isNetworkAvailable) return@LaunchedEffect

        val userResult = mediaRepository.getCurrentUser()
        currentCoroutineContext().ensureActive()
        val forceLogout = if (userResult.isSuccess) {
            val user = userResult.getOrNull()
            val isVideoTranscodingEnabled = user?.policy?.enableVideoPlaybackTranscoding ?: user?.let { true }
            val isAudioTranscodingEnabled = user?.policy?.enableAudioPlaybackTranscoding ?: user?.let { true }
            val profileUrl = try {
                mediaRepository.getUserProfileImageUrl(user?.primaryImageTag)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            currentCoroutineContext().ensureActive()
            if (!profileUrl.isNullOrBlank()) {
                noCarouselProfileImageUrl = profileUrl
            }
            authRepository.updateActiveServerProfileImage(
                profileImageUrl = profileUrl ?: persistedHomeSnapshot?.profileImageUrl
            )
            val activeUsername = currentUsername ?: persistedHomeSnapshot?.username
            mediaRepository.persistHomeSnapshot(
                username = activeUsername,
                serverName = currentServerName,
                serverUrl = currentServerUrl,
                profileImageUrl = profileUrl,
                isAdministrator = user?.policy?.isAdministrator,
                isVideoTranscodingAllowed = isVideoTranscodingEnabled,
                isAudioTranscodingAllowed = isAudioTranscodingEnabled
            )
            user?.policy?.isDisabled == true
        } else {
            val message = userResult.exceptionOrNull()?.message.orEmpty()
            message.contains("401") ||
                message.contains("403") ||
                message.contains("404")
        }

        if (forceLogout) {
            authRepository.logout()
            mediaRepository.clearPersistedHomeSnapshot()
            CachedData.clearAllCache()
            onLogout()
        }
    }

    val lazyColumnState = dashboardScrollState ?: rememberLazyListState()
    val dashboardBringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float
            ): Float {
                val leadingEdge = offset
                val trailingEdge = offset + size
                return when {
                    leadingEdge < 0f -> leadingEdge
                    trailingEdge > containerSize -> trailingEdge - containerSize
                    else -> 0f
                }
            }
        }
    }

    CompositionLocalProvider(LocalQueryManager provides queryManager) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides dashboardBringIntoViewSpec) {

        val featuredQuery = useQuery(
            key = "featured_$selectedCategory",
            config = QueryConfig(
                staleTime = 300_000L,
                enabled = isTabActive && isNetworkAvailable && featureCarouselEnabled,
                requestTimeoutMs = networkRequestTimeoutMs
            )
        ) {
            val result = when (selectedCategory) {
                HomeCategory.MOVIES -> mediaRepository.getUserItems(
                    parentId = null,
                    includeItemTypes = "Movie",
                    sortBy = "Random",
                    sortOrder = null,
                    limit = 10,
                    startIndex = 0,
                    recursive = true,
                    fields = "BasicSyncInfo,Genres,CommunityRating,CriticRating,ProductionYear,PremiereDate,OfficialRating,Overview"
                )
                HomeCategory.TV_SHOWS -> mediaRepository.getUserItems(
                    parentId = null,
                    includeItemTypes = "Series",
                    sortBy = "Random",
                    sortOrder = null,
                    limit = 10,
                    startIndex = 0,
                    recursive = true,
                    fields = "BasicSyncInfo,Genres,CommunityRating,CriticRating,ProductionYear,PremiereDate,OfficialRating,Overview"
                )
                else -> mediaRepository.getUserItems(
                    parentId = null,
                    includeItemTypes = "Movie,Series",
                    sortBy = "Random",
                    sortOrder = null,
                    limit = 10,
                    startIndex = 0,
                    recursive = true,
                    fields = "BasicSyncInfo,Genres,CommunityRating,CriticRating,ProductionYear,PremiereDate,OfficialRating,Overview"
                )
            }

            result.fold(
                onSuccess = { query ->
                    query.items
                        .orEmpty()
                        .filter { it.id != null && !it.name.isNullOrBlank() }
                },
                onFailure = { throw it }
            )
        }

        val continueWatchingQuery = useQuery(
            key = "continue_watching_resume_api_v2",
            config = QueryConfig(
                staleTime = 60_000L,
                enabled = isTabActive && selectedCategory == HomeCategory.HOME && isNetworkAvailable && continueWatchingEnabled,
                retryCount = 2,
                retryDelay = 250L,
                requestTimeoutMs = networkRequestTimeoutMs
            )
        ) {
            val result = mediaRepository.getResumeItems(limit = 24)
            result.fold(
                onSuccess = { items ->
                    val validItems = items.filter { item ->
                        item.id != null &&
                        !item.name.isNullOrBlank()
                    }
                    if (validItems.isNotEmpty()) {
                        ImagePreloader.continueWatchingImages(
                            items = validItems,
                            mediaRepository = mediaRepository,
                            context = context,
                            maxItems = minOf(validItems.size, 12),
                            priorityCount = minOf(validItems.size, 6)
                        )
                    }
                    validItems
                },
                onFailure = { throw it }
            )
        }

        val nextUpQuery = useQuery(
            key = "home_next_up_api_v1",
            config = QueryConfig(
                staleTime = 60_000L,
                enabled = isTabActive && selectedCategory == HomeCategory.HOME && isNetworkAvailable && nextUpEnabled,
                retryCount = 2,
                retryDelay = 250L,
                requestTimeoutMs = networkRequestTimeoutMs
            )
        ) {
            val result = mediaRepository.getNextUpItems(limit = 24)
            result.fold(
                onSuccess = { items ->
                    val validItems = items
                        .asSequence()
                        .filter { item ->
                            item.id != null &&
                                (
                                    !item.name.isNullOrBlank() ||
                                        !item.episodeTitle.isNullOrBlank() ||
                                        !item.seriesName.isNullOrBlank()
                                    )
                        }
                        .distinctBy { it.id }
                        .toList()
                    if (validItems.isNotEmpty()) {
                        ImagePreloader.continueWatchingImages(
                            items = validItems,
                            mediaRepository = mediaRepository,
                            context = context,
                            maxItems = minOf(validItems.size, 12),
                            priorityCount = minOf(validItems.size, 6)
                        )
                    }
                    validItems
                },
                onFailure = { throw it }
            )
        }

        val homeLibraryBurstQuery = useQuery(
            key = "home_library_burst",
            config = QueryConfig(
                staleTime = 300_000L,
                enabled = isTabActive && selectedCategory == HomeCategory.HOME && isNetworkAvailable,
                retryCount = 1,
                retryDelay = 200L,
                requestTimeoutMs = networkRequestTimeoutMs
            )
        ) {
            val result = mediaRepository.getHomeLibrarySections(
                maxLibraries = null,
                itemsPerLibrary = 14
            )
            result.fold(
                onSuccess = { sections ->
                    sections.mapNotNull { section ->
                        val libraryId = section.library.id ?: return@mapNotNull null
                        HomeLibrarySectionUi(
                            libraryId = libraryId,
                            libraryName = section.library.name ?: "Library",
                            collectionType = section.library.collectionType,
                            items = section.items
                        )
                    }
                },
                onFailure = { throw it }
            )
        }

        val homeMyMediaLibrariesQuery = useQuery(
            key = "home_my_media_libraries",
            config = QueryConfig(
                staleTime = 300_000L,
                enabled = isTabActive && selectedCategory == HomeCategory.HOME && isNetworkAvailable,
                retryCount = 1,
                retryDelay = 200L,
                requestTimeoutMs = networkRequestTimeoutMs
            )
        ) {
            val result = mediaRepository.getUserViews()
            result.fold(
                onSuccess = { queryResult ->
                    queryResult.items
                        .orEmpty()
                        .asSequence()
                        .filter { library ->
                            val libraryId = library.id
                            val libraryName = library.name
                            val type = library.type
                            val collectionType = library.collectionType
                            libraryId != null &&
                                !libraryName.isNullOrBlank() &&
                                collectionType != "boxsets" &&
                                collectionType != "playlists" &&
                                collectionType != "folders" &&
                                (type == "CollectionFolder" || type == "Folder")
                        }
                        .distinctBy { it.id }
                        .sortedBy { it.sortName ?: it.name ?: "" }
                        .toList()
                },
                onFailure = { throw it }
            )
        }

        val persistedFeaturedItems = if (selectedCategory == HomeCategory.HOME && isNetworkAvailable) {
            persistedHomeSnapshot?.featuredHomeItems.orEmpty()
        } else {
            emptyList()
        }
        val FeaturedItems = featuredQuery.data ?: persistedFeaturedItems

        val persistedContinueWatchingItems = if (selectedCategory == HomeCategory.HOME && isNetworkAvailable && continueWatchingEnabled) {
            persistedHomeSnapshot?.continueWatchingItems.orEmpty()
        } else {
            emptyList()
        }
        val ContinueWatchingItems = continueWatchingQuery.data ?: persistedContinueWatchingItems

        val persistedNextUpItems = if (selectedCategory == HomeCategory.HOME && isNetworkAvailable && nextUpEnabled) {
            persistedHomeSnapshot?.nextUpItems.orEmpty()
        } else {
            emptyList()
        }
        val NextUpItems = nextUpQuery.data ?: persistedNextUpItems

        val persistedLibrarySections = if (selectedCategory == HomeCategory.HOME && isNetworkAvailable) {
            persistedHomeSnapshot?.homeLibrarySections
                .orEmpty()
                .map { it.toUiSection() }
        } else {
            emptyList()
        }
        val LibrarySections = homeLibraryBurstQuery.data ?: persistedLibrarySections

        val persistedMyMediaLibraries = if (selectedCategory == HomeCategory.HOME && isNetworkAvailable) {
            persistedHomeSnapshot?.myMediaLibraries.orEmpty()
        } else {
            emptyList()
        }
        val MyMediaLibraries = if (selectedCategory == HomeCategory.HOME) {
            homeMyMediaLibrariesQuery.data ?: persistedMyMediaLibraries
        } else {
            emptyList()
        }

        LaunchedEffect(isTabActive, isNetworkAvailable) {
            if (isTabActive && isNetworkAvailable) {
                queryManager.refreshStaleQueries()
            }
        }
        LaunchedEffect(isTabActive, selectedCategory, isNetworkAvailable) {
            if (isTabActive && selectedCategory == HomeCategory.HOME && isNetworkAvailable) {
                queryManager.invalidateQuery("home_library_burst")
                if (continueWatchingEnabled) {
                    val cachedContinueWatching =
                        queryManager.getQuery<List<BaseItemDto>>("continue_watching_resume_api_v2")
                    if (cachedContinueWatching.data.isNullOrEmpty()) {
                        queryManager.invalidateQuery("continue_watching_resume_api_v2")
                    }
                }
                if (nextUpEnabled) {
                    val cachedNextUp = queryManager.getQuery<List<BaseItemDto>>("home_next_up_api_v1")
                    if (cachedNextUp.data.isNullOrEmpty()) {
                        queryManager.invalidateQuery("home_next_up_api_v1")
                    }
                }
            }
        }
        LaunchedEffect(
            latestPlaybackStopEvent?.timestampMs,
            isTabActive,
            selectedCategory,
            isNetworkAvailable,
            continueWatchingEnabled,
            nextUpEnabled
        ) {
            if (
                latestPlaybackStopEvent == null ||
                !isTabActive ||
                selectedCategory != HomeCategory.HOME ||
                !isNetworkAvailable
            ) {
                return@LaunchedEffect
            }

            if (continueWatchingEnabled) {
                queryManager.invalidateQuery("continue_watching_resume_api_v2")
            }
            if (nextUpEnabled) {
                queryManager.invalidateQuery("home_next_up_api_v1")
            }
        }
        LaunchedEffect(continueWatchingQuery.data?.hashCode(), isNetworkAvailable) {
            if (!isNetworkAvailable) return@LaunchedEffect
            val items = continueWatchingQuery.data ?: return@LaunchedEffect
            mediaRepository.persistHomeSnapshot(continueWatchingItems = items)
            if (items.isEmpty()) return@LaunchedEffect
            ImagePreloader.continueWatchingImages(
                items = items,
                mediaRepository = mediaRepository,
                context = context,
                maxItems = items.size.coerceAtMost(4),
                priorityCount = 4
            )
        }

        LaunchedEffect(nextUpQuery.data?.hashCode(), isNetworkAvailable) {
            if (!isNetworkAvailable) return@LaunchedEffect
            val items = nextUpQuery.data ?: return@LaunchedEffect
            mediaRepository.persistHomeSnapshot(nextUpItems = items)
            if (items.isEmpty()) return@LaunchedEffect
            ImagePreloader.continueWatchingImages(
                items = items,
                mediaRepository = mediaRepository,
                context = context,
                maxItems = items.size.coerceAtMost(4),
                priorityCount = 4
            )
        }

        LaunchedEffect(featuredQuery.data?.hashCode(), selectedCategory, isNetworkAvailable) {
            if (selectedCategory != HomeCategory.HOME || !isNetworkAvailable) return@LaunchedEffect
            val items = featuredQuery.data ?: return@LaunchedEffect
            mediaRepository.persistHomeSnapshot(featuredHomeItems = items)
        }

        LaunchedEffect(homeLibraryBurstQuery.data?.hashCode(), isNetworkAvailable) {
            if (!isNetworkAvailable) return@LaunchedEffect
            val sections = homeLibraryBurstQuery.data ?: return@LaunchedEffect
            if (sections.isEmpty()) return@LaunchedEffect
            mediaRepository.persistHomeSnapshot(
                homeLibrarySections = sections.map { it.toPersistedSection() }
            )

            val orderedItems = sections
                .flatMap { section -> section.items }
                .distinctBy { it.id }
            if (orderedItems.isEmpty()) return@LaunchedEffect

            ImagePreloader.preloadCriticalImages(
                items = orderedItems,
                mediaRepository = mediaRepository,
                context = context,
                maxItems = orderedItems.size
            )
        }

        LaunchedEffect(homeMyMediaLibrariesQuery.data?.hashCode(), isNetworkAvailable) {
            if (!isNetworkAvailable) return@LaunchedEffect
            val libraries = homeMyMediaLibrariesQuery.data ?: return@LaunchedEffect
            mediaRepository.persistHomeSnapshot(myMediaLibraries = libraries)
        }

        LaunchedEffect(
            selectedCategory,
            MyMediaLibraries.hashCode(),
            isNetworkAvailable
        ) {
            if (selectedCategory != HomeCategory.HOME || !isNetworkAvailable) return@LaunchedEffect
            if (MyMediaLibraries.isEmpty()) return@LaunchedEffect
            ImagePreloader.MyMedia(
                libraries = MyMediaLibraries,
                mediaRepository = mediaRepository,
                context = context,
                maxItems = MyMediaLibraries.size
            )
        }

        val featureParallaxOffsetPx by remember {
            derivedStateOf {
                if (lazyColumnState.firstVisibleItemIndex == 0) {
                    lazyColumnState.firstVisibleItemScrollOffset.toFloat()
                } else {
                    0f
                }
            }
        }

        if (!isNetworkAvailable) {
            if (trackedDownloads.any { it.isOfflineAvailable }) {
                DownloadsScreen(
                    onBackPressed = {},
                    embedded = true,
                    onPlayItem = onNavigateToPlayer
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    OfflineState(serverName = currentServerName)
                }
            }
        }

        if (isNetworkAvailable) {
            LazyColumn(
                state = lazyColumnState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                verticalArrangement = Arrangement.spacedBy(1.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                if (featureCarouselEnabled) {
                    item(key = "feature_tab") {
                        FeatureTab(
                            featuredItems = FeaturedItems,
                            isLoading = featuredQuery.isLoading && FeaturedItems.isEmpty(),
                            error = featuredQuery.error,
                            selectedCategory = selectedCategory,
                            verticalParallaxOffsetPx = featureParallaxOffsetPx,
                            onItemClick = onNavigateToDetail,
                            onCategorySelected = { category ->
                                selectedCategory = category
                            },
                            onProfileClick = { showAccountOverview = true },
                            sidebarFocusRequester = sidebarFocusRequester,
                            initialChipFocusRequester = headerFocusRequester,
                            lastChipFocusRequester = headerEndFocusRequester,
                            heroActionFocusRequester = heroActionEntryFocusRequester,
                            contentFocusRequester = contentEntryFocusRequester,
                            onHeroZoneFocused = {
                                onHomeReturnTargetChanged?.invoke(heroActionEntryFocusRequester)
                            }
                        )
                    }
                } else {
                    item(key = "no_carousel_top_header") {
                        TopHeader(
                            serverName = currentServerName,
                            serverTypeRaw = currentServerType,
                            userName = HeaderUserName,
                            userImageUrl = noCarouselProfileImageUrl,
                            onProfileClick = { showAccountOverview = true },
                            onServerClick = serverSwitchDialogsState::openServers
                        )
                    }
                }

                val ShowMyMediaSection =
                    selectedCategory == HomeCategory.HOME && (
                        MyMediaLibraries.isNotEmpty() ||
                            (homeMyMediaLibrariesQuery.isLoading && MyMediaLibraries.isEmpty())
                        )

                val ShowContinueWatchingSection =
                    continueWatchingEnabled && selectedCategory == HomeCategory.HOME && (
                        ContinueWatchingItems.isNotEmpty() ||
                            continueWatchingQuery.isError ||
                            (continueWatchingQuery.isLoading && persistedContinueWatchingItems.isNotEmpty())
                        )

                val ShowNextUpSection =
                    nextUpEnabled && selectedCategory == HomeCategory.HOME && (
                        NextUpItems.isNotEmpty() ||
                            nextUpQuery.isError ||
                            (nextUpQuery.isLoading && persistedNextUpItems.isNotEmpty())
                        )

                if (ShowMyMediaSection) {
                    item(key = "my_media_section") {
                        HomeMyMediaSection(
                            libraries = MyMediaLibraries,
                            isLoading = homeMyMediaLibrariesQuery.isLoading && MyMediaLibraries.isEmpty(),
                            error = if (homeMyMediaLibrariesQuery.isError) homeMyMediaLibrariesQuery.error else null,
                            mediaRepository = mediaRepository,
                            disablePosterEnhancers = disablePosterEnhancers,
                            initialFocusRequester = if (ShowMyMediaSection) contentEntryFocusRequester else null,
                            returnFocusRequester = if (ShowMyMediaSection) heroActionEntryFocusRequester else null,
                            onReturnTargetChanged = onHomeReturnTargetChanged,
                            onLibraryClick = { library ->
                                val contentType = when (library.collectionType) {
                                    "movies" -> "MOVIES"
                                    "tvshows" -> "SERIES"
                                    else -> "ALL"
                                }
                                onNavigateToViewAll(
                                    contentType,
                                    library.id,
                                    library.name ?: "Library"
                                )
                            }
                        )
                    }
                }

                if (ShowContinueWatchingSection) {
                    item(key = "continue_watching_section") {
                        Column(
                            modifier = Modifier
                                .padding(top = 0.dp)
                                .offset(y = (-12).dp)
                        ) {
                            ContinueWatchingSection(
                                items = ContinueWatchingItems,
                                isLoading = continueWatchingQuery.isLoading && ContinueWatchingItems.isEmpty(),
                                error = if (continueWatchingQuery.isError) continueWatchingQuery.error else null,
                                initialFocusRequester = if (!ShowMyMediaSection) contentEntryFocusRequester else null,
                                returnFocusRequester = if (!ShowMyMediaSection) heroActionEntryFocusRequester else null,
                                onReturnTargetChanged = onHomeReturnTargetChanged,
                                onItemClick = onNavigateToDetail
                            )
                        }
                    }
                }

                if (ShowNextUpSection) {
                    item(key = "next_up_section") {
                        Column(
                            modifier = Modifier
                                .padding(top = 0.dp)
                                .offset(y = (-12).dp)
                        ) {
                            ContinueWatchingSection(
                                titleRes = R.string.dashboard_next_up,
                                errorMessageRes = R.string.dashboard_failed_next_up,
                                items = NextUpItems,
                                isLoading = nextUpQuery.isLoading && NextUpItems.isEmpty(),
                                error = if (nextUpQuery.isError) nextUpQuery.error else null,
                                initialFocusRequester = if (!ShowMyMediaSection && !ShowContinueWatchingSection) contentEntryFocusRequester else null,
                                returnFocusRequester = if (!ShowMyMediaSection && !ShowContinueWatchingSection) heroActionEntryFocusRequester else null,
                                onReturnTargetChanged = onHomeReturnTargetChanged,
                                onItemClick = onNavigateToDetail
                            )
                        }
                    }
                }

                if (selectedCategory == HomeCategory.HOME) {
                    val topPadding = if (!ShowMyMediaSection && !ShowContinueWatchingSection && !ShowNextUpSection) 16.dp else 0.dp

                    if (topPadding > 0.dp) {
                        item(key = "home_libraries_top_padding") {
                            Spacer(modifier = Modifier.height(topPadding))
                        }
                    }

                    val libraries = LibrarySections
                    if (homeLibraryBurstQuery.isError && libraries.isEmpty()) {
                        item(key = "home_libraries_error") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.dashboard_failed_load_libraries),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else if (libraries.isNotEmpty()) {
                        itemsIndexed(
                            items = libraries,
                            key = { index, section -> section.libraryId.ifBlank { "library_$index" } }
                        ) { index, section ->
                            BurstLibrarySection(
                                section = section,
                                mediaRepository = mediaRepository,
                                disablePosterEnhancers = disablePosterEnhancers,
                                initialFocusRequester = if (
                                    index == 0 &&
                                    !ShowMyMediaSection &&
                                    !ShowContinueWatchingSection &&
                                    !ShowNextUpSection
                                ) {
                                    contentEntryFocusRequester
                                } else {
                                    null
                                },
                                returnFocusRequester = if (
                                    index == 0 &&
                                        !ShowMyMediaSection &&
                                        !ShowContinueWatchingSection &&
                                        !ShowNextUpSection
                                ) {
                                    heroActionEntryFocusRequester
                                } else {
                                    null
                                },
                                onReturnTargetChanged = onHomeReturnTargetChanged,
                                onItemClick = onNavigateToDetail,
                                onNavigateToViewAll = onNavigateToViewAll
                            )

                            if (index < libraries.lastIndex) {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                } else if (selectedCategory == HomeCategory.MOVIES) {
                    val topPadding = if (ContinueWatchingItems.isEmpty() && !continueWatchingQuery.isLoading) 16.dp else 0.dp

                    item(key = "movies_genres") {
                        Column(
                            modifier = Modifier.padding(top = topPadding)
                        ) {
                            MovieGenreSections(
                                disablePosterEnhancers = disablePosterEnhancers,
                                initialFocusRequester = contentEntryFocusRequester,
                                returnFocusRequester = heroActionEntryFocusRequester,
                                onReturnTargetChanged = onHomeReturnTargetChanged,
                                onItemClick = onNavigateToDetail,
                                onNavigateToViewAll = onNavigateToViewAll
                            )
                        }
                    }
                } else if (selectedCategory == HomeCategory.TV_SHOWS) {
                    val topPadding = if (ContinueWatchingItems.isEmpty() && !continueWatchingQuery.isLoading) 16.dp else 0.dp

                    item(key = "tv_genres") {
                        Column(
                            modifier = Modifier.padding(top = topPadding)
                        ) {
                            TVShowGenreSections(
                                disablePosterEnhancers = disablePosterEnhancers,
                                initialFocusRequester = contentEntryFocusRequester,
                                returnFocusRequester = heroActionEntryFocusRequester,
                                onReturnTargetChanged = onHomeReturnTargetChanged,
                                onItemClick = onNavigateToDetail,
                                onNavigateToViewAll = onNavigateToViewAll
                            )
                        }
                    }
                }
            }
        }
    }

    }

    if (showAccountOverview) {
        AccountOverview(
            userName = currentUsername,
            serverName = currentServerName,
            profileImageUrl = currentProfileImageUrl ?: noCarouselProfileImageUrl,
            serverTypeRaw = currentServerType,
            canChangeUser = usersForCurrentServer.isNotEmpty(),
            onDismiss = { showAccountOverview = false },
            onChangeUser = {
                showAccountOverview = false
                serverSwitchDialogsState.openUsers(currentServerName, usersForCurrentServer)
            },
            onLogout = {
                showAccountOverview = false
                scope.launch {
                    authRepository.logout()
                    mediaRepository.clearPersistedHomeSnapshot()
                    CachedData.clearAllCache()
                    onLogout()
                }
            }
        )
    }

    ServerSwitchDialogsHost(
        state = serverSwitchDialogsState,
        savedServers = sessionSnapshot.savedServers,
        activeServerId = sessionSnapshot.activeServerId,
        currentServerName = sessionSnapshot.serverName,
        currentServerUrl = sessionSnapshot.serverUrl,
        isSwitching = serverSwitchUiState.isBusy,
        onAddServer = onAddServer,
        onAddUser = onAddUser,
        onServerSelected = { server, dismissDialog ->
            serverSwitchViewModel.switchServer(
                serverId = server.id,
                activeServerId = sessionSnapshot.activeServerId,
                onSwitchComplete = dismissDialog
            )
        },
        onRequestRemoveServer = serverSwitchDialogsState::requestRemoval,
        onRequestRemoveUser = serverSwitchDialogsState::requestRemoval,
        onRemoveServer = { serverId, onRemoveComplete ->
            serverSwitchViewModel.removeServer(
                serverId = serverId,
                onRemoveComplete = onRemoveComplete
            )
        }
    )
}

@Composable
private fun TopHeader(
    serverName: String?,
    serverTypeRaw: String?,
    userName: String?,
    userImageUrl: String?,
    onProfileClick: (() -> Unit)? = null,
    onServerClick: (() -> Unit)? = null
) {
    BrandHeader(
        serverName = serverName,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(top = 4.dp),
        showUserIcon = true,
        userName = userName,
        userImageUrl = userImageUrl,
        userServerTypeRaw = serverTypeRaw,
        onProfileClick = onProfileClick,
        onServerClick = onServerClick
    )
}

@Composable
private fun BrandHeader(
    serverName: String?,
    modifier: Modifier = Modifier,
    showUserIcon: Boolean = false,
    userName: String? = null,
    userImageUrl: String? = null,
    userServerTypeRaw: String? = null,
    onProfileClick: (() -> Unit)? = null,
    onServerClick: (() -> Unit)? = null
) {
    val displayServerName = serverName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.dashboard_server_fallback)
    val headerChipShape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(headerChipShape)
                .background(Color.White.copy(alpha = 0.14f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.10f),
                    shape = headerChipShape
                )
                .then(
                    if (onServerClick != null) {
                        Modifier.clickable(onClick = onServerClick)
                    } else {
                        Modifier
                    }
                )
                .padding(start = 8.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.jellycine_logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = displayServerName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }

        if (showUserIcon) {
            UserProfileAvatar(
                imageUrl = userImageUrl,
                serverTypeRaw = userServerTypeRaw,
                onClick = { onProfileClick?.invoke() },
                modifier = Modifier.size(34.dp)
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountOverview(
    userName: String?,
    serverName: String?,
    profileImageUrl: String?,
    serverTypeRaw: String?,
    canChangeUser: Boolean,
    onDismiss: () -> Unit,
    onChangeUser: () -> Unit,
    onLogout: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        scrimColor = Color.Black.copy(alpha = 0.76f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color.White.copy(alpha = 0.18f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                ) {
                    ProfileImageLoader(
                        imageUrl = profileImageUrl,
                        serverTypeRaw = serverTypeRaw,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = userName?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.settings_unknown_user),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = serverName?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.settings_unknown_server),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.68f)
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.10f))

            AccountActionRow(
                icon = Icons.Rounded.Person,
                label = stringResource(R.string.settings_change_user),
                tint = Color(0xFF22D3EE),
                enabled = canChangeUser,
                onClick = onChangeUser
            )

            HorizontalDivider(
                modifier = Modifier.padding(start = 34.dp),
                color = Color.White.copy(alpha = 0.08f)
            )

            AccountActionRow(
                icon = Icons.Rounded.ExitToApp,
                label = stringResource(R.string.logout),
                tint = Color(0xFFFF6B6B),
                onClick = onLogout
            )
        }
    }
}

@Composable
private fun AccountActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else Color.White.copy(alpha = 0.34f),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.34f),
            modifier = Modifier.weight(1f)
        )
        if (!enabled) {
            Text(
                text = stringResource(R.string.settings_unavailable),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.34f)
            )
        }
    }
}

@Composable
private fun OfflineState(
    serverName: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(top = 12.dp, bottom = 70.dp)
    ) {
        BrandHeader(serverName = serverName)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.dashboard_offline_empty_title),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.dashboard_offline_empty_message),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun HomeMyMediaSection(
    libraries: List<BaseItemDto>,
    isLoading: Boolean,
    error: String?,
    mediaRepository: MediaRepository,
    disablePosterEnhancers: Boolean,
    initialFocusRequester: FocusRequester? = null,
    returnFocusRequester: FocusRequester? = null,
    onReturnTargetChanged: ((FocusRequester) -> Unit)? = null,
    onLibraryClick: (BaseItemDto) -> Unit = {}
) {
    val lazyRowState = rememberLazyListState()
    val flingBehavior = ScrollOptimization.rememberUltraSmoothFlingBehavior()
    val firstLibraryId = libraries.firstOrNull()?.id

    LaunchedEffect(firstLibraryId, libraries.size) {
        val hasScrolled = lazyRowState.firstVisibleItemIndex > 0 ||
            lazyRowState.firstVisibleItemScrollOffset > 0
        if (hasScrolled) {
            lazyRowState.scrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        Text(
            text = stringResource(R.string.libraries),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when {
            isLoading -> Unit
            error != null && libraries.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_failed_load_my_media),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
            }
            libraries.isNotEmpty() -> {
                LazyRow(
                    state = lazyRowState,
                    horizontalArrangement = Arrangement.spacedBy(ScrollOptimization.listItemSpacing),
                    contentPadding = ScrollOptimization.optimizedContentPadding,
                    flingBehavior = flingBehavior,
                    modifier = ScrollOptimization.getScrollContainerModifier()
                ) {
                    items(
                        items = libraries,
                        key = { library -> library.id ?: library.name ?: "library_item" }
                    ) { library ->
                        val cardFocusRequester = remember(library.id, library.name) { FocusRequester() }
                        val stableOnClick = remember(library.id, library.name) { { onLibraryClick(library) } }
                        LibraryItemCard(
                            item = library,
                            cardModifier = entryCardModifier(
                                cardFocusRequester = cardFocusRequester,
                                initialFocusRequester = if (libraries.firstOrNull()?.id == library.id) initialFocusRequester else null,
                                returnFocusRequester = if (libraries.firstOrNull()?.id == library.id) returnFocusRequester else null,
                                onReturnTargetChanged = onReturnTargetChanged
                            ),
                            mediaRepository = mediaRepository,
                            disableImageEnhancers = disablePosterEnhancers,
                            useLandscapeLayout = true,
                            onClick = stableOnClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingSection(
    titleRes: Int = R.string.dashboard_continue_watching,
    errorMessageRes: Int = R.string.dashboard_failed_continue_watching,
    items: List<BaseItemDto>,
    isLoading: Boolean,
    error: String?,
    initialFocusRequester: FocusRequester? = null,
    returnFocusRequester: FocusRequester? = null,
    onReturnTargetChanged: ((FocusRequester) -> Unit)? = null,
    onItemClick: (BaseItemDto) -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black) // AMOLED black background
    ) {
        Text(
            text = stringResource(titleRes),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when {
            isLoading -> Unit

            items.isNotEmpty() -> {
                val lazyRowState = rememberLazyListState()
                val flingBehavior = ScrollOptimization.rememberUltraSmoothFlingBehavior()
                val firstItemId = items.firstOrNull()?.id
                var renderedCount by remember(items) {
                    mutableIntStateOf(items.size.coerceAtMost(4))
                }

                LaunchedEffect(items) {
                    renderedCount = items.size.coerceAtMost(4)
                }

                LaunchedEffect(firstItemId, items.size) {
                    val hasScrolled = lazyRowState.firstVisibleItemIndex > 0 ||
                        lazyRowState.firstVisibleItemScrollOffset > 0
                    if (hasScrolled) {
                        lazyRowState.scrollToItem(0)
                    }
                }

                LaunchedEffect(lazyRowState, items) {
                    snapshotFlow {
                        lazyRowState.firstVisibleItemIndex + lazyRowState.layoutInfo.visibleItemsInfo.size
                    }.collect { visibleEnd ->
                        if (visibleEnd >= renderedCount - 2 && renderedCount < items.size) {
                            renderedCount = (renderedCount + 4).coerceAtMost(items.size)
                        }
                    }
                }

                LazyRow(
                    state = lazyRowState,
                    horizontalArrangement = Arrangement.spacedBy(ScrollOptimization.listItemSpacing),
                    contentPadding = ScrollOptimization.optimizedContentPadding,
                    flingBehavior = flingBehavior,
                    modifier = ScrollOptimization.getScrollContainerModifier()
                ) {
                    items(
                        count = renderedCount.coerceAtMost(items.size),
                        key = { index ->
                            items.getOrNull(index)?.id ?: "item_$index"
                        }
                    ) { index ->
                        val item = items.getOrNull(index)
                        if (item != null) {
                            val cardFocusRequester = remember(item.id, index) { FocusRequester() }
                            val stableOnClick = remember(item.id) { { onItemClick(item) } }

                            Box {
                                ContinueWatchingCard(
                                    item = item,
                                    cardModifier = entryCardModifier(
                                        cardFocusRequester = cardFocusRequester,
                                        initialFocusRequester = if (index == 0) initialFocusRequester else null,
                                        returnFocusRequester = if (index == 0) returnFocusRequester else null,
                                        onReturnTargetChanged = onReturnTargetChanged
                                    ),
                                    mediaRepository = mediaRepository,
                                    onClick = stableOnClick
                                )
                            }
                        }
                    }
                }
            }

            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "!",
                            fontSize = 24.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(errorMessageRes),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = error,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )

                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContinueWatchingCard(
    item: BaseItemDto,
    cardModifier: Modifier = Modifier,
    mediaRepository: MediaRepository,
    onClick: () -> Unit = {}
) {
    val stableItem = remember(item.id) { StableBaseItem.from(item) }
    val unknownTitle = stringResource(R.string.search_result_unknown_title)
    val unknownEpisode = stringResource(R.string.search_result_unknown_episode)

    val itemName = remember(
        item.type,
        item.seriesName,
        item.seasonName,
        item.episodeTitle,
        stableItem.name
    ) {
        item.preferredDisplayTitle(
            unknownTitle = unknownTitle,
            unknownEpisode = unknownEpisode
        )
    }
    val metadataText = remember(
        item.type,
        item.productionYear,
        item.parentIndexNumber,
        item.indexNumber,
        item.episodeTitle,
        item.name,
        item.seriesName,
        item.seasonName
    ) {
        when (item.type) {
            "Movie" -> {
                item.productionYear?.toString().orEmpty()
            }
            "Episode" -> {
                item.episodeDisplaySubtitle()
            }
            "Series" -> {
                val year = item.productionYear ?: item.premiereDate?.take(4)?.toIntOrNull()
                year?.toString().orEmpty()
            }
            else -> ""
        }
    }
    val remainingText = remember(item.runTimeTicks, item.userData?.playbackPositionTicks) {
        val runtimeTicks = item.runTimeTicks
        val playbackPositionTicks = item.userData?.playbackPositionTicks ?: 0L
        if (runtimeTicks != null && runtimeTicks > 0L && playbackPositionTicks > 0L && playbackPositionTicks < runtimeTicks) {
            val remainingTicks = (runtimeTicks - playbackPositionTicks).coerceAtLeast(0L)
            "${CodecUtils.formatRuntime(remainingTicks)} left"
        } else {
            ""
        }
    }

    Column(
        modifier = Modifier
            .width(200.dp)
            .height(180.dp)
    ) {
        Card(
            modifier = cardModifier
                .width(200.dp)
                .height(120.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            onClick = onClick
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                ImageLoader(
                    itemId = stableItem.id,
                    seriesId = stableItem.seriesId,
                    imageType = "Thumb",
                    fallbackImageType = "Backdrop",
                    extraFallbackImageTypes = listOf("Primary"),
                    preferSeriesIdForThumbBackdrop = true,
                    allowRgb565 = false,
                    contentDescription = stableItem.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    cornerRadius = 12,
                    crossfadeMillis = 0,
                    mediaRepository = mediaRepository,
                    imageMetadata = item,
                    itemType = stableItem.type, // Pass item type for proper episode handling
                    imageTag = null
                )

                remainingText.takeIf { it.isNotBlank() }?.let { text ->
                    PosterTextBadge(
                        text = text,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 4.dp)
                    )
                }

                stableItem.userData?.playedPercentage?.let { percentage ->
                    val progress = remember(percentage) {
                        (percentage.toFloat() / 100f).coerceIn(0f, 1f)
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter),
                        color = Color.Red,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }

            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp) // Fixed height for title area
                .padding(top = 8.dp, start = 4.dp, end = 4.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = itemName,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (metadataText.isNotBlank()) {
                Text(
                    text = metadataText,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Stable
@Immutable
private data class HomeLibrarySectionUi(
    val libraryId: String,
    val libraryName: String,
    val collectionType: String?,
    val items: List<BaseItemDto>
)

private fun HomeLibrarySectionUi.toPersistedSection(): HomeLibrarySectionData {
    val library = BaseItemDto(
        id = libraryId,
        name = libraryName,
        collectionType = collectionType,
        type = "CollectionFolder"
    )
    return HomeLibrarySectionData(
        library = library,
        items = items
    )
}

private fun HomeLibrarySectionData.toUiSection(): HomeLibrarySectionUi {
    return HomeLibrarySectionUi(
        libraryId = library.id.orEmpty(),
        libraryName = library.name.orEmpty(),
        collectionType = library.collectionType,
        items = items
    )
}

private fun entryCardModifier(
    cardFocusRequester: FocusRequester,
    initialFocusRequester: FocusRequester?,
    returnFocusRequester: FocusRequester?,
    onReturnTargetChanged: ((FocusRequester) -> Unit)? = null
): Modifier {
    val dpadFocusRequester = initialFocusRequester ?: cardFocusRequester
    return Modifier
        .focusRequester(dpadFocusRequester)
        .onFocusChanged { state ->
            if (state.isFocused) {
                onReturnTargetChanged?.invoke(dpadFocusRequester)
            }
        }
        .then(
            if (returnFocusRequester != null) {
                Modifier.focusProperties {
                    up = returnFocusRequester
                }
            } else {
                Modifier
            }
        )
}


@Composable
private fun BurstLibrarySection(
    section: HomeLibrarySectionUi,
    mediaRepository: MediaRepository,
    disablePosterEnhancers: Boolean,
    initialFocusRequester: FocusRequester? = null,
    returnFocusRequester: FocusRequester? = null,
    onReturnTargetChanged: ((FocusRequester) -> Unit)? = null,
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    val libraryRowState = rememberLazyListState()
    val libraryFlingBehavior = ScrollOptimization.rememberUltraSmoothFlingBehavior()
    val firstSectionItemId = section.items.firstOrNull()?.id
    val sectionTitle = section.libraryName.ifBlank { stringResource(R.string.my_media_library_fallback) }

    LaunchedEffect(firstSectionItemId, section.items.size) {
        val hasScrolled = libraryRowState.firstVisibleItemIndex > 0 ||
            libraryRowState.firstVisibleItemScrollOffset > 0
        if (hasScrolled) {
            libraryRowState.scrollToItem(0)
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dashboard_recently_added, sectionTitle),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = {
                    val contentType = when (section.collectionType) {
                        "movies" -> "MOVIES"
                        "tvshows" -> "SERIES"
                        else -> "ALL"
                    }
                    onNavigateToViewAll(contentType, section.libraryId, sectionTitle)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.dashboard_view_all),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (section.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.dashboard_no_items_found),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
            return
        }

        LazyRow(
            state = libraryRowState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            flingBehavior = libraryFlingBehavior,
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = section.items,
                key = { index, item -> item.id ?: "${section.libraryId}_$index" }
            ) { index, item ->
                val cardFocusRequester = remember(item.id, index) { FocusRequester() }
                val stableOnClick = remember(item.id, item.name) { { onItemClick(item) } }
                val isVisible by remember(libraryRowState, index) {
                    derivedStateOf {
                        libraryRowState.layoutInfo.visibleItemsInfo.any { it.index == index }
                    }
                }
                var hasEntered by remember(item.id ?: "${section.libraryId}_$index") {
                    mutableStateOf(false)
                }
                LaunchedEffect(isVisible) {
                    if (isVisible && !hasEntered) {
                        hasEntered = true
                    }
                }
                val entranceProgress by animateFloatAsState(
                    targetValue = if (hasEntered) 1f else 0.88f,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    label = "burst_library_item_entrance"
                )

                LibraryItemCard(
                    item = item,
                    modifier = Modifier.graphicsLayer {
                        alpha = entranceProgress
                        translationX = (1f - entranceProgress) * 26f
                        val scale = 0.96f + (0.04f * entranceProgress)
                        scaleX = scale
                        scaleY = scale
                    },
                    cardModifier = entryCardModifier(
                        cardFocusRequester = cardFocusRequester,
                        initialFocusRequester = if (index == 0) initialFocusRequester else null,
                        returnFocusRequester = if (index == 0) returnFocusRequester else null,
                        onReturnTargetChanged = onReturnTargetChanged
                    ),
                    mediaRepository = mediaRepository,
                    disableImageEnhancers = disablePosterEnhancers,
                    onClick = stableOnClick
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryItemCard(
    item: BaseItemDto,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
    mediaRepository: MediaRepository,
    disableImageEnhancers: Boolean = false,
    useLandscapeLayout: Boolean = false,
    onClick: () -> Unit = {}
) {
    val stableItem = remember(item.id) { StableBaseItem.from(item) }
    val unknownTitle = stringResource(R.string.search_result_unknown_title)
    val unknownEpisode = stringResource(R.string.search_result_unknown_episode)

    val displayName = item.preferredDisplayTitle(
        unknownTitle = unknownTitle,
        unknownEpisode = unknownEpisode
    )

    val cardWidth = if (useLandscapeLayout) 200.dp else 112.dp
    val cardHeight = if (useLandscapeLayout) 182.dp else 214.dp
    val imageHeight = if (useLandscapeLayout) 120.dp else 166.dp
    val titleAreaHeight = if (useLandscapeLayout) 64.dp else 46.dp
    val titleTopPadding = if (useLandscapeLayout) 10.dp else 4.dp
    val titleFontSize = if (useLandscapeLayout) 16.sp else 13.sp
    val titleLineHeight = if (useLandscapeLayout) 18.sp else 15.sp
    val titleMaxLines = if (useLandscapeLayout) 2 else 1
    val metadataFontSize = if (useLandscapeLayout) 14.sp else 12.sp
    val metadataLineHeight = if (useLandscapeLayout) 16.sp else 13.sp

    Column(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
    ) {
        Card(
            modifier = cardModifier
                .width(cardWidth)
                .height(imageHeight),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            onClick = onClick
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                ImageLoader(
                    itemId = stableItem.id,
                    seriesId = stableItem.seriesId,
                    imageType = if (useLandscapeLayout) "Thumb" else "Primary",
                    fallbackImageType = if (useLandscapeLayout) "Backdrop" else null,
                    extraFallbackImageTypes = if (useLandscapeLayout) listOf("Primary") else emptyList(),
                    preferSeriesIdForThumbBackdrop = useLandscapeLayout,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    cornerRadius = 12,
                    crossfadeMillis = 0,
                    mediaRepository = mediaRepository,
                    imageMetadata = item,
                    itemType = stableItem.type, // Pass item type for proper episode handling
                    hasImageEnhancers = !disableImageEnhancers,
                    imageTag = null
                )

                val episodeCount = when {
                    item.type == "Series" && item.episodeCount != null && item.episodeCount!! > 0 -> item.episodeCount!!
                    item.type == "Series" && item.recursiveItemCount != null && item.recursiveItemCount!! > 0 -> item.recursiveItemCount!!
                    else -> null
                }

                episodeCount?.let { count ->
                    PosterCountBadge(
                        count = count,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 4.dp)
                    )
                }

            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(titleAreaHeight)
                .padding(top = titleTopPadding, start = 4.dp, end = 4.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = displayName,
                    color = Color.White,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = titleLineHeight,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                val metadataText = buildMetadataText(item)
                if (metadataText.isNotEmpty()) {
                    Text(
                        text = metadataText,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = metadataFontSize,
                        lineHeight = metadataLineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun buildMetadataText(item: BaseItemDto): String {
    val StartYear = item.productionYear ?: item.premiereDate
        ?.take(4)
        ?.toIntOrNull()
    return buildString {
        when (item.type) {
            "Series" -> {
                StartYear?.let { startYear ->
                    append(startYear.toString())
                    item.endDate?.let { endDate ->
                        val endYear = endDate.take(4).toIntOrNull()
                        if (endYear != null && endYear != startYear) {
                            append(" - $endYear")
                        }
                    } ?: run {
                    }
                }
            }
            "Movie" -> {
                StartYear?.let { year ->
                    append(year.toString())
                }
            }
            "Episode" -> {
                val seasonNumber = item.parentIndexNumber
                val episodeNumber = item.indexNumber
                if (seasonNumber != null && episodeNumber != null) {
                    append("S${seasonNumber}:E${episodeNumber}")
                    item.name?.let { episodeName ->
                        if (episodeName != item.seriesName) {
                            append(" - ${episodeName.take(20)}")
                            if (episodeName.length > 20) append("...")
                        }
                    }
                }
            }
            else -> {
                StartYear?.let { year ->
                    append(year.toString())
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun DashboardPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Dashboard()
    }
}

private object Cache {
    private val cache = mutableMapOf<String, CacheEntry<Any>>()
    private const val MAX_CACHE_SIZE = 50
    private const val MEMORY_CLEANUP_INTERVAL = 300_000L // 5 minutes
    private var lastCleanup = 0L

    @Stable
    @Immutable
    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long,
        val accessCount: Int = 0,
        val lastAccessed: Long = System.currentTimeMillis()
    )

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        cleanupIfNeeded()
        val entry = cache[key] as? CacheEntry<T>
        if (entry != null) {
            cache[key] = entry.copy(
                accessCount = entry.accessCount + 1,
                lastAccessed = System.currentTimeMillis()
            ) as CacheEntry<Any>
        }
        return entry?.data
    }

    fun <T> put(key: String, data: T) {
        cleanupIfNeeded()

        if (cache.size >= MAX_CACHE_SIZE) {
            val oldestKey = cache.entries
                .minByOrNull { it.value.lastAccessed }
                ?.key
            oldestKey?.let { cache.remove(it) }
        }

        cache[key] = CacheEntry(
            data = data as Any,
            timestamp = System.currentTimeMillis()
        )
    }

    fun isStale(key: String, staleTime: Long): Boolean {
        val entry = cache[key] ?: return true
        return System.currentTimeMillis() - entry.timestamp > staleTime
    }

    fun invalidate(key: String) {
        cache.remove(key)
    }

    fun invalidatePattern(pattern: String) {
        val keysToRemove = cache.keys.filter { it.contains(pattern) }
        keysToRemove.forEach { cache.remove(it) }
    }

    private fun cleanupIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastCleanup > MEMORY_CLEANUP_INTERVAL) {
            val cutoff = now - 3_600_000L
            val keysToRemove = cache.entries
                .filter { it.value.lastAccessed < cutoff }
                .map { it.key }
            keysToRemove.forEach { cache.remove(it) }
            lastCleanup = now
        }
    }

    fun clear() {
        cache.clear()
    }

    fun getStats(): String {
        return "Cache size: ${cache.size}/$MAX_CACHE_SIZE"
    }
}

private object GenreCache {
    fun RefreshMovieGenres(): Boolean {
        return Cache.isStale("movie_genres", 600_000L)
    }

    fun RefreshTVGenres(): Boolean {
        return Cache.isStale("tv_genres", 600_000L)
    }

    fun RefreshGenreItems(genreId: String): Boolean {
        val cacheKey = "genre_items_$genreId"
        if (Cache.isStale(cacheKey, 300_000L)) {
            return true
        }
        val cachedItems = Cache.get<List<BaseItemDto>>(cacheKey) ?: return true
        if (cachedItems.isEmpty()) {
            return true
        }
        val hasYearMetadata = cachedItems.any { item ->
            item.productionYear != null ||
                !item.premiereDate.isNullOrBlank() ||
                !item.endDate.isNullOrBlank()
        }
        return !hasYearMetadata
    }

    fun updateMovieGenres(genres: List<BaseItemDto>) {
        Cache.put("movie_genres", genres)
    }

    fun updateTVGenres(genres: List<BaseItemDto>) {
        Cache.put("tv_genres", genres)
    }

    fun updateGenreItems(genreId: String, items: List<BaseItemDto>) {
        Cache.put("genre_items_$genreId", items)
    }

    fun getGenreItems(genreId: String): List<BaseItemDto> {
        return Cache.get("genre_items_$genreId") ?: emptyList()
    }

    val movieGenres: List<BaseItemDto>
        get() = Cache.get("movie_genres") ?: emptyList()

    val tvGenres: List<BaseItemDto>
        get() = Cache.get("tv_genres") ?: emptyList()
}

private object LibraryCache {
    fun RefreshLibraryViews(): Boolean {
        return Cache.isStale("library_views", 600_000L)
    }

    fun updateLibraryViews(views: List<BaseItemDto>) {
        Cache.put("library_views", views)
    }

    fun RefreshLibraryItems(libraryId: String): Boolean {
        return Cache.isStale("library_$libraryId", 300_000L)
    }

    fun updateLibraryItems(libraryId: String, items: List<BaseItemDto>) {
        Cache.put("library_$libraryId", items)
    }

    fun getLibraryItems(libraryId: String): List<BaseItemDto> {
        return Cache.get("library_$libraryId") ?: emptyList()
    }

    val libraryViews: List<BaseItemDto>
        get() = Cache.get("library_views") ?: emptyList()
}

@Composable
private fun MovieGenreSections(
    disablePosterEnhancers: Boolean,
    initialFocusRequester: FocusRequester? = null,
    returnFocusRequester: FocusRequester? = null,
    onReturnTargetChanged: ((FocusRequester) -> Unit)? = null,
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }

    var movieGenres by remember { mutableStateOf(GenreCache.movieGenres) }
    var isLoading by remember { mutableStateOf(GenreCache.RefreshMovieGenres()) }

    LaunchedEffect(Unit) {
        if (GenreCache.RefreshMovieGenres()) {
            isLoading = true
            try {
                withContext(Dispatchers.IO) {
                    val result = mediaRepository.getFilteredGenres(includeItemTypes = "Movie")

                    withContext(Dispatchers.Main) {
                        result.fold(
                            onSuccess = { genres ->
                                GenreCache.updateMovieGenres(genres)
                                movieGenres = genres
                                isLoading = false
                            },
                            onFailure = { error ->
                                isLoading = false
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                isLoading = false
            }
        } else {
            movieGenres = GenreCache.movieGenres
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        if (isLoading) {
            GenreSectionSkeleton(sectionCount = 6)
        } else {
            movieGenres.forEachIndexed { index, genre ->
                ProgressiveMovieGenreSection(
                    genre = genre,
                    mediaRepository = mediaRepository,
                    disablePosterEnhancers = disablePosterEnhancers,
                    initialFocusRequester = if (index == 0) initialFocusRequester else null,
                    returnFocusRequester = if (index == 0) returnFocusRequester else null,
                    onReturnTargetChanged = onReturnTargetChanged,
                    onItemClick = onItemClick,
                    onNavigateToViewAll = onNavigateToViewAll
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TVShowGenreSections(
    disablePosterEnhancers: Boolean,
    initialFocusRequester: FocusRequester? = null,
    returnFocusRequester: FocusRequester? = null,
    onReturnTargetChanged: ((FocusRequester) -> Unit)? = null,
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }

    var tvGenres by remember { mutableStateOf(GenreCache.tvGenres) }
    var isLoading by remember { mutableStateOf(GenreCache.RefreshTVGenres()) }

    LaunchedEffect(Unit) {
        if (GenreCache.RefreshTVGenres()) {
            isLoading = true
            try {
                val result = mediaRepository.getFilteredGenres(includeItemTypes = "Series")
                result.fold(
                    onSuccess = { genres ->
                        GenreCache.updateTVGenres(genres)
                        tvGenres = genres
                        isLoading = false
                    },
                    onFailure = { error ->
                        isLoading = false
                    }
                )
            } catch (e: Exception) {
                isLoading = false
            }
        } else {
            tvGenres = GenreCache.tvGenres
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        if (isLoading) {
            GenreSectionSkeleton(sectionCount = 6)
        } else {
            tvGenres.forEachIndexed { index, genre ->
                ProgressiveTVShowGenreSection(
                    genre = genre,
                    mediaRepository = mediaRepository,
                    disablePosterEnhancers = disablePosterEnhancers,
                    initialFocusRequester = if (index == 0) initialFocusRequester else null,
                    returnFocusRequester = if (index == 0) returnFocusRequester else null,
                    onReturnTargetChanged = onReturnTargetChanged,
                    onItemClick = onItemClick,
                    onNavigateToViewAll = onNavigateToViewAll
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ProgressiveMovieGenreSection(
    genre: BaseItemDto,
    mediaRepository: MediaRepository,
    disablePosterEnhancers: Boolean,
    initialFocusRequester: FocusRequester? = null,
    returnFocusRequester: FocusRequester? = null,
    onReturnTargetChanged: ((FocusRequester) -> Unit)? = null,
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    val genreId = genre.id ?: return // Skip if no genre ID
    var genreMovies by remember(genreId) { mutableStateOf(GenreCache.getGenreItems(genreId)) }
    var isLoading by remember(genreId) { mutableStateOf(GenreCache.RefreshGenreItems(genreId)) }
    val genreTitle = genre.name ?: stringResource(R.string.movies)

    LaunchedEffect(genreId) {
        if (GenreCache.RefreshGenreItems(genreId)) {
            isLoading = true
            try {
                val result = withContext(Dispatchers.IO) {
                    mediaRepository.getItemsByGenre(
                        genreId = genreId,
                        includeItemTypes = "Movie",
                        limit = 20
                    )
                }

                result.fold(
                    onSuccess = { items ->
                        val validItems = items.filter {
                            it.id != null && !it.name.isNullOrBlank()
                        }
                        GenreCache.updateGenreItems(genreId, validItems)
                        genreMovies = validItems
                    },
                    onFailure = {
                        genreMovies = emptyList()
                    }
                )
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
            }
        } else {
            genreMovies = GenreCache.getGenreItems(genreId)
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = genreTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            IconButton(
                onClick = {
                    onNavigateToViewAll("MOVIES_GENRE", genre.id, genreTitle)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.dashboard_view_all),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        when {
            isLoading -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    items(4) { index ->
                        PosterSkeleton()
                    }
                }
            }
            genreMovies.isNotEmpty() -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    items(
                        count = genreMovies.size,
                        key = { index -> genreMovies[index].id ?: index }
                    ) { index ->
                        val cardFocusRequester = remember(genreMovies[index].id, index) { FocusRequester() }
                        LibraryItemCard(
                            item = genreMovies[index],
                            cardModifier = entryCardModifier(
                                cardFocusRequester = cardFocusRequester,
                                initialFocusRequester = if (index == 0) initialFocusRequester else null,
                                returnFocusRequester = if (index == 0) returnFocusRequester else null,
                                onReturnTargetChanged = onReturnTargetChanged
                            ),
                            mediaRepository = mediaRepository,
                            disableImageEnhancers = disablePosterEnhancers,
                            onClick = { onItemClick(genreMovies[index]) }
                        )
                    }
                }
            }
            else -> {
                if (!isLoading && genreMovies.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dashboard_no_movies_found),
                        color = Color.Gray.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressiveTVShowGenreSection(
    genre: BaseItemDto,
    mediaRepository: MediaRepository,
    disablePosterEnhancers: Boolean,
    initialFocusRequester: FocusRequester? = null,
    returnFocusRequester: FocusRequester? = null,
    onReturnTargetChanged: ((FocusRequester) -> Unit)? = null,
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    val genreId = genre.id ?: return // Skip if no genre ID
    var genreShows by remember(genreId) { mutableStateOf(GenreCache.getGenreItems("tv_$genreId")) }
    var isLoading by remember(genreId) { mutableStateOf(GenreCache.RefreshGenreItems("tv_$genreId")) }
    val genreTitle = genre.name ?: stringResource(R.string.tv_shows)

    LaunchedEffect(genreId) {
        val cacheKey = "tv_$genreId"
        if (GenreCache.RefreshGenreItems(cacheKey)) {
            isLoading = true
            try {
                val result = withContext(Dispatchers.IO) {
                    mediaRepository.getItemsByGenre(
                        genreId = genreId,
                        includeItemTypes = "Series",
                        limit = 20
                    )
                }

                result.fold(
                    onSuccess = { items ->
                        val validItems = items.filter {
                            it.id != null && !it.name.isNullOrBlank()
                        }
                        GenreCache.updateGenreItems(cacheKey, validItems)
                        genreShows = validItems
                    },
                    onFailure = {
                        genreShows = emptyList()
                    }
                )
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
            }
        } else {
            genreShows = GenreCache.getGenreItems("tv_$genreId")
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = genreTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            IconButton(
                onClick = {
                    onNavigateToViewAll("TVSHOWS_GENRE", genre.id, genreTitle)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.dashboard_view_all),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        when {
            isLoading -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(4) {
                        PosterSkeleton()
                    }
                }
            }
            genreShows.isNotEmpty() -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = genreShows,
                        key = { show -> show.id ?: "${show.name}_${show.type}" }
                    ) { show ->
                        val cardFocusRequester = remember(show.id, show.name) { FocusRequester() }
                        LibraryItemCard(
                            item = show,
                            cardModifier = entryCardModifier(
                                cardFocusRequester = cardFocusRequester,
                                initialFocusRequester = if (genreShows.firstOrNull()?.id == show.id) initialFocusRequester else null,
                                returnFocusRequester = if (genreShows.firstOrNull()?.id == show.id) returnFocusRequester else null,
                                onReturnTargetChanged = onReturnTargetChanged
                            ),
                            mediaRepository = mediaRepository,
                            disableImageEnhancers = disablePosterEnhancers,
                            onClick = { onItemClick(show) }
                        )
                    }
                }
            }
            else -> {
                if (!isLoading && genreShows.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dashboard_no_tv_shows_found),
                        color = Color.Gray.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
