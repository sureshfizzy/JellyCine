package com.jellycine.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest

class SecureSessionStore(context: Context) : TokenStore {
    private val appContext = context.applicationContext
    private val prefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun getToken(serverId: String?): String? {
        if (serverId.isNullOrBlank()) return null
        return prefs.getString(tokenKey(serverId), null)?.takeIf { it.isNotBlank() }
    }

    override fun putToken(serverId: String, accessToken: String) {
        if (serverId.isBlank() || accessToken.isBlank()) return
        prefs.edit().putString(tokenKey(serverId), accessToken).apply()
    }

    override fun removeToken(serverId: String?) {
        if (serverId.isNullOrBlank()) return
        prefs.edit().remove(tokenKey(serverId)).apply()
    }

    override fun hasToken(serverId: String?): Boolean = !getToken(serverId).isNullOrBlank()

    private fun tokenKey(serverId: String): String = "token_${sha256(serverId)}"

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                append("%02x".format(byte.toInt() and 0xff))
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "secure_auth_store"
    }
}