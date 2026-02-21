package com.jellycine.data.repository

import android.content.Context
import android.util.AtomicFile
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.jellycine.data.api.MediaServerApi
import com.jellycine.data.datastore.DataStoreProvider
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.QueryResult
import com.jellycine.data.model.UserDto
import com.jellycine.data.network.NetworkModule
import com.jellycine.data.preferences.NetworkPreferences
import com.jellycine.data.preferences.NetworkTimeoutConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MediaRepository(private val context: Context) {

    private val dataStore: DataStore<Preferences> = DataStoreProvider.getDataStore(context)
    private val networkPreferences = NetworkPreferences(context)
    
    companion object {
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private val SERVER_TYPE_KEY = stringPreferencesKey("server_type")
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
    }

    private data class ImageAuthState(
        val serverUrl: String?,
        val accessToken: String?
    )

    private data class ApiSession(
        val api: MediaServerApi,
        val userId: String
    )

    private data class SessionConfig(
        val serverUrl: String,
        val serverTypeRaw: String?,
        val serverType: NetworkModule.ServerType?,
        val accessToken: String?,
        val userId: String,
        val timeoutConfig: NetworkTimeoutConfig
    )

    data class HomeLibrarySectionData(
        val library: BaseItemDto,
        val items: List<BaseItemDto>
    )

    data class PersistedHomeSnapshot(
        val snapshotKey: String,
        val updatedAt: Long,
        val featuredHomeItems: List<BaseItemDto>,
        val continueWatchingItems: List<BaseItemDto>,
        val homeLibrarySections: List<HomeLibrarySectionData>,
        val myMediaLibraries: List<BaseItemDto>? = null
    )

    data class ItemDownloadRequest(
        val itemId: String,
        val displayName: String,
        val downloadUrl: String,
        val authToken: String?,
        val fileExtension: String?
    )

    @Volatile
    private var cachedSession: ApiSession? = null

    @Volatile
    private var cachedSessionKey: String? = null

    @Volatile
    private var cachedSessionConfig: SessionConfig? = null

    @Volatile
    private var cachedSessionConfigAt: Long = 0L

    private val sessionConfigTtlMs = 1500L
    private val imageAuthCacheTtlMs = 1500L

    @Volatile
    private var cachedImageAuthState: ImageAuthState? = null

    @Volatile
    private var cachedImageAuthAt: Long = 0L

    private val gson = Gson()
    private val homeSnapshotFileName = "JellyCineSnapshot.json"
    private val homeSnapshotMutex = Mutex()

    private val imageAuthStateFlow: Flow<ImageAuthState> = dataStore.data
        .map { preferences ->
            ImageAuthState(
                serverUrl = preferences[SERVER_URL_KEY],
                accessToken = preferences[ACCESS_TOKEN_KEY]
            )
        }
        .distinctUntilChanged()
    
    private suspend fun getApi(): MediaServerApi? = getApiSession()?.api

    private suspend fun getUserId(): String? = getApiSession()?.userId

    private suspend fun getSessionConfig(): SessionConfig? {
        val now = System.currentTimeMillis()
        cachedSessionConfig?.let { config ->
            if (now - cachedSessionConfigAt < sessionConfigTtlMs) {
                return config
            }
        }

        val preferences = dataStore.data.first()
        val serverUrl = preferences[SERVER_URL_KEY] ?: return null
        val userId = preferences[USER_ID_KEY] ?: return null
        val serverTypeRaw = preferences[SERVER_TYPE_KEY]
        val serverType = serverTypeRaw?.let {
            runCatching { NetworkModule.ServerType.valueOf(it) }.getOrNull()
        }
        val accessToken = preferences[ACCESS_TOKEN_KEY]

        return SessionConfig(
            serverUrl = serverUrl,
            serverTypeRaw = serverTypeRaw,
            serverType = serverType,
            accessToken = accessToken,
            userId = userId,
            timeoutConfig = networkPreferences.getTimeoutConfig()
        ).also {
            cachedSessionConfig = it
            cachedSessionConfigAt = now
        }
    }

    private suspend fun getApiSession(): ApiSession? {
        val config = getSessionConfig() ?: return null
        val newSessionKey = buildString {
            append(config.serverUrl)
            append("|")
            append(config.serverTypeRaw ?: "")
            append("|")
            append(config.accessToken ?: "")
            append("|")
            append(config.userId)
            append("|")
            append(config.timeoutConfig.requestTimeoutMs)
            append("|")
            append(config.timeoutConfig.connectionTimeoutMs)
            append("|")
            append(config.timeoutConfig.socketTimeoutMs)
        }

        cachedSession?.let { session ->
            if (cachedSessionKey == newSessionKey) {
                return session
            }
        }

        synchronized(this) {
            cachedSession?.let { session ->
                if (cachedSessionKey == newSessionKey) {
                    return session
                }
            }

            val api = NetworkModule.createMediaServerApi(
                baseUrl = config.serverUrl,
                accessToken = config.accessToken,
                serverType = config.serverType,
                storageDir = context.filesDir,
                timeoutConfig = config.timeoutConfig
            )

            val session = ApiSession(api = api, userId = config.userId)
            cachedSession = session
            cachedSessionKey = newSessionKey
            return session
        }
    }

    private fun getHomeSnapshotFile() = context.filesDir.resolve(homeSnapshotFileName)

    private fun buildSnapshotKey(config: SessionConfig): String {
        return "${config.serverUrl.trimEnd('/')}|${config.userId}"
    }

    private fun HomeSnapshot(file: java.io.File): PersistedHomeSnapshot? {
        if (!file.exists()) return null
        return runCatching {
            val rawJson = file.readText()
            val jsonElement = JsonParser.parseString(rawJson)
            gson.fromJson(jsonElement, PersistedHomeSnapshot::class.java)
        }.getOrElse {
            file.delete()
            null
        }
    }

    private fun writePersistedHomeSnapshotAtomically(
        file: java.io.File,
        snapshot: PersistedHomeSnapshot
    ) {
        val atomicFile = AtomicFile(file)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(gson.toJson(snapshot).toByteArray(StandardCharsets.UTF_8))
            stream.flush()
            atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            stream?.let { atomicFile.failWrite(it) }
            throw error
        }
    }

    suspend fun loadPersistedHomeSnapshot(
        maxAgeMs: Long? = null
    ): PersistedHomeSnapshot? {
        val config = getSessionConfig() ?: return null
        val expectedSnapshotKey = buildSnapshotKey(config)
        return withContext(Dispatchers.IO) {
            homeSnapshotMutex.withLock {
                runCatching {
                    val file = getHomeSnapshotFile()
                    val parsed = HomeSnapshot(file) ?: return@runCatching null
                    val isExpired = maxAgeMs?.let { ttl ->
                        System.currentTimeMillis() - parsed.updatedAt > ttl
                    } ?: false
                    val keyMismatch = parsed.snapshotKey != expectedSnapshotKey
                    if (isExpired || keyMismatch) null else parsed
                }.getOrNull()
            }
        }
    }

    suspend fun persistHomeSnapshot(
        featuredHomeItems: List<BaseItemDto>? = null,
        continueWatchingItems: List<BaseItemDto>? = null,
        homeLibrarySections: List<HomeLibrarySectionData>? = null,
        myMediaLibraries: List<BaseItemDto>? = null
    ) {
        val config = getSessionConfig() ?: return
        val snapshotKey = buildSnapshotKey(config)
        withContext(Dispatchers.IO) {
            homeSnapshotMutex.withLock {
                runCatching {
                    val file = getHomeSnapshotFile()
                    val existing = HomeSnapshot(file)
                    val sameSessionSnapshot = existing?.takeIf { it.snapshotKey == snapshotKey }
                    val next = PersistedHomeSnapshot(
                        snapshotKey = snapshotKey,
                        updatedAt = System.currentTimeMillis(),
                        featuredHomeItems = featuredHomeItems ?: sameSessionSnapshot?.featuredHomeItems.orEmpty(),
                        continueWatchingItems = continueWatchingItems ?: sameSessionSnapshot?.continueWatchingItems.orEmpty(),
                        homeLibrarySections = homeLibrarySections ?: sameSessionSnapshot?.homeLibrarySections.orEmpty(),
                        myMediaLibraries = myMediaLibraries ?: sameSessionSnapshot?.myMediaLibraries.orEmpty()
                    )
                    writePersistedHomeSnapshotAtomically(file, next)
                }
            }
        }
    }

    suspend fun clearPersistedHomeSnapshot() {
        withContext(Dispatchers.IO) {
            homeSnapshotMutex.withLock {
                runCatching {
                    val file = getHomeSnapshotFile()
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }
        }
    }
    
    suspend fun getLatestItems(
        parentId: String? = null,
        includeItemTypes: String? = "Movie,Series",
        limit: Int? = 20,
        fields: String? = "ChildCount,RecursiveItemCount,EpisodeCount,Genres,CommunityRating,ProductionYear,OfficialRating,Overview"
    ): Result<List<BaseItemDto>> {
        return try {
            val session = getApiSession() ?: return Result.failure(Exception("Session not available"))

            val response = session.api.getLatestItems(
                userId = session.userId,
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
                fields = "People,Studios,Genres,Overview,ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId,UserData"
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

    suspend fun getSimilarItems(
        itemId: String,
        limit: Int = 12,
        fields: String? = "Overview,Genres,CommunityRating,ProductionYear,OfficialRating,SeriesName,SeriesId,UserData"
    ): Result<List<BaseItemDto>> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))
            val userId = getUserId() ?: return Result.failure(Exception("User ID not available"))

            val response = api.getSimilarItems(
                itemId = itemId,
                userId = userId,
                limit = limit,
                fields = fields
            )

            if (response.isSuccessful && response.body() != null) {
                val queryResult = response.body()!!
                Result.success(queryResult.items.orEmpty().filter { it.id != itemId })
            } else {
                Result.failure(Exception("Failed to fetch similar items: ${response.code()} - ${response.message()}"))
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
        filters: String? = null,
        fields: String? = "ChildCount,RecursiveItemCount,EpisodeCount,Genres,CommunityRating,ProductionYear,OfficialRating,Overview"
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
                filters = filters,
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

    suspend fun getFavoriteItems(
        includeItemTypes: String? = "Movie,Series,Episode",
        limit: Int? = null,
        startIndex: Int? = null
    ): Result<QueryResult<BaseItemDto>> {
        return getUserItems(
            includeItemTypes = includeItemTypes,
            recursive = true,
            sortBy = "DateCreated",
            sortOrder = "Descending",
            limit = limit,
            startIndex = startIndex,
            filters = "IsFavorite",
            fields = "SeriesName,SeriesId,EpisodeCount,RecursiveItemCount,ChildCount,IndexNumber,ParentIndexNumber,ProductionYear,RunTimeTicks,Overview,DateCreated"
        )
    }

    suspend fun setFavoriteStatus(itemId: String, isFavorite: Boolean): Result<Unit> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))
            val userId = getUserId() ?: return Result.failure(Exception("User ID not available"))

            val response = if (isFavorite) {
                api.markAsFavorite(userId = userId, itemId = itemId)
            } else {
                api.unmarkAsFavorite(userId = userId, itemId = itemId)
            }

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    Exception(
                        "Failed to ${if (isFavorite) "favorite" else "unfavorite"} item: ${response.code()} - ${response.message()}"
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getUserViews(): Result<QueryResult<BaseItemDto>> {
        return try {
            val session = getApiSession() ?: return Result.failure(Exception("Session not available"))
            val response = session.api.getUserViews(session.userId)
            
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch user views: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHomeLibrarySections(
        maxLibraries: Int? = null,
        itemsPerLibrary: Int = 20
    ): Result<List<HomeLibrarySectionData>> = coroutineScope {
        val viewsResult = getUserViews()
        viewsResult.fold(
            onSuccess = { queryResult ->
                val libraries = queryResult.items
                    .orEmpty()
                    .asSequence()
                    .filter { library ->
                        val libraryId = library.id
                        val libraryName = library.name
                        val type = library.type
                        val collectionType = library.collectionType
                        libraryId != null &&
                            !libraryName.isNullOrBlank() &&
                            collectionType != "boxsets" &&
                            collectionType != "playlists" &&
                            collectionType != "folders" &&
                            (type == "CollectionFolder" || type == "Folder") &&
                            (collectionType == "movies" || collectionType == "tvshows" || collectionType == null)
                    }
                    .distinctBy { it.id }
                    .let { sequence ->
                        if (maxLibraries != null) sequence.take(maxLibraries) else sequence
                    }
                    .toList()

                if (libraries.isEmpty()) {
                    return@fold Result.success(emptyList<HomeLibrarySectionData>())
                }

                val session = getApiSession() ?: return@fold Result.failure(
                    Exception("Session not available")
                )
                val fields = "SeriesName,SeriesId,EpisodeCount,RecursiveItemCount,ChildCount,ProductionYear,EndDate,IndexNumber,ParentIndexNumber"
                val sectionFetchSemaphore = Semaphore(4)

                val sections = libraries.map { library ->
                    async(Dispatchers.IO) {
                        sectionFetchSemaphore.withPermit {
                            val libraryId = library.id ?: return@withPermit null
                            val includeItemTypes = when (library.collectionType) {
                                "movies" -> "Movie"
                                "tvshows" -> "Episode,Series"
                                else -> "Movie,Series,Episode"
                            }

                            val latestItemsResponse = runCatching {
                                session.api.getLatestItems(
                                    userId = session.userId,
                                    parentId = libraryId,
                                    includeItemTypes = includeItemTypes,
                                    limit = itemsPerLibrary,
                                    fields = fields
                                )
                            }.getOrNull()

                            val latestItems: List<BaseItemDto> =
                                if (latestItemsResponse?.isSuccessful == true) {
                                    latestItemsResponse.body().orEmpty()
                                } else {
                                    emptyList()
                                }

                            val items = latestItems
                                .asSequence()
                                .filter { it.id != null && !it.name.isNullOrBlank() }
                                .distinctBy { it.id }
                                .toList()

                            HomeLibrarySectionData(
                                library = library,
                                items = items
                            )
                        }
                    }
                }.awaitAll()
                    .filterNotNull()
                    .filter { it.items.isNotEmpty() }

                Result.success(sections)
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    private suspend fun getImageAuthState(): ImageAuthState {
        val now = System.currentTimeMillis()
        cachedImageAuthState?.let { cached ->
            if (now - cachedImageAuthAt < imageAuthCacheTtlMs) {
                return cached
            }
        }

        val config = getSessionConfig()
        val state = ImageAuthState(
            serverUrl = config?.serverUrl,
            accessToken = config?.accessToken
        )
        cachedImageAuthState = state
        cachedImageAuthAt = now
        return state
    }

    suspend fun getImageUrlString(
        itemId: String,
        imageType: String = "Primary",
        width: Int? = null,
        height: Int? = null,
        quality: Int? = 90,
        enableImageEnhancers: Boolean = true
    ): String? {
        val authState = getImageAuthState()
        val serverUrl = authState.serverUrl
        val accessToken = authState.accessToken
        if (serverUrl.isNullOrEmpty() || itemId.isBlank()) {
            return null
        }

        val params = mutableListOf<String>()
        accessToken?.let { params.add("api_key=$it") }
        width?.let { params.add("width=$it") }
        height?.let { params.add("height=$it") }
        quality?.let { params.add("quality=$it") }
        if (!enableImageEnhancers) {
            params.add("EnableImageEnhancers=false")
        }
        val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        return "${serverUrl.trimEnd('/')}/Items/$itemId/Images/$imageType$queryString"
    }
    
    fun getImageUrl(
        itemId: String,
        imageType: String = "Primary",
        width: Int? = null,
        height: Int? = null,
        quality: Int? = 90,
        enableImageEnhancers: Boolean = true
    ): Flow<String?> {
        return imageAuthStateFlow.map { authState ->
            val serverUrl = authState.serverUrl
            val accessToken = authState.accessToken
            if (serverUrl != null && itemId.isNotEmpty()) {
                val params = mutableListOf<String>()
                accessToken?.let { params.add("api_key=$it") }
                width?.let { params.add("width=$it") }
                height?.let { params.add("height=$it") }
                quality?.let { params.add("quality=$it") }
                if (!enableImageEnhancers) {
                    params.add("EnableImageEnhancers=false")
                }
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
        quality: Int? = 90,
        enableImageEnhancers: Boolean = true
    ): Flow<String?> {
        return imageAuthStateFlow.map { authState ->
            val serverUrl = authState.serverUrl
            val accessToken = authState.accessToken
            if (serverUrl != null && itemId.isNotEmpty()) {
                val params = mutableListOf<String>()
                accessToken?.let { params.add("api_key=$it") }
                width?.let { params.add("width=$it") }
                height?.let { params.add("height=$it") }
                quality?.let { params.add("quality=$it") }
                if (!enableImageEnhancers) {
                    params.add("EnableImageEnhancers=false")
                }
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
            val session = getApiSession() ?: return Result.failure(Exception("Session not available"))

            val response = session.api.getItemsByGenre(
                userId = session.userId,
                genreIds = genreId,
                includeItemTypes = includeItemTypes,
                recursive = true,
                limit = limit,
                sortBy = "DateCreated",
                sortOrder = "Descending",
                fields = "Genres,RecursiveItemCount,ChildCount,EpisodeCount,ProductionYear,PremiereDate,EndDate,SeriesName,SeriesId"
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
        limit: Int? = null,
        startIndex: Int? = null,
        fields: String? = "ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId,ProductionYear"
    ): Result<List<BaseItemDto>> {
        return try {
            val session = getApiSession() ?: return Result.failure(Exception("Session not available"))

            val response = session.api.getResumeItems(
                userId = session.userId,
                parentId = parentId,
                includeItemTypes = includeItemTypes,
                limit = limit,
                startIndex = startIndex,
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
        val config = getSessionConfig()
        val serverUrl = config?.serverUrl
        val userId = config?.userId
        val accessToken = config?.accessToken

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
     * Build authenticated direct-download request data for DownloadManager.
     */
    suspend fun getItemDownloadRequest(itemId: String): Result<ItemDownloadRequest> {
        return try {
            val config = getSessionConfig() ?: return Result.failure(Exception("Session not available"))
            val serverUrl = config.serverUrl.trimEnd('/')
            val accessToken = config.accessToken
            if (accessToken.isNullOrBlank()) {
                return Result.failure(Exception("Access token not available"))
            }

            val item = getItemById(itemId).getOrNull()
            val playbackInfo = getPlaybackInfo(itemId).getOrNull()
            val mediaSource = playbackInfo?.mediaSources?.firstOrNull()

            val displayName = item?.name
                ?.takeIf { it.isNotBlank() }
                ?: mediaSource?.name?.takeIf { it.isNotBlank() }
                ?: "jellycine_$itemId"

            val extension = when {
                !mediaSource?.container.isNullOrBlank() -> mediaSource?.container
                !item?.container.isNullOrBlank() -> item?.container
                else -> item?.path
                    ?.substringAfterLast('.', missingDelimiterValue = "")
                    ?.takeIf { it.isNotBlank() }
            }?.trimStart('.')
                ?.lowercase()

            val encodedName = URLEncoder.encode(displayName, StandardCharsets.UTF_8.toString())
            val downloadUrl = "$serverUrl/Items/$itemId/Download?api_key=$accessToken&name=$encodedName"

            Result.success(
                ItemDownloadRequest(
                    itemId = itemId,
                    displayName = displayName,
                    downloadUrl = downloadUrl,
                    authToken = accessToken,
                    fileExtension = extension
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get direct streaming URL for a media item
     */
    suspend fun getStreamingUrl(itemId: String): Result<String> {
        return try {
            val config = getSessionConfig()
            val serverUrl = config?.serverUrl
            val accessToken = config?.accessToken

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

    // Session reporting methods for playback progress tracking

    /**
     * Report playback start to Jellyfin server
     */
    suspend fun reportPlaybackStart(
        itemId: String,
        playSessionId: String? = null,
        mediaSourceId: String? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        positionTicks: Long? = null,
        playMethod: String? = null
    ): Result<Unit> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))

            val request = com.jellycine.data.model.PlaybackStartRequest(
                itemId = itemId,
                playSessionId = playSessionId,
                mediaSourceId = mediaSourceId,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                positionTicks = positionTicks,
                playMethod = playMethod
            )

            val response = api.reportPlaybackStart(request)

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to report playback start: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Report playback progress to Jellyfin server
     */
    suspend fun reportPlaybackProgress(
        itemId: String,
        positionTicks: Long,
        playSessionId: String? = null,
        mediaSourceId: String? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        isPaused: Boolean = false,
        isMuted: Boolean = false,
        volumeLevel: Int? = null,
        playMethod: String? = null
    ): Result<Unit> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))

            val request = com.jellycine.data.model.PlaybackProgressRequest(
                itemId = itemId,
                positionTicks = positionTicks,
                playSessionId = playSessionId,
                mediaSourceId = mediaSourceId,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                isPaused = isPaused,
                isMuted = isMuted,
                volumeLevel = volumeLevel,
                playMethod = playMethod
            )

            val response = api.reportPlaybackProgress(request)

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to report playback progress: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Report playback stopped to Jellyfin server
     */
    suspend fun reportPlaybackStopped(
        itemId: String,
        positionTicks: Long? = null,
        playSessionId: String? = null,
        mediaSourceId: String? = null,
        failed: Boolean = false
    ): Result<Unit> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))

            val request = com.jellycine.data.model.PlaybackStoppedRequest(
                itemId = itemId,
                positionTicks = positionTicks,
                playSessionId = playSessionId,
                mediaSourceId = mediaSourceId,
                failed = failed
            )

            val response = api.reportPlaybackStopped(request)

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to report playback stopped: ${response.code()} - ${response.message()}"))
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
    return "$year | $genre"
}

/**
 * Get formatted rating string
 */
fun BaseItemDto.getFormattedRating(): String? {
    return communityRating?.let { rating ->
        String.format("%.1f", rating)
    }
}

/**
 * Get resume position in ticks from user data
 */
fun BaseItemDto.getResumePositionTicks(): Long? {
    return userData?.playbackPositionTicks
}

/**
 * Check if item is resumable (has a saved position and is not finished)
 */
fun BaseItemDto.isResumable(): Boolean {
    val positionTicks = userData?.playbackPositionTicks ?: return false
    val totalTicks = runTimeTicks ?: return false
    
    // Consider item resumable if position is > 0 and < 95% of total runtime
    return positionTicks > 0 && positionTicks < (totalTicks * 0.95)
}

/**
 * Get resume position as percentage (0.0 to 1.0)
 */
fun BaseItemDto.getResumePercentage(): Double {
    val positionTicks = userData?.playbackPositionTicks ?: return 0.0
    val totalTicks = runTimeTicks ?: return 0.0
    
    return if (totalTicks > 0) {
        (positionTicks.toDouble() / totalTicks.toDouble()).coerceIn(0.0, 1.0)
    } else {
        0.0
    }
}

/**
 * Get formatted resume position as time string (e.g., "15:30")
 */
fun BaseItemDto.getFormattedResumePosition(): String? {
    val positionTicks = userData?.playbackPositionTicks ?: return null
    val seconds = (positionTicks / 10_000_000).toInt()
    val minutes = seconds / 60
    val hours = minutes / 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
    } else {
        String.format("%d:%02d", minutes, seconds % 60)
    }
}
