package com.jellycine.detail

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

import com.jellycine.data.model.MediaStream

/**
 * Data class representing device audio capabilities
 */
data class AudioCapabilities(
    val supportsSpatialAudio: Boolean,
    val supportsAtmos: Boolean,
    val maxChannels: Int,
    val connectedAudioDevice: String,
    val canProcessSpatialAudio: Boolean
)

/**
 * Manager class for codec capability detection and spatial audio analysis
 */
object CodecCapabilityManager {

    /**
     * Detect spatial audio format from media stream metadata
     */
    fun detectSpatialAudio(
        codec: String, 
        channels: Int, 
        channelLayout: String?, 
        title: String?, 
        profile: String?
    ): String {
        val codecLower = codec.lowercase()
        val titleLower = title?.lowercase() ?: ""
        val layoutLower = channelLayout?.lowercase() ?: ""
        val profileLower = profile?.lowercase() ?: ""

        // Spatial Audio Detection
        when {
            // === DOLBY ATMOS DETECTION ===
            
            // Explicit Atmos indicators in metadata (most reliable)
            titleLower.contains("dolby atmos") ||
            titleLower.contains("atmos") ||
            layoutLower.contains("atmos") ||
            profileLower.contains("atmos") ||
            titleLower.contains("object audio") -> return "Dolby Atmos"

            // E-AC-3 JOC (AC-3 with Joint Object Coding)
            titleLower.contains("e-ac-3 joc") ||
            titleLower.contains("eac3 joc") ||
            titleLower.contains("joint object coding") ||
            titleLower.contains("dolby digital plus with dolby atmos") -> return "Dolby Atmos"

            // TrueHD with Atmos - Blu-ray standard (accurate channel detection)
            codecLower.contains("truehd") && (
                channels == 8 || // Standard 7.1 TrueHD Atmos
                channels > 8 ||  // Extended Atmos layouts
                titleLower.contains("7.1") ||
                titleLower.contains("object") ||
                layoutLower.contains("7.1")
            ) -> return "Dolby Atmos"

            // E-AC-3 with Atmos indicators (streaming format)
            (codecLower.contains("eac3") || codecLower.contains("e-ac-3") || codecLower.contains("ec-3")) && (
                titleLower.contains("atmos") ||
                titleLower.contains("plus") ||
                titleLower.contains("") ||
                titleLower.contains("7.1") ||
                layoutLower.contains("7.1") ||
                channels >= 8 // E-AC-3 with 8+ channels often indicates Atmos
            ) -> return "Dolby Atmos"

            // === DTS:X DETECTION ===
            
            // Explicit DTS:X indicators
            titleLower.contains("dts:x") ||
            titleLower.contains("dtsx") ||
            titleLower.contains("dts-x") ||
            layoutLower.contains("dts:x") ||
            profileLower.contains("dts:x") -> return "DTS:X"

            // DTS with object audio indicators
            codecLower.contains("dts") && (
                titleLower.contains("object") ||
                titleLower.contains("immersive") ||
                channels >= 10 // DTS with 10+ channels likely DTS:X
            ) -> return "DTS:X"

            // === SPATIAL CHANNEL LAYOUTS ===
            
            // Object-based audio detection by specific channel layouts
            layoutLower.contains("7.1.4") || layoutLower.contains("(7.1.4)") -> return "7.1.4 (Atmos)"
            layoutLower.contains("5.1.4") || layoutLower.contains("(5.1.4)") -> return "5.1.4 (Atmos)"
            layoutLower.contains("7.1.2") || layoutLower.contains("(7.1.2)") -> return "7.1.2 (Atmos)"
            layoutLower.contains("5.1.2") || layoutLower.contains("(5.1.2)") -> return "5.1.2 (Atmos)"
            layoutLower.contains("9.1.6") || layoutLower.contains("(9.1.6)") -> return "9.1.6 (Atmos)"
            layoutLower.contains("11.1.4") || layoutLower.contains("(11.1.4)") -> return "11.1.4 (Atmos)"

            // === OTHER SPATIAL FORMATS ===
            
            // Sony 360 Reality Audio
            titleLower.contains("360 reality audio") ||
            titleLower.contains("360ra") ||
            profileLower.contains("360 reality") -> return "360 Reality Audio"

            // MPEG-H Audio
            codecLower.contains("mpegh") ||
            codecLower.contains("mpeg-h") ||
            titleLower.contains("mpeg-h audio") ||
            titleLower.contains("audio") -> return "MPEG-H Audio"

            // Auro-3D
            titleLower.contains("auro-3d") ||
            titleLower.contains("auro3d") ||
            layoutLower.contains("auro") -> return "Auro-3D"

            // === FALLBACK DETECTION BY CHANNEL COUNT ===
            
            // High channel count detection (conservative approach)
            channels >= 16 -> return "${channels}ch (Spatial)"
            channels >= 12 -> return "${channels}ch ()"
        }

        return ""
    }

    /**
     * Detect HDR format from video stream
     */
    fun detectHDRFormat(videoStream: MediaStream): String {
        val videoRange = videoStream.videoRange?.lowercase() ?: ""
        val videoRangeType = videoStream.videoRangeType?.lowercase() ?: ""
        val codec = videoStream.codec?.lowercase() ?: ""
        val title = videoStream.title?.lowercase() ?: ""
        val profile = videoStream.profile?.lowercase() ?: ""
        
        // Check for Dolby Vision
        when {
            videoRangeType.contains("dovi") ||
            videoRange.contains("dovi") ||
            title.contains("dolby vision") ||
            title.contains("dv") ||
            profile.contains("dolby vision") ||
            profile.contains("dovi") ||
            // Check for DV profile indicators
            videoStream.dvProfile != null -> return "Dolby Vision"
            
            // Check for HDR10+
            videoRangeType.contains("hdr10+") ||
            videoRange.contains("hdr10+") ||
            title.contains("hdr10+") ||
            profile.contains("hdr10+") -> return "HDR10+"
            
            // Check for HDR10
            videoRangeType.contains("hdr10") ||
            videoRange.contains("hdr10") ||
            title.contains("hdr10") ||
            profile.contains("hdr10") -> return "HDR10"
            
            // Generic HDR detection
            videoRange.contains("hdr") ||
            videoRangeType.contains("hdr") ||
            title.contains("hdr") -> return "HDR"
        }
        
        return ""
    }

    /**
     * Detect device audio capabilities
     */
    fun detectDeviceAudioCapabilities(context: Context): AudioCapabilities {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Check for spatial audio support (Android 12+)
        val supportsSpatialAudio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                // Use reflection to access spatializer API safely
                val spatializer = audioManager.javaClass.getMethod("getSpatializer").invoke(audioManager)
                val isAvailable = spatializer?.javaClass?.getMethod("isAvailable")?.invoke(spatializer) as? Boolean ?: false
                val isEnabled = spatializer?.javaClass?.getMethod("isEnabled")?.invoke(spatializer) as? Boolean ?: false
                isAvailable && isEnabled
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
        
        // Detect connected audio devices
        val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val connectedDevice = outputDevices.firstOrNull()

        val deviceName = when (connectedDevice?.type) {
            AudioDeviceInfo.TYPE_HDMI -> "HDMI"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in Speaker"
            else -> "Unknown"
        }
        
        // Check for Atmos capability based on device type
        val supportsAtmos = when (connectedDevice?.type) {
            AudioDeviceInfo.TYPE_HDMI -> true // HDMI can pass through Atmos
            AudioDeviceInfo.TYPE_USB_HEADSET -> true // USB can support Atmos
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> checkBluetoothAtmosSupport(connectedDevice)
            else -> false
        }
        
        // Estimate max channels based on device
        val maxChannels = when (connectedDevice?.type) {
            AudioDeviceInfo.TYPE_HDMI -> 16 // HDMI can support many channels
            AudioDeviceInfo.TYPE_USB_HEADSET -> 8
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 2
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> 2
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 2
            else -> 2
        }
        
        val canProcessSpatialAudio = supportsSpatialAudio || supportsAtmos
        
        return AudioCapabilities(
            supportsSpatialAudio = supportsSpatialAudio,
            supportsAtmos = supportsAtmos,
            maxChannels = maxChannels,
            connectedAudioDevice = deviceName,
            canProcessSpatialAudio = canProcessSpatialAudio
        )
    }

    /**
     * Check if Bluetooth device supports spatial audio
     */
    private fun checkBluetoothAtmosSupport(device: AudioDeviceInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                // Check for LDAC, aptX HD, or other high-quality codecs
                device.productName.toString().contains("LDAC", ignoreCase = true) ||
                device.productName.toString().contains("aptX", ignoreCase = true) ||
                device.productName.toString().contains("Sony", ignoreCase = true) || // Sony often supports spatial
                device.productName.toString().contains("Bose", ignoreCase = true)    // Bose often supports spatial
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }

    /**
     * Check if audio stream is Dolby-based
     */
    fun isDolbyAudio(audioStream: MediaStream): Boolean {
        val codec = audioStream.codec?.uppercase() ?: ""
        val title = audioStream.title?.lowercase() ?: ""
        val profile = audioStream.profile?.lowercase() ?: ""
        val channelLayout = audioStream.channelLayout?.lowercase() ?: ""

        return codec.contains("EAC3") ||
               codec.contains("E-AC-3") ||
               codec.contains("EC-3") ||
               codec.contains("AC3") ||
               codec.contains("AC-3") ||
               codec.contains("TRUEHD") ||
               title.contains("dolby") ||
               title.contains("atmos") ||
               title.contains("digital+") ||
               title.contains("digital plus") ||
               profile.contains("dolby") ||
               profile.contains("atmos") ||
               channelLayout.contains("dolby") ||
               channelLayout.contains("atmos")
    }

    /**
     * Check if audio has spatial/immersive capabilities
     */
    fun hasSpatialAudio(audioStream: MediaStream): Boolean {
        val spatialInfo = detectSpatialAudio(
            audioStream.codec?.uppercase() ?: "",
            audioStream.channels ?: 0,
            audioStream.channelLayout,
            audioStream.title,
            audioStream.profile
        )
        return spatialInfo.isNotEmpty()
    }
}


