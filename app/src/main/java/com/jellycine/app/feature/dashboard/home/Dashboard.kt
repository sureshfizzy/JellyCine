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
    // Sample movies for demonstration
    val sampleMovies = remember {
        listOf(
            Movie("1", "Spider-Man: No Way Home", "2021"),
            Movie("2", "Joker", "2019"),
            Movie("3", "The Batman", "2022"),
            Movie("4", "Final Destination", "2000"),
            Movie("5", "Avengers: Endgame", "2019"),
            Movie("6", "Dune", "2021"),
            Movie("7", "Top Gun: Maverick", "2022"),
            Movie("8", "Black Widow", "2021")
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {


        item {
            // Feature Tab - Recently Added Content
            FeatureTab(
                onItemClick = onNavigateToDetail,
                onLogout = onLogout
            )
        }

        item {
            // Continue Watching Section
            ContinueWatchingSection(
                onItemClick = onNavigateToDetail
            )
        }

        item {
            // Library Sections
            LibrarySections(
                onItemClick = onNavigateToDetail
            )
        }
    }
}

@Composable
private fun ContinueWatchingSection(
    onItemClick: (com.jellycine.data.model.BaseItemDto) -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { com.jellycine.data.repository.MediaRepositoryProvider.getInstance(context) }

    // Use rememberSaveable to persist across recompositions and navigation
    var resumeItems by rememberSaveable { mutableStateOf<List<com.jellycine.data.model.BaseItemDto>>(emptyList()) }
    var isLoading by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var hasLoaded by rememberSaveable { mutableStateOf(false) }

    // Load resume items with retry logic - only if not already loaded
    LaunchedEffect(hasLoaded) {
        if (hasLoaded) return@LaunchedEffect

        var retryCount = 0
        val maxRetries = 3

        while (retryCount < maxRetries && resumeItems.isEmpty() && error == null) {
            try {
                if (retryCount > 0) {
                    delay(1000L * retryCount)
                }

                val result = mediaRepository.getResumeItems(limit = 10)
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
                        resumeItems = validItems
                        isLoading = false
                        hasLoaded = true
                        return@LaunchedEffect
                    },
                    onFailure = { throwable ->
                        if (retryCount == maxRetries - 1) {
                            error = throwable.message ?: "Failed to load continue watching items"
                            isLoading = false
                        }
                    }
                )
            } catch (e: Exception) {
                if (retryCount == maxRetries - 1) {
                    error = e.message ?: "Unknown error occurred"
                    isLoading = false
                }
            }
            retryCount++
        }

        if (resumeItems.isEmpty() && error == null) {
            isLoading = false
        }
    }

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

            resumeItems.isNotEmpty() -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(resumeItems.take(10)) { item ->
                        ContinueWatchingCard(
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
                        .height(200.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No items to continue watching",
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
                width = 300,
                height = 170,
                quality = 70
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
    onItemClick: (com.jellycine.data.model.BaseItemDto) -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { com.jellycine.data.repository.MediaRepositoryProvider.getInstance(context) }

    var libraryViews by rememberSaveable { mutableStateOf<List<com.jellycine.data.model.BaseItemDto>>(emptyList()) }
    var isLoading by rememberSaveable { mutableStateOf(true) }
    var hasLoaded by rememberSaveable { mutableStateOf(false) }

    // Load library views - only if not already loaded
    LaunchedEffect(hasLoaded) {
        if (hasLoaded) return@LaunchedEffect

        try {
            val result = mediaRepository.getUserViews()
            result.fold(
                onSuccess = { queryResult ->
                    libraryViews = queryResult.items?.filter {
                        it.id != null &&
                        !it.name.isNullOrBlank() &&
                        it.collectionType != "boxsets" &&
                        it.collectionType != "playlists" &&
                        (it.type == "CollectionFolder" || it.type == "Folder")
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
                        onItemClick = onItemClick
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
    onItemClick: (com.jellycine.data.model.BaseItemDto) -> Unit = {}
) {
    var libraryItems by rememberSaveable(library.id) { mutableStateOf<List<com.jellycine.data.model.BaseItemDto>>(emptyList()) }
    var isLoading by rememberSaveable(library.id) { mutableStateOf(true) }
    var hasLoaded by rememberSaveable(library.id) { mutableStateOf(false) }

    // Load items for this library - only if not already loaded
    LaunchedEffect(library.id, hasLoaded) {
        if (hasLoaded) return@LaunchedEffect

        library.id?.let { libraryId ->
            try {
                val result = mediaRepository.getUserItems(
                    parentId = libraryId,
                    recursive = true,
                    sortBy = "DateCreated",
                    sortOrder = "Descending",
                    limit = 8
                )
                result.fold(
                    onSuccess = { queryResult ->
                        libraryItems = queryResult.items?.filter {
                            it.id != null &&
                            !it.name.isNullOrBlank() &&
                            (it.type == "Movie" || it.type == "Series" || it.type == "Episode" || it.type == "Video") &&
                            it.type != "BoxSet" &&
                            it.type != "Collection" &&
                            it.type != "Playlist"
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

    Column {
        // Section Header
        Text(
            text = library.name ?: "Library",
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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

    // Get image URL - for episodes, use series poster
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
                width = 200,
                height = 300,
                quality = 70
            ).first()
        }
    }

    Card(
        modifier = Modifier
            .width(120.dp)
            .height(180.dp),
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
            // Poster image
            JellyfinPosterImage(
                imageUrl = imageUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                context = context,
                contentScale = ContentScale.Crop
            )

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
                        RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = item.name ?: "Unknown",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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

// Skeleton Components
@Composable
private fun ShimmerEffect(
    modifier: Modifier = Modifier,
    cornerRadius: Float = 12f
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha = transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Canvas(modifier = modifier) {
        drawRoundRect(
            color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = alpha.value),
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(6) {
            ShimmerEffect(
                modifier = Modifier
                    .width(160.dp)
                    .height(240.dp),
                cornerRadius = 16f
            )
        }
    }
}