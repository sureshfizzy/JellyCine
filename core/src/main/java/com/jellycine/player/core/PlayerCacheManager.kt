package com.jellycine.player.core

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.jellycine.player.preferences.PlayerPreferences
import java.io.File

@UnstableApi
internal object PlayerCacheManager {
    private const val CACHE_DIRECTORY_NAME = "player_media_cache"
    private const val PREFETCH_BUFFER_BYTES = 64 * 1024

    @Volatile
    private var simpleCache: SimpleCache? = null
    private var cacheSizeBytes: Long = -1L
    private var databaseProvider: StandaloneDatabaseProvider? = null

    @Synchronized
    fun createDataSourceFactory(
        context: Context,
        cacheSizeMb: Int,
        defaultRequestHeaders: Map<String, String> = emptyMap()
    ): DataSource.Factory {
        val appContext = context.applicationContext
        val httpDataSource = DefaultHttpDataSource.Factory()
        if (defaultRequestHeaders.isNotEmpty()) {
            httpDataSource.setDefaultRequestProperties(defaultRequestHeaders)
        }
        val upstream = DefaultDataSource.Factory(appContext, httpDataSource)
        return CacheDataSource.Factory()
            .setCache(getOrCreateCache(appContext, cacheSizeMb))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun prefetchToCache(
        context: Context,
        uri: Uri,
        cacheKey: String?,
        maxBytes: Long,
        defaultRequestHeaders: Map<String, String> = emptyMap()
    ) {
        if (maxBytes <= 0L) return

        val appContext = context.applicationContext
        val cacheSizeMb = PlayerPreferences(appContext).getPlayerCacheSizeMb()
        val dataSource = createDataSourceFactory(
            context = appContext,
            cacheSizeMb = cacheSizeMb,
            defaultRequestHeaders = defaultRequestHeaders
        ).createDataSource()

        val dataSpecBuilder = DataSpec.Builder()
            .setUri(uri)
            .setLength(maxBytes)
        if (!cacheKey.isNullOrBlank()) {
            dataSpecBuilder.setKey(cacheKey)
        }

        val dataSpec = dataSpecBuilder.build()
        val buffer = ByteArray(PREFETCH_BUFFER_BYTES)
        var totalBytesRead = 0L

        try {
            dataSource.open(dataSpec)

            while (totalBytesRead < maxBytes) {
                val nextReadLength = minOf(
                    buffer.size.toLong(),
                    maxBytes - totalBytesRead
                ).toInt()
                val bytesRead = dataSource.read(buffer, 0, nextReadLength)
                if (bytesRead == C.RESULT_END_OF_INPUT) break
                totalBytesRead += bytesRead
            }
        } finally {
            runCatching { dataSource.close() }
        }
    }

    @Synchronized
    private fun getOrCreateCache(
        context: Context,
        cacheSizeMb: Int
    ): SimpleCache {
        val clampedCacheSizeMb = cacheSizeMb.coerceIn(
            PlayerPreferences.MIN_PLAYER_CACHE_SIZE_MB,
            PlayerPreferences.MAX_PLAYER_CACHE_SIZE_MB
        )
        val desiredCacheSizeBytes = clampedCacheSizeMb.toLong() * 1024L * 1024L
        val currentCache = simpleCache
        if (currentCache != null && cacheSizeBytes == desiredCacheSizeBytes) {
            return currentCache
        }

        currentCache?.release()

        val provider = databaseProvider ?: StandaloneDatabaseProvider(context).also {
            databaseProvider = it
        }
        val cacheDirectory = File(context.cacheDir, CACHE_DIRECTORY_NAME)
        val cache = SimpleCache(
            cacheDirectory,
            LeastRecentlyUsedCacheEvictor(desiredCacheSizeBytes),
            provider
        )

        simpleCache = cache
        cacheSizeBytes = desiredCacheSizeBytes
        return cache
    }
}
