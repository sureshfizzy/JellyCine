package com.jellycine.app.ui.screens.dashboard.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.rounded.PlayArrow
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
import com.jellycine.app.util.image.JellyfinPosterImage
import com.jellycine.app.ui.components.common.*
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.UserItemDataDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import com.jellycine.app.ui.screens.dashboard.PosterSkeleton
import com.jellycine.app.ui.screens.dashboard.ContinueWatchingSkeleton
import com.jellycine.app.ui.screens.dashboard.GenreSectionSkeleton
import androidx.compose.foundation.gestures.ScrollableDefaults
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.ExperimentalFoundationApi
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.CachePolicy
import coil.size.Precision
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
    val requestTimeoutMs: Long = 12_000L
)

class QueryManager(private val scope: CoroutineScope) {
    private val queries = mutableMapOf<String, QueryState<Any>>()
    private val jobs = mutableMapOf<String, Job>()

    @Suppress("UNCHECKED_CAST")
    fun <T> getQuery(key: String): QueryState<T> {
        return queries[key] as? QueryState<T> ?: QueryState()
    }

    fun <T> setQuery(key: String, state: QueryState<T>) {
        queries[key] = state as QueryState<Any>
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

                    val shouldRetry = when {
                        e.message?.contains("timeout", ignoreCase = true) == true -> true
                        e.message?.contains("connection", ignoreCase = true) == true -> true
                        e.message?.contains("network", ignoreCase = true) == true -> true
                        e.message?.contains("failed to connect", ignoreCase = true) == true -> true
                        e.message?.contains("unable to resolve host", ignoreCase = true) == true -> true
                        e is TimeoutCancellationException -> false
                        retryCount <= config.retryCount -> true
                        else -> false
                    }

                    if (shouldRetry && retryCount <= config.retryCount) {
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
            queries[key] = currentState.copy(isStale = true)
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
        if (config.enabled) {
            hasInitiated = true
            val newState = queryManager.executeQuery(key, config, fetcher)
            state = newState

            while (true) {
                delay(24)
                val currentState = queryManager.getQuery<T>(key)
                if (currentState != state) {
                    state = currentState
                }
                if (!currentState.isLoading) break
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
    private val prefetchSemaphore = Semaphore(64)
    private const val posterWidth = 240
    private const val posterHeight = 360
    private const val posterQuality = 80
    private const val continueWatchingWidth = 640
    private const val continueWatchingHeight = 360
    private const val continueWatchingQuality = 92

    private fun imageCacheKey(
        itemId: String,
        imageType: String,
        width: Int,
        height: Int,
        quality: Int
    ): String = "$itemId|$imageType|$width|$height|$quality"

    fun getCachedImageUrl(
        itemId: String,
        imageType: String,
        width: Int,
        height: Int,
        quality: Int
    ): String? {
        return imageUrlCache[imageCacheKey(itemId, imageType, width, height, quality)]
    }

    fun putCachedImageUrl(
        itemId: String,
        imageType: String,
        width: Int,
        height: Int,
        quality: Int,
        imageUrl: String
    ) {
        imageUrlCache[imageCacheKey(itemId, imageType, width, height, quality)] = imageUrl
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

    suspend fun preloadContinueWatchingImages(
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

    private suspend fun preloadContinueWatchingItem(
        item: BaseItemDto,
        mediaRepository: MediaRepository,
        imageLoader: coil.ImageLoader,
        context: android.content.Context
    ) {
        val itemId = item.id ?: return
        val itemType = item.type
        val requestIds = listOfNotNull(itemId, item.seriesId).distinct()
        val preloadKey = "cw_preload_${itemId}_${item.seriesId.orEmpty()}"
        if (!preloadedUrls.add(preloadKey)) return

        try {
            val imageTypes = listOf("Thumb", "Backdrop")
            var selectedType: String? = null
            var selectedUrl: String? = null

            for (type in imageTypes) {
                for (requestId in requestIds) {
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

                    val resolvedUrl = mediaRepository.getImageUrlString(
                        itemId = requestId,
                        imageType = type,
                        width = continueWatchingWidth,
                        height = continueWatchingHeight,
                        quality = continueWatchingQuality
                    )
                    if (!resolvedUrl.isNullOrBlank()) {
                        putCachedImageUrl(
                            itemId = requestId,
                            imageType = type,
                            width = continueWatchingWidth,
                            height = continueWatchingHeight,
                            quality = continueWatchingQuality,
                            imageUrl = resolvedUrl
                        )
                        selectedType = type
                        selectedUrl = resolvedUrl
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
    preferSeriesIdForThumbBackdrop: Boolean = true,
    allowRgb565: Boolean = true,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Int = 8,
    crossfadeMillis: Int = 60,
    mediaRepository: com.jellycine.data.repository.MediaRepository,
    itemType: String? = null // Add item type to handle episodes properly
) {
    val imageTypes = remember(imageType, fallbackImageType) {
        buildList {
            add(imageType)
            if (!fallbackImageType.isNullOrBlank() && !fallbackImageType.equals(imageType, ignoreCase = true)) {
                add(fallbackImageType)
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
            else -> Triple(240, 360, 80)
        }
    }
    val initialCachedUrl = remember(actualItemId, currentImageType, width, height) {
        if (actualItemId.isNullOrBlank()) {
            null
        } else {
            ImagePreloader.getCachedImageUrl(
                itemId = actualItemId,
                imageType = currentImageType,
                width = width,
                height = height,
                quality = quality
            )
        }
    }
    var imageUrl by remember(actualItemId, currentImageType) { mutableStateOf(initialCachedUrl) }
    var hasError by remember(actualItemId, currentImageType) { mutableStateOf(false) }
    var isImageLoaded by remember(actualItemId, currentImageType) { mutableStateOf(!initialCachedUrl.isNullOrBlank()) }
    val context = LocalContext.current

    LaunchedEffect(actualItemId, currentImageType) {
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
                    quality = quality
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
                val resolvedUrl = withContext(Dispatchers.IO) {
                    mediaRepository.getImageUrlString(
                        itemId = actualItemId,
                        imageType = currentImageType,
                        width = width,
                        height = height,
                        quality = quality
                    )
                }
                if (!resolvedUrl.isNullOrBlank()) {
                    imageUrl = resolvedUrl
                    ImagePreloader.putCachedImageUrl(
                        itemId = actualItemId,
                        imageType = currentImageType,
                        width = width,
                        height = height,
                        quality = quality,
                        imageUrl = resolvedUrl
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
                        }
                        is AsyncImagePainter.State.Error -> {
                            if (hasMoreFallbacks) {
                                imageUrl = null
                                imageTypeIndex += 1
                                hasError = false
                                isImageLoaded = false
                            } else {
                                hasError = true
                                isImageLoaded = false
                            }
                        }
                        else -> {
                            isImageLoaded = false
                        }
                    }
                }
            )
        }

        if (!isImageLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius.dp))
                    .background(Color(0xFF2A2E37))
            )
        }

        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius.dp))
                    .background(Color(0xFF242833))
            )
        }
    }
}

val LocalQueryManager = compositionLocalOf<QueryManager> {
    error("QueryManager not provided")
}

private object DashboardHomeQueryStore {
    private var ownerUsername: String? = null
    private var manager: QueryManager? = null

    @Synchronized
    fun get(username: String?): QueryManager {
        val existing = manager
        if (existing != null) {
            if (username == null) {
                return existing
            }
            if (ownerUsername == username) {
                return existing
            }
        }
        manager?.cleanup()
        ownerUsername = username
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

@Composable
fun Dashboard(
    onLogout: () -> Unit = {},
    onNavigateToDetail: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> },
    isTabActive: Boolean = true
) {
    var selectedCategory by rememberSaveable { mutableStateOf("Home") }
    val context = LocalContext.current
    val mediaRepository = remember { com.jellycine.data.repository.MediaRepositoryProvider.getInstance(context) }
    val authRepository = remember { com.jellycine.data.repository.AuthRepositoryProvider.getInstance(context) }

    val currentUsername by authRepository.getUsername().collectAsState(initial = null)
    val queryManager = remember(currentUsername) {
        DashboardHomeQueryStore.get(currentUsername)
    }
    var previousUsername by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentUsername) {
        if (previousUsername != null && previousUsername != currentUsername) {
            Cache.clear()
        }
        previousUsername = currentUsername
    }

    val lazyColumnState = rememberLazyListState()

    CompositionLocalProvider(LocalQueryManager provides queryManager) {

        val featuredQuery = useQuery(
            key = "featured_$selectedCategory",
            config = QueryConfig(
                staleTime = 300_000L,
                enabled = isTabActive
            )
        ) {
            val result = when (selectedCategory) {
                "Movies" -> mediaRepository.getUserItems(
                    parentId = null,
                    includeItemTypes = "Movie",
                    sortBy = "Random",
                    sortOrder = null,
                    limit = 10,
                    startIndex = 0,
                    recursive = true,
                    fields = "BasicSyncInfo,Genres,CommunityRating,CriticRating,ProductionYear,PremiereDate,OfficialRating,Overview"
                )
                "TV Shows" -> mediaRepository.getUserItems(
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
                    query.items.orEmpty().filter { it.id != null && !it.name.isNullOrBlank() }
                },
                onFailure = { throw it }
            )
        }

        val continueWatchingQuery = useQuery(
            key = "continue_watching_resume_api_v2",
            config = QueryConfig(
                staleTime = 60_000L,
                enabled = isTabActive && selectedCategory == "Home",
                retryCount = 2,
                retryDelay = 250L
            )
        ) {
            val result = mediaRepository.getResumeItems()
            result.fold(
                onSuccess = { items ->
                    val validItems = items.filter { item ->
                        item.id != null &&
                        !item.name.isNullOrBlank()
                    }
                    if (validItems.isNotEmpty()) {
                        ImagePreloader.preloadContinueWatchingImages(
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
                enabled = isTabActive && selectedCategory == "Home",
                retryCount = 1,
                retryDelay = 200L
            )
        ) {
            val result = mediaRepository.getHomeLibrarySections(
                maxLibraries = 36,
                itemsPerLibrary = 30
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

        LaunchedEffect(isTabActive) {
            if (isTabActive) {
                queryManager.refreshStaleQueries()
            }
        }
        LaunchedEffect(isTabActive, selectedCategory) {
            if (isTabActive && selectedCategory == "Home") {
                val cachedContinueWatching =
                    queryManager.getQuery<List<BaseItemDto>>("continue_watching_resume_api_v2")
                if (cachedContinueWatching.data.isNullOrEmpty()) {
                    queryManager.invalidateQuery("continue_watching_resume_api_v2")
                }
            }
        }
        LaunchedEffect(continueWatchingQuery.data?.hashCode()) {
            val items = continueWatchingQuery.data ?: return@LaunchedEffect
            if (items.isEmpty()) return@LaunchedEffect
            ImagePreloader.preloadContinueWatchingImages(
                items = items,
                mediaRepository = mediaRepository,
                context = context,
                maxItems = items.size.coerceAtMost(30)
            )
        }

        LaunchedEffect(homeLibraryBurstQuery.data?.hashCode()) {
            val sections = homeLibraryBurstQuery.data ?: return@LaunchedEffect
            if (sections.isEmpty()) return@LaunchedEffect

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

        val featureParallaxOffsetPx by remember {
            derivedStateOf {
                if (lazyColumnState.firstVisibleItemIndex == 0) {
                    lazyColumnState.firstVisibleItemScrollOffset.toFloat()
                } else {
                    0f
                }
            }
        }

        LazyColumn(
            state = lazyColumnState,
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item(key = "feature_tab") {
                FeatureTab(
                    featuredItems = featuredQuery.data ?: emptyList(),
                    isLoading = featuredQuery.isLoading,
                    error = featuredQuery.error,
                    selectedCategory = selectedCategory,
                    verticalParallaxOffsetPx = featureParallaxOffsetPx,
                    onItemClick = onNavigateToDetail,
                    onLogout = onLogout,
                    onCategorySelected = { category ->
                        selectedCategory = category
                    }
                )
            }

            if (selectedCategory == "Home" && (
                continueWatchingQuery.isLoading ||
                    continueWatchingQuery.data != null ||
                    continueWatchingQuery.isError
                )
            ) {
                item(key = "continue_watching_section") {
                    Column(
                        modifier = Modifier
                            .padding(top = 0.dp)
                            .offset(y = (-12).dp)
                    ) {
                        val hasContinueWatchingData = !continueWatchingQuery.data.isNullOrEmpty()
                        if (!hasContinueWatchingData && (continueWatchingQuery.isLoading || (continueWatchingQuery.data == null && !continueWatchingQuery.isError))) {
                            Text(
                                text = "Continue Watching",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            ContinueWatchingSkeleton()
                        } else {
                            ContinueWatchingSection(
                                items = continueWatchingQuery.data ?: emptyList(),
                                isLoading = continueWatchingQuery.isLoading,
                                error = if (continueWatchingQuery.isError) continueWatchingQuery.error else null,
                                onItemClick = onNavigateToDetail
                            )
                        }
                    }
                }
            }

            if (selectedCategory == "Home") {
                val topPadding = if (continueWatchingQuery.data.isNullOrEmpty() && !continueWatchingQuery.isLoading) 16.dp else 0.dp

                if (topPadding > 0.dp) {
                    item(key = "home_libraries_top_padding") {
                        Spacer(modifier = Modifier.height(topPadding))
                    }
                }

                val libraries = homeLibraryBurstQuery.data ?: emptyList()
                if (homeLibraryBurstQuery.isError && libraries.isEmpty()) {
                    item(key = "home_libraries_error") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Failed to load libraries",
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
                            onItemClick = onNavigateToDetail,
                            onNavigateToViewAll = onNavigateToViewAll
                        )

                        if (index < libraries.lastIndex) {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            } else if (selectedCategory == "Movies") {
                val topPadding = if (continueWatchingQuery.data.isNullOrEmpty() && !continueWatchingQuery.isLoading) 16.dp else 0.dp

                item(key = "movies_genres") {
                    Column(
                        modifier = Modifier.padding(top = topPadding)
                    ) {
                        MovieGenreSections(
                            onItemClick = onNavigateToDetail,
                            onNavigateToViewAll = onNavigateToViewAll
                        )
                    }
                }
            } else if (selectedCategory == "TV Shows") {
                val topPadding = if (continueWatchingQuery.data.isNullOrEmpty() && !continueWatchingQuery.isLoading) 16.dp else 0.dp

                item(key = "tv_genres") {
                    Column(
                        modifier = Modifier.padding(top = topPadding)
                    ) {
                        TVShowGenreSections(
                            onItemClick = onNavigateToDetail,
                            onNavigateToViewAll = onNavigateToViewAll
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingSection(
    items: List<BaseItemDto>,
    isLoading: Boolean,
    error: String?,
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
            text = "Continue Watching",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when {
            isLoading -> {
                ContinueWatchingSkeleton()
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
                            text = "Failed to load continue watching",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = error ?: "Unknown error",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )

                    }
                }
            }

            items.isNotEmpty() -> {
                val lazyRowState = rememberLazyListState()
                val flingBehavior = ScrollOptimization.rememberUltraSmoothFlingBehavior()

                LazyRow(
                    state = lazyRowState,
                    horizontalArrangement = Arrangement.spacedBy(ScrollOptimization.listItemSpacing),
                    contentPadding = ScrollOptimization.optimizedContentPadding,
                    flingBehavior = flingBehavior,
                    modifier = ScrollOptimization.getScrollContainerModifier()
                ) {
                    items(
                        count = items.size,
                        key = { index -> items[index].id ?: "item_$index" }
                    ) { index ->
                        val item = remember(items[index].id) { items[index] }
                        val stableOnClick = remember(item.id) { { onItemClick(item) } }

                        Box {
                            ContinueWatchingCard(
                                item = item,
                                mediaRepository = mediaRepository,
                                onClick = stableOnClick
                            )
                        }
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
    mediaRepository: MediaRepository,
    onClick: () -> Unit = {}
) {
    val stableItem = remember(item.id) { StableBaseItem.from(item) }

    val itemName = remember(stableItem.name) { stableItem.name ?: "Unknown" }
    val typeText = remember(stableItem.type) {
        when (stableItem.type) {
            "Movie" -> "Movie"
            "Series" -> "TV Series"
            "Episode" -> "Episode"
            else -> stableItem.type ?: "Media"
        }
    }
    val yearText = remember(stableItem.productionYear, typeText) {
        val year = stableItem.productionYear?.toString().orEmpty()
        if (year.isNotBlank()) {
            "$year | $typeText"
        } else {
            typeText
        }
    }

    Column(
        modifier = Modifier
            .width(200.dp)
            .height(180.dp)
    ) {
        Card(
            modifier = Modifier
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
                    preferSeriesIdForThumbBackdrop = false,
                    allowRgb565 = false,
                    contentDescription = stableItem.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    cornerRadius = 12,
                    crossfadeMillis = 0,
                    mediaRepository = mediaRepository,
                    itemType = stableItem.type // Pass item type for proper episode handling
                )

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

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Continue Watching",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = yearText,
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

@Stable
@Immutable
private data class HomeLibrarySectionUi(
    val libraryId: String,
    val libraryName: String,
    val collectionType: String?,
    val items: List<BaseItemDto>
)


@Composable
private fun BurstLibrarySection(
    section: HomeLibrarySectionUi,
    mediaRepository: MediaRepository,
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    val libraryRowState = rememberLazyListState()
    val libraryFlingBehavior = ScrollOptimization.rememberUltraSmoothFlingBehavior()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recently Added - ${section.libraryName}",
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
                    onNavigateToViewAll(contentType, section.libraryId, section.libraryName)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "View All",
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
                    text = "No items found",
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
                    mediaRepository = mediaRepository,
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
    mediaRepository: MediaRepository,
    onClick: () -> Unit = {}
) {
    val stableItem = remember(item.id) { StableBaseItem.from(item) }

    val displayName = if (item.type == "Episode" && !item.seriesName.isNullOrBlank()) {
        item.seriesName!!
    } else {
        item.name ?: "Unknown"
    }

    Column(
        modifier = modifier
            .width(112.dp)
            .height(214.dp)
    ) {
        Card(
            modifier = Modifier
                .width(112.dp)
                .height(166.dp),
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
                    imageType = "Primary",
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    cornerRadius = 12,
                    crossfadeMillis = 0,
                    mediaRepository = mediaRepository,
                    itemType = stableItem.type // Pass item type for proper episode handling
                )

                val episodeCount = when {
                    item.type == "Series" && item.episodeCount != null && item.episodeCount!! > 0 -> item.episodeCount!!
                    item.type == "Series" && item.recursiveItemCount != null && item.recursiveItemCount!! > 0 -> item.recursiveItemCount!!
                    else -> null
                }

                episodeCount?.let { count ->
                    LibraryCountBadge(
                        count = count,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp)
                    )
                }


            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(top = 2.dp, start = 2.dp, end = 2.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = displayName,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                val metadataText = buildMetadataText(item)
                if (metadataText.isNotEmpty()) {
                    Text(
                        text = metadataText,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
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
    val resolvedStartYear = item.productionYear ?: item.premiereDate
        ?.take(4)
        ?.toIntOrNull()
    return buildString {
        when (item.type) {
            "Series" -> {
                resolvedStartYear?.let { startYear ->
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
                resolvedStartYear?.let { year ->
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
                resolvedStartYear?.let { year ->
                    append(year.toString())
                }
            }
        }
    }
}

@Composable
private fun LibraryCountBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count > 0) {
        val badgeSize = if (count >= 100) 22.dp else 20.dp
        val displayCount = if (count >= 100) "99+" else count.toString()
        Surface(
            modifier = modifier.size(badgeSize),
            shape = CircleShape,
            color = Color(0xFF4B62BE).copy(alpha = 0.96f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = displayCount,
                    color = Color.White,
                    fontSize = if (count >= 100) 7.sp else 8.sp,
                    fontWeight = FontWeight.Bold
                )
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

private object DashboardCache {
    fun shouldRefreshFeaturedItems(category: String): Boolean {
        return Cache.isStale("featured_$category", 300_000L)
    }

    fun getFeaturedItems(category: String): List<BaseItemDto> {
        return Cache.get<List<BaseItemDto>>("featured_$category") ?: emptyList()
    }

    fun updateFeaturedItems(category: String, items: List<BaseItemDto>) {
        Cache.put("featured_$category", items)
    }

    fun shouldRefreshContinueWatching(): Boolean {
        return Cache.isStale("continue_watching", 120_000L)
    }

    fun updateContinueWatching(items: List<BaseItemDto>) {
        Cache.put("continue_watching", items)
    }

    val continueWatchingItems: List<BaseItemDto>
        get() = Cache.get("continue_watching") ?: emptyList()
}

private object GenreCache {
    fun shouldRefreshMovieGenres(): Boolean {
        return Cache.isStale("movie_genres", 600_000L)
    }

    fun shouldRefreshTVGenres(): Boolean {
        return Cache.isStale("tv_genres", 600_000L)
    }

    fun shouldRefreshGenreItems(genreId: String): Boolean {
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
    fun shouldRefreshLibraryViews(): Boolean {
        return Cache.isStale("library_views", 600_000L)
    }

    fun updateLibraryViews(views: List<BaseItemDto>) {
        Cache.put("library_views", views)
    }

    fun shouldRefreshLibraryItems(libraryId: String): Boolean {
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
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }

    var movieGenres by remember { mutableStateOf(GenreCache.movieGenres) }
    var isLoading by remember { mutableStateOf(GenreCache.shouldRefreshMovieGenres()) }

    LaunchedEffect(Unit) {
        if (GenreCache.shouldRefreshMovieGenres()) {
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
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }

    var tvGenres by remember { mutableStateOf(GenreCache.tvGenres) }
    var isLoading by remember { mutableStateOf(GenreCache.shouldRefreshTVGenres()) }

    LaunchedEffect(Unit) {
        if (GenreCache.shouldRefreshTVGenres()) {
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
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    val genreId = genre.id ?: return // Skip if no genre ID
    var genreMovies by remember(genreId) { mutableStateOf(GenreCache.getGenreItems(genreId)) }
    var isLoading by remember(genreId) { mutableStateOf(GenreCache.shouldRefreshGenreItems(genreId)) }

    LaunchedEffect(genreId) {
        if (GenreCache.shouldRefreshGenreItems(genreId)) {
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
                text = "${genre.name ?: "Movies"}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            IconButton(
                onClick = {
                    onNavigateToViewAll("MOVIES_GENRE", genre.id, genre.name ?: "Movies")
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "View All",
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
                        LibraryItemCard(
                            item = genreMovies[index],
                            mediaRepository = mediaRepository,
                            onClick = { onItemClick(genreMovies[index]) }
                        )
                    }
                }
            }
            else -> {
                if (!isLoading && genreMovies.isEmpty()) {
                    Text(
                        text = "No movies found",
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
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    val genreId = genre.id ?: return // Skip if no genre ID
    var genreShows by remember(genreId) { mutableStateOf(GenreCache.getGenreItems("tv_$genreId")) }
    var isLoading by remember(genreId) { mutableStateOf(GenreCache.shouldRefreshGenreItems("tv_$genreId")) }

    LaunchedEffect(genreId) {
        val cacheKey = "tv_$genreId"
        if (GenreCache.shouldRefreshGenreItems(cacheKey)) {
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
                text = "${genre.name ?: "TV Shows"}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            IconButton(
                onClick = {
                    onNavigateToViewAll("TVSHOWS_GENRE", genre.id, genre.name ?: "TV Shows")
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "View All",
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
                        LibraryItemCard(
                            item = show,
                            mediaRepository = mediaRepository,
                            onClick = { onItemClick(show) }
                        )
                    }
                }
            }
            else -> {
                if (!isLoading && genreShows.isEmpty()) {
                    Text(
                        text = "No TV shows found",
                        color = Color.Gray.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
