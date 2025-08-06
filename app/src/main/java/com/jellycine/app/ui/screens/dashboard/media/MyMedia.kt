package com.jellycine.app.ui.screens.dashboard.media

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.first
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun MyMedia(
    onLibraryClick: (com.jellycine.app.ui.screens.dashboard.media.ContentType, String?, String) -> Unit = { _, _, _ -> }
) {
    var libraryViews by remember { mutableStateOf<List<com.jellycine.data.model.BaseItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val mediaRepository = remember { com.jellycine.data.repository.MediaRepositoryProvider.getInstance(context) }
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    // Loading function
    suspend fun loadLibraries(showRefreshIndicator: Boolean = false) {
        if (showRefreshIndicator) refreshing = true

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
                                (it.type == "CollectionFolder" || it.type == "Folder")
                            }?.sortedBy { it.sortName ?: it.name } ?: emptyList()

                            libraryViews = validViews
                            isLoading = false
                            error = null
                        },
                        onFailure = { throwable ->
                            error = throwable.message ?: "Failed to load libraries"
                            isLoading = false
                        }
                    )
                }
            }
        } catch (e: Exception) {
            error = e.message ?: "Unknown error occurred"
            isLoading = false
        } finally {
            refreshing = false
        }
    }

    // Load library views
    LaunchedEffect(Unit) {
        loadLibraries()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Text(
                        text = "My Media",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    if (libraryViews.isNotEmpty()) {
                        Text(
                            text = "${libraryViews.size} ${if (libraryViews.size == 1) "library" else "libraries"} available",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    isLoading -> {
                        // Loading state with grid layout
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(8) { index ->
                                LibraryCardSkeleton(
                                    animationDelay = index * 100
                                )
                            }
                        }
                    }

                    error != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = Color.Red.copy(alpha = 0.7f),
                                    modifier = Modifier.size(48.dp)
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Text(
                                    text = "Unable to load libraries",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                
                                Text(
                                    text = error ?: "Something went wrong",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                
                                Button(
                                    onClick = { 
                                        coroutineScope.launch {
                                            isLoading = true
                                            error = null
                                            loadLibraries()
                                        }
                                    },
                                    modifier = Modifier.padding(top = 24.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0080FF)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        "Try Again",
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    libraryViews.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(64.dp)
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Text(
                                    text = "No libraries found",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                Text(
                                    text = "Set up your media libraries in Jellyfin",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    else -> {
                        // Grid layout with visual cards
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            state = gridState,
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = libraryViews,
                                key = { library -> library.id ?: "${library.name}_${library.collectionType}" }
                            ) { library ->
                                VisualLibraryCard(
                                    library = library,
                                    onClick = {
                                        val contentType = when (library.collectionType) {
                                            "movies" -> com.jellycine.app.ui.screens.dashboard.media.ContentType.MOVIES
                                            "tvshows" -> com.jellycine.app.ui.screens.dashboard.media.ContentType.SERIES
                                            else -> com.jellycine.app.ui.screens.dashboard.media.ContentType.ALL
                                        }
                                        onLibraryClick(contentType, library.id, library.name ?: "Library")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisualLibraryCard(
    library: com.jellycine.data.model.BaseItemDto,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val mediaRepository = remember { com.jellycine.data.repository.MediaRepositoryProvider.getInstance(context) }
    val libraryColor = getLibraryColor(library.collectionType)

    val itemCount = when {
        library.childCount != null && library.childCount!! > 0 -> library.childCount!!
        library.recursiveItemCount != null && library.recursiveItemCount!! > 0 -> library.recursiveItemCount!!
        else -> 0
    }
    
    // Load library image
    var imageUrl by remember(library.id) { mutableStateOf<String?>(null) }
    var isImageLoading by remember(library.id) { mutableStateOf(true) }
    
    LaunchedEffect(library.id) {
        val itemId = library.id
        if (itemId != null) {
            try {
                val url = mediaRepository.getImageUrl(
                    itemId = itemId,
                    width = 400,
                    height = 300,
                    quality = 90
                ).first()
                imageUrl = url
                isImageLoading = false
            } catch (e: Exception) {
                imageUrl = null
                isImageLoading = false
            }
        }
    }

    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(50)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it / 4 },
            animationSpec = tween(500, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(500))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp
            ),
            onClick = onClick
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                if (imageUrl != null && !isImageLoading) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = library.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        libraryColor.copy(alpha = 0.8f),
                                        libraryColor.copy(alpha = 0.95f)
                                    ),
                                    startY = 0f,
                                    endY = Float.POSITIVE_INFINITY
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryCardSkeleton(
    animationDelay: Int = 0
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = animationDelay),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.2f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = shimmerAlpha * 0.1f),
                                Color.White.copy(alpha = shimmerAlpha * 0.15f)
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                Color.White.copy(alpha = shimmerAlpha * 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                    )
                }

                // Bottom text skeleton
                Column {
                    // Title skeleton
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(18.dp)
                            .background(
                                Color.White.copy(alpha = shimmerAlpha * 0.25f),
                                RoundedCornerShape(6.dp)
                            )
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // Subtitle skeleton
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(13.dp)
                            .background(
                                Color.White.copy(alpha = shimmerAlpha * 0.15f),
                                RoundedCornerShape(4.dp)
                            )
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(20.dp)
                            .background(
                                Color.White.copy(alpha = shimmerAlpha * 0.12f),
                                RoundedCornerShape(12.dp)
                            )
                    )
                }
            }
        }
    }
}

private fun getLibraryIcon(collectionType: String?): ImageVector {
    return when (collectionType) {
        "movies" -> Icons.Filled.Movie
        "tvshows" -> Icons.Filled.Tv
        else -> Icons.Filled.VideoLibrary
    }
}

private fun getLibraryTypeText(collectionType: String?): String {
    return when (collectionType) {
        "movies" -> "Movies"
        "tvshows" -> "TV Shows"
        else -> "Library"
    }
}

private fun getLibraryColor(collectionType: String?): Color {
    return when (collectionType) {
        "movies" -> Color(0xFF0080FF)
        "tvshows" -> Color(0xFF00C851)
        else -> Color(0xFF0080FF)
    }
}