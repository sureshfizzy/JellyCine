package com.jellycine.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jellycine.data.DataModuleConfig
import com.jellycine.data.api.MediaServerApi
import com.jellycine.data.datastore.DataStoreProvider
import com.jellycine.data.datastore.HomeSnapshotStore
import com.jellycine.data.model.AudioTranscodeMode
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.HomeLibrarySectionData
import com.jellycine.data.model.IntroWindow
import com.jellycine.data.model.PlaybackAuthContext
import com.jellycine.data.model.PlaybackUrlBuilder
import com.jellycine.data.model.PlaybackRequest
import com.jellycine.data.model.PlaybackStreamOptions
import com.jellycine.data.model.PersistedHomeSnapshot
import com.jellycine.data.model.QueryResult
import com.jellycine.data.model.PlaybackInfoRequest
import com.jellycine.data.model.RecommendationDto
import com.jellycine.data.model.UserDto
import com.jellycine.data.network.NetworkModule
import com.jellycine.data.network.ServerType
import com.jellycine.data.network.trimTrailingSlash
import com.jellycine.data.preferences.NetworkPreferences
import com.jellycine.data.preferences.NetworkTimeoutConfig
import com.jellycine.data.security.AuthSessionIds
import com.jellycine.data.security.LEGACY_ACCESS_TOKEN_KEY
import com.jellycine.data.security.SecureSessionStore
import com.jellycine.data.util.buildServerUrl
import com.jellycine.data.util.getServerUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class MediaRepository(private val context: Context) {
    companion object {
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private val SERVER_TYPE_KEY = stringPreferencesKey("server_type")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
    }

    private val dataStore: DataStore<Preferences> = DataStoreProvider.getDataStore(context)
    private val networkPreferences = NetworkPreferences(context)
    private val secureSessionStore = SecureSessionStore(context)

    private data class ImageAuthState(
        val serverUrl: String?
    )

    private data class ApiSession(
        val api: MediaServerApi,
        val userId: String,
        val serverType: ServerType?,
        val baseUrl: String
    )

    private data class SuggestionsRoute(
        val endpoint: String,
        val userIdQuery: String?
    )

    private data class SessionConfig(
        val serverUrl: String,
        val serverTypeRaw: String?,
        val serverType: ServerType?,
        val accessToken: String?,
        val userId: String,
        val timeoutConfig: NetworkTimeoutConfig
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

    private val imageAuthCacheTtlMs = 1500L

    @Volatile
    private var cachedImageAuthState: ImageAuthState? = null

    @Volatile
    private var cachedImageAuthAt: Long = 0L

    private val homeSnapshotStore = HomeSnapshotStore(context.filesDir)
    private val introDbClient = IntroDbClient(
        getSeriesItem = { seriesId -> getItemById(seriesId).getOrNull() }
    )

    private val imageAuthStateFlow: Flow<ImageAuthState> = dataStore.data
        .map { preferences ->
            ImageAuthState(
                serverUrl = preferences[SERVER_URL_KEY]
            )
        }
        .distinctUntilChanged()
    
    private suspend fun getApi(): MediaServerApi? = getApiSession()?.api

    private suspend fun getUserId(): String? = getApiSession()?.userId

    private fun normalizeSubtitleStreamIndex(subtitleStreamIndex: Int?): Int? {
        return subtitleStreamIndex?.takeIf { it >= 0 }
    }

    private fun createPlaybackAuthContext(config: SessionConfig): PlaybackAuthContext {
        return PlaybackAuthContext(
            serverUrl = config.serverUrl,
            serverType = config.serverType,
            accessToken = config.accessToken,
            deviceId = NetworkModule.getClientDeviceId(),
            clientVersion = DataModuleConfig.CLIENT_VERSION
        )
    }

    private suspend fun getSessionConfig(): SessionConfig? {
        val preferences = dataStore.data.first()
        val serverUrl = preferences[SERVER_URL_KEY] ?: return null
        val userId = preferences[USER_ID_KEY] ?: return null
        val serverTypeRaw = preferences[SERVER_TYPE_KEY]
        val serverType = serverTypeRaw?.let {
            runCatching { ServerType.valueOf(it) }.getOrNull()
        }
        val accessToken = secureSessionStore.getToken(AuthSessionIds.buildServerId(serverUrl, userId))
            ?: preferences[LEGACY_ACCESS_TOKEN_KEY]

        return SessionConfig(
            serverUrl = serverUrl,
            serverTypeRaw = serverTypeRaw,
            serverType = serverType,
            accessToken = accessToken,
            userId = userId,
            timeoutConfig = networkPreferences.getTimeoutConfig()
        ).also {
            cachedSessionConfig = it
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

            val session = ApiSession(
                api = api,
                userId = config.userId,
                serverType = config.serverType,
                baseUrl = config.serverUrl
            )
            cachedSession = session
            cachedSessionKey = newSessionKey
            return session
        }
    }

    private fun buildSnapshotKey(config: SessionConfig): String {
        return "${trimTrailingSlash(config.serverUrl)}|${config.userId}"
    }

    fun getPersistedHomeSnapshot(): PersistedHomeSnapshot? {
        return homeSnapshotStore.getPersistedHomeSnapshot()
    }

    suspend fun loadPersistedHomeSnapshot(
        maxAgeMs: Long? = null
    ): PersistedHomeSnapshot? {
        val config = getSessionConfig() ?: return null
        return homeSnapshotStore.loadPersistedHomeSnapshot(
            expectedSnapshotKey = buildSnapshotKey(config),
            maxAgeMs = maxAgeMs
        )
    }

    suspend fun persistHomeSnapshot(
        featuredHomeItems: List<BaseItemDto>? = null,
        continueWatchingItems: List<BaseItemDto>? = null,
        nextUpItems: List<BaseItemDto>? = null,
        homeLibrarySections: List<HomeLibrarySectionData>? = null,
        myMediaLibraries: List<BaseItemDto>? = null,
        username: String? = null,
        serverName: String? = null,
        serverUrl: String? = null,
        profileImageUrl: String? = null,
        isAdministrator: Boolean? = null,
        isVideoTranscodingAllowed: Boolean? = null,
        isAudioTranscodingAllowed: Boolean? = null
    ) {
        val config = getSessionConfig() ?: return
        homeSnapshotStore.persistHomeSnapshot(
            snapshotKey = buildSnapshotKey(config),
            featuredHomeItems = featuredHomeItems,
            continueWatchingItems = continueWatchingItems,
            nextUpItems = nextUpItems,
            homeLibrarySections = homeLibrarySections,
            myMediaLibraries = myMediaLibraries,
            username = username,
            serverName = serverName,
            serverUrl = serverUrl,
            profileImageUrl = profileImageUrl,
            isAdministrator = isAdministrator,
            isVideoTranscodingAllowed = isVideoTranscodingAllowed,
            isAudioTranscodingAllowed = isAudioTranscodingAllowed
        )
    }

    suspend fun clearPersistedHomeSnapshot() {
        homeSnapshotStore.clearPersistedHomeSnapshot()
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

    suspend fun getSuggestions(
        mediaType: String = "Movie,Series",
        limit: Int = 15,
        fields: String? = "ChildCount,RecursiveItemCount,EpisodeCount,Genres,CommunityRating,ProductionYear,Overview"
    ): Result<List<BaseItemDto>> {
        return try {
            val session = getApiSession() ?: return Result.failure(Exception("Session not available"))
            val route = SuggestionsEndpoint(
                serverType = session.serverType,
                userId = session.userId
            )
            val isEmby = route.userIdQuery == null
            val response = session.api.getSuggestions(
                endpoint = route.endpoint,
                userId = route.userIdQuery,
                mediaType = null,
                type = if (isEmby) null else mediaType,
                includeItemTypes = if (isEmby) mediaType else null,
                limit = limit,
                fields = fields
            )

            if (response.isSuccessful && response.body() != null) {
                val items = response.body()!!.items
                    .orEmpty()
                    .asSequence()
                    .filter { it.id != null && !it.name.isNullOrBlank() }
                    .distinctBy { it.id }
                    .take(limit)
                    .toList()
                Result.success(items)
            } else {
                Result.failure(Exception("Failed to fetch suggestions: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMovieRecommendations(
        parentId: String? = null,
        categoryLimit: Int = 8,
        itemLimit: Int = 16,
        fields: String? = "Genres,CommunityRating,ProductionYear,Overview,SeriesName,SeriesId,ParentIndexNumber,IndexNumber,EpisodeCount,RecursiveItemCount,ChildCount,UserData"
    ): Result<List<RecommendationDto>> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))
            val userId = getUserId() ?: return Result.failure(Exception("User ID not available"))

            val response = api.getMovieRecommendations(
                userId = userId,
                parentId = parentId,
                categoryLimit = categoryLimit,
                itemLimit = itemLimit,
                fields = fields
            )

            if (response.isSuccessful && response.body() != null) {
                val recommendations = response.body().orEmpty()
                    .map { row ->
                        row.copy(
                            items = row.items
                                .orEmpty()
                                .filter { item -> item.id != null && !item.name.isNullOrBlank() }
                                .distinctBy { item -> item.id }
                        )
                    }
                    .filter { it.items.orEmpty().isNotEmpty() }
                Result.success(recommendations)
            } else {
                Result.failure(Exception("Failed to fetch movie recommendations: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun SuggestionsEndpoint(
        serverType: ServerType?,
        userId: String
    ): SuggestionsRoute {
        val isEmby = serverType == ServerType.EMBY
        return if (isEmby) {
            SuggestionsRoute(endpoint = "Users/$userId/Suggestions", userIdQuery = null)
        } else {
            SuggestionsRoute(endpoint = "Items/Suggestions", userIdQuery = userId)
        }
    }

    suspend fun getItemById(itemId: String): Result<BaseItemDto> {
        return try {
            val api = getApi() ?: return Result.failure(Exception("API not available"))
            val userId = getUserId() ?: return Result.failure(Exception("User ID not available"))
            val detailFields = "People,Studios,Genres,Overview,ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId,OfficialRating,UserData,Chapters,ProviderIds,IndexNumber,ParentIndexNumber"

            val response = api.getItemById(
                userId = userId,
                itemId = itemId,
                fields = detailFields
            )

            if (response.isSuccessful && response.body() != null) {
                val item = response.body()!!
                val mappedItem = if (
                    item.type == "Episode" &&
                    item.officialRating.isNullOrBlank() &&
                    !item.seriesId.isNullOrBlank()
                ) {
                    val seriesResponse = api.getItemById(
                        userId = userId,
                        itemId = item.seriesId!!,
                        fields = "OfficialRating"
                    )

                    val seriesRating = seriesResponse.body()
                        ?.officialRating
                        ?.takeIf { it.isNotBlank() }

                    if (seriesResponse.isSuccessful && seriesRating != null) {
                        item.copy(officialRating = seriesRating)
                    } else {
                        item
                    }
                } else {
                    item
                }

                Result.success(mappedItem)
            } else {
                Result.failure(Exception("Failed to fetch item: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCommunityIntroWindow(item: BaseItemDto): Result<IntroWindow?> {
        return try {
            Result.success(introDbClient.getIntroWindow(item))
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
        personIds: String? = null,
        genres: String? = null,
        genreIds: String? = null,
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
                personIds = personIds,
                genres = genres,
                genreIds = genreIds,
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

    suspend fun getItemsForPerson(
        personId: String,
        limit: Int = 120
    ): Result<List<BaseItemDto>> {
        return getUserItems(
            personIds = personId,
            includeItemTypes = "Movie,Series,Episode",
            recursive = true,
            sortBy = "SortName",
            sortOrder = "Ascending",
            limit = limit,
            fields = "Genres,CommunityRating,ProductionYear,Overview,SeriesName,SeriesId,ParentIndexNumber,IndexNumber,EpisodeCount,RecursiveItemCount,ChildCount,UserData"
        ).map { result ->
            result.items
                .orEmpty()
                .filter { it.id != null && !it.name.isNullOrBlank() }
                .distinctBy { it.id }
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
            serverUrl = config?.serverUrl
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
        enableImageEnhancers: Boolean = true,
        imageTag: String? = null
    ): String? {
        val authState = getImageAuthState()
        val serverUrl = authState.serverUrl
        if (serverUrl.isNullOrEmpty() || itemId.isBlank()) {
            return null
        }

        val queryParams = mutableListOf<Pair<String, String?>>()
        width?.let { queryParams.add("width" to it.toString()) }
        height?.let { queryParams.add("height" to it.toString()) }
        quality?.let { queryParams.add("quality" to it.toString()) }
        imageTag?.takeIf { it.isNotBlank() }?.let { queryParams.add("tag" to it) }
        if (!enableImageEnhancers) {
            queryParams.add("HasImageEnhancers" to "false")
            queryParams.add("EnableImageEnhancers" to "false")
        }
        return buildServerUrl(baseUrl = serverUrl, encodedPath = "Items/$itemId/Images/$imageType", queryParams = queryParams)
    }
    
    fun getImageUrl(
        itemId: String,
        imageType: String = "Primary",
        width: Int? = null,
        height: Int? = null,
        quality: Int? = 90,
        enableImageEnhancers: Boolean = true,
        imageTag: String? = null
    ): Flow<String?> {
        return imageAuthStateFlow.map { authState ->
            val serverUrl = authState.serverUrl
            if (serverUrl != null && itemId.isNotEmpty()) {
                val queryParams = mutableListOf<Pair<String, String?>>()
                width?.let { queryParams.add("width" to it.toString()) }
                height?.let { queryParams.add("height" to it.toString()) }
                quality?.let { queryParams.add("quality" to it.toString()) }
                imageTag?.takeIf { it.isNotBlank() }?.let { queryParams.add("tag" to it) }
                if (!enableImageEnhancers) {
                    queryParams.add("HasImageEnhancers" to "false")
                    queryParams.add("EnableImageEnhancers" to "false")
                }
                buildServerUrl(baseUrl = serverUrl, encodedPath = "Items/$itemId/Images/$imageType", queryParams = queryParams)
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
        enableImageEnhancers: Boolean = true,
        imageTag: String? = null
    ): Flow<String?> {
        return imageAuthStateFlow.map { authState ->
            val serverUrl = authState.serverUrl
            if (serverUrl != null && itemId.isNotEmpty()) {
                val queryParams = mutableListOf<Pair<String, String?>>()
                width?.let { queryParams.add("width" to it.toString()) }
                height?.let { queryParams.add("height" to it.toString()) }
                quality?.let { queryParams.add("quality" to it.toString()) }
                imageTag?.takeIf { it.isNotBlank() }?.let { queryParams.add("tag" to it) }
                if (!enableImageEnhancers) {
                    queryParams.add("HasImageEnhancers" to "false")
                    queryParams.add("EnableImageEnhancers" to "false")
                }
                buildServerUrl(baseUrl = serverUrl, encodedPath = "Items/$itemId/Images/Backdrop/$imageIndex", queryParams = queryParams)
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
            "Mystery" to setOf("Thriller"),
            "Animation" to setOf("Anime")
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

    suspend fun getNextUpItems(
        seriesId: String? = null,
        parentId: String? = null,
        limit: Int? = null,
        startIndex: Int? = null,
        fields: String? = "Overview,SeriesName,SeriesId,SeasonName,SeasonId"
    ): Result<List<BaseItemDto>> {
        return try {
            val session = getApiSession() ?: return Result.failure(Exception("Session not available"))
            val legacyNextUp = if (session.serverType == ServerType.EMBY) true else null

            val response = session.api.getNextUp(
                userId = session.userId,
                seriesId = seriesId,
                parentId = parentId,
                limit = limit,
                startIndex = startIndex,
                legacyNextUp = legacyNextUp,
                fields = fields,
                enableUserData = true,
                enableImages = true
            )

            if (response.isSuccessful && response.body() != null) {
                val queryResult = response.body()!!
                Result.success(queryResult.items ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch next up items: ${response.code()} - ${response.message()}"))
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

    suspend fun getUserProfileImageUrl(primaryImageTag: String? = null): String? {
        val config = getSessionConfig()
        val serverUrl = config?.serverUrl
        val userId = config?.userId

        return if (serverUrl != null && userId != null) {
            buildServerUrl(
                baseUrl = serverUrl,
                encodedPath = "Users/$userId/Images/Primary",
                queryParams = listOf("tag" to primaryImageTag)
            )
        } else {
            null
        }
    }

    // Player-related methods

    /**
     * Get playback information for a media item
     */
    suspend fun getPlaybackInfo(
        itemId: String,
        maxStreamingBitrate: Int? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        audioTranscodeMode: AudioTranscodeMode = AudioTranscodeMode.AUTO
    ): Result<com.jellycine.data.model.PlaybackInfoResponse> {
        return try {
            val session = getApiSession() ?: return Result.failure(Exception("Session not available"))
            val api = session.api
            val userId = session.userId
            val serverUrl = session.baseUrl
            val serverType = session.serverType
            val forceTranscode = (maxStreamingBitrate ?: 0) > 0
            val normalizedSubtitleStreamIndex = normalizeSubtitleStreamIndex(subtitleStreamIndex)
            // Emby resolves original-playback requests more reliably through the query-based endpoint.
            val preferGetPlaybackInfo = serverType == ServerType.EMBY &&
                !forceTranscode && audioTranscodeMode == AudioTranscodeMode.AUTO
            val deviceProfile = PlaybackDeviceProfileFactory.create(
                maxStreamingBitrate = maxStreamingBitrate?.toLong(),
                audioTranscodeMode = audioTranscodeMode
            )
            if (preferGetPlaybackInfo) {
                val getResponse = api.getPlaybackInfoGet(
                    itemId = itemId,
                    userId = userId,
                    maxStreamingBitrate = maxStreamingBitrate,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = normalizedSubtitleStreamIndex,
                    enableDirectPlay = null,
                    enableDirectStream = null,
                    enableTranscoding = null
                )

                if (getResponse.isSuccessful && getResponse.body() != null) {
                    val responseBody = PlaybackUrlBuilder.normalizePlaybackInfoUrls(
                        serverUrl = serverUrl,
                        playbackInfo = getResponse.body()!!
                    )
                    return Result.success(responseBody)
                }
            }

            val playbackInfoRequest = PlaybackInfoRequest(
                userId = userId,
                maxStreamingBitrate = maxStreamingBitrate?.toLong(),
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = normalizedSubtitleStreamIndex,
                enableDirectPlay = if (forceTranscode) false else null,
                enableDirectStream = if (forceTranscode) false else null,
                enableTranscoding = if (forceTranscode) true else null,
                deviceProfile = deviceProfile
            )

            val postResponse = api.getPlaybackInfoPost(
                itemId = itemId,
                request = playbackInfoRequest
            )

            if (postResponse.isSuccessful && postResponse.body() != null) {
                val responseBody = PlaybackUrlBuilder.normalizePlaybackInfoUrls(
                    serverUrl = serverUrl,
                    playbackInfo = postResponse.body()!!
                )
                return Result.success(responseBody)
            }

            val getResponse = api.getPlaybackInfoGet(
                itemId = itemId,
                userId = userId,
                maxStreamingBitrate = maxStreamingBitrate,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = normalizedSubtitleStreamIndex,
                enableDirectPlay = if (forceTranscode) false else null,
                enableDirectStream = if (forceTranscode) false else null,
                enableTranscoding = if (forceTranscode) true else null
            )

            if (getResponse.isSuccessful && getResponse.body() != null) {
                val responseBody = PlaybackUrlBuilder.normalizePlaybackInfoUrls(
                    serverUrl = serverUrl,
                    playbackInfo = getResponse.body()!!
                )
                Result.success(responseBody)
            } else {
                Result.failure(
                    Exception(
                        "Failed to fetch playback info: POST ${postResponse.code()} / GET ${getResponse.code()}"
                    )
                )
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
            val serverUrl = config.serverUrl
            val accessToken = config.accessToken
            if (accessToken.isNullOrBlank()) {
                return Result.failure(Exception("Access token not available"))
            }

            val item = getItemById(itemId).getOrNull()
            val itemDisplayName = item?.name?.takeIf { it.isNotBlank() }
            val itemExtension = when {
                !item?.container.isNullOrBlank() -> item?.container
                else -> item?.path
                    ?.substringAfterLast('.', missingDelimiterValue = "")
                    ?.takeIf { it.isNotBlank() }
            }?.trimStart('.')
                ?.lowercase()

            val needsPlaybackInfo = itemDisplayName.isNullOrBlank() || itemExtension.isNullOrBlank()
            val mediaSource = if (needsPlaybackInfo) {
                getPlaybackInfo(itemId).getOrNull()?.mediaSources?.firstOrNull()
            } else {
                null
            }

            val displayName = itemDisplayName
                ?: mediaSource?.name?.takeIf { it.isNotBlank() }
                ?: "jellycine_$itemId"

            val extension = itemExtension
                ?: mediaSource?.container
                    ?.trimStart('.')
                    ?.lowercase()

            val downloadUrl = buildServerUrl(
                baseUrl = serverUrl,
                encodedPath = "Items/$itemId/Download",
                queryParams = listOf("name" to displayName)
            )

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

    suspend fun getPlaybackRequest(
        itemId: String,
        maxStreamingBitrate: Int? = null,
        maxStreamingHeight: Int? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        audioTranscodeMode: AudioTranscodeMode = AudioTranscodeMode.AUTO,
        playbackInfo: com.jellycine.data.model.PlaybackInfoResponse? = null
    ): Result<PlaybackRequest> {
        val config = getSessionConfig() ?: return Result.failure(Exception("Session not available"))
        val authContext = createPlaybackAuthContext(config)
        val activePlaybackInfo = playbackInfo ?: run {
            val playbackInfoResult = getPlaybackInfo(
                itemId = itemId,
                maxStreamingBitrate = maxStreamingBitrate,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                audioTranscodeMode = audioTranscodeMode
            )
            if (playbackInfoResult.isFailure) {
                return Result.failure(
                    playbackInfoResult.exceptionOrNull() ?: Exception("Failed to get playback info")
                )
            }
            playbackInfoResult.getOrNull()
        } ?: return Result.failure(Exception("Playback info not available"))

        return PlaybackUrlBuilder.createLocalPlaybackRequest(
            authContext = authContext,
            itemId = itemId,
            playbackInfo = activePlaybackInfo,
            options = PlaybackStreamOptions(
                maxStreamingBitrate = maxStreamingBitrate,
                maxStreamingHeight = maxStreamingHeight,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                audioTranscodeMode = audioTranscodeMode
            )
        )
    }

    suspend fun getCastStreamingUrl(
        itemId: String,
        maxStreamingBitrate: Int? = null,
        maxStreamingHeight: Int? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        audioTranscodeMode: AudioTranscodeMode = AudioTranscodeMode.AUTO,
        playbackInfo: com.jellycine.data.model.PlaybackInfoResponse? = null
    ): Result<String> {
        val config = getSessionConfig() ?: return Result.failure(Exception("Session not available"))
        val authContext = createPlaybackAuthContext(config)
        val activePlaybackInfo = playbackInfo ?: run {
            val playbackInfoResult = getPlaybackInfo(
                itemId = itemId,
                maxStreamingBitrate = maxStreamingBitrate,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                audioTranscodeMode = audioTranscodeMode
            )
            if (playbackInfoResult.isFailure) {
                return Result.failure(
                    playbackInfoResult.exceptionOrNull() ?: Exception("Failed to get playback info")
                )
            }
            playbackInfoResult.getOrNull()
        } ?: return Result.failure(Exception("Playback info not available"))

        return PlaybackUrlBuilder.createCastStreamingUrl(
            authContext = authContext,
            itemId = itemId,
            playbackInfo = activePlaybackInfo,
            options = PlaybackStreamOptions(
                maxStreamingBitrate = maxStreamingBitrate,
                maxStreamingHeight = maxStreamingHeight,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                audioTranscodeMode = audioTranscodeMode
            )
        )
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