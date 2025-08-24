package com.jellycine.app.ui.screens.player

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.player.PlayerState
import com.jellycine.player.PlayerUtils
import com.jellycine.player.SpatializerHelper
import com.jellycine.player.SpatializerStateListener
import com.jellycine.detail.CodecCapabilityManager
import com.jellycine.detail.SpatializationResult
import android.media.Spatializer

/**
 * Player ViewModel
 */
@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor() : ViewModel() {

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
    
    private val _showSpatialAudioInfo = MutableStateFlow(false)
    val showSpatialAudioInfo: StateFlow<Boolean> = _showSpatialAudioInfo.asStateFlow()

    var exoPlayer: ExoPlayer? = null
        private set
    
    private var spatializerHelper: SpatializerHelper? = null
    private lateinit var mediaRepository: MediaRepository
    private var playerContext: Context? = null

    fun initializePlayer(context: Context, mediaId: String) {
        viewModelScope.launch {
            try {
                _playerState.value = _playerState.value.copy(isLoading = true, error = null)

                playerContext = context // Store context for later use
                mediaRepository = MediaRepositoryProvider.getInstance(context)
                
                // Initialize spatial audio monitoring
                spatializerHelper = SpatializerHelper(context)
                setupSpatializerListener()
                
                exoPlayer = PlayerUtils.createPlayer(context)

                val streamingResult = mediaRepository.getStreamingUrl(mediaId)
                if (streamingResult.isFailure) {
                    val error = streamingResult.exceptionOrNull()?.message ?: "Failed to get streaming URL"
                    _playerState.value = _playerState.value.copy(isLoading = false, error = error)
                    return@launch
                }

                val streamingUrl = streamingResult.getOrNull()!!
                val mediaItem = MediaItem.fromUri(streamingUrl)
                
                // Get media info for spatial audio analysis
                val playbackInfoResult = mediaRepository.getPlaybackInfo(mediaId)
                
                val spatializationResult = playbackInfoResult.getOrNull()?.let { playbackInfo ->
                    playbackInfo.mediaSources?.firstOrNull()?.mediaStreams?.let { streams ->
                        val audioStreams = streams.filter { it.type == "Audio" }
                        val primaryAudioStream = audioStreams.firstOrNull()
                        if (primaryAudioStream != null) {
                            val result = CodecCapabilityManager.canSpatializeAudioStream(context, primaryAudioStream)
                            result
                        } else {
                            null
                        }
                    }
                }
                
                exoPlayer?.apply {
                    setMediaItem(mediaItem)
                    prepare()
                    playWhenReady = true
                    addListener(playerListener)
                }

                // Spatial audio analysis and device capabilities
                val spatialInfo = spatializerHelper?.getSpatialAudioInfo()
                val contentSupportsSpatialization = spatializationResult?.canSpatialize == true
                val deviceSupportsSpatialization = spatialInfo?.isAvailable == true
                
                // Primary condition: Content format-based detection
                val hasCompatibleAudioFormat = spatializationResult?.let { result ->
                    val format = result.spatialFormat.lowercase()
                    // Check for specific spatial audio formats
                    format.contains("dolby atmos") || 
                    format.contains("atmos") ||
                    format.contains("dts:x") || 
                    format.contains("dts-x") ||
                    format.contains("5.1") || 
                    format.contains("7.1") ||
                    format.contains("360 reality") ||
                    format.contains("mpeg-h") ||
                    format.contains("auro-3d") ||
                    format.contains("ch") && result.spatialFormat.contains(Regex("\\d+ch")) // Multi-channel detection
                } ?: false
                
                // Enhanced condition: Content format OR successful spatialization detection
                val shouldEnableSpatialAudio = hasCompatibleAudioFormat || contentSupportsSpatialization
                
                // Additional enhancement when device supports spatial audio
                val deviceEnhancementAvailable = deviceSupportsSpatialization
                
                // Configure spatial audio effects based on content compatibility
                if (shouldEnableSpatialAudio) {
                    PlayerUtils.configureSpatialAudioForContent(exoPlayer!!, context, spatializationResult)
                }
                
                _playerState.value = _playerState.value.copy(
                    isLoading = false,
                    isPlaying = true,
                    spatializationResult = spatializationResult,
                    // Enable based on content format compatibility
                    isSpatialAudioEnabled = shouldEnableSpatialAudio,
                    spatialAudioFormat = spatializationResult?.spatialFormat ?: "Stereo",
                    hasHeadTracking = spatializationResult?.hasHeadTracking == true
                )

            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Player initialization failed", e)
                _playerState.value = _playerState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
        _playerState.value = _playerState.value.copy(currentPosition = position)
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume
        _playerState.value = _playerState.value.copy(volume = volume)
    }

    fun setBrightness(brightness: Float) {
        _playerState.value = _playerState.value.copy(brightness = brightness)
    }

    fun toggleControls() {
        _playerState.value = _playerState.value.copy(showControls = !_playerState.value.showControls)
    }

    fun releasePlayer() {
        exoPlayer?.apply {
            removeListener(playerListener)
            release()
        }
        exoPlayer = null
        spatializerHelper?.cleanup()
        spatializerHelper = null
        _playerState.value = PlayerState()
    }

    fun clearError() {
        _playerState.value = _playerState.value.copy(error = null)
    }
    
    fun showSpatialAudioInfo() {
        _showSpatialAudioInfo.value = true
    }
    
    fun hideSpatialAudioInfo() {
        _showSpatialAudioInfo.value = false
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _playerState.value = _playerState.value.copy(
                isLoading = playbackState == Player.STATE_BUFFERING,
                isPlaying = playbackState == Player.STATE_READY && exoPlayer?.playWhenReady == true
            )
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _playerState.value = _playerState.value.copy(
                error = error.message ?: "Playback error occurred",
                isLoading = false,
                isPlaying = false
            )
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            _playerState.value = _playerState.value.copy(
                currentPosition = newPosition.positionMs,
                duration = exoPlayer?.duration ?: 0L
            )
        }
    }

    private fun setupSpatializerListener() {
        spatializerHelper?.addSpatializerStateListener(object : SpatializerStateListener {
            override fun onSpatializerStateChanged(spatializer: Spatializer, state: Int) {
                val spatialInfo = spatializerHelper?.getSpatialAudioInfo()
                val currentState = _playerState.value
                
                // Enhanced content-aware detection
                val contentSupportsSpatialization = currentState.spatializationResult?.canSpatialize == true
                val hasCompatibleAudioFormat = currentState.spatializationResult?.let { result ->
                    val format = result.spatialFormat.lowercase()
                    format.contains("dolby atmos") || format.contains("atmos") ||
                    format.contains("dts:x") || format.contains("dts-x") ||
                    format.contains("5.1") || format.contains("7.1") ||
                    format.contains("360 reality") || format.contains("mpeg-h") ||
                    format.contains("auro-3d") || 
                    (format.contains("ch") && result.spatialFormat.contains(Regex("\\d+ch")))
                } ?: false
                
                // Primary: Content format compatibility
                val shouldEnable = hasCompatibleAudioFormat || contentSupportsSpatialization
                val deviceSupportsSpatialization = spatialInfo?.isAvailable == true
                
                _playerState.value = _playerState.value.copy(
                    isSpatialAudioEnabled = shouldEnable,
                    hasHeadTracking = spatialInfo?.hasHeadTracker == true
                )
            }
            
            override fun onHeadTrackerAvailableChanged(spatializer: Spatializer, available: Boolean) {
                _playerState.value = _playerState.value.copy(hasHeadTracking = available)
            }
        })
    }
    
    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }

    fun getSpatialAudioStatusInfo(): String {
        val currentState = _playerState.value
        val spatializationResult = currentState.spatializationResult
        
        return if (currentState.isSpatialAudioEnabled) {
            buildString {
                appendLine("Spatial Audio: ACTIVE")
                appendLine("")
                appendLine("Format: ${currentState.spatialAudioFormat}")
                appendLine("Head Tracking: ${if (currentState.hasHeadTracking) "Yes" else "No"}")
                appendLine("")
                appendLine("Content contains compatible spatial")
                appendLine("audio format. Effects are applied")
                appendLine("based on content characteristics.")
                
                // Show device enhancement status if available
                spatializerHelper?.getSpatialAudioInfo()?.let { spatialInfo ->
                    if (spatialInfo.isAvailable) {
                        appendLine("")
                        appendLine("+ Device spatial enhancement: Active")
                    }
                }
            }
        } else {
            val reason = spatializationResult?.reason ?: "Content does not support spatial audio"
            buildString {
                appendLine("Spatial Audio: NOT AVAILABLE")
                appendLine("")
                appendLine("Reason: $reason")
                appendLine("")
                appendLine("Spatial audio requires compatible")
                appendLine("content formats like:")
                appendLine("• Dolby Atmos / E-AC-3 JOC")
                appendLine("• DTS:X")
                appendLine("• Multi-channel (5.1+)")
                appendLine("• Object-based audio")
            }
        }
    }
}
