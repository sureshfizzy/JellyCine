package com.jellycine.app.ui.screens.detail

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.jellycine.app.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.jellycine.app.util.image.JellyfinPosterImage
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaSourceInfo
import com.jellycine.data.model.MediaStream
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.flow.first
import android.content.res.Configuration
import com.jellycine.app.ui.screens.player.PlayerScreen
import com.jellycine.detail.CodecUtils
import com.jellycine.app.ui.components.common.CastSection
import com.jellycine.app.ui.components.common.CastActionButton
import com.jellycine.app.ui.components.common.CastDevicePicker
import com.jellycine.app.ui.components.common.OverviewSection
import com.jellycine.app.ui.components.common.ScreenWrapper
import com.jellycine.app.ui.components.common.ShimmerEffect
import com.jellycine.app.cast.CastController
import com.jellycine.app.download.BatchDownloadCandidate
import com.jellycine.app.download.BatchDownloadEstimate
import com.jellycine.app.download.DownloadRepositoryProvider
import com.jellycine.app.download.DownloadStatus
import com.jellycine.app.download.ItemDownloadState
import com.jellycine.app.ui.screens.cast.CastPlayback
import com.jellycine.app.ui.screens.cast.loadCastPlaybackData
import com.jellycine.app.ui.screens.cast.activeCastArtworkUrl
import com.jellycine.player.core.defaultSubtitleDisplayTitle
import com.jellycine.player.core.mediaStreamDisplayTitles
import com.jellycine.player.preferences.PlayerPreferences
import java.util.Locale
import androidx.media3.common.util.UnstableApi
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch


@UnstableApi
@Composable
fun DetailScreenContainer(
    itemId: String,
    onBackPressed: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToPerson: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val playerPreferences = remember { PlayerPreferences(context) }

    var item by remember { mutableStateOf<BaseItemDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPlayer by remember { mutableStateOf(false) }
    var playbackItemId by remember { mutableStateOf<String?>(null) }
    var preferredAudioStreamIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var preferredSubtitleStreamIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var trackSelectionSyncVersion by rememberSaveable { mutableStateOf(0) }

    // Navigation state
    var currentScreen by remember { mutableStateOf("detail") }
    var seasonDetailData by remember { mutableStateOf<Triple<String, String, String?>?>(null) }
    var episodeDetailId by remember { mutableStateOf<String?>(null) }
    var episodeItem by remember { mutableStateOf<BaseItemDto?>(null) }
    var isEpisodeLoading by remember { mutableStateOf(false) }
    var episodeError by remember { mutableStateOf<String?>(null) }
    var castingDisplay by remember { mutableStateOf(false) }
    var castDisplayItem by remember { mutableStateOf<BaseItemDto?>(null) }
    var castDisplayArtworkUrl by remember { mutableStateOf<String?>(null) }
    var castDisplayStreams by remember { mutableStateOf<List<MediaStream>>(emptyList()) }
    var castDisplayAudioStreamIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var castDisplaySubtitleStreamIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var castTracks by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val castPlaybackState by CastController.playbackState.collectAsState()

    LaunchedEffect(context) {
        CastController.ensureInitialized(context)
    }

    suspend fun castDisplayState(castItemId: String) {
        val activeItem = when {
            castDisplayItem?.id == castItemId -> castDisplayItem
            episodeItem?.id == castItemId -> episodeItem
            item?.id == castItemId -> item
            else -> mediaRepository.getItemById(castItemId).getOrNull()
        }

        val playbackData = loadCastPlaybackData(
            mediaRepository = mediaRepository,
            playerPreferences = playerPreferences,
            itemId = castItemId,
            activeItem = activeItem
        )
        castDisplayItem = playbackData.item
        castDisplayArtworkUrl = playbackData.artworkUrl
        castDisplayStreams = playbackData.streams
        castDisplayAudioStreamIndex = playbackData.selectedAudioStreamIndex
        castDisplaySubtitleStreamIndex = playbackData.selectedSubtitleStreamIndex
    }

    fun localPlayer(targetItemId: String) {
        playbackItemId = targetItemId
        castingDisplay = false
        showPlayer = true
    }

    fun openCastingDisplay() {
        if (!castPlaybackState.isConnected) return
        val castItemId = castPlaybackState.currentItemId
            ?: castDisplayItem?.id
        castingDisplay = true
        if (castItemId.isNullOrBlank()) return
        scope.launch {
            castDisplayState(castItemId = castItemId)
        }
    }

    fun updateCastStreams(
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?
    ) {
        if (castTracks) return
        val castItemId = castPlaybackState.currentItemId ?: castDisplayItem?.id ?: return
        val metadataItem = castDisplayItem

        scope.launch {
            castTracks = true
            try {
                val castResult = CastController.castItem(
                    context = context,
                    mediaRepository = mediaRepository,
                    itemId = castItemId,
                    title = metadataItem?.name ?: castPlaybackState.mediaTitle,
                    subtitle = metadataItem?.seriesName ?: castPlaybackState.mediaSubtitle,
                    itemType = metadataItem?.type,
                    artworkUrl = castPlaybackState.artworkUrl ?: castDisplayArtworkUrl,
                    startPositionMs = castPlaybackState.positionMs,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex
                )

                if (castResult.isSuccess) {
                    castDisplayAudioStreamIndex = audioStreamIndex
                    castDisplaySubtitleStreamIndex = subtitleStreamIndex
                    playerPreferences.setPreferredAudioStreamIndex(castItemId, audioStreamIndex)
                    playerPreferences.setPreferredSubtitleStreamIndex(castItemId, subtitleStreamIndex)
                }
            } finally {
                castTracks = false
            }
        }
    }

    fun startPlaybackForItem(
        targetItem: BaseItemDto?,
        fallbackItemId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?
    ) {
        val activeItemId = targetItem?.id?.takeIf { it.isNotBlank() } ?: fallbackItemId
        preferredAudioStreamIndex = audioStreamIndex
        preferredSubtitleStreamIndex = subtitleStreamIndex

        scope.launch {
            if (castPlaybackState.isConnected) {
                val startPositionMs = (targetItem?.userData?.playbackPositionTicks ?: 0L) / 10_000L
                val artworkUrl = activeCastArtworkUrl(
                    mediaRepository = mediaRepository,
                    item = targetItem,
                    fallbackItemId = activeItemId
                )
                val castResult = CastController.castItem(
                    context = context,
                    mediaRepository = mediaRepository,
                    itemId = activeItemId,
                    title = targetItem?.name,
                    subtitle = targetItem?.seriesName,
                    itemType = targetItem?.type,
                    artworkUrl = artworkUrl,
                    startPositionMs = startPositionMs,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex
                )

                if (castResult.isSuccess) {
                    playbackItemId = activeItemId
                    castDisplayItem = targetItem
                    castDisplayArtworkUrl = artworkUrl
                    castDisplayAudioStreamIndex = audioStreamIndex
                    castDisplaySubtitleStreamIndex = subtitleStreamIndex
                    showPlayer = false
                    castingDisplay = true
                    castDisplayState(castItemId = activeItemId)
                } else {
                    localPlayer(activeItemId)
                }
            } else {
                localPlayer(activeItemId)
            }
        }
    }

    val handleBackNavigation: () -> Unit = {
        when {
            castingDisplay -> {
                castingDisplay = false
            }
            showPlayer -> {
                val playedItemId = playbackItemId ?: itemId
                preferredAudioStreamIndex = playerPreferences.getPreferredAudioStreamIndex(playedItemId)
                preferredSubtitleStreamIndex = playerPreferences.getPreferredSubtitleStreamIndex(playedItemId)
                trackSelectionSyncVersion += 1
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

    suspend fun nextEpisodeId(completedItemId: String): String? {
        val completedItem = mediaRepository.getItemById(completedItemId).getOrNull() ?: return null
        if (!completedItem.type.equals("Episode", ignoreCase = true)) return null

        val seriesId = completedItem.seriesId ?: return null
        val episodesResult = if (!completedItem.seasonId.isNullOrBlank()) {
            mediaRepository.getEpisodes(seriesId = seriesId, seasonId = completedItem.seasonId)
        } else {
            mediaRepository.getEpisodes(seriesId = seriesId)
        }
        val orderedEpisodes = episodesResult
            .getOrNull()
            ?.sortedWith(
                compareBy<BaseItemDto>(
                    { it.parentIndexNumber ?: Int.MAX_VALUE },
                    { it.indexNumber ?: Int.MAX_VALUE },
                    { it.name.orEmpty() },
                    { it.id.orEmpty() }
                )
            )
            .orEmpty()

        if (orderedEpisodes.isEmpty()) return null
        val currentIndex = orderedEpisodes.indexOfFirst { it.id == completedItemId }
        if (currentIndex < 0 || currentIndex >= orderedEpisodes.lastIndex) return null

        val nextEpisodeId = orderedEpisodes[currentIndex + 1].id
        return nextEpisodeId?.takeIf { it.isNotBlank() && it != completedItemId }
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

    LaunchedEffect(castPlaybackState.isConnected, castPlaybackState.isCastingMedia) {
        if (castingDisplay && !castPlaybackState.isConnected) {
            castingDisplay = false
        }
        if (!castPlaybackState.isConnected) {
            castDisplayStreams = emptyList()
            castDisplayItem = null
            castDisplayArtworkUrl = null
            castDisplayAudioStreamIndex = null
            castDisplaySubtitleStreamIndex = null
            castTracks = false
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
                preferredAudioStreamIndex = preferredAudioStreamIndex,
                preferredSubtitleStreamIndex = preferredSubtitleStreamIndex,
                onPreferredStreamIndexesChanged = { audioStreamIndex, subtitleStreamIndex ->
                    preferredAudioStreamIndex = audioStreamIndex
                    preferredSubtitleStreamIndex = subtitleStreamIndex
                },
                onBackPressed = {
                    val playedItemId = playbackItemId ?: itemId
                    preferredAudioStreamIndex = playerPreferences.getPreferredAudioStreamIndex(playedItemId)
                    preferredSubtitleStreamIndex = playerPreferences.getPreferredSubtitleStreamIndex(playedItemId)
                    trackSelectionSyncVersion += 1
                    showPlayer = false
                    playbackItemId = null
                },
                onPlaybackCompleted = { completedItemId ->
                    scope.launch {
                        val nextEpisodeId = nextEpisodeId(completedItemId) ?: return@launch
                        playbackItemId = nextEpisodeId
                        if (currentScreen == "episode") {
                            episodeDetailId = nextEpisodeId
                        }
                    }
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
                                    trackSelectionSyncVersion = trackSelectionSyncVersion,
                                    onBackPressed = handleBackNavigation,
                                    onPlayClick = { audioStreamIndex, subtitleStreamIndex ->
                                        startPlaybackForItem(
                                            targetItem = currentItem,
                                            fallbackItemId = itemId,
                                            audioStreamIndex = audioStreamIndex,
                                            subtitleStreamIndex = subtitleStreamIndex
                                        )
                                    },
                                    onPreferredStreamIndexesChanged = { audioStreamIndex, subtitleStreamIndex ->
                                        preferredAudioStreamIndex = audioStreamIndex
                                        preferredSubtitleStreamIndex = subtitleStreamIndex
                                    },
                                    onSimilarItemClick = { selectedItemId ->
                                        onNavigateToDetail(selectedItemId)
                                    },
                                    onPersonClick = { personId ->
                                        onNavigateToPerson(personId)
                                    },
                                    onCastButtonClick = { openCastingDisplay() },
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
                                            trackSelectionSyncVersion = trackSelectionSyncVersion,
                                            onBackPressed = handleBackNavigation,
                                            onPlayClick = { audioStreamIndex, subtitleStreamIndex ->
                                                startPlaybackForItem(
                                                    targetItem = episodeItem,
                                                    fallbackItemId = episodeId,
                                                    audioStreamIndex = audioStreamIndex,
                                                    subtitleStreamIndex = subtitleStreamIndex
                                                )
                                            },
                                            onPreferredStreamIndexesChanged = { audioStreamIndex, subtitleStreamIndex ->
                                                preferredAudioStreamIndex = audioStreamIndex
                                                preferredSubtitleStreamIndex = subtitleStreamIndex
                                            },
                                            onSimilarItemClick = { selectedItemId ->
                                                onNavigateToDetail(selectedItemId)
                                            },
                                            onPersonClick = { personId ->
                                                onNavigateToPerson(personId)
                                            },
                                            onCastButtonClick = { openCastingDisplay() },
                                            onSeasonClick = { seriesId, seasonId, seasonName ->
                                                seasonDetailData = Triple(seriesId, seasonId, seasonName)
                                                currentScreen = "season"
                                            }
                                        )
                                    }

                                    else -> {
                                        DetailScreenSkeleton(onBackPressed = handleBackNavigation)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (castingDisplay) {
            CastPlayback(
                castState = castPlaybackState,
                streams = castDisplayStreams,
                fallbackArtworkUrl = castDisplayArtworkUrl,
                selectedAudioStreamIndex = castDisplayAudioStreamIndex,
                selectedSubtitleStreamIndex = castDisplaySubtitleStreamIndex,
                isTrackSelectionUpdating = castTracks,
                onDismissRequest = { castingDisplay = false },
                onTogglePlayPause = { CastController.togglePlayPause(context) },
                onStopCasting = { CastController.stopPlayback(context) },
                onDisconnect = {
                    CastController.disconnect(context)
                    castingDisplay = false
                },
                onSeekTo = { seekPosition -> CastController.seekTo(context, seekPosition) },
                onTrackSelectionChanged = { audioStreamIndex, subtitleStreamIndex ->
                    updateCastStreams(
                        audioStreamIndex = audioStreamIndex,
                        subtitleStreamIndex = subtitleStreamIndex
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    item: BaseItemDto,
    isLoading: Boolean = false,
    trackSelectionSyncVersion: Int = 0,
    onBackPressed: () -> Unit = {},
    onPlayClick: (Int?, Int?) -> Unit = { _, _ -> },
    onPreferredStreamIndexesChanged: (Int?, Int?) -> Unit = { _, _ -> },
    onSimilarItemClick: (String) -> Unit = {},
    onPersonClick: (String) -> Unit = {},
    onCastButtonClick: () -> Unit = {},
    onSeasonClick: (String, String, String?) -> Unit = { _, _, _ -> }
) {
    DetailContent(
        item = item,
        isLoading = isLoading,
        trackSelectionSyncVersion = trackSelectionSyncVersion,
        onBackPressed = onBackPressed,
        onPlayClick = onPlayClick,
        onPreferredStreamIndexesChanged = onPreferredStreamIndexesChanged,
        onSimilarItemClick = onSimilarItemClick,
        onPersonClick = onPersonClick,
        onCastButtonClick = onCastButtonClick,
        onSeasonClick = onSeasonClick
    )
}

@Composable
fun DetailContent(
    item: BaseItemDto,
    isLoading: Boolean = false,
    trackSelectionSyncVersion: Int = 0,
    onBackPressed: () -> Unit = {},
    onPlayClick: (Int?, Int?) -> Unit = { _, _ -> },
    onPreferredStreamIndexesChanged: (Int?, Int?) -> Unit = { _, _ -> },
    onSimilarItemClick: (String) -> Unit = {},
    onPersonClick: (String) -> Unit = {},
    onCastButtonClick: () -> Unit = {},
    onSeasonClick: (String, String, String?) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val downloadRepository = remember { DownloadRepositoryProvider.getInstance(context) }
    val playerPreferences = remember { PlayerPreferences(context) }
    val coroutineScope = rememberCoroutineScope()
    val castPlaybackState by CastController.playbackState.collectAsState()
    val configuration = LocalConfiguration.current
    val isWidescreenLayout = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                             configuration.screenWidthDp >= 600
    val metadataScrollState = rememberScrollState()
    var showCastDevicePicker by remember { mutableStateOf(false) }

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
    val effectiveMediaStreams = remember(item.mediaStreams, item.mediaSources) {
        val fromSources = item.mediaSources.orEmpty().flatMap { it.mediaStreams.orEmpty() }
        if (fromSources.isNotEmpty()) fromSources else item.mediaStreams.orEmpty()
    }
    val savedAudioOption = remember(item.id, effectiveMediaStreams, trackSelectionSyncVersion) {
        val currentItemId = item.id ?: return@remember null
        AudioStreamIndex(
            streams = effectiveMediaStreams,
            streamIndex = playerPreferences.getPreferredAudioStreamIndex(currentItemId)
        )
    }
    val savedSubtitleOption = remember(item.id, effectiveMediaStreams, trackSelectionSyncVersion) {
        val currentItemId = item.id ?: return@remember null
        SubtitleStreamIndex(
            streams = effectiveMediaStreams,
            streamIndex = playerPreferences.getPreferredSubtitleStreamIndex(currentItemId)
        )
    }
    val initialVideoOption = remember(item.id, effectiveMediaStreams) {
        buildVideoOptions(effectiveMediaStreams).firstOrNull().orEmpty()
    }
    val initialAudioOption = remember(item.id, effectiveMediaStreams, savedAudioOption) {
        savedAudioOption ?: buildAudioOptions(effectiveMediaStreams).firstOrNull().orEmpty()
    }
    val initialSubtitleOption = remember(item.id, effectiveMediaStreams, savedSubtitleOption) {
        savedSubtitleOption ?: buildDefaultSubtitleOption(effectiveMediaStreams)
    }
    var selectedVideo by rememberSaveable(item.id) { mutableStateOf(initialVideoOption) }
    var selectedAudio by rememberSaveable(item.id, trackSelectionSyncVersion) { mutableStateOf(initialAudioOption) }
    var selectedSubtitle by rememberSaveable(item.id, trackSelectionSyncVersion) { mutableStateOf(initialSubtitleOption) }
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
    val genresText = remember(item.genres) {
        item.genres?.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }
    val canDownloadItem = item.id != null && item.canDownload != false
    val itemDownloadStateFlow = remember(item.id) { item.id?.let { downloadRepository.observeItemDownload(it) } }
    val itemDownloadState by (itemDownloadStateFlow?.collectAsState()
        ?: remember(item.id) { mutableStateOf(ItemDownloadState()) })
    var downloadErrorDialogMessage by remember(item.id) { mutableStateOf<String?>(null) }
    var previousDownloadStatus by remember(item.id) { mutableStateOf(itemDownloadState.status) }
    var seriesQueueInProgress by remember(item.id) { mutableStateOf(false) }
    var seriesStorageSelectionDialogState by remember(item.id) { mutableStateOf<SeriesSeasonSelectionDialogState?>(null) }
    var isFavorite by remember(item.id, item.userData?.isFavorite) {
        mutableStateOf(item.userData?.isFavorite == true)
    }
    var similarItems by remember(item.id) { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var moreFromSeasonEpisodes by remember(item.id, item.seriesId, item.seasonId) {
        mutableStateOf<List<BaseItemDto>>(emptyList())
    }
    fun toggleFavorite() {
        val currentItemId = item.id ?: return
        val targetState = !isFavorite
        coroutineScope.launch {
            val result = mediaRepository.setFavoriteStatus(
                itemId = currentItemId,
                isFavorite = targetState
            )
            if (result.isSuccess) {
                isFavorite = targetState
            }
        }
    }
    val animatedDownloadProgress by animateFloatAsState(
        targetValue = when (itemDownloadState.status) {
            DownloadStatus.QUEUED -> {
                if (itemDownloadState.message == "Paused") 0.05f
                else itemDownloadState.progress.coerceIn(0.05f, 0.99f)
            }
            DownloadStatus.DOWNLOADING -> itemDownloadState.progress.coerceIn(0.05f, 0.99f)
            DownloadStatus.COMPLETED -> 1f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 350),
        label = "detail_download_progress"
    )

    LaunchedEffect(itemDownloadState.status) {
        if (
            previousDownloadStatus != DownloadStatus.FAILED &&
            itemDownloadState.status == DownloadStatus.FAILED
        ) {
            downloadErrorDialogMessage = downloadFailureDialogMessage(state = itemDownloadState)
        }
        previousDownloadStatus = itemDownloadState.status
    }

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

    LaunchedEffect(item.id) {
        val currentItemId = item.id
        if (currentItemId.isNullOrBlank()) {
            similarItems = emptyList()
            return@LaunchedEffect
        }

        mediaRepository.getSimilarItems(itemId = currentItemId, limit = 16).fold(
            onSuccess = { items ->
                similarItems = items.filter { !it.id.isNullOrBlank() }
            },
            onFailure = {
                similarItems = emptyList()
            }
        )
    }

    LaunchedEffect(item.id, item.type, item.seriesId, item.seasonId, item.parentIndexNumber) {
        if (!isEpisode) {
            moreFromSeasonEpisodes = emptyList()
            return@LaunchedEffect
        }

        val seriesId = item.seriesId?.takeIf { it.isNotBlank() }
        if (seriesId.isNullOrBlank()) {
            moreFromSeasonEpisodes = emptyList()
            return@LaunchedEffect
        }

        val seasonId = item.seasonId?.takeIf { it.isNotBlank() }
        val result = if (seasonId != null) {
            mediaRepository.getEpisodes(seriesId = seriesId, seasonId = seasonId)
        } else {
            mediaRepository.getEpisodes(seriesId = seriesId)
        }

        moreFromSeasonEpisodes = result
            .getOrNull()
            .orEmpty()
            .filter { episode ->
                when {
                    seasonId != null -> episode.seasonId == seasonId
                    item.parentIndexNumber != null -> episode.parentIndexNumber == item.parentIndexNumber
                    else -> true
                }
            }
            .filter { episode ->
                val episodeId = episode.id
                !episodeId.isNullOrBlank() && episodeId != item.id
            }
            .sortedWith(
                compareBy<BaseItemDto>(
                    { it.indexNumber ?: Int.MAX_VALUE },
                    { it.name.orEmpty() },
                    { it.id.orEmpty() }
                )
            )
    }

    val moreFromSeasonTitle = remember(item.seasonName, item.parentIndexNumber) {
        val seasonLabel = item.parentIndexNumber?.let { "Season $it" }
            ?: item.seasonName?.takeIf { it.isNotBlank() }
            ?: "Season"
        "More from $seasonLabel"
    }

    val videoOptions = remember(effectiveMediaStreams) { buildVideoOptions(effectiveMediaStreams) }
    val audioOptions = remember(effectiveMediaStreams) { buildAudioOptions(effectiveMediaStreams) }
    val subtitleOptions = remember(effectiveMediaStreams) { buildSubtitleOptions(effectiveMediaStreams) }
    val defaultSubtitleOption = remember(effectiveMediaStreams) { buildDefaultSubtitleOption(effectiveMediaStreams) }
    val codecBadges = CodecBadges(
        streams = effectiveMediaStreams,
        selectedVideo = selectedVideo,
        selectedAudio = selectedAudio
    )
    val videoInlineMetaText = remember(
        item.mediaSources,
        effectiveMediaStreams
    ) {
        buildVideoInlineText(
            mediaSources = item.mediaSources.orEmpty(),
            streams = effectiveMediaStreams
        )
    }

    fun persistTrackSelection(audioOption: String, subtitleOption: String): Pair<Int?, Int?> {
        val audioStreamIndex = AudioStreamIndex(
            streams = effectiveMediaStreams,
            selectedOption = audioOption
        )
        val subtitleStreamIndex = SubtitleStreamIndex(
            streams = effectiveMediaStreams,
            selectedOption = subtitleOption
        )
        item.id?.let { currentItemId ->
            playerPreferences.setPreferredAudioStreamIndex(currentItemId, audioStreamIndex)
            playerPreferences.setPreferredSubtitleStreamIndex(currentItemId, subtitleStreamIndex)
        }
        onPreferredStreamIndexesChanged(audioStreamIndex, subtitleStreamIndex)
        return audioStreamIndex to subtitleStreamIndex
    }

    val onVideoOptionSelected: (String) -> Unit = { option ->
        selectedVideo = option
    }
    val onAudioOptionSelected: (String) -> Unit = { option ->
        selectedAudio = option
        persistTrackSelection(
            audioOption = option,
            subtitleOption = selectedSubtitle
        )
    }
    val onSubtitleOptionSelected: (String) -> Unit = { option ->
        selectedSubtitle = option
        persistTrackSelection(
            audioOption = selectedAudio,
            subtitleOption = option
        )
    }

    LaunchedEffect(videoOptions, audioOptions, subtitleOptions, defaultSubtitleOption) {
        if (selectedVideo !in videoOptions) {
            selectedVideo = videoOptions.firstOrNull().orEmpty()
        }
        if (selectedAudio !in audioOptions) {
            selectedAudio = audioOptions.firstOrNull().orEmpty()
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

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 12.dp, end = 14.dp)
                ) {
                    CastActionButton(
                        isConnected = castPlaybackState.isConnected,
                        onClick = {
                            if (castPlaybackState.isConnected) {
                                onCastButtonClick()
                            } else {
                                showCastDevicePicker = true
                            }
                        }
                    )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isWidescreenLayout) {
                                Modifier.horizontalScroll(metadataScrollState)
                            } else {
                                Modifier
                            }
                        )
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

                    val officialRatingLabel = item.officialRating?.takeIf { it.isNotBlank() } ?: "NR"
                    Surface(
                        color = Color.Transparent,
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = officialRatingLabel,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            maxLines = 1
                        )
                    }

                    if (isWidescreenLayout && !genresText.isNullOrBlank()) {
                        Text(
                            text = genresText,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!isWidescreenLayout && !genresText.isNullOrBlank()) {
                    Text(
                        text = genresText,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }

                if (castPlaybackState.isConnected) {
                    Surface(
                        color = Color(0xFF173025),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Cast,
                                contentDescription = null,
                                tint = Color(0xFFA7FFD7),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (castPlaybackState.isCastingMedia) {
                                    "Casting to ${castPlaybackState.deviceName?.takeIf { it.isNotBlank() } ?: "device"}"
                                } else {
                                    "Connected to ${castPlaybackState.deviceName?.takeIf { it.isNotBlank() } ?: "device"}"
                                },
                                fontSize = 12.sp,
                                color = Color(0xFFE6FFF3),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (codecBadges.hasAnyBadges) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        if (codecBadges.has4K) {
                            item {
                                CapabilityBadge(text = "4K")
                            }
                        }

                        if (codecBadges.hdrBadgeText.isNotBlank()) {
                            item {
                                CapabilityBadge(
                                    text = codecBadges.hdrBadgeText,
                                    icon = if (codecBadges.hdrBadgeText.equals("Dolby Vision", ignoreCase = true)) null else Icons.Rounded.HdrOn,
                                    customIcon = if (codecBadges.hdrBadgeText.equals("Dolby Vision", ignoreCase = true)) R.drawable.ic_dolby_logo else null,
                                    iconTintUnspecified = codecBadges.hdrBadgeText.equals("Dolby Vision", ignoreCase = true)
                                )
                            }
                        }

                        if (codecBadges.hasSpatialAudio) {
                            item {
                                CapabilityBadge(
                                    text = "Spatial Audio",
                                    customIcon = R.drawable.ic_spatial_audio,
                                    customIconTint = Color(0xFF8DFFB3)
                                )
                            }
                        }

                        if (codecBadges.dolbyAudioBadgeText.isNotBlank()) {
                            item {
                                CapabilityBadge(
                                    text = codecBadges.dolbyAudioBadgeText,
                                    customIcon = if (codecBadges.hasDolbyAtmos) R.drawable.ic_dolby_atmos else R.drawable.ic_dolby_logo,
                                    iconTintUnspecified = true
                                )
                            }
                        }

                        if (codecBadges.audioChannelBadgeText.isNotBlank()) {
                            item {
                                CapabilityBadge(
                                    text = codecBadges.audioChannelBadgeText
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val hasVideoSection = videoOptions.isNotEmpty()
                val hasAudioSection = audioOptions.isNotEmpty()
                val hasSubtitleSection = subtitleOptions.size > 1
                val isVideoAudioOnlyWideLayout = hasVideoSection && hasAudioSection && !hasSubtitleSection

                if (isWidescreenLayout) {
                    if (isVideoAudioOnlyWideLayout) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val videoModifier = if (videoOptions.size > 1) {
                                Modifier.widthIn(max = 520.dp)
                            } else {
                                Modifier.wrapContentWidth()
                            }
                            TrackField(
                                modifier = videoModifier,
                                label = "Video",
                                selectedOption = selectedVideo,
                                options = videoOptions,
                                inlineMetaText = videoInlineMetaText,
                                singleValueFillWidth = false,
                                onOptionSelected = onVideoOptionSelected
                            )

                            val audioModifier = if (audioOptions.size > 1) {
                                Modifier.widthIn(min = 260.dp, max = 560.dp)
                            } else {
                                Modifier.wrapContentWidth()
                            }
                            TrackField(
                                modifier = audioModifier,
                                label = "Audio",
                                selectedOption = selectedAudio,
                                options = audioOptions,
                                singleValueFillWidth = false,
                                onOptionSelected = onAudioOptionSelected
                            )

                            Spacer(modifier = Modifier.weight(1f))
                        }
                    } else if (hasVideoSection || hasAudioSection || hasSubtitleSection) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasVideoSection) {
                                val videoModifier = when {
                                    hasSubtitleSection -> Modifier.weight(1f)
                                    hasAudioSection -> Modifier.widthIn(max = 520.dp)
                                    else -> Modifier.fillMaxWidth()
                                }
                                TrackField(
                                    modifier = videoModifier,
                                    label = "Video",
                                    selectedOption = selectedVideo,
                                    options = videoOptions,
                                    inlineMetaText = videoInlineMetaText,
                                    onOptionSelected = onVideoOptionSelected
                                )
                            }

                            if (hasAudioSection) {
                                val audioModifier = if (hasSubtitleSection || hasVideoSection) {
                                    Modifier.weight(1f)
                                } else {
                                    Modifier.fillMaxWidth()
                                }
                                TrackField(
                                    modifier = audioModifier,
                                    label = "Audio",
                                    selectedOption = selectedAudio,
                                    options = audioOptions,
                                    onOptionSelected = onAudioOptionSelected
                                )
                            }

                            if (hasSubtitleSection) {
                                val subtitleModifier = if (hasVideoSection || hasAudioSection) {
                                    Modifier.weight(1f)
                                } else {
                                    Modifier.fillMaxWidth()
                                }
                                TrackField(
                                    modifier = subtitleModifier,
                                    label = "Subtitles",
                                    selectedOption = selectedSubtitle,
                                    options = subtitleOptions,
                                    onOptionSelected = onSubtitleOptionSelected
                                )
                            }
                        }
                    }
                } else {
                    if (hasVideoSection) {
                        TrackField(
                            label = "Video",
                            selectedOption = selectedVideo,
                            options = videoOptions,
                            inlineMetaText = videoInlineMetaText,
                            onOptionSelected = onVideoOptionSelected
                        )
                    }

                    if (hasVideoSection && hasAudioSection) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (hasAudioSection) {
                        TrackField(
                            label = "Audio",
                            selectedOption = selectedAudio,
                            options = audioOptions,
                            onOptionSelected = onAudioOptionSelected
                        )
                    }

                    if (hasSubtitleSection) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TrackField(
                            label = "Subtitles",
                            selectedOption = selectedSubtitle,
                            options = subtitleOptions,
                            onOptionSelected = onSubtitleOptionSelected
                        )
                    }
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
                                onClick = {
                                    val (selectedAudioStreamIndex, selectedSubtitleStreamIndex) = persistTrackSelection(
                                        audioOption = selectedAudio,
                                        subtitleOption = selectedSubtitle
                                    )
                                    onPlayClick(selectedAudioStreamIndex, selectedSubtitleStreamIndex)
                                },
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
                                            .onFailure { throwable ->
                                                downloadErrorDialogMessage = downloadFailureDialogMessage(
                                                    rawMessage = throwable.message
                                                )
                                            }
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
                                else -> "idle"
                            }
                            val iconLabel: @Composable (androidx.compose.ui.graphics.vector.ImageVector, String, String, Color?, TextUnit) -> Unit =
                                { icon, label, contentDescription, tint, textSize ->
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
                                            fontSize = textSize,
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
                                        iconLabel(Icons.Rounded.CheckCircle, "Downloaded", "Downloaded", Color(0xFF4CAF50), 12.sp)
                                    }
                                    "paused" -> {
                                        iconLabel(Icons.Rounded.PauseCircle, "Paused", "Paused", Color(0xFFFFC107), 14.sp)
                                    }
                                    "unavailable" -> {
                                        iconLabel(Icons.Rounded.Download, "Unavailable", "Download unavailable", null, 14.sp)
                                    }
                                    else -> {
                                        iconLabel(Icons.Rounded.Download, "Download", "Download", null, 14.sp)
                                    }
                                }
                            }
                        }

                        FavoriteActionButton(
                            isFavorite = isFavorite,
                            onClick = ::toggleFavorite
                        )
                    }
                }

                item.overview?.let { overview ->
                    OverviewSection(
                        overview = overview,
                        title = "Description",
                        modifier = Modifier.padding(top = 18.dp)
                    )
                }

                if (isEpisode) {
                    MoreFromSeasonSection(
                        episodes = moreFromSeasonEpisodes,
                        mediaRepository = mediaRepository,
                        title = moreFromSeasonTitle,
                        onEpisodeClick = onSimilarItemClick
                    )
                }

                if (item.type == "Series") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val seriesId = item.id
                                    if (seriesId.isNullOrBlank()) {
                                        return@launch
                                    }
                                    seriesQueueInProgress = true
                                    try {
                                        val estimateResult = downloadRepository.buildSeriesDownloadEstimate(seriesId)
                                        estimateResult.fold(
                                            onSuccess = { estimate ->
                                                seriesStorageSelectionDialogState = SeriesSeasonSelectionDialogState.fromEstimate(estimate)
                                            },
                                            onFailure = { throwable ->
                                                downloadErrorDialogMessage = downloadFailureDialogMessage(rawMessage = throwable.message)
                                            }
                                        )
                                    } finally {
                                        seriesQueueInProgress = false
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

                        FavoriteActionButton(
                            isFavorite = isFavorite,
                            onClick = ::toggleFavorite
                        )
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
                    mediaRepository = mediaRepository,
                    onPersonClick = onPersonClick
                )

                SimilarItemsSection(
                    similarItems = similarItems,
                    mediaRepository = mediaRepository,
                    onItemClick = onSimilarItemClick
                )
            }
        }
    }

    CastDevicePicker(
        isVisible = showCastDevicePicker,
        onDismissRequest = { showCastDevicePicker = false }
    )

    seriesStorageSelectionDialogState?.let { dialogState ->
        DownloadDialog(
            title = "Choose Seasons",
            subtitle = "Pick seasons to download. Selected total must fit available storage.",
            availableBytes = dialogState.availableBytes,
            options = dialogState.options,
            initialSelection = dialogState.options.map { it.id }.toSet(),
            confirmLabel = "Download Seasons",
            onDismiss = { seriesStorageSelectionDialogState = null },
            onConfirm = { selectedIds ->
                val selectedEpisodes = selectedIds
                    .flatMap { seasonId -> dialogState.episodesBySeasonId[seasonId].orEmpty() }
                    .distinctBy { it.id }
                seriesStorageSelectionDialogState = null
                coroutineScope.launch {
                    seriesQueueInProgress = true
                    try {
                        downloadRepository.enqueueEpisodeDownloads(selectedEpisodes).onFailure { throwable ->
                            downloadErrorDialogMessage = downloadFailureDialogMessage(rawMessage = throwable.message)
                        }
                    } finally {
                        seriesQueueInProgress = false
                    }
                }
            }
        )
    }

    downloadErrorDialogMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { downloadErrorDialogMessage = null },
            containerColor = Color(0xFF1A1C22),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.92f),
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Download Failed",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp
                )
            },
            text = {
                Text(text = message)
            },
            confirmButton = {
                TextButton(
                    onClick = { downloadErrorDialogMessage = null },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF22D3EE)
                    )
                ) {
                    Text("OK", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

@Composable
private fun MoreFromSeasonSection(
    episodes: List<BaseItemDto>,
    mediaRepository: MediaRepository,
    title: String,
    onEpisodeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (episodes.isEmpty()) return

    Column(
        modifier = modifier.padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(
                items = episodes,
                key = { episode -> episode.id ?: "${episode.name}-${episode.indexNumber}" }
            ) { episode ->
                EpisodePreviewCard(
                    episode = episode,
                    mediaRepository = mediaRepository,
                    cardWidth = 224.dp,
                    thumbnailHeight = 126.dp,
                    onClick = {
                        episode.id?.let(onEpisodeClick)
                    }
                )
            }
        }
    }
}

@Composable
private fun SimilarItemsSection(
    similarItems: List<BaseItemDto>,
    mediaRepository: MediaRepository,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "More Like This"
) {
    if (similarItems.isEmpty()) return

    Column(
        modifier = modifier.padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(
                items = similarItems,
                key = { similarItem -> similarItem.id ?: "${similarItem.name}-${similarItem.type}" }
            ) { similarItem ->
                SimilarItemCard(
                    item = similarItem,
                    mediaRepository = mediaRepository,
                    onClick = {
                        similarItem.id?.let(onItemClick)
                    }
                )
            }
        }
    }
}

@Composable
private fun SimilarItemCard(
    item: BaseItemDto,
    mediaRepository: MediaRepository,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var imageUrl by remember(item.id, item.type, item.seriesId) { mutableStateOf<String?>(null) }

    LaunchedEffect(item.id, item.type, item.seriesId) {
        val imageItemId = when {
            item.type == "Episode" && !item.seriesId.isNullOrBlank() -> item.seriesId
            else -> item.id
        }
        imageUrl = imageItemId?.let { id ->
            mediaRepository.getImageUrl(
                itemId = id,
                imageType = "Primary",
                width = 320,
                height = 480,
                quality = 90
            ).first()
        }
    }

    Column(
        modifier = Modifier
            .width(116.dp)
            .clickable(
                enabled = !item.id.isNullOrBlank(),
                onClick = onClick
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(166.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2A2A2A)
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    JellyfinPosterImage(
                        imageUrl = imageUrl,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        context = context,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Movie,
                        contentDescription = item.name,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.name ?: "Unknown",
            fontSize = 12.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            lineHeight = 14.sp
        )

        val subtitle = item.productionYear?.toString()
            ?: item.type?.takeIf { it.isNotBlank() }

        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

@Composable
private fun DetailInfoRow(
    label: String,
    value: String,
    fillWidth: Boolean = true
) {
    Row(
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label  ",
            fontSize = 13.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TrackField(
    label: String,
    selectedOption: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    inlineMetaText: String? = null,
    singleValueFillWidth: Boolean = true,
    onOptionSelected: (String) -> Unit
) {
    if (options.isEmpty()) return

    Box(modifier = modifier) {
        if (options.size > 1) {
            OptionSelectorRow(
                label = label,
                selectedOption = selectedOption,
                options = options,
                inlineMetaText = inlineMetaText,
                onOptionSelected = onOptionSelected
            )
        } else {
            val value = if (!inlineMetaText.isNullOrBlank()) {
                "${options.first()} / $inlineMetaText"
            } else {
                options.first()
            }
            DetailInfoRow(
                label = label,
                value = value,
                fillWidth = singleValueFillWidth
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionSelectorRow(
    label: String,
    selectedOption: String,
    options: List<String>,
    inlineMetaText: String? = null,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label  ",
            fontSize = 13.sp,
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
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
                    .fillMaxWidth()
                    .heightIn(min = 38.dp),
                color = Color(0xFF1F1F24),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val displayText = buildString {
                        append(selectedOption.ifBlank { options.firstOrNull().orEmpty() })
                        if (!inlineMetaText.isNullOrBlank()) {
                            append(" / ")
                            append(inlineMetaText)
                        }
                    }
                    Text(
                        text = displayText,
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Select $label",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
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

private fun buildVideoInlineText(
    mediaSources: List<MediaSourceInfo>,
    streams: List<MediaStream>
): String? {
    val source = inlinePrimaryMediaSource(mediaSources)
    val parts = mutableListOf<String>()
    CodecUtils.getFileSize(source?.size)?.let(parts::add)

    val fileBitrate = source?.bitrate?.toLong()
        ?: streams
            .sumOf { (it.bitRate ?: 0).toLong() }
            .takeIf { it > 0L }
    formatBitrate(fileBitrate)?.let(parts::add)

    return parts.takeIf { it.isNotEmpty() }?.joinToString(" / ")
}

private fun inlinePrimaryMediaSource(sources: List<MediaSourceInfo>): MediaSourceInfo? {
    return sources.firstOrNull { source ->
        !source.name.isNullOrBlank() ||
            !source.container.isNullOrBlank() ||
            source.size != null ||
            source.bitrate != null ||
            source.mediaStreams.orEmpty().isNotEmpty()
    } ?: sources.firstOrNull()
}

private fun formatBitrate(bitsPerSecond: Int?): String? = formatBitrate(bitsPerSecond?.toLong())

private fun formatBitrate(bitsPerSecond: Long?): String? {
    val value = bitsPerSecond?.takeIf { it > 0L } ?: return null
    return if (value >= 1_000_000L) {
        "${String.format(Locale.US, "%.1f", value / 1_000_000.0)} Mbps"
    } else {
        "${value / 1000L} kbps"
    }
}

@Composable
private fun FavoriteActionButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.size(46.dp),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF1F1F24),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
            modifier = Modifier.size(20.dp),
            tint = if (isFavorite) Color(0xFFFF4D6D) else Color.White
        )
    }
}

private fun buildVideoOptions(streams: List<MediaStream>): List<String> {
    return OptionLabels(mediaStreamDisplayTitles(streams, "Video"))
}

private fun buildAudioOptions(streams: List<MediaStream>): List<String> {
    return OptionLabels(mediaStreamDisplayTitles(streams, "Audio"))
}

private fun buildSubtitleOptions(streams: List<MediaStream>): List<String> {
    val options = mutableListOf("Off")
    options += mediaStreamDisplayTitles(streams, "Subtitle")
    return OptionLabels(options)
}

private fun buildDefaultSubtitleOption(streams: List<MediaStream>): String {
    return defaultSubtitleDisplayTitle(streams)
}

private fun downloadFailureDialogMessage(
    state: ItemDownloadState? = null,
    rawMessage: String? = null
): String {
    state?.storageShortageInfo?.let { storage ->
        val fileSize = storage.fileSizeBytes?.let(::formatStorageBytesForDialog) ?: "Unknown"
        val available = formatStorageBytesForDialog(storage.availableBytes)
        val needed = formatStorageBytesForDialog(storage.neededBytes)
        return buildString {
            appendLine("Not enough storage space on this device.")
            appendLine()
            appendLine("File size: $fileSize")
            appendLine("Available: $available")
            append("Needed: $needed")
        }
    }

    val resolvedMessage = rawMessage?.trim().takeUnless { it.isNullOrBlank() }
        ?: state?.message?.trim().takeUnless { it.isNullOrBlank() }
    return resolvedMessage ?: "Download failed. Please try again."
}

private fun formatStorageBytesForDialog(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L).toDouble()
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        value >= gb -> String.format(Locale.US, "%.2f GB", value / gb)
        value >= mb -> String.format(Locale.US, "%.1f MB", value / mb)
        value >= kb -> String.format(Locale.US, "%.1f KB", value / kb)
        else -> "${bytes.coerceAtLeast(0L)} B"
    }
}

private fun AudioStreamIndex(
    streams: List<MediaStream>,
    selectedOption: String
): Int? {
    val audioStreams = streams
        .filter { it.type == "Audio" }
        .sortedBy { it.index ?: Int.MAX_VALUE }
    if (audioStreams.isEmpty()) return null
    if (selectedOption.isBlank()) return audioStreams.firstOrNull()?.index
    val audioOptions = buildAudioOptions(streams)
    val optionOrdinal = audioOptions.indexOf(selectedOption)
    if (optionOrdinal < 0 || optionOrdinal >= audioStreams.size) return null
    return audioStreams[optionOrdinal].index
}

private fun SubtitleStreamIndex(
    streams: List<MediaStream>,
    selectedOption: String
): Int? {
    if (selectedOption == "Off") return -1

    val subtitleStreams = streams
        .filter { it.type == "Subtitle" }
        .sortedBy { it.index ?: Int.MAX_VALUE }
    if (subtitleStreams.isEmpty()) return null
    if (selectedOption.isBlank()) return subtitleStreams.firstOrNull()?.index
    val subtitleOptions = buildSubtitleOptions(streams).drop(1)
    val optionOrdinal = subtitleOptions.indexOf(selectedOption)
    if (optionOrdinal < 0 || optionOrdinal >= subtitleStreams.size) return null
    return subtitleStreams[optionOrdinal].index
}

private fun AudioStreamIndex(
    streams: List<MediaStream>,
    streamIndex: Int?
): String? {
    if (streamIndex == null) return null

    val audioStreams = streams
        .filter { it.type == "Audio" }
        .sortedBy { it.index ?: Int.MAX_VALUE }
    val streamOrdinal = audioStreams.indexOfFirst { it.index == streamIndex }
    if (streamOrdinal < 0) return null
    return buildAudioOptions(streams).getOrNull(streamOrdinal)
}

private fun SubtitleStreamIndex(
    streams: List<MediaStream>,
    streamIndex: Int?
): String? {
    if (streamIndex == null) return null
    if (streamIndex == -1) return "Off"

    val subtitleStreams = streams
        .filter { it.type == "Subtitle" }
        .sortedBy { it.index ?: Int.MAX_VALUE }
    val streamOrdinal = subtitleStreams.indexOfFirst { it.index == streamIndex }
    if (streamOrdinal < 0) return null
    val subtitleOptions = buildSubtitleOptions(streams).drop(1)
    return subtitleOptions.getOrNull(streamOrdinal)
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

private fun OptionLabels(options: List<String>): List<String> {
    val counts = mutableMapOf<String, Int>()
    return options.map { option ->
        val seen = (counts[option] ?: 0) + 1
        counts[option] = seen
        if (seen == 1) option else "$option ($seen)"
    }
}

private data class SeriesSeasonSelectionDialogState(
    val availableBytes: Long,
    val options: List<StorageSelectionOption>,
    val episodesBySeasonId: Map<String, List<BaseItemDto>>
) {
    companion object {
        fun fromEstimate(estimate: BatchDownloadEstimate): SeriesSeasonSelectionDialogState {
            data class SeasonGroupSummary(
                val id: String,
                val title: String,
                val subtitle: String,
                val requiredBytes: Long,
                val seasonNumber: Int?,
                val episodes: List<BaseItemDto>
            )

            val grouped = estimate.candidates
                .filter { !it.item.id.isNullOrBlank() }
                .groupBy { candidate -> seasonGroupKey(candidate.item) }

            val groupedSummaries = grouped.map { (seasonId, candidates) ->
                val orderedCandidates = candidates.sortedWith(
                    compareBy<BatchDownloadCandidate>(
                        { it.item.parentIndexNumber ?: Int.MAX_VALUE },
                        { it.item.indexNumber ?: Int.MAX_VALUE },
                        { it.item.name.orEmpty() }
                    )
                )
                val firstItem = orderedCandidates.first().item
                val seasonNumber = firstItem.parentIndexNumber
                val title = when {
                    !firstItem.seasonName.isNullOrBlank() -> firstItem.seasonName.orEmpty()
                    seasonNumber != null -> "Season $seasonNumber"
                    else -> "Season"
                }

                val episodeCount = orderedCandidates.size
                val requiredBytes = orderedCandidates.sumOf { it.remainingBytes ?: 0L }

                SeasonGroupSummary(
                    id = seasonId,
                    title = title,
                    subtitle = episodeCountLabel(episodeCount),
                    requiredBytes = requiredBytes,
                    seasonNumber = seasonNumber,
                    episodes = orderedCandidates.map { it.item }
                )
            }.sortedWith(
                compareBy<SeasonGroupSummary>({ it.seasonNumber ?: Int.MAX_VALUE }, { it.title })
            )

            return SeriesSeasonSelectionDialogState(
                availableBytes = estimate.availableBytes,
                options = groupedSummaries.map { summary ->
                    StorageSelectionOption(
                        id = summary.id,
                        title = summary.title,
                        subtitle = summary.subtitle,
                        requiredBytes = summary.requiredBytes
                    )
                },
                episodesBySeasonId = groupedSummaries.associate { it.id to it.episodes }
            )
        }
    }
}

private fun seasonGroupKey(item: BaseItemDto): String {
    return item.seasonId?.takeIf { it.isNotBlank() }
        ?: item.parentId?.takeIf { it.isNotBlank() }
        ?: item.parentIndexNumber?.let { "season_$it" }
        ?: "season_unknown_${item.id.orEmpty()}"
}

private fun episodeCountLabel(count: Int): String {
    return "$count episode" + if (count == 1) "" else "s"
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
            onPlayClick = { _, _ -> }
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
            onPlayClick = { _, _ -> }
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
            onPlayClick = { _, _ -> }
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
            onPlayClick = { _, _ -> }
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
