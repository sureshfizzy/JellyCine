package com.jellycine.app.ui.components.common

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.jellycine.player.core.PlayerUtils
import com.jellycine.player.core.RemoteTrailerUrl
import kotlinx.coroutines.*

@Composable
fun InlineTrailerPlayer(
    trailerUrl: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    onPlaybackCompleted: () -> Unit = {},
    onError: (Throwable) -> Unit = {}
) {
    val context = LocalContext.current
    val player = remember { createInlineExoPlayer(context) }
    val playbackCompleted = remember { mutableStateOf(false) }
    val isLoading = remember { mutableStateOf(true) }
    val errorState = remember { mutableStateOf<Throwable?>(null) }
    val isPlayerReady = remember { mutableStateOf(false) }
    val hasRenderedFirstFrame = remember { mutableStateOf(false) }
    val shouldBeVisible = isVisible && hasRenderedFirstFrame.value
    val playerAlpha by animateFloatAsState(
        targetValue = if (shouldBeVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "player_alpha"
    )
    
    // Resolve trailer URL and prepare player
    LaunchedEffect(trailerUrl) {
        playbackCompleted.value = false
        isPlayerReady.value = false
        errorState.value = null
        hasRenderedFirstFrame.value = false
        
        if (trailerUrl.isBlank()) {
            withContext(Dispatchers.Main) {
                player.stop()
                player.clearMediaItems()
                player.playWhenReady = false
                player.pause()
            }
            isLoading.value = false
            return@LaunchedEffect
        }
        
        try {
            isLoading.value = true
            
            val resolvedStream = RemoteTrailerUrl.resolve(trailerUrl)

            val mediaItem = MediaItem.Builder()
                .setUri(resolvedStream.url)
                .setMimeType(resolvedStream.mimeType)
                .build()
            
            withContext(Dispatchers.Main) {
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = false
                player.volume = 0f
            }
            
            isLoading.value = false
        } catch (e: Exception) {
            errorState.value = e
            isLoading.value = false
            onError(e)
        }
    }
    
    // Listen for player readiness, first frame, and playback completion
    LaunchedEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        isPlayerReady.value = true
                        if (isVisible && !playbackCompleted.value && errorState.value == null) {
                            launch(Dispatchers.Main) {
                                player.playWhenReady = true
                            }
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
                hasRenderedFirstFrame.value = true
            }
            
            override fun onPlayerError(error: PlaybackException) {
                errorState.value = error
                onError(error)
            }
        }
        
        player.addListener(listener)
        
        awaitCancellation()
    }
    
    // Handle visibility changes
    LaunchedEffect(isVisible) {
        if (isVisible && !playbackCompleted.value && errorState.value == null) {
            if (isPlayerReady.value) {
                withContext(Dispatchers.Main) {
                    player.playWhenReady = true
                }
            }
        } else if (!isVisible) {
            withContext(Dispatchers.Main) {
                player.playWhenReady = false
                player.pause()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

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
                this.player = player
                alpha = 0f
            }
        },
        update = { view ->
            view.alpha = playerAlpha
        },
        modifier = modifier
    )
}

/**
 * Creates and configures an ExoPlayer instance for inline trailer playback.
 */
private fun createInlineExoPlayer(context: Context): ExoPlayer {
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