package com.jellycine.app.ui.screens.player

import android.content.Context
import android.view.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import coil3.SingletonImageLoader
import com.jellycine.app.discord.DiscordRpcEffect
import com.jellycine.app.ui.screens.player.PlayerViewModel
import com.jellycine.data.model.BaseItemDto
import com.jellycine.player.core.*
import com.jellycine.player.discord.NowPlayingInfo
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.ui.screens.player.*
import kotlinx.coroutines.delay

/**
 * Android player screen: owns playback setup, lifecycle and the ExoPlayer/mpv surface, and
 * hands everything else to the shared [PlayerContent].
 */
@UnstableApi
@Composable
fun PlayerScreen(
    mediaId: String,
    initialItemDetails: BaseItemDto? = null,
    remoteMediaUrl: String? = null,
    remoteMediaTitle: String? = null,
    preferredAudioStreamIndex: Int? = null,
    preferredSubtitleStreamIndex: Int? = null,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
    onPreferredStreamIndexesChanged: (Int?, Int?) -> Unit = { _, _ -> },
    onBackPressed: (() -> Unit)? = null,
    onPlaybackCompleted: ((String) -> Unit)? = null,
    previousEpisodeId: String? = null,
    onWatchPreviousEpisode: ((String) -> Unit)? = null,
    nextEpisodeId: String? = null,
    onWatchNextEpisode: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val platform = remember(context) { AndroidPlayerPlatform(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val playerPreferences = remember { PlayerPreferences(context) }
    val useDeviceVolumeInPlayer = remember { playerPreferences.isUseDeviceVolumeInPlayerEnabled() }

    var lifecycle by remember { mutableStateOf(Lifecycle.Event.ON_CREATE) }
    var shouldPrefetchNextEpisode by remember { mutableStateOf(false) }

    val playerState by viewModel.playerState.collectAsState()
    val preferredStreamIndexes by viewModel.preferredStreamIndexes.collectAsState()

    // Track initialized media so this screen can switch to a new episode in-place.
    var initializedMediaId by remember { mutableStateOf<String?>(null) }

    PlayerScreenEffects(
        context = context,
        platform = platform,
        lifecycleOwner = lifecycleOwner,
        mediaId = mediaId,
        initialItemDetails = initialItemDetails,
        remoteMediaUrl = remoteMediaUrl,
        remoteMediaTitle = remoteMediaTitle,
        preferredAudioStreamIndex = preferredAudioStreamIndex,
        preferredSubtitleStreamIndex = preferredSubtitleStreamIndex,
        viewModel = viewModel,
        onPlaybackCompleted = onPlaybackCompleted,
        preferredStreamIndexes = preferredStreamIndexes,
        playerState = playerState,
        useDeviceVolumeInPlayer = useDeviceVolumeInPlayer,
        initializedMediaIdProvider = { initializedMediaId },
        onInitializedMediaIdChange = { initializedMediaId = it },
        onLifecycleChange = { lifecycle = it },
        onPreferredStreamIndexesChanged = onPreferredStreamIndexesChanged
    )

    // Discord Rich Presence
    DiscordRpcEffect(
        playerState = viewModel.playerState,
        mediaId = mediaId,
        seriesName = initialItemDetails?.seriesName,
        year = initialItemDetails?.productionYear,
        mediaType = when {
            initialItemDetails?.type.equals("Episode", ignoreCase = true) -> NowPlayingInfo.MediaType.EPISODE
            initialItemDetails?.type.equals("Audio", ignoreCase = true) -> NowPlayingInfo.MediaType.MUSIC
            else -> NowPlayingInfo.MediaType.MOVIE
        },
        imageUrlProvider = { viewModel.discordPosterUrl }
    )

    LaunchedEffect(
        initializedMediaId,
        nextEpisodeId,
        shouldPrefetchNextEpisode,
        preferredStreamIndexes.audioStreamIndex,
        preferredStreamIndexes.subtitleStreamIndex
    ) {
        viewModel.updateNextEpisodeCache(
            context = context,
            nextEpisodeId = nextEpisodeId.takeIf {
                initializedMediaId == mediaId && shouldPrefetchNextEpisode
            },
            preferredAudioStreamIndex = preferredStreamIndexes.audioStreamIndex,
            preferredSubtitleStreamIndex = preferredStreamIndexes.subtitleStreamIndex
        )
    }

    PlayerContent(
        controller = viewModel,
        preferences = playerPreferences,
        platform = platform,
        mediaId = mediaId,
        sourceVideoHeight = viewModel.getSourceVideoHeight(),
        mediaInfoProvider = viewModel::getMediaMetadataInfo,
        onApplyPlaybackSettings = { _, _ ->
            val resumePositionMs = viewModel.getCurrentPosition()
            val shouldResumePlaying = viewModel.isPlayingNow()
            viewModel.releasePlayer()
            initializedMediaId = null
            viewModel.initializePlayer(
                context = context,
                mediaId = mediaId,
                initialItemDetails = initialItemDetails,
                preferredAudioStreamIndex = preferredStreamIndexes.audioStreamIndex,
                preferredSubtitleStreamIndex = preferredStreamIndexes.subtitleStreamIndex,
                initialSeekPositionMs = resumePositionMs,
                startPlayback = shouldResumePlaying
            )
            initializedMediaId = mediaId
        },
        onBackPressed = onBackPressed,
        modifier = modifier,
        previousEpisodeId = previousEpisodeId,
        onWatchPreviousEpisode = onWatchPreviousEpisode,
        nextEpisodeId = nextEpisodeId,
        onWatchNextEpisode = onWatchNextEpisode,
        onNextEpisodePrefetchChange = { shouldPrefetchNextEpisode = it }
    ) { surfaceModifier ->
        VideoSurface(
            player = viewModel.exoPlayer,
            mpvPlayer = viewModel.mpvPlayer,
            lifecycle = lifecycle,
            scale = playerState.videoScale,
            offsetX = playerState.videoOffsetX,
            offsetY = playerState.videoOffsetY,
            resizeMode = viewModel.getCurrentResizeMode(),
            isHdr = playerState.isHdrEnabled,
            modifier = surfaceModifier
        )
    }
}

private fun trimImageMemoryCacheForPlayback(context: Context) {
    runCatching {
        SingletonImageLoader.get(context).memoryCache?.clear()
    }
}

@Composable
private fun PlayerScreenEffects(
    context: Context,
    platform: PlayerPlatform,
    lifecycleOwner: LifecycleOwner,
    mediaId: String,
    initialItemDetails: BaseItemDto?,
    remoteMediaUrl: String?,
    remoteMediaTitle: String?,
    preferredAudioStreamIndex: Int?,
    preferredSubtitleStreamIndex: Int?,
    viewModel: PlayerViewModel,
    onPlaybackCompleted: ((String) -> Unit)?,
    preferredStreamIndexes: PreferredStreamIndexes,
    playerState: PlayerState,
    useDeviceVolumeInPlayer: Boolean,
    initializedMediaIdProvider: () -> String?,
    onInitializedMediaIdChange: (String?) -> Unit,
    onLifecycleChange: (Lifecycle.Event) -> Unit,
    onPreferredStreamIndexesChanged: (Int?, Int?) -> Unit
) {
    DisposableEffect(Unit) {
        platform.enterPlayerMode()
        onDispose { platform.exitPlayerMode(restoreVolume = !useDeviceVolumeInPlayer) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            onLifecycleChange(event)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val hdrPassthrough = remember { !PlayerPreferences(context).getMpvHdrToSdrTonemapping() }
    DisposableEffect(viewModel.mpvPlayer, playerState.isHdrEnabled, hdrPassthrough) {
        platform.setHdrMode(
            viewModel.mpvPlayer != null && playerState.isHdrEnabled && hdrPassthrough
        )
        onDispose { platform.setHdrMode(false) }
    }

    val initializationKey = remoteMediaUrl
        ?.takeIf { it.isNotBlank() }
        ?.let { "remote:$it" }
        ?: mediaId

    LaunchedEffect(initializationKey) {
        if (initializedMediaIdProvider() == initializationKey) return@LaunchedEffect

        try {
            trimImageMemoryCacheForPlayback(context)
            if (initializedMediaIdProvider() != null) {
                viewModel.releasePlayer()
            }
            if (!remoteMediaUrl.isNullOrBlank()) {
                viewModel.initializeRemotePlayer(
                    context = context,
                    mediaId = initializationKey,
                    remoteUrl = remoteMediaUrl,
                    title = remoteMediaTitle
                )
            } else {
                viewModel.initializePlayer(
                    context = context,
                    mediaId = mediaId,
                    initialItemDetails = initialItemDetails,
                    preferredAudioStreamIndex = preferredAudioStreamIndex,
                    preferredSubtitleStreamIndex = preferredSubtitleStreamIndex
                )
            }
            onInitializedMediaIdChange(initializationKey)
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(viewModel, onPlaybackCompleted) {
        viewModel.playbackCompletedEvents.collect { completedMediaId ->
            onPlaybackCompleted?.invoke(completedMediaId)
        }
    }

    LaunchedEffect(
        initializedMediaIdProvider(),
        preferredStreamIndexes.audioStreamIndex,
        preferredStreamIndexes.subtitleStreamIndex
    ) {
        if (initializedMediaIdProvider() == initializationKey && remoteMediaUrl.isNullOrBlank()) {
            onPreferredStreamIndexesChanged(
                preferredStreamIndexes.audioStreamIndex,
                preferredStreamIndexes.subtitleStreamIndex
            )
        }
    }
}

@Preview(
    name = "Player Screen - Controls Visible",
    showBackground = true,
    widthDp = 800,
    heightDp = 450
)
@Composable
fun PlayerScreenPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Mock video surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text(
                text = "Video Content",
                color = Color.White.copy(alpha = 0.3f),
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
            )
        }

        // Show controls overlay
        ControlsOverlay(
            title = "Sample Movie Title",
            chapterMarkers = emptyList(),
            isPlaying = true,
            currentPosition = 45000L, // 45 seconds
            duration = 7200000L, // 2 hours
            onBackClick = { },
            onPlayPause = { },
            onSeek = { },
            isLocked = false,
            onToggleLock = { },
            onShowAudioTrackSelection = { },
            onShowSubtitleTrackSelection = { },
            onCycleAspectRatio = { },
            onSeekBackward = { },
            onSeekForward = { },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(
    name = "Player Screen - Gesture Indicators",
    showBackground = true,
    widthDp = 800,
    heightDp = 450
)
@Composable
fun PlayerScreenGesturePreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Mock video surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Gray),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Video Surface",
                color = Color.White,
                fontSize = 24.sp
            )
        }

        // Gesture indicators preview
        GestureIndicators(
            volumeLevel = 0.7f, // 70% volume
            brightnessLevel = 0.5f, // 50% brightness
            seekPosition = "+10s"
        )

        // Loading indicator preview
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Preview(
    name = "Player Screen - Controls Hidden",
    showBackground = true,
    widthDp = 800,
    heightDp = 450
)
@Composable
fun PlayerScreenPreviewHidden() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text(
                text = "Video Content",
                color = Color.White.copy(alpha = 0.3f),
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
            )
        }
    }
}
