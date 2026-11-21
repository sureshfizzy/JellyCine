package com.jellycine.ui.screens.player

import com.jellycine.player.preferences.PlayerPreferences
import platform.Foundation.NSProcessInfo

private val IOS_HARDWARE_DECODING = setOf(
    PlayerPreferences.MPV_HARDWARE_DECODING_VIDEOTOOLBOX,
    PlayerPreferences.MPV_HARDWARE_DECODING_VIDEOTOOLBOX_COPY,
    PlayerPreferences.MPV_HARDWARE_DECODING_NONE
)

private val IOS_AUDIO_OUTPUT = setOf(PlayerPreferences.MPV_AUDIO_OUTPUT_AUDIOUNIT)

private val isSimulator = NSProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != null

private fun String.orElse(valid: Set<String>, fallback: String) = takeIf { it in valid } ?: fallback

fun iosMpvOptions(preferences: PlayerPreferences): Map<String, String> {
    val hardwareDecoding = preferences.getMpvHardwareDecoding()
        .orElse(IOS_HARDWARE_DECODING, PlayerPreferences.MPV_HARDWARE_DECODING_VIDEOTOOLBOX)
        .let { value ->
            if (isSimulator && value == PlayerPreferences.MPV_HARDWARE_DECODING_VIDEOTOOLBOX_COPY) {
                PlayerPreferences.MPV_HARDWARE_DECODING_VIDEOTOOLBOX
            } else {
                value
            }
        }

    return buildMap {
        put("vo", preferences.getMpvVideoOutput())
        put("hwdec", hardwareDecoding)
        put("ao", preferences.getMpvAudioOutput().orElse(IOS_AUDIO_OUTPUT, PlayerPreferences.MPV_AUDIO_OUTPUT_AUDIOUNIT))
        put("scale", preferences.getMpvUpscaleFilter())
        put("dscale", preferences.getMpvDownscaleFilter())
        put("deband", if (preferences.getMpvDeband()) "yes" else "no")
        put("tone-mapping", preferences.getMpvToneMapping())
        put("hdr-compute-peak", if (preferences.getMpvDynamicPeak()) "yes" else "no")
        if (preferences.getMpvHdrToSdrTonemapping()) {
            put("target-prim", "bt.709")
            put("target-trc", "bt.1886")
            put("tone-mapping-mode", "hybrid")
            put("gamut-mapping-mode", "perceptual")
        }
        if (preferences.getMpvSmoothMotion()) {
            put("interpolation", "yes")
            put("tscale", "oversample")
            put("video-sync", "display-resample")
        }
    }
}