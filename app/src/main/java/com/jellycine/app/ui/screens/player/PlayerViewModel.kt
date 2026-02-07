package com.jellycine.app.ui.screens.player

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import javax.inject.Inject
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.data.model.MediaStream
import com.jellycine.player.core.PlayerState
import com.jellycine.player.core.PlayerUtils
import com.jellycine.player.audio.SpatializerHelper
import com.jellycine.player.audio.SpatializerStateListener
import com.jellycine.detail.CodecCapabilityManager
import com.jellycine.detail.CodecUtils
import com.jellycine.detail.SpatializationResult
import com.jellycine.player.video.HdrCapabilityManager
import android.media.Spatializer
import com.jellycine.app.ui.screens.player.MediaMetadataInfo
import com.jellycine.app.ui.screens.player.SpatialAudioInfo
import com.jellycine.app.ui.screens.player.HdrFormatInfo
import com.jellycine.app.ui.screens.player.VideoFormatInfo
import com.jellycine.app.ui.screens.player.AudioFormatInfo
import com.jellycine.app.ui.screens.player.HardwareAccelerationInfo

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
    private var apiMediaStreams: List<MediaStream>? = null
    private var currentMediaId: String? = null
    private var playSessionId: String? = null
    private var mediaSourceId: String? = null
    private var progressReportingJob: kotlinx.coroutines.Job? = null
    private var hasReportedStart = false

    fun initializePlayer(context: Context, mediaId: String) {
        viewModelScope.launch {
            try {
                _playerState.value = _playerState.value.copy(isLoading = true, error = null)

                playerContext = context // Store context for later use
                currentMediaId = mediaId // Store media ID for session reporting
                mediaRepository = MediaRepositoryProvider.getInstance(context)
                
                // Initialize spatial audio monitoring
                spatializerHelper = SpatializerHelper(context)
                setupSpatializerListener()
                
                exoPlayer = PlayerUtils.createPlayer(context)

                // Get playback info first to obtain session details and check for resume position
                val playbackInfoResult = mediaRepository.getPlaybackInfo(mediaId)
                if (playbackInfoResult.isFailure) {
                    val error = playbackInfoResult.exceptionOrNull()?.message ?: "Failed to get playback info"
                    _playerState.value = _playerState.value.copy(isLoading = false, error = error)
                    return@launch
                }

                val playbackInfo = playbackInfoResult.getOrNull()!!
                playSessionId = playbackInfo.playSessionId
                mediaSourceId = playbackInfo.mediaSources?.firstOrNull()?.id

                // Get item details to check for resume position
                val itemResult = mediaRepository.getItemById(mediaId)
                val resumePositionTicks = itemResult.getOrNull()?.userData?.playbackPositionTicks

                val streamingResult = mediaRepository.getStreamingUrl(mediaId)
                if (streamingResult.isFailure) {
                    val error = streamingResult.exceptionOrNull()?.message ?: "Failed to get streaming URL"
                    _playerState.value = _playerState.value.copy(isLoading = false, error = error)
                    return@launch
                }

                val streamingUrl = streamingResult.getOrNull()!!
                val mediaItem = MediaItem.fromUri(streamingUrl)
                
                // Get media info for spatial audio analysis
                apiMediaStreams = playbackInfo.mediaSources?.firstOrNull()?.mediaStreams

                val spatializationResult = apiMediaStreams?.let { streams ->
                    val audioStreams = streams.filter { it.type == "Audio" }
                    val primaryAudioStream = audioStreams.firstOrNull()
                    if (primaryAudioStream != null) {
                        val result = CodecCapabilityManager.canSpatializeAudioStream(context, primaryAudioStream)
                        result
                    } else {
                        null
                    }
                }
                
                exoPlayer?.apply {
                    setMediaItem(mediaItem)
                    prepare()

                    if (resumePositionTicks != null && resumePositionTicks > 0) {
                        val resumePositionMs = resumePositionTicks / 10000L // Convert ticks to milliseconds
                        seekTo(resumePositionMs)
                    }
                    
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
                val contentBasedSpatialAudio = hasCompatibleAudioFormat || contentSupportsSpatialization
                
                // Check user preference for spatial audio
                val playerPreferences = com.jellycine.player.preferences.PlayerPreferences(context)
                val userSpatialAudioEnabled = playerPreferences.isSpatialAudioEnabled()
                
                // Final decision: Content supports AND user has enabled spatial audio
                val shouldEnableSpatialAudio = contentBasedSpatialAudio && userSpatialAudioEnabled
                
                // Additional enhancement when device supports spatial audio
                val deviceEnhancementAvailable = deviceSupportsSpatialization
                
                // Configure spatial audio effects based on content compatibility
                if (shouldEnableSpatialAudio) {
                    PlayerUtils.configureSpatialAudioForContent(exoPlayer!!, context, spatializationResult)
                }

                // Update track information after player is ready
                updateTrackInformation()
                
                // Apply start maximized setting if enabled
                applyStartMaximizedSetting(context)

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
            
            // Report progress on pause/play
            if (hasReportedStart) {
                viewModelScope.launch {
                    reportPlaybackProgress()
                }
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
        // Report playback stopped before releasing
        reportPlaybackStopped()
        progressReportingJob?.cancel()
        progressReportingJob = null
        
        exoPlayer?.apply {
            removeListener(playerListener)
            release()
        }
        exoPlayer = null
        spatializerHelper?.cleanup()
        spatializerHelper = null
        
        // Reset session tracking variables
        currentMediaId = null
        playSessionId = null
        mediaSourceId = null
        hasReportedStart = false
        
        _playerState.value = PlayerState()
    }

    /**
     * Report playback start to Jellyfin server
     */
    private fun reportPlaybackStart() {
        currentMediaId?.let { mediaId ->
            if (!hasReportedStart) {
                viewModelScope.launch {
                    try {
                        val currentPos = exoPlayer?.currentPosition ?: 0L
                        val positionTicks = currentPos * 10000L // Convert ms to ticks
                        
                        val result = mediaRepository.reportPlaybackStart(
                            itemId = mediaId,
                            playSessionId = playSessionId,
                            mediaSourceId = mediaSourceId,
                            positionTicks = positionTicks,
                            playMethod = "DirectPlay"
                        )
                        
                        if (result.isSuccess) {
                            hasReportedStart = true
                            startProgressReporting()
                        } else {
                            Log.e("PlayerViewModel", "Failed to report playback start: ${result.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Log.e("PlayerViewModel", "Error reporting playback start", e)
                    }
                }
            }
        }
    }

    /**
     * Start periodic progress reporting
     */
    private fun startProgressReporting() {
        progressReportingJob?.cancel()
        progressReportingJob = viewModelScope.launch {
            while (hasReportedStart && exoPlayer != null) {
                try {
                    delay(15000L)
                    reportPlaybackProgress()
                } catch (e: Exception) {
                    Log.e("PlayerViewModel", "Error in progress reporting loop", e)
                    break
                }
            }
        }
    }

    /**
     * Report current playback progress to Jellyfin server
     */
    private fun reportPlaybackProgress() {
        currentMediaId?.let { mediaId ->
            if (hasReportedStart && exoPlayer != null) {
                viewModelScope.launch {
                    try {
                        val currentPos = exoPlayer?.currentPosition ?: 0L
                        val positionTicks = currentPos * 10000L
                        val isPaused = exoPlayer?.playWhenReady != true
                        
                        val result = mediaRepository.reportPlaybackProgress(
                            itemId = mediaId,
                            positionTicks = positionTicks,
                            playSessionId = playSessionId,
                            mediaSourceId = mediaSourceId,
                            isPaused = isPaused,
                            playMethod = "DirectPlay"
                        )
                        
                        if (result.isSuccess) {
                            // Progress reported successfully
                        } else {
                            Log.e("PlayerViewModel", "Failed to report progress: ${result.exceptionOrNull()?.message}")
                        }
                    } catch (e: Exception) {
                        Log.e("PlayerViewModel", "Error reporting playback progress", e)
                    }
                }
            }
        }
    }

    /**
     * Report playback stopped to Jellyfin server
     */
    private fun reportPlaybackStopped() {
        currentMediaId?.let { mediaId ->
            if (hasReportedStart) {
                viewModelScope.launch {
                    try {
                        val currentPos = exoPlayer?.currentPosition ?: 0L
                        val positionTicks = currentPos * 10000L // Convert ms to ticks
                        
                        val result = mediaRepository.reportPlaybackStopped(
                            itemId = mediaId,
                            positionTicks = positionTicks,
                            playSessionId = playSessionId,
                            mediaSourceId = mediaSourceId
                        )
                        
                        if (result.isSuccess) {
                            // Playback stopped reported successfully
                        } else {
                            Log.e("PlayerViewModel", "Failed to report playback stopped: ${result.exceptionOrNull()?.message}")
                        }
                        
                        hasReportedStart = false
                    } catch (e: Exception) {
                        Log.e("PlayerViewModel", "Error reporting playback stopped", e)
                    }
                }
            }
        }
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

    /**
     * Toggle lock state - when locked, disable all gestures and hide controls
     */
    fun toggleLock() {
        val currentState = _playerState.value
        _playerState.value = currentState.copy(
            isLocked = !currentState.isLocked,
            showControls = if (!currentState.isLocked) false else currentState.showControls
        )
    }

    /**
     * Update track information from ExoPlayer
     */
    private fun updateTrackInformation() {
        exoPlayer?.let { player ->
            try {
                val audioTracks = PlayerUtils.getAvailableAudioTracks(player)
                val subtitleTracks = PlayerUtils.getAvailableSubtitleTracks(player)
                val videoTracks = PlayerUtils.getAvailableVideoTracks(player)
                val currentAudio = PlayerUtils.getCurrentAudioTrack(player)
                val currentSubtitle = PlayerUtils.getCurrentSubtitleTrack(player)

                _playerState.value = _playerState.value.copy(
                    availableAudioTracks = audioTracks,
                    currentAudioTrack = currentAudio,
                    availableSubtitleTracks = subtitleTracks,
                    currentSubtitleTrack = currentSubtitle,
                    availableVideoTracks = videoTracks
                )
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Failed to update track information", e)
            }
        }
    }

    /**
     * Select audio track by ID
     */
    fun selectAudioTrack(trackId: String) {
        exoPlayer?.let { player ->
            PlayerUtils.selectAudioTrack(player, trackId)
            viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                updateTrackInformation()
            }
        }
    }

    /**
     * Select subtitle track by ID
     */
    fun selectSubtitleTrack(trackId: String) {
        exoPlayer?.let { player ->
            PlayerUtils.selectSubtitleTrack(player, trackId)
            viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                updateTrackInformation()
            }
        }
    }

    // Aspect ratio states
    private var currentAspectRatio by mutableIntStateOf(0)
    private val aspectRatioModes = listOf("Fit", "Zoom")
    
    // Keep track of ExoPlayer resize mode
    private var currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT

    /**
     * Toggle between fit and zoom modes
     * Uses ExoPlayer's native AspectRatioFrameLayout resize modes for proper aspect ratio handling
     */
    fun cycleAspectRatio() {
        currentAspectRatio = (currentAspectRatio + 1) % aspectRatioModes.size
        
        currentResizeMode = when (currentAspectRatio) {
            0 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT // Respects video aspect ratio
            1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM // Fills screen, crops if needed
            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        
        val mode = aspectRatioModes[currentAspectRatio]
        _playerState.value = _playerState.value.copy(
            aspectRatioMode = mode,
            videoScale = 1f,
            videoOffsetX = 0f,
            videoOffsetY = 0f
        )
    }
    
    /**
     * Get current resize mode for VideoSurface
     */
    fun getCurrentResizeMode(): Int = currentResizeMode
    
    /**
     * Handle pinch-to-zoom gesture to set appropriate resize mode
     */
    fun handlePinchZoom(isZooming: Boolean) {
        if (isZooming && currentAspectRatio == 0) {
            currentAspectRatio = 1
            currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            _playerState.value = _playerState.value.copy(
                aspectRatioMode = "Zoom",
                videoScale = 1f,
                videoOffsetX = 0f,
                videoOffsetY = 0f
            )
        } else if (!isZooming && currentAspectRatio == 1) {
            // Switch to fit mode when user pinches to fit
            currentAspectRatio = 0
            currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            _playerState.value = _playerState.value.copy(
                aspectRatioMode = "Fit",
                videoScale = 1f,
                videoOffsetX = 0f,
                videoOffsetY = 0f
            )
        }
    }

    /**
     * Apply start maximized setting based on user preference
     * Uses ExoPlayer's native resize modes for proper aspect ratio handling
     */
    private fun applyStartMaximizedSetting(context: Context) {
        val playerPreferences = com.jellycine.player.preferences.PlayerPreferences(context)
        val startMaximized = playerPreferences.isStartMaximizedEnabled()
        
        if (startMaximized) {
            currentAspectRatio = 1 // Zoom mode
            currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            _playerState.value = _playerState.value.copy(
                aspectRatioMode = "Zoom",
                videoScale = 1f,
                videoOffsetX = 0f,
                videoOffsetY = 0f
            )
        } else {
            currentAspectRatio = 0 // Fit mode
            currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            _playerState.value = _playerState.value.copy(
                aspectRatioMode = "Fit",
                videoScale = 1f,
                videoOffsetX = 0f,
                videoOffsetY = 0f
            )
        }
    }

    /**
     * Update video transform values
     */
    fun updateVideoTransform(scale: Float, offsetX: Float, offsetY: Float) {
        val mode = aspectRatioModes[currentAspectRatio]
        _playerState.value = _playerState.value.copy(
            videoScale = scale,
            videoOffsetX = offsetX,
            videoOffsetY = offsetY,
            aspectRatioMode = mode
        )
    }

    /**
     * Seek backward by 30 seconds
     */
    fun seekBackward() {
        exoPlayer?.let { player ->
            val currentPos = player.currentPosition
            val newPos = (currentPos - 30000L).coerceAtLeast(0L)
            player.seekTo(newPos)
        }
    }

    /**
     * Seek forward by 30 seconds
     */
    fun seekForward() {
        exoPlayer?.let { player ->
            val currentPos = player.currentPosition
            val duration = player.duration
            val newPos = if (duration > 0) {
                (currentPos + 30000L).coerceAtMost(duration)
            } else {
                currentPos + 30000L
            }
            player.seekTo(newPos)
        }
    }

    /**
     * Go to previous track/episode (placeholder)
     */
    fun goToPrevious() {
        // Placeholder for previous track functionality
    }

    /**
     * Go to next track/episode (placeholder)
     */
    fun goToNext() {
        // Placeholder for next track functionality
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val wasPlaying = _playerState.value.isPlaying
            val isNowPlaying = playbackState == Player.STATE_READY && exoPlayer?.playWhenReady == true
            
            _playerState.value = _playerState.value.copy(
                isLoading = playbackState == Player.STATE_BUFFERING,
                isPlaying = isNowPlaying
            )

            // Report playback start when player becomes ready and starts playing
            if (playbackState == Player.STATE_READY && isNowPlaying && !hasReportedStart) {
                reportPlaybackStart()
            }
            
            // Report progress when pause/resume state changes
            if (hasReportedStart && wasPlaying != isNowPlaying) {
                viewModelScope.launch {
                    reportPlaybackProgress()
                }
            }

            // Update track information when player becomes ready
            if (playbackState == Player.STATE_READY) {
                updateTrackInformation()
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _playerState.value = _playerState.value.copy(
                error = error.message ?: "Playback error occurred",
                isLoading = false,
                isPlaying = false
            )
            
            // Report playback stopped due to error
            if (hasReportedStart) {
                viewModelScope.launch {
                    currentMediaId?.let { mediaId ->
                        mediaRepository.reportPlaybackStopped(
                            itemId = mediaId,
                            positionTicks = (exoPlayer?.currentPosition ?: 0L) * 10000L,
                            playSessionId = playSessionId,
                            mediaSourceId = mediaSourceId,
                            failed = true
                        )
                    }
                }
            }
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
            
            // Report progress on significant position changes (seeking)
            if (hasReportedStart && reason == Player.DISCONTINUITY_REASON_SEEK) {
                viewModelScope.launch {
                    reportPlaybackProgress()
                }
            }
        }
    }

    private fun setupSpatializerListener() {
        spatializerHelper?.addSpatializerStateListener(object : SpatializerStateListener {
            override fun onSpatializerAvailabilityChanged(isAvailable: Boolean) {
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
                
                _playerState.value = _playerState.value.copy(
                    isSpatialAudioEnabled = shouldEnable,
                    hasHeadTracking = spatialInfo?.hasHeadTracker == true
                )
            }
            
            override fun onSpatializerEnabledChanged(isEnabled: Boolean) {
                val spatialInfo = spatializerHelper?.getSpatialAudioInfo()
                _playerState.value = _playerState.value.copy(hasHeadTracking = spatialInfo?.hasHeadTracker == true)
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

    /**
     * Get HDR capability and format information
     */
    fun getHdrFormatInfo(): String {
        return playerContext?.let { context ->
            buildString {
                appendLine("=== HDR & Video Format Information ===")
                appendLine("")

                // Device capabilities
                val hdrInfo = PlayerUtils.getHdrCapabilityInfo(context)
                appendLine(hdrInfo)
                appendLine("")

                // Current video track information if available
                exoPlayer?.currentTracks?.let { tracks ->
                    tracks.groups.forEach { group ->
                        if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                            for (i in 0 until group.mediaTrackGroup.length) {
                                if (group.isTrackSelected(i)) {
                                    val format = group.mediaTrackGroup.getFormat(i)
                                    appendLine("=== Current Video Format ===")
                                    appendLine("MIME Type: ${format.sampleMimeType}")
                                    appendLine("Codec: ${format.codecs ?: "Unknown"}")
                                    appendLine("Resolution: ${format.width}x${format.height}")
                                    appendLine("Color Info: ${format.colorInfo?.toString() ?: "None"}")
                                    appendLine("")

                                    // Analyze format fallback
                                    val analysisResult = PlayerUtils.analyzeVideoFormatForPlayback(
                                        context, format.sampleMimeType, format.codecs, format.colorInfo?.toString()
                                    )
                                    appendLine("=== Format Analysis ===")
                                    appendLine(analysisResult)
                                    break
                                }
                            }
                        }
                    }
                }

                appendLine("")
                appendLine("Note: This player automatically handles")
                appendLine("HDR format fallbacks to prevent black screens.")
            }
        } ?: "HDR info not available - player not initialized"
    }

    /**
     * Get unified media metadata information for the modern bubble dialog
     */
    fun getMediaMetadataInfo(): MediaMetadataInfo {
        val currentState = _playerState.value
        val spatializationResult = currentState.spatializationResult

        // Spatial Audio Info
        val spatialAudioInfo = SpatialAudioInfo(
            isActive = currentState.isSpatialAudioEnabled,
            format = currentState.spatialAudioFormat,
            hasHeadTracking = currentState.hasHeadTracking,
            deviceEnhancement = spatializerHelper?.getSpatialAudioInfo()?.isAvailable == true,
            reason = if (!currentState.isSpatialAudioEnabled) {
                spatializationResult?.reason ?: "Content does not support spatial audio"
            } else null
        )

        // HDR Format Info
        val hdrFormatInfo = playerContext?.let { context ->
            val deviceHdrInfo = PlayerUtils.getHdrCapabilityInfo(context)
            val deviceSupportsHdr = deviceHdrInfo.contains("HDR10") || deviceHdrInfo.contains("Dolby Vision") || deviceHdrInfo.contains("HDR")

            var currentFormat: String? = null
            var isContentHdr = false
            var analysisResult: String? = null
            var originalContentFormat: String? = null

            exoPlayer?.currentTracks?.let { tracks ->
                tracks.groups.forEach { group ->
                    if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.mediaTrackGroup.length) {
                            if (group.isTrackSelected(i)) {
                                val format = group.mediaTrackGroup.getFormat(i)
                                val colorInfo = format.colorInfo?.toString()
                                val mimeType = format.sampleMimeType
                                val codecs = format.codecs

                                val isHdrByColorInfo = colorInfo?.contains("HDR", ignoreCase = true) == true ||
                                                     colorInfo?.contains("Dolby Vision", ignoreCase = true) == true ||
                                                     colorInfo?.contains("SMPTE2084", ignoreCase = true) == true ||
                                                     colorInfo?.contains("BT.2020", ignoreCase = true) == true ||
                                                     colorInfo?.contains("PQ", ignoreCase = true) == true

                                val isHdrByMimeType = mimeType?.contains("dolby-vision", ignoreCase = true) == true ||
                                                     mimeType?.contains("hdr", ignoreCase = true) == true

                                val isHdrByCodec = codecs?.contains("dvhe", ignoreCase = true) == true ||
                                                 codecs?.contains("dvh1", ignoreCase = true) == true ||
                                                 codecs?.contains("hev1", ignoreCase = true) == true ||
                                                 codecs?.contains("hvc1", ignoreCase = true) == true

                                // Get original content analysis for fallback detection
                                val videoFormatAnalysis = HdrCapabilityManager.analyzeVideoFormat(mimeType, codecs, colorInfo)
                                val bestFormat = HdrCapabilityManager.getBestPlayableFormat(context, videoFormatAnalysis)
                                originalContentFormat = videoFormatAnalysis.hdrSupport.displayName

                                // Determine if content is HDR
                                isContentHdr = isHdrByColorInfo || isHdrByMimeType || isHdrByCodec || videoFormatAnalysis.hdrSupport != HdrCapabilityManager.HdrSupport.SDR

                                if (isContentHdr) {
                                    currentFormat = when {
                                        colorInfo?.contains("Dolby Vision", ignoreCase = true) == true -> "Dolby Vision"
                                        codecs?.contains("dvhe", ignoreCase = true) == true || codecs?.contains("dvh1", ignoreCase = true) == true -> "Dolby Vision"
                                        colorInfo?.contains("HDR10+", ignoreCase = true) == true -> "HDR10+"
                                        colorInfo?.contains("HDR10", ignoreCase = true) == true || colorInfo?.contains("SMPTE2084", ignoreCase = true) == true -> "HDR10"
                                        colorInfo?.contains("HLG", ignoreCase = true) == true -> "HLG"
                                        else -> "HDR"
                                    }
                                }

                                // Get detailed analysis including fallback information
                                analysisResult = if (videoFormatAnalysis.hdrSupport != bestFormat.hdrSupport) {
                                    "Content: ${originalContentFormat} → Playing: ${bestFormat.hdrSupport.displayName} (fallback applied)"
                                } else if (isContentHdr) {
                                    "Playing in native ${currentFormat} format"
                                } else {
                                    "Standard Dynamic Range (SDR)"
                                }

                                break
                            }
                        }
                    }
                }
            }

            HdrFormatInfo(
                isSupported = isContentHdr,
                currentFormat = currentFormat,
                deviceCapabilities = if (deviceSupportsHdr) "Yes" else "No",
                analysisResult = analysisResult
            )
        }

        // Video Format Info
        val videoFormatInfo = apiMediaStreams?.find { it.type == "Video" }?.let { videoStream ->
            val rawCodec = videoStream.codec ?: "Unknown"
            val displayCodec = getDisplayVideoCodecName(rawCodec)
            VideoFormatInfo(
                codec = displayCodec,
                resolution = if (videoStream.width != null && videoStream.height != null) {
                    "${videoStream.width}x${videoStream.height}"
                } else "Unknown",
                mimeType = videoStream.codec?.let { "video/${it.lowercase()}" } ?: "Unknown",
                colorInfo = listOfNotNull(
                    videoStream.colorSpace,
                    videoStream.colorTransfer,
                    videoStream.colorPrimaries
                ).joinToString(", ").takeIf { it.isNotEmpty() }
            )
        }

        // Audio Format Info
        val audioFormatInfo = apiMediaStreams?.find { it.type == "Audio" }?.let { audioStream ->
            val channels = audioStream.channels?.let { channelCount ->
                when (channelCount) {
                    1 -> "Mono"
                    2 -> "Stereo"
                    6 -> "5.1"
                    8 -> "7.1"
                    else -> "${channelCount}ch"
                }
            } ?: "Unknown"

            val rawCodec = audioStream.codec ?: "Unknown"
            val displayCodec = getDisplayAudioCodecName(rawCodec)

            AudioFormatInfo(
                codec = displayCodec,
                channels = channels,
                bitrate = audioStream.bitRate?.let { "${it / 1000} kbps" },
                sampleRate = audioStream.sampleRate?.let { "${it / 1000} kHz" }
            )
        }

        // Hardware Acceleration Info
        val hardwareAccelerationInfo = playerContext?.let { context ->
            val playerPreferences = com.jellycine.player.preferences.PlayerPreferences(context)
            val isHwAccelEnabled = playerPreferences.isHardwareAccelerationEnabled()
            val isAsyncEnabled = playerPreferences.isAsyncMediaCodecEnabled()
            val decoderPriority = playerPreferences.getDecoderPriority()
            
            // Get active codec information from current tracks
            var activeVideoCodec: String? = null
            var activeAudioCodec: String? = null
            var isUsingHardwareDecoder = false
            
            exoPlayer?.currentTracks?.let { tracks ->
                tracks.groups.forEach { group ->
                    when (group.type) {
                        androidx.media3.common.C.TRACK_TYPE_VIDEO -> {
                            for (i in 0 until group.mediaTrackGroup.length) {
                                if (group.isTrackSelected(i)) {
                                    val format = group.mediaTrackGroup.getFormat(i)
                                    activeVideoCodec = getDisplayVideoCodecName(format.codecs ?: format.sampleMimeType ?: "Unknown")
                                    // Hardware decoding is likely if HW acceleration is enabled and format supports it
                                    isUsingHardwareDecoder = isHwAccelEnabled && 
                                        (format.sampleMimeType?.contains("video/") == true)
                                    break
                                }
                            }
                        }
                        androidx.media3.common.C.TRACK_TYPE_AUDIO -> {
                            for (i in 0 until group.mediaTrackGroup.length) {
                                if (group.isTrackSelected(i)) {
                                    val format = group.mediaTrackGroup.getFormat(i)
                                    activeAudioCodec = getDisplayAudioCodecName(format.codecs ?: format.sampleMimeType ?: "Unknown")
                                    break
                                }
                            }
                        }
                    }
                }
            }
            
            val decoderType = when {
                !isHwAccelEnabled -> "Software"
                isUsingHardwareDecoder -> "Hardware"
                decoderPriority == "Software First" -> "Software Preferred"
                else -> "Hardware Preferred"
            }
            
            HardwareAccelerationInfo(
                isHardwareDecoding = isHwAccelEnabled,
                activeVideoCodec = activeVideoCodec,
                activeAudioCodec = activeAudioCodec,
                decoderType = decoderType,
                asyncModeEnabled = isAsyncEnabled,
                performanceMetrics = if (isHwAccelEnabled) "GPU-accelerated" else "CPU-only"
            )
        }

        return MediaMetadataInfo(
            spatialAudio = spatialAudioInfo,
            hdrFormat = hdrFormatInfo,
            videoFormat = videoFormatInfo,
            audioFormat = audioFormatInfo,
            hardwareAcceleration = hardwareAccelerationInfo
        )
    }

    /**
     * Get display-friendly video codec name
     */
    private fun getDisplayVideoCodecName(codec: String): String {
        return when (codec.uppercase()) {
            "H264", "AVC", "AVC1" -> "H.264"
            "H265", "HEVC", "HEV1", "HVC1" -> "H.265"
            "VP8" -> "VP8"
            "VP9" -> "VP9"
            "AV1" -> "AV1"
            "MPEG2", "MPEG-2" -> "MPEG-2"
            "MPEG4", "MPEG-4" -> "MPEG-4"
            "XVID" -> "Xvid"
            "DIVX" -> "DivX"
            "WMV" -> "WMV"
            else -> codec.uppercase()
        }
    }

    /**
     * Get display-friendly audio codec name
     */
    private fun getDisplayAudioCodecName(codec: String): String {
        return when (codec.uppercase()) {
            "EAC3", "E-AC-3", "EC-3" -> "Dolby Digital+"
            "AC3", "AC-3" -> "Dolby Digital"
            "TRUEHD" -> "Dolby TrueHD"
            "DTS" -> "DTS"
            "DTSHD", "DTS-HD" -> "DTS-HD"
            "AAC", "MP4A", "MP4A.40.2" -> "AAC"
            "MP3" -> "MP3"
            "FLAC" -> "FLAC"
            "PCM" -> "PCM"
            "OPUS" -> "Opus"
            "VORBIS" -> "Vorbis"
            "WMA" -> "WMA"
            "ALAC" -> "ALAC"
            else -> codec.uppercase()
        }
    }
}
