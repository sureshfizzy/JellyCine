package com.jellycine.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.jellycine.data.network.JellyCineJson
import com.jellycine.data.network.trimTrailingSlash
import com.jellycine.data.preferences.NetworkPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

data class SeerrConnectionInfo(
    val serverUrl: String,
    val serverVersion: String? = null,
    val isVerified: Boolean = false
)

class SeerrRepository(context: Context) {
    private val appContext = context.applicationContext
    private val networkPreferences = NetworkPreferences(appContext)
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val securePrefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        EncryptedSharedPreferences.create(
            SECURE_PREFS_NAME,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    init {
        clearLegacyConnectionStorage()
    }

    fun getSavedConnectionInfo(scopeId: String?): SeerrConnectionInfo? {
        val validScopeId = scopeId?.takeIf { it.isNotBlank() } ?: return null
        val storedConnection = storedConnection(validScopeId) ?: return null

        return SeerrConnectionInfo(
            serverUrl = storedConnection.serverUrl,
            serverVersion = prefs.getString(scopedKey(KEY_SERVER_VERSION, validScopeId), null),
            isVerified = prefs.getBoolean(scopedKey(KEY_IS_VERIFIED, validScopeId), false)
        )
    }

    suspend fun connect(
        scopeId: String,
        serverUrl: String,
        username: String,
        password: String,
        mediaServerUrl: String
    ): Result<SeerrConnectionInfo> = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeServerUrl(serverUrl).getOrElse { error ->
            return@withContext Result.failure(error)
        }
        val sanitizedUsername = username.trim()
        val normalizedMediaServerUrl = buildMediaServerUrl(mediaServerUrl).getOrElse { error ->
            return@withContext Result.failure(error)
        }
        if (sanitizedUsername.isBlank()) {
            return@withContext Result.failure(Exception("Please enter your Seerr username."))
        }
        if (password.isBlank()) {
            return@withContext Result.failure(Exception("Please enter your Seerr password."))
        }

        val signInResult = signIn(
            serverUrl = normalizedUrl,
            username = sanitizedUsername,
            password = password,
            mediaServerUrl = normalizedMediaServerUrl
        )
            .getOrElse { error -> return@withContext Result.failure(error) }

        val connection = signInResult.connection.copy(isVerified = true)
        persistConnection(
            scopeId = scopeId,
            connection = connection,
            sessionCookie = signInResult.sessionCookie
        )

        Result.success(connection)
    }

    suspend fun refreshConnection(scopeId: String): Result<SeerrConnectionInfo> = withContext(Dispatchers.IO) {
        val storedConnection = storedConnection(scopeId) ?: return@withContext Result.failure(
            Exception("Seerr is not connected.")
        )

        verifyConnection(
            serverUrl = storedConnection.serverUrl,
            sessionCookie = storedConnection.sessionCookie
        ).onSuccess { connection ->
            persistConnection(
                scopeId = scopeId,
                connection = connection.copy(isVerified = true),
                sessionCookie = storedConnection.sessionCookie
            )
        }.onFailure {
            prefs.edit()
                .putBoolean(scopedKey(KEY_IS_VERIFIED, scopeId), false)
                .apply()
        }
    }

    fun disconnect(scopeId: String?) {
        if (scopeId.isNullOrBlank()) return
        prefs.edit()
            .remove(scopedKey(KEY_SERVER_URL, scopeId))
            .remove(scopedKey(KEY_SERVER_VERSION, scopeId))
            .remove(scopedKey(KEY_IS_VERIFIED, scopeId))
            .apply()
        securePrefs.edit()
            .remove(scopedKey(KEY_SESSION_COOKIE, scopeId))
            .apply()
    }

    private fun storedConnection(scopeId: String?): StoredConnection? {
        if (scopeId.isNullOrBlank()) return null
        val serverUrl = prefs.getString(scopedKey(KEY_SERVER_URL, scopeId), null).ifNotBlank()
            ?: return null
        val sessionCookie = securePrefs.getString(scopedKey(KEY_SESSION_COOKIE, scopeId), null).ifNotBlank()
            ?: return null

        return StoredConnection(
            serverUrl = serverUrl,
            sessionCookie = sessionCookie
        )
    }

    private fun persistConnection(
        scopeId: String,
        connection: SeerrConnectionInfo,
        sessionCookie: String
    ) {
        prefs.edit()
            .putString(scopedKey(KEY_SERVER_URL, scopeId), connection.serverUrl)
            .putString(scopedKey(KEY_SERVER_VERSION, scopeId), connection.serverVersion)
            .putBoolean(scopedKey(KEY_IS_VERIFIED, scopeId), connection.isVerified)
            .apply()
        securePrefs.edit()
            .putString(scopedKey(KEY_SESSION_COOKIE, scopeId), sessionCookie)
            .apply()
    }

    private fun signIn(
        serverUrl: String,
        username: String,
        password: String,
        mediaServerUrl: String
    ): Result<SignInResult> {
        return runRequestCatching {
            val client = createHttpClient()
            val statusPayload = getStatusPayload(client, serverUrl).getOrElse { error ->
                return Result.failure(error)
            }
            val authUrl = buildApiUrl(serverUrl, "auth/jellyfin")
            val initialAttempt = performSignInRequest(
                client = client,
                serverUrl = serverUrl,
                authUrl = authUrl,
                username = username,
                password = password,
                mediaServerUrl = mediaServerUrl,
                statusPayload = statusPayload,
                includeMediaServerConfig = true
            )
            if (
                initialAttempt.isFailure &&
                initialAttempt.exceptionOrNull()?.message == HOSTNAME_ALREADY_CONFIGURED_ERROR
            ) {
                return performSignInRequest(
                    client = client,
                    serverUrl = serverUrl,
                    authUrl = authUrl,
                    username = username,
                    password = password,
                    mediaServerUrl = mediaServerUrl,
                    statusPayload = statusPayload,
                    includeMediaServerConfig = false
                )
            }
            initialAttempt
        }
    }

    private fun performSignInRequest(
        client: OkHttpClient,
        serverUrl: String,
        authUrl: okhttp3.HttpUrl,
        username: String,
        password: String,
        mediaServerUrl: String,
        statusPayload: SeerrStatusResponse,
        includeMediaServerConfig: Boolean
    ): Result<SignInResult> {
        val requestBody = JellyCineJson.encodeToString(
            SeerrLoginRequest(
                username = username,
                password = password,
                hostname = mediaServerUrl.takeIf { includeMediaServerConfig }
            )
        ).toRequestBody("application/json".toMediaType())

        val response = client.newCall(
            Request.Builder()
                .url(authUrl)
                .post(requestBody)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .build()
        ).execute()

        return response.use { authResponse ->
            val responseBody = authResponse.body?.string().orEmpty()
            when {
                authResponse.isSuccessful -> {
                    val sessionCookie = extractSessionCookie(authResponse.headers.values("Set-Cookie"))
                        ?: return Result.failure(
                            Exception("Seerr sign-in succeeded, but no session cookie was returned.")
                        )

                    Result.success(
                        SignInResult(
                            connection = buildConnectionInfo(
                                serverUrl = serverUrl,
                                serverVersion = statusPayload.version
                            ),
                            sessionCookie = sessionCookie
                        )
                    )
                }

                authResponse.code == 401 || authResponse.code == 403 -> {
                    Result.failure(
                        Exception("Seerr rejected these credentials. Please check your username and password.")
                    )
                }

                authResponse.code == 500 &&
                    responseBody.contains("hostname already configured", ignoreCase = true) -> {
                    Result.failure(Exception(HOSTNAME_ALREADY_CONFIGURED_ERROR))
                }

                else -> {
                    Result.failure(
                        Exception("Seerr sign-in failed with HTTP ${authResponse.code}.")
                    )
                }
            }
        }
    }

    private fun verifyConnection(
        serverUrl: String,
        sessionCookie: String
    ): Result<SeerrConnectionInfo> {
        return runRequestCatching {
            val client = createHttpClient()
            val statusPayload = getStatusPayload(client, serverUrl).getOrElse { error ->
                return Result.failure(error)
            }

            val authResponse = client.newCall(
                Request.Builder()
                    .url(buildApiUrl(serverUrl, "auth/me"))
                    .get()
                    .addHeader("Accept", "application/json")
                    .addHeader("Cookie", sessionCookie)
                    .build()
            ).execute()

            authResponse.use { response ->
                val responseBody = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> {
                        Result.success(
                            buildConnectionInfo(
                                serverUrl = serverUrl,
                                serverVersion = statusPayload.version
                            )
                        )
                    }

                    response.code == 401 || response.code == 403 -> {
                        Result.failure(
                            Exception("Your Seerr session expired. Please sign in again.")
                        )
                    }

                    else -> {
                        Result.failure(
                            Exception("Could not verify the Seerr session. Server responded with HTTP ${response.code}.")
                        )
                    }
                }
            }
        }
    }

    private fun getStatusPayload(
        client: OkHttpClient,
        serverUrl: String
    ): Result<SeerrStatusResponse> {
        val statusResponse = client.newCall(
            Request.Builder()
                .url(buildApiUrl(serverUrl, "status"))
                .get()
                .build()
        ).execute()

        return statusResponse.use { response ->
            val responseBody = response.body?.string().orEmpty()
            when {
                response.isSuccessful -> {
                    val payload = runCatching {
                        JellyCineJson.decodeFromString<SeerrStatusResponse>(responseBody)
                    }.getOrDefault(SeerrStatusResponse())
                    Result.success(payload)
                }

                response.code == 404 -> {
                    Result.failure(
                        Exception("Seerr was not found at this URL. Please check the address.")
                    )
                }

                else -> {
                    Result.failure(
                        Exception("Seerr returned HTTP ${response.code}. Please verify the URL.")
                    )
                }
            }
        }
    }

    private fun createHttpClient(): OkHttpClient {
        val timeouts = networkPreferences.getTimeoutConfig()
        return OkHttpClient.Builder()
            .callTimeout(timeouts.requestTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .connectTimeout(timeouts.connectionTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeouts.socketTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(timeouts.socketTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun normalizeServerUrl(serverUrl: String): Result<String> {
        val trimmed = serverUrl.trim()
        if (trimmed.isBlank()) {
            return Result.failure(Exception("Please enter your Seerr URL."))
        }
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return Result.failure(Exception("Seerr URL must start with http:// or https://"))
        }

        val withoutTrailingSlash = trimTrailingSlash(trimmed)
            .removeKnownApiSuffix()
        val validatedUrl = withoutTrailingSlash.toHttpUrlOrNull()
            ?: return Result.failure(Exception("Please enter a valid Seerr URL."))

        return Result.success(
            trimTrailingSlash(validatedUrl.toString())
        )
    }

    private fun buildMediaServerUrl(serverUrl: String): Result<String> {
        val trimmed = serverUrl.trim()
        if (trimmed.isBlank()) {
            return Result.failure(Exception("Your media server URL is missing. Sign in to JellyCine first."))
        }
        val validatedUrl = trimTrailingSlash(trimmed).toHttpUrlOrNull()
            ?: return Result.failure(Exception("Your media server URL is invalid."))

        return Result.success(trimTrailingSlash(validatedUrl.toString()))
    }

    private fun buildApiUrl(
        serverUrl: String,
        path: String
    ) = "${trimTrailingSlash(serverUrl)}/api/v1/$path".toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Invalid Seerr URL.")

    private fun extractSessionCookie(setCookieHeaders: List<String>): String? {
        return setCookieHeaders
            .mapNotNull { header ->
                header.substringBefore(';')
                    .trim()
                    .takeIf { cookie -> cookie.contains('=') && cookie.isNotBlank() }
            }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("; ")
    }

    private fun scopedKey(baseKey: String, scopeId: String): String {
        return "${baseKey}_${sha256(scopeId)}"
    }

    private fun buildConnectionInfo(
        serverUrl: String,
        serverVersion: String?
    ): SeerrConnectionInfo {
        return SeerrConnectionInfo(
            serverUrl = serverUrl,
            serverVersion = serverVersion.ifNotBlank(),
            isVerified = true
        )
    }

    private inline fun <T> runRequestCatching(block: () -> Result<T>): Result<T> = try {
        block()
    } catch (error: Exception) {
        Result.failure(connectionError(error))
    }

    private fun connectionError(error: Exception): Exception = when (error) {
        is UnknownHostException -> Exception("Cannot find Seerr. Check the URL and your network connection.")
        is ConnectException -> Exception("Cannot connect to Seerr. Please make sure it is online.")
        is SocketTimeoutException -> Exception("Seerr took too long to respond. Please try again.")
        is SSLException -> Exception("SSL connection to Seerr failed. Check your HTTPS setup.")
        is CertificateException -> Exception("Seerr's SSL certificate could not be verified.")
        is IOException -> Exception(error.message ?: "Unable to reach Seerr right now.")
        else -> Exception(error.message ?: "Unable to connect to Seerr.")
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                append("%02x".format(byte.toInt() and 0xff))
            }
        }
    }

    private fun String?.ifNotBlank(): String? = this?.takeIf { it.isNotBlank() }

    private fun String.removeKnownApiSuffix(): String {
        return when {
            endsWith("/api/v1", ignoreCase = true) -> dropLast("/api/v1".length)
            endsWith("/api", ignoreCase = true) -> dropLast("/api".length)
            else -> this
        }
    }

    private fun clearLegacyConnectionStorage() {
        prefs.edit()
            .remove(KEY_SERVER_URL)
            .remove(KEY_SERVER_VERSION)
            .remove(KEY_IS_VERIFIED)
            .apply()
        securePrefs.edit()
            .remove(KEY_SESSION_COOKIE)
            .apply()
    }

    @Serializable
    private data class SeerrStatusResponse(
        val version: String? = null
    )

    @Serializable
    private data class SeerrLoginRequest(
        val username: String,
        val password: String,
        val hostname: String? = null
    )

    private data class StoredConnection(
        val serverUrl: String,
        val sessionCookie: String
    )

    private data class SignInResult(
        val connection: SeerrConnectionInfo,
        val sessionCookie: String
    )

    companion object {
        private const val HOSTNAME_ALREADY_CONFIGURED_ERROR = "Seerr hostname already configured"
        private const val PREFS_NAME = "seerr_connection_prefs"
        private const val SECURE_PREFS_NAME = "seerr_connection_secure_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_SERVER_VERSION = "server_version"
        private const val KEY_IS_VERIFIED = "is_verified"
        private const val KEY_SESSION_COOKIE = "session_cookie"
    }
}
