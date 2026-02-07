package com.jellycine.app.data.api

object EmbyApiEndpoints {

    private fun embyBaseUrl(baseUrl: String): String {
        val normalized = baseUrl.trimEnd('/')
        return if (normalized.endsWith("/emby", ignoreCase = true)) normalized else "$normalized/emby"
    }

    fun getSystemInfo(baseUrl: String): String = JellyfinApiEndpoints.getSystemInfo(embyBaseUrl(baseUrl))
    fun authenticateByName(baseUrl: String): String = JellyfinApiEndpoints.authenticateByName(embyBaseUrl(baseUrl))
    fun getCurrentUser(baseUrl: String): String = JellyfinApiEndpoints.getCurrentUser(embyBaseUrl(baseUrl))
    fun getUserById(baseUrl: String, userId: String): String = JellyfinApiEndpoints.getUserById(embyBaseUrl(baseUrl), userId)
    fun getUserViews(baseUrl: String, userId: String): String = JellyfinApiEndpoints.getUserViews(embyBaseUrl(baseUrl), userId)
    fun getPlaybackInfo(baseUrl: String, itemId: String, userId: String): String =
        JellyfinApiEndpoints.getPlaybackInfo(embyBaseUrl(baseUrl), itemId, userId)
    fun getStreamUrl(baseUrl: String, itemId: String): String =
        JellyfinApiEndpoints.getStreamUrl(embyBaseUrl(baseUrl), itemId)
}
