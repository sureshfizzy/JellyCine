package com.jellycine.data.security

import com.jellycine.data.network.canonicalServerUrlKey

object AuthSessionIds {
    fun buildServerId(serverUrl: String, userId: String): String {
        return "${canonicalServerUrlKey(serverUrl)}|${userId.trim()}"
    }
}
