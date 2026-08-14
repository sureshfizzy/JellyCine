package com.jellycine.data.network

import com.jellycine.data.DataModuleConfig
import com.jellycine.data.api.MediaServerApi
import com.jellycine.data.api.MediaServerApiClient
import com.jellycine.data.model.AuthHeaderDto
import com.jellycine.data.preferences.NetworkTimeoutConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import platform.Foundation.NSUUID
import platform.SystemConfiguration.SCNetworkReachabilityCreateWithName
import platform.SystemConfiguration.SCNetworkReachabilityFlags
import platform.SystemConfiguration.SCNetworkReachabilityFlagsVar
import platform.SystemConfiguration.SCNetworkReachabilityGetFlags
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsConnectionRequired
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsReachable

@OptIn(ExperimentalForeignApi::class)
object NetworkModule {

    private const val CLIENT_NAME = "JellyCine"
    private const val DEVICE_NAME = "iOS"
    private const val OFFLINE_DEBOUNCE_MS = 4000L
    private val deviceId by lazy {
        "jellycine-ios-${NSUUID().UUIDString}"
    }
    private val apiCache = mutableMapOf<String, MediaServerApi>()

    fun getClientDeviceId(): String = deviceId

    fun isInternetAvailable(): Boolean {
        val reachability = SCNetworkReachabilityCreateWithName(null, "www.google.com")
            ?: return false

        memScoped {
            val flags = alloc<SCNetworkReachabilityFlagsVar>()
            val success = SCNetworkReachabilityGetFlags(reachability, flags.ptr)

            if (!success) return false

            val flagsValue: SCNetworkReachabilityFlags = flags.value
            val reachable = (flagsValue.toInt() and kSCNetworkReachabilityFlagsReachable.toInt()) != 0
            val connectionRequired = (flagsValue.toInt() and kSCNetworkReachabilityFlagsConnectionRequired.toInt()) != 0

            return reachable && !connectionRequired
        }
    }

    fun isWifiConnected(): Boolean {
        // iOS network check for WiFi specifically
        // This is a simplified version - more sophisticated checks can be added
        return isInternetAvailable()
    }

    fun observeNetworkAvailability(): Flow<Boolean> = callbackFlow {
        trySend(isInternetAvailable())

        val pollJob = launch {
            while (true) {
                delay(OFFLINE_DEBOUNCE_MS)
                trySend(isInternetAvailable())
            }
        }

        awaitClose {
            pollJob.cancel()
        }
    }.distinctUntilChanged()

    fun createMediaServerApi(
        baseUrl: String,
        accessToken: String? = null,
        serverType: ServerType? = null,
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

        val httpClient = createHttpClient(
            baseUrl = baseUrlStd,
            accessToken = accessToken,
            serverType = endpointType,
            timeoutConfig = resolvedTimeoutConfig
        )

        return MediaServerApiClient(
            client = httpClient,
            baseUrl = baseUrlStd
        ).also { apiCache[cacheKey] = it }
    }

    suspend fun serverEndpoint(
        serverUrl: String,
        timeoutConfig: NetworkTimeoutConfig? = null
    ): Result<ServerEndpoint> {
        val candidates = buildBaseUrlCandidates(serverUrl)
        var lastError: Exception? = null

        for (candidate in candidates) {
            try {
                val api = createMediaServerApi(
                    baseUrl = candidate,
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

                lastError = Exception("Server endpoint returned HTTP ${response.code()}")
            } catch (e: Exception) {
                lastError = e
            }
        }

        return Result.failure(
            lastError ?: Exception("Could not resolve server endpoint")
        )
    }

    private fun createHttpClient(
        baseUrl: String,
        accessToken: String? = null,
        serverType: ServerType,
        timeoutConfig: NetworkTimeoutConfig
    ): HttpClient {
        val authHeader = buildAuthHeader(accessToken, deviceId, serverType)

        return HttpClient(Darwin) {
            expectSuccess = false

            engine {
                configureRequest {
                    setTimeoutInterval(timeoutConfig.requestTimeoutMs / 1000.0)
                }
            }

            defaultRequest {
                url(baseUrl)
                headers.append("Authorization", authHeader)
                headers.append("X-Emby-Authorization", authHeader)
                headers.append("Content-Type", "application/json")
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
