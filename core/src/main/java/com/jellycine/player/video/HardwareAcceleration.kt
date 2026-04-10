package com.jellycine.player.video

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.player.preferences.PlayerPreferences.Companion.DECODER_PRIORITY_SOFTWARE

/**
 * Enhanced RenderersFactory that provides configurable hardware acceleration support
 * Combines Dolby Vision fallback with user preferences for hardware acceleration
 */
@UnstableApi
class HardwareAcceleration(
    context: Context
) : DefaultRenderersFactory(context) {
    
    private val playerPreferences = PlayerPreferences(context)
    
    companion object {
        private const val TAG = "HardwareAcceleration"
    }
    
    init {
        setupRenderersFactory()
    }
    
    private fun setupRenderersFactory() {
        // Prefer extension decoders
        setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        // Enable decoder fallback by default for better HDR/Dolby Vision compatibility
        setEnableDecoderFallback(true)

        val hardwareAccelerationEnabled = playerPreferences.isHardwareAccelerationEnabled()
        val asyncMediaCodecEnabled = hardwareAccelerationEnabled && playerPreferences.isAsyncMediaCodecEnabled()
        if (asyncMediaCodecEnabled) {
            forceEnableMediaCodecAsynchronousQueueing()
            experimentalSetMediaCodecAsyncCryptoFlagEnabled(true)
        } else {
            forceDisableMediaCodecAsynchronousQueueing()
            experimentalSetMediaCodecAsyncCryptoFlagEnabled(false)
        }

        val decoderPriority = playerPreferences.getDecoderPriority()
        val shouldUsePlatformSelector = hardwareAccelerationEnabled &&
            decoderPriority != DECODER_PRIORITY_SOFTWARE

        if (shouldUsePlatformSelector) {
            setMediaCodecSelector(MediaCodecSelector.DEFAULT)
            Log.d(
                TAG,
                "Using platform MediaCodec selector (HW=$hardwareAccelerationEnabled, Priority=$decoderPriority)"
            )
        } else {
            setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                selectOptimalCodec(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            }
            Log.d(
                TAG,
                "Using custom MediaCodec selector (HW=$hardwareAccelerationEnabled, Priority=$decoderPriority)"
            )
        }
    }
    
    private fun selectOptimalCodec(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): List<MediaCodecInfo> {
        val defaultSelector = MediaCodecSelector.DEFAULT
        Log.d(TAG, "Selecting software codec for: $mimeType")
        
        return try {
            val allCodecs = defaultSelector.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            
            if (allCodecs.isEmpty()) {
                handleFallbackForSpecialFormats(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            } else {
                filterSoftwareCodecs(allCodecs, mimeType)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting codecs for $mimeType", e)
            emptyList()
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
                    filterSoftwareCodecs(h265Codecs, androidx.media3.common.MimeTypes.VIDEO_H265)
                } else {
                    Log.w(TAG, "No H.265 codecs, trying H.264 as final fallback")
                    filterSoftwareCodecs(
                        defaultSelector.getDecoderInfos(
                            androidx.media3.common.MimeTypes.VIDEO_H264,
                            requiresSecureDecoder,
                            requiresTunnelingDecoder
                        ),
                        androidx.media3.common.MimeTypes.VIDEO_H264
                    )
                }
            }
            else -> emptyList()
        }
    }
    
    private fun filterSoftwareCodecs(
        codecs: List<MediaCodecInfo>,
        mimeType: String
    ): List<MediaCodecInfo> {
        if (codecs.isEmpty()) return codecs
        
        val softwareCodecs = mutableListOf<MediaCodecInfo>()
        
        codecs.forEach { codec ->
            if (!isHardwareCodec(codec)) {
                softwareCodecs.add(codec)
            }
        }
        
        return if (softwareCodecs.isNotEmpty()) {
            Log.d(TAG, "Using ${softwareCodecs.size} software codecs for $mimeType")
            softwareCodecs
        } else {
            Log.w(TAG, "No software codecs found for $mimeType; falling back to default codec order")
            codecs
        }
    }
    
    private fun isHardwareCodec(codecInfo: MediaCodecInfo): Boolean {
        return codecInfo.vendor &&
            codecInfo.hardwareAccelerated &&
            !codecInfo.softwareOnly
    }
    
    /**
     * Refresh codec selection based on current preferences
     */
    fun refreshCodecSelection() {
        setupRenderersFactory()
        Log.i(TAG, "Codec selection refreshed based on current preferences")
    }
}
