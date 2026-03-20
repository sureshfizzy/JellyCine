package com.jellycine.app.ui.screens.dashboard.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.data.model.AudioTranscodeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.data.repository.MediaRepositoryProvider

data class PlayerSettingsUiState(
    // Hardware Acceleration
    val hardwareDecodingEnabled: Boolean = true,
    val asyncMediaCodecEnabled: Boolean = false,

    // Video
    val decoderPriority: String = "Auto",
    val streamingQuality: String = PlayerPreferences.DEFAULT_STREAMING_QUALITY,
    val audioTranscodeMode: String = AudioTranscodeMode.AUTO.displayName,
    val isVideoTranscodingAllowed: Boolean = false,
    val isAudioTranscodingAllowed: Boolean = false,
    val startMaximized: Boolean = false,
    val playerCacheSizeMb: Int = PlayerPreferences.DEFAULT_PLAYER_CACHE_SIZE_MB,
    val playerCacheTimeSeconds: Int = PlayerPreferences.DEFAULT_PLAYER_CACHE_TIME_SECONDS,
    val seekBackwardIntervalSeconds: Int = PlayerPreferences.DEFAULT_SEEK_INTERVAL_SECONDS,
    val seekForwardIntervalSeconds: Int = PlayerPreferences.DEFAULT_SEEK_INTERVAL_SECONDS,
    
    // Performance
    val batteryOptimizationEnabled: Boolean = false,

    // Loading states
    val isLoading: Boolean = false,
    val error: String? = null
)

class PlayerSettingsViewModel(private val context: Context) : ViewModel() {
    
    private val playerPreferences = PlayerPreferences(context)
    private val mediaRepository = MediaRepositoryProvider.getInstance(context)
    
    private val _uiState = MutableStateFlow(PlayerSettingsUiState())
    val uiState: StateFlow<PlayerSettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
        userTranscodingPolicy()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                hardwareDecodingEnabled = playerPreferences.isHardwareAccelerationEnabled(),
                asyncMediaCodecEnabled = playerPreferences.isAsyncMediaCodecEnabled(),
                decoderPriority = playerPreferences.getDecoderPriority(),
                streamingQuality = playerPreferences.getStreamingQuality(),
                audioTranscodeMode = playerPreferences.getAudioTranscodeMode().displayName,
                startMaximized = playerPreferences.isStartMaximizedEnabled(),
                playerCacheSizeMb = playerPreferences.getPlayerCacheSizeMb(),
                playerCacheTimeSeconds = playerPreferences.getPlayerCacheTimeSeconds(),
                seekBackwardIntervalSeconds = playerPreferences.getSeekBackwardIntervalSeconds(),
                seekForwardIntervalSeconds = playerPreferences.getSeekForwardIntervalSeconds(),
                batteryOptimizationEnabled = playerPreferences.isBatteryOptimizationEnabled()
            )
        }
    }
    
    // Setting update functions
    fun setHardwareDecodingEnabled(enabled: Boolean) {
        playerPreferences.setHardwareAccelerationEnabled(enabled)
        _uiState.value = _uiState.value.copy(hardwareDecodingEnabled = enabled)
        if (!enabled) {
            setAsyncMediaCodecEnabled(false)
        }
    }
    
    fun setAsyncMediaCodecEnabled(enabled: Boolean) {
        playerPreferences.setAsyncMediaCodecEnabled(enabled)
        _uiState.value = _uiState.value.copy(asyncMediaCodecEnabled = enabled)
    }
    
    fun setDecoderPriority(priority: String) {
        playerPreferences.setDecoderPriority(priority)
        _uiState.value = _uiState.value.copy(decoderPriority = priority)
    }

    private fun userTranscodingPolicy() {
        viewModelScope.launch {
            val user = mediaRepository.getCurrentUser().getOrNull()
            val isVideoAllowed = user?.policy?.enableVideoPlaybackTranscoding
                ?: user?.let { true }
                ?: _uiState.value.isVideoTranscodingAllowed
            val isAudioAllowed = user?.policy?.enableAudioPlaybackTranscoding
                ?: user?.let { true }
                ?: _uiState.value.isAudioTranscodingAllowed

            _uiState.value = _uiState.value.copy(
                isVideoTranscodingAllowed = isVideoAllowed,
                isAudioTranscodingAllowed = isAudioAllowed
            )
        }
    }

    fun setStreamingQuality(quality: String) {
        playerPreferences.setStreamingQuality(quality)
        _uiState.value = _uiState.value.copy(streamingQuality = playerPreferences.getStreamingQuality())
    }

    fun setAudioTranscodeMode(modeDisplayName: String) {
        val mode = AudioTranscodeMode.fromDisplayName(modeDisplayName)
        playerPreferences.setAudioTranscodeMode(mode)
        _uiState.value = _uiState.value.copy(audioTranscodeMode = mode.displayName)
    }
    
    fun setStartMaximized(enabled: Boolean) {
        playerPreferences.setStartMaximizedEnabled(enabled)
        _uiState.value = _uiState.value.copy(startMaximized = enabled)
    }

    fun setPlayerCacheSizeMb(sizeMb: Int) {
        playerPreferences.setPlayerCacheSizeMb(sizeMb)
        _uiState.value = _uiState.value.copy(
            playerCacheSizeMb = playerPreferences.getPlayerCacheSizeMb()
        )
    }

    fun setPlayerCacheTimeSeconds(seconds: Int) {
        playerPreferences.setPlayerCacheTimeSeconds(seconds)
        _uiState.value = _uiState.value.copy(
            playerCacheTimeSeconds = playerPreferences.getPlayerCacheTimeSeconds()
        )
    }

    fun setSeekBackwardIntervalSeconds(seconds: Int) {
        playerPreferences.setSeekBackwardIntervalSeconds(seconds)
        _uiState.value = _uiState.value.copy(
            seekBackwardIntervalSeconds = playerPreferences.getSeekBackwardIntervalSeconds()
        )
    }

    fun setSeekForwardIntervalSeconds(seconds: Int) {
        playerPreferences.setSeekForwardIntervalSeconds(seconds)
        _uiState.value = _uiState.value.copy(
            seekForwardIntervalSeconds = playerPreferences.getSeekForwardIntervalSeconds()
        )
    }
    
    fun setBatteryOptimizationEnabled(enabled: Boolean) {
        playerPreferences.setBatteryOptimizationEnabled(enabled)
        _uiState.value = _uiState.value.copy(batteryOptimizationEnabled = enabled)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
