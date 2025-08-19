package com.jellycine.app.ui.screens.dashboard.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import com.jellycine.app.ui.screens.dashboard.ShimmerEffect
import com.jellycine.app.ui.screens.dashboard.PosterSkeleton
import com.jellycine.app.ui.screens.dashboard.ContinueWatchingSkeleton
import com.jellycine.app.ui.screens.dashboard.LibrarySkeleton
import com.jellycine.app.ui.screens.dashboard.SectionTitleSkeleton
import com.jellycine.app.ui.screens.dashboard.GenreSectionSkeleton
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Immutable
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.draw.drawWithCache
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.rememberPagerState
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.request.CachePolicy

// Data fetching state management
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

// Query priority levels
enum class QueryPriority {
    HIGH,
    MEDIUM,
    LOW
}

// Query configuration
@Stable
@Immutable
data class QueryConfig(
    val staleTime: Long = 300_000L,
    val cacheTime: Long = 600_000L,
    val retryCount: Int = 3,
    val retryDelay: Long = 1000L,
    val enabled: Boolean = true,
    val priority: QueryPriority = QueryPriority.MEDIUM
)

// Query manager for efficient data fetching with priority and concurrency control
class QueryManager(private val scope: CoroutineScope) {
    private val queries = mutableMapOf<String, QueryState<Any>>()
    private val jobs = mutableMapOf<String, Job>()
    private val activeRequests = mutableMapOf<QueryPriority, Int>()
    // Concurrency limits per priority
    private val maxConcurrentRequests = mapOf(
        QueryPriority.HIGH to 4,
        QueryPriority.MEDIUM to 3,
        QueryPriority.LOW to 2
    )

    @Suppress("UNCHECKED_CAST")
    fun <T> getQuery(key: String): QueryState<T> {
        return queries[key] as? QueryState<T> ?: QueryState()
    }

    fun <T> setQuery(key: String, state: QueryState<T>) {
        queries[key] = state as QueryState<Any>
    }

    private fun canExecuteRequest(priority: QueryPriority): Boolean {
        val currentCount = activeRequests[priority] ?: 0
        val maxCount = maxConcurrentRequests[priority] ?: 1
        return currentCount < maxCount
    }

    private fun incrementActiveRequests(priority: QueryPriority) {
        activeRequests[priority] = (activeRequests[priority] ?: 0) + 1
    }

    private fun decrementActiveRequests(priority: QueryPriority) {
        val current = activeRequests[priority] ?: 0
        activeRequests[priority] = maxOf(0, current - 1)
    }

    fun <T> executeQuery(
        key: String,
        config: QueryConfig = QueryConfig(),
        fetcher: suspend () -> T
    ): QueryState<T> {
        val currentState = getQuery<T>(key)

        // Return cached data if not stale and enabled
        if (!config.enabled) {
            return currentState
        }

        val isStale = System.currentTimeMillis() - currentState.lastFetched > config.staleTime
        if (currentState.data != null && !isStale) {
            return currentState
        }

        // Prevent duplicate requests for the same key
        val isAlreadyFetching = jobs[key]?.isActive == true
        if (isAlreadyFetching) {
            return currentState
        }

        // Start new fetch with priority handling
        val newState = currentState.copy(isLoading = true, isError = false, error = null)
        setQuery(key, newState)

        jobs[key] = scope.launch {
            var waitTime = 50L
            while (!canExecuteRequest(config.priority)) {
                delay(waitTime)
                waitTime = if (waitTime * 1.5 > 500.0) 500L else (waitTime * 1.5).toLong()
            }

            incrementActiveRequests(config.priority)

            // Retry logic for failed requests
            var lastException: Exception? = null
            var retryCount = 0

            while (retryCount <= config.retryCount) {
                try {
                    val result = fetcher()
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

            decrementActiveRequests(config.priority)
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

    // Refresh stale queries when app comes back to foreground
    fun refreshStaleQueries() {
        val currentTime = System.currentTimeMillis()
        queries.forEach { (key, state) ->
            val isStale = currentTime - state.lastFetched > 60_000L // 60 seconds
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

// Stable data class to prevent unnecessary recompositions
@Stable
@Immutable
private data class StableLibraryState(
    val libraryViews: List<BaseItemDto>,
    val isLoading: Boolean
)


// Composable hook for using queries
@Composable
fun <T> useQuery(
    key: String,
    config: QueryConfig = QueryConfig(),
    fetcher: suspend () -> T
): QueryState<T> {
    val queryManager = LocalQueryManager.current
    var state by remember(key) { mutableStateOf(queryManager.getQuery<T>(key)) }
    var hasInitiated by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key, config.enabled) {
        if (config.enabled) {
            hasInitiated = true
            val newState = queryManager.executeQuery(key, config, fetcher)
            state = newState

            // Listen for state changes
            while (true) {
                delay(50)
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

// Image preloader for better performance
object ImagePreloader {
    private val preloadedUrls = mutableSetOf<String>()

    fun preloadImages(
        items: List<BaseItemDto>,
        mediaRepository: MediaRepository,
        scope: CoroutineScope
    ) {
        scope.launch(Dispatchers.IO) {
            items.take(6).forEach { item ->
                val itemId = item.id ?: return@forEach
                val key = "preload_$itemId"

                if (!preloadedUrls.contains(key)) {
                    try {
                        delay(100)
                        mediaRepository.getImageUrl(
                            itemId = itemId,
                            imageType = "Primary",
                            width = 300,
                            height = 450,
                            quality = 100
                        ).first()
                        preloadedUrls.add(key)
                    } catch (e: Exception) {
                    }
                }
            }
        }
    }
}

// Image loading
@Composable
fun ImageLoader(
    itemId: String?,
    seriesId: String? = null,
    imageType: String = "Primary",
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Int = 8,
    mediaRepository: com.jellycine.data.repository.MediaRepository,
    itemType: String? = null // Add item type to handle episodes properly
) {
    val actualItemId = remember(itemId, seriesId, itemType) {
        when {
            itemType == "Episode" && imageType == "Primary" && !seriesId.isNullOrBlank() -> seriesId
            imageType == "Thumb" && !seriesId.isNullOrBlank() -> seriesId
            else -> itemId
        }
    }

    var blurImageUrl by remember(actualItemId) { mutableStateOf<String?>(null) }
    var highQualityImageUrl by remember(actualItemId) { mutableStateOf<String?>(null) }
    var isHighQualityLoaded by remember(actualItemId) { mutableStateOf(false) }
    var hasError by remember(actualItemId) { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(actualItemId) {
        if (actualItemId != null) {
            withContext(Dispatchers.IO) {
                try {
                    val blurUrl = mediaRepository.getImageUrl(
                        itemId = actualItemId,
                        imageType = imageType,
                        width = if (imageType == "Thumb") 80 else 60,
                        height = if (imageType == "Thumb") 50 else 90,
                        quality = 20
                    ).first()

                    withContext(Dispatchers.Main) {
                        blurImageUrl = blurUrl
                    }

                    delay(50)

                    val highQualityUrl = mediaRepository.getImageUrl(
                        itemId = actualItemId,
                        imageType = imageType,
                        width = if (imageType == "Thumb") 400 else 300,
                        height = if (imageType == "Thumb") 240 else 450,
                        quality = 95
                    ).first()

                    withContext(Dispatchers.Main) {
                        highQualityImageUrl = highQualityUrl
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        hasError = true
                    }
                }
            }
        }
    }

    Box(modifier = modifier) {
        // Show blur image first for instant visual feedback
        if (!blurImageUrl.isNullOrEmpty() && !hasError) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(blurImageUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .allowHardware(true)
                    .allowRgb565(false)
                    .crossfade(0)
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius.dp))
                    .blur(radius = 4.dp),
                contentScale = contentScale
            )
        }

        // Show high quality image on top when loaded
        if (!highQualityImageUrl.isNullOrEmpty() && !hasError) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(highQualityImageUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .allowHardware(true)
                    .allowRgb565(false)
                    .crossfade(200)
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

        // Placeholder only if no blur image is available yet
        if (blurImageUrl.isNullOrEmpty() && !hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            )
        }

        // Error state
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

// CompositionLocal for QueryManager
val LocalQueryManager = compositionLocalOf<QueryManager> {
    error("QueryManager not provided")
}

// Scroll configuration for buttery smooth performance
@Stable
object ScrollOptimization {
    @Composable
    fun rememberUltraSmoothFlingBehavior(): FlingBehavior {
        return ScrollableDefaults.flingBehavior()
    }

    // Optimized content padding for smooth edge scrolling
    val optimizedContentPadding = PaddingValues(horizontal = 16.dp)

    // Reduced spacing for better performance
    val optimizedSpacing = 12.dp

    // Optimized item spacing for lists
    val listItemSpacing = 8.dp

    // Performance-optimized modifier for scroll containers
    fun getScrollContainerModifier(): Modifier = Modifier
        .fillMaxWidth()
}

// Sample movie data for demonstration
data class Movie(
    val id: String,
    val title: String,
    val year: String,
    val posterUrl: String? = null
)

// Stable wrapper for BaseItemDto to prevent recomposition
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
    // Memoized computed properties to reduce recomposition
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
    var selectedCategory by remember { mutableStateOf("Home") }
    val context = LocalContext.current
    val mediaRepository = remember { com.jellycine.data.repository.MediaRepositoryProvider.getInstance(context) }
    val authRepository = remember { com.jellycine.data.repository.AuthRepositoryProvider.getInstance(context) }

    // Track current user to detect user changes
    val currentUsername by authRepository.getUsername().collectAsState(initial = null)
    var lastKnownUsername by remember { mutableStateOf<String?>(null) }

    // Create QueryManager with proper scope
    val queryManager = remember {
        QueryManager(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    }

    LaunchedEffect(currentUsername) {
        if (lastKnownUsername != null && lastKnownUsername != currentUsername) {
            queryManager.cleanup()
            Cache.clear()
        }
        lastKnownUsername = currentUsername
    }

    // Cleanup on disposal
    DisposableEffect(Unit) {
        onDispose {
            queryManager.cleanup()
        }
    }

    // Optimize LazyColumn state for better performance
    val lazyColumnState = rememberLazyListState()

    // Use derivedStateOf to prevent unnecessary recompositions during scrolling
    val isScrolling by remember {
        derivedStateOf {
            lazyColumnState.isScrollInProgress
        }
    }

    // Progressive loading state to control when secondary content loads
    var primaryContentLoaded by remember { mutableStateOf(false) }

    // Provide QueryManager to child components
    CompositionLocalProvider(LocalQueryManager provides queryManager) {

        // Featured items query - loads based on selected category (HIGH priority)
        val featuredQuery = useQuery(
            key = "featured_$selectedCategory",
            config = QueryConfig(
                staleTime = 300_000L,
                enabled = isTabActive,
                priority = QueryPriority.HIGH
            )
        ) {
            val result = when (selectedCategory) {
                "Movies" -> mediaRepository.getLatestItems(
                    parentId = null,
                    includeItemTypes = "Movie",
                    limit = 5,
                    fields = "BasicSyncInfo,Genres,CommunityRating,CriticRating"
                )
                "TV Shows" -> mediaRepository.getLatestItems(
                    parentId = null,
                    includeItemTypes = "Series",
                    limit = 5,
                    fields = "BasicSyncInfo,Genres,CommunityRating,CriticRating"
                )
                else -> mediaRepository.getLatestItems(
                    parentId = null,
                    includeItemTypes = "Movie,Series",
                    limit = 5,
                    fields = "BasicSyncInfo,Genres,CommunityRating,CriticRating"
                )
            }

            result.fold(
                onSuccess = { items ->
                    items.filter { it.id != null && !it.name.isNullOrBlank() }
                },
                onFailure = { throw it }
            )
        }

        // Track when primary content is loaded
        LaunchedEffect(featuredQuery.isSuccess) {
            if (featuredQuery.isSuccess) {
                primaryContentLoaded = true
            }
        }

        // Continue watching query - with lifecycle awareness (HIGH priority)
        val continueWatchingQuery = useQuery(
            key = "continue_watching",
            config = QueryConfig(
                staleTime = 60_000L,
                enabled = selectedCategory == "Home",
                priority = QueryPriority.HIGH,
                retryCount = 3,
                retryDelay = 1000L
            )
        ) {
            val result = mediaRepository.getResumeItems(limit = 12)
            result.fold(
                onSuccess = { items ->
                    items.filter { item ->
                        val userData = item.userData
                        item.id != null &&
                        !item.name.isNullOrBlank() &&
                        userData?.playedPercentage != null &&
                        userData.playedPercentage!! > 0 &&
                        userData.playedPercentage!! < 95
                    }
                },
                onFailure = { throw it }
            )
        }

        // Library views query
         val libraryViewsQuery = useQuery(
             key = "library_views", 
             config = QueryConfig(
                                staleTime = 600_000L,
               enabled = isTabActive && featuredQuery.isSuccess,
               priority = QueryPriority.MEDIUM
             )
        ) {
            val result = mediaRepository.getUserViews()
            result.fold(
                onSuccess = { queryResult ->
                    queryResult.items?.filter {
                        it.id != null &&
                        !it.name.isNullOrBlank() &&
                        it.collectionType != "boxsets" &&
                        it.collectionType != "playlists" &&
                        it.collectionType != "folders" &&
                        (it.type == "CollectionFolder" || it.type == "Folder") &&
                        (it.collectionType == "movies" || it.collectionType == "tvshows" ||
                         it.collectionType == null)
                    } ?: emptyList()
                },
                onFailure = { throw it }
            )
        }

        LaunchedEffect(featuredQuery.isSuccess) {
            if (featuredQuery.isSuccess && primaryContentLoaded) {
                launch {
                    if (selectedCategory != "Movies") {
                        queryManager.executeQuery("featured_Movies", QueryConfig(
                            staleTime = 300_000L,
                            priority = QueryPriority.MEDIUM
                        )) {
                            val result = mediaRepository.getLatestItems(
                                parentId = null,
                                includeItemTypes = "Movie",
                                limit = 5,
                                fields = "BasicSyncInfo,Genres,CommunityRating,CriticRating"
                            )
                            result.getOrThrow().filter { it.id != null && !it.name.isNullOrBlank() }
                        }
                    }
                }
                
                launch {
                    if (selectedCategory != "TV Shows") {
                        queryManager.executeQuery("featured_TV Shows", QueryConfig(
                            staleTime = 300_000L,
                            priority = QueryPriority.MEDIUM
                        )) {
                            val result = mediaRepository.getLatestItems(
                                parentId = null,
                                includeItemTypes = "Series",
                                limit = 5,
                                fields = "BasicSyncInfo,Genres,CommunityRating,CriticRating"
                            )
                            result.getOrThrow().filter { it.id != null && !it.name.isNullOrBlank() }
                        }
                    }
                }
            }
        }

        val mainScrollState = rememberScrollState()

        // Track if user is actively scrolling to pause non-essential API calls
        var isScrolling by remember { mutableStateOf(false) }
        var scrollDebounceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

        // Detect scrolling state changes
        LaunchedEffect(mainScrollState.value) {
            isScrolling = true
            scrollDebounceJob?.cancel()
            scrollDebounceJob = launch {
                delay(150)
                isScrolling = false
            }
        }

        // Lifecycle-aware query refresh
        LaunchedEffect(isTabActive) {
            if (isTabActive) {
                queryManager.refreshStaleQueries()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    state = mainScrollState,
                    flingBehavior = ScrollableDefaults.flingBehavior()
                ),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            FeatureTab(
                featuredItems = featuredQuery.data ?: emptyList(),
                isLoading = featuredQuery.isLoading,
                error = featuredQuery.error,
                selectedCategory = selectedCategory,
                onItemClick = onNavigateToDetail,
                onLogout = onLogout,
                onCategorySelected = { category ->
                    selectedCategory = category
                }
            )

            // Continue Watching section - only show on Home tab
            if (selectedCategory == "Home" && (continueWatchingQuery.isLoading || !continueWatchingQuery.data.isNullOrEmpty() || (continueWatchingQuery.data == null && !continueWatchingQuery.isError))) {
                Column(
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    if (continueWatchingQuery.isLoading || (continueWatchingQuery.data == null && !continueWatchingQuery.isError)) {
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

            // Only show library sections on Home tab
            if (selectedCategory == "Home") {
                val topPadding = if (continueWatchingQuery.data.isNullOrEmpty() && !continueWatchingQuery.isLoading) 16.dp else 0.dp

                Column(
                    modifier = Modifier.padding(top = topPadding)
                ) {
                    LibrarySections(
                        libraryViews = libraryViewsQuery.data ?: emptyList(),
                        isLoading = libraryViewsQuery.isLoading,
                        onItemClick = onNavigateToDetail,
                        onNavigateToViewAll = onNavigateToViewAll
                    )
                }
            } else if (selectedCategory == "Movies") {
                val topPadding = if (continueWatchingQuery.data.isNullOrEmpty() && !continueWatchingQuery.isLoading) 16.dp else 0.dp

                Column(
                    modifier = Modifier.padding(top = topPadding)
                ) {
                    MovieGenreSections(
                        onItemClick = onNavigateToDetail,
                        onNavigateToViewAll = onNavigateToViewAll
                    )
                }
            } else if (selectedCategory == "TV Shows") {
                val topPadding = if (continueWatchingQuery.data.isNullOrEmpty() && !continueWatchingQuery.isLoading) 16.dp else 0.dp

                Column(
                    modifier = Modifier.padding(top = topPadding)
                ) {
                    TVShowGenreSections(
                        onItemClick = onNavigateToDetail,
                        onNavigateToViewAll = onNavigateToViewAll
                    )
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
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
        // Section Header
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
                            text = "⚠️",
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
                        count = minOf(items.size, 8),
                        key = { index ->
                            // Ultra-stable keys for smooth scrolling
                            items[index].id ?: "item_$index"
                        }
                    ) { index ->
                        // Memoize item for ultra-smooth scrolling
                        val item = remember(items[index].id) { items[index] }
                        val stableOnClick = remember(item.id) { { onItemClick(item) } }

                        // Wrap in graphics layer for hardware acceleration
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
        "${stableItem.productionYear ?: ""} • $typeText"
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
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            onClick = onClick
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                ImageLoader(
                    itemId = stableItem.id,
                    seriesId = stableItem.seriesId,
                    imageType = "Thumb",
                    contentDescription = stableItem.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    cornerRadius = 12,
                    mediaRepository = mediaRepository,
                    itemType = stableItem.type // Pass item type for proper episode handling
                )

                // Progress bar at bottom - memoized
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

                // Play button overlay
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

        // Title and info below the image with fixed height container
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

@Composable
private fun LibrarySections(
    libraryViews: List<BaseItemDto>,
    isLoading: Boolean,
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    // Use a stable composition approach
    StableLibrarySectionsContent(
        libraryViews = libraryViews,
        isLoading = isLoading,
        onItemClick = onItemClick,
        onNavigateToViewAll = onNavigateToViewAll
    )
}

@Composable
private fun StableLibrarySectionsContent(
    libraryViews: List<BaseItemDto>,
    isLoading: Boolean,
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }

    // Create a stable key based on actual content
    val contentKey = remember(libraryViews) {
        libraryViews.map { it.id }.hashCode()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        when {
            isLoading -> {
                GenreSectionSkeleton(sectionCount = 2)
            }
            libraryViews.isNotEmpty() -> {
                // Use a stable list approach instead of forEachIndexed
                StableLibraryList(
                    libraries = libraryViews,
                    mediaRepository = mediaRepository,
                    onItemClick = onItemClick,
                    onNavigateToViewAll = onNavigateToViewAll
                )
            }
        }
    }
}

@Composable
private fun StableLibraryList(
    libraries: List<BaseItemDto>,
    mediaRepository: MediaRepository,
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    // Create a stable list that only changes when content changes
    val stableLibraries = remember(libraries.map { it.id }.hashCode()) { libraries }

    stableLibraries.forEachIndexed { index, library ->
        key(library.id ?: index) {
            ProgressiveLibrarySection(
                library = library,
                mediaRepository = mediaRepository,
                onItemClick = onItemClick,
                onNavigateToViewAll = onNavigateToViewAll,
                loadPriority = when {
                    index < 2 -> QueryPriority.HIGH
                    index < 4 -> QueryPriority.MEDIUM
                    else -> QueryPriority.LOW
                },
                loadDelay = when {
                    index == 0 -> 0L
                    index == 1 -> 200L
                    else -> (index * 300L).coerceAtMost(1500L)
                }
            )
        }
        if (index < stableLibraries.size - 1) {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProgressiveLibrarySection(
    library: BaseItemDto,
    mediaRepository: MediaRepository,
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> },
    loadPriority: QueryPriority = QueryPriority.MEDIUM,
    loadDelay: Long = 0L
) {
    val libraryId = library.id ?: return

    val isEssentialLibrary = remember(library.name) {
        library.name?.lowercase()?.contains("movie") == true ||
        library.name?.lowercase()?.contains("tv") == true ||
        library.name?.lowercase()?.contains("series") == true
    }

    // Progressive loading
    var shouldLoad by remember { mutableStateOf(loadDelay == 0L) }
    
    LaunchedEffect(libraryId) {
        if (loadDelay > 0L) {
            delay(loadDelay)
            shouldLoad = true
        }
    }

    val libraryQuery = useQuery(
        key = "library_$libraryId",
        config = QueryConfig(
            staleTime = 60_000L,
            enabled = shouldLoad,
            priority = loadPriority,
            retryCount = 3,
            retryDelay = 1000L
        )
    ) {
        val includeItemTypes = when (library.collectionType) {
            "tvshows" -> "Episode"
            "movies" -> "Movie"
            else -> "Movie,Series,Episode"
        }

        val result = mediaRepository.getLatestItems(
            parentId = libraryId,
            includeItemTypes = includeItemTypes,
            limit = 12,
            fields = "ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId"
        )

        result.fold(
            onSuccess = { items ->
                items.filter { it.id != null && !it.name.isNullOrBlank() }
            },
            onFailure = { throw it }
        )
    }

    Column {
        // Section header with View All button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recently Added • ${library.name ?: "Library"}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            
            // View All button
            IconButton(
                onClick = {
                    val contentType = when (library.collectionType) {
                        "movies" -> "MOVIES"
                        "tvshows" -> "SERIES"
                        else -> "ALL"
                    }
                    onNavigateToViewAll(contentType, library.id, library.name ?: "Library")
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
            libraryQuery.isLoading -> {
                // Show actual blur placeholders instead of generic skeletons
                // We need to get some sample items to show blur placeholders
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    items(4) {
                        PosterSkeleton()
                    }
                }
            }

            libraryQuery.isSuccess && !libraryQuery.data.isNullOrEmpty() -> {
                val items = libraryQuery.data!!

                // Preload images for better performance
                LaunchedEffect(items) {
                    ImagePreloader.preloadImages(items, mediaRepository, this)
                }

                val libraryRowState = rememberLazyListState()
                val libraryFlingBehavior = ScrollOptimization.rememberUltraSmoothFlingBehavior()

                LazyRow(
                    state = libraryRowState,
                    horizontalArrangement = Arrangement.spacedBy(ScrollOptimization.listItemSpacing),
                    contentPadding = ScrollOptimization.optimizedContentPadding,
                    flingBehavior = libraryFlingBehavior,
                    modifier = ScrollOptimization.getScrollContainerModifier()
                ) {
                    items(
                        count = items.size,
                        key = { index ->
                            // Simple, stable keys for optimal performance
                            items[index].id ?: "lib_item_$index"
                        }
                    ) { index ->
                        // Memoize item for ultra-smooth scrolling
                        val item = remember(items[index].id) { items[index] }
                        val stableOnClick = remember(item.id) { { onItemClick(item) } }

                        // Wrap in graphics layer for hardware acceleration
                        Box {
                            LibraryItemCard(
                                item = item,
                                mediaRepository = mediaRepository,
                                onClick = stableOnClick
                            )
                        }
                    }
                }
            }

            libraryQuery.isError -> {
                // Error state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Failed to load items",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            }

            else -> {
                if (libraryQuery.data == null && !libraryQuery.isError) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        items(4) {
                            PosterSkeleton()
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No items found",
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
internal fun LibraryItemCard(
    item: BaseItemDto,
    mediaRepository: MediaRepository,
    onClick: () -> Unit = {}
) {
    val stableItem = remember(item.id) { StableBaseItem.from(item) }

    // Show series name for episodes, otherwise show item name
    val displayName = if (item.type == "Episode" && !item.seriesName.isNullOrBlank()) {
        item.seriesName!!
    } else {
        item.name ?: "Unknown"
    }

    Column(
        modifier = Modifier
            .width(140.dp)
            .height(260.dp) // Fixed total height to prevent recomposition issues
    ) {
        Card(
            modifier = Modifier
                .width(140.dp)
                .height(210.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            onClick = onClick
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Poster image
                // Episodes will automatically use series poster
                ImageLoader(
                    itemId = stableItem.id,
                    seriesId = stableItem.seriesId,
                    imageType = "Primary",
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    cornerRadius = 16,
                    mediaRepository = mediaRepository,
                    itemType = stableItem.type // Pass item type for proper episode handling
                )

                // Episode count badge for series (top-right corner)
                val episodeCount = when {
                    item.type == "Series" && item.episodeCount != null && item.episodeCount!! > 0 -> item.episodeCount!!
                    item.type == "Series" && item.recursiveItemCount != null && item.recursiveItemCount!! > 0 -> item.recursiveItemCount!!
                    else -> null
                }

                episodeCount?.let { count ->
                    EpisodeCountBadge(
                        count = count,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    )
                }


            }
        }

        // Title and metadata below the image with fixed height container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(top = 8.dp, start = 4.dp, end = 4.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = displayName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Additional metadata
                val metadataText = buildMetadataText(item)
                if (metadataText.isNotEmpty()) {
                    Text(
                        text = metadataText,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
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

/**
 * Build metadata text for library items
 */
private fun buildMetadataText(item: BaseItemDto): String {
    return buildString {
        when (item.type) {
            "Series" -> {
                // For series, show year range if available
                item.productionYear?.let { startYear ->
                    append(startYear.toString())
                    item.endDate?.let { endDate ->
                        val endYear = endDate.substring(0, 4).toIntOrNull()
                        if (endYear != null && endYear != startYear) {
                            append(" - $endYear")
                        }
                    } ?: run {
                        // If no end date and series is ongoing, show start year only
                        // Could add " - Present" for ongoing series if needed
                    }
                }
            }
            "Movie" -> {
                // For movies, show year
                item.productionYear?.let { year ->
                    append(year.toString())
                }
            }
            "Episode" -> {
                // For episodes, show season and episode info
                val seasonNumber = item.parentIndexNumber
                val episodeNumber = item.indexNumber
                if (seasonNumber != null && episodeNumber != null) {
                    append("S${seasonNumber}:E${episodeNumber}")
                    // Add episode title if different from series name
                    item.name?.let { episodeName ->
                        if (episodeName != item.seriesName) {
                            append(" - ${episodeName.take(20)}")
                            if (episodeName.length > 20) append("...")
                        }
                    }
                }
            }
            else -> {
                // For other types, show year if available
                item.productionYear?.let { year ->
                    append(year.toString())
                }
            }
        }
    }
}

@Composable
private fun EpisodeCountBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count > 0) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.8f)
        ) {
            Text(
                text = count.toString(),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
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

// cache with memory management and background refresh
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
            // Update access statistics
            cache[key] = entry.copy(
                accessCount = entry.accessCount + 1,
                lastAccessed = System.currentTimeMillis()
            ) as CacheEntry<Any>
        }
        return entry?.data
    }

    fun <T> put(key: String, data: T) {
        cleanupIfNeeded()

        // Remove oldest entries if cache is full
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
            // Remove entries older than 1 hour
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

// Legacy cache objects for backward compatibility (now using Cache internally)
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

// Cache for genre data (now using Cache)
private object GenreCache {
    fun shouldRefreshMovieGenres(): Boolean {
        return Cache.isStale("movie_genres", 600_000L)
    }

    fun shouldRefreshTVGenres(): Boolean {
        return Cache.isStale("tv_genres", 600_000L)
    }

    fun shouldRefreshGenreItems(genreId: String): Boolean {
        return Cache.isStale("genre_items_$genreId", 300_000L)
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

// Cache for library data (now using Cache)
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

    // Optimize movie genres loading with background thread
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
                    onNavigateToViewAll = onNavigateToViewAll,
                    loadDelay = index * 200L
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

    // Load TV show genres with caching
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
                    onNavigateToViewAll = onNavigateToViewAll,
                    loadDelay = index * 200L
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
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> },
    loadDelay: Long = 0L
) {
    val genreId = genre.id ?: return // Skip if no genre ID
    var genreMovies by remember(genreId) { mutableStateOf(GenreCache.getGenreItems(genreId)) }
    var isLoading by remember(genreId) { mutableStateOf(GenreCache.shouldRefreshGenreItems(genreId)) }

    // Progressive loading with staggered delays
    LaunchedEffect(genreId) {
        if (GenreCache.shouldRefreshGenreItems(genreId)) {
            // Add longer staggered delay to prevent all genres loading at once
            if (loadDelay > 0) {
                delay(loadDelay + 500)
            }

            isLoading = true
            try {
                withContext(Dispatchers.IO) {
                    val result = mediaRepository.getItemsByGenre(
                        genreId = genreId,
                        includeItemTypes = "Movie",
                        limit = 20
                    )

                    withContext(Dispatchers.Main) {
                        result.fold(
                            onSuccess = { items ->
                                val validItems = items.filter {
                                    it.id != null && !it.name.isNullOrBlank()
                                }
                                GenreCache.updateGenreItems(genreId, validItems)
                                genreMovies = validItems
                            },
                            onFailure = { error ->
                                genreMovies = emptyList()
                            }
                        )
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                isLoading = false
            }
        } else {
            genreMovies = GenreCache.getGenreItems(genreId)
            isLoading = false
        }
    }

    // Always show the genre section
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        // Section Header with View All button
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

            // View All button - positioned right next to the genre name
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
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> },
    loadDelay: Long = 0L
) {
    val genreId = genre.id ?: return // Skip if no genre ID
    var genreShows by remember(genreId) { mutableStateOf(GenreCache.getGenreItems("tv_$genreId")) }
    var isLoading by remember(genreId) { mutableStateOf(GenreCache.shouldRefreshGenreItems("tv_$genreId")) }

    // Progressive loading with staggered delays
    LaunchedEffect(genreId) {
        val cacheKey = "tv_$genreId"
        if (GenreCache.shouldRefreshGenreItems(cacheKey)) {
            // Add longer staggered delay to prevent all genres loading at once
            if (loadDelay > 0) {
                delay(loadDelay + 500)
            }

            isLoading = true
            try {
                withContext(Dispatchers.IO) {
                    val result = mediaRepository.getItemsByGenre(
                        genreId = genreId,
                        includeItemTypes = "Series",
                        limit = 20
                    )

                    withContext(Dispatchers.Main) {
                        result.fold(
                            onSuccess = { items ->
                                val validItems = items.filter {
                                    it.id != null && !it.name.isNullOrBlank()
                                }
                                GenreCache.updateGenreItems(cacheKey, validItems)
                                genreShows = validItems
                            },
                            onFailure = { error ->
                                genreShows = emptyList()
                            }
                        )
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                isLoading = false
            }
        } else {
            genreShows = GenreCache.getGenreItems("tv_$genreId")
            isLoading = false
        }
    }

    // Always show the genre section
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        // Section Header with View All button
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

            // View All button - positioned right next to the genre name
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
                    items(genreShows) { show ->
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