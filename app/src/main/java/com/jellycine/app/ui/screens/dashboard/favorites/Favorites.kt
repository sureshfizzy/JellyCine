package com.jellycine.app.ui.screens.dashboard.favorites

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun Favorites(
    onItemClick: (BaseItemDto) -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }

    var favorites by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        isLoading = true
        error = null
        val result = withContext(Dispatchers.IO) { mediaRepository.getFavoriteItems() }
        result.fold(
            onSuccess = { queryResult ->
                favorites = queryResult.items.orEmpty()
                isLoading = false
            },
            onFailure = { throwable ->
                favorites = emptyList()
                error = throwable.message ?: "Failed to load favorites"
                isLoading = false
            }
        )
    }

    val movies = remember(favorites) { favorites.filter(::isMovieFavorite) }
    val shows = remember(favorites) { favorites.filter(::isShowFavorite) }
    val episodes = remember(favorites) { favorites.filter(::isEpisodeFavorite) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                    Text(
                        text = "Favorites",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            when {
                isLoading -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        items(3) { sectionIndex ->
                            TierSkeleton(sectionIndex = sectionIndex)
                        }
                    }
                }

                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Unable to load favorites",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = error ?: "Unknown error",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Button(
                                onClick = { refreshKey++ },
                                modifier = Modifier.padding(top = 20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0080FF))
                            ) {
                                Text(text = "Try Again", color = Color.White)
                            }
                        }
                    }
                }

                favorites.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(60.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "No favorites yet",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Add movies, shows, or episodes from detail pages.",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }

                movies.isEmpty() && shows.isEmpty() && episodes.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No movie, show, or episode favorites found",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Favorites exist, but they are different media types.",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        if (movies.isNotEmpty()) {
                            item {
                                FavoriteTierSection(
                                    title = "Favorite Movies",
                                    items = movies,
                                    mediaRepository = mediaRepository,
                                    onItemClick = onItemClick
                                )
                            }
                        }

                        if (shows.isNotEmpty()) {
                            item {
                                FavoriteTierSection(
                                    title = "Favorite Shows",
                                    items = shows,
                                    mediaRepository = mediaRepository,
                                    onItemClick = onItemClick
                                )
                            }
                        }

                        if (episodes.isNotEmpty()) {
                            item {
                                FavoriteTierSection(
                                    title = "Favorite Episodes",
                                    items = episodes,
                                    mediaRepository = mediaRepository,
                                    onItemClick = onItemClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteTierSection(
    title: String,
    items: List<BaseItemDto>,
    mediaRepository: MediaRepository,
    onItemClick: (BaseItemDto) -> Unit
) {
    Column {
        RowTitle(title = title)

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = items,
                key = { item -> item.id ?: "${item.name}_${item.type}_${item.indexNumber ?: 0}" }
            ) { item ->
                FavoriteItemCard(
                    item = item,
                    mediaRepository = mediaRepository,
                    modifier = Modifier.width(148.dp),
                    onClick = {
                        if (item.id != null) onItemClick(item)
                    }
                )
            }
        }
    }
}

@Composable
private fun RowTitle(
    title: String
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FavoriteItemCard(
    item: BaseItemDto,
    mediaRepository: MediaRepository,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var imageUrl by remember(item.id, item.type, item.seriesId) { mutableStateOf<String?>(null) }

    val targetImageId = remember(item) {
        when (item.type) {
            "Episode" -> item.seriesId ?: item.parentPrimaryImageItemId ?: item.id
            else -> item.id
        }
    }

    LaunchedEffect(targetImageId) {
        val id = targetImageId ?: return@LaunchedEffect
        imageUrl = runCatching {
            withContext(Dispatchers.IO) {
                mediaRepository.getImageUrl(
                    itemId = id,
                    width = 320,
                    height = 480,
                    quality = 90
                ).first()
            }
        }.getOrNull()
    }

    val badgeText = when (item.type) {
        "Movie" -> "MOVIE"
        "Series" -> "SHOW"
        "Episode" -> "EPISODE"
        else -> item.type?.uppercase().orEmpty()
    }

    val title = when (item.type) {
        "Series" -> item.name?.takeIf { it.isNotBlank() } ?: "Show"
        "Episode" -> {
            val episodeCode = episodeCode(item)
            val episodeName = item.name?.takeIf { it.isNotBlank() }
            episodeName ?: episodeCode
        }
        else -> item.name ?: "Unknown"
    }

    val subtitle = when (item.type) {
        "Movie" -> buildString {
            item.productionYear?.let { append(it) }
            formatRuntime(item.runTimeTicks)?.let { runtime ->
                if (isNotEmpty()) append(" - ")
                append(runtime)
            }
        }.ifBlank { "Movie" }
        "Series" -> buildString {
            item.productionYear?.let { append(it) }
            val episodeCount = item.episodeCount ?: item.recursiveItemCount ?: item.childCount
            if (episodeCount != null && episodeCount > 0) {
                if (isNotEmpty()) append(" - ")
                append("$episodeCount eps")
            }
        }.ifBlank { "Show" }
        "Episode" -> {
            val show = item.seriesName?.takeIf { it.isNotBlank() } ?: "Series"
            "$show - ${episodeCode(item)}"
        }
        else -> item.name ?: ""
    }

    Column(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            onClick = onClick
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFF1D2735), Color(0xFF131313))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (item.type) {
                                "Movie" -> Icons.Default.Movie
                                else -> Icons.Default.Tv
                            },
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.84f))
                            )
                        )
                        .height(52.dp)
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp)
        )
        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.68f),
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp)
        )
    }
}

@Composable
private fun TierSkeleton(sectionIndex: Int) {
    val transition = rememberInfiniteTransition(label = "favorite_tier_skeleton")
    val shimmer by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, delayMillis = sectionIndex * 80),
            repeatMode = RepeatMode.Reverse
        ),
        label = "favorite_tier_skeleton_alpha"
    )

    Column {
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth(0.55f)
                .height(22.dp)
                .background(
                    color = Color.White.copy(alpha = shimmer * 0.26f),
                    shape = RoundedCornerShape(7.dp)
                )
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(4) {
                Column(modifier = Modifier.width(148.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.68f)
                            .background(
                                color = Color.White.copy(alpha = shimmer * 0.22f),
                                shape = RoundedCornerShape(16.dp)
                            )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(12.dp)
                            .background(
                                color = Color.White.copy(alpha = shimmer * 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(10.dp)
                            .background(
                                color = Color.White.copy(alpha = shimmer * 0.14f),
                                shape = RoundedCornerShape(5.dp)
                            )
                    )
                }
            }
        }
    }
}

private fun episodeCode(item: BaseItemDto): String {
    val season = item.parentIndexNumber
    val episode = item.indexNumber
    return if (season != null && episode != null) {
        "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
    } else {
        "Episode"
    }
}

private fun isMovieFavorite(item: BaseItemDto): Boolean {
    return item.type == "Movie"
}

private fun isEpisodeFavorite(item: BaseItemDto): Boolean {
    return item.type == "Episode"
}

private fun isShowFavorite(item: BaseItemDto): Boolean {
    return item.type == "Series"
}

private fun formatRuntime(ticks: Long?): String? {
    val totalMinutes = ticks?.let { (it / 600000000.0).roundToInt() } ?: return null
    if (totalMinutes <= 0) return null
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Preview(showBackground = true)
@Composable
fun FavoritesPreview() {
    Favorites()
}
