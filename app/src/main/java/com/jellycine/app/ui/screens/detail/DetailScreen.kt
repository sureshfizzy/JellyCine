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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
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
import com.jellycine.app.ui.components.common.ScreenWrapper
import com.jellycine.app.ui.components.common.AnimatedCard
import com.jellycine.app.ui.components.common.ShimmerEffect
import com.jellycine.app.download.DownloadRepositoryProvider
import com.jellycine.app.download.DownloadStatus
import com.jellycine.app.download.ItemDownloadState
import java.util.Locale
import androidx.media3.common.util.UnstableApi
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch


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
    var episodeItem by remember { mutableStateOf<BaseItemDto?>(null) }
    var isEpisodeLoading by remember { mutableStateOf(false) }
    var episodeError by remember { mutableStateOf<String?>(null) }

    val handleBackNavigation: () -> Unit = {
        when {
            showPlayer -> {
                showPlayer = false
                playbackItemId = null
            }
            currentScreen == "episode" && seasonDetailData != null -> {
                currentScreen = "season"
            }
            currentScreen == "season" -> {
                currentScreen = "detail"
            }
            else -> onBackPressed()
        }
    }

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

    LaunchedEffect(currentScreen, episodeDetailId) {
        val targetEpisodeId = episodeDetailId
        if (currentScreen != "episode" || targetEpisodeId.isNullOrBlank()) {
            return@LaunchedEffect
        }

        try {
            isEpisodeLoading = true
            episodeError = null
            episodeItem = null

            val result = mediaRepository.getItemById(targetEpisodeId)
            result.fold(
                onSuccess = { fetchedEpisode ->
                    episodeItem = fetchedEpisode
                    isEpisodeLoading = false
                },
                onFailure = { exception ->
                    episodeError = exception.message
                    episodeItem = null
                    isEpisodeLoading = false
                }
            )
        } catch (e: Exception) {
            episodeError = e.message
            episodeItem = null
            isEpisodeLoading = false
        }
    }

    BackHandler {
        handleBackNavigation()
    }

    if (error != null) {
        LaunchedEffect(Unit) {
            onBackPressed()
        }
    } else {
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
                        ScreenWrapper(isActive = true) {
                            val currentItem = item
                            if (currentItem != null) {
                                DetailScreen(
                                    item = currentItem,
                                    isLoading = isLoading,
                                    onBackPressed = handleBackNavigation,
                                    onPlayClick = {
                                        playbackItemId = itemId
                                        showPlayer = true
                                    },
                                    onSeasonClick = { seriesId, seasonId, seasonName ->
                                        seasonDetailData = Triple(seriesId, seasonId, seasonName)
                                        currentScreen = "season"
                                    }
                                )
                            } else {
                                DetailScreenSkeleton(onBackPressed = handleBackNavigation)
                            }
                        }
                    }
                    "season" -> {
                        seasonDetailData?.let { (seriesId, seasonId, seasonName) ->
                            ScreenWrapper(isActive = true) {
                                SeasonDetailScreen(
                                    seriesId = seriesId,
                                    seasonId = seasonId,
                                    seasonName = seasonName,
                                    onBackPressed = handleBackNavigation,
                                    onEpisodeClick = { episodeId ->
                                        episodeDetailId = episodeId
                                        currentScreen = "episode"
                                    }
                                )
                            }
                        }
                    }
                    "episode" -> {
                        episodeDetailId?.let { episodeId ->
                            ScreenWrapper(isActive = true) {
                                when {
                                    episodeError != null -> {
                                        LaunchedEffect(episodeError) {
                                            if (seasonDetailData != null) {
                                                currentScreen = "season"
                                                episodeError = null
                                            } else {
                                                onBackPressed()
                                            }
                                        }
                                    }
                                    episodeItem != null -> {
                                        DetailScreen(
                                            item = episodeItem!!,
                                            isLoading = isEpisodeLoading,
                                            onBackPressed = handleBackNavigation,
                                            onPlayClick = {
                                                playbackItemId = episodeItem?.id ?: episodeId
                                                showPlayer = true
                                            },
                                            onSeasonClick = { seriesId, seasonId, seasonName ->
                                                seasonDetailData = Triple(seriesId, seasonId, seasonName)
                                                currentScreen = "season"
                                            }
                                        )
                                    }
                                    else -> {
                                        DetailScreenSkeleton(
                                            onBackPressed = handleBackNavigation
                                        )
                                    }
                                }
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
fun DetailScreen(
    item: BaseItemDto,
    isLoading: Boolean = false,
    onBackPressed: () -> Unit = {},
    onPlayClick: () -> Unit = {},
    onSeasonClick: (String, String, String?) -> Unit = { _, _, _ -> }
) {
    DetailContent(
        item = item,
        isLoading = isLoading,
        onBackPressed = onBackPressed,
        onPlayClick = onPlayClick,
        onSeasonClick = onSeasonClick
    )
}

@Composable
fun DetailContent(
    item: BaseItemDto,
    isLoading: Boolean = false,
    onBackPressed: () -> Unit = {},
    onPlayClick: () -> Unit = {},
    onSeasonClick: (String, String, String?) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val downloadRepository = remember { DownloadRepositoryProvider.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val isEpisode = item.type == "Episode"
    val episodeHeaderText = remember(
        item.type,
        item.parentIndexNumber,
        item.indexNumber,
        item.name
    ) {
        episodeHeaderText(item)
    }
    var heroImageCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var heroImageIndex by remember(item.id) { mutableStateOf(0) }
    val backdropImageUrl = heroImageCandidates.getOrNull(heroImageIndex)
    var logoImageUrl by remember(item.id) { mutableStateOf<String?>(null) }
    var logoLookup by remember(item.id) { mutableStateOf(true) }
    var logoLoadError by remember(item.id) { mutableStateOf(false) }
    var selectedVideo by remember { mutableStateOf("") }
    var selectedAudio by remember { mutableStateOf("") }
    var selectedSubtitle by remember { mutableStateOf("Off") }
    val effectiveMediaStreams = remember(item.mediaStreams, item.mediaSources) {
        val fromSources = item.mediaSources.orEmpty().flatMap { it.mediaStreams.orEmpty() }
        if (fromSources.isNotEmpty()) fromSources else item.mediaStreams.orEmpty()
    }
    val runtimeTicks = item.runTimeTicks
    val playbackPositionTicks = item.userData?.playbackPositionTicks ?: 0L
    val isPartiallyWatched = runtimeTicks != null && playbackPositionTicks > 0L && playbackPositionTicks < runtimeTicks
    val playButtonText = if (isPartiallyWatched) {
        val remainingTicks = (runtimeTicks - playbackPositionTicks).coerceAtLeast(0L)
        "Resume ${CodecUtils.formatRuntime(remainingTicks)} left"
    } else {
        "Play"
    }
    val resumeProgress = if (runtimeTicks != null && runtimeTicks > 0) {
        (playbackPositionTicks.toFloat() / runtimeTicks.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val logoFallbackTitle = if (isEpisode) {
        item.seriesName?.takeIf { it.isNotBlank() }
            ?: item.name?.takeIf { it.isNotBlank() }
            ?: "Unknown"
    } else {
        item.name?.takeIf { it.isNotBlank() } ?: "Unknown"
    }
    val reserveLogoSpace = (!logoImageUrl.isNullOrBlank() && !logoLoadError) || logoLookup
    val showTitleFallback = !logoLookup && (logoImageUrl.isNullOrBlank() || logoLoadError)
    val canDownloadItem = item.id != null && item.canDownload != false
    val itemDownloadStateFlow = remember(item.id) { item.id?.let { downloadRepository.observeItemDownload(it) } }
    val itemDownloadState by (itemDownloadStateFlow?.collectAsState()
        ?: remember(item.id) { mutableStateOf(ItemDownloadState()) })
    var seriesQueueInProgress by remember(item.id) { mutableStateOf(false) }
    val animatedDownloadProgress by animateFloatAsState(
        targetValue = when (itemDownloadState.status) {
            DownloadStatus.QUEUED -> 0.05f
            DownloadStatus.DOWNLOADING -> itemDownloadState.progress.coerceIn(0.05f, 0.99f)
            DownloadStatus.COMPLETED -> 1f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 350),
        label = "detail_download_progress"
    )

    LaunchedEffect(item.id) {
        if (item.id == null) {
            logoLookup = false
            return@LaunchedEffect
        }
        heroImageCandidates = heroImageCandidates(
            item = item,
            mediaRepository = mediaRepository
        )
        heroImageIndex = 0

        logoLookup = true
        logoLoadError = false
        try {
            logoImageUrl = logoImage(
                item = item,
                mediaRepository = mediaRepository
            )
        } finally {
            logoLookup = false
        }
    }

    val videoOptions = remember(effectiveMediaStreams) { buildVideoOptions(effectiveMediaStreams) }
    val audioOptions = remember(effectiveMediaStreams) { buildAudioOptions(effectiveMediaStreams) }
    val subtitleOptions = remember(effectiveMediaStreams) { buildSubtitleOptions(effectiveMediaStreams) }
    val defaultSubtitleOption = remember(effectiveMediaStreams) { buildDefaultSubtitleOption(effectiveMediaStreams) }

    LaunchedEffect(videoOptions, audioOptions, subtitleOptions, defaultSubtitleOption) {
        if (selectedVideo !in videoOptions) {
            selectedVideo = videoOptions.firstOrNull().orEmpty()
        }
        if (selectedAudio !in audioOptions) {
            selectedAudio = audioOptions.firstOrNull().orEmpty()
        }
        if (selectedSubtitle !in subtitleOptions || selectedSubtitle == "Off") {
            selectedSubtitle = defaultSubtitleOption
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
                    .height(330.dp)
                    .clipToBounds()
            ) {

                JellyfinPosterImage(
                    imageUrl = backdropImageUrl,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    context = context,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    onErrorStateChange = { hasError ->
                        if (
                            hasError &&
                            backdropImageUrl == heroImageCandidates.getOrNull(heroImageIndex) &&
                            heroImageIndex < heroImageCandidates.lastIndex
                        ) {
                            heroImageIndex += 1
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.82f to Color.Transparent,
                                    0.90f to Color.Black.copy(alpha = 0.25f),
                                    0.96f to Color.Black.copy(alpha = 0.52f),
                                    1.0f to Color.Black.copy(alpha = 0.72f)
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.78f to Color.Transparent,
                                    0.92f to Color.Black.copy(alpha = 0.86f),
                                    1.0f to Color.Black
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
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
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .offset(y = (-42).dp)
            ) {
                if (reserveLogoSpace) {
                    Box(
                        modifier = Modifier
                            .height(78.dp)
                            .fillMaxWidth()
                    ) {
                        if (!logoImageUrl.isNullOrBlank()) {
                            JellyfinPosterImage(
                                imageUrl = if (isLoading) null else logoImageUrl,
                                contentDescription = item.name,
                                modifier = Modifier
                                    .fillMaxWidth(0.94f)
                                    .height(78.dp)
                                    .align(Alignment.CenterStart),
                                context = context,
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.CenterStart,
                                onErrorStateChange = { hasError ->
                                    logoLoadError = hasError
                                }
                            )
                        }
                    }
                } else if (showTitleFallback) {
                    Text(
                        text = logoFallbackTitle,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 30.sp
                    )
                }

                if (isEpisode) {
                    episodeHeaderText?.let { header ->
                        Text(
                            text = header,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.88f),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item.communityRating?.let { rating ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = "Rating",
                                tint = Color(0xFFFF4D4F),
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f", rating),
                                fontSize = 14.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    item.productionYear?.let { year ->
                        Text(
                            text = year.toString(),
                            fontSize = 14.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }

                    item.runTimeTicks?.let { ticks ->
                        Text(
                            text = CodecUtils.formatRuntime(ticks),
                            fontSize = 14.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }

                    item.officialRating?.let { rating ->
                        Surface(
                            color = Color(0xFF1A1A1A),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = rating,
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                maxLines = 1
                            )
                        }
                    }
                }

                item.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                    Text(
                        text = genres.joinToString(", "),
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (videoOptions.isNotEmpty()) {
                    if (videoOptions.size > 1) {
                        OptionSelectorRow(
                            label = "Video",
                            selectedOption = selectedVideo,
                            options = videoOptions,
                            onOptionSelected = { selectedVideo = it }
                        )
                    } else {
                        DetailInfoRow(
                            label = "Video",
                            value = videoOptions.first()
                        )
                    }
                }

                if (videoOptions.isNotEmpty() && audioOptions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (audioOptions.isNotEmpty()) {
                    if (audioOptions.size > 1) {
                        OptionSelectorRow(
                            label = "Audio",
                            selectedOption = selectedAudio,
                            options = audioOptions,
                            onOptionSelected = { selectedAudio = it }
                        )
                    } else {
                        DetailInfoRow(
                            label = "Audio",
                            value = audioOptions.first()
                        )
                    }
                }

                if (subtitleOptions.size > 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (subtitleOptions.size > 1) {
                    OptionSelectorRow(
                        label = "Subtitles",
                        selectedOption = selectedSubtitle,
                        options = subtitleOptions,
                        onOptionSelected = { selectedSubtitle = it }
                    )
                }

                if (item.type != "Series") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(24.dp))
                        ) {
                            Button(
                                onClick = onPlayClick,
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPartiallyWatched) Color(0xFF1F1F24) else Color.White,
                                    contentColor = Color.Black
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    val progressFraction = resumeProgress.coerceIn(0f, 1f)

                                    if (isPartiallyWatched) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(progressFraction)
                                                .background(Color.White)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 14.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = if (isPartiallyWatched) "Resume" else "Play",
                                            modifier = Modifier.size(22.dp),
                                            tint = if (isPartiallyWatched) Color.White else Color.Black
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = playButtonText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isPartiallyWatched) Color.White else Color.Black
                                        )
                                    }

                                    if (isPartiallyWatched) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 14.dp)
                                                .drawWithContent {
                                                    clipRect(right = size.width * progressFraction) {
                                                        this@drawWithContent.drawContent()
                                                    }
                                                },
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = if (isPartiallyWatched) "Resume" else "Play",
                                                modifier = Modifier.size(22.dp),
                                                tint = Color.Black
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = playButtonText,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = Color.Black
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                if (canDownloadItem) {
                                    coroutineScope.launch {
                                        downloadRepository.enqueueItemDownload(item)
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF1F1F24),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                        ) {
                            val buttonState = when {
                                !canDownloadItem -> "unavailable"
                                itemDownloadState.status == DownloadStatus.COMPLETED -> "completed"
                                itemDownloadState.status == DownloadStatus.DOWNLOADING -> "downloading"
                                itemDownloadState.status == DownloadStatus.QUEUED && itemDownloadState.message == "Paused" -> "paused"
                                itemDownloadState.status == DownloadStatus.QUEUED -> "queued"
                                itemDownloadState.status == DownloadStatus.FAILED -> "failed"
                                else -> "idle"
                            }
                            val iconLabel: @Composable (androidx.compose.ui.graphics.vector.ImageVector, String, String, Color?) -> Unit =
                                { icon, label, contentDescription, tint ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = contentDescription,
                                            modifier = Modifier.size(18.dp),
                                            tint = tint ?: LocalContentColor.current
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = label,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            AnimatedContent(
                                targetState = buttonState,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(220)) togetherWith
                                        fadeOut(animationSpec = tween(180))
                                },
                                label = "download_button_state"
                            ) { state ->
                                when (state) {
                                    "downloading", "queued" -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            CircularProgressIndicator(
                                                progress = { animatedDownloadProgress.coerceIn(0.05f, 0.99f) },
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = Color(0xFF03A9F4)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${(animatedDownloadProgress * 100).toInt()}%",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    "completed" -> {
                                        iconLabel(Icons.Rounded.CheckCircle, "Downloaded", "Downloaded", Color(0xFF4CAF50))
                                    }
                                    "paused" -> {
                                        iconLabel(Icons.Rounded.PauseCircle, "Paused", "Paused", Color(0xFFFFC107))
                                    }
                                    "failed" -> {
                                        iconLabel(Icons.Rounded.Refresh, "Retry", "Retry download", null)
                                    }
                                    "unavailable" -> {
                                        iconLabel(Icons.Rounded.Download, "Unavailable", "Download unavailable", null)
                                    }
                                    else -> {
                                        iconLabel(Icons.Rounded.Download, "Download", "Download", null)
                                    }
                                }
                            }
                        }
                    }
                }

                item.overview?.let { overview ->
                    OverviewSection(
                        overview = overview,
                        title = "Description",
                        modifier = Modifier.padding(top = 18.dp)
                    )
                }

                if (item.type == "Series") {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val seriesId = item.id
                                if (seriesId.isNullOrBlank()) {
                                    return@launch
                                }
                                seriesQueueInProgress = true
                                try {
                                    downloadRepository.enqueueSeriesDownload(seriesId)
                                } finally {
                                    seriesQueueInProgress = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                            .height(46.dp),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF1F1F24),
                            contentColor = Color.White
                        )
                    ) {
                        AnimatedContent(
                            targetState = seriesQueueInProgress,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) togetherWith
                                    fadeOut(animationSpec = tween(180))
                            },
                            label = "download_series_state"
                        ) { inProgress ->
                            if (inProgress) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF03A9F4)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Queuing...",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = "Download series",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Download Series",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    item.id?.let { seriesId ->
                        SeasonsSection(
                            seriesId = seriesId,
                            mediaRepository = mediaRepository,
                            onSeasonClick = onSeasonClick
                        )
                    }
                }

                CastSection(
                    item = item,
                    mediaRepository = mediaRepository
                )
            }
        }
    }
}

@Composable
private fun DetailInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label  ",
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionSelectorRow(
    label: String,
    selectedOption: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label  ",
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier
                    .menuAnchor(
                        type = MenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
                    .fillMaxWidth(),
                color = Color(0xFF1F1F24),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedOption.ifBlank { options.firstOrNull().orEmpty() },
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Select $label",
                        tint = Color.White
                    )
                }
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun buildVideoOptions(streams: List<MediaStream>): List<String> {
    return uniquifyOptionLabels(
        streams
        .filter { it.type == "Video" }
        .mapNotNull { stream ->
            val display = stream.displayTitle?.takeIf { it.isNotBlank() }
            display?.takeIf { it.isNotBlank() }
        }
    )
}

private fun buildAudioOptions(streams: List<MediaStream>): List<String> {
    val options = uniquifyOptionLabels(
        streams
        .filter { it.type == "Audio" }
        .mapNotNull { stream ->
            val display = stream.displayTitle?.takeIf { it.isNotBlank() }
            display?.takeIf { it.isNotBlank() }
        }
    )

    val defaultLabel = streams
        .firstOrNull { it.type == "Audio" && it.isDefault == true }
        ?.let { stream ->
            stream.displayTitle?.takeIf { it.isNotBlank() }
        }

    return if (!defaultLabel.isNullOrBlank() && options.contains(defaultLabel)) {
        listOf(defaultLabel) + options.filter { it != defaultLabel }
    } else {
        options
    }
}

private fun buildSubtitleOptions(streams: List<MediaStream>): List<String> {
    val options = mutableListOf("Off")
    streams
        .filter { it.type == "Subtitle" }
        .forEach { stream ->
            val subtitleLabel = stream.displayTitle?.takeIf { it.isNotBlank() }
            subtitleLabel?.let { options.add(it) }
        }
    return uniquifyOptionLabels(options)
}

private fun buildDefaultSubtitleOption(streams: List<MediaStream>): String {
    return streams
        .firstOrNull { it.type == "Subtitle" && it.isDefault == true }
        ?.let { stream ->
            stream.displayTitle?.takeIf { it.isNotBlank() }
        }
        ?: "Off"
}

private suspend fun heroImageCandidates(
    item: BaseItemDto,
    mediaRepository: MediaRepository
): List<String> {
    val itemId = item.id ?: return emptyList()
    val seriesId = item.seriesId
    val candidates = mutableListOf<String>()

    fun addCandidate(url: String?) {
        if (!url.isNullOrBlank() && !candidates.contains(url)) {
            candidates.add(url)
        }
    }

    if (item.type == "Episode") {
        addCandidate(mediaRepository.getBackdropImageUrl(
            itemId = itemId,
            width = 1920,
            height = 1080,
            quality = 100,
            enableImageEnhancers = false
        ).first())

        addCandidate(mediaRepository.getImageUrl(
            itemId = itemId,
            imageType = "Primary",
            width = 1920,
            height = 1080,
            quality = 100,
            enableImageEnhancers = false
        ).first())

        addCandidate(mediaRepository.getImageUrl(
            itemId = itemId,
            imageType = "Thumb",
            width = 1920,
            height = 1080,
            quality = 100,
            enableImageEnhancers = false
        ).first())

        if (!seriesId.isNullOrBlank()) {
            addCandidate(mediaRepository.getBackdropImageUrl(
                itemId = seriesId,
                width = 1920,
                height = 1080,
                quality = 100,
                enableImageEnhancers = false
            ).first())
            addCandidate(mediaRepository.getImageUrl(
                itemId = seriesId,
                imageType = "Primary",
                width = 1920,
                height = 1080,
                quality = 100,
                enableImageEnhancers = false
            ).first())
        }
    } else {
        addCandidate(mediaRepository.getBackdropImageUrl(
            itemId = itemId,
            width = 1200,
            height = 675,
            quality = 95
        ).first())

        addCandidate(mediaRepository.getImageUrl(
            itemId = itemId,
            imageType = "Primary",
            width = 1200,
            height = 675,
            quality = 95
        ).first())
    }

    return candidates
}

private suspend fun logoImage(
    item: BaseItemDto,
    mediaRepository: MediaRepository
): String? {
    val logoItemId = if (item.type == "Episode") {
        item.seriesId ?: item.id
    } else {
        item.id
    } ?: return null

    return mediaRepository.getImageUrl(
        itemId = logoItemId,
        imageType = "Logo",
        width = 1200,
        quality = 95
    ).first()
}

private fun episodeHeaderText(item: BaseItemDto): String? {
    if (item.type != "Episode") return null
    val title = item.name?.takeIf { it.isNotBlank() } ?: "Unknown"
    val season = item.parentIndexNumber
    val episode = item.indexNumber
    return when {
        season != null && episode != null -> "S${season}:E${episode} - $title"
        episode != null -> "Episode $episode - $title"
        else -> title
    }
}

private fun uniquifyOptionLabels(options: List<String>): List<String> {
    val counts = mutableMapOf<String, Int>()
    return options.map { option ->
        val seen = (counts[option] ?: 0) + 1
        counts[option] = seen
        if (seen == 1) option else "$option ($seen)"
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
fun DetailScreenSeriesPreview() {
    MaterialTheme {
        val mockItem = BaseItemDto(
            id = "mock-id",
            name = "Planet Earth",
            type = "Series",
            overview = "A landmark documentary series showcasing Earth's natural wonders and wildlife.",
            productionYear = 2006,
            communityRating = 9.4f,
            officialRating = "TV-PG",
            genres = listOf("Documentary", "Nature"),
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

@Composable
fun DetailScreenSkeleton(
    onBackPressed: () -> Unit = {}
) {
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
                    .height(330.dp)
            ) {
                ShimmerEffect(
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0f
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
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .offset(y = (-58).dp)
            ) {
                ShimmerEffect(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .height(62.dp),
                    cornerRadius = 10f
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShimmerEffect(modifier = Modifier.width(56.dp).height(16.dp), cornerRadius = 8f)
                    ShimmerEffect(modifier = Modifier.width(42.dp).height(16.dp), cornerRadius = 8f)
                    ShimmerEffect(modifier = Modifier.width(64.dp).height(16.dp), cornerRadius = 8f)
                    ShimmerEffect(modifier = Modifier.width(50.dp).height(18.dp), cornerRadius = 8f)
                }

                Spacer(modifier = Modifier.height(8.dp))

                ShimmerEffect(
                    modifier = Modifier.fillMaxWidth(0.78f).height(14.dp),
                    cornerRadius = 8f
                )

                Spacer(modifier = Modifier.height(14.dp))

                repeat(2) {
                    ShimmerEffect(
                        modifier = Modifier.fillMaxWidth(0.82f).height(18.dp),
                        cornerRadius = 8f
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                ShimmerEffect(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    cornerRadius = 18f
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShimmerEffect(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        cornerRadius = 24f
                    )
                    ShimmerEffect(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        cornerRadius = 24f
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                ShimmerEffect(
                    modifier = Modifier.width(110.dp).height(22.dp),
                    cornerRadius = 10f
                )
                Spacer(modifier = Modifier.height(10.dp))
                repeat(3) {
                    ShimmerEffect(
                        modifier = Modifier
                            .fillMaxWidth(if (it == 2) 0.72f else 1f)
                            .height(14.dp),
                        cornerRadius = 8f
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
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

