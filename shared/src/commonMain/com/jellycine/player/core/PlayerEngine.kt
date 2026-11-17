package com.jellycine.player.core

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-neutral playback engine. Android (MpvPlayerController) and iOS (IosMpvEngine) both
 * implement it over libmpv.
 */
interface PlayerEngine {
    val isPlaying: Boolean
    val currentPosition: Long
    val bufferedPosition: Long
    val duration: Long

    fun load(
        url: String,
        subtitleUrls: List<String>,
        audioTrackId: String?,
        subtitleTrackId: String?,
        selectedSubtitleUrl: String?,
        startPositionMs: Long?,
        startPlayback: Boolean,
        requestHeaders: Map<String, String> = emptyMap()
    )

    fun setListener(listener: PlayerEngineListener)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun selectAudioTrack(trackId: String)
    fun selectSubtitleTrack(trackId: String, externalUrl: String?)
    fun setZoomMode(enabled: Boolean)
    fun applySubtitlePreferences()
    fun release()
}

interface PlayerEngineListener {
    fun onBuffering()
    fun onReady()
    fun onEnded()
    fun onError(message: String) {}
}

/**
 * What the player screen needs from whatever is running playback. The Android view model and
 * the iOS controller both implement it, so the same screen can drive either.
 */
interface PlayerController {
    val playerState: StateFlow<PlayerState>

    fun getDuration(): Long
    fun getCurrentPosition(): Long
    fun getBufferedPosition(): Long
    fun isPlayingNow(): Boolean

    fun play()
    fun pause()
    fun togglePlayPause()
    fun seekTo(position: Long)
    fun seekToProgress(progress: Float)
    fun seekBy(deltaMs: Long)
    fun seekBackward()
    fun seekForward()

    fun toggleLock()
    fun cycleAspectRatio()
    fun handlePinchZoom(isZooming: Boolean)

    fun selectAudioTrack(trackId: String)
    fun selectSubtitleTrack(trackId: String)

    fun releasePlayer()
}
