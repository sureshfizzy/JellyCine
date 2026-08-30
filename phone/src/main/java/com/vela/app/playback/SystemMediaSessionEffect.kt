package com.vela.app.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun SystemMediaSessionEffect(
    mediaId: String?,
    title: String,
    subtitle: String?,
    durationMs: Long,
    playing: Boolean,
    artworkUrl: String?,
    canSkip: Boolean,
    positionProvider: () -> Long,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    val playHandler = rememberUpdatedState(onPlay)
    val pauseHandler = rememberUpdatedState(onPause)
    val seekHandler = rememberUpdatedState(onSeek)
    val nextHandler = rememberUpdatedState(onSkipNext)
    val previousHandler = rememberUpdatedState(onSkipPrevious)
    val stopHandler = rememberUpdatedState(onStop)
    val positionHandler = rememberUpdatedState(positionProvider)
    val controller = remember {
        object : PlaybackMediaSession.Controller {
            override fun play() = playHandler.value()
            override fun pause() = pauseHandler.value()
            override fun seekTo(positionMs: Long) = seekHandler.value(positionMs)
            override fun skipToNext() = nextHandler.value()
            override fun skipToPrevious() = previousHandler.value()
            override fun stop() = stopHandler.value()
        }
    }

    DisposableEffect(Unit) {
        PlaybackMediaSession.attach(context, controller)
        onDispose {
            PlaybackMediaSession.detach(context, controller)
        }
    }

    fun publish() {
        if (title.isBlank()) return
        PlaybackMediaSession.update(
            context = context,
            mediaId = mediaId,
            title = title,
            subtitle = subtitle,
            durationMs = durationMs,
            positionMs = positionHandler.value(),
            playing = playing,
            artworkUrl = artworkUrl,
            canSkip = canSkip
        )
    }

    LaunchedEffect(mediaId, title, subtitle, durationMs, playing, artworkUrl, canSkip) {
        publish()
    }

    LaunchedEffect(playing, mediaId, title) {
        if (!playing || title.isBlank()) return@LaunchedEffect
        while (isActive) {
            delay(1_000)
            publish()
        }
    }
}
