package com.jellycine.data.security

interface TokenStore {
    fun getToken(serverId: String?): String?
    fun putToken(serverId: String, accessToken: String)
    fun removeToken(serverId: String?)
    fun hasToken(serverId: String?): Boolean
}
