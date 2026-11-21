package com.jellycine.util.image

import coil3.*
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import com.jellycine.data.preferences.NetworkSettingsStore
import okio.Path

private const val BYTES_PER_MB = 1024L * 1024L

/**
 * Applies the user's cache settings (Settings > Cache) to a Coil loader so every platform behaves the same.
 * A configured size applies to both caches.
 */
fun ImageLoader.Builder.applyImageCacheSettings(
    context: PlatformContext,
    settings: NetworkSettingsStore,
    diskDirectory: Path,
    autoMemoryPercent: Double,
    autoDiskBytes: Long
): ImageLoader.Builder {
    val configuredMb = settings.getImageMemoryCacheMb()
    val configuredBytes = configuredMb
        .takeIf { it != NetworkSettingsStore.AUTO_IMAGE_MEMORY_CACHE_MB }
        ?.let { it * BYTES_PER_MB }

    memoryCache {
        MemoryCache.Builder().apply {
            if (configuredBytes != null) maxSizeBytes(configuredBytes) else maxSizePercent(context, autoMemoryPercent)
        }.build()
    }
    diskCache {
        DiskCache.Builder()
            .directory(diskDirectory)
            .maxSizeBytes(configuredBytes ?: autoDiskBytes)
            .build()
    }

    val policy = if (settings.isImageCachingEnabled()) CachePolicy.ENABLED else CachePolicy.DISABLED
    memoryCachePolicy(policy)
    diskCachePolicy(policy)
    networkCachePolicy(policy)
    return this
}