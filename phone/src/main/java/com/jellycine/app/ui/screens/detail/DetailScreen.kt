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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.shared.R
import com.jellycine.shared.preferences.Preferences
import com.jellycine.shared.util.image.JellyfinPosterImage
import com.jellycine.shared.util.image.imageTagFor
import com.jellycine.data.model.BatchDownloadCandidate
import com.jellycine.data.model.BatchDownloadEstimate
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.BaseItemPerson
import com.jellycine.data.model.DownloadStatus
import com.jellycine.data.model.ItemDownloadState
import com.jellycine.data.model.MediaStream
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.flow.first
import android.content.res.Configuration
import com.jellycine.app.ui.screens.player.PlayerScreen
import com.jellycine.detail.CodecUtils
import com.jellycine.app.ui.components.common.DownloadActionMenu
import com.jellycine.app.ui.components.common.DownloadContent
import com.jellycine.shared.ui.components.common.CastSection
import com.jellycine.shared.ui.components.common.DetailDownloadActionButton
import com.jellycine.shared.ui.components.common.DetailDownloadActionState
import com.jellycine.shared.ui.components.common.activeDetailMediaSources
import com.jellycine.shared.ui.components.common.buildInlineText
import com.jellycine.shared.ui.components.common.buildLocalVersionEntries
import com.jellycine.shared.ui.components.common.DetailPlayActionButton
import com.jellycine.shared.ui.components.common.FavoriteActionButton
import com.jellycine.shared.ui.components.common.OverviewSection
import com.jellycine.shared.ui.components.common.selectedVideoOption
import com.jellycine.shared.ui.components.common.SimilarItemsSection
import com.jellycine.app.ui.components.common.ScreenCastButton
import com.jellycine.shared.ui.components.common.ScreenWrapper
import com.jellycine.shared.ui.components.common.ShimmerEffect
import com.jellycine.app.ui.components.common.canResumeDownloads
import com.jellycine.app.ui.components.common.hasActiveDownloads
import com.jellycine.app.ui.components.common.pausableItemIds
import com.jellycine.app.ui.components.common.isPausedDownloadState
import com.jellycine.app.ui.components.common.rememberDownloadPanelProgress
import com.jellycine.app.ui.components.common.rememberDownloadPanelState
import com.jellycine.app.cast.CastController
import com.jellycine.app.download.DownloadRepositoryProvider
import com.jellycine.app.ui.screens.cast.CastPlayback
import com.jellycine.app.ui.screens.cast.loadCastPlaybackData
import com.jellycine.app.ui.screens.cast.activeCastArtworkUrl
import com.jellycine.player.core.defaultSubtitleDisplayTitle
import com.jellycine.player.core.mediaStreamDisplayTitles
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.shared.playback.PlaybackRefreshSignals
import com.jellycine.app.ui.components.common.DetailBackdropHero
import com.jellycine.app.ui.components.common.DetailBackdropHeroStyle
import com.jellycine.app.ui.components.common.containerHeightDp
import com.jellycine.app.ui.components.common.containerWidthDp
import com.jellycine.app.ui.components.common.detailContentMaxWidth
import com.jellycine.app.ui.components.common.detailActionWidth
import com.jellycine.app.ui.components.common.isTabletDetailLayout
import com.jellycine.app.ui.components.common.isTabletLayout
import java.util.Locale
import androidx.media3.common.util.UnstableApi
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch

private val heroOverlayGradient = arrayOf(
    0.0f to Color.Transparent,
    0.62f to Color.Transparent,
    0.78f to Color.Black.copy(alpha = 0.08f),
    0.88f to Color.Black.copy(alpha = 0.18f),
    0.95f to Color.Black.copy(alpha = 0.30f),
    1.0f to Color.Black.copy(alpha = 0.46f)
)

private val heroBottomFadeGradient = arrayOf(
    0.0f to Color.Transparent,
    0.12f to Color.Transparent,
    0.34f to Color.Black.copy(alpha = 0.05f),
    0.56f to Color.Black.copy(alpha = 0.16f),
    0.74f to Color.Black.copy(alpha = 0.34f),
    0.88f to Color.Black.copy(alpha = 0.62f),
    0.96f to Color.Black.copy(alpha = 0.86f),
    1.0f to Color.Black
)

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
    val authRepository = remember { AuthRepositoryProvider.getInstance(context) }
    val seerrRepository = rememberSeerrRepository(context)
    val playerPreferences = remember { PlayerPreferences(context) }
    val activeServerId by authRepository.getActiveServerId()
        .collectAsState(initial = authRepository.getActiveSessionSnapshot().activeServerId)

    var item by remember { mutableStateOf<BaseItemDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPlayer by remember { mutableStateOf(false) }
    var playbackItemId by remember { mutableStateOf<String?>(null) }
    var availablePreviousEpisodeId by remember { mutableStateOf<String?>(null) }
    var availableNextEpisodeId by remember { mutableStateOf<String?>(null) }
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
    val latestPlaybackStopEvent by PlaybackRefreshSignals.latestStopEvent.collectAsState()

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
                    playerPreferences.setPreferredSubtitleStreamIndex(
                        castItemId,
                        subtitleStreamIndex
                    )
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
                preferredAudioStreamIndex =
                    playerPreferences.getPreferredAudioStreamIndex(playedItemId)
                preferredSubtitleStreamIndex =
                    playerPreferences.getPreferredSubtitleStreamIndex(playedItemId)
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

    fun playEpisode(episodeId: String) {
        playbackItemId = episodeId
        availablePreviousEpisodeId = null
        availableNextEpisodeId = null
        if (currentScreen == "episode") {
            episodeDetailId = episodeId
        }
    }

    fun selectLocalVersion(selectedItemId: String) {
        val selectingEpisode = currentScreen == "episode" && episodeItem != null
        val currentItemId = if (selectingEpisode) episodeItem?.id else item?.id
        if (selectedItemId.isBlank() || selectedItemId == currentItemId) return

        scope.launch {
            if (selectingEpisode) {
                isEpisodeLoading = true
                episodeError = null
            } else {
                isLoading = true
                error = null
            }

            mediaRepository.getItemById(selectedItemId).fold(
                onSuccess = { selectedItem ->
                    if (selectingEpisode) {
                        episodeItem = selectedItem
                        isEpisodeLoading = false
                    } else {
                        item = selectedItem
                        isLoading = false
                    }
                },
                onFailure = { exception ->
                    if (selectingEpisode) {
                        episodeError = exception.message
                        isEpisodeLoading = false
                    } else {
                        error = exception.message
                        isLoading = false
                    }
                }
            )
        }
    }

    LaunchedEffect(itemId, activeServerId) {
        try {
            isLoading = true
            error = null

            if (isSeerDetailItemId(itemId) && activeServerId.isNullOrBlank()) {
                error = context.getString(R.string.detail_seerr_not_connected)
                item = null
                isLoading = false
                return@LaunchedEffect
            }

            val result = loadDetailItem(
                itemId = itemId,
                activeServerId = activeServerId,
                mediaRepository = mediaRepository,
                seerrRepository = seerrRepository,
                seerrNotConnectedMessage = context.getString(R.string.detail_seerr_not_connected)
            )
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

    LaunchedEffect(showPlayer, playbackItemId, itemId) {
        if (!showPlayer) {
            availablePreviousEpisodeId = null
            availableNextEpisodeId = null
            return@LaunchedEffect
        }

        val activePlaybackId = playbackItemId ?: itemId
        val episodeNavigationIds = mediaRepository.getEpisodeNavigationIds(activePlaybackId)
        availablePreviousEpisodeId = episodeNavigationIds.previousEpisodeId
        availableNextEpisodeId = episodeNavigationIds.nextEpisodeId
    }

    LaunchedEffect(latestPlaybackStopEvent?.timestampMs) {
        val playbackStopEvent = latestPlaybackStopEvent ?: return@LaunchedEffect
        val refreshedItemId = playbackStopEvent.itemId ?: return@LaunchedEffect

        if (item?.id == refreshedItemId || itemId == refreshedItemId) {
            mediaRepository.getItemById(refreshedItemId).getOrNull()?.let { refreshedItem ->
                item = refreshedItem
            }
        }

        if (episodeItem?.id == refreshedItemId || episodeDetailId == refreshedItemId) {
            mediaRepository.getItemById(refreshedItemId).getOrNull()?.let { refreshedEpisode ->
                episodeItem = refreshedEpisode
            }
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
            val activePlaybackId = playbackItemId ?: itemId
            val initialPlaybackItemDetails = when (activePlaybackId) {
                item?.id -> item
                episodeItem?.id -> episodeItem
                else -> null
            }
            PlayerScreen(
                mediaId = activePlaybackId,
                initialItemDetails = initialPlaybackItemDetails,
                preferredAudioStreamIndex = preferredAudioStreamIndex,
                preferredSubtitleStreamIndex = preferredSubtitleStreamIndex,
                onPreferredStreamIndexesChanged = { audioStreamIndex, subtitleStreamIndex ->
                    preferredAudioStreamIndex = audioStreamIndex
                    preferredSubtitleStreamIndex = subtitleStreamIndex
                },
                onBackPressed = {
                    val playedItemId = playbackItemId ?: itemId
                    preferredAudioStreamIndex =
                        playerPreferences.getPreferredAudioStreamIndex(playedItemId)
                    preferredSubtitleStreamIndex =
                        playerPreferences.getPreferredSubtitleStreamIndex(playedItemId)
                    trackSelectionSyncVersion += 1
                    showPlayer = false
                    playbackItemId = null
                },
                previousEpisodeId = availablePreviousEpisodeId,
                onWatchPreviousEpisode = ::playEpisode,
                nextEpisodeId = availableNextEpisodeId,
                onWatchNextEpisode = ::playEpisode,
                onPlaybackCompleted = { completedItemId ->
                    scope.launch {
                        val nextEpisodeId = mediaRepository.getNextEpisodeId(completedItemId) ?: return@launch
                        playEpisode(nextEpisodeId)
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
                                    onVersionItemSelected = ::selectLocalVersion,
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
                                            onVersionItemSelected = ::selectLocalVersion,
                                            onPersonClick = { personId ->
                                                onNavigateToPerson(personId)
                                            },
                                            onCastButtonClick = { openCastingDisplay() },
                                            onSeasonClick = { seriesId, seasonId, seasonName ->
                                                seasonDetailData =
                                                    Triple(seriesId, seasonId, seasonName)
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
    onVersionItemSelected: (String) -> Unit = {},
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
        onVersionItemSelected = onVersionItemSelected,
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
    onVersionItemSelected: (String) -> Unit = {},
    onPersonClick: (String) -> Unit = {},
    onCastButtonClick: () -> Unit = {},
    onSeasonClick: (String, String, String?) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val authRepository = remember { AuthRepositoryProvider.getInstance(context) }
    val seerrRepository = rememberSeerrRepository(context)
    val downloadRepository = remember { DownloadRepositoryProvider.getInstance(context) }
    val playerPreferences = remember { PlayerPreferences(context) }
    val preferences = remember { Preferences(context) }
    val coroutineScope = rememberCoroutineScope()
    val castPlaybackState by CastController.playbackState.collectAsState()
    val activeServerId by authRepository.getActiveServerId()
        .collectAsState(initial = authRepository.getActiveSessionSnapshot().activeServerId)
    val screenWidthDp = containerWidthDp()
    val screenHeightDp = containerHeightDp()
    val isWidescreenLayout = isTabletLayout(screenWidthDp)
    val useTabletBackdropLayout = isTabletDetailLayout(
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp
    )
    val isSeerDetail = item.isSeerDetailItem()
    val mergeVersionsEnabled by preferences.MergeVersionsEnabled()
        .collectAsState(initial = preferences.isMergeVersionsEnabled())
    val metadataScrollState = rememberScrollState()
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
    var logoImageUrl by remember { mutableStateOf<String?>(null) }
    var logoResolved by remember { mutableStateOf(false) }
    var logoLookup by remember { mutableStateOf(true) }
    var logoLoadError by remember { mutableStateOf(false) }
    val activeMediaSources = remember(item.id, item.mediaSources) {
        item.activeDetailMediaSources()
    }
    val effectiveMediaStreams = remember(item.mediaStreams, activeMediaSources) {
        val fromSources = activeMediaSources.flatMap { it.mediaStreams.orEmpty() }
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
    var selectedAudio by rememberSaveable(item.id, trackSelectionSyncVersion) {
        mutableStateOf(
            initialAudioOption
        )
    }
    var selectedSubtitle by rememberSaveable(item.id, trackSelectionSyncVersion) {
        mutableStateOf(
            initialSubtitleOption
        )
    }
    val runtimeTicks = item.runTimeTicks
    val playbackPositionTicks = item.userData?.playbackPositionTicks ?: 0L
    val isPartiallyWatched =
        runtimeTicks != null && playbackPositionTicks > 0L && playbackPositionTicks < runtimeTicks
    val playButtonText = if (isPartiallyWatched) {
        val remainingTicks = (runtimeTicks - playbackPositionTicks).coerceAtLeast(0L)
        "${CodecUtils.formatRuntime(remainingTicks)} left"
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
    val reserveLogoSpace = isLoading || (!logoImageUrl.isNullOrBlank() && !logoLoadError) || logoLookup
    val showTitleFallback = !isLoading && !logoLookup && (logoImageUrl.isNullOrBlank() || logoLoadError)
    val genresText = remember(item.genres) {
        item.genres?.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }
    val descriptionTagline = remember(item.taglines) {
        item.taglines?.firstOrNull { !it.isNullOrBlank() }
    }
    val directors = remember(item.people, isSeerDetail) {
        val directorPeople = item.people?.filter { person ->
            person.isCreditType("Director")
        }.orEmpty()
        if (directorPeople.isNotEmpty() || !isSeerDetail) {
            directorPeople
        } else {
            item.people?.filter { person ->
                person.isCreditType("Creator")
            }.orEmpty()
        }
    }
    val directorCreditLabel = remember(directors) {
        if (directors.any { person -> person.isCreditType("Director") }) {
            "Director:"
        } else {
            "Creator:"
        }
    }
    val hasDescriptionContent = !item.overview.isNullOrBlank() || !descriptionTagline.isNullOrBlank()
    val canDownloadItem = item.id != null && item.canDownload != false && !isSeerDetail
    val pausedDownloadMessage = stringResource(R.string.downloads_status_paused)
    val itemDownloadStateFlow = if (isSeerDetail) null else item.id?.let { downloadRepository.observeItemDownload(it) }
    val itemDownloadState by (itemDownloadStateFlow?.collectAsState()
        ?: remember(item.id) { mutableStateOf(ItemDownloadState()) })
    val isPausedDownload = isPausedDownloadState(itemDownloadState, pausedDownloadMessage)
    val hasActiveDownload = itemDownloadState.status == DownloadStatus.DOWNLOADING ||
            itemDownloadState.status == DownloadStatus.QUEUED
    var downloadErrorDialogMessage by remember(item.id) { mutableStateOf<String?>(null) }
    var downloadActionMenu by remember(
        item.id,
        itemDownloadState.status,
        itemDownloadState.message
    ) {
        mutableStateOf(false)
    }
    var previousDownloadStatus by remember(item.id) { mutableStateOf(itemDownloadState.status) }
    var seriesQueueInProgress by remember(item.id) { mutableStateOf(false) }
    var seriesStorageSelectionDialogState by remember(item.id) {
        mutableStateOf<SeriesSeasonSelectionDialogState?>(
            null
        )
    }
    val trackedDownloads by downloadRepository.observeTrackedDownloads()
        .collectAsState(initial = emptyList())
    var isFavorite by remember(item.id, item.userData?.isFavorite) {
        mutableStateOf(item.userData?.isFavorite == true)
    }
    val seerrRelatedItems = seerrRelatedItemsState(
        item = item,
        isSeerDetail = isSeerDetail,
        activeServerId = activeServerId,
        mediaRepository = mediaRepository,
        seerrRepository = seerrRepository
    )
    var moreFromSeasonEpisodes by remember(item.id, item.seriesId, item.seasonId) {
        mutableStateOf<List<BaseItemDto>>(emptyList())
    }
    var localVersions by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    val seerrRequestState = seerrRequestState(
        item = item,
        isSeerDetail = isSeerDetail,
        activeServerId = activeServerId,
        seerrRepository = seerrRepository,
        coroutineScope = coroutineScope
    )
    val seriesDownloadEntries = remember(item.id, item.type, trackedDownloads) {
        val seriesId = item.id
        if (isSeerDetail || item.type != "Series" || seriesId.isNullOrBlank()) {
            emptyList()
        } else {
            trackedDownloads.filter { it.item?.seriesId == seriesId }
        }
    }
    val seriesDownload = rememberDownloadPanelState(entries = seriesDownloadEntries)
    val hasActiveSeriesDownloads = seriesDownload.hasActiveDownloads
    val canResumeSeriesDownloads = seriesDownload.canResumeDownloads
    var seriesDownloadActionMenu by remember(
        item.id,
        seriesDownload.status,
        seriesDownload.activeItemIds.size,
        seriesDownload.pausedItemIds.size
    ) { mutableStateOf(false) }

    fun toggleFavorite() {
        if (isSeerDetail) return
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
            DownloadStatus.QUEUED -> itemDownloadState.progress.coerceIn(0f, 0.99f)
            DownloadStatus.DOWNLOADING -> itemDownloadState.progress.coerceIn(0f, 0.99f)
            DownloadStatus.COMPLETED -> 1f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 350),
        label = "detail_download_progress"
    )
    val animatedSeriesDownloadProgress = rememberDownloadPanelProgress(
        panelState = seriesDownload,
        label = "series_download_progress"
    )
    val layout = detailScreenLayoutSpec(
        isWidescreenLayout = isWidescreenLayout,
        useTabletBackdropLayout = useTabletBackdropLayout,
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp
    )
    val detailActionButtonHeight = if (useTabletBackdropLayout) 40.dp else 46.dp
    val contentFadeStart = if (useTabletBackdropLayout && layout.backdropHeight.value > 0f) {
        (
            ((layout.contentTopPadding + layout.logoContainerHeight) - 56.dp).value /
                layout.backdropHeight.value
            ).coerceIn(0f, 1f)
    } else {
        null
    }
    val onBackdropLoadError: (Boolean) -> Unit = { hasError ->
        if (
            hasError &&
            backdropImageUrl == heroImageCandidates.getOrNull(heroImageIndex) &&
            heroImageIndex < heroImageCandidates.lastIndex
        ) {
            heroImageIndex += 1
        }
    }
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
            return@LaunchedEffect
        }
        heroImageCandidates = heroImageCandidates(
            item = item,
            mediaRepository = mediaRepository
        )
        heroImageIndex = 0
    }

    LaunchedEffect(item.id, activeServerId) {
        if (item.id == null) {
            logoLookup = false
            return@LaunchedEffect
        }
        if (logoResolved) {
            logoLookup = false
            return@LaunchedEffect
        }

        logoLookup = true
        logoLoadError = false
        try {
            val nextLogoImageUrl = detailLogoImage(
                item = item,
                activeServerId = activeServerId,
                isSeerDetail = isSeerDetail,
                mediaRepository = mediaRepository,
                seerrRepository = seerrRepository
            )
            if (nextLogoImageUrl != logoImageUrl) {
                logoImageUrl = nextLogoImageUrl
            }
            logoResolved = true
        } finally {
            logoLookup = false
        }
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

    LaunchedEffect(item.id, item.type, isSeerDetail, mergeVersionsEnabled) {
        val supportsLocalVersions = item.type.equals("Movie", ignoreCase = true) ||
            item.type.equals("Episode", ignoreCase = true)
        if (
            !mergeVersionsEnabled ||
            isSeerDetail ||
            !supportsLocalVersions ||
            item.id.isNullOrBlank()
        ) {
            localVersions = emptyList()
            return@LaunchedEffect
        }

        mediaRepository.getLocalVersions(item)
            .getOrNull()
            ?.filter { version -> !version.id.isNullOrBlank() }
            ?.let { versions ->
                val hasCurrentVersionList = localVersions.any { version -> version.id == item.id }
                if (versions.size > 1 || !hasCurrentVersionList) {
                    localVersions = versions
                }
            }
    }

    val moreFromSeasonTitle = remember(item.seasonName, item.parentIndexNumber) {
        val seasonLabel = item.parentIndexNumber?.let { "Season $it" }
            ?: item.seasonName?.takeIf { it.isNotBlank() }
            ?: "Season"
        "More from $seasonLabel"
    }

    val baseVideoOptions = remember(effectiveMediaStreams) { buildVideoOptions(effectiveMediaStreams) }
    val videoFallbackLabel = stringResource(R.string.detail_video_fallback)
    val smallFileSizeLabel = stringResource(R.string.detail_file_size_under_1_mb)
    val localVersionEntries = remember(localVersions, item.id, videoFallbackLabel, smallFileSizeLabel) {
        buildLocalVersionEntries(
            localVersions = localVersions,
            currentItemId = item.id,
            videoFallbackLabel = videoFallbackLabel,
            smallFileSizeLabel = smallFileSizeLabel
        )
    }
    val localVersionOptions = localVersionEntries.map { (label, _) -> label }
    val videoOptions = localVersionOptions.ifEmpty { baseVideoOptions }
    val displayedSelectedVideo = selectedVideoOption(
        localVersionEntries = localVersionEntries,
        currentItemId = item.id,
        selectedVideo = selectedVideo,
        videoOptions = videoOptions,
        baseVideoOptions = baseVideoOptions
    )
    val audioOptions = remember(effectiveMediaStreams) { buildAudioOptions(effectiveMediaStreams) }
    val subtitleOptions =
        remember(effectiveMediaStreams) { buildSubtitleOptions(effectiveMediaStreams) }
    val defaultSubtitleOption =
        remember(effectiveMediaStreams) { buildDefaultSubtitleOption(effectiveMediaStreams) }
    val codecBadges = CodecBadges(
        streams = effectiveMediaStreams,
        selectedVideo = displayedSelectedVideo,
        selectedAudio = selectedAudio
    )
    val videoInlineMetaText = remember(
        activeMediaSources,
        effectiveMediaStreams,
        localVersionOptions
    ) {
        if (localVersionOptions.isNotEmpty()) {
            null
        } else {
            buildInlineText(
                mediaSources = activeMediaSources,
                streams = effectiveMediaStreams,
                smallFileSizeLabel = smallFileSizeLabel
            )
        }
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
        val selectedVersion = localVersionEntries.firstOrNull { (label, _) -> label == option }?.second
        val selectedVersionId = selectedVersion?.id
        if (selectedVersionId != null && selectedVersionId != item.id) {
            onVersionItemSelected(selectedVersionId)
        } else {
            selectedVideo = option
        }
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

    LaunchedEffect(displayedSelectedVideo, audioOptions, subtitleOptions, defaultSubtitleOption) {
        if (selectedVideo != displayedSelectedVideo) selectedVideo = displayedSelectedVideo
        if (selectedAudio !in audioOptions) {
            selectedAudio = audioOptions.firstOrNull().orEmpty()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (useTabletBackdropLayout) {
            DetailBackdropHero(
                imageUrl = backdropImageUrl,
                contentDescription = item.name,
                heroHeight = layout.backdropHeight,
                style = DetailBackdropHeroStyle.TabletBackdrop,
                bottomFadeHeight = 0.dp,
                contentFadeStartFraction = contentFadeStart,
                onErrorStateChange = onBackdropLoadError
            ) {
                if (!isSeerDetail) {
                    DetailHeroCastButtonOverlay(onCastButtonClick = onCastButtonClick)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(if (useTabletBackdropLayout) Color.Transparent else Color.Black),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {

            if (!useTabletBackdropLayout) {
                item {
                    DetailBackdropHero(
                        imageUrl = backdropImageUrl,
                        contentDescription = item.name,
                        heroHeight = layout.heroHeight,
                        bottomFadeHeight = 156.dp,
                        overlayGradient = heroOverlayGradient,
                        bottomFadeGradient = heroBottomFadeGradient,
                        onErrorStateChange = onBackdropLoadError
                    ) {
                        if (!isSeerDetail) {
                            DetailHeroCastButtonOverlay(onCastButtonClick = onCastButtonClick)
                        }
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = layout.horizontalPadding)
                        .padding(top = layout.contentTopPadding)
                        .offset(y = layout.headerOffset)
                ) {
                    Column(
                        modifier = if (layout.contentMaxWidth != null) {
                            Modifier
                                .fillMaxWidth()
                                .widthIn(max = layout.contentMaxWidth)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    ) {
                        if (reserveLogoSpace || showTitleFallback) {
                            Box(
                                modifier = Modifier
                                    .height(layout.logoContainerHeight)
                                    .fillMaxWidth()
                            ) {
                                if (!logoImageUrl.isNullOrBlank() && !logoLoadError) {
                                    JellyfinPosterImage(
                                        imageUrl = logoImageUrl,
                                        contentDescription = item.name,
                                        modifier = Modifier
                                            .fillMaxWidth(0.94f)
                                            .height(layout.logoContainerHeight)
                                            .align(Alignment.CenterStart),
                                        context = context,
                                        contentScale = ContentScale.Fit,
                                        alignment = Alignment.CenterStart,
                                        onErrorStateChange = { hasError ->
                                            logoLoadError = hasError
                                        }
                                    )
                                } else if (showTitleFallback) {
                                    Text(
                                        text = logoFallbackTitle,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        lineHeight = 30.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .offset(y = (-2).dp)
                                    )
                                }
                            }
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

                            val officialRatingLabel =
                                item.officialRating?.takeIf { it.isNotBlank() } ?: "NR"
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

                        if (castPlaybackState.isConnected && !isSeerDetail) {
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
                                            icon = if (codecBadges.hdrBadgeText.equals(
                                                    "Dolby Vision",
                                                    ignoreCase = true
                                                )
                                            ) null else Icons.Rounded.HdrOn,
                                            customIcon = if (codecBadges.hdrBadgeText.equals(
                                                    "Dolby Vision",
                                                    ignoreCase = true
                                                )
                                            ) R.drawable.ic_dolby_logo else null,
                                            iconTintUnspecified = codecBadges.hdrBadgeText.equals(
                                                "Dolby Vision",
                                                ignoreCase = true
                                            )
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

                        if (isWidescreenLayout && hasDescriptionContent) {
                            OverviewSection(
                                overview = item.overview,
                                tagline = descriptionTagline,
                                modifier = Modifier.padding(top = 14.dp)
                            )
                        }

                        if (isWidescreenLayout && isSeerDetail) {
                            SeerrRequestButton(
                                state = seerrRequestState,
                                modifier = Modifier
                                    .fillMaxWidth(
                                        detailActionWidth(
                                            screenWidthDp,
                                            useTabletLayout = useTabletBackdropLayout
                                        )
                                    )
                                    .padding(top = if (hasDescriptionContent) 12.dp else 14.dp)
                                    .height(detailActionButtonHeight)
                            )
                        }

                        if (isWidescreenLayout && directors.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = directorCreditLabel,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                DirectorNamesRow(
                                    directors = directors,
                                    onPersonClick = onPersonClick
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(layout.logoBottomSpacing))

                        val hasVideoSection = videoOptions.isNotEmpty()
                        val hasAudioSection = audioOptions.isNotEmpty()
                        val hasSubtitleSection = subtitleOptions.size > 1
                        val trackFieldCount =
                            listOf(hasVideoSection, hasAudioSection, hasSubtitleSection)
                                .count { it }
                        val tabletTrackFieldMaxWidth = when (trackFieldCount) {
                            3 -> 230.dp
                            2 -> 300.dp
                            else -> 360.dp
                        }

                        if (isWidescreenLayout) {
                            if (hasVideoSection || hasAudioSection || hasSubtitleSection) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (hasVideoSection) {
                                        val sharesRow = hasSubtitleSection || hasAudioSection
                                        TrackField(
                                            modifier = if (sharesRow) {
                                                Modifier.widthIn(max = tabletTrackFieldMaxWidth)
                                            } else {
                                                Modifier.fillMaxWidth()
                                            },
                                            label = "Video",
                                            selectedOption = displayedSelectedVideo,
                                            options = videoOptions,
                                            inlineMetaText = videoInlineMetaText,
                                            singleValueFillWidth = !sharesRow,
                                            onOptionSelected = onVideoOptionSelected
                                        )
                                    }

                                    if (hasAudioSection) {
                                        val sharesRow = hasSubtitleSection || hasVideoSection
                                        TrackField(
                                            modifier = if (sharesRow) {
                                                Modifier.widthIn(max = tabletTrackFieldMaxWidth)
                                            } else {
                                                Modifier.fillMaxWidth()
                                            },
                                            label = "Audio",
                                            selectedOption = selectedAudio,
                                            options = audioOptions,
                                            singleValueFillWidth = !sharesRow,
                                            onOptionSelected = onAudioOptionSelected
                                        )
                                    }

                                    if (hasSubtitleSection) {
                                        val sharesRow = hasVideoSection || hasAudioSection
                                        TrackField(
                                            modifier = if (sharesRow) {
                                                Modifier.widthIn(max = tabletTrackFieldMaxWidth)
                                            } else {
                                                Modifier.fillMaxWidth()
                                            },
                                            label = "Subtitles",
                                            selectedOption = selectedSubtitle,
                                            options = subtitleOptions,
                                            singleValueFillWidth = !sharesRow,
                                            onOptionSelected = onSubtitleOptionSelected
                                        )
                                    }

                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        } else {
                            if (hasVideoSection) {
                                TrackField(
                                    label = "Video",
                                    selectedOption = displayedSelectedVideo,
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

                        if (item.type != "Series" && !isSeerDetail) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(
                                        detailActionWidth(
                                            screenWidthDp,
                                            useTabletLayout = useTabletBackdropLayout
                                        )
                                    )
                                    .padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(detailActionButtonHeight)
                                        .clip(RoundedCornerShape(24.dp))
                                ) {
                                    DetailPlayActionButton(
                                        text = playButtonText,
                                        isPartiallyWatched = isPartiallyWatched,
                                        resumeProgress = resumeProgress,
                                        onClick = {
                                            val (selectedAudioStreamIndex, selectedSubtitleStreamIndex) = persistTrackSelection(
                                                audioOption = selectedAudio,
                                                subtitleOption = selectedSubtitle
                                            )
                                            onPlayClick(
                                                selectedAudioStreamIndex,
                                                selectedSubtitleStreamIndex
                                            )
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(detailActionButtonHeight)
                                ) {
                                    val downloadActionState = when {
                                        !canDownloadItem -> DetailDownloadActionState.Unavailable
                                        itemDownloadState.status == DownloadStatus.COMPLETED -> DetailDownloadActionState.Completed
                                        itemDownloadState.status == DownloadStatus.DOWNLOADING -> DetailDownloadActionState.Downloading
                                        isPausedDownload -> DetailDownloadActionState.Paused
                                        itemDownloadState.status == DownloadStatus.QUEUED -> DetailDownloadActionState.Queued
                                        else -> DetailDownloadActionState.Idle
                                    }

                                    DetailDownloadActionButton(
                                        state = downloadActionState,
                                        progress = animatedDownloadProgress,
                                        onClick = {
                                            when {
                                                !canDownloadItem -> Unit
                                                hasActiveDownload -> downloadActionMenu = true
                                                else -> {
                                                    coroutineScope.launch {
                                                        downloadRepository.enqueueItemDownload(item)
                                                            .onFailure { throwable ->
                                                                downloadErrorDialogMessage =
                                                                    downloadFailureDialogMessage(
                                                                        rawMessage = throwable.message
                                                                    )
                                                            }
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    DownloadActionMenu(
                                        expanded = downloadActionMenu,
                                        canResume = isPausedDownload,
                                        hasActiveDownloads = hasActiveDownload,
                                        onDismissRequest = { downloadActionMenu = false },
                                        onPauseResume = {
                                            downloadActionMenu = false
                                            item.id?.let { itemId ->
                                                if (isPausedDownload) {
                                                    downloadRepository.resumeDownload(itemId)
                                                } else {
                                                    downloadRepository.pauseDownload(itemId)
                                                }
                                            }
                                        },
                                        onCancel = {
                                            downloadActionMenu = false
                                            item.id?.let(downloadRepository::cancelDownload)
                                        }
                                    )
                                }

                                if (!isSeerDetail) {
                                    FavoriteActionButton(
                                        isFavorite = isFavorite,
                                        onClick = ::toggleFavorite
                                    )
                                }
                            }
                        }

                        if (!isWidescreenLayout && hasDescriptionContent) {
                            OverviewSection(
                                overview = item.overview,
                                tagline = descriptionTagline,
                                modifier = Modifier.padding(top = 18.dp)
                            )
                        }

                        if (!isWidescreenLayout && isSeerDetail) {
                            SeerrRequestButton(
                                state = seerrRequestState,
                                modifier = Modifier
                                    .fillMaxWidth(
                                        detailActionWidth(
                                            screenWidthDp,
                                            useTabletLayout = useTabletBackdropLayout
                                        )
                                    )
                                    .padding(top = if (hasDescriptionContent) 14.dp else 18.dp)
                                    .height(detailActionButtonHeight)
                            )
                        }

                        if (!isWidescreenLayout && directors.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = directorCreditLabel,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                DirectorNamesRow(
                                    directors = directors,
                                    onPersonClick = onPersonClick
                                )
                            }
                        }

                        if (isEpisode) {
                            MoreFromSeasonSection(
                                episodes = moreFromSeasonEpisodes,
                                mediaRepository = mediaRepository,
                                title = moreFromSeasonTitle,
                                onEpisodeClick = onSimilarItemClick
                            )
                        }

                        if (item.type == "Series" && !isSeerDetail) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(
                                        detailActionWidth(
                                            screenWidthDp,
                                            useTabletLayout = useTabletBackdropLayout
                                        )
                                    )
                                    .padding(top = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(detailActionButtonHeight)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            when {
                                                hasActiveSeriesDownloads -> seriesDownloadActionMenu =
                                                    true

                                                else -> {
                                                    coroutineScope.launch {
                                                        val seriesId = item.id
                                                        if (seriesId.isNullOrBlank()) {
                                                            return@launch
                                                        }
                                                        seriesQueueInProgress = true
                                                        try {
                                                            val estimateResult =
                                                                downloadRepository.buildSeriesDownloadEstimate(
                                                                    seriesId
                                                                )
                                                            estimateResult.fold(
                                                                onSuccess = { estimate ->
                                                                    seriesStorageSelectionDialogState =
                                                                        SeriesSeasonSelectionDialogState.fromEstimate(
                                                                            estimate
                                                                        )
                                                                },
                                                                onFailure = { throwable ->
                                                                    downloadErrorDialogMessage =
                                                                        downloadFailureDialogMessage(
                                                                            rawMessage = throwable.message
                                                                        )
                                                                }
                                                            )
                                                        } finally {
                                                            seriesQueueInProgress = false
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                        shape = RoundedCornerShape(24.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            Color.White.copy(alpha = 0.18f)
                                        ),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color(0xFF1F1F24),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        DownloadContent(
                                            panelState = seriesDownload,
                                            isQueueing = seriesQueueInProgress,
                                            progress = animatedSeriesDownloadProgress,
                                            idleLabelRes = R.string.downloads_action_download_series,
                                            fontSize = 14.sp,
                                            iconSize = 18.dp,
                                            progressSize = 18.dp
                                        )
                                    }

                                    DownloadActionMenu(
                                        expanded = seriesDownloadActionMenu,
                                        canResume = canResumeSeriesDownloads,
                                        hasActiveDownloads = hasActiveSeriesDownloads,
                                        onDismissRequest = { seriesDownloadActionMenu = false },
                                        onPauseResume = {
                                            seriesDownloadActionMenu = false
                                            if (canResumeSeriesDownloads) {
                                                seriesDownload.pausedItemIds.forEach(
                                                    downloadRepository::resumeDownload
                                                )
                                            } else {
                                                seriesDownload.pausableItemIds.forEach(
                                                    downloadRepository::pauseDownload
                                                )
                                            }
                                        },
                                        onCancel = {
                                            seriesDownloadActionMenu = false
                                            seriesDownload.activeItemIds.forEach(downloadRepository::cancelDownload)
                                        }
                                    )
                                }

                                if (!isSeerDetail) {
                                    FavoriteActionButton(
                                        isFavorite = isFavorite,
                                        onClick = ::toggleFavorite
                                    )
                                }
                            }

                            item.id?.let { seriesId ->
                                SeasonsSection(
                                    series = item,
                                    seriesId = seriesId,
                                    mediaRepository = mediaRepository,
                                    seerrRepository = seerrRepository,
                                    activeServerId = activeServerId,
                                    refreshKey = seerrRequestState.seasonRefreshKey,
                                    loadingSeasonNumber = seerrRequestState.pendingSeasonRequestNumber
                                        ?.takeIf { seerrRequestState.isBusy },
                                    onSeasonRequest = { seasonNumber ->
                                        seerrRequestState.onLoadRequestOptions(seasonNumber)
                                    },
                                    onSeasonClick = onSeasonClick
                                )
                            }
                        }

                        CastSection(
                            item = item,
                            mediaRepository = mediaRepository,
                            onPersonClick = onPersonClick
                        )

                        val primaryDirector = directors.firstOrNull()
                        val seerrDirectorItems = seerrDirectorItemsState(
                            item = item,
                            directors = directors,
                            isSeerDetail = isSeerDetail,
                            activeServerId = activeServerId,
                            mediaRepository = mediaRepository,
                            seerrRepository = seerrRepository
                        )

                        if (
                            primaryDirector != null &&
                            (seerrDirectorItems.localDirectorItems.isNotEmpty() ||
                                seerrDirectorItems.seerrDirectorItems.isNotEmpty())
                        ) {
                            SimilarItemsSection(
                                similarItems = seerrDirectorItems.localDirectorItems,
                                seerrItems = seerrDirectorItems.seerrDirectorItems,
                                mediaRepository = mediaRepository,
                                onItemClick = onSimilarItemClick,
                                title = "Directed by ${primaryDirector.name}"
                            )
                        }

                        SimilarItemsSection(
                            similarItems = emptyList(),
                            seerrItems = seerrRelatedItems.seerrRecommendedItems,
                            mediaRepository = mediaRepository,
                            onItemClick = onSimilarItemClick,
                            title = stringResource(R.string.detail_seerr_recommendations_title)
                        )

                        SimilarItemsSection(
                            similarItems = seerrRelatedItems.localSimilarItems,
                            seerrItems = seerrRelatedItems.seerrSimilarItems,
                            mediaRepository = mediaRepository,
                            onItemClick = onSimilarItemClick
                        )
                    }
                }
            }
        }
    }

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
                        downloadRepository.enqueueEpisodeDownloads(selectedEpisodes)
                            .onFailure { throwable ->
                                downloadErrorDialogMessage =
                                    downloadFailureDialogMessage(rawMessage = throwable.message)
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

    SeerrRequestDialogs(
        state = seerrRequestState,
        itemName = item.name?.takeIf { it.isNotBlank() } ?: "Unknown",
        backdropImageUrl = backdropImageUrl ?: item.backdropImageUrl ?: item.imageUrl
    )

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
            itemsIndexed(
                items = episodes,
                key = { index, episode ->
                    "${episode.id ?: "${episode.name}-${episode.indexNumber}"}_$index"
                }
            ) { _, episode ->
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
private fun DirectorNamesRow(
    directors: List<BaseItemPerson>,
    onPersonClick: (String) -> Unit
) {
    Row {
        directors.forEachIndexed { index, person ->
            val personId = person.id
            val canOpenPerson = !personId.isNullOrBlank()
            val name = person.name ?: "Unknown"
            Text(
                text = name + if (index < directors.lastIndex) ", " else "",
                fontSize = 13.sp,
                color = Color(0xFF89ECFF),
                modifier = Modifier.clickable(enabled = canOpenPerson) {
                    personId?.let(onPersonClick)
                }
            )
        }
    }
}

private data class DetailScreenLayoutSpec(
    val heroHeight: Dp,
    val backdropHeight: Dp,
    val headerOffset: Dp,
    val contentTopPadding: Dp,
    val horizontalPadding: Dp,
    val contentMaxWidth: Dp?,
    val logoContainerHeight: Dp,
    val logoBottomSpacing: Dp
)

private fun detailScreenLayoutSpec(
    isWidescreenLayout: Boolean,
    useTabletBackdropLayout: Boolean,
    screenWidthDp: Dp,
    screenHeightDp: Dp
): DetailScreenLayoutSpec {
    val horizontalPadding = if (isWidescreenLayout) 30.dp else 14.dp

    return DetailScreenLayoutSpec(
        heroHeight = 330.dp,
        backdropHeight = if (useTabletBackdropLayout) screenHeightDp else 330.dp,
        headerOffset = if (useTabletBackdropLayout) 0.dp else (-96).dp,
        contentTopPadding = if (useTabletBackdropLayout) 312.dp else 0.dp,
        horizontalPadding = horizontalPadding,
        contentMaxWidth = detailContentMaxWidth(
            screenWidthDp = screenWidthDp,
            horizontalPadding = horizontalPadding
        ),
        logoContainerHeight = if (isWidescreenLayout) 74.dp else 78.dp,
        logoBottomSpacing = if (isWidescreenLayout) 18.dp else 8.dp
    )
}

@Composable
private fun BoxScope.DetailHeroCastButtonOverlay(
    onCastButtonClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(top = 12.dp, end = 14.dp)
    ) {
        ScreenCastButton(onConnectedClick = onCastButtonClick)
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

    if (item.isSeerDetailItem()) {
        addCandidate(item.backdropImageUrl)
        addCandidate(item.imageUrl)
        return candidates
    }

    if (item.type == "Episode") {
        addCandidate(
            mediaRepository.getBackdropImageUrl(
                itemId = itemId,
                width = 1920,
                height = 1080,
                quality = 100,
                enableImageEnhancers = false,
                imageTag = item.imageTagFor(
                    imageType = "Backdrop",
                    targetItemId = itemId
                )
            ).first()
        )

        addCandidate(
            mediaRepository.getImageUrl(
                itemId = itemId,
                imageType = "Primary",
                width = 1920,
                height = 1080,
                quality = 100,
                enableImageEnhancers = false,
                imageTag = item.imageTagFor(
                    imageType = "Primary",
                    targetItemId = itemId
                )
            ).first()
        )

        addCandidate(
            mediaRepository.getImageUrl(
                itemId = itemId,
                imageType = "Thumb",
                width = 1920,
                height = 1080,
                quality = 100,
                enableImageEnhancers = false,
                imageTag = item.imageTagFor(
                    imageType = "Thumb",
                    targetItemId = itemId
                )
            ).first()
        )

        if (!seriesId.isNullOrBlank()) {
            addCandidate(
                mediaRepository.getBackdropImageUrl(
                    itemId = seriesId,
                    width = 1920,
                    height = 1080,
                    quality = 100,
                    enableImageEnhancers = false,
                    imageTag = item.imageTagFor(
                        imageType = "Backdrop",
                        targetItemId = seriesId
                    )
                ).first()
            )
            addCandidate(
                mediaRepository.getImageUrl(
                    itemId = seriesId,
                    imageType = "Primary",
                    width = 1920,
                    height = 1080,
                    quality = 100,
                    enableImageEnhancers = false,
                    imageTag = item.imageTagFor(
                        imageType = "Primary",
                        targetItemId = seriesId
                    )
                ).first()
            )
        }
    } else {
        addCandidate(
            mediaRepository.getBackdropImageUrl(
                itemId = itemId,
                width = 1200,
                height = 675,
                quality = 95,
                imageTag = item.imageTagFor(
                    imageType = "Backdrop",
                    targetItemId = itemId
                )
            ).first()
        )

        addCandidate(
            mediaRepository.getImageUrl(
                itemId = itemId,
                imageType = "Primary",
                width = 1200,
                height = 675,
                quality = 95,
                imageTag = item.imageTagFor(
                    imageType = "Primary",
                    targetItemId = itemId
                )
            ).first()
        )

    }

    return candidates
}

internal suspend fun logoImage(
    item: BaseItemDto,
    mediaRepository: MediaRepository
): String? {
    if (item.isSeerDetailItem()) return null

    val logoItemId = item.logoItemId() ?: return null
    val logoImageTag = item.imageTagFor(
        imageType = "Logo",
        targetItemId = logoItemId
    ) ?: return mediaRepository.getTmdbLogoUrl(item)

    return mediaRepository.getImageUrl(
        itemId = logoItemId,
        imageType = "Logo",
        width = 1200,
        quality = 95,
        imageTag = logoImageTag
    ).first()
}

private fun BaseItemDto.logoItemId(): String? {
    return if (type == "Episode") {
        seriesId ?: id
    } else {
        id
    }
}

private fun BaseItemPerson.isCreditType(expectedType: String): Boolean {
    return type.equals(expectedType, ignoreCase = true) ||
        role.equals(expectedType, ignoreCase = true)
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
            genres = listOf(
                "Action",
                "Adventure",
                "Comedy",
                "Drama",
                "Fantasy",
                "Horror",
                "Mystery",
                "Romance",
                "Sci-Fi",
                "Thriller"
            ),
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
                    ShimmerEffect(modifier = Modifier
                        .width(56.dp)
                        .height(16.dp), cornerRadius = 8f)
                    ShimmerEffect(modifier = Modifier
                        .width(42.dp)
                        .height(16.dp), cornerRadius = 8f)
                    ShimmerEffect(modifier = Modifier
                        .width(64.dp)
                        .height(16.dp), cornerRadius = 8f)
                    ShimmerEffect(modifier = Modifier
                        .width(50.dp)
                        .height(18.dp), cornerRadius = 8f)
                }

                Spacer(modifier = Modifier.height(8.dp))

                ShimmerEffect(
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .height(14.dp),
                    cornerRadius = 8f
                )

                Spacer(modifier = Modifier.height(14.dp))

                repeat(2) {
                    ShimmerEffect(
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .height(18.dp),
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
                    modifier = Modifier
                        .width(110.dp)
                        .height(22.dp),
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