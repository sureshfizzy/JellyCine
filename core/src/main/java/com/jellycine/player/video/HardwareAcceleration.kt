package com.jellycine.player.video

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import com.jellycine.player.preferences.PlayerPreferences

/**
 * Enhanced RenderersFactory that provides configurable hardware acceleration support
 * Combines Dolby Vision/HDR fallback with user preferences for hardware acceleration
 */
@UnstableApi
class HardwareAcceleration(
    private val context: Context
) : DefaultRenderersFactory(context) {
    
    private val playerPreferences = PlayerPreferences(context)
    
    companion object {
        private const val TAG = "HardwareAcceleration"
    }
    
    init {
        setupRenderersFactory()
    }
    
    private fun setupRenderersFactory() {
        // Enable decoder fallback by default for better HDR/Dolby Vision compatibility
        setEnableDecoderFallback(true)
        
        // Use custom codec selector for better hardware acceleration control
        setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            selectOptimalCodec(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        }
        
        try {
            val deviceSupport = HdrCapabilityManager.getDeviceHdrSupport(context)
            Log.i(TAG, "Hardware-accelerated renderer factory initialized. HDR support: ${deviceSupport.displayName}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine HDR capabilities", e)
        }
    }
    
    private fun selectOptimalCodec(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): List<MediaCodecInfo> {
        val defaultSelector = MediaCodecSelector.DEFAULT
        val hardwareAccelerationEnabled = playerPreferences.isHardwareAccelerationEnabled()
        val decoderPriority = playerPreferences.getDecoderPriority()
        val hdrEnabled = playerPreferences.isHdrEnabled()
        
        Log.d(TAG, "Selecting codec for: $mimeType (HW: $hardwareAccelerationEnabled, Priority: $decoderPriority, HDR: $hdrEnabled)")
        
        // If HDR is disabled and this is an HDR format, return empty to force fallback
        if (!hdrEnabled && isHdrFormat(mimeType)) {
            Log.d(TAG, "HDR disabled by user, skipping HDR codec: $mimeType")
            return handleHdrDisabledFallback(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        }
        
        return try {
            val allCodecs = defaultSelector.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            
            if (allCodecs.isEmpty()) {
                handleFallbackForSpecialFormats(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            } else {
                prioritizeCodecs(allCodecs, hardwareAccelerationEnabled, decoderPriority, mimeType)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting codecs for $mimeType", e)
            emptyList()
        }
    }
    
    /**
     * Check if the MIME type is an HDR format
     */
    private fun isHdrFormat(mimeType: String): Boolean {
        return mimeType == androidx.media3.common.MimeTypes.VIDEO_DOLBY_VISION ||
               mimeType.contains("dolby-vision", ignoreCase = true) ||
               mimeType.contains("hdr", ignoreCase = true)
    }
    
    /**
     * Handle fallback when HDR is disabled by user
     */
    private fun handleHdrDisabledFallback(
        originalMimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): List<MediaCodecInfo> {
        val defaultSelector = MediaCodecSelector.DEFAULT
        
        return when (originalMimeType) {
            androidx.media3.common.MimeTypes.VIDEO_DOLBY_VISION -> {
                Log.i(TAG, "HDR disabled: Dolby Vision -> H.265 fallback")
                val h265Codecs = defaultSelector.getDecoderInfos(
                    androidx.media3.common.MimeTypes.VIDEO_H265,
                    requiresSecureDecoder,
                    requiresTunnelingDecoder
                )
                
                if (h265Codecs.isNotEmpty()) {
                    h265Codecs
                } else {
                    Log.i(TAG, "HDR disabled: H.265 -> H.264 final fallback")
                    defaultSelector.getDecoderInfos(
                        androidx.media3.common.MimeTypes.VIDEO_H264,
                        requiresSecureDecoder,
                        requiresTunnelingDecoder
                    )
                }
            }
            else -> {
                // For other HDR formats, fallback to H.264
                Log.i(TAG, "HDR disabled: $originalMimeType -> H.264 fallback")
                defaultSelector.getDecoderInfos(
                    androidx.media3.common.MimeTypes.VIDEO_H264,
                    requiresSecureDecoder,
                    requiresTunnelingDecoder
                )
            }
        }
    }

    private fun handleFallbackForSpecialFormats(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): List<MediaCodecInfo> {
        val defaultSelector = MediaCodecSelector.DEFAULT
        
        return when (mimeType) {
            androidx.media3.common.MimeTypes.VIDEO_DOLBY_VISION -> {
                // Dolby Vision fallback chain: DV -> H.265 -> H.264
                Log.w(TAG, "No Dolby Vision codecs found, trying H.265 fallback")
                val h265Codecs = defaultSelector.getDecoderInfos(
                    androidx.media3.common.MimeTypes.VIDEO_H265,
                    requiresSecureDecoder,
                    requiresTunnelingDecoder
                )
                
                if (h265Codecs.isNotEmpty()) {
                    Log.i(TAG, "Using H.265 codecs as Dolby Vision fallback")
                    h265Codecs
                } else {
                    Log.w(TAG, "No H.265 codecs, trying H.264 as final fallback")
                    defaultSelector.getDecoderInfos(
                        androidx.media3.common.MimeTypes.VIDEO_H264,
                        requiresSecureDecoder,
                        requiresTunnelingDecoder
                    )
                }
            }
            else -> emptyList()
        }
    }
    
    private fun prioritizeCodecs(
        codecs: List<MediaCodecInfo>,
        hardwareAccelerationEnabled: Boolean,
        decoderPriority: String,
        mimeType: String
    ): List<MediaCodecInfo> {
        if (codecs.isEmpty()) return codecs
        
        val hardwareCodecs = mutableListOf<MediaCodecInfo>()
        val softwareCodecs = mutableListOf<MediaCodecInfo>()
        
        // Separate hardware and software codecs
        codecs.forEach { codec ->
            if (isHardwareCodec(codec)) {
                hardwareCodecs.add(codec)
            } else {
                softwareCodecs.add(codec)
            }
        }
        
        Log.d(TAG, "Found ${hardwareCodecs.size} hardware and ${softwareCodecs.size} software codecs for $mimeType")
        
        return when {
            !hardwareAccelerationEnabled -> {
                Log.d(TAG, "Hardware acceleration disabled, preferring software codecs")
                softwareCodecs + hardwareCodecs
            }
            decoderPriority == "Software First" -> {
                Log.d(TAG, "Software decoder priority, preferring software codecs")
                softwareCodecs + hardwareCodecs
            }
            decoderPriority == "Hardware First" -> {
                Log.d(TAG, "Hardware decoder priority, preferring hardware codecs")
                hardwareCodecs + softwareCodecs
            }
            else -> { // Auto
                Log.d(TAG, "Auto decoder priority, using default order")
                // For auto mode, prefer hardware for video, software for audio quality
                if (mimeType.startsWith("video/")) {
                    hardwareCodecs + softwareCodecs
                } else {
                    // For audio, prefer software for better quality unless it's a high-performance scenario
                    softwareCodecs + hardwareCodecs
                }
            }
        }
    }
    
    private fun isHardwareCodec(codecInfo: MediaCodecInfo): Boolean {
        val name = codecInfo.name.lowercase()
        
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                try {
                    // Try to use the isHardwareAccelerated method if available
                    // Note: This is available in newer versions but may require reflection
                    isHardwareCodecByName(name)
                } catch (e: Exception) {
                    // Fallback to name-based detection
                    isHardwareCodecByName(name)
                }
            }
            else -> isHardwareCodecByName(name)
        }
    }
    
    private fun isHardwareCodecByName(name: String): Boolean {
        return !name.contains("google") && 
               !name.contains("ffmpeg") &&
               !name.contains("software") &&
               (name.contains("qcom") || 
                name.contains("exynos") || 
                name.contains("mtk") ||
                name.contains("kirin") ||
                name.contains("intel") ||
                name.startsWith("omx.") ||
                (name.startsWith("c2.android") && !name.contains("sw")))
    }
    
    /**
     * Refresh codec selection based on current preferences
     */
    fun refreshCodecSelection() {
        setupRenderersFactory()
        Log.i(TAG, "Codec selection refreshed based on current preferences")
    }
}