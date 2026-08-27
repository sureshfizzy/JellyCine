package com.vela.app.ui.components.common

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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vela.player.core.PlayerUtils
import com.vela.player.core.RemoteTrailerUrl
import androidx.compose.ui.platform.LocalConfiguration
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
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val maxVideoHeight = if (isTablet) Int.MAX_VALUE else 720
    val player = remember { createInlineExoPlayer(context) }
    val playbackCompleted = remember { mutableStateOf(false) }
    val isLoading = remember { mutableStateOf(true) }
    val errorState = remember { mutableStateOf<Throwable?>(null) }
    val isPlayerReady = remember { mutableStateOf(false) }
    val hasRenderedFirstFrame = remember { mutableStateOf(false) }
    var isMuted by rememberSaveable { mutableStateOf(false) }
    val shouldBeVisible = isVisible && hasRenderedFirstFrame.value
    val playerAlpha by animateFloatAsState(
        targetValue = if (shouldBeVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "player_alpha"
    )

    LaunchedEffect(isMuted) {
        withContext(Dispatchers.Main) {
            player.volume = if (isMuted) 0f else 1f
        }
    }

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

            val resolvedStream = RemoteTrailerUrl.resolve(trailerUrl, maxVideoHeight)

            withContext(Dispatchers.Main) {
                if (!resolvedStream.audioUrl.isNullOrBlank()) {
                    val mediaSourceFactory = DefaultMediaSourceFactory(context)
                    val videoItem = MediaItem.Builder()
                        .setUri(resolvedStream.url)
                        .apply { resolvedStream.mimeType?.let { setMimeType(it) } }
                        .build()
                    val audioItem = MediaItem.Builder()
                        .setUri(resolvedStream.audioUrl)
                        .apply { resolvedStream.audioMimeType?.let { setMimeType(it) } }
                        .build()
                    val videoSource = mediaSourceFactory.createMediaSource(videoItem)
                    val audioSource = mediaSourceFactory.createMediaSource(audioItem)
                    player.setMediaSource(MergingMediaSource(videoSource, audioSource))
                } else {
                    val mediaItem = MediaItem.Builder()
                        .setUri(resolvedStream.url)
                        .apply { resolvedStream.mimeType?.let { setMimeType(it) } }
                        .build()
                    player.setMediaItem(mediaItem)
                }
                player.prepare()
                player.playWhenReady = false
                player.volume = if (isMuted) 0f else 1f
            }

            isLoading.value = false
        } catch (e: Exception) {
            errorState.value = e
            isLoading.value = false
            onError(e)
        }
    }

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
                    this.player = player
                    alpha = 0f
                    keepScreenOn = true
                }
            },
            update = { view ->
                view.alpha = playerAlpha
                view.keepScreenOn = isVisible && !playbackCompleted.value
            },
            modifier = Modifier.matchParentSize()
        )

        if (shouldBeVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { isMuted = !isMuted },
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
