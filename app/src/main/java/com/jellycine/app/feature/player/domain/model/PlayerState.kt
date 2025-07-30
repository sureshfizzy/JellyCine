package com.jellycine.app.feature.player.domain.model

/**
 * Represents the current state of the video player
 */
data class PlayerState(
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val error: String? = null,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPercentage: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val volume: Float = 1.0f,
    val isFullscreen: Boolean = false,
    val selectedAudioTrack: AudioTrackInfo? = null,
    val selectedVideoTrack: VideoTrackInfo? = null,
    val availableAudioTracks: List<AudioTrackInfo> = emptyList(),
    val availableVideoTracks: List<VideoTrackInfo> = emptyList()
)

/**
 * Information about an audio track
 */
data class AudioTrackInfo(
    val id: String,
    val label: String,
    val language: String?,
    val channelCount: Int,
    val sampleRate: Int,
    val bitrate: Int?,
    val codec: String?,
    val isSpatialAudio: Boolean = false
)

/**
 * Information about a video track
 */
data class VideoTrackInfo(
    val id: String,
    val label: String,
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val bitrate: Int?,
    val codec: String?
)

/**
 * Spatial audio capabilities
 */
data class SpatialAudioCapabilities(
    val isSupported: Boolean,
    val isAvailable: Boolean,
    val isEnabled: Boolean,
    val hasHeadTracking: Boolean,
    val supportedFormats: List<String>
)
