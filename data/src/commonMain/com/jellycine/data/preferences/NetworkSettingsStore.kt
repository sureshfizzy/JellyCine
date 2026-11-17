package com.jellycine.data.preferences

interface NetworkSettingsStore {
    fun getTimeoutConfig(): NetworkTimeoutConfig

    fun setRequestTimeoutMs(milliseconds: Int)

    fun setConnectionTimeoutMs(milliseconds: Int)

    fun setSocketTimeoutMs(milliseconds: Int)

    fun getImageMemoryCacheMb(): Int

    fun setImageMemoryCacheMb(megabytes: Int)

    fun isImageCachingEnabled(): Boolean

    fun setImageCachingEnabled(enabled: Boolean)

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
    }
}