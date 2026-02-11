package com.jellycine.app.ui.screens.dashboard.settings

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.player.audio.SpatializerHelper
import com.jellycine.player.video.HdrCapabilityManager
import com.jellycine.detail.CodecCapabilityManager
import com.jellycine.player.audio.AudioDeviceManager
import com.jellycine.player.audio.ExternalAudioDevice

data class PlayerSettingsUiState(
    // Hardware Acceleration
    val hardwareDecodingEnabled: Boolean = true,
    val asyncMediaCodecEnabled: Boolean = false,
    val hardwareStatus: String? = null,
    
    // Audio
    val spatialAudioEnabled: Boolean = true,
    val spatialAudioSupported: Boolean = false,
    val headTrackingEnabled: Boolean = false,
    val headTrackingSupported: Boolean = false,

    // Video
    val hdrEnabled: Boolean = true,
    val hdrSupported: Boolean = false,
    val decoderPriority: String = "Auto",
    val startMaximized: Boolean = false,
    
    // Performance
    val bufferOptimizationEnabled: Boolean = true,
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
    private val spatializerHelper = SpatializerHelper(context)
    private val audioDeviceManager = AudioDeviceManager(context)
    
    private val _uiState = MutableStateFlow(PlayerSettingsUiState())
    val uiState: StateFlow<PlayerSettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
        detectDeviceCapabilities()
        startAudioDeviceMonitoring()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                hardwareDecodingEnabled = playerPreferences.isHardwareAccelerationEnabled(),
                asyncMediaCodecEnabled = playerPreferences.isAsyncMediaCodecEnabled(),
                spatialAudioEnabled = playerPreferences.isSpatialAudioEnabled(),
                headTrackingEnabled = playerPreferences.isHeadTrackingEnabled(),
                hdrEnabled = playerPreferences.isHdrEnabled(),
                decoderPriority = playerPreferences.getDecoderPriority(),
                startMaximized = playerPreferences.isStartMaximizedEnabled(),
                bufferOptimizationEnabled = playerPreferences.isBufferOptimizationEnabled(),
                batteryOptimizationEnabled = playerPreferences.isBatteryOptimizationEnabled()
            )
        }
    }
    
    private fun detectDeviceCapabilities() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Detect spatial audio capabilities
                    val spatialInfo = spatializerHelper.getSpatialAudioInfo()
                    val spatialSupported = spatialInfo.isSupported && spatialInfo.isAvailable
                    val headTrackingSupported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        spatialInfo.hasHeadTracker
                    } else false
                    
                    // Detect HDR capabilities
                    val hdrSupported = try {
                        val hdrCapability = HdrCapabilityManager.getDeviceHdrSupport(context)
                        hdrCapability != HdrCapabilityManager.HdrSupport.SDR
                    } catch (e: Exception) {
                        false
                    }
                    
                    // Get supported codecs
                    val supportedCodecs = getSupportedCodecs()
                    
                    // Get hardware status
                    val hardwareStatus = getHardwareStatus()
                    
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            spatialAudioSupported = spatialSupported,
                            headTrackingSupported = headTrackingSupported,
                            hdrSupported = hdrSupported,
                            supportedCodecs = supportedCodecs,
                            hardwareStatus = hardwareStatus
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
    
    private fun getHardwareStatus(): String {
        return try {
            val mediaCodecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val hardwareDecoders = mediaCodecList.codecInfos.count { codecInfo ->
                !codecInfo.isEncoder && isHardwareDecoder(codecInfo)
            }
            val totalDecoders = mediaCodecList.codecInfos.count { !it.isEncoder }
            
            "Hardware decoders: $hardwareDecoders/$totalDecoders available"
        } catch (e: Exception) {
            "Unable to detect hardware status"
        }
    }
    
    private fun isHardwareDecoder(codecInfo: MediaCodecInfo): Boolean {
        val name = codecInfo.name.lowercase()
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                codecInfo.isHardwareAccelerated
            }
            else -> {
                !name.contains("google") && 
                !name.contains("ffmpeg") &&
                (name.contains("qcom") || 
                 name.contains("exynos") || 
                 name.contains("mtk") ||
                 name.startsWith("omx.") ||
                 (name.startsWith("c2.android") && !name.contains("software")))
            }
        }
    }
    
    fun refreshHardwareStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(hardwareStatus = "Refreshing...")
            detectDeviceCapabilities()
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
    
    fun setSpatialAudioEnabled(enabled: Boolean) {
        playerPreferences.setSpatialAudioEnabled(enabled)
        _uiState.value = _uiState.value.copy(spatialAudioEnabled = enabled)
        if (!enabled) {
            setHeadTrackingEnabled(false)
        }
    }
    
    fun setHeadTrackingEnabled(enabled: Boolean) {
        playerPreferences.setHeadTrackingEnabled(enabled)
        _uiState.value = _uiState.value.copy(headTrackingEnabled = enabled)
    }
    

    fun setHdrEnabled(enabled: Boolean) {
        playerPreferences.setHdrEnabled(enabled)
        _uiState.value = _uiState.value.copy(hdrEnabled = enabled)
    }
    
    fun setDecoderPriority(priority: String) {
        playerPreferences.setDecoderPriority(priority)
        _uiState.value = _uiState.value.copy(decoderPriority = priority)
    }
    
    fun setStartMaximized(enabled: Boolean) {
        playerPreferences.setStartMaximizedEnabled(enabled)
        _uiState.value = _uiState.value.copy(startMaximized = enabled)
    }
    
    fun setBufferOptimizationEnabled(enabled: Boolean) {
        playerPreferences.setBufferOptimizationEnabled(enabled)
        _uiState.value = _uiState.value.copy(bufferOptimizationEnabled = enabled)
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
