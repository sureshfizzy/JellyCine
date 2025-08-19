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
        fields: String? = "ChildCount,RecursiveItemCount,EpisodeCount,Genres,CommunityRating,ProductionYear,Overview"
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
        fields: String? = "ChildCount,RecursiveItemCount,EpisodeCount,Genres,CommunityRating,ProductionYear,Overview"
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
            val accessToken = preferences[ACCESS_TOKEN_KEY]
            if (serverUrl != null && itemId.isNotEmpty()) {
                val params = mutableListOf<String>()
                accessToken?.let { params.add("api_key=$it") }
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
            val accessToken = preferences[ACCESS_TOKEN_KEY]
            if (serverUrl != null && itemId.isNotEmpty()) {
                val params = mutableListOf<String>()
                accessToken?.let { params.add("api_key=$it") }
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
            limit = limit,
            fields = "ChildCount,RecursiveItemCount,EpisodeCount,Genres,CommunityRating,ProductionYear,Overview"
        )
    }
    
    suspend fun getRecentlyAddedSeries(limit: Int = 10): Result<List<BaseItemDto>> {
        return getLatestItems(
            includeItemTypes = "Series",
            limit = limit,
            fields = "ChildCount,RecursiveItemCount,EpisodeCount,Genres,CommunityRating,ProductionYear,Overview"
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
        includeItemTypes: String? = null,
        recursive: Boolean? = true,
        sortBy: String? = "SortName",
        sortOrder: String? = "Ascending"
    ): Result<List<BaseItemDto>> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))
            val userId = getUserId() ?: return Result.failure(Exception("User ID not available"))

            val response = api.getGenres(
                userId = userId,
                parentId = parentId,
                includeItemTypes = includeItemTypes,
                recursive = recursive,
                sortBy = sortBy,
                sortOrder = sortOrder,
                enableTotalRecordCount = true,
                enableImages = false
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

    /**
     * Get filtered genres like the official Jellyfin web client
     * This filters out redundant individual genres when compound genres exist
     */
    suspend fun getFilteredGenres(
        parentId: String? = null,
        includeItemTypes: String? = null,
        recursive: Boolean? = true,
        sortBy: String? = "SortName",
        sortOrder: String? = "Ascending"
    ): Result<List<BaseItemDto>> {
        return try {
            val genresResult = getGenres(parentId, includeItemTypes, recursive, sortBy, sortOrder)

            genresResult.fold(
                onSuccess = { genres ->
                    val filteredGenres = filterRedundantGenres(genres, includeItemTypes)
                    Result.success(filteredGenres)
                },
                onFailure = { exception ->
                    Result.failure(exception)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Filter out redundant individual genres when compound genres exist
     * Based on how the official Jellyfin web client handles genre display
     */
    private fun filterRedundantGenres(genres: List<BaseItemDto>, includeItemTypes: String? = null): List<BaseItemDto> {
        val genreNames = genres.mapNotNull { it.name }.toSet()

        // Define compound genre patterns and their individual components
        // Based on common TMDB/TVDB genre patterns
        val compoundGenreMap = mapOf(
            "Action & Adventure" to setOf("Action", "Adventure"),
            "Sci-Fi & Fantasy" to setOf("Sci-Fi", "Science Fiction", "Fantasy", "Sci Fi", "SciFi"),
            "Crime & Mystery" to setOf("Crime", "Mystery"),
            "Comedy & Drama" to setOf("Comedy", "Drama"),
            "Horror & Thriller" to setOf("Horror", "Thriller"),
            "Romance & Drama" to setOf("Romance", "Drama"),
            "War & Politics" to setOf("War", "Politics"),
            "Kids & Family" to setOf("Kids", "Family", "Children"),
            "News & Documentary" to setOf("News", "Documentary"),
            "Reality & Talk Show" to setOf("Reality", "Talk Show", "Talk"),
            "Soap & Drama" to setOf("Soap", "Drama")
        )

        // Additional genre consolidation rules (prefer one over the other)
        val genreConsolidationMap = mapOf(
            "Talk" to setOf("Talk-Show", "Talk Show"),
            "Reality" to setOf("Reality TV", "Reality-TV"),
            "Mystery" to setOf("Thriller")
        )

        // Find which compound genres actually exist in the data
        val existingCompoundGenres = genreNames.filter { genreName ->
            compoundGenreMap.keys.any { compound ->
                genreName.equals(compound, ignoreCase = true)
            }
        }

        // Collect all individual genres that should be filtered out
        val genresToFilter = mutableSetOf<String>()
        existingCompoundGenres.forEach { compoundGenre ->
            compoundGenreMap.entries.find {
                it.key.equals(compoundGenre, ignoreCase = true)
            }?.value?.let { individuals ->
                genresToFilter.addAll(individuals)
            }
        }

        // Also handle any other " & " patterns dynamically
        val dynamicCompoundGenres = genreNames.filter {
            it.contains(" & ") && !compoundGenreMap.keys.any { compound ->
                it.equals(compound, ignoreCase = true)
            }
        }

        dynamicCompoundGenres.forEach { compound ->
            val parts = compound.split(" & ").map { it.trim() }
            genresToFilter.addAll(parts)
        }

        // Apply genre consolidation rules
        genreConsolidationMap.forEach { (preferredGenre, genresToMerge) ->
            // If the preferred genre exists, filter out the genres to merge
            if (genreNames.any { it.equals(preferredGenre, ignoreCase = true) }) {
                genresToFilter.addAll(genresToMerge)
            }
        }

        // Additional filtering for TV Shows - exclude Romance and Game Show
        val tvShowExcludedGenres = setOf("Romance", "Game Show", "Game-Show")
        val isTVShows = includeItemTypes?.contains("Series", ignoreCase = true) == true

        // Filter the genres
        return genres.filter { genre ->
            val genreName = genre.name ?: return@filter false

            // Exclude specific genres for TV Shows
            if (isTVShows && tvShowExcludedGenres.any { it.equals(genreName, ignoreCase = true) }) {
                return@filter false
            }

            // Always keep compound genres
            if (genreName.contains(" & ")) {
                true
            } else {
                // Keep individual genres only if they're not part of any compound genre
                !genresToFilter.any { filterGenre ->
                    genreName.equals(filterGenre, ignoreCase = true)
                }
            }
        }.sortedBy { it.name } // Sort alphabetically for consistent display
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

    suspend fun getAllItemsByGenre(
        genreId: String,
        includeItemTypes: String? = null,
        sortBy: String? = "SortName",
        sortOrder: String? = "Ascending"
    ): Result<List<BaseItemDto>> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))
            val userId = getUserId() ?: return Result.failure(Exception("User ID not available"))

            val response = api.getItemsByGenre(
                userId = userId,
                genreIds = genreId,
                includeItemTypes = includeItemTypes,
                recursive = true,
                limit = null,
                sortBy = sortBy,
                sortOrder = sortOrder,
                fields = "Genres,RecursiveItemCount,ChildCount,EpisodeCount,CommunityRating,CriticRating,ProductionYear,Overview"
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val items = body.items ?: emptyList()

                Result.success(items)
            } else {
                val errorMsg = "Failed to fetch all items by genreId '$genreId': ${response.code()} - ${response.message()}"

                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {

            Result.failure(Exception("Error fetching all items by genreId '$genreId': ${e.message}", e))
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

    suspend fun getSeasons(seriesId: String): Result<List<BaseItemDto>> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))
            val userId = getUserId() ?: return Result.failure(Exception("User ID not available"))

            val response = api.getSeasons(
                seriesId = seriesId,
                userId = userId,
                fields = "ChildCount,RecursiveItemCount,EpisodeCount"
            )

            if (response.isSuccessful && response.body() != null) {
                val queryResult = response.body()!!
                Result.success(queryResult.items ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch seasons: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEpisodes(
        seriesId: String,
        seasonId: String? = null,
        limit: Int? = null,
        startIndex: Int? = null
    ): Result<List<BaseItemDto>> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))
            val userId = getUserId() ?: return Result.failure(Exception("User ID not available"))

            val response = api.getEpisodes(
                seriesId = seriesId,
                userId = userId,
                seasonId = seasonId,
                fields = "Overview,MediaStreams,SeriesName,SeriesId,SeasonName,SeasonId",
                limit = limit,
                startIndex = startIndex
            )

            if (response.isSuccessful && response.body() != null) {
                val queryResult = response.body()!!
                Result.success(queryResult.items ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch episodes: ${response.code()} - ${response.message()}"))
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
        val accessToken = preferences[ACCESS_TOKEN_KEY]

        return if (serverUrl != null && userId != null) {
            val apiKeyParam = if (accessToken != null) "?api_key=$accessToken" else ""
            "${serverUrl.trimEnd('/')}/Users/$userId/Images/Primary$apiKeyParam"
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

    /**
     * Search for items using Jellyfin's search API
     */
    suspend fun searchItems(
        searchTerm: String,
        includeItemTypes: String? = "Movie,Series",
        limit: Int? = 50
    ): Result<List<BaseItemDto>> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))
            val userId = getUserId() ?: return Result.failure(Exception("User ID not available"))

            // Try searchTerm parameter first
            var response = api.searchItems(
                userId = userId,
                searchTerm = searchTerm,
                includeItemTypes = includeItemTypes,
                recursive = true,
                limit = limit,
                fields = "ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId,Genres,CommunityRating,ProductionYear,Overview"
            )

            // If searchTerm doesn't work, try nameStartsWith
            if (!response.isSuccessful || response.body()?.items?.isEmpty() == true) {
                response = api.searchItemsByName(
                    userId = userId,
                    nameStartsWith = searchTerm,
                    includeItemTypes = includeItemTypes,
                    recursive = true,
                    limit = limit,
                    fields = "ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId,Genres,CommunityRating,ProductionYear,Overview"
                )
            }

            if (response.isSuccessful && response.body() != null) {
                val queryResult = response.body()!!
                val items = queryResult.items ?: emptyList()
                Result.success(items)
            } else {
                Result.failure(Exception("Failed to search items: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Extension functions for BaseItemDto
/**
 * Extension function to get image URL for BaseItemDto
 * Returns the item ID which will be used by the image loader to construct the full URL
 */
fun BaseItemDto.getImageUrl(imageType: String = "Primary"): String {
    // Return the item ID - the LazyImageLoader will handle constructing the full URL
    return this.id ?: ""
}

/**
 * Get formatted runtime string from ticks
 */
fun BaseItemDto.getFormattedRuntime(): String {
    return runTimeTicks?.let { ticks ->
        val minutes = (ticks / 600000000).toInt()
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m"
    } ?: ""
}

/**
 * Get formatted year and genre string
 */
fun BaseItemDto.getYearAndGenre(): String {
    val year = productionYear?.toString() ?: "Unknown"
    val genre = genres?.firstOrNull() ?: "Unknown"
    return "$year • $genre"
}

/**
 * Get formatted rating string
 */
fun BaseItemDto.getFormattedRating(): String? {
    return communityRating?.let { rating ->
        String.format("%.1f", rating)
    }
}
