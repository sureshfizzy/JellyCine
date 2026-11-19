package com.jellycine.data.repository

import com.jellycine.data.network.JellyCineJson
import com.jellycine.data.preferences.NetworkPreferences
import com.jellycine.data.preferences.NetworkTimeoutConfig
import com.jellycine.data.security.SecureSessionStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import platform.Foundation.NSUserDefaults

class SeerrPlatform(
    secureSessionStore: SecureSessionStore,
    private val messages: suspend (SeerrMessage, Array<out Any>) -> String
) : SeerrHost {
    private val networkPreferences = NetworkPreferences()

    override val preferences: SeerrKeyValueStore = UserDefaultsStore(NSUserDefaults.standardUserDefaults)

    override val secureStore: SeerrKeyValueStore = KeychainStore(secureSessionStore)

    override fun timeoutConfig(): NetworkTimeoutConfig = networkPreferences.getTimeoutConfig()

    override fun createHttpClient(timeouts: NetworkTimeoutConfig): HttpClient = HttpClient(Darwin) {
        expectSuccess = false
        engine {
            configureRequest {
                setTimeoutInterval(timeouts.requestTimeoutMs / 1000.0)
            }
            configureSession {
                HTTPShouldSetCookies = false
            }
        }
        install(ContentNegotiation) {
            json(JellyCineJson)
        }
    }

    override suspend fun text(message: SeerrMessage, vararg args: Any): String = messages(message, args)

    override suspend fun connectionError(error: Exception): Exception {
        val lower = error.message.orEmpty().lowercase()
        val message = when {
            "timed out" in lower || "code=-1001" in lower -> SeerrMessage.ERROR_TIMEOUT
            "hostname could not be found" in lower || "code=-1003" in lower || "code=-1009" in lower ->
                SeerrMessage.ERROR_UNKNOWN_HOST
            "could not connect" in lower || "code=-1004" in lower -> SeerrMessage.ERROR_CONNECT
            "certificate" in lower || "code=-1202" in lower -> SeerrMessage.ERROR_CERTIFICATE
            "secure connection" in lower || "code=-1200" in lower -> SeerrMessage.ERROR_SSL
            else -> null
        }
        return Exception(message?.let { text(it) } ?: error.message ?: text(SeerrMessage.ERROR_CONNECT_GENERIC))
    }
}

private class UserDefaultsStore(private val defaults: NSUserDefaults) : SeerrKeyValueStore {
    override fun getString(key: String): String? = defaults.stringForKey(storageKey(key))

    override fun putString(key: String, value: String?) {
        if (value == null) remove(key) else defaults.setObject(value, storageKey(key))
    }

    override fun getInt(key: String): Int? =
        defaults.objectForKey(storageKey(key))?.let { defaults.integerForKey(storageKey(key)).toInt() }

    override fun putInt(key: String, value: Int?) {
        if (value == null) remove(key) else defaults.setInteger(value.toLong(), storageKey(key))
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(storageKey(key)) == null) default else defaults.boolForKey(storageKey(key))

    override fun putBoolean(key: String, value: Boolean) {
        defaults.setBool(value, storageKey(key))
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey(storageKey(key))
    }

    private fun storageKey(key: String) = "seerr_$key"
}

private class KeychainStore(private val store: SecureSessionStore) : SeerrKeyValueStore {
    override fun getString(key: String): String? = store.getSecret(key)

    override fun putString(key: String, value: String?) {
        if (value == null) remove(key) else store.putSecret(key, value)
    }

    override fun getInt(key: String): Int? = store.getSecret(key)?.toIntOrNull()

    override fun putInt(key: String, value: Int?) {
        if (value == null) remove(key) else store.putSecret(key, value.toString())
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        store.getSecret(key)?.toBooleanStrictOrNull() ?: default

    override fun putBoolean(key: String, value: Boolean) {
        store.putSecret(key, value.toString())
    }

    override fun remove(key: String) {
        store.removeSecret(key)
    }
}
