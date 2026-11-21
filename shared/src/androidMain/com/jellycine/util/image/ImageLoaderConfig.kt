package com.jellycine.util.image

import android.app.ActivityManager
import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import coil3.*
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.jellycine.data.DataModuleConfig
import com.jellycine.data.datastore.DataStoreProvider
import com.jellycine.data.model.AuthHeaderDto
import com.jellycine.data.network.ServerType
import com.jellycine.data.preferences.NetworkPreferences
import com.jellycine.data.security.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okio.Path.Companion.toOkioPath
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.*

object ImageLoaderConfig {
    private val SERVER_URL_KEY = stringPreferencesKey("server_url")
    private val USER_ID_KEY = stringPreferencesKey("user_id")
    private val SERVER_TYPE_KEY = stringPreferencesKey("server_type")

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

    private fun getOptimalMemoryPercent(context: Context): Double {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRamMB = memoryInfo.totalMem / (1024L * 1024L)
        val isLargeHeap = activityManager.memoryClass != activityManager.largeMemoryClass

        val basePercent = when {
            totalRamMB >= 8192 -> if (isLargeHeap) 0.15 else 0.12
            totalRamMB >= 4096 -> if (isLargeHeap) 0.12 else 0.10
            totalRamMB >= 2048 -> if (isLargeHeap) 0.10 else 0.08
            else -> if (isLargeHeap) 0.08 else 0.06
        }

        return max(0.08, min(0.30, basePercent))
    }

    private fun createAuthenticatedOkHttpClient(context: Context): OkHttpClient {
        val dataStore = DataStoreProvider.getDataStore(context)
        val secureSessionStore = SecureSessionStore(context)
        val authHeaderLock = Any()
        var cachedAuthHeader: String? = null
        var cachedAuthHeaderAt = 0L
        val authHeaderTtlMs = 1200L

        fun buildAuthHeader(): String {
            val now = System.currentTimeMillis()
            synchronized(authHeaderLock) {
                val existingHeader = cachedAuthHeader
                if (existingHeader != null && (now - cachedAuthHeaderAt) < authHeaderTtlMs) {
                    return existingHeader
                }

                val preferences = runBlocking {
                    runCatching { dataStore.data.first() }.getOrNull()
                }
                val serverUrl = preferences?.get(SERVER_URL_KEY)
                val userId = preferences?.get(USER_ID_KEY)
                val accessToken = if (!serverUrl.isNullOrBlank() && !userId.isNullOrBlank()) {
                    secureSessionStore.getToken(AuthSessionIds.buildServerId(serverUrl, userId))
                } else {
                    null
                } ?: preferences?.get(LEGACY_ACCESS_TOKEN_KEY)
                val serverType = preferences?.get(SERVER_TYPE_KEY)?.let {
                    runCatching { ServerType.valueOf(it) }.getOrNull()
                }
                val header = AuthHeaderDto.fromServerType(
                    serverType = serverType,
                    deviceId = deviceId,
                    version = DataModuleConfig.CLIENT_VERSION,
                    accessToken = accessToken
                ).asHeaderValue()
                cachedAuthHeader = header
                cachedAuthHeaderAt = now
                return header
            }
        }

        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val authHeader = buildAuthHeader()
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
        val componentRegistry = ComponentRegistry.Builder()
            .add(
                OkHttpNetworkFetcherFactory(
                    callFactory = { createAuthenticatedOkHttpClient(context) }
                )
            )
            .build()

        return ImageLoader.Builder(context)
            .components(componentRegistry)
            .applyImageCacheSettings(
                context = context,
                settings = NetworkPreferences(context),
                diskDirectory = persistentImageCacheDir(context).toOkioPath(),
                autoMemoryPercent = getOptimalMemoryPercent(context),
                autoDiskBytes = DiskCacheSize(context)
            )
            .build()
    }

}
