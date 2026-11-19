package com.jellycine.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.jellycine.data.R
import com.jellycine.data.network.JellyCineJson
import com.jellycine.data.preferences.NetworkPreferences
import com.jellycine.data.preferences.NetworkTimeoutConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

fun SeerrRepository(context: Context): SeerrRepository =
    SeerrRepository(SeerrPlatform(context))

class SeerrPlatform(context: Context) : SeerrHost {
    private val appContext = context.applicationContext
    private val networkPreferences = NetworkPreferences(appContext)

    override val preferences: SeerrKeyValueStore = SharedPreferencesStore(
        lazy { appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    )

    override val secureStore: SeerrKeyValueStore = SharedPreferencesStore(
        lazy {
            EncryptedSharedPreferences.create(
                SECURE_PREFS_NAME,
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                appContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    )

    override fun timeoutConfig(): NetworkTimeoutConfig = networkPreferences.getTimeoutConfig()

    override fun createHttpClient(timeouts: NetworkTimeoutConfig): HttpClient {
        val okHttpClient = OkHttpClient.Builder()
            .callTimeout(timeouts.requestTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .connectTimeout(timeouts.connectionTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeouts.socketTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(timeouts.socketTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                preconfigured = okHttpClient
            }
            install(ContentNegotiation) {
                json(JellyCineJson)
            }
        }
    }

    override suspend fun text(message: SeerrMessage, vararg args: Any): String =
        appContext.getString(stringRes(message), *args)

    override suspend fun connectionError(error: Exception): Exception = when (error) {
        is UnknownHostException -> Exception(text(SeerrMessage.ERROR_UNKNOWN_HOST))
        is ConnectException -> Exception(text(SeerrMessage.ERROR_CONNECT))
        is SocketTimeoutException -> Exception(text(SeerrMessage.ERROR_TIMEOUT))
        is SSLException -> Exception(text(SeerrMessage.ERROR_SSL))
        is CertificateException -> Exception(text(SeerrMessage.ERROR_CERTIFICATE))
        is IOException -> Exception(error.message ?: text(SeerrMessage.ERROR_UNREACHABLE))
        else -> Exception(error.message ?: text(SeerrMessage.ERROR_CONNECT_GENERIC))
    }

    private fun stringRes(message: SeerrMessage): Int = when (message) {
        SeerrMessage.CREDIT_ROLE_ACTOR -> R.string.seerr_credit_role_actor
        SeerrMessage.CREDIT_ROLE_CREATOR -> R.string.seerr_credit_role_creator
        SeerrMessage.CREDIT_ROLE_DIRECTOR -> R.string.seerr_credit_role_director
        SeerrMessage.CREDIT_ROLE_OTHER -> R.string.seerr_credit_role_other
        SeerrMessage.CREDIT_ROLE_PRODUCER -> R.string.seerr_credit_role_producer
        SeerrMessage.CREDIT_ROLE_WRITER -> R.string.seerr_credit_role_writer
        SeerrMessage.ERROR_CERTIFICATE -> R.string.seerr_error_certificate
        SeerrMessage.ERROR_CONNECT -> R.string.seerr_error_connect
        SeerrMessage.ERROR_CONNECT_GENERIC -> R.string.seerr_error_connect_generic
        SeerrMessage.ERROR_CREDENTIALS_REJECTED -> R.string.seerr_error_credentials_rejected
        SeerrMessage.ERROR_CURRENT_USER_LOAD -> R.string.seerr_error_current_user_load
        SeerrMessage.ERROR_CURRENT_USER_PARSE -> R.string.seerr_error_current_user_parse
        SeerrMessage.ERROR_DISCOVERY_LOAD -> R.string.seerr_error_discovery_load
        SeerrMessage.ERROR_DISCOVERY_PARSE -> R.string.seerr_error_discovery_parse
        SeerrMessage.ERROR_HTTP_RESPONSE -> R.string.seerr_error_http_response
        SeerrMessage.ERROR_MEDIA_SERVER_URL_INVALID -> R.string.seerr_error_media_server_url_invalid
        SeerrMessage.ERROR_MEDIA_SERVER_URL_MISSING -> R.string.seerr_error_media_server_url_missing
        SeerrMessage.ERROR_MEDIA_TYPE_UNSUPPORTED -> R.string.seerr_error_media_type_unsupported
        SeerrMessage.ERROR_MISSING_SESSION_COOKIE -> R.string.seerr_error_missing_session_cookie
        SeerrMessage.ERROR_NETWORK_SHOWS_LOAD -> R.string.seerr_error_network_shows_load
        SeerrMessage.ERROR_NETWORK_SHOWS_PARSE -> R.string.seerr_error_network_shows_parse
        SeerrMessage.ERROR_NOT_CONNECTED -> R.string.seerr_error_not_connected
        SeerrMessage.ERROR_NOT_FOUND_AT_URL -> R.string.seerr_error_not_found_at_url
        SeerrMessage.ERROR_PASSWORD_REQUIRED -> R.string.seerr_error_password_required
        SeerrMessage.ERROR_PERSON_CREDITS_FETCH -> R.string.seerr_error_person_credits_fetch
        SeerrMessage.ERROR_PERSON_CREDITS_PARSE -> R.string.seerr_error_person_credits_parse
        SeerrMessage.ERROR_PERSON_DETAILS_INCOMPLETE -> R.string.seerr_error_person_details_incomplete
        SeerrMessage.ERROR_PERSON_DETAILS_LOAD -> R.string.seerr_error_person_details_load
        SeerrMessage.ERROR_PERSON_DETAILS_PARSE -> R.string.seerr_error_person_details_parse
        SeerrMessage.ERROR_PERSON_ID_MISSING -> R.string.seerr_error_person_id_missing
        SeerrMessage.ERROR_PERSON_NOT_FOUND -> R.string.seerr_error_person_not_found
        SeerrMessage.ERROR_RELATED_TITLES_LOAD -> R.string.seerr_error_related_titles_load
        SeerrMessage.ERROR_RELATED_TITLES_PARSE -> R.string.seerr_error_related_titles_parse
        SeerrMessage.ERROR_REQUEST_OPTIONS_LOAD -> R.string.seerr_error_request_options_load
        SeerrMessage.ERROR_REQUEST_OPTIONS_PARSE -> R.string.seerr_error_request_options_parse
        SeerrMessage.ERROR_REQUEST_TITLE -> R.string.seerr_error_request_title
        SeerrMessage.ERROR_REQUEST_TITLE_PARSE -> R.string.seerr_error_request_title_parse
        SeerrMessage.ERROR_REQUESTS_LOAD -> R.string.seerr_error_requests_load
        SeerrMessage.ERROR_REQUESTS_PARSE -> R.string.seerr_error_requests_parse
        SeerrMessage.ERROR_RETURNED_HTTP_VERIFY_URL -> R.string.seerr_error_returned_http_verify_url
        SeerrMessage.ERROR_SEARCH_RESULTS_PARSE -> R.string.seerr_error_search_results_parse
        SeerrMessage.ERROR_SEARCH_TITLES -> R.string.seerr_error_search_titles
        SeerrMessage.ERROR_SIGN_IN_HTTP -> R.string.seerr_error_sign_in_http
        SeerrMessage.ERROR_SSL -> R.string.seerr_error_ssl
        SeerrMessage.ERROR_STUDIO_MOVIES_LOAD -> R.string.seerr_error_studio_movies_load
        SeerrMessage.ERROR_STUDIO_MOVIES_PARSE -> R.string.seerr_error_studio_movies_parse
        SeerrMessage.ERROR_TIMEOUT -> R.string.seerr_error_timeout
        SeerrMessage.ERROR_TITLE_DETAILS_INCOMPLETE -> R.string.seerr_error_title_details_incomplete
        SeerrMessage.ERROR_TITLE_DETAILS_LOAD -> R.string.seerr_error_title_details_load
        SeerrMessage.ERROR_TITLE_DETAILS_PARSE -> R.string.seerr_error_title_details_parse
        SeerrMessage.ERROR_TITLE_ID_MISSING -> R.string.seerr_error_title_id_missing
        SeerrMessage.ERROR_TITLE_NOT_FOUND -> R.string.seerr_error_title_not_found
        SeerrMessage.ERROR_UNKNOWN_HOST -> R.string.seerr_error_unknown_host
        SeerrMessage.ERROR_UNREACHABLE -> R.string.seerr_error_unreachable
        SeerrMessage.ERROR_URL_INVALID -> R.string.seerr_error_url_invalid
        SeerrMessage.ERROR_URL_REQUIRED -> R.string.seerr_error_url_required
        SeerrMessage.ERROR_URL_SCHEME_REQUIRED -> R.string.seerr_error_url_scheme_required
        SeerrMessage.ERROR_USERNAME_REQUIRED -> R.string.seerr_error_username_required
        SeerrMessage.ERROR_VERIFY_SESSION_HTTP -> R.string.seerr_error_verify_session_http
    }
}

private class SharedPreferencesStore(prefsLazy: Lazy<SharedPreferences>) : SeerrKeyValueStore {
    private val prefs by prefsLazy

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    override fun getInt(key: String): Int? = if (prefs.contains(key)) prefs.getInt(key, 0) else null

    override fun putInt(key: String, value: Int?) {
        prefs.edit().apply { if (value == null) remove(key) else putInt(key, value) }.apply()
    }

    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
