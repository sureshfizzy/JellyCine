package com.jellycine.app.data.api

import com.jellycine.data.network.NetworkModule

object JellyfinApiEndpoints {

    private fun endpoint(baseUrl: String, path: String): String {
        return "${normalizeBaseUrl(baseUrl)}/$path"
    }

    // System endpoints
    fun getSystemInfo(baseUrl: String) = endpoint(baseUrl, "System/Info/Public")
    fun getServerConfiguration(baseUrl: String) = endpoint(baseUrl, "System/Configuration")
    fun pingServer(baseUrl: String) = endpoint(baseUrl, "System/Ping")

    // Authentication
    fun authenticateByName(baseUrl: String) = endpoint(baseUrl, "Users/AuthenticateByName")
    fun authenticateWithQuickConnect(baseUrl: String) = endpoint(baseUrl, "Users/AuthenticateWithQuickConnect")
    fun logout(baseUrl: String) = endpoint(baseUrl, "Sessions/Logout")

    // Users
    fun getUsers(baseUrl: String) = endpoint(baseUrl, "Users")
    fun getUserById(baseUrl: String, userId: String) = endpoint(baseUrl, "Users/$userId")
    fun getCurrentUser(baseUrl: String) = endpoint(baseUrl, "Users/Me")
    fun updateUserConfiguration(baseUrl: String, userId: String) = endpoint(baseUrl, "Users/$userId/Configuration")

    // User images
    fun getUserProfileImage(
        baseUrl: String,
        userId: String,
        width: Int? = null,
        height: Int? = null,
        quality: Int? = null
    ): String {
        val params = mutableListOf<String>()
        width?.let { params.add("width=$it") }
        height?.let { params.add("height=$it") }
        quality?.let { params.add("quality=$it") }
        val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        return endpoint(baseUrl, "Users/$userId/Images/Primary$queryString")
    }

    fun getUserBackdropImage(
        baseUrl: String,
        userId: String,
        width: Int? = null,
        height: Int? = null
    ): String {
        val params = mutableListOf<String>()
        width?.let { params.add("width=$it") }
        height?.let { params.add("height=$it") }
        val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        return endpoint(baseUrl, "Users/$userId/Images/Backdrop$queryString")
    }

    fun uploadUserProfileImage(baseUrl: String, userId: String) = endpoint(baseUrl, "Users/$userId/Images/Primary")
    fun deleteUserProfileImage(baseUrl: String, userId: String) = endpoint(baseUrl, "Users/$userId/Images/Primary")

    // Library & items
    fun getUserItems(
        baseUrl: String,
        userId: String,
        parentId: String? = null,
        includeItemTypes: String? = null,
        recursive: Boolean? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
        limit: Int? = null,
        startIndex: Int? = null
    ): String {
        val params = mutableListOf<String>()
        parentId?.let { params.add("parentId=$it") }
        includeItemTypes?.let { params.add("includeItemTypes=$it") }
        recursive?.let { params.add("recursive=$it") }
        sortBy?.let { params.add("sortBy=$it") }
        sortOrder?.let { params.add("sortOrder=$it") }
        limit?.let { params.add("limit=$it") }
        startIndex?.let { params.add("startIndex=$it") }
        val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        return endpoint(baseUrl, "Users/$userId/Items$queryString")
    }

    fun getItemById(baseUrl: String, userId: String, itemId: String) = endpoint(baseUrl, "Users/$userId/Items/$itemId")

    fun getLatestItems(
        baseUrl: String,
        userId: String,
        parentId: String? = null,
        includeItemTypes: String? = null,
        limit: Int? = null,
        fields: String? = null
    ): String {
        val params = mutableListOf<String>()
        parentId?.let { params.add("parentId=$it") }
        includeItemTypes?.let { params.add("includeItemTypes=$it") }
        limit?.let { params.add("limit=$it") }
        fields?.let { params.add("fields=$it") }
        val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        return endpoint(baseUrl, "Users/$userId/Items/Latest$queryString")
    }

    fun getResumeItems(
        baseUrl: String,
        userId: String,
        parentId: String? = null,
        includeItemTypes: String? = null,
        limit: Int? = null,
        startIndex: Int? = null,
        fields: String? = null
    ): String {
        val params = mutableListOf<String>()
        parentId?.let { params.add("parentId=$it") }
        includeItemTypes?.let { params.add("includeItemTypes=$it") }
        limit?.let { params.add("limit=$it") }
        startIndex?.let { params.add("startIndex=$it") }
        params.add("recursive=true")
        params.add("sortBy=DatePlayed")
        params.add("sortOrder=Descending")
        fields?.let { params.add("fields=$it") }
        val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        return endpoint(baseUrl, "Users/$userId/Items/Resume$queryString")
    }

    // Media images
    fun getItemPrimaryImage(
        baseUrl: String,
        itemId: String,
        width: Int? = null,
        height: Int? = null,
        quality: Int? = null
    ): String {
        val params = mutableListOf<String>()
        width?.let { params.add("width=$it") }
        height?.let { params.add("height=$it") }
        quality?.let { params.add("quality=$it") }
        val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        return endpoint(baseUrl, "Items/$itemId/Images/Primary$queryString")
    }

    fun getItemBackdropImage(
        baseUrl: String,
        itemId: String,
        imageIndex: Int = 0,
        width: Int? = null,
        height: Int? = null,
        quality: Int? = null
    ): String {
        val params = mutableListOf<String>()
        width?.let { params.add("width=$it") }
        height?.let { params.add("height=$it") }
        quality?.let { params.add("quality=$it") }
        val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        return endpoint(baseUrl, "Items/$itemId/Images/Backdrop/$imageIndex$queryString")
    }

    fun getItemLogoImage(
        baseUrl: String,
        itemId: String,
        width: Int? = null,
        height: Int? = null
    ): String {
        val params = mutableListOf<String>()
        width?.let { params.add("width=$it") }
        height?.let { params.add("height=$it") }
        val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        return endpoint(baseUrl, "Items/$itemId/Images/Logo$queryString")
    }

    // Playback
    fun getPlaybackInfo(
        baseUrl: String,
        itemId: String,
        userId: String,
        maxStreamingBitrate: Int? = null,
        startTimeTicks: Long? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null
    ): String {
        val params = mutableListOf<String>()
        params.add("userId=$userId")
        maxStreamingBitrate?.let { params.add("maxStreamingBitrate=$it") }
        startTimeTicks?.let { params.add("startTimeTicks=$it") }
        audioStreamIndex?.let { params.add("audioStreamIndex=$it") }
        subtitleStreamIndex?.let { params.add("subtitleStreamIndex=$it") }
        val queryString = "?${params.joinToString("&")}"
        return endpoint(baseUrl, "Items/$itemId/PlaybackInfo$queryString")
    }

    fun reportPlaybackStart(baseUrl: String) = endpoint(baseUrl, "Sessions/Playing")
    fun reportPlaybackProgress(baseUrl: String) = endpoint(baseUrl, "Sessions/Playing/Progress")
    fun reportPlaybackStopped(baseUrl: String) = endpoint(baseUrl, "Sessions/Playing/Stopped")

    // Search
    fun searchHints(
        baseUrl: String,
        userId: String,
        searchTerm: String,
        includeItemTypes: String? = null,
        limit: Int? = null,
        includeStudios: Boolean? = null,
        includeGenres: Boolean? = null,
        includePeople: Boolean? = null,
        includeMedia: Boolean? = null,
        includeArtists: Boolean? = null
    ): String {
        val params = mutableListOf<String>()
        params.add("userId=$userId")
        params.add("searchTerm=$searchTerm")
        includeItemTypes?.let { params.add("includeItemTypes=$it") }
        limit?.let { params.add("limit=$it") }
        includeStudios?.let { params.add("includeStudios=$it") }
        includeGenres?.let { params.add("includeGenres=$it") }
        includePeople?.let { params.add("includePeople=$it") }
        includeMedia?.let { params.add("includeMedia=$it") }
        includeArtists?.let { params.add("includeArtists=$it") }
        val queryString = "?${params.joinToString("&")}"
        return endpoint(baseUrl, "Search/Hints$queryString")
    }

    // Library views
    fun getUserViews(baseUrl: String, userId: String) = endpoint(baseUrl, "Users/$userId/Views")

    fun getGenres(
        baseUrl: String,
        userId: String,
        parentId: String? = null,
        includeItemTypes: String? = null
    ): String {
        val params = mutableListOf<String>()
        params.add("userId=$userId")
        parentId?.let { params.add("parentId=$it") }
        includeItemTypes?.let { params.add("includeItemTypes=$it") }
        val queryString = "?${params.joinToString("&")}"
        return endpoint(baseUrl, "Genres$queryString")
    }

    fun getStudios(
        baseUrl: String,
        userId: String,
        parentId: String? = null,
        includeItemTypes: String? = null
    ): String {
        val params = mutableListOf<String>()
        params.add("userId=$userId")
        parentId?.let { params.add("parentId=$it") }
        includeItemTypes?.let { params.add("includeItemTypes=$it") }
        val queryString = "?${params.joinToString("&")}"
        return endpoint(baseUrl, "Studios$queryString")
    }

    // Favorites & ratings
    fun markAsFavorite(baseUrl: String, userId: String, itemId: String) = endpoint(baseUrl, "Users/$userId/FavoriteItems/$itemId")
    fun unmarkAsFavorite(baseUrl: String, userId: String, itemId: String) = endpoint(baseUrl, "Users/$userId/FavoriteItems/$itemId")
    fun updateItemRating(baseUrl: String, userId: String, itemId: String, likes: Boolean) = endpoint(baseUrl, "Users/$userId/Items/$itemId/Rating?likes=$likes")
    fun deleteItemRating(baseUrl: String, userId: String, itemId: String) = endpoint(baseUrl, "Users/$userId/Items/$itemId/Rating")

    // Utility
    fun getStreamUrl(
        baseUrl: String,
        itemId: String,
        container: String? = null,
        audioCodec: String? = null,
        videoCodec: String? = null,
        audioBitRate: Int? = null,
        videoBitRate: Int? = null,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        startTimeTicks: Long? = null,
        deviceId: String? = null,
        mediaSourceId: String? = null,
        playSessionId: String? = null
    ): String {
        val params = mutableListOf<String>()
        container?.let { params.add("container=$it") }
        audioCodec?.let { params.add("audioCodec=$it") }
        videoCodec?.let { params.add("videoCodec=$it") }
        audioBitRate?.let { params.add("audioBitRate=$it") }
        videoBitRate?.let { params.add("videoBitRate=$it") }
        maxWidth?.let { params.add("maxWidth=$it") }
        maxHeight?.let { params.add("maxHeight=$it") }
        startTimeTicks?.let { params.add("startTimeTicks=$it") }
        deviceId?.let { params.add("deviceId=$it") }
        mediaSourceId?.let { params.add("mediaSourceId=$it") }
        playSessionId?.let { params.add("playSessionId=$it") }
        val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        return endpoint(baseUrl, "Videos/$itemId/stream$queryString")
    }

    fun validateBaseUrl(baseUrl: String) = baseUrl.startsWith("http://") || baseUrl.startsWith("https://")
    fun normalizeBaseUrl(baseUrl: String) = NetworkModule.trimTrailingSlash(baseUrl)
}
