package com.jellycine.app.util.image

import android.app.ActivityManager
import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.jellycine.app.BuildConfig
import com.jellycine.data.datastore.DataStoreProvider
import com.jellycine.data.preferences.NetworkPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

object ImageLoaderConfig {

    private const val CLIENT_NAME = "JellyCine"
    private const val DEVICE_NAME = "Android"

    private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
    private val SERVER_TYPE_KEY = stringPreferencesKey("server_type")
    private const val BYTES_PER_MB = 1024L * 1024L

    private val deviceId by lazy { UUID.randomUUID().toString() }
    private const val IMAGE_STORE_DIR = "media_store/image_cache"

    private fun persistentImageCacheDir(context: Context): File {
        val dir = File(context.filesDir, IMAGE_STORE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun DiskCacheSize(context: Context): Long {
        val availableBytes = context.filesDir.usableSpace
        val percent = when {
            availableBytes > 32L * 1024 * 1024 * 1024 -> 0.02
            availableBytes > 8L * 1024 * 1024 * 1024 -> 0.04
            else -> 0.05
        }

        val calculatedSize = (availableBytes * percent).toLong()
        val finalSize = max(50L * 1024 * 1024, min(500L * 1024 * 1024, calculatedSize))

        return finalSize
    }

    private fun configuredImageCacheBytes(context: Context): Long? {
        val configuredMb = NetworkPreferences(context).getImageMemoryCacheMb()
        if (configuredMb == NetworkPreferences.AUTO_IMAGE_MEMORY_CACHE_MB) {
            return null
        }
        return configuredMb * BYTES_PER_MB
    }

    private fun getOptimalMemoryPercent(context: Context): Double {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRamMB = memoryInfo.totalMem
        val isLargeHeap = activityManager.memoryClass != activityManager.largeMemoryClass

        val basePercent = when {
            totalRamMB >= 8192 -> if (isLargeHeap) 0.15 else 0.12
            totalRamMB >= 4096 -> if (isLargeHeap) 0.12 else 0.10
            totalRamMB >= 2048 -> if (isLargeHeap) 0.10 else 0.08
            else -> if (isLargeHeap) 0.08 else 0.06
        }

        return max(0.08, min(0.30, basePercent))
    }

    private fun ImageMemoryCacheBytes(context: Context): Int? {
        val configuredMb = NetworkPreferences(context).getImageMemoryCacheMb()
        if (configuredMb == NetworkPreferences.AUTO_IMAGE_MEMORY_CACHE_MB) {
            return null
        }
        return (configuredMb * BYTES_PER_MB).toInt()
    }

    private fun createAuthenticatedOkHttpClient(context: Context): OkHttpClient {
        val dataStore = DataStoreProvider.getDataStore(context)
        val preferences = runBlocking {
            runCatching { dataStore.data.first() }.getOrNull()
        }
        val accessToken = preferences?.get(ACCESS_TOKEN_KEY)
        val serverType = preferences?.get(SERVER_TYPE_KEY)
        val headerPrefix = if (serverType.equals("EMBY", ignoreCase = true)) "Emby" else "MediaBrowser"
        val authHeader = buildString {
            append("$headerPrefix ")
            append("Client=\"$CLIENT_NAME\", ")
            append("Device=\"$DEVICE_NAME\", ")
            append("DeviceId=\"$deviceId\", ")
            append("Version=\"${BuildConfig.VERSION_NAME}\"")

            if (!accessToken.isNullOrEmpty()) {
                append(", Token=\"$accessToken\"")
            }
        }

        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
                .addHeader("Authorization", authHeader)
                .addHeader("X-Emby-Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .build()

            var response = chain.proceed(newRequest)
            var retryCount = 0

            while (!response.isSuccessful && response.code >= 500 && retryCount < 2) {
                response.close()
                retryCount++
                response = chain.proceed(newRequest)
            }

            response
        }

        val dispatcher = Dispatcher().apply {
            maxRequests = 128
            maxRequestsPerHost = 32
        }

        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(24, 5, TimeUnit.MINUTES))
            .addInterceptor(authInterceptor)
            .build()
    }

    fun createOptimizedImageLoader(context: Context): ImageLoader {
        val networkPreferences = NetworkPreferences(context)
        val imageCachingEnabled = networkPreferences.isImageCachingEnabled()
        val configuredMemoryCacheBytes = ImageMemoryCacheBytes(context)
        val builder = ImageLoader.Builder(context)
            .memoryCache {
                val memoryCacheBuilder = MemoryCache.Builder(context)
                    .strongReferencesEnabled(true)
                if (configuredMemoryCacheBytes != null) {
                    memoryCacheBuilder.maxSizeBytes(configuredMemoryCacheBytes)
                } else {
                    memoryCacheBuilder.maxSizePercent(getOptimalMemoryPercent(context))
                }
                memoryCacheBuilder.build()
            }
            .okHttpClient(createAuthenticatedOkHttpClient(context))
            .respectCacheHeaders(false)
            .allowHardware(true)
            .crossfade(false)

        builder.diskCache {
            DiskCache.Builder()
                .directory(persistentImageCacheDir(context))
                .maxSizeBytes(configuredImageCacheBytes(context) ?: DiskCacheSize(context))
                .build()
        }

        if (imageCachingEnabled) {
            builder.memoryCachePolicy(CachePolicy.ENABLED)
            builder.diskCachePolicy(CachePolicy.ENABLED)
            builder.networkCachePolicy(CachePolicy.ENABLED)
        } else {
            builder.memoryCachePolicy(CachePolicy.DISABLED)
            builder.diskCachePolicy(CachePolicy.DISABLED)
            builder.networkCachePolicy(CachePolicy.DISABLED)
        }

        return builder.build()
    }

}
