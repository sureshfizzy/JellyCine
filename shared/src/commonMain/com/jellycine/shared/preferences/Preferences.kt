package com.jellycine.shared.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow

class Preferences(private val prefs: KeyValueStore) {

    companion object {
        const val PREFS_NAME = "jellycine_download_prefs"
        private const val KEY_WIFI_ONLY_DOWNLOADS = "wifi_only_downloads"
        private const val KEY_FORCE_OFFLINE = "force_offline"
        private const val KEY_FEATURE_CAROUSEL_ENABLED = "feature_carousel_enabled"
        private const val KEY_FEATURE_CAROUSEL_HEIGHT = "feature_carousel_height"
        private const val KEY_POSTER_ENHANCERS_ENABLED = "poster_enhancers_enabled"
        private const val KEY_CONTINUE_WATCHING_ENABLED = "continue_watching_enabled"
        private const val KEY_NEXT_UP_ENABLED = "next_up_enabled"
        private const val KEY_USE_MY_MEDIA_TAB = "use_my_media_tab"
        private const val KEY_MERGE_VERSIONS_ENABLED = "merge_versions_enabled"
        private const val KEY_SHOW_REVIEWS_ENABLED = "show_reviews_enabled"
        private const val KEY_SEERR_STUDIOS_ENABLED = "seerr_studios_enabled"
        private const val KEY_SEERR_NETWORKS_ENABLED = "seerr_networks_enabled"
        private const val KEY_FEATURE_CAROUSEL_AUTOPLAY_TRAILERS = "feature_carousel_autoplay_trailers"
        private const val KEY_DISCORD_RPC_ENABLED = "discord_rpc_enabled"
        private const val KEY_THEME_MUSIC_MODE = "theme_music_mode"
        private const val KEY_THEME_MUSIC_VOLUME = "theme_music_volume"

        private const val KEY_CLIPS_MUTED = "clips_muted"
        private const val KEY_CLIPS_MAX_QUALITY = "clips_max_quality"
        private const val KEY_CLIPS_CONTENT_TYPE = "clips_content_type"
        private const val KEY_CLIPS_TRENDING_WINDOW = "clips_trending_window"
        private const val KEY_CLIPS_EXCLUDED_GENRES = "clips_excluded_genres"

        const val CLIPS_QUALITY_AUTO = 0
        const val CLIPS_CONTENT_ALL = "all"
        const val CLIPS_CONTENT_MOVIE = "movie"
        const val CLIPS_CONTENT_TV = "tv"
        const val CLIPS_WINDOW_DAY = "day"
        const val CLIPS_WINDOW_WEEK = "week"

        const val FEATURE_CAROUSEL_HEIGHT_LARGE = "large"
        const val FEATURE_CAROUSEL_HEIGHT_MEDIUM = "medium"
        const val FEATURE_CAROUSEL_HEIGHT_SMALL = "small"
        const val THEME_MUSIC_NO = "no"
        const val THEME_MUSIC_ONCE = "once"
        const val THEME_MUSIC_ENDLESS = "endless"
        const val DEFAULT_THEME_MUSIC_VOLUME = 0.6f
    }

    fun isWifiOnlyDownloadsEnabled(): Boolean {
        return prefs.getBoolean(KEY_WIFI_ONLY_DOWNLOADS, false)
    }

    fun setWifiOnlyDownloadsEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_WIFI_ONLY_DOWNLOADS, enabled)
    }

    fun isForceOfflineEnabled(): Boolean {
        return prefs.getBoolean(KEY_FORCE_OFFLINE, false)
    }

    fun setForceOfflineEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_FORCE_OFFLINE, enabled)
    }

    fun ForceOfflineEnabled(): Flow<Boolean> = flow {
        emit(isForceOfflineEnabled())
        prefs.changes
            .filter { key -> key == null || key == KEY_FORCE_OFFLINE }
            .collect { emit(isForceOfflineEnabled()) }
    }.distinctUntilChanged()

    fun isFeatureCarouselEnabled(): Boolean {
        return prefs.getBoolean(KEY_FEATURE_CAROUSEL_ENABLED, true)
    }

    fun setFeatureCarouselEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_FEATURE_CAROUSEL_ENABLED, enabled)
    }

    fun FeatureCarouselEnabled(): Flow<Boolean> = flow {
        emit(isFeatureCarouselEnabled())
        prefs.changes
            .filter { key -> key == null || key == KEY_FEATURE_CAROUSEL_ENABLED }
            .collect { emit(isFeatureCarouselEnabled()) }
    }.distinctUntilChanged()

    fun getFeatureCarouselHeight(): String {
        return normalizeFeatureCarouselHeight(
            prefs.getString(KEY_FEATURE_CAROUSEL_HEIGHT, FEATURE_CAROUSEL_HEIGHT_LARGE)
        )
    }

    fun setFeatureCarouselHeight(height: String) {
        prefs.putString(KEY_FEATURE_CAROUSEL_HEIGHT, normalizeFeatureCarouselHeight(height))
    }

    fun FeatureCarouselHeight(): Flow<String> = flow {
        emit(getFeatureCarouselHeight())
        prefs.changes
            .filter { key -> key == null || key == KEY_FEATURE_CAROUSEL_HEIGHT }
            .collect { emit(getFeatureCarouselHeight()) }
    }.distinctUntilChanged()

    fun isAutoplayTrailersEnabled(): Boolean {
        return prefs.getBoolean(KEY_FEATURE_CAROUSEL_AUTOPLAY_TRAILERS, false)
    }

    fun setFeatureCarouselAutoplayTrailersEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_FEATURE_CAROUSEL_AUTOPLAY_TRAILERS, enabled)
    }

    fun autoplayTrailersEnabled(): Flow<Boolean> = flow {
        emit(isAutoplayTrailersEnabled())
        prefs.changes
            .filter { key -> key == null || key == KEY_FEATURE_CAROUSEL_AUTOPLAY_TRAILERS }
            .collect { emit(isAutoplayTrailersEnabled()) }
    }.distinctUntilChanged()

    fun isPosterEnhancersEnabled(): Boolean {
        return prefs.getBoolean(KEY_POSTER_ENHANCERS_ENABLED, false)
    }

    fun setPosterEnhancersEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_POSTER_ENHANCERS_ENABLED, enabled)
    }

    fun PosterEnhancersEnabled(): Flow<Boolean> = flow {
        emit(isPosterEnhancersEnabled())
        prefs.changes
            .filter { key -> key == null || key == KEY_POSTER_ENHANCERS_ENABLED }
            .collect { emit(isPosterEnhancersEnabled()) }
    }.distinctUntilChanged()

    fun isContinueWatchingEnabled(): Boolean {
        return prefs.getBoolean(KEY_CONTINUE_WATCHING_ENABLED, true)
    }

    fun setContinueWatchingEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_CONTINUE_WATCHING_ENABLED, enabled)
    }

    fun ContinueWatchingEnabled(): Flow<Boolean> = flow {
        emit(isContinueWatchingEnabled())
        prefs.changes
            .filter { key -> key == null || key == KEY_CONTINUE_WATCHING_ENABLED }
            .collect { emit(isContinueWatchingEnabled()) }
    }.distinctUntilChanged()

    fun isNextUpEnabled(): Boolean {
        return prefs.getBoolean(KEY_NEXT_UP_ENABLED, false)
    }

    fun setNextUpEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_NEXT_UP_ENABLED, enabled)
    }

    fun NextUpEnabled(): Flow<Boolean> = flow {
        emit(isNextUpEnabled())
        prefs.changes
            .filter { key -> key == null || key == KEY_NEXT_UP_ENABLED }
            .collect { emit(isNextUpEnabled()) }
    }.distinctUntilChanged()

    fun isUseMyMediaTabEnabled(): Boolean {
        return prefs.getBoolean(KEY_USE_MY_MEDIA_TAB, false)
    }

    fun setUseMyMediaTabEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_USE_MY_MEDIA_TAB, enabled)
    }

    fun UseMyMediaTabEnabled(): Flow<Boolean> = flow {
        emit(isUseMyMediaTabEnabled())
        prefs.changes
            .filter { key -> key == null || key == KEY_USE_MY_MEDIA_TAB }
            .collect { emit(isUseMyMediaTabEnabled()) }
    }.distinctUntilChanged()

    fun isMergeVersionsEnabled(): Boolean {
        return prefs.getBoolean(KEY_MERGE_VERSIONS_ENABLED, false)
    }

    fun setMergeVersionsEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_MERGE_VERSIONS_ENABLED, enabled)
    }

    fun MergeVersionsEnabled(): Flow<Boolean> = flow {
        emit(isMergeVersionsEnabled())
        prefs.changes
            .filter { key -> key == null || key == KEY_MERGE_VERSIONS_ENABLED }
            .collect { emit(isMergeVersionsEnabled()) }
    }.distinctUntilChanged()

    fun isShowReviewsEnabled(): Boolean {
        return prefs.getBoolean(KEY_SHOW_REVIEWS_ENABLED, true)
    }

    fun setShowReviewsEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_SHOW_REVIEWS_ENABLED, enabled)
    }

    fun ShowReviewsEnabled(): Flow<Boolean> = flow {
        emit(isShowReviewsEnabled())
        prefs.changes
            .filter { key -> key == null || key == KEY_SHOW_REVIEWS_ENABLED }
            .collect { emit(isShowReviewsEnabled()) }
    }.distinctUntilChanged()

    fun isSeerrStudiosEnabled(): Boolean {
        return prefs.getBoolean(KEY_SEERR_STUDIOS_ENABLED, true)
    }

    fun setSeerrStudiosEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_SEERR_STUDIOS_ENABLED, enabled)
    }

    fun SeerrStudiosEnabled(): Flow<Boolean> = flow {
        emit(isSeerrStudiosEnabled())
        prefs.changes
            .filter { key -> key == null || key == KEY_SEERR_STUDIOS_ENABLED }
            .collect { emit(isSeerrStudiosEnabled()) }
    }.distinctUntilChanged()

    fun isSeerrNetworksEnabled(): Boolean {
        return prefs.getBoolean(KEY_SEERR_NETWORKS_ENABLED, true)
    }

    fun setSeerrNetworksEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_SEERR_NETWORKS_ENABLED, enabled)
    }

    fun SeerrNetworksEnabled(): Flow<Boolean> = flow {
        emit(isSeerrNetworksEnabled())
        prefs.changes
            .filter { key -> key == null || key == KEY_SEERR_NETWORKS_ENABLED }
            .collect { emit(isSeerrNetworksEnabled()) }
    }.distinctUntilChanged()

    fun isDiscordRpcEnabled(): Boolean {
        return prefs.getBoolean(KEY_DISCORD_RPC_ENABLED, true)
    }

    fun setDiscordRpcEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_DISCORD_RPC_ENABLED, enabled)
    }

    fun discordRpcEnabled(): Flow<Boolean> = flow {
        emit(isDiscordRpcEnabled())
        prefs.changes
            .filter { key -> key == null || key == KEY_DISCORD_RPC_ENABLED }
            .collect { emit(isDiscordRpcEnabled()) }
    }.distinctUntilChanged()

    fun getThemeMusicMode(): String {
        return validateThemeMusicMode(
            prefs.getString(KEY_THEME_MUSIC_MODE, THEME_MUSIC_ONCE)
        )
    }

    fun setThemeMusicMode(mode: String) {
        prefs.putString(KEY_THEME_MUSIC_MODE, validateThemeMusicMode(mode))
    }

    fun themeMusicMode(): Flow<String> = flow {
        emit(getThemeMusicMode())
        prefs.changes
            .filter { key -> key == null || key == KEY_THEME_MUSIC_MODE }
            .collect { emit(getThemeMusicMode()) }
    }.distinctUntilChanged()

    fun getThemeMusicVolume(): Float {
        return prefs.getFloat(KEY_THEME_MUSIC_VOLUME, DEFAULT_THEME_MUSIC_VOLUME).coerceIn(0f, 1f)
    }

    fun setThemeMusicVolume(volume: Float) {
        prefs.putFloat(KEY_THEME_MUSIC_VOLUME, volume.coerceIn(0f, 1f))
    }

    fun themeMusicVolume(): Flow<Float> = flow {
        emit(getThemeMusicVolume())
        prefs.changes
            .filter { key -> key == null || key == KEY_THEME_MUSIC_VOLUME }
            .collect { emit(getThemeMusicVolume()) }
    }.distinctUntilChanged()

    private fun <T> clipsFlow(vararg keys: String, read: () -> T): Flow<T> = flow {
        emit(read())
        prefs.changes
            .filter { key -> key == null || key in keys }
            .collect { emit(read()) }
    }.distinctUntilChanged()

    fun isClipsMuted(): Boolean = prefs.getBoolean(KEY_CLIPS_MUTED, false)
    fun setClipsMuted(muted: Boolean) {
        prefs.putBoolean(KEY_CLIPS_MUTED, muted)
    }

    fun getClipsMaxQuality(): Int = prefs.getInt(KEY_CLIPS_MAX_QUALITY, CLIPS_QUALITY_AUTO)
    fun setClipsMaxQuality(height: Int) {
        prefs.putInt(KEY_CLIPS_MAX_QUALITY, height)
    }
    fun clipsMaxQuality(): Flow<Int> = clipsFlow(KEY_CLIPS_MAX_QUALITY) { getClipsMaxQuality() }

    fun getClipsContentType(): String =
        when (val v = prefs.getString(KEY_CLIPS_CONTENT_TYPE, CLIPS_CONTENT_ALL)) {
            CLIPS_CONTENT_MOVIE, CLIPS_CONTENT_TV -> v
            else -> CLIPS_CONTENT_ALL
        }
    fun setClipsContentType(type: String) {
        prefs.putString(KEY_CLIPS_CONTENT_TYPE, type)
    }
    fun clipsContentType(): Flow<String> = clipsFlow(KEY_CLIPS_CONTENT_TYPE) { getClipsContentType() }

    fun getClipsTrendingWindow(): String =
        if (prefs.getString(KEY_CLIPS_TRENDING_WINDOW, CLIPS_WINDOW_WEEK) == CLIPS_WINDOW_DAY) {
            CLIPS_WINDOW_DAY
        } else {
            CLIPS_WINDOW_WEEK
        }
    fun setClipsTrendingWindow(window: String) {
        prefs.putString(KEY_CLIPS_TRENDING_WINDOW, window)
    }
    fun clipsTrendingWindow(): Flow<String> =
        clipsFlow(KEY_CLIPS_TRENDING_WINDOW) { getClipsTrendingWindow() }

    fun getClipsExcludedGenres(): Set<Int> =
        prefs.getStringSet(KEY_CLIPS_EXCLUDED_GENRES, emptySet())
            .mapNotNull { it.toIntOrNull() }.toSet()
    fun setClipsExcludedGenres(ids: Set<Int>) {
        prefs.putStringSet(KEY_CLIPS_EXCLUDED_GENRES, ids.map { it.toString() }.toSet())
    }
    fun clipsExcludedGenres(): Flow<Set<Int>> =
        clipsFlow(KEY_CLIPS_EXCLUDED_GENRES) { getClipsExcludedGenres() }

    private fun validateThemeMusicMode(mode: String?): String {
        return when (mode) {
            THEME_MUSIC_NO -> THEME_MUSIC_NO
            THEME_MUSIC_ENDLESS -> THEME_MUSIC_ENDLESS
            else -> THEME_MUSIC_ONCE
        }
    }

    private fun normalizeFeatureCarouselHeight(height: String?): String {
        return when (height) {
            FEATURE_CAROUSEL_HEIGHT_SMALL -> FEATURE_CAROUSEL_HEIGHT_SMALL
            FEATURE_CAROUSEL_HEIGHT_MEDIUM -> FEATURE_CAROUSEL_HEIGHT_MEDIUM
            else -> FEATURE_CAROUSEL_HEIGHT_LARGE
        }
    }
}