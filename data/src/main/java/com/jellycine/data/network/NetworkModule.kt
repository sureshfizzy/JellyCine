package com.jellycine.data.network

import com.jellycine.data.api.MediaServerApi
import com.jellycine.data.model.ServerInfo
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

object NetworkModule {

    private const val CLIENT_NAME = "JellyCine"
    private const val CLIENT_VERSION = "1.0.0"
    private const val DEVICE_NAME = "Android"
    private val deviceId by lazy { "jellycine-android-${UUID.randomUUID()}" }

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
        serverType: ServerType? = null
    ): MediaServerApi {
        val resolvedType = serverType ?: inferServerType(baseUrl)
        val okHttpClient = createOkHttpClient(accessToken, resolvedType)

        val retrofit = Retrofit.Builder()
            .baseUrl(ensureTrailingSlash(baseUrl))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(MediaServerApi::class.java)
    }

    fun createJellyfinApi(baseUrl: String, accessToken: String? = null): MediaServerApi {
        return createMediaServerApi(baseUrl, accessToken, ServerType.JELLYFIN)
    }

    suspend fun resolveServerEndpoint(serverUrl: String): Result<ResolvedServerEndpoint> {
        val candidates = buildBaseUrlCandidates(serverUrl)
        var lastError: Exception? = null

        for (candidate in candidates) {
            try {
                val api = createMediaServerApi(candidate)
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
        serverType: ServerType
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

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

        builder.addInterceptor(authInterceptor)

        return builder.build()
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
            append("Version=\"$CLIENT_VERSION\"")

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
