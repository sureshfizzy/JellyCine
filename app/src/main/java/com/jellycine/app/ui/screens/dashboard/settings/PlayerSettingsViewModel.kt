package com.jellycine.app.ui.screens.dashboard.settings

import android.content.Context
import android.media.MediaCodecList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.data.model.AudioTranscodeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.player.audio.AudioDeviceManager
import com.jellycine.player.audio.ExternalAudioDevice
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
    
    // Performance
    val batteryOptimizationEnabled: Boolean = false,
    
    // Device Information
    val supportedCodecs: String = "Loading...",
    val externalAudioDevices: List<ExternalAudioDevice> = emptyList(),
    
    // Loading states
    val isLoading: Boolean = false,
    val error: String? = null
)

class PlayerSettingsViewModel(private val context: Context) : ViewModel() {
    
    private val playerPreferences = PlayerPreferences(context)
    private val audioDeviceManager = AudioDeviceManager(context)
    private val mediaRepository = MediaRepositoryProvider.getInstance(context)
    
    private val _uiState = MutableStateFlow(PlayerSettingsUiState())
    val uiState: StateFlow<PlayerSettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
        userTranscodingPolicy()
        detectDeviceCapabilities()
        startAudioDeviceMonitoring()
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
                batteryOptimizationEnabled = playerPreferences.isBatteryOptimizationEnabled()
            )
        }
    }
    
    private fun detectDeviceCapabilities() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Get supported codecs
                    val supportedCodecs = getSupportedCodecs()

                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            supportedCodecs = supportedCodecs
                        )
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            error = "Failed to detect device capabilities: ${e.message}"
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Start monitoring external audio devices
     */
    private fun startAudioDeviceMonitoring() {
        audioDeviceManager.startMonitoring()
        
        // Collect device changes
        viewModelScope.launch {
            audioDeviceManager.connectedDevices.collect { devices ->
                _uiState.value = _uiState.value.copy(externalAudioDevices = devices)
            }
        }
    }
    
    private fun getSupportedCodecs(): String {
        return try {
            val mediaCodecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val codecInfos = mediaCodecList.codecInfos
            
            val videoCodecs = mutableSetOf<String>()
            val audioCodecs = mutableSetOf<String>()
            
            codecInfos.forEach { codecInfo ->
                if (!codecInfo.isEncoder) {
                    codecInfo.supportedTypes.forEach { type ->
                        when {
                            type.startsWith("video/") -> {
                                when (type) {
                                    "video/avc" -> videoCodecs.add("H.264")
                                    "video/hevc" -> videoCodecs.add("H.265")
                                    "video/x-vnd.on2.vp9" -> videoCodecs.add("VP9")
                                    "video/av01" -> videoCodecs.add("AV1")
                                    "video/dolby-vision" -> videoCodecs.add("Dolby Vision")
                                }
                            }
                            type.startsWith("audio/") -> {
                                when (type) {
                                    "audio/mp4a-latm" -> audioCodecs.add("AAC")
                                    "audio/ac3" -> audioCodecs.add("AC-3")
                                    "audio/eac3" -> audioCodecs.add("E-AC-3")
                                    "audio/flac" -> audioCodecs.add("FLAC")
                                    "audio/opus" -> audioCodecs.add("Opus")
                                }
                            }
                        }
                    }
                }
            }
            
            val videoCodecString = videoCodecs.sorted().joinToString(", ")
            val audioCodecString = audioCodecs.sorted().joinToString(", ")
            
            "Video: $videoCodecString\nAudio: $audioCodecString"
        } catch (e: Exception) {
            "Unable to detect codecs"
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
    
    fun setBatteryOptimizationEnabled(enabled: Boolean) {
        playerPreferences.setBatteryOptimizationEnabled(enabled)
        _uiState.value = _uiState.value.copy(batteryOptimizationEnabled = enabled)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    override fun onCleared() {
        super.onCleared()
        audioDeviceManager.stopMonitoring()
    }
}
