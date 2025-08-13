package com.jellycine.app.ui.screens.detail

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.jellycine.app.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jellycine.app.util.image.JellyfinPosterImage
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.BaseItemPerson
import com.jellycine.data.model.MediaStream
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import android.content.Context
import android.content.res.Configuration
import com.jellycine.app.ui.screens.player.PlayerScreen
import com.jellycine.detail.CodecUtils
import com.jellycine.detail.CodecCapabilityManager
import com.jellycine.app.ui.components.common.rememberAudioCapabilities
import com.jellycine.app.ui.components.common.ActionButtonsSection
import com.jellycine.app.ui.components.common.CastSection
import com.jellycine.app.ui.components.common.CodecInfoSection
import com.jellycine.app.ui.components.common.ModernFileInfoRow
import com.jellycine.app.ui.components.common.OverviewSection
import com.jellycine.app.ui.components.common.TechnicalInfoSection
import java.util.Locale
import androidx.media3.common.util.UnstableApi

@Composable
fun LoadingAnimation(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Canvas(
        modifier = modifier.size(60.dp)
    ) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 4

        rotate(rotation, pivot = center) {
            for (i in 0 until 8) {
                val angle = (i * 45f) * (kotlin.math.PI / 180f)
                val x = centerX + cos(angle) * radius * scale
                val y = centerY + sin(angle) * radius * scale
                val circleRadius = (8f - i) * 2f * scale

                drawCircle(
                    color = color.copy(alpha = 0.7f - (i * 0.08f)),
                    radius = circleRadius,
                    center = Offset(x.toFloat(), y.toFloat())
                )
            }
        }
    }
}

@UnstableApi
@Composable
fun DetailScreenContainer(
    itemId: String,
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }

    var item by remember { mutableStateOf<BaseItemDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPlayer by remember { mutableStateOf(false) }
    var playbackItemId by remember { mutableStateOf<String?>(null) }

    // Navigation state
    var currentScreen by remember { mutableStateOf("detail") }
    var seasonDetailData by remember { mutableStateOf<Triple<String, String, String?>?>(null) }
    var episodeDetailId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(itemId) {
        try {
            isLoading = true
            error = null

            val result = mediaRepository.getItemById(itemId)
            result.fold(
                onSuccess = { fetchedItem ->
                    item = fetchedItem
                    isLoading = false
                },
                onFailure = { exception ->
                    error = exception.message
                    isLoading = false
                }
            )
        } catch (e: Exception) {
            error = e.message
            isLoading = false
        }
    }

    when {
        isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    LoadingAnimation()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp
                    )
                }
            }
        }
        error != null -> {
            LaunchedEffect(Unit) {
                onBackPressed()
            }
        }
        item != null -> {
            if (showPlayer) {
                PlayerScreen(
                    mediaId = playbackItemId ?: itemId,
                    onBackPressed = {
                        showPlayer = false
                        playbackItemId = null
                    }
                )
            } else {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        fadeIn(
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        ) togetherWith fadeOut(
                            animationSpec = tween(300, easing = LinearOutSlowInEasing)
                        )
                    },
                    label = "screen_navigation"
                ) { screen ->
                    when (screen) {
                        "detail" -> {
                            DetailScreen(
                                item = item!!,
                                onBackPressed = onBackPressed,
                                onPlayClick = {
                                    playbackItemId = itemId
                                    showPlayer = true
                                },
                                onSeasonClick = { seriesId, seasonId, seasonName ->
                                    seasonDetailData = Triple(seriesId, seasonId, seasonName)
                                    currentScreen = "season"
                                }
                            )
                        }
                        "season" -> {
                            seasonDetailData?.let { (seriesId, seasonId, seasonName) ->
                                SeasonDetailScreen(
                                    seriesId = seriesId,
                                    seasonId = seasonId,
                                    seasonName = seasonName,
                                    onBackPressed = {
                                        currentScreen = "detail"
                                        seasonDetailData = null
                                    },
                                    onEpisodeClick = { episodeId ->
                                        episodeDetailId = episodeId
                                        currentScreen = "episode"
                                    }
                                )
                            }
                        }
                        "episode" -> {
                            episodeDetailId?.let { episodeId ->
                                EpisodeDetailScreen(
                                    episodeId = episodeId,
                                    onBackPressed = {
                                        currentScreen = "season"
                                        episodeDetailId = null
                                    },
                                    onPlayClick = {
                                        playbackItemId = episodeId
                                        showPlayer = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        else -> {
            LaunchedEffect(Unit) {
                onBackPressed()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    item: BaseItemDto,
    onBackPressed: () -> Unit = {},
    onPlayClick: () -> Unit = {},
    onSeasonClick: (String, String, String?) -> Unit = { _, _, _ -> }
) {
    DetailContent(
        item = item,
        onBackPressed = onBackPressed,
        onPlayClick = onPlayClick,
        onSeasonClick = onSeasonClick
    )
}

@Composable
fun DetailContent(
    item: BaseItemDto,
    onBackPressed: () -> Unit = {},
    onPlayClick: () -> Unit = {},
    onSeasonClick: (String, String, String?) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    var backdropImageUrl by remember { mutableStateOf<String?>(null) }
    var posterImageUrl by remember { mutableStateOf<String?>(null) }
    var isFavorite by remember { mutableStateOf(item.userData?.isFavorite == true) }
    var showFullOverview by remember { mutableStateOf(false) }

    // Get device audio capabilities
    val deviceCapabilities = rememberAudioCapabilities(context)

    LaunchedEffect(item.id) {
        val itemId = item.id
        if (itemId != null) {
            backdropImageUrl = mediaRepository.getBackdropImageUrl(
                itemId = itemId,
                width = 1200,
                height = 675,
                quality = 95
            ).first()

            posterImageUrl = mediaRepository.getImageUrl(
                itemId = itemId,
                imageType = "Primary",
                width = 400,
                height = 600,
                quality = 95
            ).first()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {

                JellyfinPosterImage(
                    imageUrl = backdropImageUrl,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    context = context,
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Black.copy(alpha = 0.8f),
                                    Color.Black
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackPressed,
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                CircleShape
                            )
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = { isFavorite = !isFavorite },
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                CircleShape
                            )
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (isFavorite) Color.Red else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .offset(y = (-85).dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {

                    Card(
                        modifier = Modifier
                            .width(120.dp)
                            .height(180.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        JellyfinPosterImage(
                            imageUrl = posterImageUrl,
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            context = context,
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = item.name ?: "Unknown Title",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 32.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            item.productionYear?.let { year ->
                                Text(
                                    text = year.toString(),
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }

                            item.runTimeTicks?.let { ticks ->
                                Text(
                                    text = CodecUtils.formatRuntime(ticks),
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }

                        // Rating on separate line to prevent layout breaking
                        item.officialRating?.let { rating ->
                            Surface(
                                color = Color(0xFF1A1A1A),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(
                                    text = rating,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    maxLines = 1
                                )
                            }
                        }

                        item.communityRating?.let { rating ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = "Rating",
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f", rating),
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                item.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        items(genres) { genre ->
                            Surface(
                                color = Color(0xFF2A2A2A),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            ) {
                                Text(
                                    text = genre,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Codec Information Section
                item.mediaStreams?.let { streams ->
                    Text(
                        text = "Codecs Info",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                    )
                    CodecInfoSection(mediaStreams = streams)
                }

                // Action Buttons
                if (item.type != "Series") {
                    ActionButtonsSection(
                        onPlayClick = onPlayClick,
                        onDownloadClick = { /* TODO: Download */ }
                    )
                }

                // Overview Section (after action buttons)
                item.overview?.let { overview ->
                    OverviewSection(overview = overview)
                }

                // Technical Information Section
                if (item.type != "Series") {
                    TechnicalInfoSection(item = item)
                }

                // Seasons Section for TV Series
                if (item.type == "Series") {
                    item.id?.let { seriesId ->
                        SeasonsSection(
                            seriesId = seriesId,
                            mediaRepository = mediaRepository,
                            onSeasonClick = onSeasonClick
                        )
                    }
                }

                // Cast Section
                CastSection(
                    item = item,
                    mediaRepository = mediaRepository
                )
            }
        }
    }
}

@Composable
fun CastMemberCard(
    person: BaseItemPerson,
    mediaRepository: MediaRepository
) {
    var personImageUrl by remember(person.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(person.id) {
        if (person.id != null) {
            personImageUrl = getPersonImageUrl(person.id, mediaRepository).first()
        }
    }

    Card(
        modifier = Modifier.width(120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp)
        ) {
            Card(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                if (personImageUrl != null) {
                    AsyncImage(
                        model = personImageUrl,
                        contentDescription = person.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF2A2A2A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = person.name,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = person.name ?: "Unknown",
                fontSize = 13.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )

            person.role?.let { role ->
                Text(
                    text = role,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun FileInfoRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = value,
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun SeasonsSection(
    seriesId: String,
    mediaRepository: MediaRepository,
    onSeasonClick: (String, String, String?) -> Unit = { _, _, _ -> }
) {
    var seasons by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isLoadingSeasons by remember { mutableStateOf(true) }

    // Load seasons
    LaunchedEffect(seriesId) {
        isLoadingSeasons = true
        try {
            val result = mediaRepository.getSeasons(seriesId)
            result.fold(
                onSuccess = { seasonList ->
                    seasons = seasonList.sortedBy { it.indexNumber ?: 0 }
                    isLoadingSeasons = false
                },
                onFailure = {
                    isLoadingSeasons = false
                }
            )
        } catch (e: Exception) {
            isLoadingSeasons = false
        }
    }

    Column(
        modifier = Modifier.padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Seasons Section
        Text(
            text = "Seasons",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        when {
            isLoadingSeasons -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    items(3) {
                        SeasonCardSkeleton()
                    }
                }
            }
            seasons.isNotEmpty() -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    items(seasons) { season ->
                        SeasonCard(
                            season = season,
                            mediaRepository = mediaRepository,
                            onClick = {
                                season.id?.let { seasonId ->
                                    onSeasonClick(seriesId, seasonId, season.name)
                                }
                            },
                            onPreviewClick = {
                                // TODO: Implement season preview functionality
                                season.id?.let { seasonId ->
                                    onSeasonClick(seriesId, seasonId, season.name)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DetailScreenPreview() {
    MaterialTheme {
        val mockItem = BaseItemDto(
            id = "mock-id",
            name = "Seal Team",
            overview = "After his best friend is killed in a shark attack, Quinn, a lovable yet tenacious seal assembles a SEAL TEAM to fight back against a gang of sharks overtaking the neighborhood.",
            productionYear = 2021,
            runTimeTicks = 6000000000L, // 1h 40m
            communityRating = 7.6f,
            officialRating = "TV-Y7",
            genres = listOf("Animation", "Family", "Adventure"),
            userData = null,
            people = null,
            studios = null,
            mediaStreams = listOf(
                MediaStream(
                    type = "Video",
                    codec = "h264",
                    width = 1920,
                    height = 1080
                ),
                MediaStream(
                    type = "Audio",
                    codec = "aac",
                    channels = 2,
                    language = "eng"
                )
            )
        )

        DetailContent(
            item = mockItem,
            onBackPressed = {},
            onPlayClick = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DetailScreenLongRatingPreview() {
    MaterialTheme {
        val mockItem = BaseItemDto(
            id = "mock-id",
            name = "The Dark Knight",
            overview = "When the menace known as the Joker wreaks havoc and chaos on the people of Gotham, Batman must accept one of the greatest psychological and physical tests of his ability to fight injustice.",
            productionYear = 2008,
            runTimeTicks = 9120000000L, // 2h 32m
            communityRating = 9.0f,
            officialRating = "Not Rated", // Long rating text
            genres = listOf("Action", "Crime", "Drama", "Thriller"),
            userData = null,
            people = null,
            studios = null,
            mediaStreams = listOf(
                MediaStream(
                    type = "Video",
                    codec = "h264",
                    width = 3840,
                    height = 2160,
                    videoRange = "HDR"
                ),
                MediaStream(
                    type = "Audio",
                    codec = "eac3",
                    channels = 6,
                    language = "eng",
                    title = "Dolby Digital+ 5.1"
                ),
                MediaStream(
                    type = "Subtitle",
                    codec = "subrip",
                    language = "eng"
                )
            )
        )

        DetailContent(
            item = mockItem,
            onBackPressed = {},
            onPlayClick = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DetailScreenNoGenresPreview() {
    MaterialTheme {
        val mockItem = BaseItemDto(
            id = "mock-id",
            name = "Mystery Movie",
            overview = "A mysterious film with no genre information available.",
            productionYear = 2023,
            runTimeTicks = 5400000000L, // 1h 30m
            communityRating = 6.5f,
            officialRating = "PG-13",
            genres = null, // No genres
            userData = null,
            people = null,
            studios = null
        )

        DetailContent(
            item = mockItem,
            onBackPressed = {},
            onPlayClick = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DetailScreenManyGenresPreview() {
    MaterialTheme {
        val mockItem = BaseItemDto(
            id = "mock-id",
            name = "Genre Overload",
            overview = "A movie that somehow fits into every possible genre category.",
            productionYear = 2024,
            runTimeTicks = 7200000000L, // 2h
            communityRating = 8.2f,
            officialRating = "R",
            genres = listOf("Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror", "Mystery", "Romance", "Sci-Fi", "Thriller"),
            userData = null,
            people = null,
            studios = null,
            mediaStreams = listOf(
                MediaStream(
                    type = "Video",
                    codec = "hevc",
                    width = 1920,
                    height = 1080
                ),
                MediaStream(
                    type = "Audio",
                    codec = "truehd",
                    channels = 8,
                    language = "eng",
                    title = "Dolby TrueHD 7.1 Atmos"
                ),
                MediaStream(
                    type = "Audio",
                    codec = "ac3",
                    channels = 6,
                    language = "spa"
                ),
                MediaStream(
                    type = "Subtitle",
                    codec = "ass",
                    language = "eng"
                ),
                MediaStream(
                    type = "Subtitle",
                    codec = "subrip",
                    language = "spa"
                )
            )
        )

        DetailContent(
            item = mockItem,
            onBackPressed = {},
            onPlayClick = {}
        )
    }
}

private fun getPersonImageUrl(personId: String?, mediaRepository: MediaRepository): Flow<String?> {
    return if (personId != null) {
        mediaRepository.getImageUrl(
            itemId = personId,
            imageType = "Primary",
            width = 120,
            height = 120,
            quality = 90
        )
    } else {
        flowOf(null)
    }
}