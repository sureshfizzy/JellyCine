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

// Stable data class to prevent unnecessary recompositions
@Stable
@Immutable
private data class StableLibraryState(
    val libraryViews: List<com.jellycine.data.model.BaseItemDto>,
    val isLoading: Boolean
)


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
    onNavigateToDetail: (com.jellycine.data.model.BaseItemDto) -> Unit = {},
    isTabActive: Boolean = true
) {
    var selectedCategory by remember { mutableStateOf("Home") }

    var continueWatchingItems by remember { mutableStateOf<List<com.jellycine.data.model.BaseItemDto>>(emptyList()) }
    var continueWatchingLoading by remember { mutableStateOf(true) }
    var continueWatchingError by remember { mutableStateOf<String?>(null) }
    var continueWatchingLoaded by remember { mutableStateOf(false) }

    // Featured items state
    var featuredItems by remember { mutableStateOf<List<com.jellycine.data.model.BaseItemDto>>(emptyList()) }
    var featuredLoading by remember { mutableStateOf(true) }
    var featuredError by remember { mutableStateOf<String?>(null) }

    // Library views state
    var libraryViews by remember { mutableStateOf<List<com.jellycine.data.model.BaseItemDto>>(emptyList()) }
    var libraryViewsLoading by remember { mutableStateOf(true) }
    var libraryViewsLoaded by remember { mutableStateOf(false) }

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
                            limit = 10,
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
                            limit = 10,
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

    // Library views loading
    LaunchedEffect(Unit) {
        if (!libraryViewsLoaded) {
            try {
                withContext(Dispatchers.IO) {
                    val result = mediaRepository.getUserViews()

                    withContext(Dispatchers.Main) {
                        result.fold(
                            onSuccess = { queryResult ->
                                val validViews = queryResult.items?.filter {
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

                                LibraryCache.updateLibraryViews(validViews)
                                libraryViews = validViews
                                libraryViewsLoading = false
                                libraryViewsLoaded = true
                            },
                            onFailure = {
                                libraryViewsLoading = false
                                libraryViewsLoaded = true
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                libraryViewsLoading = false
                libraryViewsLoaded = true
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
                        val result = mediaRepository.getResumeItems(limit = 12)

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
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

        // Continue Watching section - only show on Home tab
        if (selectedCategory == "Home" && (continueWatchingLoading || continueWatchingItems.isNotEmpty())) {
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

        // Only show library sections on Home tab
        if (selectedCategory == "Home") {

            val topPadding = if (continueWatchingItems.isEmpty() && !continueWatchingLoading) 16.dp else 0.dp

            Column(
                modifier = Modifier.padding(top = topPadding)
            ) {
                LibrarySections(
                    libraryViews = libraryViews,
                    isLoading = libraryViewsLoading,
                    onItemClick = onNavigateToDetail
                )
            }
        } else if (selectedCategory == "Movies") {
            val topPadding = if (continueWatchingItems.isEmpty() && !continueWatchingLoading) 16.dp else 0.dp

            Column(
                modifier = Modifier.padding(top = topPadding)
            ) {
                MovieGenreSections(
                    onItemClick = onNavigateToDetail
                )
            }


        } else if (selectedCategory == "TV Shows") {
            val topPadding = if (continueWatchingItems.isEmpty() && !continueWatchingLoading) 16.dp else 0.dp

            Column(
                modifier = Modifier.padding(top = topPadding)
            ) {
                TVShowGenreSections(
                    onItemClick = onNavigateToDetail
                )
            }
        }
        Spacer(modifier = Modifier.height(100.dp))
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        count = minOf(items.size, 8),
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
                        imageType = "Thumb",
                        width = 300,
                        height = 180,
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
                // Movie poster
                JellyfinPosterImage(
                    imageUrl = imageUrl,
                    contentDescription = stableItem.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
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
    libraryViews: List<com.jellycine.data.model.BaseItemDto>,
    isLoading: Boolean,
    onItemClick: (com.jellycine.data.model.BaseItemDto) -> Unit = {}
) {
    // Use a stable composition approach
    StableLibrarySectionsContent(
        libraryViews = libraryViews,
        isLoading = isLoading,
        onItemClick = onItemClick
    )
}

@Composable
private fun StableLibrarySectionsContent(
    libraryViews: List<com.jellycine.data.model.BaseItemDto>,
    isLoading: Boolean,
    onItemClick: (com.jellycine.data.model.BaseItemDto) -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { com.jellycine.data.repository.MediaRepositoryProvider.getInstance(context) }

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
                // Use a stable list approach instead of forEachIndexed
                StableLibraryList(
                    libraries = libraryViews,
                    mediaRepository = mediaRepository,
                    onItemClick = onItemClick
                )
            }
        }
    }
}

@Composable
private fun StableLibraryList(
    libraries: List<com.jellycine.data.model.BaseItemDto>,
    mediaRepository: com.jellycine.data.repository.MediaRepository,
    onItemClick: (com.jellycine.data.model.BaseItemDto) -> Unit = {}
) {
    // Create a stable list that only changes when content changes
    val stableLibraries = remember(libraries.map { it.id }.hashCode()) { libraries }



    // Render libraries with stable keys
    stableLibraries.forEachIndexed { index, library ->
        key(library.id ?: index) {
            ProgressiveLibrarySection(
                library = library,
                mediaRepository = mediaRepository,
                onItemClick = onItemClick,
                loadDelay = index * 150L
            )
        }
        if (index < stableLibraries.size - 1) {
            Spacer(modifier = Modifier.height(16.dp))
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
    val libraryId = library.id ?: return

    // Use cache to check if we already have data
    val cachedItems = LibraryCache.getLibraryItems(libraryId)
    val shouldRefresh = LibraryCache.shouldRefreshLibraryItems(libraryId)

    var libraryItems by remember(libraryId) {
        mutableStateOf(if (!shouldRefresh && cachedItems.isNotEmpty()) cachedItems else emptyList())
    }
    var isLoading by remember(libraryId) {
        mutableStateOf(shouldRefresh || cachedItems.isEmpty())
    }
    var hasLoaded by remember(libraryId) {
        mutableStateOf(!shouldRefresh && cachedItems.isNotEmpty())
    }

    // Prevent duplicate loading with a loading state tracker
    var isCurrentlyLoading by remember(libraryId) { mutableStateOf(false) }

    // Optimized loading with proper network throttling and duplicate prevention
    LaunchedEffect(libraryId) {
        if (!hasLoaded && !isCurrentlyLoading) {
            isCurrentlyLoading = true
            try {
                // Add delay to prevent network overload
                if (loadDelay > 0) {
                    delay(loadDelay)
                }

                // Use a separate dispatcher to avoid blocking UI
                withContext(Dispatchers.IO) {
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

                    val validItems = result.getOrNull()?.filter {
                        it.id != null && !it.name.isNullOrBlank()
                    } ?: emptyList()

                    // Cache the results immediately
                    LibraryCache.updateLibraryItems(libraryId, validItems)

                    // Update UI on main thread
                    withContext(Dispatchers.Main) {
                        libraryItems = validItems
                        isLoading = false
                        hasLoaded = true
                        isCurrentlyLoading = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    hasLoaded = true
                    isCurrentlyLoading = false
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
private fun buildMetadataText(item: com.jellycine.data.model.BaseItemDto): String {
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

// Cache for library data
private object LibraryCache {
    var libraryViews: List<com.jellycine.data.model.BaseItemDto> = emptyList()
    private var libraryViewsLoadTime: Long = 0
    private val libraryItemsCache = mutableMapOf<String, List<com.jellycine.data.model.BaseItemDto>>()
    private val libraryItemsLoadTime = mutableMapOf<String, Long>()
    private const val LIBRARY_CACHE_TIMEOUT = 300_000L
    private const val LIBRARY_VIEWS_CACHE_TIMEOUT = 600_000L

    fun shouldRefreshLibraryViews(): Boolean {
        return libraryViews.isEmpty() || System.currentTimeMillis() - libraryViewsLoadTime > LIBRARY_VIEWS_CACHE_TIMEOUT
    }

    fun updateLibraryViews(views: List<com.jellycine.data.model.BaseItemDto>) {
        libraryViews = views
        libraryViewsLoadTime = System.currentTimeMillis()
    }

    fun shouldRefreshLibraryItems(libraryId: String): Boolean {
        val lastLoadTime = libraryItemsLoadTime[libraryId] ?: 0
        return !libraryItemsCache.containsKey(libraryId) || System.currentTimeMillis() - lastLoadTime > LIBRARY_CACHE_TIMEOUT
    }

    fun updateLibraryItems(libraryId: String, items: List<com.jellycine.data.model.BaseItemDto>) {
        libraryItemsCache[libraryId] = items
        libraryItemsLoadTime[libraryId] = System.currentTimeMillis()
    }

    fun getLibraryItems(libraryId: String): List<com.jellycine.data.model.BaseItemDto> {
        return libraryItemsCache[libraryId] ?: emptyList()
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