package com.jellycine.app.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class Preferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "jellycine_download_prefs"
        private const val KEY_WIFI_ONLY_DOWNLOADS = "wifi_only_downloads"
        private const val KEY_FEATURE_CAROUSEL_ENABLED = "feature_carousel_enabled"
        private const val KEY_POSTER_ENHANCERS_ENABLED = "poster_enhancers_enabled"
        private const val KEY_CONTINUE_WATCHING_ENABLED = "continue_watching_enabled"
        private const val KEY_NEXT_UP_ENABLED = "next_up_enabled"
    }

    fun isWifiOnlyDownloadsEnabled(): Boolean {
        return prefs.getBoolean(KEY_WIFI_ONLY_DOWNLOADS, false)
    }

    fun setWifiOnlyDownloadsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY_DOWNLOADS, enabled).apply()
    }

    fun isFeatureCarouselEnabled(): Boolean {
        return prefs.getBoolean(KEY_FEATURE_CAROUSEL_ENABLED, true)
    }

    fun setFeatureCarouselEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FEATURE_CAROUSEL_ENABLED, enabled).apply()
    }

    fun FeatureCarouselEnabled(): Flow<Boolean> = callbackFlow {
        trySend(isFeatureCarouselEnabled())

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_FEATURE_CAROUSEL_ENABLED) {
                trySend(isFeatureCarouselEnabled())
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun isPosterEnhancersEnabled(): Boolean {
        return prefs.getBoolean(KEY_POSTER_ENHANCERS_ENABLED, false)
    }

    fun setPosterEnhancersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_POSTER_ENHANCERS_ENABLED, enabled).apply()
    }

    fun PosterEnhancersEnabled(): Flow<Boolean> = callbackFlow {
        trySend(isPosterEnhancersEnabled())

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_POSTER_ENHANCERS_ENABLED) {
                trySend(isPosterEnhancersEnabled())
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun isContinueWatchingEnabled(): Boolean {
        return prefs.getBoolean(KEY_CONTINUE_WATCHING_ENABLED, true)
    }

    fun setContinueWatchingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONTINUE_WATCHING_ENABLED, enabled).apply()
    }

    fun ContinueWatchingEnabled(): Flow<Boolean> = callbackFlow {
        trySend(isContinueWatchingEnabled())

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_CONTINUE_WATCHING_ENABLED) {
                trySend(isContinueWatchingEnabled())
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun isNextUpEnabled(): Boolean {
        return prefs.getBoolean(KEY_NEXT_UP_ENABLED, false)
    }

    fun setNextUpEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NEXT_UP_ENABLED, enabled).apply()
    }

    fun NextUpEnabled(): Flow<Boolean> = callbackFlow {
        trySend(isNextUpEnabled())

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_NEXT_UP_ENABLED) {
                trySend(isNextUpEnabled())
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}

