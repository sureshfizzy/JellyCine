package com.jellycine.app.util.image

import android.app.ActivityManager
import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.jellycine.data.datastore.DataStoreProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

object ImageLoaderConfig {

    private const val CLIENT_NAME = "JellyCine"
    private const val CLIENT_VERSION = "1.0.0"
    private const val DEVICE_NAME = "Android"

    private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
    private val SERVER_TYPE_KEY = stringPreferencesKey("server_type")

    // Generate device ID once and reuse it
    private val deviceId by lazy { UUID.randomUUID().toString() }

    private fun getOptimalMemoryPercent(context: Context): Double {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRamMB = memoryInfo.totalMem
        val isLargeHeap = activityManager.memoryClass != activityManager.largeMemoryClass

        // Reduced memory allocation to prevent lag from GC pressure
        val basePercent = when {
            totalRamMB >= 8192 -> if (isLargeHeap) 0.15 else 0.12
            totalRamMB >= 4096 -> if (isLargeHeap) 0.12 else 0.10
            totalRamMB >= 2048 -> if (isLargeHeap) 0.10 else 0.08
            else -> if (isLargeHeap) 0.08 else 0.06
        }

        val finalPercent = max(0.08, min(0.30, basePercent))
        return finalPercent
    }

    private fun getOptimalDiskCacheSize(context: Context): Long {
        val availableBytes = context.cacheDir.usableSpace
        val percent = when {
            availableBytes > 32L * 1024 * 1024 * 1024 -> 0.02
            availableBytes > 8L * 1024 * 1024 * 1024 -> 0.04
            else -> 0.05
        }

        val calculatedSize = (availableBytes * percent).toLong()
        val finalSize = max(50L * 1024 * 1024, min(500L * 1024 * 1024, calculatedSize))

        return finalSize
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
            append("Version=\"$CLIENT_VERSION\"")

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

            // Simple retry logic for failed requests
            var response = chain.proceed(newRequest)
            var retryCount = 0

            while (!response.isSuccessful && retryCount < 2) {
                response.close()
                retryCount++
                response = chain.proceed(newRequest)
            }

            response
        }

        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(8, 3, TimeUnit.MINUTES))
            .addInterceptor(authInterceptor)
            .build()
    }

    fun createOptimizedImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(getOptimalMemoryPercent(context))
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(getOptimalDiskCacheSize(context))
                    .build()
            }
            .okHttpClient(createAuthenticatedOkHttpClient(context))
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false)
            .allowHardware(true)
            .crossfade(200)
            .build()
    }
}
