package com.jellycine.data.repository

import com.jellycine.data.preferences.NetworkTimeoutConfig
import io.ktor.client.HttpClient
enum class SeerrMessage {
    CREDIT_ROLE_ACTOR,
    CREDIT_ROLE_CREATOR,
    CREDIT_ROLE_DIRECTOR,
    CREDIT_ROLE_OTHER,
    CREDIT_ROLE_PRODUCER,
    CREDIT_ROLE_WRITER,
    ERROR_CERTIFICATE,
    ERROR_CONNECT,
    ERROR_CONNECT_GENERIC,
    ERROR_CREDENTIALS_REJECTED,
    ERROR_CURRENT_USER_LOAD,
    ERROR_CURRENT_USER_PARSE,
    ERROR_DISCOVERY_LOAD,
    ERROR_DISCOVERY_PARSE,
    ERROR_HTTP_RESPONSE,
    ERROR_MEDIA_SERVER_URL_INVALID,
    ERROR_MEDIA_SERVER_URL_MISSING,
    ERROR_MEDIA_TYPE_UNSUPPORTED,
    ERROR_MISSING_SESSION_COOKIE,
    ERROR_NETWORK_SHOWS_LOAD,
    ERROR_NETWORK_SHOWS_PARSE,
    ERROR_NOT_CONNECTED,
    ERROR_NOT_FOUND_AT_URL,
    ERROR_PASSWORD_REQUIRED,
    ERROR_PERSON_CREDITS_FETCH,
    ERROR_PERSON_CREDITS_PARSE,
    ERROR_PERSON_DETAILS_INCOMPLETE,
    ERROR_PERSON_DETAILS_LOAD,
    ERROR_PERSON_DETAILS_PARSE,
    ERROR_PERSON_ID_MISSING,
    ERROR_PERSON_NOT_FOUND,
    ERROR_RELATED_TITLES_LOAD,
    ERROR_RELATED_TITLES_PARSE,
    ERROR_REQUEST_OPTIONS_LOAD,
    ERROR_REQUEST_OPTIONS_PARSE,
    ERROR_REQUEST_TITLE,
    ERROR_REQUEST_TITLE_PARSE,
    ERROR_REQUESTS_LOAD,
    ERROR_REQUESTS_PARSE,
    ERROR_RETURNED_HTTP_VERIFY_URL,
    ERROR_SEARCH_RESULTS_PARSE,
    ERROR_SEARCH_TITLES,
    ERROR_SIGN_IN_HTTP,
    ERROR_SSL,
    ERROR_STUDIO_MOVIES_LOAD,
    ERROR_STUDIO_MOVIES_PARSE,
    ERROR_TIMEOUT,
    ERROR_TITLE_DETAILS_INCOMPLETE,
    ERROR_TITLE_DETAILS_LOAD,
    ERROR_TITLE_DETAILS_PARSE,
    ERROR_TITLE_ID_MISSING,
    ERROR_TITLE_NOT_FOUND,
    ERROR_UNKNOWN_HOST,
    ERROR_UNREACHABLE,
    ERROR_URL_INVALID,
    ERROR_URL_REQUIRED,
    ERROR_URL_SCHEME_REQUIRED,
    ERROR_USERNAME_REQUIRED,
    ERROR_VERIFY_SESSION_HTTP
}

/** Writing a null value removes the key. */
interface SeerrKeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun getInt(key: String): Int?
    fun putInt(key: String, value: Int?)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
}

interface SeerrHost {
    val preferences: SeerrKeyValueStore
    val secureStore: SeerrKeyValueStore

    fun timeoutConfig(): NetworkTimeoutConfig
    fun createHttpClient(timeouts: NetworkTimeoutConfig): HttpClient

    suspend fun text(message: SeerrMessage, vararg args: Any): String
    suspend fun connectionError(error: Exception): Exception
}
