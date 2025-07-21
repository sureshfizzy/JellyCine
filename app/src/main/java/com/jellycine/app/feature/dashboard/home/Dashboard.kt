package com.jellycine.app.feature.dashboard.home

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
import com.jellycine.app.util.JellyfinPosterImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.border


// Sample movie data for demonstration
data class Movie(
    val id: String,
    val title: String,
    val year: String,
    val posterUrl: String? = null
)

@Composable
fun Dashboard(
    onLogout: () -> Unit = {},
    onNavigateToDetail: (com.jellycine.data.model.BaseItemDto) -> Unit = {}
) {
    var refreshTrigger by remember { mutableStateOf(0) }

    var selectedCategory by remember { mutableStateOf("Home") }

    var continueWatchingItems by remember { mutableStateOf<List<com.jellycine.data.model.BaseItemDto>>(emptyList()) }
    var continueWatchingLoading by remember { mutableStateOf(true) }
    var continueWatchingError by remember { mutableStateOf<String?>(null) }
    var continueWatchingLoaded by remember { mutableStateOf(false) }

    var featuredItems by remember { mutableStateOf<List<com.jellycine.data.model.BaseItemDto>>(emptyList()) }
    var featuredLoading by remember { mutableStateOf(true) }
    var featuredError by remember { mutableStateOf<String?>(null) }
    var featuredLoaded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val mediaRepository = remember { com.jellycine.data.repository.MediaRepositoryProvider.getInstance(context) }

    LaunchedEffect(key1 = "featured_items_dashboard", key2 = refreshTrigger, key3 = selectedCategory) {
        if (featuredItems.isEmpty() || refreshTrigger > 0 || selectedCategory != "Home") {
            featuredLoading = true
            featuredError = null

            try {
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
                result.fold(
                    onSuccess = { items ->
                        val validItems = items.filter {
                            it.id != null && !it.name.isNullOrBlank()
                        }
                        featuredItems = validItems
                        featuredLoading = false
                        featuredLoaded = true
                    },
                    onFailure = { throwable ->
                        featuredError = throwable.message ?: "Failed to load featured content"
                        featuredLoading = false
                    }
                )
            } catch (e: Exception) {
                featuredError = e.message ?: "Unknown error occurred"
                featuredLoading = false
            }
        }
    }

    LaunchedEffect(key1 = "continue_watching_dashboard", key2 = refreshTrigger) {
        if (continueWatchingItems.isEmpty() || refreshTrigger > 0) {
            continueWatchingLoading = true
            continueWatchingError = null

            try {
                val result = mediaRepository.getResumeItems(limit = 5)
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
                        continueWatchingLoading = false
                        continueWatchingLoaded = true
                    },
                    onFailure = { throwable ->
                        continueWatchingError = throwable.message ?: "Failed to load continue watching items"
                        continueWatchingLoading = false
                    }
                )
            } catch (e: Exception) {
                continueWatchingError = e.message ?: "Unknown error occurred"
                continueWatchingLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshTrigger = 1
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                },
                refreshTrigger = refreshTrigger
            )
        }

        item("continue_watching") {
            // Only show Continue Watching section if there are items or it's loading
            if (continueWatchingLoading || continueWatchingItems.isNotEmpty()) {

                Column(
                    modifier = Modifier.padding(top = 8.dp)
                ) {
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
            item("library_sections") {
                val topPadding = if (continueWatchingItems.isEmpty() && !continueWatchingLoading) 16.dp else 0.dp

                Column(
                    modifier = Modifier.padding(top = topPadding)
                ) {
                    LibrarySections(
                        onItemClick = onNavigateToDetail,
                        refreshTrigger = refreshTrigger
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
                        onItemClick = onNavigateToDetail,
                        refreshTrigger = refreshTrigger
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
                        onItemClick = onNavigateToDetail,
                        refreshTrigger = refreshTrigger
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(items.take(10)) { item ->
                        ContinueWatchingCard(
                            item = item,
                            mediaRepository = mediaRepository,
                            onClick = { onItemClick(item) }
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
    val context = LocalContext.current
    var imageUrl by remember(item.id, item.seriesId) { mutableStateOf<String?>(null) }

    // Get image URL with higher quality
    LaunchedEffect(item.id) {
        val itemId = item.id
        if (itemId != null) {
            // For episodes, get the series poster instead
            val actualItemId = if (item.type == "Episode" && !item.seriesId.isNullOrBlank()) {
                item.seriesId!!
            } else {
                itemId
            }

            imageUrl = mediaRepository.getImageUrl(
                itemId = actualItemId,
                width = 250,
                height = 140,
                quality = 50
            ).first()
        }
    }

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
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                context = context,
                contentScale = ContentScale.Crop
            )

            // Progress bar at bottom
            item.userData?.playedPercentage?.let { percentage ->
                LinearProgressIndicator(
                    progress = (percentage.toFloat() / 100f).coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter),
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
                    Text(
                        text = item.name ?: "Unknown",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val typeText = when (item.type) {
                        "Movie" -> "Movie"
                        "Series" -> "TV Series"
                        "Episode" -> "Episode"
                        else -> item.type ?: "Media"
                    }

                    Text(
                        text = "${item.productionYear ?: ""} • $typeText",
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
    onItemClick: (com.jellycine.data.model.BaseItemDto) -> Unit = {},
    refreshTrigger: Int = 0
) {
    val context = LocalContext.current
    val mediaRepository = remember { com.jellycine.data.repository.MediaRepositoryProvider.getInstance(context) }

    var libraryViews by remember { mutableStateOf<List<com.jellycine.data.model.BaseItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasLoaded by remember { mutableStateOf(false) }

    // Load library views - only load if not already loaded or when refreshTrigger changes
    LaunchedEffect(refreshTrigger) {
        if (!hasLoaded || refreshTrigger > 0) {
            isLoading = true

            if (refreshTrigger == 0) {
                delay(200L)
            }

            try {
                val result = mediaRepository.getUserViews()
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
            } catch (e: Exception) {
                isLoading = false
                hasLoaded = true
            }
        }
    }

    when {
        isLoading -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                repeat(3) {
                    Column {
                        ShimmerEffect(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .width(120.dp)
                                .height(24.dp),
                            cornerRadius = 4f
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Library items skeleton
                        LibrarySkeleton()
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
        libraryViews.isNotEmpty() -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                // Show all libraries
                libraryViews.forEach { library ->
                    LibrarySection(
                        library = library,
                        mediaRepository = mediaRepository,
                        onItemClick = onItemClick,
                        refreshTrigger = refreshTrigger
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun LibrarySection(
    library: com.jellycine.data.model.BaseItemDto,
    mediaRepository: com.jellycine.data.repository.MediaRepository,
    onItemClick: (com.jellycine.data.model.BaseItemDto) -> Unit = {},
    refreshTrigger: Int = 0
) {
    var libraryItems by remember(library.id) { mutableStateOf<List<com.jellycine.data.model.BaseItemDto>>(emptyList()) }
    var isLoading by remember(library.id) { mutableStateOf(true) }
    var hasLoaded by remember(library.id) { mutableStateOf(false) }

    // Load items for this library - only load if not already loaded or when refreshTrigger changes
    LaunchedEffect(library.id, refreshTrigger) {
        if (!hasLoaded || refreshTrigger > 0) {
            isLoading = true

            library.id?.let { libraryId ->
                try {
                    val includeItemTypes = when (library.collectionType) {
                        "tvshows" -> "Episode"
                        "movies" -> "Movie"
                        else -> "Movie,Series,Episode"
                    }

                    val result = mediaRepository.getLatestItems(
                        parentId = libraryId,
                        includeItemTypes = includeItemTypes,
                        limit = 20,
                        fields = "ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId"
                    )

                    val validItems = result.getOrNull()?.filter {
                        it.id != null && !it.name.isNullOrBlank()
                    } ?: emptyList()

                    libraryItems = validItems.take(10)
                    isLoading = false
                    hasLoaded = true

                } catch (e: Exception) {
                    isLoading = false
                    hasLoaded = true
                }
            }
        }
    }

    Column {
        // Section Header
        Text(
            text = "Recently Added • ${library.name ?: "Library"}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when {
            isLoading -> {
                LibrarySkeleton()
            }

            libraryItems.isNotEmpty() -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(libraryItems.take(10)) { item ->
                        LibraryItemCard(
                            item = item,
                            mediaRepository = mediaRepository,
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No items found",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
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
    val context = LocalContext.current
    var imageUrl by remember(item.id, item.seriesId) { mutableStateOf<String?>(null) }

    // Get image URL - for episodes, use series poster; for series/movies, use their own poster
    LaunchedEffect(item.id, item.seriesId) {
        val itemId = item.id
        if (itemId != null) {
            // For episodes, get the series poster instead
            val actualItemId = if (item.type == "Episode" && !item.seriesId.isNullOrBlank()) {
                item.seriesId!!
            } else {
                itemId
            }

            imageUrl = mediaRepository.getImageUrl(
                itemId = actualItemId,
                width = 200,
                height = 300,
                quality = 60
            ).first()
        }
    }

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
                    Color(0xFF1976D2).copy(alpha = 0.95f), // Blue background like Findroid
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
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Canvas(modifier = modifier) {
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
    onItemClick: (com.jellycine.data.model.BaseItemDto) -> Unit = {},
    refreshTrigger: Int = 0
) {
    val context = LocalContext.current
    val mediaRepository = remember { com.jellycine.data.repository.MediaRepositoryProvider.getInstance(context) }

    var movieGenres by remember { mutableStateOf(GenreCache.movieGenres) }
    var isLoading by remember { mutableStateOf(GenreCache.shouldRefreshMovieGenres()) }

    // Load movie genres with caching
    LaunchedEffect(refreshTrigger) {
        if (GenreCache.shouldRefreshMovieGenres()) {
            isLoading = true
            try {
                val result = mediaRepository.getGenres(includeItemTypes = "Movie")
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
                    Text(
                        text = "Loading...",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LibrarySkeleton()
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        } else {
            val genresToShow = movieGenres.take(5)
            genresToShow.forEach { genre ->
                MovieGenreSection(
                    genre = genre,
                    mediaRepository = mediaRepository,
                    onItemClick = onItemClick,
                    refreshTrigger = refreshTrigger
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TVShowGenreSections(
    onItemClick: (com.jellycine.data.model.BaseItemDto) -> Unit = {},
    refreshTrigger: Int = 0
) {
    val context = LocalContext.current
    val mediaRepository = remember { com.jellycine.data.repository.MediaRepositoryProvider.getInstance(context) }

    var tvGenres by remember { mutableStateOf(GenreCache.tvGenres) }
    var isLoading by remember { mutableStateOf(GenreCache.shouldRefreshTVGenres()) }

    // Load TV show genres with caching
    LaunchedEffect(refreshTrigger) {
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
            // Use the same skeleton as home
            repeat(3) {
                Column {
                    Text(
                        text = "Loading...",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LibrarySkeleton()
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        } else {
            // Show only first 5 genres initially for better performance
            val genresToShow = tvGenres.take(5)
            genresToShow.forEach { genre ->
                TVShowGenreSection(
                    genre = genre,
                    mediaRepository = mediaRepository,
                    onItemClick = onItemClick,
                    refreshTrigger = refreshTrigger
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MovieGenreSection(
    genre: com.jellycine.data.model.BaseItemDto,
    mediaRepository: com.jellycine.data.repository.MediaRepository,
    onItemClick: (com.jellycine.data.model.BaseItemDto) -> Unit = {},
    refreshTrigger: Int = 0
) {
    val genreId = genre.id ?: return // Skip if no genre ID
    var genreMovies by remember(genreId) { mutableStateOf(GenreCache.getGenreItems(genreId)) }
    var isLoading by remember(genreId) { mutableStateOf(GenreCache.shouldRefreshGenreItems(genreId)) }

    // Load movies for this specific genre with caching
    LaunchedEffect(genreId, refreshTrigger) {
        if (GenreCache.shouldRefreshGenreItems(genreId)) {
            isLoading = true
            try {
                val result = mediaRepository.getItemsByGenre(
                    genreId = genreId,
                    includeItemTypes = "Movie",
                    limit = 20
                )

                result.fold(
                    onSuccess = { items ->
                        val validItems = items.filter {
                            it.id != null && !it.name.isNullOrBlank()
                        }.take(8)
                        GenreCache.updateGenreItems(genreId, validItems)
                        genreMovies = validItems
                    },
                    onFailure = { error ->
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

    // Always show the genre section, but show different content based on state
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
                LibrarySkeleton()
            }
            genreMovies.isNotEmpty() -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(genreMovies) { movie ->
                        LibraryItemCard(
                            item = movie,
                            mediaRepository = mediaRepository,
                            onClick = { onItemClick(movie) }
                        )
                    }
                }
            }
            else -> {
                // Show a message when no items are found
                Text(
                    text = "No movies found in this genre",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun TVShowGenreSection(
    genre: com.jellycine.data.model.BaseItemDto,
    mediaRepository: com.jellycine.data.repository.MediaRepository,
    onItemClick: (com.jellycine.data.model.BaseItemDto) -> Unit = {},
    refreshTrigger: Int = 0
) {
    val genreId = genre.id ?: return // Skip if no genre ID
    var genreShows by remember(genreId) { mutableStateOf(GenreCache.getGenreItems("tv_$genreId")) }
    var isLoading by remember(genreId) { mutableStateOf(GenreCache.shouldRefreshGenreItems("tv_$genreId")) }

    // Load TV shows for this specific genre with caching
    LaunchedEffect(genreId, refreshTrigger) {
        val cacheKey = "tv_$genreId"
        if (GenreCache.shouldRefreshGenreItems(cacheKey)) {
            isLoading = true
            try {
                val result = mediaRepository.getItemsByGenre(
                    genreId = genreId,
                    includeItemTypes = "Series",
                    limit = 20
                )

                result.fold(
                    onSuccess = { items ->
                        val validItems = items.filter {
                            it.id != null && !it.name.isNullOrBlank()
                        }.take(8)
                        GenreCache.updateGenreItems(cacheKey, validItems)
                        genreShows = validItems
                    },
                    onFailure = { error ->
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

    // Always show the genre section, but show different content based on state
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
                LibrarySkeleton()
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
                // Show a message when no items are found
                Text(
                    text = "No TV shows found in this genre",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}