package com.jellycine.data.security

import com.jellycine.data.network.canonicalServerUrlKey
import platform.Foundation.NSUserDefaults

class SecureSessionStore {

    private val defaults = NSUserDefaults.standardUserDefaults

    fun getToken(serverId: String?): String? {
        if (serverId.isNullOrBlank()) return null
        return defaults.stringForKey(tokenKey(serverId))
    }

    fun putToken(serverId: String, accessToken: String) {
        if (serverId.isBlank() || accessToken.isBlank()) return
        defaults.setObject(accessToken, tokenKey(serverId))
        defaults.synchronize()
    }

    fun removeToken(serverId: String?) {
        if (serverId.isNullOrBlank()) return
        defaults.removeObjectForKey(tokenKey(serverId))
        defaults.synchronize()
    }

    fun hasToken(serverId: String?): Boolean = !getToken(serverId).isNullOrBlank()

    private fun tokenKey(serverId: String): String = "token_${sha256(serverId)}"

    private fun sha256(value: String): String {
        return value.hashCode().toString()
    }

    companion object {
        private const val SERVICE_NAME = "com.jellycine.app.secure_auth_store"
    }
}