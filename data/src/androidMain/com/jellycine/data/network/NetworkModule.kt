package com.jellycine.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.jellycine.data.DataModuleConfig
import com.jellycine.data.api.MediaServerApi
import com.jellycine.data.api.MediaServerApiClient
import com.jellycine.data.model.AuthHeaderDto
import com.jellycine.data.preferences.NetworkTimeoutConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object NetworkModule {

    private const val CLIENT_NAME = "JellyCine"
    private const val DEVICE_NAME = "Android"
    private const val NETWORK_LOG_TAG = "JellyCineNetwork"
    private const val OFFLINE_DEBOUNCE_MS = 4000L
    private val deviceId by lazy { "jellycine-android-${UUID.randomUUID()}" }
    private val apiCache = ConcurrentHashMap<String, MediaServerApi>()

    fun getClientDeviceId(): String = deviceId

    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun observeNetworkAvailability(context: Context): Flow<Boolean> = callbackFlow {
        val appContext = context.applicationContext
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        var OfflineEmission: Job? = null

        trySend(isInternetAvailable(appContext))

        fun OfflineDebounce() {
            if (isInternetAvailable(appContext)) {
                OfflineEmission?.cancel()
                OfflineEmission = null
                trySend(true)
                return
            }

            if (OfflineEmission?.isActive == true) {
                return
            }

            OfflineEmission = launch {
                delay(OFFLINE_DEBOUNCE_MS)
                if (!isInternetAvailable(appContext)) {
                    trySend(false)
                }
            }
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                OfflineDebounce()
            }

            override fun onLost(network: Network) {
                OfflineDebounce()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                OfflineDebounce()
            }

            override fun onUnavailable() {
                OfflineDebounce()
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        runCatching {
            connectivityManager.registerNetworkCallback(request, callback)
        }.onFailure {
            trySend(isInternetAvailable(appContext))
        }

        awaitClose {
            OfflineEmission?.cancel()
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }.distinctUntilChanged()

    fun createMediaServerApi(
        baseUrl: String,
        accessToken: String? = null,
        serverType: ServerType? = null,
        storageDir: File? = null,
        timeoutConfig: NetworkTimeoutConfig? = null
    ): MediaServerApi {
        val baseUrlStd = trimTrailingSlash(baseUrl, trailingSlash = true)
        val endpointType = serverType ?: inferServerType(baseUrlStd)
        val resolvedTimeoutConfig = timeoutConfig ?: defaultTimeoutConfig()
        val cacheKey = buildString {
            append(trimTrailingSlash(baseUrlStd))
            append("|")
            append(accessToken.orEmpty())
            append("|")
            append(endpointType.name)
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
            serverType = endpointType,
            storageDir = storageDir,
            timeoutConfig = resolvedTimeoutConfig
        )
        val httpClient = createHttpClient(
            baseUrl = baseUrlStd,
            okHttpClient = okHttpClient
        )

        return MediaServerApiClient(
            client = httpClient,
            baseUrl = baseUrlStd
        ).also { apiCache[cacheKey] = it }
    }

    suspend fun serverEndpoint(
        serverUrl: String,
        storageDir: File? = null,
        timeoutConfig: NetworkTimeoutConfig? = null
    ): Result<ServerEndpoint> {
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
                    val detectedType = detectServerType(serverInfo, response.headers())
                    return Result.success(
                        ServerEndpoint(
                            baseUrl = trimTrailingSlash(candidate, trailingSlash = true),
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

    private fun createHttpClient(
        baseUrl: String,
        okHttpClient: OkHttpClient
    ): HttpClient {
        return HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                preconfigured = okHttpClient
            }
            defaultRequest {
                url(baseUrl)
            }
            install(ContentNegotiation) {
                json(JellyCineJson)
            }
        }
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
        return AuthHeaderDto.fromServerType(
            serverType = serverType,
            deviceId = deviceId,
            version = DataModuleConfig.CLIENT_VERSION,
            accessToken = accessToken,
            clientName = CLIENT_NAME,
            deviceName = DEVICE_NAME
        ).asHeaderValue()
    }

}