package com.jellycine.app.ui.screens.detail

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.shared.R
import com.jellycine.shared.util.image.JellyfinPosterImage
import com.jellycine.shared.util.image.imageTagFor
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaStream
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.flow.first
import android.content.res.Configuration
import com.jellycine.app.ui.screens.player.PlayerScreen
import com.jellycine.detail.CodecUtils
import com.jellycine.shared.ui.components.common.ScreenWrapper
import com.jellycine.shared.ui.components.common.ShimmerEffect
import com.jellycine.app.cast.CastController
import com.jellycine.app.download.BatchDownloadCandidate
import com.jellycine.app.download.BatchDownloadEstimate
import com.jellycine.app.download.DownloadStatus
import com.jellycine.app.download.ItemDownloadState
import com.jellycine.app.ui.screens.cast.CastPlayback
import com.jellycine.app.ui.screens.cast.loadCastPlaybackData
import com.jellycine.app.ui.screens.cast.activeCastArtworkUrl
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.shared.playback.PlaybackRefreshSignals
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
                    preferredAudioStreamIndex = playerPreferences.getPreferredAudioStreamIndex(playedItemId)
                    preferredSubtitleStreamIndex = playerPreferences.getPreferredSubtitleStreamIndex(playedItemId)
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
    onVersionItemSelected: (String) -> Unit = {},
    onPersonClick: (String) -> Unit = {},
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
        onSeasonClick = onSeasonClick
    )
}

@Composable
internal fun MoreFromSeasonSection(
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

internal fun downloadFailure(
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

internal fun formatStorageBytesForDialog(bytes: Long): String {
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

internal suspend fun heroImageCandidates(
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
            enableImageEnhancers = false,
            imageTag = item.imageTagFor(
                imageType = "Backdrop",
                targetItemId = itemId
            )
        ).first())

        addCandidate(mediaRepository.getImageUrl(
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
        ).first())

        addCandidate(mediaRepository.getImageUrl(
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
        ).first())

        if (!seriesId.isNullOrBlank()) {
            addCandidate(mediaRepository.getBackdropImageUrl(
                itemId = seriesId,
                width = 1920,
                height = 1080,
                quality = 100,
                enableImageEnhancers = false,
                imageTag = item.imageTagFor(
                    imageType = "Backdrop",
                    targetItemId = seriesId
                )
            ).first())
            addCandidate(mediaRepository.getImageUrl(
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
            ).first())
        }
    } else {
        addCandidate(mediaRepository.getBackdropImageUrl(
            itemId = itemId,
            width = 1200,
            height = 675,
            quality = 95,
            imageTag = item.imageTagFor(
                imageType = "Backdrop",
                targetItemId = itemId
            )
        ).first())

        addCandidate(mediaRepository.getImageUrl(
            itemId = itemId,
            imageType = "Primary",
            width = 1200,
            height = 675,
            quality = 95,
            imageTag = item.imageTagFor(
                imageType = "Primary",
                targetItemId = itemId
            )
        ).first())
    }

    return candidates
}

internal suspend fun logoImage(
    item: BaseItemDto,
    mediaRepository: MediaRepository
): String? {
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

internal fun BaseItemDto.logoItemId(): String? {
    return if (type == "Episode") {
        seriesId ?: id
    } else {
        id
    }
}

internal fun episodeHeaderText(item: BaseItemDto): String? {
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


internal data class SeriesSeasonSelectionDialogState(
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

internal fun seasonGroupKey(item: BaseItemDto): String {
    return item.seasonId?.takeIf { it.isNotBlank() }
        ?: item.parentId?.takeIf { it.isNotBlank() }
        ?: item.parentIndexNumber?.let { "season_$it" }
        ?: "season_unknown_${item.id.orEmpty()}"
}

internal fun episodeCountLabel(count: Int): String {
    return "$count episode" + if (count == 1) "" else "s"
}

@Composable
internal fun SeasonsSection(
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
            overview = "After his best friend is killed in a shark attack, Quinn, a lovable " +
                "yet tenacious seal assembles a SEAL TEAM to fight back against a gang of sharks.",
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