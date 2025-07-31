package com.jellycine.app.ui.screens.dashboard.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.border
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Immutable
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.draw.drawWithCache


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
    val userData: com.jellycine.data.model.UserItemDataDto?,
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
        fun from(item: com.jellycine.data.model.BaseItemDto): StableBaseItem {
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
    onNavigateToDetail: (com.jellycine.data.model.BaseItemDto) -> Unit = {}
) {
    var selectedCategory by remember { mutableStateOf("Home") }

    var continueWatchingItems by remember {
        mutableStateOf<List<com.jellycine.data.model.BaseItemDto>>(
            if (!DashboardCache.shouldRefreshContinueWatching()) {
                DashboardCache.continueWatchingItems
            } else {
                emptyList()
            }
        )
    }
    var continueWatchingLoading by remember {
        mutableStateOf(
            DashboardCache.shouldRefreshContinueWatching() || DashboardCache.continueWatchingItems.isEmpty()
        )
    }
    var continueWatchingError by remember { mutableStateOf<String?>(null) }
    var continueWatchingLoaded by remember {
        mutableStateOf(
            !DashboardCache.shouldRefreshContinueWatching() && DashboardCache.continueWatchingItems.isNotEmpty()
        )
    }

    // Featured items state
    var featuredItems by remember { mutableStateOf<List<com.jellycine.data.model.BaseItemDto>>(emptyList()) }
    var featuredLoading by remember { mutableStateOf(true) }
    var featuredError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val mediaRepository = remember { com.jellycine.data.repository.MediaRepositoryProvider.getInstance(context) }

    // Optimize LazyColumn state for better performance
    val lazyColumnState = rememberLazyListState()

    // Use derivedStateOf to prevent unnecessary recompositions during scrolling
    val isScrolling by remember {
        derivedStateOf {
            lazyColumnState.isScrollInProgress
        }
    }

    // Additional scroll optimization
    val scrollOptimization = remember {
        true
    }

    LaunchedEffect(selectedCategory) {
        val cachedItems = DashboardCache.getFeaturedItems(selectedCategory)

        if (!DashboardCache.shouldRefreshFeaturedItems(selectedCategory) && cachedItems.isNotEmpty()) {
            featuredItems = cachedItems
            featuredLoading = false
            featuredError = null
        } else {
            // Load data only if not cached
            featuredLoading = true
            featuredError = null

            try {
                withContext(Dispatchers.IO) {
                    val result = when (selectedCategory) {
                        "Movies" -> mediaRepository.getLatestItems(
                            parentId = null,
                            includeItemTypes = "Movie",
                            limit = 5,
                            fields = "BasicSyncInfo"
                        )
                        "TV Shows" -> mediaRepository.getLatestItems(
                            parentId = null,
                            includeItemTypes = "Series",
                            limit = 5,
                            fields = "BasicSyncInfo"
                        )
                        else -> mediaRepository.getLatestItems(
                            parentId = null,
                            includeItemTypes = "Movie,Series",
                            limit = 5,
                            fields = "BasicSyncInfo"
                        )
                    }

                    withContext(Dispatchers.Main) {
                        result.fold(
                            onSuccess = { items ->
                                val validItems = items.filter {
                                    it.id != null && !it.name.isNullOrBlank()
                                }

                                DashboardCache.updateFeaturedItems(selectedCategory, validItems)

                                featuredItems = validItems
                                featuredLoading = false
                            },
                            onFailure = { throwable ->
                                featuredError = throwable.message ?: "Failed to load featured content"
                                featuredLoading = false
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                featuredError = e.message ?: "Unknown error occurred"
                featuredLoading = false
            }
        }
    }

    // Preloading - only after user interaction or when UI is stable
    LaunchedEffect(selectedCategory) {
        if (featuredItems.isNotEmpty() && !featuredLoading) {
            delay(3000)

            // Preload other tabs in background
            withContext(Dispatchers.IO) {
                try {
                    if (selectedCategory != "Movies" && DashboardCache.shouldRefreshFeaturedItems("Movies")) {
                        delay(500)
                        val moviesResult = mediaRepository.getLatestItems(
                            parentId = null,
                            includeItemTypes = "Movie",
                            limit = 5,
                            fields = "BasicSyncInfo"
                        )
                        moviesResult.getOrNull()?.let { items ->
                            val validItems = items.filter { it.id != null && !it.name.isNullOrBlank() }
                            DashboardCache.updateFeaturedItems("Movies", validItems)
                        }
                    }

                    if (selectedCategory != "TV Shows" && DashboardCache.shouldRefreshFeaturedItems("TV Shows")) {
                        delay(500)
                        val tvResult = mediaRepository.getLatestItems(
                            parentId = null,
                            includeItemTypes = "Series",
                            limit = 5,
                            fields = "BasicSyncInfo"
                        )
                        tvResult.getOrNull()?.let { items ->
                            val validItems = items.filter { it.id != null && !it.name.isNullOrBlank() }
                            DashboardCache.updateFeaturedItems("TV Shows", validItems)
                        }
                    }
                } catch (e: Exception) {
                }
            }
        }
    }

    // Continue watching loading with background thread
    LaunchedEffect(Unit) {
        if (!continueWatchingLoaded) {
            if (!DashboardCache.shouldRefreshContinueWatching() && DashboardCache.continueWatchingItems.isNotEmpty()) {
                continueWatchingItems = DashboardCache.continueWatchingItems
                continueWatchingLoading = false
                continueWatchingLoaded = true
                continueWatchingError = null
            } else {
                continueWatchingLoading = true
                continueWatchingError = null

                try {
                    withContext(Dispatchers.IO) {
                        val result = mediaRepository.getResumeItems(limit = 5)

                        withContext(Dispatchers.Main) {
                            result.fold(
                                onSuccess = { items ->
                                    val validItems = items.filter { item ->
                                        val userData = item.userData
                                        item.id != null &&
                                        !item.name.isNullOrBlank() &&
                                        userData?.playedPercentage != null &&
                                        userData.playedPercentage!! > 0 &&
                                        userData.playedPercentage!! < 95
                                    }
                                    continueWatchingItems = validItems
                                    DashboardCache.updateContinueWatching(validItems)
                                    continueWatchingLoading = false
                                    continueWatchingLoaded = true
                                },
                                onFailure = { throwable ->
                                    continueWatchingError = throwable.message ?: "Failed to load continue watching items"
                                    continueWatchingLoading = false
                                    continueWatchingLoaded = true
                                }
                            )
                        }
                    }
                } catch (e: Exception) {
                    continueWatchingError = e.message ?: "Unknown error occurred"
                    continueWatchingLoading = false
                    continueWatchingLoaded = true
                }
            }
        }
    }

    LazyColumn(
        state = lazyColumnState,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = if (isScrolling) {
                    CompositingStrategy.Auto
                } else {
                    CompositingStrategy.Offscreen
                }
                renderEffect = null
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
        userScrollEnabled = true,
        flingBehavior = ScrollableDefaults.flingBehavior()
    ) {
        item {
            FeatureTab(
                featuredItems = featuredItems,
                isLoading = featuredLoading,
                error = featuredError,
                selectedCategory = selectedCategory,
                onItemClick = onNavigateToDetail,
                onLogout = onLogout,
                onCategorySelected = { category ->
                    selectedCategory = category
                }
            )
        }

        // Continue Watching section - only show on Home tab
        if (selectedCategory == "Home" && (continueWatchingLoading || continueWatchingItems.isNotEmpty())) {
            item("continue_watching") {
                Column(
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    if (continueWatchingLoading) {
                        Text(
                            text = "Continue Watching",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        ContinueWatchingSkeleton()
                    } else {
                        ContinueWatchingSection(
                            items = continueWatchingItems,
                            isLoading = continueWatchingLoading,
                            error = continueWatchingError,
                            onItemClick = onNavigateToDetail
                        )
                    }
                }
            }
        }

        // Only show library sections on Home tab
        if (selectedCategory == "Home") {
            item("library_sections") {
                val topPadding = if (continueWatchingItems.isEmpty() && !continueWatchingLoading) 16.dp else 0.dp

                Column(
                    modifier = Modifier.padding(top = topPadding)
                ) {
                    LibrarySections(
                        onItemClick = onNavigateToDetail
                    )
                }
            }
        } else if (selectedCategory == "Movies") {
            item("movie_genres") {
                val topPadding = if (continueWatchingItems.isEmpty() && !continueWatchingLoading) 16.dp else 0.dp

                Column(
                    modifier = Modifier.padding(top = topPadding)
                ) {
                    MovieGenreSections(
                        onItemClick = onNavigateToDetail
                    )
                }
            }


        } else if (selectedCategory == "TV Shows") {
            item("tvshow_genres") {
                val topPadding = if (continueWatchingItems.isEmpty() && !continueWatchingLoading) 16.dp else 0.dp

                Column(
                    modifier = Modifier.padding(top = topPadding)
                ) {
                    TVShowGenreSections(
                        onItemClick = onNavigateToDetail
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingSection(
    items: List<com.jellycine.data.model.BaseItemDto>,
    isLoading: Boolean,
    error: String?,
    onItemClick: (com.jellycine.data.model.BaseItemDto) -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { com.jellycine.data.repository.MediaRepositoryProvider.getInstance(context) }

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
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                            renderEffect = null
                            clip = false
                        }
                ) {
                    items(
                        count = minOf(items.size, 10),
                        key = { index -> items[index].id ?: index }
                    ) { index ->
                        ContinueWatchingCard(
                            item = items[index],
                            mediaRepository = mediaRepository,
                            onClick = { onItemClick(items[index]) }
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
    item: com.jellycine.data.model.BaseItemDto,
    mediaRepository: com.jellycine.data.repository.MediaRepository,
    onClick: () -> Unit = {}
) {
    val stableItem = remember(item.id) { StableBaseItem.from(item) }
    val context = LocalContext.current

    var imageUrl by remember(stableItem.id, stableItem.seriesId) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(stableItem.id) {
        val itemId = stableItem.id
        if (itemId != null) {
            withContext(Dispatchers.IO) {
                try {
                    val actualItemId = if (stableItem.type == "Episode" && !stableItem.seriesId.isNullOrBlank()) {
                        stableItem.seriesId!!
                    } else {
                        itemId
                    }

                    val url = mediaRepository.getImageUrl(
                        itemId = actualItemId,
                        width = 250,
                        height = 140,
                        quality = 100
                    ).first()

                    withContext(Dispatchers.Main) {
                        imageUrl = url
                    }
                } catch (e: Exception) {
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .width(200.dp)
            .height(120.dp)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                renderEffect = null
                clip = false
            }
            .drawWithCache {
                onDrawBehind { }
            },
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
            // Movie poster
            JellyfinPosterImage(
                imageUrl = imageUrl,
                contentDescription = stableItem.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    },
                context = context,
                contentScale = ContentScale.Crop
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
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        },
                    color = Color.Red,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }

            // Movie info overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
                    .padding(12.dp)
            ) {
                Column {
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

                    Text(
                        text = itemName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = yearText,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
}

@Composable
private fun LibrarySections(
    onItemClick: (com.jellycine.data.model.BaseItemDto) -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { com.jellycine.data.repository.MediaRepositoryProvider.getInstance(context) }

    var libraryViews by remember { mutableStateOf<List<com.jellycine.data.model.BaseItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasLoaded by remember { mutableStateOf(false) }

    // Load library views
    LaunchedEffect(Unit) {
        if (!hasLoaded) {
            try {
                withContext(Dispatchers.IO) {
                    val result = mediaRepository.getUserViews()

                    withContext(Dispatchers.Main) {
                        result.fold(
                            onSuccess = { queryResult ->
                                libraryViews = queryResult.items?.filter {
                                    it.id != null &&
                                    !it.name.isNullOrBlank() &&
                                    it.collectionType != "boxsets" &&
                                    it.collectionType != "playlists" &&
                                    it.collectionType != "folders" &&
                                    (it.type == "CollectionFolder" || it.type == "Folder") &&

                                    (it.collectionType == "movies" || it.collectionType == "tvshows" ||
                                     it.collectionType == "music" || it.collectionType == "books" ||
                                     it.collectionType == "mixed" || it.collectionType == null)
                                } ?: emptyList()
                                isLoading = false
                                hasLoaded = true
                            },
                            onFailure = {
                                isLoading = false
                                hasLoaded = true
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                isLoading = false
                hasLoaded = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        when {
            isLoading -> {
                repeat(2) {
                    Column {
                        ShimmerEffect(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .width(120.dp)
                                .height(24.dp),
                            cornerRadius = 4f
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                        ) {
                            items(4) { index ->
                                ShimmerEffect(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .height(210.dp),
                                    cornerRadius = 16f
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
            libraryViews.isNotEmpty() -> {
                libraryViews.forEachIndexed { index, library ->
                    ProgressiveLibrarySection(
                        library = library,
                        mediaRepository = mediaRepository,
                        onItemClick = onItemClick,
                        loadDelay = index * 300L
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ProgressiveLibrarySection(
    library: com.jellycine.data.model.BaseItemDto,
    mediaRepository: com.jellycine.data.repository.MediaRepository,
    onItemClick: (com.jellycine.data.model.BaseItemDto) -> Unit = {},
    loadDelay: Long = 0L
) {
    var libraryItems by remember(library.id) { mutableStateOf<List<com.jellycine.data.model.BaseItemDto>>(emptyList()) }
    var isLoading by remember(library.id) { mutableStateOf(true) }
    var hasLoaded by remember(library.id) { mutableStateOf(false) }

    // Progressive loading with staggered delays
    LaunchedEffect(library.id) {
        if (!hasLoaded) {
            if (loadDelay > 0) {
                delay(loadDelay)
            }

            library.id?.let { libraryId ->
                try {
                    withContext(Dispatchers.IO) {
                        val includeItemTypes = when (library.collectionType) {
                            "tvshows" -> "Episode"
                            "movies" -> "Movie"
                            else -> "Movie,Series,Episode"
                        }

                        val result = mediaRepository.getLatestItems(
                            parentId = libraryId,
                            includeItemTypes = includeItemTypes,
                            limit = 10,
                            fields = "ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId"
                        )

                        val validItems = result.getOrNull()?.filter {
                            it.id != null && !it.name.isNullOrBlank()
                        } ?: emptyList()

                        withContext(Dispatchers.Main) {
                            libraryItems = validItems.take(6)
                            isLoading = false
                            hasLoaded = true
                        }
                    }
                } catch (e: Exception) {
                    isLoading = false
                    hasLoaded = true
                }
            }
        }
    }

    Column {
        Text(
            text = "Recently Added • ${library.name ?: "Library"}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when {
            isLoading -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                ) {
                    items(4) {
                        ShimmerEffect(
                            modifier = Modifier
                                .width(140.dp)
                                .height(210.dp),
                            cornerRadius = 16f
                        )
                    }
                }
            }

            libraryItems.isNotEmpty() -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                ) {
                    items(
                        count = libraryItems.size,
                        key = { index -> libraryItems[index].id ?: index }
                    ) { index ->
                        LibraryItemCard(
                            item = libraryItems[index],
                            mediaRepository = mediaRepository,
                            onClick = { onItemClick(libraryItems[index]) }
                        )
                    }
                }
            }

            else -> {
                // Minimal empty state
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryItemCard(
    item: com.jellycine.data.model.BaseItemDto,
    mediaRepository: com.jellycine.data.repository.MediaRepository,
    onClick: () -> Unit = {}
) {
    val stableItem = remember(item.id) { StableBaseItem.from(item) }
    val context = LocalContext.current

    var imageUrl by remember(stableItem.id, stableItem.seriesId) { mutableStateOf<String?>(null) }

    LaunchedEffect(stableItem.id, stableItem.seriesId) {
        val itemId = stableItem.id
        if (itemId != null) {
            withContext(Dispatchers.IO) {
                try {
                    val actualItemId = if (stableItem.type == "Episode" && !stableItem.seriesId.isNullOrBlank()) {
                        stableItem.seriesId!!
                    } else {
                        itemId
                    }

                    val url = mediaRepository.getImageUrl(
                        itemId = actualItemId,
                        width = 200,
                        height = 300,
                        quality = 100
                    ).first()

                    withContext(Dispatchers.Main) {
                        imageUrl = url
                    }
                } catch (e: Exception) {
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .width(140.dp)
            .height(210.dp)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                renderEffect = null
                clip = false
            }
            .drawWithCache {
                onDrawBehind { }
            },
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
            JellyfinPosterImage(
                imageUrl = imageUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
                context = context,
                contentScale = ContentScale.Crop
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

            // Title overlay at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        ),
                        RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                    )
                    .padding(12.dp)
            ) {
                // Show series name for episodes, otherwise show item name
                val displayName = if (item.type == "Episode" && !item.seriesName.isNullOrBlank()) {
                    item.seriesName!!
                } else {
                    item.name ?: "Unknown"
                }

                Text(
                    text = displayName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
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
        Box(
            modifier = modifier
                .background(
                    Color(0xFF1976D2).copy(alpha = 0.95f),
                    shape = RoundedCornerShape(14.dp)
                )
                .border(
                    1.5.dp,
                    Color.White.copy(alpha = 0.4f),
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = count.toString(),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
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

// Skeleton Components
@Composable
private fun ShimmerEffect(
    modifier: Modifier = Modifier,
    cornerRadius: Float = 12f
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha = transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Canvas(
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    ) {
        drawRoundRect(
            color = androidx.compose.ui.graphics.Color(0xFF2A2A2A).copy(alpha = alpha.value),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )
    }
}

@Composable
private fun ContinueWatchingSkeleton() {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(5) {
            ShimmerEffect(
                modifier = Modifier
                    .width(200.dp)
                    .height(120.dp),
                cornerRadius = 12f
            )
        }
    }
}

@Composable
private fun LibrarySkeleton() {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(6) {
            ShimmerEffect(
                modifier = Modifier
                    .width(140.dp)
                    .height(210.dp),
                cornerRadius = 16f
            )
        }
    }
}

// Enhanced cache for all dashboard data
private object DashboardCache {
    var homeItems: List<com.jellycine.data.model.BaseItemDto> = emptyList()
    var movieItems: List<com.jellycine.data.model.BaseItemDto> = emptyList()
    var tvShowItems: List<com.jellycine.data.model.BaseItemDto> = emptyList()
    var continueWatchingItems: List<com.jellycine.data.model.BaseItemDto> = emptyList()

    var homeItemsLoadTime: Long = 0
    var movieItemsLoadTime: Long = 0
    var tvShowItemsLoadTime: Long = 0
    var continueWatchingLoadTime: Long = 0

    // Cache timeout: 5 minutes for featured items
    private const val FEATURED_CACHE_TIMEOUT = 300_000L
    private const val CONTINUE_WATCHING_CACHE_TIMEOUT = 120_000L

    fun shouldRefreshFeaturedItems(category: String): Boolean {
        val loadTime = when (category) {
            "Movies" -> movieItemsLoadTime
            "TV Shows" -> tvShowItemsLoadTime
            else -> homeItemsLoadTime
        }
        return System.currentTimeMillis() - loadTime > FEATURED_CACHE_TIMEOUT
    }

    fun getFeaturedItems(category: String): List<com.jellycine.data.model.BaseItemDto> {
        return when (category) {
            "Movies" -> movieItems
            "TV Shows" -> tvShowItems
            else -> homeItems
        }
    }

    fun updateFeaturedItems(category: String, items: List<com.jellycine.data.model.BaseItemDto>) {
        when (category) {
            "Movies" -> {
                movieItems = items
                movieItemsLoadTime = System.currentTimeMillis()
            }
            "TV Shows" -> {
                tvShowItems = items
                tvShowItemsLoadTime = System.currentTimeMillis()
            }
            else -> {
                homeItems = items
                homeItemsLoadTime = System.currentTimeMillis()
            }
        }
    }

    fun shouldRefreshContinueWatching(): Boolean {
        return System.currentTimeMillis() - continueWatchingLoadTime > CONTINUE_WATCHING_CACHE_TIMEOUT
    }

    fun updateContinueWatching(items: List<com.jellycine.data.model.BaseItemDto>) {
        continueWatchingItems = items
        continueWatchingLoadTime = System.currentTimeMillis()
    }
}

// Cache for genre data
private object GenreCache {
    var movieGenres: List<com.jellycine.data.model.BaseItemDto> = emptyList()
    var tvGenres: List<com.jellycine.data.model.BaseItemDto> = emptyList()
    var movieGenresLoadTime: Long = 0
    var tvGenresLoadTime: Long = 0

    // Cache for genre items
    private val genreItemsCache = mutableMapOf<String, List<com.jellycine.data.model.BaseItemDto>>()
    private val genreItemsLoadTime = mutableMapOf<String, Long>()

    fun shouldRefreshMovieGenres(): Boolean {
        return movieGenres.isEmpty() || System.currentTimeMillis() - movieGenresLoadTime > 600_000 // 10 minutes
    }

    fun shouldRefreshTVGenres(): Boolean {
        return tvGenres.isEmpty() || System.currentTimeMillis() - tvGenresLoadTime > 600_000 // 10 minutes
    }

    fun shouldRefreshGenreItems(genreId: String): Boolean {
        val lastLoadTime = genreItemsLoadTime[genreId] ?: 0
        return !genreItemsCache.containsKey(genreId) || System.currentTimeMillis() - lastLoadTime > 300_000 // 5 minutes
    }

    fun updateMovieGenres(genres: List<com.jellycine.data.model.BaseItemDto>) {
        movieGenres = genres
        movieGenresLoadTime = System.currentTimeMillis()
    }

    fun updateTVGenres(genres: List<com.jellycine.data.model.BaseItemDto>) {
        tvGenres = genres
        tvGenresLoadTime = System.currentTimeMillis()
    }

    fun updateGenreItems(genreId: String, items: List<com.jellycine.data.model.BaseItemDto>) {
        genreItemsCache[genreId] = items
        genreItemsLoadTime[genreId] = System.currentTimeMillis()
    }

    fun getGenreItems(genreId: String): List<com.jellycine.data.model.BaseItemDto> {
        return genreItemsCache[genreId] ?: emptyList()
    }
}

@Composable
private fun MovieGenreSections(
    onItemClick: (com.jellycine.data.model.BaseItemDto) -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { com.jellycine.data.repository.MediaRepositoryProvider.getInstance(context) }

    var movieGenres by remember { mutableStateOf(GenreCache.movieGenres) }
    var isLoading by remember { mutableStateOf(GenreCache.shouldRefreshMovieGenres()) }

    // Optimize movie genres loading with background thread
    LaunchedEffect(Unit) {
        if (GenreCache.shouldRefreshMovieGenres()) {
            isLoading = true
            try {
                withContext(Dispatchers.IO) {
                    val result = mediaRepository.getGenres(includeItemTypes = "Movie")

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
            repeat(3) {
                Column {
                    // Genre title skeleton
                    ShimmerEffect(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .width(150.dp)
                            .height(24.dp),
                        cornerRadius = 4f
                    )
                    // Genre items skeleton
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                    ) {
                        items(4) { index ->
                            ShimmerEffect(
                                modifier = Modifier
                                    .width(140.dp)
                                    .height(210.dp),
                                cornerRadius = 16f
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        } else {
            // Show only first 3 genres with progressive loading
            val genresToShow = movieGenres.take(3)
            genresToShow.forEachIndexed { index, genre ->
                ProgressiveMovieGenreSection(
                    genre = genre,
                    mediaRepository = mediaRepository,
                    onItemClick = onItemClick,
                    loadDelay = index * 400L
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TVShowGenreSections(
    onItemClick: (com.jellycine.data.model.BaseItemDto) -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { com.jellycine.data.repository.MediaRepositoryProvider.getInstance(context) }

    var tvGenres by remember { mutableStateOf(GenreCache.tvGenres) }
    var isLoading by remember { mutableStateOf(GenreCache.shouldRefreshTVGenres()) }

    // Load TV show genres with caching
    LaunchedEffect(Unit) {
        if (GenreCache.shouldRefreshTVGenres()) {
            isLoading = true
            try {
                val result = mediaRepository.getGenres(includeItemTypes = "Series")
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
            repeat(3) {
                Column {
                    // Genre title skeleton
                    ShimmerEffect(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .width(150.dp)
                            .height(24.dp),
                        cornerRadius = 4f
                    )
                    // Genre items skeleton
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(4) {
                            ShimmerEffect(
                                modifier = Modifier
                                    .width(140.dp)
                                    .height(210.dp),
                                cornerRadius = 16f
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        } else {
            // Show only first 3 genres with progressive loading
            val genresToShow = tvGenres.take(3)
            genresToShow.forEachIndexed { index, genre ->
                ProgressiveTVShowGenreSection(
                    genre = genre,
                    mediaRepository = mediaRepository,
                    onItemClick = onItemClick,
                    loadDelay = index * 400L
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ProgressiveMovieGenreSection(
    genre: com.jellycine.data.model.BaseItemDto,
    mediaRepository: com.jellycine.data.repository.MediaRepository,
    onItemClick: (com.jellycine.data.model.BaseItemDto) -> Unit = {},
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
                        limit = 8
                    )

                    withContext(Dispatchers.Main) {
                        result.fold(
                            onSuccess = { items ->
                                val validItems = items.filter {
                                    it.id != null && !it.name.isNullOrBlank()
                                }.take(5)
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
        // Section Header
        Text(
            text = "${genre.name ?: "Movies"}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when {
            isLoading -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                ) {
                    items(4) { index ->
                        ShimmerEffect(
                            modifier = Modifier
                                .width(140.dp)
                                .height(210.dp),
                            cornerRadius = 16f
                        )
                    }
                }
            }
            genreMovies.isNotEmpty() -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
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
                // Minimal empty state
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

@Composable
private fun ProgressiveTVShowGenreSection(
    genre: com.jellycine.data.model.BaseItemDto,
    mediaRepository: com.jellycine.data.repository.MediaRepository,
    onItemClick: (com.jellycine.data.model.BaseItemDto) -> Unit = {},
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
                        limit = 8
                    )

                    withContext(Dispatchers.Main) {
                        result.fold(
                            onSuccess = { items ->
                                val validItems = items.filter {
                                    it.id != null && !it.name.isNullOrBlank()
                                }.take(5)
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
        // Section Header
        Text(
            text = "${genre.name ?: "TV Shows"}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when {
            isLoading -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(4) {
                        ShimmerEffect(
                            modifier = Modifier
                                .width(140.dp)
                                .height(210.dp),
                            cornerRadius = 16f
                        )
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
                // Minimal empty state
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