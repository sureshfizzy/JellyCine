package com.jellycine.app.ui.screens.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jellycine.app.cast.CastController
import com.jellycine.app.ui.screens.cast.CastPlayback
import com.jellycine.app.ui.screens.cast.activeCastArtworkUrl
import com.jellycine.app.ui.screens.cast.loadCastPlaybackData
import com.jellycine.app.ui.screens.player.PlayerScreen
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaStream
import com.jellycine.data.repository.AuthRepository
import com.jellycine.data.repository.MediaRepository
import com.jellycine.player.audio.ThemeMusicController
import com.jellycine.player.core.RemoteTrailerUrl
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.preferences.Preferences
import com.jellycine.ui.screens.detail.DetailController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

@Composable
internal fun rememberPhoneDetailController(
    context: Context,
    itemId: String,
    item: BaseItemDto?,
    episodeItem: BaseItemDto?,
    mediaRepository: MediaRepository,
    authRepository: AuthRepository,
    playerPreferences: PlayerPreferences,
    preferences: Preferences,
    onEpisodePlaybackChanged: (String) -> Unit
): DetailController {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val themeMusicController = remember { ThemeMusicController(context) }
    val themeMusicMode by preferences.themeMusicMode()
        .collectAsState(initial = preferences.getThemeMusicMode())
    val themeMusicVolume by preferences.themeMusicVolume()
        .collectAsState(initial = preferences.getThemeMusicVolume())
    val activeServerId by authRepository.getActiveServerId()
        .collectAsState(initial = authRepository.getActiveSessionSnapshot().activeServerId)
    val castPlaybackState by CastController.playbackState.collectAsState()

    val controller = remember(itemId) {
        PhoneDetailController(
            context = context,
            itemId = itemId,
            scope = scope,
            mediaRepository = mediaRepository,
            playerPreferences = playerPreferences
        )
    }
    controller.item = item
    controller.episodeItem = episodeItem
    controller.castPlaybackState = castPlaybackState
    controller.onEpisodePlaybackChanged = onEpisodePlaybackChanged

    DisposableEffect(lifecycleOwner, themeMusicController) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> controller.isLifecycleActive = true
                Lifecycle.Event.ON_STOP -> {
                    controller.isLifecycleActive = false
                    themeMusicController.stop()
                    controller.playingThemeUrls = null
                }
                else -> Unit
            }
        }
        controller.isLifecycleActive =
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            themeMusicController.release()
        }
    }

    val activeThemeItemId = item?.let { it.seriesId?.takeIf(String::isNotBlank) ?: it.id }
    val themePlaybackBlocked = controller.coversContent ||
        controller.castingDisplay || castPlaybackState.isCastingMedia

    LaunchedEffect(
        activeThemeItemId,
        activeServerId,
        themeMusicMode,
        themePlaybackBlocked,
        controller.isLifecycleActive
    ) {
        val themeItemId = activeThemeItemId?.takeIf(String::isNotBlank)
        if (
            themeItemId == null || themeMusicMode == Preferences.THEME_MUSIC_NO ||
            themePlaybackBlocked || !controller.isLifecycleActive
        ) {
            themeMusicController.stop()
            controller.playingThemeUrls = null
            return@LaunchedEffect
        }

        var urls = mediaRepository.getThemeSongUrls(themeItemId).getOrDefault(emptyList())
        currentCoroutineContext().ensureActive()
        if (urls.isEmpty()) {
            val preloadedThemeItem = listOfNotNull(item, episodeItem).firstOrNull { it.id == themeItemId }
            val audioUrl = coroutineScope {
                val searchDeferred = async {
                    val search = mediaRepository.getThemeYoutubeSearchQuery(themeItemId, preloadedThemeItem)
                    search?.let { runCatching { RemoteTrailerUrl.searchAudioUrl(it.query, it.title) }.getOrNull() }
                }
                val youtubeThemeUrl = mediaRepository.getThemerrThemeYoutubeUrl(themeItemId, preloadedThemeItem)
                if (!youtubeThemeUrl.isNullOrBlank()) {
                    searchDeferred.cancel()
                    runCatching { RemoteTrailerUrl.getAudioUrl(youtubeThemeUrl) }.getOrNull()
                } else {
                    searchDeferred.await()
                }
            }
            currentCoroutineContext().ensureActive()
            if (!audioUrl.isNullOrBlank()) {
                urls = listOf(audioUrl)
            }
        }

        if (urls.isEmpty()) {
            themeMusicController.stop()
            controller.playingThemeUrls = null
            return@LaunchedEffect
        }
        if (urls == controller.playingThemeUrls) {
            return@LaunchedEffect
        }

        themeMusicController.stop()
        controller.playingThemeUrls = urls
        themeMusicController.play(
            urls = urls,
            endless = themeMusicMode == Preferences.THEME_MUSIC_ENDLESS,
            volume = themeMusicVolume
        )
    }

    LaunchedEffect(themeMusicVolume) {
        themeMusicController.setVolume(themeMusicVolume)
    }

    LaunchedEffect(context) {
        CastController.ensureInitialized(context)
    }

    LaunchedEffect(controller.showPlayer, controller.playbackItemId, itemId) {
        if (!controller.showPlayer) {
            controller.availablePreviousEpisodeId = null
            controller.availableNextEpisodeId = null
            return@LaunchedEffect
        }

        val activePlaybackId = controller.playbackItemId ?: itemId
        val episodeNavigationIds = mediaRepository.getEpisodeNavigationIds(activePlaybackId)
        controller.availablePreviousEpisodeId = episodeNavigationIds.previousEpisodeId
        controller.availableNextEpisodeId = episodeNavigationIds.nextEpisodeId
    }

    LaunchedEffect(castPlaybackState.isConnected, castPlaybackState.isCastingMedia) {
        if (controller.castingDisplay && !castPlaybackState.isConnected) {
            controller.castingDisplay = false
        }
        if (!castPlaybackState.isConnected) {
            controller.resetCastDisplay()
        }
    }

    return controller
}

@Stable
private class PhoneDetailController(
    private val context: Context,
    private val itemId: String,
    private val scope: CoroutineScope,
    private val mediaRepository: MediaRepository,
    private val playerPreferences: PlayerPreferences
) : DetailController {
    var item: BaseItemDto? = null
    var episodeItem: BaseItemDto? = null
    var castPlaybackState = CastController.playbackState.value
    var onEpisodePlaybackChanged: (String) -> Unit = {}

    var isLifecycleActive by mutableStateOf(true)
    var playingThemeUrls by mutableStateOf<List<String>?>(null)

    var showPlayer by mutableStateOf(false)
    var remoteTrailerUrl by mutableStateOf<String?>(null)
    var remoteTrailerTitle by mutableStateOf<String?>(null)
    var playbackItemId by mutableStateOf<String?>(null)
    var availablePreviousEpisodeId by mutableStateOf<String?>(null)
    var availableNextEpisodeId by mutableStateOf<String?>(null)
    private var preferredAudioStreamIndex by mutableStateOf<Int?>(null)
    private var preferredSubtitleStreamIndex by mutableStateOf<Int?>(null)
    private var syncVersion by mutableIntStateOf(0)

    var castingDisplay by mutableStateOf(false)
    private var castDisplayItem by mutableStateOf<BaseItemDto?>(null)
    private var castDisplayArtworkUrl by mutableStateOf<String?>(null)
    private var castDisplayStreams by mutableStateOf<List<MediaStream>>(emptyList())
    private var castDisplayAudioStreamIndex by mutableStateOf<Int?>(null)
    private var castDisplaySubtitleStreamIndex by mutableStateOf<Int?>(null)
    private var castTracks by mutableStateOf(false)

    override val coversContent: Boolean
        get() = !remoteTrailerUrl.isNullOrBlank() || showPlayer
    override val trackSelectionSyncVersion: Int get() = syncVersion

    fun resetCastDisplay() {
        castDisplayStreams = emptyList()
        castDisplayItem = null
        castDisplayArtworkUrl = null
        castDisplayAudioStreamIndex = null
        castDisplaySubtitleStreamIndex = null
        castTracks = false
    }

    private suspend fun castDisplayState(castItemId: String) {
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

    private fun localPlayer(targetItemId: String) {
        playbackItemId = targetItemId
        castingDisplay = false
        remoteTrailerUrl = null
        remoteTrailerTitle = null
        showPlayer = true
    }

    override fun playRemoteTrailer(url: String, title: String?) {
        val targetTrailerUrl = url.takeIf { it.isNotBlank() } ?: return
        remoteTrailerUrl = targetTrailerUrl
        remoteTrailerTitle = title
        playbackItemId = null
        castingDisplay = false
        showPlayer = false
    }

    override fun openCast() {
        if (!castPlaybackState.isConnected) return
        val castItemId = castPlaybackState.currentItemId ?: castDisplayItem?.id
        castingDisplay = true
        if (castItemId.isNullOrBlank()) return
        scope.launch { castDisplayState(castItemId = castItemId) }
    }

    private fun updateCastStreams(audioStreamIndex: Int?, subtitleStreamIndex: Int?) {
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

    override fun play(
        item: BaseItemDto?,
        fallbackItemId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?
    ) {
        val activeItemId = item?.id?.takeIf { it.isNotBlank() } ?: fallbackItemId
        preferredAudioStreamIndex = audioStreamIndex
        preferredSubtitleStreamIndex = subtitleStreamIndex

        scope.launch {
            if (castPlaybackState.isConnected) {
                val startPositionMs = (item?.userData?.playbackPositionTicks ?: 0L) / 10_000L
                val artworkUrl = activeCastArtworkUrl(
                    mediaRepository = mediaRepository,
                    item = item,
                    fallbackItemId = activeItemId
                )
                val castResult = CastController.castItem(
                    context = context,
                    mediaRepository = mediaRepository,
                    itemId = activeItemId,
                    title = item?.name,
                    subtitle = item?.seriesName,
                    itemType = item?.type,
                    artworkUrl = artworkUrl,
                    startPositionMs = startPositionMs,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex
                )

                if (castResult.isSuccess) {
                    playbackItemId = activeItemId
                    castDisplayItem = item
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

    override fun onPreferredStreamIndexesChanged(audioStreamIndex: Int?, subtitleStreamIndex: Int?) {
        preferredAudioStreamIndex = audioStreamIndex
        preferredSubtitleStreamIndex = subtitleStreamIndex
    }

    private fun closePlayer() {
        val playedItemId = playbackItemId ?: itemId
        preferredAudioStreamIndex = playerPreferences.getPreferredAudioStreamIndex(playedItemId)
        preferredSubtitleStreamIndex = playerPreferences.getPreferredSubtitleStreamIndex(playedItemId)
        syncVersion += 1
        showPlayer = false
        playbackItemId = null
    }

    override fun handleBack(): Boolean = when {
        !remoteTrailerUrl.isNullOrBlank() -> {
            remoteTrailerUrl = null
            remoteTrailerTitle = null
            true
        }

        castingDisplay -> {
            castingDisplay = false
            true
        }

        showPlayer -> {
            closePlayer()
            true
        }

        else -> false
    }

    private fun playEpisode(episodeId: String) {
        playbackItemId = episodeId
        availablePreviousEpisodeId = null
        availableNextEpisodeId = null
        onEpisodePlaybackChanged(episodeId)
    }

    @Composable
    override fun Content() {
        val activeRemoteTrailerUrl = remoteTrailerUrl
        if (!activeRemoteTrailerUrl.isNullOrBlank()) {
            PlayerScreen(
                mediaId = "remote_trailer_${activeRemoteTrailerUrl.hashCode()}",
                remoteMediaUrl = activeRemoteTrailerUrl,
                remoteMediaTitle = remoteTrailerTitle ?: item?.name ?: episodeItem?.name ?: "Trailer",
                onBackPressed = {
                    remoteTrailerUrl = null
                    remoteTrailerTitle = null
                }
            )
        } else if (showPlayer) {
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
                onBackPressed = { closePlayer() },
                previousEpisodeId = availablePreviousEpisodeId,
                onWatchPreviousEpisode = ::playEpisode,
                nextEpisodeId = availableNextEpisodeId,
                onWatchNextEpisode = ::playEpisode,
                onPlaybackCompleted = { completedItemId ->
                    scope.launch {
                        val nextEpisodeId = mediaRepository.getNextEpisodeId(completedItemId)
                            ?: return@launch
                        playEpisode(nextEpisodeId)
                    }
                }
            )
        }
    }

    @Composable
    override fun Overlays() {
        if (!castingDisplay) return
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
