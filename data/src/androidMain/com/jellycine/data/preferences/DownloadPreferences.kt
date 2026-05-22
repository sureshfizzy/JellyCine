package com.jellycine.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.jellycine.data.model.DownloadStorageBehavior

class DownloadPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getStorageBehavior(): DownloadStorageBehavior {
        val raw = prefs.getString(KEY_STORAGE_BEHAVIOR, null)
            ?: DEFAULT_STORAGE_BEHAVIOR.name
        return runCatching {
            DownloadStorageBehavior.valueOf(raw)
        }.getOrDefault(DEFAULT_STORAGE_BEHAVIOR)
    }

    fun setStorageBehavior(behavior: DownloadStorageBehavior) {
        prefs.edit().putString(KEY_STORAGE_BEHAVIOR, behavior.name).apply()
    }

    fun getDeviceDownloadsTreeUri(): String? {
        return prefs.getString(KEY_DEVICE_DOWNLOADS_TREE_URI, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun setDeviceDownloadsTreeUri(uri: String?) {
        prefs.edit().apply {
            if (uri.isNullOrBlank()) {
                remove(KEY_DEVICE_DOWNLOADS_TREE_URI)
            } else {
                putString(KEY_DEVICE_DOWNLOADS_TREE_URI, uri)
            }
        }.apply()
    }

    fun getMaxConcurrentDownloads(): Int {
        return prefs.getInt(KEY_MAX_CONCURRENT_DOWNLOADS, DEFAULT_MAX_CONCURRENT_DOWNLOADS)
            .coerceIn(MIN_CONCURRENT_DOWNLOADS, MAX_CONCURRENT_DOWNLOADS)
    }

    fun setMaxConcurrentDownloads(count: Int) {
        prefs.edit()
            .putInt(
                KEY_MAX_CONCURRENT_DOWNLOADS,
                count.coerceIn(MIN_CONCURRENT_DOWNLOADS, MAX_CONCURRENT_DOWNLOADS)
            )
            .apply()
    }

    fun getCancelDeleteWaitMs(): Long = DEFAULT_CANCEL_DELETE_WAIT_MS

    fun getMetadataPersistIntervalMs(): Long = DEFAULT_METADATA_PERSIST_INTERVAL_MS

    fun getTrackedRefreshIntervalMs(): Long = DEFAULT_TRACKED_REFRESH_INTERVAL_MS

    companion object {
        val DEFAULT_STORAGE_BEHAVIOR = DownloadStorageBehavior.APP_STORAGE

        const val MIN_CONCURRENT_DOWNLOADS = 1
        const val MAX_CONCURRENT_DOWNLOADS = 10
        const val DEFAULT_MAX_CONCURRENT_DOWNLOADS = 1

        const val DEFAULT_CANCEL_DELETE_WAIT_MS = 1500L

        const val DEFAULT_METADATA_PERSIST_INTERVAL_MS = 1500L

        const val DEFAULT_TRACKED_REFRESH_INTERVAL_MS = 1000L

        private const val PREFS_NAME = "jellycine_download_prefs"
        private const val KEY_STORAGE_BEHAVIOR = "storage_behavior"
        private const val KEY_DEVICE_DOWNLOADS_TREE_URI = "device_downloads_tree_uri"
        private const val KEY_MAX_CONCURRENT_DOWNLOADS = "max_concurrent_downloads"
    }
}