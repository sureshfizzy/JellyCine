package com.jellycine.player.video

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * Custom RenderersFactory that provides intelligent Dolby Vision/HDR fallback support
 * This enables decoder fallback for better HDR compatibility
 */
@UnstableApi
class DolbyVisionCompatibleRenderersFactory(
    context: Context
) : DefaultRenderersFactory(context) {
    
    init {
        // Enable decoder fallback by default for better HDR/Dolby Vision compatibility
        setEnableDecoderFallback(true)
        
        // Use custom codec selector for better fallback handling
        setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val defaultSelector = MediaCodecSelector.DEFAULT

            Log.d(TAG, "Selecting codec for: $mimeType")
            
            try {
                val codecs = defaultSelector.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                
                if (codecs.isNotEmpty()) {
                    Log.d(TAG, "Found ${codecs.size} codecs for $mimeType")
                    codecs
                } else if (mimeType == androidx.media3.common.MimeTypes.VIDEO_DOLBY_VISION) {
                    // If no Dolby Vision codecs found, try H.265 as fallback
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
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error selecting codecs for $mimeType", e)
                emptyList()
            }
        }

        try {
            val deviceSupport = HdrCapabilityManager.getDeviceHdrSupport(context)
            Log.i(TAG, "HDR-compatible renderer factory initialized. Device support: ${deviceSupport.displayName}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine HDR capabilities", e)
        }
    }
    
    companion object {
        private const val TAG = "DolbyVisionRendererFactory"
    }
}
