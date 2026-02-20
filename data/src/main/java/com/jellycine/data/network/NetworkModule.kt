package com.jellycine.data.network

import android.util.Log
import com.jellycine.data.api.MediaServerApi
import com.jellycine.data.BuildConfig
import com.jellycine.data.model.ServerInfo
import com.jellycine.data.preferences.NetworkTimeoutConfig
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object NetworkModule {

    private const val CLIENT_NAME = "JellyCine"
    private const val DEVICE_NAME = "Android"
    private const val NETWORK_LOG_TAG = "JellyCineNetwork"
    private val deviceId by lazy { "jellycine-android-${UUID.randomUUID()}" }
    private val apiCache = ConcurrentHashMap<String, MediaServerApi>()

    enum class ServerType {
        JELLYFIN,
        EMBY
    }

    data class ResolvedServerEndpoint(
        val baseUrl: String,
        val serverType: ServerType,
        val serverInfo: ServerInfo
    )

    fun createMediaServerApi(
        baseUrl: String,
        accessToken: String? = null,
        serverType: ServerType? = null,
        storageDir: File? = null,
        timeoutConfig: NetworkTimeoutConfig? = null
    ): MediaServerApi {
        val normalizedBaseUrl = ensureTrailingSlash(baseUrl)
        val resolvedType = serverType ?: inferServerType(normalizedBaseUrl)
        val resolvedTimeoutConfig = timeoutConfig ?: defaultTimeoutConfig()
        val cacheKey = buildString {
            append(normalizedBaseUrl.trimEnd('/'))
            append("|")
            append(accessToken.orEmpty())
            append("|")
            append(resolvedType.name)
            append("|")
            append(resolvedTimeoutConfig.requestTimeoutMs)
            append("|")
            append(resolvedTimeoutConfig.connectionTimeoutMs)
            append("|")
            append(resolvedTimeoutConfig.socketTimeoutMs)
        }
        apiCache[cacheKey]?.let { return it }

        val okHttpClient = createOkHttpClient(
            accessToken = accessToken,
            serverType = resolvedType,
            storageDir = storageDir,
            timeoutConfig = resolvedTimeoutConfig
        )
        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(MediaServerApi::class.java).also { apiCache[cacheKey] = it }
    }

    fun createJellyfinApi(
        baseUrl: String,
        accessToken: String? = null,
        storageDir: File? = null,
        timeoutConfig: NetworkTimeoutConfig? = null
    ): MediaServerApi {
        return createMediaServerApi(
            baseUrl = baseUrl,
            accessToken = accessToken,
            serverType = ServerType.JELLYFIN,
            storageDir = storageDir,
            timeoutConfig = timeoutConfig
        )
    }

    suspend fun resolveServerEndpoint(
        serverUrl: String,
        storageDir: File? = null,
        timeoutConfig: NetworkTimeoutConfig? = null
    ): Result<ResolvedServerEndpoint> {
        val candidates = buildBaseUrlCandidates(serverUrl)
        var lastError: Exception? = null

        for (candidate in candidates) {
            try {
                val api = createMediaServerApi(
                    baseUrl = candidate,
                    storageDir = storageDir,
                    timeoutConfig = timeoutConfig
                )
                val response = api.getPublicSystemInfo()
                if (response.isSuccessful && response.body() != null) {
                    val serverInfo = response.body()!!
                    val detectedType = detectServerType(candidate, serverInfo)
                    return Result.success(
                        ResolvedServerEndpoint(
                            baseUrl = ensureTrailingSlash(candidate),
                            serverType = detectedType,
                            serverInfo = serverInfo
                        )
                    )
                }

                lastError = Exception("Server connection failed with HTTP ${response.code()}")
            } catch (e: Exception) {
                lastError = e
            }
        }

        return Result.failure(lastError ?: Exception("Unable to resolve server endpoint"))
    }

    private fun createOkHttpClient(
        accessToken: String? = null,
        serverType: ServerType,
        storageDir: File? = null,
        timeoutConfig: NetworkTimeoutConfig
    ): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 128
            maxRequestsPerHost = 32
        }

        val builder = OkHttpClient.Builder()
            .callTimeout(timeoutConfig.requestTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .connectTimeout(timeoutConfig.connectionTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeoutConfig.socketTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutConfig.socketTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)

        if (storageDir != null) {
            val networkStore = File(storageDir, "media_store/network_http_cache")
            if (!networkStore.exists()) {
                networkStore.mkdirs()
            }
            builder.cache(Cache(networkStore, 64L * 1024L * 1024L))
        }

        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val authHeader = buildAuthHeader(accessToken, deviceId, serverType)

            val newRequest = originalRequest.newBuilder()
                .addHeader("Authorization", authHeader)
                .addHeader("X-Emby-Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .build()

            chain.proceed(newRequest)
        }

        val cacheInterceptor = Interceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            val isGet = request.method.equals("GET", ignoreCase = true)
            val path = request.url.encodedPath
            val isUserScopedData = path.contains("/Users/", ignoreCase = true)
            val cacheControl = response.header("Cache-Control").orEmpty()
            val hasExplicitCaching =
                cacheControl.contains("max-age", ignoreCase = true) ||
                    cacheControl.contains("no-store", ignoreCase = true) ||
                    cacheControl.contains("no-cache", ignoreCase = true)

            if (isGet && response.isSuccessful && !hasExplicitCaching && !isUserScopedData) {
                response.newBuilder()
                    .header("Cache-Control", "public, max-age=60")
                    .build()
            } else {
                response
            }
        }

        val timingInterceptor = Interceptor { chain ->
            val request = chain.request()
            val startNanos = System.nanoTime()
            try {
                val response = chain.proceed(request)
                val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
                val endpoint = request.url.encodedPath
                Log.d(
                    NETWORK_LOG_TAG,
                    "${request.method} $endpoint -> ${response.code} in ${durationMs}ms"
                )
                response
            } catch (e: Exception) {
                val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
                val endpoint = request.url.encodedPath
                Log.w(
                    NETWORK_LOG_TAG,
                    "${request.method} $endpoint failed in ${durationMs}ms: ${e.message}"
                )
                throw e
            }
        }

        builder.addInterceptor(authInterceptor)
        builder.addNetworkInterceptor(cacheInterceptor)
        builder.addInterceptor(timingInterceptor)

        return builder.build()
    }

    private fun defaultTimeoutConfig(): NetworkTimeoutConfig {
        return NetworkTimeoutConfig(
            requestTimeoutMs = 30000,
            connectionTimeoutMs = 6000,
            socketTimeoutMs = 10000
        )
    }

    private fun buildAuthHeader(
        accessToken: String?,
        deviceId: String,
        serverType: ServerType
    ): String {
        val headerPrefix = if (serverType == ServerType.EMBY) "Emby" else "MediaBrowser"
        return buildString {
            append("$headerPrefix ")
            append("Client=\"$CLIENT_NAME\", ")
            append("Device=\"$DEVICE_NAME\", ")
            append("DeviceId=\"$deviceId\", ")
            append("Version=\"${BuildConfig.CLIENT_VERSION}\"")

            if (!accessToken.isNullOrEmpty()) {
                append(", Token=\"$accessToken\"")
            }
        }
    }

    private fun ensureTrailingSlash(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }

    private fun buildBaseUrlCandidates(serverUrl: String): List<String> {
        val normalized = serverUrl.trim().trimEnd('/')
        if (normalized.endsWith("/emby", ignoreCase = true)) {
            return listOf(normalized)
        }

        return listOf(normalized, "$normalized/emby")
    }

    private fun inferServerType(baseUrl: String): ServerType {
        return if (baseUrl.trimEnd('/').endsWith("/emby", ignoreCase = true)) {
            ServerType.EMBY
        } else {
            ServerType.JELLYFIN
        }
    }

    private fun detectServerType(baseUrl: String, serverInfo: ServerInfo): ServerType {
        val productName = serverInfo.productName.orEmpty()
        return if (
            productName.contains("emby", ignoreCase = true) ||
            baseUrl.trimEnd('/').endsWith("/emby", ignoreCase = true)
        ) {
            ServerType.EMBY
        } else {
            ServerType.JELLYFIN
        }
    }

}
