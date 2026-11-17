package com.jellycine.data.preferences

import android.content.Context
import android.content.SharedPreferences

class NetworkPreferences(context: Context) : NetworkSettingsStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getTimeoutConfig(): NetworkTimeoutConfig {
        val requestMs = sanitize(prefs.getInt(KEY_REQUEST_TIMEOUT, DEFAULT_REQUEST_TIMEOUT_MS))
        val connectionMs = sanitize(
            prefs.getInt(KEY_CONNECTION_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT_MS)
        )
        val socketMs = sanitize(prefs.getInt(KEY_SOCKET_TIMEOUT, DEFAULT_SOCKET_TIMEOUT_MS))

        return NetworkTimeoutConfig(
            requestTimeoutMs = requestMs,
            connectionTimeoutMs = connectionMs,
            socketTimeoutMs = socketMs
        )
    }

    override fun setRequestTimeoutMs(milliseconds: Int) {
        prefs.edit().putInt(KEY_REQUEST_TIMEOUT, sanitize(milliseconds)).apply()
    }

    override fun setConnectionTimeoutMs(milliseconds: Int) {
        prefs.edit().putInt(KEY_CONNECTION_TIMEOUT, sanitize(milliseconds)).apply()
    }

    override fun setSocketTimeoutMs(milliseconds: Int) {
        prefs.edit().putInt(KEY_SOCKET_TIMEOUT, sanitize(milliseconds)).apply()
    }

    override fun getImageMemoryCacheMb(): Int {
        return ImageCacheMemoryMb(
            prefs.getInt(KEY_IMAGE_MEMORY_CACHE_MB, DEFAULT_IMAGE_MEMORY_CACHE_MB)
        )
    }

    override fun setImageMemoryCacheMb(megabytes: Int) {
        prefs.edit().putInt(KEY_IMAGE_MEMORY_CACHE_MB, ImageCacheMemoryMb(megabytes)).apply()
    }

    override fun isImageCachingEnabled(): Boolean {
        return prefs.getBoolean(KEY_IMAGE_CACHING_ENABLED, DEFAULT_IMAGE_CACHING_ENABLED)
    }

    override fun setImageCachingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IMAGE_CACHING_ENABLED, enabled).apply()
    }

    private fun sanitize(milliseconds: Int): Int {
        return milliseconds.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
    }

    private fun ImageCacheMemoryMb(megabytes: Int): Int {
        if (megabytes == AUTO_IMAGE_MEMORY_CACHE_MB) {
            return AUTO_IMAGE_MEMORY_CACHE_MB
        }
        return megabytes.coerceIn(MIN_IMAGE_MEMORY_CACHE_MB, MAX_IMAGE_MEMORY_CACHE_MB)
    }

    companion object {
        const val MIN_TIMEOUT_MS = NetworkSettingsStore.MIN_TIMEOUT_MS
        const val MAX_TIMEOUT_MS = NetworkSettingsStore.MAX_TIMEOUT_MS

        const val DEFAULT_REQUEST_TIMEOUT_MS = NetworkSettingsStore.DEFAULT_REQUEST_TIMEOUT_MS
        const val DEFAULT_CONNECTION_TIMEOUT_MS = NetworkSettingsStore.DEFAULT_CONNECTION_TIMEOUT_MS
        const val DEFAULT_SOCKET_TIMEOUT_MS = NetworkSettingsStore.DEFAULT_SOCKET_TIMEOUT_MS
        const val MIN_IMAGE_MEMORY_CACHE_MB = NetworkSettingsStore.MIN_IMAGE_MEMORY_CACHE_MB
        const val MAX_IMAGE_MEMORY_CACHE_MB = NetworkSettingsStore.MAX_IMAGE_MEMORY_CACHE_MB
        const val AUTO_IMAGE_MEMORY_CACHE_MB = NetworkSettingsStore.AUTO_IMAGE_MEMORY_CACHE_MB
        const val DEFAULT_IMAGE_MEMORY_CACHE_MB = NetworkSettingsStore.DEFAULT_IMAGE_MEMORY_CACHE_MB
        const val DEFAULT_IMAGE_CACHING_ENABLED = NetworkSettingsStore.DEFAULT_IMAGE_CACHING_ENABLED

        private const val PREFS_NAME = "jellycine_network_prefs"
        private const val KEY_REQUEST_TIMEOUT = "request_timeout_ms"
        private const val KEY_CONNECTION_TIMEOUT = "connection_timeout_ms"
        private const val KEY_SOCKET_TIMEOUT = "socket_timeout_ms"
        private const val KEY_IMAGE_MEMORY_CACHE_MB = "image_memory_cache_mb"
        private const val KEY_IMAGE_CACHING_ENABLED = "image_caching_enabled"
    }
}
