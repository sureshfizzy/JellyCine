package com.jellycine.player

import com.jellycine.detail.SpatializationResult

/**
 * Player state data class
 */
data class PlayerState(
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val error: String? = null,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPercentage: Int = 0,
    val volume: Float = 1.0f,
    val brightness: Float = 0.5f,
    val showControls: Boolean = true,
    val isSeekingGesture: Boolean = false,
    val isVolumeGesture: Boolean = false,
    val isBrightnessGesture: Boolean = false,
    val gestureSeekPosition: Long = 0L,
    val gestureVolume: Float = 1.0f,
    val gestureBrightness: Float = 0.5f,
    // Spatial audio related fields
    val spatializationResult: SpatializationResult? = null,
    val isSpatialAudioEnabled: Boolean = false,
    val spatialAudioFormat: String = "",
    val hasHeadTracking: Boolean = false
)

/**
 * Audio track information
 */
data class AudioTrackInfo(
    val id: String,
    val label: String,
    val language: String?,
    val channelCount: Int,
    val codec: String?
)

/**
 * Video track information
 */
data class VideoTrackInfo(
    val id: String,
    val label: String,
    val width: Int,
    val height: Int,
    val codec: String?
)
