package com.jellycine.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jellycine.data.api.JellyfinApi
import com.jellycine.data.datastore.DataStoreProvider
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.QueryResult
import com.jellycine.data.model.UserDto
import com.jellycine.data.network.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class MediaRepository(private val context: Context) {

    private val dataStore: DataStore<Preferences> = DataStoreProvider.getDataStore(context)
    
    companion object {
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
    }
    
    private suspend fun getApi(): JellyfinApi? {
        val preferences = dataStore.data.first()
        val serverUrl = preferences[SERVER_URL_KEY]
        val accessToken = preferences[ACCESS_TOKEN_KEY]

        return if (serverUrl != null) {
            NetworkModule.createJellyfinApi(serverUrl, accessToken)
        } else {
            null
        }
    }

    private suspend fun getUserId(): String? {
        val preferences = dataStore.data.first()
        return preferences[USER_ID_KEY]
    }
    
    suspend fun getLatestItems(
        parentId: String? = null,
        includeItemTypes: String? = "Movie,Series",
        limit: Int? = 20,
        fields: String? = "ChildCount,RecursiveItemCount,EpisodeCount"
    ): Result<List<BaseItemDto>> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))
            val userId = getUserId() ?: return Result.failure(Exception("User ID not available"))

            val response = api.getLatestItems(
                userId = userId,
                parentId = parentId,
                includeItemTypes = includeItemTypes,
                limit = limit,
                fields = fields
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch latest items: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getItemById(itemId: String): Result<BaseItemDto> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))
            val userId = getUserId() ?: return Result.failure(Exception("User ID not available"))

            val response = api.getItemById(
                userId = userId,
                itemId = itemId,
                fields = "People,Studios,Genres,Overview,ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId"
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch item: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserItems(
        parentId: String? = null,
        includeItemTypes: String? = null,
        recursive: Boolean? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
        limit: Int? = null,
        startIndex: Int? = null,
        fields: String? = "ChildCount,RecursiveItemCount,EpisodeCount"
    ): Result<QueryResult<BaseItemDto>> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))
            val userId = getUserId() ?: return Result.failure(Exception("User ID not available"))

            val response = api.getUserItems(
                userId = userId,
                parentId = parentId,
                includeItemTypes = includeItemTypes,
                recursive = recursive,
                sortBy = sortBy,
                sortOrder = sortOrder,
                limit = limit,
                startIndex = startIndex,
                fields = fields
            )

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch user items: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getUserViews(): Result<QueryResult<BaseItemDto>> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))
            val userId = getUserId() ?: return Result.failure(Exception("User ID not available"))
            
            val response = api.getUserViews(userId)
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch user views: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getImageUrl(
        itemId: String,
        imageType: String = "Primary",
        width: Int? = null,
        height: Int? = null,
        quality: Int? = 90
    ): Flow<String?> {
        return dataStore.data.map { preferences ->
            val serverUrl = preferences[SERVER_URL_KEY]
            if (serverUrl != null && itemId.isNotEmpty()) {
                val params = mutableListOf<String>()
                width?.let { params.add("width=$it") }
                height?.let { params.add("height=$it") }
                quality?.let { params.add("quality=$it") }
                val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
                "${serverUrl.trimEnd('/')}/Items/$itemId/Images/$imageType$queryString"
            } else {
                null
            }
        }
    }
    
    fun getBackdropImageUrl(
        itemId: String,
        imageIndex: Int = 0,
        width: Int? = null,
        height: Int? = null,
        quality: Int? = 90
    ): Flow<String?> {
        return dataStore.data.map { preferences ->
            val serverUrl = preferences[SERVER_URL_KEY]
            if (serverUrl != null && itemId.isNotEmpty()) {
                val params = mutableListOf<String>()
                width?.let { params.add("width=$it") }
                height?.let { params.add("height=$it") }
                quality?.let { params.add("quality=$it") }
                val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
                "${serverUrl.trimEnd('/')}/Items/$itemId/Images/Backdrop/$imageIndex$queryString"
            } else {
                null
            }
        }
    }
    
    suspend fun getRecentlyAddedMovies(limit: Int = 10): Result<List<BaseItemDto>> {
        return getLatestItems(
            includeItemTypes = "Movie",
            limit = limit
        )
    }
    
    suspend fun getRecentlyAddedSeries(limit: Int = 10): Result<List<BaseItemDto>> {
        return getLatestItems(
            includeItemTypes = "Series",
            limit = limit
        )
    }
    
    suspend fun getRecentlyAddedEpisodes(limit: Int = 10): Result<List<BaseItemDto>> {
        return getLatestItems(
            includeItemTypes = "Episode",
            limit = limit
        )
    }

    suspend fun getGenres(
        parentId: String? = null,
        includeItemTypes: String? = null
    ): Result<List<BaseItemDto>> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))
            val userId = getUserId() ?: return Result.failure(Exception("User ID not available"))

            val response = api.getGenres(
                userId = userId,
                parentId = parentId,
                includeItemTypes = includeItemTypes
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val items = body.items ?: emptyList()

                Result.success(items)
            } else {
                val errorMsg = "Failed to fetch genres: ${response.code()} - ${response.message()}"

                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {

            Result.failure(e)
        }
    }

    suspend fun getItemsByGenre(
        genreId: String,
        includeItemTypes: String? = null,
        limit: Int? = 20
    ): Result<List<BaseItemDto>> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))
            val userId = getUserId() ?: return Result.failure(Exception("User ID not available"))

            val response = api.getItemsByGenre(
                userId = userId,
                genreIds = genreId,
                includeItemTypes = includeItemTypes,
                recursive = true,
                limit = limit,
                sortBy = "Random",
                sortOrder = null,
                fields = "Genres,RecursiveItemCount,ChildCount,EpisodeCount"
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val items = body.items ?: emptyList()

                Result.success(items)
            } else {
                val errorMsg = "Failed to fetch items by genreId '$genreId': ${response.code()} - ${response.message()}"

                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {

            Result.failure(Exception("Error fetching items by genreId '$genreId': ${e.message}", e))
        }
    }

    suspend fun getResumeItems(
        parentId: String? = null,
        includeItemTypes: String? = "Movie,Series,Episode",
        limit: Int? = 20,
        fields: String? = "ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId"
    ): Result<List<BaseItemDto>> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))
            val userId = getUserId() ?: return Result.failure(Exception("User ID not available"))

            val response = api.getResumeItems(
                userId = userId,
                parentId = parentId,
                includeItemTypes = includeItemTypes,
                limit = limit,
                filters = "IsResumable",
                recursive = true,
                sortBy = "DatePlayed",
                sortOrder = "Descending",
                fields = fields
            )

            if (response.isSuccessful && response.body() != null) {
                val queryResult = response.body()!!
                Result.success(queryResult.items ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch resume items: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentUser(): Result<UserDto> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))
            val userId = getUserId() ?: return Result.failure(Exception("User ID not available"))

            val response = api.getUserById(userId)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch user info: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserProfileImageUrl(): String? {
        val preferences = dataStore.data.first()
        val serverUrl = preferences[SERVER_URL_KEY]
        val userId = preferences[USER_ID_KEY]

        return if (serverUrl != null && userId != null) {
            "${serverUrl.trimEnd('/')}/Users/$userId/Images/Primary"
        } else {
            null
        }
    }

    // Player-related methods

    /**
     * Get playback information for a media item
     */
    suspend fun getPlaybackInfo(itemId: String): Result<com.jellycine.data.model.PlaybackInfoResponse> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))
            val userId = getUserId() ?: return Result.failure(Exception("User ID not available"))

            val response = api.getPlaybackInfo(itemId, userId)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch playback info: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get direct streaming URL for a media item
     */
    suspend fun getStreamingUrl(itemId: String): Result<String> {
        return try {
            val preferences = dataStore.data.first()
            val serverUrl = preferences[SERVER_URL_KEY]
            val accessToken = preferences[ACCESS_TOKEN_KEY]

            if (serverUrl == null) {
                return Result.failure(Exception("Server URL not available"))
            }

            // Get playback info first to determine the best streaming method
            val playbackInfoResult = getPlaybackInfo(itemId)
            if (playbackInfoResult.isFailure) {
                return Result.failure(playbackInfoResult.exceptionOrNull() ?: Exception("Failed to get playback info"))
            }

            val playbackInfo = playbackInfoResult.getOrNull()
            val mediaSource = playbackInfo?.mediaSources?.firstOrNull()

            if (mediaSource == null) {
                return Result.failure(Exception("No media source available"))
            }

            // Build streaming URL
            val streamingUrl = if (mediaSource.supportsDirectPlay == true) {
                // Direct play
                "${serverUrl.trimEnd('/')}/Videos/$itemId/stream?static=true&mediaSourceId=${mediaSource.id}&api_key=$accessToken"
            } else if (mediaSource.supportsDirectStream == true) {
                // Direct stream
                "${serverUrl.trimEnd('/')}/Videos/$itemId/stream?static=true&mediaSourceId=${mediaSource.id}&api_key=$accessToken"
            } else {
                // Transcoding
                mediaSource.transcodingUrl?.let { url ->
                    if (url.startsWith("http")) url else "${serverUrl.trimEnd('/')}$url"
                } ?: "${serverUrl.trimEnd('/')}/Videos/$itemId/stream?api_key=$accessToken"
            }

            Result.success(streamingUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get available audio tracks for a media item
     */
    suspend fun getAudioTracks(itemId: String): Result<List<com.jellycine.data.model.MediaStream>> {
        return try {
            val playbackInfoResult = getPlaybackInfo(itemId)
            if (playbackInfoResult.isFailure) {
                return Result.failure(playbackInfoResult.exceptionOrNull() ?: Exception("Failed to get playback info"))
            }

            val playbackInfo = playbackInfoResult.getOrNull()
            val mediaSource = playbackInfo?.mediaSources?.firstOrNull()
            val audioStreams = mediaSource?.mediaStreams?.filter { it.type == "Audio" } ?: emptyList()

            Result.success(audioStreams)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get available video tracks for a media item
     */
    suspend fun getVideoTracks(itemId: String): Result<List<com.jellycine.data.model.MediaStream>> {
        return try {
            val playbackInfoResult = getPlaybackInfo(itemId)
            if (playbackInfoResult.isFailure) {
                return Result.failure(playbackInfoResult.exceptionOrNull() ?: Exception("Failed to get playback info"))
            }

            val playbackInfo = playbackInfoResult.getOrNull()
            val mediaSource = playbackInfo?.mediaSources?.firstOrNull()
            val videoStreams = mediaSource?.mediaStreams?.filter { it.type == "Video" } ?: emptyList()

            Result.success(videoStreams)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get available subtitle tracks for a media item
     */
    suspend fun getSubtitleTracks(itemId: String): Result<List<com.jellycine.data.model.MediaStream>> {
        return try {
            val playbackInfoResult = getPlaybackInfo(itemId)
            if (playbackInfoResult.isFailure) {
                return Result.failure(playbackInfoResult.exceptionOrNull() ?: Exception("Failed to get playback info"))
            }

            val playbackInfo = playbackInfoResult.getOrNull()
            val mediaSource = playbackInfo?.mediaSources?.firstOrNull()
            val subtitleStreams = mediaSource?.mediaStreams?.filter { it.type == "Subtitle" } ?: emptyList()

            Result.success(subtitleStreams)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}