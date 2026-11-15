package com.jellycine.data.repository

import com.jellycine.data.api.MediaServerApi
import com.jellycine.data.network.NetworkModule
import com.jellycine.data.network.ServerType
import com.jellycine.data.network.JellyCineJson
import com.jellycine.data.preferences.NetworkPreferences
import com.jellycine.data.preferences.NetworkTimeoutConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

class IosMediaPlatform(
    private val messages: suspend (MediaMessage, Array<out Any>) -> String
) : MediaPlatform {

    private val networkPreferences = NetworkPreferences()

    override val clientDeviceId: String
        get() = NetworkModule.getClientDeviceId()

    @OptIn(ExperimentalForeignApi::class)
    override val snapshotDirectory: String
        get() = requireNotNull(
            NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null
            )?.path
        )

    override suspend fun text(message: MediaMessage, vararg args: Any): String = messages(message, args)

    override fun isInternetAvailable(): Boolean = NetworkModule.isInternetAvailable()

    override fun timeoutConfig(): NetworkTimeoutConfig = networkPreferences.getTimeoutConfig()

    override fun createApi(
        baseUrl: String,
        accessToken: String?,
        serverType: ServerType?,
        timeoutConfig: NetworkTimeoutConfig
    ): MediaServerApi {
        return NetworkModule.createMediaServerApi(
            baseUrl = baseUrl,
            accessToken = accessToken,
            serverType = serverType,
            timeoutConfig = timeoutConfig
        )
    }

    override fun createExternalHttpClient(): HttpClient = HttpClient(Darwin) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(JellyCineJson)
        }
    }

    override fun createFastHttpClient(): HttpClient = HttpClient(Darwin) {
        expectSuccess = false
        engine {
            configureRequest {
                setTimeoutInterval(5.0)
            }
        }
    }
}
