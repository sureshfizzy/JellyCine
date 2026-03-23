package com.jellycine.player.preferences

import android.content.Context
import android.content.SharedPreferences
import com.jellycine.data.model.AudioTranscodeMode
import com.jellycine.player.core.PlayerConstants.DEFAULT_BRIGHTNESS
import com.jellycine.player.core.PlayerConstants.DEFAULT_VOLUME

/**
 * Manages player-specific preferences like brightness, volume levels, and hardware acceleration settings
 * Follows ExoPlayer's approach to remember user settings per player session
 */
class PlayerPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "jellycine_player_prefs"
        private const val KEY_PLAYER_BRIGHTNESS = "player_brightness"
        private const val KEY_PLAYER_VOLUME = "player_volume"
        private const val KEY_HARDWARE_ACCELERATION = "hardware_acceleration_enabled"
        private const val KEY_ASYNC_MEDIACODEC = "async_mediacodec_enabled"
        private const val KEY_DECODER_PRIORITY = "decoder_priority"
        private const val KEY_BATTERY_OPTIMIZATION = "battery_optimization_enabled"
        private const val KEY_PLAYER_GESTURES_ENABLED = "player_gestures_enabled"
        private const val KEY_VOLUME_BRIGHTNESS_GESTURES_ENABLED = "volume_brightness_gestures_enabled"
        private const val KEY_PROGRESS_SEEK_GESTURE_ENABLED = "progress_seek_gesture_enabled"
        private const val KEY_ZOOM_GESTURE_ENABLED = "zoom_gesture_enabled"
        private const val KEY_START_MAXIMIZED = "start_maximized"
        private const val KEY_HDR_ENABLED = "hdr_enabled"
        private const val KEY_PLAYER_CACHE_SIZE_MB = "player_cache_size_mb"
        private const val KEY_PLAYER_CACHE_TIME_SECONDS = "player_cache_time_seconds"
        private const val KEY_SEEK_BACKWARD_INTERVAL_SECONDS = "seek_backward_interval_seconds"
        private const val KEY_SEEK_FORWARD_INTERVAL_SECONDS = "seek_forward_interval_seconds"
        private const val KEY_SKIP_INTRO_ENABLED = "skip_intro_enabled"
        private const val KEY_SUBTITLE_TEXT_SIZE = "subtitle_text_size"
        private const val KEY_SUBTITLE_TEXT_COLOR = "subtitle_text_color"
        private const val KEY_SUBTITLE_BACKGROUND_COLOR = "subtitle_background_color"
        private const val KEY_SUBTITLE_EDGE_TYPE = "subtitle_edge_type"
        private const val KEY_SUBTITLE_TEXT_OPACITY_PERCENT = "subtitle_text_opacity_percent"
        private const val KEY_SUBTITLE_BOTTOM_EDGE_PERCENT = "subtitle_bottom_edge_percent"
        private const val KEY_SUBTITLE_TOP_EDGE_PERCENT = "subtitle_top_edge_percent"
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
        const val DEFAULT_SUBTITLE_TEXT_COLOR = SUBTITLE_TEXT_COLOR_WHITE
        const val DEFAULT_SUBTITLE_BACKGROUND_COLOR = SUBTITLE_BACKGROUND_TRANSPARENT
        const val DEFAULT_SUBTITLE_EDGE_TYPE = SUBTITLE_EDGE_TYPE_NONE
        const val DEFAULT_SUBTITLE_TEXT_OPACITY_PERCENT = 100
        const val DEFAULT_SUBTITLE_BOTTOM_EDGE_PERCENT = 10
        const val DEFAULT_SUBTITLE_TOP_EDGE_PERCENT = 5
        const val DEFAULT_PLAYER_CACHE_SIZE_MB = 200
        const val MAX_PLAYER_CACHE_SIZE_MB = 500
        const val MIN_PLAYER_CACHE_SIZE_MB = 50
        const val PLAYER_CACHE_SIZE_STEP_MB = 50
        const val DEFAULT_PLAYER_CACHE_TIME_SECONDS = 120
        const val MAX_PLAYER_CACHE_TIME_SECONDS = 900
        const val MIN_PLAYER_CACHE_TIME_SECONDS = 30
        const val PLAYER_CACHE_TIME_STEP_SECONDS = 30
        const val DEFAULT_SEEK_INTERVAL_SECONDS = 30
        const val MAX_SEEK_INTERVAL_SECONDS = 30
        const val MIN_SEEK_INTERVAL_SECONDS = 5
        const val SEEK_INTERVAL_STEP_SECONDS = 5
        const val DEFAULT_SKIP_INTRO_ENABLED = true

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

        private const val MAX_SUBTITLE_EDGE_PERCENT = 50
        private const val MAX_SUBTITLE_OPACITY_PERCENT = 100
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
        return prefs.getString(KEY_DECODER_PRIORITY, "Auto") ?: "Auto"
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
    
    /**
     * Get HDR enabled preference
     */
    fun isHdrEnabled(): Boolean {
        return prefs.getBoolean(KEY_HDR_ENABLED, true)
    }
    
    /**
     * Set HDR enabled preference
     */
    fun setHdrEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HDR_ENABLED, enabled).apply()
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

    fun isSkipIntroEnabled(): Boolean {
        return prefs.getBoolean(KEY_SKIP_INTRO_ENABLED, DEFAULT_SKIP_INTRO_ENABLED)
    }

    fun setSkipIntroEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SKIP_INTRO_ENABLED, enabled)
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
        val saved = prefs.getString(KEY_SUBTITLE_TEXT_SIZE, DEFAULT_SUBTITLE_TEXT_SIZE)
        return if (saved in SUBTITLE_TEXT_SIZE_OPTIONS) saved!! else DEFAULT_SUBTITLE_TEXT_SIZE
    }

    fun setSubtitleTextSize(size: String) {
        val value = if (size in SUBTITLE_TEXT_SIZE_OPTIONS) size else DEFAULT_SUBTITLE_TEXT_SIZE
        prefs.edit().putString(KEY_SUBTITLE_TEXT_SIZE, value).apply()
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
