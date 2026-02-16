package com.jellycine.app.preferences

import android.content.Context
import android.content.SharedPreferences

class DownloadPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "jellycine_download_prefs"
        private const val KEY_WIFI_ONLY_DOWNLOADS = "wifi_only_downloads"
    }

    fun isWifiOnlyDownloadsEnabled(): Boolean {
        return prefs.getBoolean(KEY_WIFI_ONLY_DOWNLOADS, true)
    }

    fun setWifiOnlyDownloadsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY_DOWNLOADS, enabled).apply()
    }
}
