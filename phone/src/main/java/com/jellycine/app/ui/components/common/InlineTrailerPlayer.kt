package com.jellycine.app.ui.components.common

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.jellycine.player.core.PlayerUtils
import com.jellycine.player.core.RemoteTrailerUrl
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.*

@Composable
fun InlineTrailerPlayer(
    trailerUrl: String,
    isVisible: Boolean,
    loadEnabled: Boolean = true,
    player: ExoPlayer? = null,
    modifier: Modifier = Modifier,
    showBuiltInMute: Boolean = true,
    mutedOverride: Boolean? = null,
    onPlaybackCompleted: () -> Unit = {},
    onError: (Throwable) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val maxVideoHeight = if (isTablet) Int.MAX_VALUE else 720
    val ownedPlayer = if (player == null) remember { createInlineExoPlayer(context) } else null
    val activePlayer = player ?: ownedPlayer!!
    val playbackCompleted = remember { mutableStateOf(false) }
    val isLoading = remember { mutableStateOf(true) }
    val errorState = remember { mutableStateOf<Throwable?>(null) }
    val hasRenderedFirstFrame = remember { mutableStateOf(false) }
    var internalMuted by rememberSaveable { mutableStateOf(false) }
    val isMuted = mutedOverride ?: internalMuted
    val currentIsVisible by rememberUpdatedState(isVisible)
    val ownsPlayer = remember(trailerUrl) { mutableStateOf(activePlayer.currentMediaItem?.mediaId == trailerUrl) }
    val shouldBeVisible = isVisible && hasRenderedFirstFrame.value
    val playerAlpha by animateFloatAsState(
        targetValue = if (shouldBeVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "player_alpha"
    )

    LaunchedEffect(isMuted) {
        withContext(Dispatchers.Main) {
            activePlayer.volume = if (isMuted) 0f else 1f
        }
    }

    LaunchedEffect(trailerUrl, loadEnabled) {
        if (!loadEnabled) return@LaunchedEffect
        if (trailerUrl.isNotBlank() && activePlayer.currentMediaItem?.mediaId == trailerUrl &&
            activePlayer.playbackState != Player.STATE_IDLE
        ) {
            ownsPlayer.value = true
            hasRenderedFirstFrame.value = activePlayer.videoSize.width > 0
            isLoading.value = false
            return@LaunchedEffect
        }
        playbackCompleted.value = false
        errorState.value = null
        hasRenderedFirstFrame.value = false

        if (trailerUrl.isBlank()) {
            withContext(Dispatchers.Main) {
                activePlayer.stop()
                activePlayer.clearMediaItems()
                activePlayer.playWhenReady = false
                activePlayer.pause()
            }
            isLoading.value = false
            return@LaunchedEffect
        }

        try {
            isLoading.value = true

            val resolvedStream = RemoteTrailerUrl.resolve(trailerUrl, maxVideoHeight)

            withContext(Dispatchers.Main) {
                val mediaSourceFactory = unthrottledMediaSourceFactory(context)
                val videoItem = MediaItem.Builder()
                    .setMediaId(trailerUrl)
                    .setUri(resolvedStream.url)
                    .apply { resolvedStream.mimeType?.let { setMimeType(it) } }
                    .build()
                if (!resolvedStream.audioUrl.isNullOrBlank()) {
                    val audioItem = MediaItem.Builder()
                        .setUri(resolvedStream.audioUrl)
                        .apply { resolvedStream.audioMimeType?.let { setMimeType(it) } }
                        .build()
                    activePlayer.setMediaSource(
                        MergingMediaSource(
                            mediaSourceFactory.createMediaSource(videoItem),
                            mediaSourceFactory.createMediaSource(audioItem)
                        )
                    )
                } else {
                    activePlayer.setMediaSource(mediaSourceFactory.createMediaSource(videoItem))
                }
                ownsPlayer.value = true
                activePlayer.prepare()
                activePlayer.playWhenReady = false
                activePlayer.volume = if (isMuted) 0f else 1f
            }

            isLoading.value = false
        } catch (e: Exception) {
            errorState.value = e
            isLoading.value = false
            onError(e)
        }
    }

    DisposableEffect(activePlayer) {
        val listener = object : Player.Listener {
            private fun owns() = activePlayer.currentMediaItem?.mediaId == trailerUrl

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                ownsPlayer.value = mediaItem?.mediaId == trailerUrl
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!owns()) return
                when (playbackState) {
                    Player.STATE_READY -> {
                        if (currentIsVisible && !playbackCompleted.value && errorState.value == null) {
                            activePlayer.playWhenReady = true
                        }
                    }
                    Player.STATE_ENDED -> {
                        if (!playbackCompleted.value) {
                            playbackCompleted.value = true
                            onPlaybackCompleted()
                        }
                    }
                }
            }

            override fun onRenderedFirstFrame() {
                if (owns()) hasRenderedFirstFrame.value = true
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!owns()) return
                errorState.value = error
                onError(error)
            }
        }

        activePlayer.addListener(listener)

        onDispose {
            activePlayer.removeListener(listener)
            if (activePlayer.currentMediaItem?.mediaId == trailerUrl) {
                activePlayer.playWhenReady = false
                activePlayer.pause()
            }
            ownedPlayer?.release()
        }
    }

    LaunchedEffect(isVisible, ownsPlayer.value) {
        if (!ownsPlayer.value) return@LaunchedEffect
        if (isVisible && !playbackCompleted.value && errorState.value == null) {
            if (activePlayer.playbackState == Player.STATE_READY) {
                withContext(Dispatchers.Main) {
                    activePlayer.playWhenReady = true
                }
            }
        } else if (!isVisible) {
            withContext(Dispatchers.Main) {
                activePlayer.playWhenReady = false
                activePlayer.pause()
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setPadding(0, 0, 0, 0)
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.player = activePlayer
                    alpha = 0f
                    keepScreenOn = true
                }
            },
            update = { view ->
                view.player = if (ownsPlayer.value) activePlayer else null
                view.alpha = playerAlpha
                view.keepScreenOn = isVisible && !playbackCompleted.value
            },
            modifier = Modifier.matchParentSize()
        )

        if (shouldBeVisible && showBuiltInMute) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { internalMuted = !internalMuted },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun unthrottledMediaSourceFactory(context: Context) = DefaultMediaSourceFactory(
    DefaultDataSource.Factory(
        context,
        DefaultHttpDataSource.Factory().setDefaultRequestProperties(mapOf("Range" to "bytes=0-"))
    )
)

fun createInlineExoPlayer(context: Context): ExoPlayer {
    return PlayerUtils.createPlayer(
        context = context,
        bufferOverride = PlayerUtils.PlaybackBufferOverride(
            minBufferMs = 15_000,
            maxBufferMs = 60_000,
            bufferForPlaybackMs = 1_500,
            bufferForPlaybackAfterRebufferMs = 3_000
        )
    ).apply {
        repeatMode = Player.REPEAT_MODE_OFF
        setHandleAudioBecomingNoisy(false)
    }
}
