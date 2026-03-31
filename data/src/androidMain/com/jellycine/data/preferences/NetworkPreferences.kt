package com.jellycine.data.preferences

import android.content.Context
import android.content.SharedPreferences

data class NetworkTimeoutConfig(
    val requestTimeoutMs: Int,
    val connectionTimeoutMs: Int,
    val socketTimeoutMs: Int
)

class NetworkPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTimeoutConfig(): NetworkTimeoutConfig {
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

    fun setRequestTimeoutMs(milliseconds: Int) {
        prefs.edit().putInt(KEY_REQUEST_TIMEOUT, sanitize(milliseconds)).apply()
    }

    fun setConnectionTimeoutMs(milliseconds: Int) {
        prefs.edit().putInt(KEY_CONNECTION_TIMEOUT, sanitize(milliseconds)).apply()
    }

    fun setSocketTimeoutMs(milliseconds: Int) {
        prefs.edit().putInt(KEY_SOCKET_TIMEOUT, sanitize(milliseconds)).apply()
    }

    fun getImageMemoryCacheMb(): Int {
        return ImageCacheMemoryMb(
            prefs.getInt(KEY_IMAGE_MEMORY_CACHE_MB, DEFAULT_IMAGE_MEMORY_CACHE_MB)
        )
    }

    fun setImageMemoryCacheMb(megabytes: Int) {
        prefs.edit().putInt(KEY_IMAGE_MEMORY_CACHE_MB, ImageCacheMemoryMb(megabytes)).apply()
    }

    fun isImageCachingEnabled(): Boolean {
        return prefs.getBoolean(KEY_IMAGE_CACHING_ENABLED, DEFAULT_IMAGE_CACHING_ENABLED)
    }

    fun setImageCachingEnabled(enabled: Boolean) {
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
        const val MIN_TIMEOUT_MS = 1000
        const val MAX_TIMEOUT_MS = 300000

        const val DEFAULT_REQUEST_TIMEOUT_MS = 30000
        const val DEFAULT_CONNECTION_TIMEOUT_MS = 6000
        const val DEFAULT_SOCKET_TIMEOUT_MS = 10000
        const val MIN_IMAGE_MEMORY_CACHE_MB = 32
        const val MAX_IMAGE_MEMORY_CACHE_MB = 512
        const val AUTO_IMAGE_MEMORY_CACHE_MB = 0
        const val DEFAULT_IMAGE_MEMORY_CACHE_MB = 120
        const val DEFAULT_IMAGE_CACHING_ENABLED = true

        private const val PREFS_NAME = "jellycine_network_prefs"
        private const val KEY_REQUEST_TIMEOUT = "request_timeout_ms"
        private const val KEY_CONNECTION_TIMEOUT = "connection_timeout_ms"
        private const val KEY_SOCKET_TIMEOUT = "socket_timeout_ms"
        private const val KEY_IMAGE_MEMORY_CACHE_MB = "image_memory_cache_mb"
        private const val KEY_IMAGE_CACHING_ENABLED = "image_caching_enabled"
    }
}
