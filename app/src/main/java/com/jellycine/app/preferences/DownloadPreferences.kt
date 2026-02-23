package com.jellycine.app.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class DownloadPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "jellycine_download_prefs"
        private const val KEY_WIFI_ONLY_DOWNLOADS = "wifi_only_downloads"
        private const val KEY_FEATURE_CAROUSEL_ENABLED = "feature_carousel_enabled"
    }

    fun isWifiOnlyDownloadsEnabled(): Boolean {
        return prefs.getBoolean(KEY_WIFI_ONLY_DOWNLOADS, true)
    }

    fun setWifiOnlyDownloadsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY_DOWNLOADS, enabled).apply()
    }

    fun isFeatureCarouselEnabled(): Boolean {
        return prefs.getBoolean(KEY_FEATURE_CAROUSEL_ENABLED, false)
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
}
