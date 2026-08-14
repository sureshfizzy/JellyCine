package com.jellycine.data.preferences

import platform.Foundation.NSUserDefaults

data class NetworkTimeoutConfig(
    val requestTimeoutMs: Int,
    val connectionTimeoutMs: Int,
    val socketTimeoutMs: Int
)

class NetworkPreferences {
    private val defaults = NSUserDefaults.standardUserDefaults

    fun getTimeoutConfig(): NetworkTimeoutConfig {
        val requestMs = sanitize(defaults.integerForKey(KEY_REQUEST_TIMEOUT).toInt().takeIf { it != 0 } ?: DEFAULT_REQUEST_TIMEOUT_MS)
        val connectionMs = sanitize(defaults.integerForKey(KEY_CONNECTION_TIMEOUT).toInt().takeIf { it != 0 } ?: DEFAULT_CONNECTION_TIMEOUT_MS)
        val socketMs = sanitize(defaults.integerForKey(KEY_SOCKET_TIMEOUT).toInt().takeIf { it != 0 } ?: DEFAULT_SOCKET_TIMEOUT_MS)

        return NetworkTimeoutConfig(
            requestTimeoutMs = requestMs,
            connectionTimeoutMs = connectionMs,
            socketTimeoutMs = socketMs
        )
    }

    fun setRequestTimeoutMs(milliseconds: Int) {
        defaults.setInteger(sanitize(milliseconds).toLong(), KEY_REQUEST_TIMEOUT)
        defaults.synchronize()
    }

    fun setConnectionTimeoutMs(milliseconds: Int) {
        defaults.setInteger(sanitize(milliseconds).toLong(), KEY_CONNECTION_TIMEOUT)
        defaults.synchronize()
    }

    fun setSocketTimeoutMs(milliseconds: Int) {
        defaults.setInteger(sanitize(milliseconds).toLong(), KEY_SOCKET_TIMEOUT)
        defaults.synchronize()
    }

    fun getImageMemoryCacheMb(): Int {
        val value = defaults.integerForKey(KEY_IMAGE_MEMORY_CACHE_MB).toInt().takeIf { it != 0 } ?: DEFAULT_IMAGE_MEMORY_CACHE_MB
        return ImageCacheMemoryMb(value)
    }

    fun setImageMemoryCacheMb(megabytes: Int) {
        defaults.setInteger(ImageCacheMemoryMb(megabytes).toLong(), KEY_IMAGE_MEMORY_CACHE_MB)
        defaults.synchronize()
    }

    fun isImageCachingEnabled(): Boolean {
        // NSUserDefaults returns false for non-existent keys
        if (defaults.objectForKey(KEY_IMAGE_CACHING_ENABLED) == null) {
            return DEFAULT_IMAGE_CACHING_ENABLED
        }
        return defaults.boolForKey(KEY_IMAGE_CACHING_ENABLED)
    }

    fun setImageCachingEnabled(enabled: Boolean) {
        defaults.setBool(enabled, KEY_IMAGE_CACHING_ENABLED)
        defaults.synchronize()
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

        private const val KEY_REQUEST_TIMEOUT = "request_timeout_ms"
        private const val KEY_CONNECTION_TIMEOUT = "connection_timeout_ms"
        private const val KEY_SOCKET_TIMEOUT = "socket_timeout_ms"
        private const val KEY_IMAGE_MEMORY_CACHE_MB = "image_memory_cache_mb"
        private const val KEY_IMAGE_CACHING_ENABLED = "image_caching_enabled"
    }
}
