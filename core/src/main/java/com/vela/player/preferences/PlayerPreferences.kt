package com.vela.player.preferences

import android.content.Context
import android.content.SharedPreferences
import com.vela.data.model.AudioTranscodeMode
import com.vela.player.core.PlayerConstants.DEFAULT_BRIGHTNESS
import com.vela.player.core.PlayerConstants.DEFAULT_VOLUME
import kotlin.math.roundToInt

/**
 * Manages player-specific preferences like brightness, volume levels, and hardware acceleration settings
 * Follows ExoPlayer's approach to remember user settings per player session
 */
class PlayerPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "vela_player_prefs"
        private const val KEY_PLAYER_BRIGHTNESS = "player_brightness"
        private const val KEY_PLAYER_VOLUME = "player_volume"
        private const val KEY_PLAYER_ENGINE = "player_engine"
        private const val KEY_PLAYER_ORIENTATION = "player_orientation"
        private const val KEY_MPV_HARDWARE_DECODING = "mpv_hardware_decoding"
        private const val KEY_MPV_VIDEO_OUTPUT = "mpv_video_output"
        private const val KEY_MPV_AUDIO_OUTPUT = "mpv_audio_output"
        private const val KEY_MPV_UPSCALE_FILTER = "mpv_upscale_filter"
        private const val KEY_MPV_DOWNSCALE_FILTER = "mpv_downscale_filter"
        private const val KEY_MPV_TONE_MAPPING = "mpv_tone_mapping"
        private const val KEY_MPV_SMOOTH_MOTION = "mpv_smooth_motion"
        private const val KEY_MPV_DEBAND = "mpv_deband"
        private const val KEY_MPV_DYNAMIC_PEAK = "mpv_dynamic_peak"
        private const val KEY_MPV_HDR_TO_SDR_TONEMAPPING = "mpv_hdr_to_sdr_tonemapping"
        private const val KEY_MPV_TARGET_PRIM = "mpv_target_prim"
        private const val KEY_MPV_TARGET_TRC = "mpv_target_trc"
        private const val KEY_MPV_OUTPUT_LEVELS = "mpv_output_levels"
        private const val KEY_HARDWARE_ACCELERATION = "hardware_acceleration_enabled"
        private const val KEY_ASYNC_MEDIACODEC = "async_mediacodec_enabled"
        private const val KEY_DECODER_PRIORITY = "decoder_priority"
        private const val KEY_BATTERY_OPTIMIZATION = "battery_optimization_enabled"
        private const val KEY_PLAYER_GESTURES_ENABLED = "player_gestures_enabled"
        private const val KEY_VOLUME_BRIGHTNESS_GESTURES_ENABLED = "volume_brightness_gestures_enabled"
        private const val KEY_USE_DEVICE_VOLUME_IN_PLAYER = "use_device_volume_in_player"
        private const val KEY_USE_DEVICE_BRIGHTNESS_IN_PLAYER = "use_device_brightness_in_player"
        private const val KEY_PROGRESS_SEEK_GESTURE_ENABLED = "progress_seek_gesture_enabled"
        private const val KEY_ZOOM_GESTURE_ENABLED = "zoom_gesture_enabled"
        private const val KEY_START_MAXIMIZED = "start_maximized"
        private const val KEY_CACHE_NEXT_EPISODE = "cache_next_episode"
        private const val KEY_PLAYER_CACHE_SIZE_MB = "player_cache_size_mb"
        private const val KEY_PLAYER_CACHE_TIME_SECONDS = "player_cache_time_seconds"
        private const val KEY_SEEK_BACKWARD_INTERVAL_SECONDS = "seek_backward_interval_seconds"
        private const val KEY_SEEK_FORWARD_INTERVAL_SECONDS = "seek_forward_interval_seconds"
        private const val KEY_LONG_PRESS_PLAYBACK_SPEED = "long_press_playback_speed"
        private const val KEY_SKIP_INTRO_ENABLED = "skip_intro_enabled"
        private const val KEY_CHAPTER_MARKERS_ENABLED = "chapter_markers_enabled"
        private const val KEY_SUBTITLE_TEXT_SIZE = "subtitle_text_size"
        private const val KEY_SUBTITLE_SCALE = "subtitle_scale"
        private const val KEY_SUBTITLE_TEXT_COLOR = "subtitle_text_color"
        private const val KEY_SUBTITLE_BACKGROUND_COLOR = "subtitle_background_color"
        private const val KEY_SUBTITLE_EDGE_TYPE = "subtitle_edge_type"
        private const val KEY_SUBTITLE_TEXT_OPACITY_PERCENT = "subtitle_text_opacity_percent"
        private const val KEY_SUBTITLE_BOTTOM_EDGE_PERCENT = "subtitle_bottom_edge_percent"
        private const val KEY_SUBTITLE_TOP_EDGE_PERCENT = "subtitle_top_edge_percent"
        private const val KEY_SUBTITLE_DELAY_MS = "subtitle_delay_ms"
        private const val KEY_SUBTITLE_ASS_COMPATIBLE = "subtitle_ass_compatible"
        private const val KEY_SUBTITLE_SCALE_DEFAULT_1_5 = "subtitle_scale_default_1_5"
        private const val KEY_STREAMING_QUALITY = "streaming_quality"
        private const val KEY_AUDIO_TRANSCODE_MODE = "audio_transcode_mode"
        private const val KEY_AUDIO_STREAM_INDEX_PREFIX = "audio_stream_index_"
        private const val KEY_SUBTITLE_STREAM_INDEX_PREFIX = "subtitle_stream_index_"
        private const val KEY_STREAM_INDEX_UPDATED_AT_PREFIX = "stream_index_updated_at_"
        private const val MAX_PREFERRED_STREAM_ITEMS = 500

        const val SUBTITLE_TEXT_SIZE_SMALL = "Small"
        const val SUBTITLE_TEXT_SIZE_NORMAL = "Normal"
        const val SUBTITLE_TEXT_SIZE_LARGE = "Large"
        const val SUBTITLE_TEXT_SIZE_EXTRA_LARGE = "Extra Large"
        val SUBTITLE_TEXT_SIZE_OPTIONS = listOf(
            SUBTITLE_TEXT_SIZE_SMALL,
            SUBTITLE_TEXT_SIZE_NORMAL,
            SUBTITLE_TEXT_SIZE_LARGE,
            SUBTITLE_TEXT_SIZE_EXTRA_LARGE
        )

        const val SUBTITLE_TEXT_COLOR_WHITE = "White"
        const val SUBTITLE_TEXT_COLOR_YELLOW = "Yellow"
        const val SUBTITLE_TEXT_COLOR_GREEN = "Green"
        const val SUBTITLE_TEXT_COLOR_CYAN = "Cyan"
        const val SUBTITLE_TEXT_COLOR_BLACK = "Black"
        val SUBTITLE_TEXT_COLOR_OPTIONS = listOf(
            SUBTITLE_TEXT_COLOR_WHITE,
            SUBTITLE_TEXT_COLOR_YELLOW,
            SUBTITLE_TEXT_COLOR_GREEN,
            SUBTITLE_TEXT_COLOR_CYAN,
            SUBTITLE_TEXT_COLOR_BLACK
        )

        const val SUBTITLE_BACKGROUND_TRANSPARENT = "Transparent"
        const val SUBTITLE_BACKGROUND_BLACK = "Black"
        const val SUBTITLE_BACKGROUND_WHITE = "White"
        val SUBTITLE_BACKGROUND_OPTIONS = listOf(
            SUBTITLE_BACKGROUND_TRANSPARENT,
            SUBTITLE_BACKGROUND_BLACK,
            SUBTITLE_BACKGROUND_WHITE
        )

        const val SUBTITLE_EDGE_TYPE_NONE = "None"
        const val SUBTITLE_EDGE_TYPE_OUTLINE = "Outline"
        const val SUBTITLE_EDGE_TYPE_DROP_SHADOW = "Drop Shadow"
        const val SUBTITLE_EDGE_TYPE_RAISED = "Raised"
        const val SUBTITLE_EDGE_TYPE_DEPRESSED = "Depressed"
        val SUBTITLE_EDGE_TYPE_OPTIONS = listOf(
            SUBTITLE_EDGE_TYPE_NONE,
            SUBTITLE_EDGE_TYPE_OUTLINE,
            SUBTITLE_EDGE_TYPE_DROP_SHADOW,
            SUBTITLE_EDGE_TYPE_RAISED,
            SUBTITLE_EDGE_TYPE_DEPRESSED
        )

        const val DEFAULT_SUBTITLE_TEXT_SIZE = SUBTITLE_TEXT_SIZE_NORMAL
        const val MIN_SUBTITLE_SCALE = 0.5f
        const val MAX_SUBTITLE_SCALE = 5.0f
        const val DEFAULT_SUBTITLE_SCALE = 1.5f
        const val DEFAULT_SUBTITLE_TEXT_COLOR = SUBTITLE_TEXT_COLOR_WHITE
        const val DEFAULT_SUBTITLE_BACKGROUND_COLOR = SUBTITLE_BACKGROUND_TRANSPARENT
        const val DEFAULT_SUBTITLE_EDGE_TYPE = SUBTITLE_EDGE_TYPE_NONE
        const val DEFAULT_SUBTITLE_TEXT_OPACITY_PERCENT = 100
        const val DEFAULT_SUBTITLE_BOTTOM_EDGE_PERCENT = 0
        const val DEFAULT_SUBTITLE_TOP_EDGE_PERCENT = 5
        const val DEFAULT_SUBTITLE_ASS_COMPATIBLE = false
        const val MIN_SUBTITLE_DELAY_MS = -10_000
        const val MAX_SUBTITLE_DELAY_MS = 10_000
        const val SUBTITLE_DELAY_STEP_MS = 100
        const val DEFAULT_PLAYER_CACHE_SIZE_MB = 200
        const val MAX_PLAYER_CACHE_SIZE_MB = 500
        const val MIN_PLAYER_CACHE_SIZE_MB = 50
        const val PLAYER_CACHE_SIZE_STEP_MB = 50
        const val DEFAULT_PLAYER_CACHE_TIME_SECONDS = 180
        const val MAX_PLAYER_CACHE_TIME_SECONDS = 900
        const val MIN_PLAYER_CACHE_TIME_SECONDS = 30
        const val PLAYER_CACHE_TIME_STEP_SECONDS = 30
        const val DEFAULT_SEEK_INTERVAL_SECONDS = 10
        const val MAX_SEEK_INTERVAL_SECONDS = 30
        const val MIN_SEEK_INTERVAL_SECONDS = 5
        const val SEEK_INTERVAL_STEP_SECONDS = 5
        const val DEFAULT_LONG_PRESS_PLAYBACK_SPEED = 2.0f
        const val MIN_LONG_PRESS_PLAYBACK_SPEED = 1.5f
        const val MAX_LONG_PRESS_PLAYBACK_SPEED = 3.0f
        val LONG_PRESS_PLAYBACK_SPEED_OPTIONS = listOf(1.5f, 2.0f, 2.5f, 3.0f)
        const val DEFAULT_USE_DEVICE_VOLUME_IN_PLAYER = false
        const val DEFAULT_USE_DEVICE_BRIGHTNESS_IN_PLAYER = false
        const val DEFAULT_CACHE_NEXT_EPISODE = true
        const val DEFAULT_SKIP_INTRO_ENABLED = true
        const val DEFAULT_CHAPTER_MARKERS_ENABLED = true
        const val PLAYER_ENGINE_EXO = "ExoPlayer"
        const val PLAYER_ENGINE_MPV = "MPV"
        const val DEFAULT_PLAYER_ENGINE = PLAYER_ENGINE_MPV
        const val PLAYER_ORIENTATION_PORTRAIT = "portrait"
        const val PLAYER_ORIENTATION_LANDSCAPE = "landscape"
        const val PLAYER_ORIENTATION_AUTO = "auto"
        const val DEFAULT_PLAYER_ORIENTATION = PLAYER_ORIENTATION_PORTRAIT
        val PLAYER_ORIENTATION_OPTIONS = listOf(
            PLAYER_ORIENTATION_PORTRAIT,
            PLAYER_ORIENTATION_LANDSCAPE,
            PLAYER_ORIENTATION_AUTO
        )
        val PLAYER_ENGINE_OPTIONS = listOf(PLAYER_ENGINE_EXO, PLAYER_ENGINE_MPV)
        const val MPV_HARDWARE_DECODING_NONE = "no"
        const val MPV_HARDWARE_DECODING_MEDIACODEC = "mediacodec"
        const val MPV_HARDWARE_DECODING_MEDIACODEC_COPY = "mediacodec-copy"
        const val DEFAULT_MPV_HARDWARE_DECODING = MPV_HARDWARE_DECODING_MEDIACODEC
        val MPV_HARDWARE_DECODING_OPTIONS = listOf(
            MPV_HARDWARE_DECODING_NONE,
            MPV_HARDWARE_DECODING_MEDIACODEC,
            MPV_HARDWARE_DECODING_MEDIACODEC_COPY
        )
        const val MPV_VIDEO_OUTPUT_GPU_NEXT = "gpu-next"
        const val MPV_VIDEO_OUTPUT_GPU = "gpu"
        const val DEFAULT_MPV_VIDEO_OUTPUT = MPV_VIDEO_OUTPUT_GPU
        val MPV_VIDEO_OUTPUT_OPTIONS = listOf(MPV_VIDEO_OUTPUT_GPU_NEXT, MPV_VIDEO_OUTPUT_GPU)
        const val MPV_AUDIO_OUTPUT_AAUDIO = "aaudio"
        const val MPV_AUDIO_OUTPUT_AUDIOTRACK = "audiotrack"
        const val MPV_AUDIO_OUTPUT_OPENSLES = "opensles"
        const val DEFAULT_MPV_AUDIO_OUTPUT = MPV_AUDIO_OUTPUT_AUDIOTRACK
        val MPV_AUDIO_OUTPUT_OPTIONS = listOf(
            MPV_AUDIO_OUTPUT_AAUDIO,
            MPV_AUDIO_OUTPUT_AUDIOTRACK,
            MPV_AUDIO_OUTPUT_OPENSLES
        )

        const val MPV_UPSCALE_FILTER_BILINEAR = "bilinear"
        const val MPV_UPSCALE_FILTER_SPLINE36 = "spline36"
        const val MPV_UPSCALE_FILTER_LANCZOS = "lanczos"
        const val MPV_UPSCALE_FILTER_EWA_LANCZOS = "ewa_lanczos"
        const val DEFAULT_MPV_UPSCALE_FILTER = MPV_UPSCALE_FILTER_LANCZOS
        val MPV_UPSCALE_FILTER_OPTIONS = listOf(
            MPV_UPSCALE_FILTER_BILINEAR,
            MPV_UPSCALE_FILTER_SPLINE36,
            MPV_UPSCALE_FILTER_LANCZOS,
            MPV_UPSCALE_FILTER_EWA_LANCZOS
        )

        const val MPV_DOWNSCALE_FILTER_HERMITE = "hermite"
        const val MPV_DOWNSCALE_FILTER_MITCHELL = "mitchell"
        const val MPV_DOWNSCALE_FILTER_CATMULL_ROM = "catmull_rom"
        const val MPV_DOWNSCALE_FILTER_LANCZOS = "lanczos"
        const val DEFAULT_MPV_DOWNSCALE_FILTER = MPV_DOWNSCALE_FILTER_HERMITE
        val MPV_DOWNSCALE_FILTER_OPTIONS = listOf(
            MPV_DOWNSCALE_FILTER_HERMITE,
            MPV_DOWNSCALE_FILTER_MITCHELL,
            MPV_DOWNSCALE_FILTER_CATMULL_ROM,
            MPV_DOWNSCALE_FILTER_LANCZOS
        )

        const val MPV_TONE_MAPPING_AUTO = "auto"
        const val MPV_TONE_MAPPING_BT2390 = "bt.2390"
        const val MPV_TONE_MAPPING_SPLINE = "spline"
        const val MPV_TONE_MAPPING_HABLE = "hable"
        const val MPV_TONE_MAPPING_MOBIUS = "mobius"
        const val MPV_TONE_MAPPING_REINHARD = "reinhard"
        const val DEFAULT_MPV_TONE_MAPPING = MPV_TONE_MAPPING_AUTO
        val MPV_TONE_MAPPING_OPTIONS = listOf(
            MPV_TONE_MAPPING_AUTO,
            MPV_TONE_MAPPING_BT2390,
            MPV_TONE_MAPPING_SPLINE,
            MPV_TONE_MAPPING_HABLE,
            MPV_TONE_MAPPING_MOBIUS,
            MPV_TONE_MAPPING_REINHARD
        )

        const val DEFAULT_MPV_SMOOTH_MOTION = false
        const val DEFAULT_MPV_DEBAND = false
        const val DEFAULT_MPV_DYNAMIC_PEAK = true
        const val DEFAULT_MPV_HDR_TO_SDR_TONEMAPPING = false
        const val MPV_TARGET_PRIM_AUTO = "auto"
        const val MPV_TARGET_PRIM_BT709 = "bt.709"
        const val MPV_TARGET_PRIM_BT2020 = "bt.2020"
        const val DEFAULT_MPV_TARGET_PRIM = MPV_TARGET_PRIM_AUTO
        val MPV_TARGET_PRIM_OPTIONS = listOf(
            MPV_TARGET_PRIM_AUTO,
            MPV_TARGET_PRIM_BT709,
            MPV_TARGET_PRIM_BT2020
        )
        const val MPV_TARGET_TRC_AUTO = "auto"
        const val MPV_TARGET_TRC_BT1886 = "bt.1886"
        const val MPV_TARGET_TRC_SRGB = "srgb"
        const val MPV_TARGET_TRC_GAMMA22 = "gamma2.2"
        const val DEFAULT_MPV_TARGET_TRC = MPV_TARGET_TRC_AUTO
        val MPV_TARGET_TRC_OPTIONS = listOf(
            MPV_TARGET_TRC_AUTO,
            MPV_TARGET_TRC_BT1886,
            MPV_TARGET_TRC_SRGB,
            MPV_TARGET_TRC_GAMMA22
        )
        const val MPV_OUTPUT_LEVELS_AUTO = "auto"
        const val MPV_OUTPUT_LEVELS_FULL = "full"
        const val MPV_OUTPUT_LEVELS_LIMITED = "limited"
        const val DEFAULT_MPV_OUTPUT_LEVELS = MPV_OUTPUT_LEVELS_AUTO
        val MPV_OUTPUT_LEVELS_OPTIONS = listOf(
            MPV_OUTPUT_LEVELS_AUTO,
            MPV_OUTPUT_LEVELS_FULL,
            MPV_OUTPUT_LEVELS_LIMITED
        )
        const val DECODER_PRIORITY_HARDWARE = "Hardware Decoder"
        const val DECODER_PRIORITY_SOFTWARE = "Software Decoder"
        const val DECODER_PRIORITY_AUTO = "Auto"

        const val STREAMING_QUALITY_ORIGINAL = TranscodeProfiles.ORIGINAL
        val STREAMING_QUALITY_OPTIONS: List<String> = TranscodeProfiles.OPTIONS
        const val DEFAULT_STREAMING_QUALITY = STREAMING_QUALITY_ORIGINAL
        val AUDIO_TRANSCODE_MODE_OPTIONS: List<String> =
            AudioTranscodeMode.entries.map { it.displayName }

        fun getStreamingQualityMaxHeightForOption(quality: String): Int? {
            return TranscodeProfiles.maxHeightForOption(quality)
        }

        fun getStreamingQualityOptions(sourceVideoHeight: Int?): List<String> {
            if (sourceVideoHeight == null || sourceVideoHeight <= 0) {
                return STREAMING_QUALITY_OPTIONS
            }

            return STREAMING_QUALITY_OPTIONS.filter { quality ->
                val maxHeight = getStreamingQualityMaxHeightForOption(quality)
                maxHeight == null || maxHeight <= sourceVideoHeight
            }
        }

        const val MAX_SUBTITLE_EDGE_PERCENT = 100
        private const val MAX_SUBTITLE_OPACITY_PERCENT = 100
        private const val EXO_SUBTITLE_BASE_FRACTION = 0.0533f

        fun scaleForTextSize(size: String): Float {
            return when (size) {
                SUBTITLE_TEXT_SIZE_SMALL -> 0.85f
                SUBTITLE_TEXT_SIZE_NORMAL -> 1.0f
                SUBTITLE_TEXT_SIZE_LARGE -> 1.25f
                SUBTITLE_TEXT_SIZE_EXTRA_LARGE -> 1.5f
                else -> DEFAULT_SUBTITLE_SCALE
            }
        }

        fun textSizeForScale(scale: Float): String {
            return when {
                scale < 0.925f -> SUBTITLE_TEXT_SIZE_SMALL
                scale < 1.125f -> SUBTITLE_TEXT_SIZE_NORMAL
                scale < 1.375f -> SUBTITLE_TEXT_SIZE_LARGE
                else -> SUBTITLE_TEXT_SIZE_EXTRA_LARGE
            }
        }

        fun mpvSubPosFromBottomPercent(percent: Int): Int {
            // 横屏：0 → 视频底边（紧贴），100 → 视频顶部。
            return (100 - percent.coerceIn(0, MAX_SUBTITLE_EDGE_PERCENT)).coerceIn(0, 100)
        }

        const val DEFAULT_VIDEO_ASPECT = 16f / 9f
        const val PORTRAIT_SUBTITLE_GAP_EM = 1.2f
        /** 横屏底边留一点缝，避免贴死屏幕边缘。 */
        const val LANDSCAPE_SUBTITLE_GAP_EM = 0.5f
        /**
         * 竖屏 1.75x 的观感对应横屏约 1.25x：画面更高时同样滑条会显得过大。
         */
        const val LANDSCAPE_SUBTITLE_SCALE_RATIO = 1.25f / 1.75f
        const val MPV_SUB_FONT_SIZE = 55f
        const val MPV_SUB_FONT_REF_HEIGHT = 720f

        fun fittedVideoHeightPx(
            windowWidth: Int,
            windowHeight: Int,
            videoAspect: Float = DEFAULT_VIDEO_ASPECT
        ): Float {
            if (windowWidth <= 0 || windowHeight <= 0) return 0f
            val aspect = videoAspect.takeIf { it > 0.1f } ?: DEFAULT_VIDEO_ASPECT
            return minOf(windowHeight.toFloat(), windowWidth / aspect).coerceAtLeast(1f)
        }

        fun mpvEstimatedFontHeightPx(userScale: Float, videoHeight: Float): Float {
            val scale = userScale.coerceIn(MIN_SUBTITLE_SCALE, MAX_SUBTITLE_SCALE)
            return (MPV_SUB_FONT_SIZE / MPV_SUB_FONT_REF_HEIGHT) * scale * videoHeight.coerceAtLeast(1f)
        }

        /**
         * 竖屏字号按滑条原值；横屏同一滑条按 [LANDSCAPE_SUBTITLE_SCALE_RATIO] 缩小，
         * 使竖屏 1.75x 转到横屏时接近 1.25x 的观感。
         */
        fun mpvSubScaleForWindow(
            userScale: Float,
            windowWidth: Int,
            windowHeight: Int,
            videoAspect: Float = DEFAULT_VIDEO_ASPECT
        ): Float {
            val scale = userScale.coerceIn(MIN_SUBTITLE_SCALE, MAX_SUBTITLE_SCALE)
            if (fillsWindowVertically(windowWidth, windowHeight, videoAspect)) {
                return scale * LANDSCAPE_SUBTITLE_SCALE_RATIO
            }
            return scale
        }

        /**
         * 竖屏 0% 会把字幕沉到画面下 (1.2+1) em。1.75x 时约等于滑条 30%，
         * 用户常停在这里才贴近画面。横屏没有这段黑边，要扣掉这份补偿。
         */
        fun portraitSubtitleRestShiftPercent(userScale: Float): Float {
            val scale = userScale.coerceIn(MIN_SUBTITLE_SCALE, MAX_SUBTITLE_SCALE)
            return (PORTRAIT_SUBTITLE_GAP_EM + 1f) *
                (MPV_SUB_FONT_SIZE / MPV_SUB_FONT_REF_HEIGHT) *
                scale * 100f
        }

        /**
         * 横屏贴底（留 0.5em 缝）；竖屏落到画面下方 1.2 倍字号处。
         * 滑条往右仍上移。横屏会扣掉竖屏黑边补偿，避免 30% 被抬到画面中部。
         */
        fun mpvSubPosForWindow(
            bottomPercent: Int,
            windowWidth: Int,
            windowHeight: Int,
            userScale: Float = DEFAULT_SUBTITLE_SCALE,
            videoAspect: Float = DEFAULT_VIDEO_ASPECT
        ): Int {
            val fromBottom = bottomPercent.coerceIn(0, MAX_SUBTITLE_EDGE_PERCENT)
            if (windowWidth <= 0 || windowHeight <= 0) {
                return mpvSubPosFromBottomPercent(fromBottom)
            }
            val inset = subtitleViewLetterboxInsetPx(windowWidth, windowHeight, videoAspect)
            val videoHeight = fittedVideoHeightPx(windowWidth, windowHeight, videoAspect)
            if (inset <= 0) {
                val effectiveScale = mpvSubScaleForWindow(
                    userScale,
                    windowWidth,
                    windowHeight,
                    videoAspect
                )
                val fontHeight = mpvEstimatedFontHeightPx(effectiveScale, videoHeight)
                val gapPercent = LANDSCAPE_SUBTITLE_GAP_EM * fontHeight / windowHeight.toFloat() * 100f
                val liftPercent = (
                    fromBottom - portraitSubtitleRestShiftPercent(userScale)
                    ).coerceAtLeast(0f)
                return (100f - gapPercent - liftPercent).roundToInt().coerceIn(0, 100)
            }
            val fontHeight = mpvEstimatedFontHeightPx(userScale, videoHeight)
            val videoBottomFromTop = inset + videoHeight
            val textBottomFromTop = videoBottomFromTop + (PORTRAIT_SUBTITLE_GAP_EM + 1f) * fontHeight
            val restPos = textBottomFromTop / windowHeight.toFloat() * 100f
            val userShift = fromBottom / 100f * videoHeight / windowHeight.toFloat() * 100f
            return (restPos - userShift).roundToInt().coerceIn(0, 150)
        }

        fun exoTextSizeFractionForWindow(
            scale: Float,
            windowWidth: Int,
            windowHeight: Int,
            videoAspect: Float = DEFAULT_VIDEO_ASPECT
        ): Float {
            val effective = mpvSubScaleForWindow(scale, windowWidth, windowHeight, videoAspect)
            val base = exoTextSizeFraction(effective)
            if (windowWidth <= 0 || windowHeight <= 0) return base
            val videoHeight = fittedVideoHeightPx(windowWidth, windowHeight, videoAspect)
            return (base * videoHeight / windowHeight.toFloat()).coerceIn(0.008f, 0.25f)
        }

        fun subtitleViewLetterboxInsetPx(
            windowWidth: Int,
            windowHeight: Int,
            videoAspect: Float = DEFAULT_VIDEO_ASPECT
        ): Int {
            if (windowWidth <= 0 || windowHeight <= 0) return 0
            val videoHeight = fittedVideoHeightPx(windowWidth, windowHeight, videoAspect)
            return ((windowHeight - videoHeight) / 2f).roundToInt().coerceAtLeast(0)
        }

        fun fillsWindowVertically(
            windowWidth: Int,
            windowHeight: Int,
            videoAspect: Float = DEFAULT_VIDEO_ASPECT
        ): Boolean {
            if (windowWidth <= 0 || windowHeight <= 0) return false
            return subtitleViewLetterboxInsetPx(windowWidth, windowHeight, videoAspect) <= 0
        }

        fun subtitleViewBottomPaddingPx(
            bottomPercent: Int,
            windowWidth: Int,
            windowHeight: Int,
            videoAspect: Float = DEFAULT_VIDEO_ASPECT,
            userScale: Float = DEFAULT_SUBTITLE_SCALE
        ): Int {
            val inset = subtitleViewLetterboxInsetPx(windowWidth, windowHeight, videoAspect)
            val videoHeight = fittedVideoHeightPx(windowWidth, windowHeight, videoAspect)
            val fromBottom = bottomPercent.coerceIn(0, MAX_SUBTITLE_EDGE_PERCENT) / 100f
            if (inset <= 0) {
                val effectiveScale = mpvSubScaleForWindow(
                    userScale,
                    windowWidth,
                    windowHeight,
                    videoAspect
                )
                val fontHeight = exoTextSizeFraction(effectiveScale) * videoHeight.coerceAtLeast(1f)
                val gap = LANDSCAPE_SUBTITLE_GAP_EM * fontHeight
                val compensation = portraitSubtitleRestShiftPercent(userScale) / 100f * videoHeight
                val lift = (fromBottom * videoHeight - compensation).coerceAtLeast(0f)
                return (gap + lift).roundToInt().coerceAtLeast(0)
            }
            val fontHeight = exoTextSizeFraction(userScale) * videoHeight
            val belowVideo = (PORTRAIT_SUBTITLE_GAP_EM + 1f) * fontHeight
            val userShift = fromBottom * videoHeight
            return (inset - belowVideo + userShift).roundToInt().coerceAtLeast(0)
        }

        fun subtitleViewTopPaddingPx(
            topPercent: Int,
            windowWidth: Int,
            windowHeight: Int,
            videoAspect: Float = DEFAULT_VIDEO_ASPECT
        ): Int {
            val inset = subtitleViewLetterboxInsetPx(windowWidth, windowHeight, videoAspect)
            val videoHeight = fittedVideoHeightPx(windowWidth, windowHeight, videoAspect)
            val fromTop = topPercent.coerceIn(0, MAX_SUBTITLE_EDGE_PERCENT) / 100f
            return (inset + fromTop * videoHeight).roundToInt()
        }

        fun exoTextSizeFraction(scale: Float): Float {
            return (EXO_SUBTITLE_BASE_FRACTION * scale.coerceIn(MIN_SUBTITLE_SCALE, MAX_SUBTITLE_SCALE))
                .coerceIn(0.02f, 0.25f)
        }

        fun mpvAssOverride(compatible: Boolean): String {
            return if (compatible) "scale" else "force"
        }

        fun mpvAssForceStyle(
            fontFamily: String,
            edgeType: String,
            backgroundColor: String,
            compatible: Boolean
        ): String {
            val parts = mutableListOf<String>()
            if (!compatible && fontFamily.isNotBlank()) {
                parts += "FontName=$fontFamily"
            }
            if (compatible) {
                return parts.joinToString(",")
            }
            parts += "Alignment=2"
            parts += "MarginV=0"
            parts += "MarginL=0"
            parts += "MarginR=0"
            when (edgeType) {
                SUBTITLE_EDGE_TYPE_OUTLINE -> {
                    parts += "Outline=2"
                    parts += "Shadow=0"
                    parts += "BorderStyle=1"
                }
                SUBTITLE_EDGE_TYPE_DROP_SHADOW -> {
                    parts += "Outline=0"
                    parts += "Shadow=2"
                    parts += "BorderStyle=1"
                }
                SUBTITLE_EDGE_TYPE_NONE -> {
                    parts += "Outline=0"
                    parts += "Shadow=0"
                    parts += "BorderStyle=1"
                    parts += "OutlineColour=&H00000000"
                }
                else -> {
                    parts += "Outline=1"
                    parts += "Shadow=0"
                    parts += "BorderStyle=1"
                }
            }
            when (backgroundColor) {
                SUBTITLE_BACKGROUND_BLACK -> {
                    parts += "BorderStyle=3"
                    parts += "BackColour=&HCC000000"
                }
                SUBTITLE_BACKGROUND_WHITE -> {
                    parts += "BorderStyle=3"
                    parts += "BackColour=&HCCFFFFFF"
                }
                else -> parts += "BackColour=&H00000000"
            }
            return parts.joinToString(",")
        }
    }
    
    /**
     * Get the saved player brightness level (0.01f to 1.0f)
     * Returns the last used brightness or default if none saved
     */
    fun getPlayerBrightness(): Float {
        return prefs.getFloat(KEY_PLAYER_BRIGHTNESS, DEFAULT_BRIGHTNESS)
            .coerceIn(0.01f, 1.0f)
    }
    
    /**
     * Save the current player brightness level
     */
    fun setPlayerBrightness(brightness: Float) {
        prefs.edit()
            .putFloat(KEY_PLAYER_BRIGHTNESS, brightness.coerceIn(0.01f, 1.0f))
            .apply()
    }
    
    /**
     * Get the saved player volume level (0.0f to 1.0f)
     * Returns the last used volume or default if none saved
     */
    fun getPlayerVolume(): Float {
        return prefs.getFloat(KEY_PLAYER_VOLUME, DEFAULT_VOLUME)
            .coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Save the current player volume level
     */
    fun setPlayerVolume(volume: Float) {
        prefs.edit()
            .putFloat(KEY_PLAYER_VOLUME, volume.coerceIn(0.0f, 1.0f))
            .apply()
    }
    
    /**
     * Clear all player preferences (useful for reset)
     */
    fun clearPreferences() {
        prefs.edit().clear().apply()
    }

    fun getPlayerEngine(): String {
        val engine = prefs.getString(KEY_PLAYER_ENGINE, DEFAULT_PLAYER_ENGINE) ?: DEFAULT_PLAYER_ENGINE
        return if (engine in PLAYER_ENGINE_OPTIONS) engine else DEFAULT_PLAYER_ENGINE
    }

    fun getPlayerOrientation(): String {
        val value = prefs.getString(KEY_PLAYER_ORIENTATION, DEFAULT_PLAYER_ORIENTATION)
            ?: DEFAULT_PLAYER_ORIENTATION
        return if (value in PLAYER_ORIENTATION_OPTIONS) value else DEFAULT_PLAYER_ORIENTATION
    }

    fun setPlayerOrientation(orientation: String) {
        prefs.edit()
            .putString(
                KEY_PLAYER_ORIENTATION,
                if (orientation in PLAYER_ORIENTATION_OPTIONS) orientation else DEFAULT_PLAYER_ORIENTATION
            )
            .apply()
    }

    fun setPlayerEngine(engine: String) {
        prefs.edit()
            .putString(
                KEY_PLAYER_ENGINE,
                if (engine in PLAYER_ENGINE_OPTIONS) engine else DEFAULT_PLAYER_ENGINE
            )
            .apply()
    }

    fun getMpvHardwareDecoding(): String {
        val value = prefs.getString(KEY_MPV_HARDWARE_DECODING, DEFAULT_MPV_HARDWARE_DECODING)
            ?: DEFAULT_MPV_HARDWARE_DECODING
        return if (value in MPV_HARDWARE_DECODING_OPTIONS) value else DEFAULT_MPV_HARDWARE_DECODING
    }

    fun setMpvHardwareDecoding(hardwareDecoding: String) {
        prefs.edit()
            .putString(
                KEY_MPV_HARDWARE_DECODING,
                if (hardwareDecoding in MPV_HARDWARE_DECODING_OPTIONS) {
                    hardwareDecoding
                } else {
                    DEFAULT_MPV_HARDWARE_DECODING
                }
            )
            .apply()
    }

    fun getMpvVideoOutput(): String {
        val value = prefs.getString(KEY_MPV_VIDEO_OUTPUT, DEFAULT_MPV_VIDEO_OUTPUT)
            ?: DEFAULT_MPV_VIDEO_OUTPUT
        return if (value in MPV_VIDEO_OUTPUT_OPTIONS) value else DEFAULT_MPV_VIDEO_OUTPUT
    }

    fun setMpvVideoOutput(videoOutput: String) {
        prefs.edit()
            .putString(
                KEY_MPV_VIDEO_OUTPUT,
                if (videoOutput in MPV_VIDEO_OUTPUT_OPTIONS) videoOutput else DEFAULT_MPV_VIDEO_OUTPUT
            )
            .apply()
    }

    fun getMpvAudioOutput(): String {
        val value = prefs.getString(KEY_MPV_AUDIO_OUTPUT, DEFAULT_MPV_AUDIO_OUTPUT)
            ?: DEFAULT_MPV_AUDIO_OUTPUT
        return if (value in MPV_AUDIO_OUTPUT_OPTIONS) value else DEFAULT_MPV_AUDIO_OUTPUT
    }

    fun setMpvAudioOutput(audioOutput: String) {
        prefs.edit()
            .putString(
                KEY_MPV_AUDIO_OUTPUT,
                if (audioOutput in MPV_AUDIO_OUTPUT_OPTIONS) audioOutput else DEFAULT_MPV_AUDIO_OUTPUT
            )
            .apply()
    }

    fun getMpvUpscaleFilter(): String {
        val value = prefs.getString(KEY_MPV_UPSCALE_FILTER, DEFAULT_MPV_UPSCALE_FILTER)
            ?: DEFAULT_MPV_UPSCALE_FILTER
        return if (value in MPV_UPSCALE_FILTER_OPTIONS) value else DEFAULT_MPV_UPSCALE_FILTER
    }

    fun setMpvUpscaleFilter(filter: String) {
        prefs.edit()
            .putString(
                KEY_MPV_UPSCALE_FILTER,
                if (filter in MPV_UPSCALE_FILTER_OPTIONS) filter else DEFAULT_MPV_UPSCALE_FILTER
            )
            .apply()
    }

    fun getMpvDownscaleFilter(): String {
        val value = prefs.getString(KEY_MPV_DOWNSCALE_FILTER, DEFAULT_MPV_DOWNSCALE_FILTER)
            ?: DEFAULT_MPV_DOWNSCALE_FILTER
        return if (value in MPV_DOWNSCALE_FILTER_OPTIONS) value else DEFAULT_MPV_DOWNSCALE_FILTER
    }

    fun setMpvDownscaleFilter(filter: String) {
        prefs.edit()
            .putString(
                KEY_MPV_DOWNSCALE_FILTER,
                if (filter in MPV_DOWNSCALE_FILTER_OPTIONS) filter else DEFAULT_MPV_DOWNSCALE_FILTER
            )
            .apply()
    }

    fun getMpvToneMapping(): String {
        val value = prefs.getString(KEY_MPV_TONE_MAPPING, DEFAULT_MPV_TONE_MAPPING)
            ?: DEFAULT_MPV_TONE_MAPPING
        return if (value in MPV_TONE_MAPPING_OPTIONS) value else DEFAULT_MPV_TONE_MAPPING
    }

    fun setMpvToneMapping(toneMapping: String) {
        prefs.edit()
            .putString(
                KEY_MPV_TONE_MAPPING,
                if (toneMapping in MPV_TONE_MAPPING_OPTIONS) toneMapping else DEFAULT_MPV_TONE_MAPPING
            )
            .apply()
    }

    fun getMpvSmoothMotion(): Boolean {
        return prefs.getBoolean(KEY_MPV_SMOOTH_MOTION, DEFAULT_MPV_SMOOTH_MOTION)
    }

    fun setMpvSmoothMotion(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MPV_SMOOTH_MOTION, enabled).apply()
    }

    fun getMpvDeband(): Boolean {
        return prefs.getBoolean(KEY_MPV_DEBAND, DEFAULT_MPV_DEBAND)
    }

    fun setMpvDeband(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MPV_DEBAND, enabled).apply()
    }

    fun getMpvDynamicPeak(): Boolean {
        return prefs.getBoolean(KEY_MPV_DYNAMIC_PEAK, DEFAULT_MPV_DYNAMIC_PEAK)
    }

    fun setMpvDynamicPeak(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MPV_DYNAMIC_PEAK, enabled).apply()
    }

    fun getMpvHdrToSdrTonemapping(): Boolean {
        return prefs.getBoolean(KEY_MPV_HDR_TO_SDR_TONEMAPPING, DEFAULT_MPV_HDR_TO_SDR_TONEMAPPING)
    }

    fun setMpvHdrToSdrTonemapping(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MPV_HDR_TO_SDR_TONEMAPPING, enabled).commit()
    }

    fun getMpvTargetPrim(): String {
        val value = prefs.getString(KEY_MPV_TARGET_PRIM, DEFAULT_MPV_TARGET_PRIM)
            ?: DEFAULT_MPV_TARGET_PRIM
        return if (value in MPV_TARGET_PRIM_OPTIONS) value else DEFAULT_MPV_TARGET_PRIM
    }

    fun setMpvTargetPrim(value: String) {
        prefs.edit()
            .putString(
                KEY_MPV_TARGET_PRIM,
                if (value in MPV_TARGET_PRIM_OPTIONS) value else DEFAULT_MPV_TARGET_PRIM
            )
            .apply()
    }

    fun getMpvTargetTrc(): String {
        val value = prefs.getString(KEY_MPV_TARGET_TRC, DEFAULT_MPV_TARGET_TRC)
            ?: DEFAULT_MPV_TARGET_TRC
        return if (value in MPV_TARGET_TRC_OPTIONS) value else DEFAULT_MPV_TARGET_TRC
    }

    fun setMpvTargetTrc(value: String) {
        prefs.edit()
            .putString(
                KEY_MPV_TARGET_TRC,
                if (value in MPV_TARGET_TRC_OPTIONS) value else DEFAULT_MPV_TARGET_TRC
            )
            .apply()
    }

    fun getMpvOutputLevels(): String {
        val value = prefs.getString(KEY_MPV_OUTPUT_LEVELS, DEFAULT_MPV_OUTPUT_LEVELS)
            ?: DEFAULT_MPV_OUTPUT_LEVELS
        return if (value in MPV_OUTPUT_LEVELS_OPTIONS) value else DEFAULT_MPV_OUTPUT_LEVELS
    }

    fun setMpvOutputLevels(value: String) {
        prefs.edit()
            .putString(
                KEY_MPV_OUTPUT_LEVELS,
                if (value in MPV_OUTPUT_LEVELS_OPTIONS) value else DEFAULT_MPV_OUTPUT_LEVELS
            )
            .apply()
    }

    /**
     * Get hardware acceleration preference
     */
    fun isHardwareAccelerationEnabled(): Boolean {
        return prefs.getBoolean(KEY_HARDWARE_ACCELERATION, true)
    }
    
    /**
     * Set hardware acceleration preference
     */
    fun setHardwareAccelerationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HARDWARE_ACCELERATION, enabled).apply()
    }
    
    /**
     * Get asynchronous MediaCodec preference
     */
    fun isAsyncMediaCodecEnabled(): Boolean {
        return prefs.getBoolean(KEY_ASYNC_MEDIACODEC, false)
    }
    
    /**
     * Set asynchronous MediaCodec preference
     */
    fun setAsyncMediaCodecEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ASYNC_MEDIACODEC, enabled).apply()
    }
    
    /**
     * Get decoder priority preference
     */
    fun getDecoderPriority(): String {
        return prefs.getString(KEY_DECODER_PRIORITY, DECODER_PRIORITY_AUTO) ?: DECODER_PRIORITY_AUTO
    }
    
    /**
     * Set decoder priority preference
     */
    fun setDecoderPriority(priority: String) {
        prefs.edit().putString(KEY_DECODER_PRIORITY, priority).apply()
    }
    
    /**
     * Get battery optimization preference
     */
    fun isBatteryOptimizationEnabled(): Boolean {
        return prefs.getBoolean(KEY_BATTERY_OPTIMIZATION, false)
    }
    
    /**
     * Set battery optimization preference
     */
    fun setBatteryOptimizationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BATTERY_OPTIMIZATION, enabled).apply()
    }

    fun arePlayerGesturesEnabled(): Boolean {
        return prefs.getBoolean(KEY_PLAYER_GESTURES_ENABLED, true)
    }

    fun setPlayerGesturesEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_PLAYER_GESTURES_ENABLED, enabled)
            .apply()
    }

    fun isVolumeBrightnessGesturesEnabled(): Boolean {
        return prefs.getBoolean(KEY_VOLUME_BRIGHTNESS_GESTURES_ENABLED, true)
    }

    fun setVolumeBrightnessGesturesEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_VOLUME_BRIGHTNESS_GESTURES_ENABLED, enabled)
            .apply()
    }

    fun isUseDeviceVolumeInPlayerEnabled(): Boolean {
        return prefs.getBoolean(
            KEY_USE_DEVICE_VOLUME_IN_PLAYER,
            DEFAULT_USE_DEVICE_VOLUME_IN_PLAYER
        )
    }

    fun setUseDeviceVolumeInPlayerEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_USE_DEVICE_VOLUME_IN_PLAYER, enabled)
            .apply()
    }

    fun isUseDeviceBrightnessInPlayerEnabled(): Boolean {
        return prefs.getBoolean(
            KEY_USE_DEVICE_BRIGHTNESS_IN_PLAYER,
            DEFAULT_USE_DEVICE_BRIGHTNESS_IN_PLAYER
        )
    }

    fun setUseDeviceBrightnessInPlayerEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_USE_DEVICE_BRIGHTNESS_IN_PLAYER, enabled)
            .apply()
    }

    fun isProgressSeekGestureEnabled(): Boolean {
        return prefs.getBoolean(KEY_PROGRESS_SEEK_GESTURE_ENABLED, true)
    }

    fun setProgressSeekGestureEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_PROGRESS_SEEK_GESTURE_ENABLED, enabled)
            .apply()
    }

    fun isZoomGestureEnabled(): Boolean {
        return prefs.getBoolean(KEY_ZOOM_GESTURE_ENABLED, true)
    }

    fun setZoomGestureEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_ZOOM_GESTURE_ENABLED, enabled)
            .apply()
    }
    
    /**
     * Get start maximized preference
     */
    fun isStartMaximizedEnabled(): Boolean {
        return prefs.getBoolean(KEY_START_MAXIMIZED, false)
    }
    
    /**
     * Set start maximized preference
     */
    fun setStartMaximizedEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_START_MAXIMIZED, enabled).apply()
    }

    fun isCacheNextEpisodeEnabled(): Boolean {
        return prefs.getBoolean(KEY_CACHE_NEXT_EPISODE, DEFAULT_CACHE_NEXT_EPISODE)
    }

    fun setCacheNextEpisodeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CACHE_NEXT_EPISODE, enabled).apply()
    }
    
    fun getPlayerCacheSizeMb(): Int {
        return prefs.getInt(KEY_PLAYER_CACHE_SIZE_MB, DEFAULT_PLAYER_CACHE_SIZE_MB)
            .coerceIn(MIN_PLAYER_CACHE_SIZE_MB, MAX_PLAYER_CACHE_SIZE_MB)
    }

    fun setPlayerCacheSizeMb(sizeMb: Int) {
        prefs.edit()
            .putInt(
                KEY_PLAYER_CACHE_SIZE_MB,
                sizeMb.coerceIn(MIN_PLAYER_CACHE_SIZE_MB, MAX_PLAYER_CACHE_SIZE_MB)
            )
            .apply()
    }

    fun getPlayerCacheTimeSeconds(): Int {
        return prefs.getInt(KEY_PLAYER_CACHE_TIME_SECONDS, DEFAULT_PLAYER_CACHE_TIME_SECONDS)
            .coerceIn(MIN_PLAYER_CACHE_TIME_SECONDS, MAX_PLAYER_CACHE_TIME_SECONDS)
    }

    fun setPlayerCacheTimeSeconds(seconds: Int) {
        prefs.edit()
            .putInt(
                KEY_PLAYER_CACHE_TIME_SECONDS,
                seconds.coerceIn(MIN_PLAYER_CACHE_TIME_SECONDS, MAX_PLAYER_CACHE_TIME_SECONDS)
            )
            .apply()
    }

    fun getSeekBackwardIntervalSeconds(): Int {
        return prefs.getInt(KEY_SEEK_BACKWARD_INTERVAL_SECONDS, DEFAULT_SEEK_INTERVAL_SECONDS)
            .coerceIn(MIN_SEEK_INTERVAL_SECONDS, MAX_SEEK_INTERVAL_SECONDS)
    }

    fun setSeekBackwardIntervalSeconds(seconds: Int) {
        prefs.edit()
            .putInt(
                KEY_SEEK_BACKWARD_INTERVAL_SECONDS,
                seconds.coerceIn(MIN_SEEK_INTERVAL_SECONDS, MAX_SEEK_INTERVAL_SECONDS)
            )
            .apply()
    }

    fun getSeekForwardIntervalSeconds(): Int {
        return prefs.getInt(KEY_SEEK_FORWARD_INTERVAL_SECONDS, DEFAULT_SEEK_INTERVAL_SECONDS)
            .coerceIn(MIN_SEEK_INTERVAL_SECONDS, MAX_SEEK_INTERVAL_SECONDS)
    }

    fun setSeekForwardIntervalSeconds(seconds: Int) {
        prefs.edit()
            .putInt(
                KEY_SEEK_FORWARD_INTERVAL_SECONDS,
                seconds.coerceIn(MIN_SEEK_INTERVAL_SECONDS, MAX_SEEK_INTERVAL_SECONDS)
            )
            .apply()
    }

    fun getLongPressPlaybackSpeed(): Float {
        return prefs.getFloat(KEY_LONG_PRESS_PLAYBACK_SPEED, DEFAULT_LONG_PRESS_PLAYBACK_SPEED)
            .coerceIn(MIN_LONG_PRESS_PLAYBACK_SPEED, MAX_LONG_PRESS_PLAYBACK_SPEED)
    }

    fun setLongPressPlaybackSpeed(speed: Float) {
        val nearest = LONG_PRESS_PLAYBACK_SPEED_OPTIONS.minBy { option -> kotlin.math.abs(option - speed) }
        prefs.edit().putFloat(KEY_LONG_PRESS_PLAYBACK_SPEED, nearest).apply()
    }

    fun isSkipIntroEnabled(): Boolean {
        return prefs.getBoolean(KEY_SKIP_INTRO_ENABLED, DEFAULT_SKIP_INTRO_ENABLED)
    }

    fun setSkipIntroEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SKIP_INTRO_ENABLED, enabled)
            .apply()
    }

    fun areChapterMarkersEnabled(): Boolean {
        return prefs.getBoolean(KEY_CHAPTER_MARKERS_ENABLED, DEFAULT_CHAPTER_MARKERS_ENABLED)
    }

    fun setChapterMarkersEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_CHAPTER_MARKERS_ENABLED, enabled)
            .apply()
    }

    fun getStreamingQuality(): String {
        val saved = prefs.getString(KEY_STREAMING_QUALITY, DEFAULT_STREAMING_QUALITY)
        return if (saved in STREAMING_QUALITY_OPTIONS) {
            saved!!
        } else {
            DEFAULT_STREAMING_QUALITY
        }
    }

    fun setStreamingQuality(quality: String) {
        val value = if (quality in STREAMING_QUALITY_OPTIONS) {
            quality
        } else {
            DEFAULT_STREAMING_QUALITY
        }
        prefs.edit().putString(KEY_STREAMING_QUALITY, value).apply()
    }

    fun getMaxStreamingBitrate(): Int? {
        return TranscodeProfiles.byLabel(getStreamingQuality())?.maxBitrate
    }

    fun getStreamingQualityMaxHeight(): Int? {
        return TranscodeProfiles.byLabel(getStreamingQuality())?.maxHeight
    }

    fun getAudioTranscodeMode(): AudioTranscodeMode {
        val saved = prefs.getString(
            KEY_AUDIO_TRANSCODE_MODE,
            AudioTranscodeMode.AUTO.preferenceValue
        )
        return AudioTranscodeMode.fromPreferenceValue(saved)
    }

    fun setAudioTranscodeMode(mode: AudioTranscodeMode) {
        prefs.edit()
            .putString(KEY_AUDIO_TRANSCODE_MODE, mode.preferenceValue)
            .apply()
    }

    fun getSubtitleTextSize(): String {
        if (prefs.contains(KEY_SUBTITLE_SCALE)) {
            return textSizeForScale(getSubtitleScale())
        }
        val saved = prefs.getString(KEY_SUBTITLE_TEXT_SIZE, DEFAULT_SUBTITLE_TEXT_SIZE)
        return if (saved in SUBTITLE_TEXT_SIZE_OPTIONS) saved!! else DEFAULT_SUBTITLE_TEXT_SIZE
    }

    fun setSubtitleTextSize(size: String) {
        val value = if (size in SUBTITLE_TEXT_SIZE_OPTIONS) size else DEFAULT_SUBTITLE_TEXT_SIZE
        prefs.edit()
            .putString(KEY_SUBTITLE_TEXT_SIZE, value)
            .putFloat(KEY_SUBTITLE_SCALE, scaleForTextSize(value))
            .apply()
    }

    fun getSubtitleScale(): Float {
        bumpLegacyDefaultSubtitleScale()
        if (prefs.contains(KEY_SUBTITLE_SCALE)) {
            return prefs.getFloat(KEY_SUBTITLE_SCALE, DEFAULT_SUBTITLE_SCALE)
                .coerceIn(MIN_SUBTITLE_SCALE, MAX_SUBTITLE_SCALE)
        }
        return scaleForTextSize(getSubtitleTextSize())
    }

    private fun bumpLegacyDefaultSubtitleScale() {
        if (prefs.getBoolean(KEY_SUBTITLE_SCALE_DEFAULT_1_5, false)) return
        val editor = prefs.edit().putBoolean(KEY_SUBTITLE_SCALE_DEFAULT_1_5, true)
        val storedScale = if (prefs.contains(KEY_SUBTITLE_SCALE)) {
            prefs.getFloat(KEY_SUBTITLE_SCALE, 1.0f)
        } else {
            null
        }
        if (storedScale == null || storedScale == 1.0f) {
            editor.putFloat(KEY_SUBTITLE_SCALE, DEFAULT_SUBTITLE_SCALE)
            editor.putString(KEY_SUBTITLE_TEXT_SIZE, textSizeForScale(DEFAULT_SUBTITLE_SCALE))
        }
        editor.apply()
    }

    fun setSubtitleScale(scale: Float) {
        val value = scale.coerceIn(MIN_SUBTITLE_SCALE, MAX_SUBTITLE_SCALE)
        prefs.edit()
            .putFloat(KEY_SUBTITLE_SCALE, value)
            .putString(KEY_SUBTITLE_TEXT_SIZE, textSizeForScale(value))
            .apply()
    }

    fun isSubtitleAssCompatible(): Boolean {
        return prefs.getBoolean(KEY_SUBTITLE_ASS_COMPATIBLE, DEFAULT_SUBTITLE_ASS_COMPATIBLE)
    }

    fun setSubtitleAssCompatible(compatible: Boolean) {
        prefs.edit().putBoolean(KEY_SUBTITLE_ASS_COMPATIBLE, compatible).apply()
    }

    fun getSubtitleTextColor(): String {
        val saved = prefs.getString(KEY_SUBTITLE_TEXT_COLOR, DEFAULT_SUBTITLE_TEXT_COLOR)
        return if (saved in SUBTITLE_TEXT_COLOR_OPTIONS) saved!! else DEFAULT_SUBTITLE_TEXT_COLOR
    }

    fun setSubtitleTextColor(color: String) {
        val value = if (color in SUBTITLE_TEXT_COLOR_OPTIONS) color else DEFAULT_SUBTITLE_TEXT_COLOR
        prefs.edit().putString(KEY_SUBTITLE_TEXT_COLOR, value).apply()
    }

    fun getSubtitleBackgroundColor(): String {
        val saved = prefs.getString(KEY_SUBTITLE_BACKGROUND_COLOR, DEFAULT_SUBTITLE_BACKGROUND_COLOR)
        return if (saved in SUBTITLE_BACKGROUND_OPTIONS) saved!! else DEFAULT_SUBTITLE_BACKGROUND_COLOR
    }

    fun setSubtitleBackgroundColor(color: String) {
        val value = if (color in SUBTITLE_BACKGROUND_OPTIONS) color else DEFAULT_SUBTITLE_BACKGROUND_COLOR
        prefs.edit().putString(KEY_SUBTITLE_BACKGROUND_COLOR, value).apply()
    }

    fun getSubtitleEdgeType(): String {
        val saved = prefs.getString(KEY_SUBTITLE_EDGE_TYPE, DEFAULT_SUBTITLE_EDGE_TYPE)
        return if (saved in SUBTITLE_EDGE_TYPE_OPTIONS) saved!! else DEFAULT_SUBTITLE_EDGE_TYPE
    }

    fun setSubtitleEdgeType(edgeType: String) {
        val value = if (edgeType in SUBTITLE_EDGE_TYPE_OPTIONS) edgeType else DEFAULT_SUBTITLE_EDGE_TYPE
        prefs.edit().putString(KEY_SUBTITLE_EDGE_TYPE, value).apply()
    }

    fun getSubtitleTextOpacityPercent(): Int {
        return prefs.getInt(
            KEY_SUBTITLE_TEXT_OPACITY_PERCENT,
            DEFAULT_SUBTITLE_TEXT_OPACITY_PERCENT
        ).coerceIn(0, MAX_SUBTITLE_OPACITY_PERCENT)
    }

    fun setSubtitleTextOpacityPercent(percent: Int) {
        prefs.edit()
            .putInt(
                KEY_SUBTITLE_TEXT_OPACITY_PERCENT,
                percent.coerceIn(0, MAX_SUBTITLE_OPACITY_PERCENT)
            )
            .apply()
    }

    fun getSubtitlePosition(): Int {
        return prefs.getInt(
            KEY_SUBTITLE_BOTTOM_EDGE_PERCENT,
            DEFAULT_SUBTITLE_BOTTOM_EDGE_PERCENT
        ).coerceIn(0, MAX_SUBTITLE_EDGE_PERCENT)
    }

    fun getSubtitleBottomEdgePositionPercent(): Int {
        return prefs.getInt(
            KEY_SUBTITLE_BOTTOM_EDGE_PERCENT,
            DEFAULT_SUBTITLE_BOTTOM_EDGE_PERCENT
        ).coerceIn(0, MAX_SUBTITLE_EDGE_PERCENT)
    }

    fun setSubtitleBottomEdgePositionPercent(percent: Int) {
        prefs.edit()
            .putInt(KEY_SUBTITLE_BOTTOM_EDGE_PERCENT, percent.coerceIn(0, MAX_SUBTITLE_EDGE_PERCENT))
            .apply()
    }

    fun getSubtitleTopEdgePositionPercent(): Int {
        return prefs.getInt(
            KEY_SUBTITLE_TOP_EDGE_PERCENT,
            DEFAULT_SUBTITLE_TOP_EDGE_PERCENT
        ).coerceIn(0, MAX_SUBTITLE_EDGE_PERCENT)
    }

    fun setSubtitleTopEdgePositionPercent(percent: Int) {
        prefs.edit()
            .putInt(KEY_SUBTITLE_TOP_EDGE_PERCENT, percent.coerceIn(0, MAX_SUBTITLE_EDGE_PERCENT))
            .apply()
    }

    fun getSubtitleDelayMs(): Int {
        return prefs.getInt(KEY_SUBTITLE_DELAY_MS, 0)
            .coerceIn(MIN_SUBTITLE_DELAY_MS, MAX_SUBTITLE_DELAY_MS)
    }

    fun setSubtitleDelayMs(delayMs: Int) {
        prefs.edit()
            .putInt(
                KEY_SUBTITLE_DELAY_MS,
                delayMs.coerceIn(MIN_SUBTITLE_DELAY_MS, MAX_SUBTITLE_DELAY_MS)
            )
            .apply()
    }

    fun getPreferredAudioStreamIndex(itemId: String): Int? {
        val key = audioStreamKey(itemId)
        return if (prefs.contains(key)) prefs.getInt(key, 0) else null
    }

    fun setPreferredAudioStreamIndex(itemId: String, streamIndex: Int?) {
        val key = audioStreamKey(itemId)
        val subtitleExists = prefs.contains(subtitleStreamKey(itemId))
        prefs.edit().apply {
            if (streamIndex == null) {
                remove(key)
            } else {
                putInt(key, streamIndex)
            }
            if (streamIndex == null && !subtitleExists) {
                remove(streamUpdatedAtKey(itemId))
            } else {
                putLong(streamUpdatedAtKey(itemId), System.currentTimeMillis())
            }
        }.apply()
        prunePreferredStreamIndexesIfNeeded()
    }

    fun getPreferredSubtitleStreamIndex(itemId: String): Int? {
        val key = subtitleStreamKey(itemId)
        return if (prefs.contains(key)) prefs.getInt(key, 0) else null
    }

    fun setPreferredSubtitleStreamIndex(itemId: String, streamIndex: Int?) {
        val key = subtitleStreamKey(itemId)
        val audioExists = prefs.contains(audioStreamKey(itemId))
        prefs.edit().apply {
            if (streamIndex == null) {
                remove(key)
            } else {
                putInt(key, streamIndex)
            }
            if (streamIndex == null && !audioExists) {
                remove(streamUpdatedAtKey(itemId))
            } else {
                putLong(streamUpdatedAtKey(itemId), System.currentTimeMillis())
            }
        }.apply()
        prunePreferredStreamIndexesIfNeeded()
    }

    private fun audioStreamKey(itemId: String): String {
        return "$KEY_AUDIO_STREAM_INDEX_PREFIX$itemId"
    }

    private fun subtitleStreamKey(itemId: String): String {
        return "$KEY_SUBTITLE_STREAM_INDEX_PREFIX$itemId"
    }

    private fun streamUpdatedAtKey(itemId: String): String {
        return "$KEY_STREAM_INDEX_UPDATED_AT_PREFIX$itemId"
    }

    private fun prunePreferredStreamIndexesIfNeeded() {
        val itemIds = mutableSetOf<String>()
        prefs.all.keys.forEach { key ->
            when {
                key.startsWith(KEY_AUDIO_STREAM_INDEX_PREFIX) -> {
                    itemIds.add(key.removePrefix(KEY_AUDIO_STREAM_INDEX_PREFIX))
                }
                key.startsWith(KEY_SUBTITLE_STREAM_INDEX_PREFIX) -> {
                    itemIds.add(key.removePrefix(KEY_SUBTITLE_STREAM_INDEX_PREFIX))
                }
            }
        }

        if (itemIds.size <= MAX_PREFERRED_STREAM_ITEMS) return

        val toRemoveCount = itemIds.size - MAX_PREFERRED_STREAM_ITEMS
        val oldestItems = itemIds
            .map { itemId ->
                itemId to prefs.getLong(streamUpdatedAtKey(itemId), 0L)
            }
            .sortedBy { it.second }
            .take(toRemoveCount)

        prefs.edit().apply {
            oldestItems.forEach { (itemId, _) ->
                remove(audioStreamKey(itemId))
                remove(subtitleStreamKey(itemId))
                remove(streamUpdatedAtKey(itemId))
            }
        }.apply()
    }
}
