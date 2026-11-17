package com.jellycine.player.core

/**
 * Platform-neutral playback engine. Android implements it over libmpv
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
        startPlayback: Boolean
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
}
