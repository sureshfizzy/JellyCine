package com.jellycine.data.preferences

import platform.Foundation.NSUserDefaults

class NetworkPreferences : NetworkSettingsStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getTimeoutConfig(): NetworkTimeoutConfig {
        val requestMs = sanitize(defaults.integerForKey(KEY_REQUEST_TIMEOUT).toInt().takeIf { it != 0 } ?: DEFAULT_REQUEST_TIMEOUT_MS)
        val connectionMs = sanitize(defaults.integerForKey(KEY_CONNECTION_TIMEOUT).toInt().takeIf { it != 0 } ?: DEFAULT_CONNECTION_TIMEOUT_MS)
        val socketMs = sanitize(defaults.integerForKey(KEY_SOCKET_TIMEOUT).toInt().takeIf { it != 0 } ?: DEFAULT_SOCKET_TIMEOUT_MS)

        return NetworkTimeoutConfig(
            requestTimeoutMs = requestMs,
            connectionTimeoutMs = connectionMs,
            socketTimeoutMs = socketMs
        )
    }

    override fun setRequestTimeoutMs(milliseconds: Int) {
        defaults.setInteger(sanitize(milliseconds).toLong(), KEY_REQUEST_TIMEOUT)
        defaults.synchronize()
    }

    override fun setConnectionTimeoutMs(milliseconds: Int) {
        defaults.setInteger(sanitize(milliseconds).toLong(), KEY_CONNECTION_TIMEOUT)
        defaults.synchronize()
    }

    override fun setSocketTimeoutMs(milliseconds: Int) {
        defaults.setInteger(sanitize(milliseconds).toLong(), KEY_SOCKET_TIMEOUT)
        defaults.synchronize()
    }

    override fun getImageMemoryCacheMb(): Int {
        val value = defaults.integerForKey(KEY_IMAGE_MEMORY_CACHE_MB).toInt().takeIf { it != 0 } ?: DEFAULT_IMAGE_MEMORY_CACHE_MB
        return ImageCacheMemoryMb(value)
    }

    override fun setImageMemoryCacheMb(megabytes: Int) {
        defaults.setInteger(ImageCacheMemoryMb(megabytes).toLong(), KEY_IMAGE_MEMORY_CACHE_MB)
        defaults.synchronize()
    }

    override fun isImageCachingEnabled(): Boolean {
        // NSUserDefaults returns false for non-existent keys
        if (defaults.objectForKey(KEY_IMAGE_CACHING_ENABLED) == null) {
            return DEFAULT_IMAGE_CACHING_ENABLED
        }
        return defaults.boolForKey(KEY_IMAGE_CACHING_ENABLED)
    }

    override fun setImageCachingEnabled(enabled: Boolean) {
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

        private const val KEY_REQUEST_TIMEOUT = "request_timeout_ms"
        private const val KEY_CONNECTION_TIMEOUT = "connection_timeout_ms"
        private const val KEY_SOCKET_TIMEOUT = "socket_timeout_ms"
        private const val KEY_IMAGE_MEMORY_CACHE_MB = "image_memory_cache_mb"
        private const val KEY_IMAGE_CACHING_ENABLED = "image_caching_enabled"
    }
}
