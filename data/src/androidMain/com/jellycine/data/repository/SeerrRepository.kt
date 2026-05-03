package com.jellycine.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.jellycine.data.R
import com.jellycine.data.api.SeerrApiClient
import com.jellycine.data.api.TmdbApi
import com.jellycine.data.network.ApiResponse
import com.jellycine.data.network.ApiHeaders
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.SeerrConnectionInfo
import com.jellycine.data.model.SeerrItemIds
import com.jellycine.data.model.SeerrLoginRequest
import com.jellycine.data.model.SeerrMediaInfo
import com.jellycine.data.model.SeerrMapper
import com.jellycine.data.model.SeerrPersonCreditType
import com.jellycine.data.model.SeerrRecommendationTitle
import com.jellycine.data.model.SeerrRequestState
import com.jellycine.data.model.SeerrSearchResponse
import com.jellycine.data.model.SeerrStatusResponse
import com.jellycine.data.model.SeerrUserRequestLimits
import com.jellycine.data.model.QueryResult
import com.jellycine.data.model.seerrImageUrl
import com.jellycine.data.network.JellyCineJson
import com.jellycine.data.network.canonicalServerUrl
import com.jellycine.data.preferences.NetworkPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

class SeerrHostnameAlreadyConfiguredException : Exception()

class SeerrSessionExpiredException : Exception()

class SeerrRepository(context: Context) {
    private val appContext = context.applicationContext
    private val networkPreferences = NetworkPreferences(appContext)
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val securePrefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        EncryptedSharedPreferences.create(
            SECURE_PREFS_NAME,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getSavedConnectionInfo(scopeId: String?): SeerrConnectionInfo? {
        val validScopeId = scopeId?.takeIf { it.isNotBlank() } ?: return null
        val storedConnection = storedConnection(validScopeId) ?: return null

        return SeerrConnectionInfo(
            serverUrl = storedConnection.serverUrl,
            serverVersion = prefs.getString(scopedKey(KEY_SERVER_VERSION, validScopeId), null),
            requestLimits = getSavedRequestLimits(validScopeId),
            isVerified = prefs.getBoolean(scopedKey(KEY_IS_VERIFIED, validScopeId), false)
        )
    }

    suspend fun connect(
        scopeId: String,
        serverUrl: String,
        username: String,
        password: String,
        mediaServerUrl: String
    ): Result<SeerrConnectionInfo> = withContext(Dispatchers.IO) {
        val normalizedUrl = seerrServerUrl(serverUrl).getOrElse { error ->
            return@withContext Result.failure(error)
        }
        val sanitizedUsername = username.trim()
        val normalizedMediaServerUrl = buildMediaServerUrl(mediaServerUrl).getOrElse { error ->
            return@withContext Result.failure(error)
        }
        if (sanitizedUsername.isBlank()) {
            return@withContext Result.failure(Exception(string(R.string.seerr_error_username_required)))
        }
        if (password.isBlank()) {
            return@withContext Result.failure(Exception(string(R.string.seerr_error_password_required)))
        }

        val signInResult = signIn(
            serverUrl = normalizedUrl,
            username = sanitizedUsername,
            password = password,
            mediaServerUrl = normalizedMediaServerUrl
        )
            .getOrElse { error -> return@withContext Result.failure(error) }

        val connection = signInResult.connection.copy(isVerified = true)
        persistConnection(
            scopeId = scopeId,
            connection = connection,
            sessionCookie = signInResult.sessionCookie
        )

        Result.success(connection)
    }

    suspend fun refreshConnection(scopeId: String): Result<SeerrConnectionInfo> = withContext(Dispatchers.IO) {
        val storedConnection = storedConnection(scopeId) ?: return@withContext notConnectedFailure()

        verifyConnection(
            serverUrl = storedConnection.serverUrl,
            sessionCookie = storedConnection.sessionCookie
        ).onSuccess { connection ->
            persistConnection(
                scopeId = scopeId,
                connection = connection.copy(isVerified = true),
                sessionCookie = storedConnection.sessionCookie
            )
        }.onFailure {
            prefs.edit()
                .putBoolean(scopedKey(KEY_IS_VERIFIED, scopeId), false)
                .apply()
        }
    }

    suspend fun getPersonTitles(
        scopeId: String,
        personTmdbId: String,
        mediaType: String,
        creditType: SeerrPersonCreditType,
        limit: Int = 12
    ): Result<List<SeerrRecommendationTitle>> = withContext(Dispatchers.IO) {
        val storedConnection = storedConnection(scopeId) ?: return@withContext notConnectedFailure()
        val tmdbPersonId = personTmdbId.trim()
        if (tmdbPersonId.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        runRequestCatching {
            seerrApi(storedConnection)
                .personCredits(tmdbPersonId)
                .mapBody(
                    parseError = string(R.string.seerr_error_person_credits_parse),
                    httpError = string(R.string.seerr_error_person_credits_fetch)
                ) { payload ->
                    val seerMediaType = if (mediaType.equals("tv", ignoreCase = true)) "tv" else "movie"
                    val credits = buildList {
                        when (creditType) {
                            SeerrPersonCreditType.DIRECTOR -> addAll(
                                payload.crew.filter { credit -> credit.job.crewRoleLabel() != null }
                            )

                            SeerrPersonCreditType.ACTOR -> {
                                addAll(payload.cast)
                                addAll(payload.crew.filter { credit -> credit.job.crewRoleLabel() != null })
                            }
                        }
                    }.filter { it.mediaType.equals(seerMediaType, ignoreCase = true) }

                    Result.success(
                        credits
                            .asSequence()
                            .mapNotNull { credit ->
                                val tmdbId = credit.id?.toString()?.takeIf { it.isNotBlank() }
                                    ?: return@mapNotNull null
                                val title = credit.title.ifNotBlank()
                                    ?: credit.name.ifNotBlank()
                                    ?: return@mapNotNull null
                                SeerrRecommendationTitle(
                                    tmdbId = tmdbId,
                                    title = title,
                                    mediaType = seerMediaType,
                                    productionYear = credit.releaseDate.extractYear()
                                        ?: credit.firstAirDate.extractYear(),
                                    posterUrl = seerrImageUrl(
                                        storedConnection.serverUrl,
                                        credit.posterPath.ifNotBlank(),
                                        "w500"
                                    ),
                                    jellyfinMediaId = credit.mediaInfo?.jellyfinMediaId.ifNotBlank(),
                                    requestState = credit.mediaInfo.toRequestState(),
                                    roleLabel = credit.job.crewRoleLabel()
                                        ?: creditType.defaultRoleLabel()
                                )
                            }
                            .distinctBy { it.tmdbId }
                            .take(limit)
                            .toList()
                    )
                }
        }
    }
    suspend fun getPersonDetails(
        scopeId: String,
        personTmdbId: String
    ): Result<BaseItemDto> = withContext(Dispatchers.IO) {
        val storedConnection = storedConnection(scopeId) ?: return@withContext notConnectedFailure()
        val tmdbPersonId = personTmdbId.trim()
        if (tmdbPersonId.isBlank()) {
            return@withContext Result.failure(Exception(string(R.string.seerr_error_person_id_missing)))
        }

        runRequestCatching {
            seerrApi(storedConnection)
                .personDetails(tmdbPersonId)
                .mapBody(
                    parseError = string(R.string.seerr_error_person_details_parse),
                    httpError = string(R.string.seerr_error_person_details_load),
                    notFoundError = string(R.string.seerr_error_person_not_found)
                ) { payload ->
                    Result.success(
                        SeerrMapper.toBaseItem(
                            response = payload,
                            serverUrl = storedConnection.serverUrl
                        ) ?: return@mapBody Result.failure(
                            Exception(string(R.string.seerr_error_person_details_incomplete))
                        )
                    )
                }
        }
    }
    suspend fun searchTitles(
        scopeId: String,
        query: String,
        limit: Int = 20
    ): Result<List<SeerrRecommendationTitle>> = withContext(Dispatchers.IO) {
        val storedConnection = storedConnection(scopeId) ?: return@withContext notConnectedFailure()
        val searchQuery = query.trim()
        if (searchQuery.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        runRequestCatching {
            seerrApi(storedConnection)
                .search(searchQuery)
                .mapBody(
                    parseError = string(R.string.seerr_error_search_results_parse),
                    httpError = string(R.string.seerr_error_search_titles)
                ) { payload ->
                    Result.success(
                        payload.results
                            .asSequence()
                            .mapNotNull { result ->
                                val normalizedMediaType = when {
                                    result.mediaType.equals("tv", ignoreCase = true) -> "tv"
                                    result.mediaType.equals("movie", ignoreCase = true) -> "movie"
                                    else -> return@mapNotNull null
                                }
                                val tmdbId = result.id?.toString()?.takeIf { it.isNotBlank() }
                                    ?: return@mapNotNull null
                                val title = result.title.ifNotBlank()
                                    ?: result.name.ifNotBlank()
                                    ?: return@mapNotNull null

                                SeerrRecommendationTitle(
                                    tmdbId = tmdbId,
                                    title = title,
                                    mediaType = normalizedMediaType,
                                    productionYear = result.releaseDate.extractYear()
                                        ?: result.firstAirDate.extractYear(),
                                    posterUrl = seerrImageUrl(
                                        storedConnection.serverUrl,
                                        result.posterPath.ifNotBlank(),
                                        "w500"
                                    ),
                                    jellyfinMediaId = result.mediaInfo?.jellyfinMediaId.ifNotBlank(),
                                    requestState = result.mediaInfo.toRequestState()
                                )
                            }
                            .distinctBy { result -> "${result.mediaType}:${result.tmdbId}" }
                            .take(limit)
                            .toList()
                    )
                }
        }
    }
    suspend fun getStudios(
        scopeId: String,
        studioId: String,
        limit: Int,
        startIndex: Int
    ): Result<QueryResult<BaseItemDto>> = getCatalogItems(
        scopeId = scopeId,
        limit = limit,
        startIndex = startIndex,
        mediaType = "movie",
        loadPage = { api, page -> api.studioMovies(studioId, page) },
        parseError = string(R.string.seerr_error_studio_movies_parse),
        httpError = string(R.string.seerr_error_studio_movies_load)
    )

    suspend fun getNetworks(
        scopeId: String,
        networkId: String,
        limit: Int,
        startIndex: Int
    ): Result<QueryResult<BaseItemDto>> = getCatalogItems(
        scopeId = scopeId,
        limit = limit,
        startIndex = startIndex,
        mediaType = "tv",
        loadPage = { api, page -> api.networkSeries(networkId, page) },
        parseError = string(R.string.seerr_error_network_shows_parse),
        httpError = string(R.string.seerr_error_network_shows_load)
    )

    private suspend fun getCatalogItems(
        scopeId: String,
        limit: Int,
        startIndex: Int,
        mediaType: String,
        loadPage: suspend (SeerrApiClient, Int) -> ApiResponse<SeerrSearchResponse>,
        parseError: String,
        httpError: String
    ): Result<QueryResult<BaseItemDto>> = withContext(Dispatchers.IO) {
        val storedConnection = storedConnection(scopeId) ?: return@withContext notConnectedFailure()

        runRequestCatching {
            val items = mutableListOf<BaseItemDto>()
            var page = (startIndex / SEERR_DISCOVER_PAGE_SIZE) + 1
            var totalPages = page
            var totalResults = 0
            var dropFromFirstPage = startIndex % SEERR_DISCOVER_PAGE_SIZE
            val api = seerrApi(storedConnection)

            while (items.size < limit && page <= totalPages) {
                val payload = loadPage(api, page).mapBody(
                    parseError = parseError,
                    httpError = httpError
                ) { payload -> Result.success(payload) }
                    .getOrElse { error -> return@runRequestCatching Result.failure(error) }

                totalPages = payload.totalPages ?: page
                totalResults = payload.totalResults ?: totalResults
                payload.results
                    .drop(dropFromFirstPage)
                    .mapNotNull { result ->
                        val tmdbId = result.id?.toString()?.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        val title = result.title.ifNotBlank()
                            ?: result.name.ifNotBlank()
                            ?: return@mapNotNull null
                        val jellyfinMediaId = result.mediaInfo?.jellyfinMediaId.ifNotBlank()
                        BaseItemDto(
                            id = jellyfinMediaId ?: SeerrItemIds.detailId(tmdbId, mediaType),
                            name = title,
                            type = if (mediaType == "tv") "Series" else "Movie",
                            providerIds = mapOf("tmdb" to tmdbId),
                            productionYear = result.releaseDate.extractYear()
                                ?: result.firstAirDate.extractYear(),
                            imageUrl = seerrImageUrl(
                                storedConnection.serverUrl,
                                result.posterPath.ifNotBlank(),
                                "w500"
                            )
                        )
                    }
                    .take(limit - items.size)
                    .let(items::addAll)

                dropFromFirstPage = 0
                page++
            }

            Result.success(
                QueryResult(
                    items = items,
                    totalRecordCount = totalResults,
                    startIndex = startIndex
                )
            )
        }
    }

    suspend fun getTitleDetails(
        scopeId: String,
        tmdbId: String,
        mediaType: String
    ): Result<BaseItemDto> = withContext(Dispatchers.IO) {
        val storedConnection = storedConnection(scopeId) ?: return@withContext notConnectedFailure()
        val seerTmdbId = tmdbId.trim()
        if (seerTmdbId.isBlank()) {
            return@withContext Result.failure(Exception(string(R.string.seerr_error_title_id_missing)))
        }
        val normalizedMediaType = when {
            mediaType.equals("tv", ignoreCase = true) -> "tv"
            mediaType.equals("movie", ignoreCase = true) -> "movie"
            else -> return@withContext Result.failure(Exception(string(R.string.seerr_error_media_type_unsupported)))
        }

        runRequestCatching {
            seerrApi(storedConnection)
                .titleDetails(normalizedMediaType, seerTmdbId)
                .mapBody(
                    parseError = string(R.string.seerr_error_title_details_parse),
                    httpError = string(R.string.seerr_error_title_details_load),
                    notFoundError = string(R.string.seerr_error_title_not_found)
                ) { payload ->
                    Result.success(
                        SeerrMapper.toBaseItem(
                            response = payload,
                            itemId = SeerrItemIds.detailId(seerTmdbId, normalizedMediaType),
                            mediaType = normalizedMediaType,
                            serverUrl = storedConnection.serverUrl
                        ) ?: return@mapBody Result.failure(
                            Exception(string(R.string.seerr_error_title_details_incomplete))
                        )
                    )
                }
        }
    }

    suspend fun getTitleLogoUrl(
        scopeId: String?,
        itemId: String?
    ): String? = withContext(Dispatchers.IO) {
        val storedConnection = storedConnection(scopeId) ?: return@withContext null
        val (mediaType, tmdbId) = SeerrItemIds.detailParams(itemId ?: return@withContext null)
            ?: return@withContext null
        val tmdbType = if (mediaType.equals("tv", ignoreCase = true)) "tv" else "movie"
        val logoPath = TmdbApi(createHttpClient()).titleLogoPath(tmdbType, tmdbId)
            ?: return@withContext null

        seerrImageUrl(storedConnection.serverUrl, logoPath, "original")
    }

    fun disconnect(scopeId: String?) {
        if (scopeId.isNullOrBlank()) return
        prefs.edit()
            .remove(scopedKey(KEY_SERVER_URL, scopeId))
            .remove(scopedKey(KEY_SERVER_VERSION, scopeId))
            .remove(scopedKey(KEY_MOVIE_QUOTA_LIMIT, scopeId))
            .remove(scopedKey(KEY_MOVIE_QUOTA_DAYS, scopeId))
            .remove(scopedKey(KEY_TV_QUOTA_LIMIT, scopeId))
            .remove(scopedKey(KEY_TV_QUOTA_DAYS, scopeId))
            .remove(scopedKey(KEY_IS_VERIFIED, scopeId))
            .apply()
        securePrefs.edit()
            .remove(scopedKey(KEY_SESSION_COOKIE, scopeId))
            .apply()
    }

    private fun storedConnection(scopeId: String?): StoredConnection? {
        if (scopeId.isNullOrBlank()) return null
        val serverUrl = prefs.getString(scopedKey(KEY_SERVER_URL, scopeId), null).ifNotBlank()
            ?: return null
        val sessionCookie = securePrefs.getString(scopedKey(KEY_SESSION_COOKIE, scopeId), null).ifNotBlank()
            ?: return null

        return StoredConnection(
            serverUrl = serverUrl,
            sessionCookie = sessionCookie
        )
    }

    private fun persistConnection(
        scopeId: String,
        connection: SeerrConnectionInfo,
        sessionCookie: String
    ) {
        prefs.edit().apply {
            putString(scopedKey(KEY_SERVER_URL, scopeId), connection.serverUrl)
            putString(scopedKey(KEY_SERVER_VERSION, scopeId), connection.serverVersion)
            putBoolean(scopedKey(KEY_IS_VERIFIED, scopeId), connection.isVerified)
            putScopedIntOrRemove(KEY_MOVIE_QUOTA_LIMIT, scopeId, connection.requestLimits?.movieQuotaLimit)
            putScopedIntOrRemove(KEY_MOVIE_QUOTA_DAYS, scopeId, connection.requestLimits?.movieQuotaDays)
            putScopedIntOrRemove(KEY_TV_QUOTA_LIMIT, scopeId, connection.requestLimits?.tvQuotaLimit)
            putScopedIntOrRemove(KEY_TV_QUOTA_DAYS, scopeId, connection.requestLimits?.tvQuotaDays)
            apply()
        }
        securePrefs.edit()
            .putString(scopedKey(KEY_SESSION_COOKIE, scopeId), sessionCookie)
            .apply()
    }

    private suspend fun signIn(
        serverUrl: String,
        username: String,
        password: String,
        mediaServerUrl: String
    ): Result<SignInResult> {
        return runRequestCatching {
            val client = createHttpClient()
            val api = SeerrApiClient(
                serverUrl = serverUrl,
                client = client
            )
            val statusPayload = getStatusPayload(api).getOrElse { error ->
                return Result.failure(error)
            }
            val initialAttempt = performSignInRequest(
                api = api,
                client = client,
                serverUrl = serverUrl,
                username = username,
                password = password,
                mediaServerUrl = mediaServerUrl,
                statusPayload = statusPayload,
                includeMediaServerConfig = true
            )
            if (
                initialAttempt.isFailure &&
                initialAttempt.exceptionOrNull() is SeerrHostnameAlreadyConfiguredException
            ) {
                return performSignInRequest(
                    api = api,
                    client = client,
                    serverUrl = serverUrl,
                    username = username,
                    password = password,
                    mediaServerUrl = mediaServerUrl,
                    statusPayload = statusPayload,
                    includeMediaServerConfig = false
                )
            }
            initialAttempt
        }
    }

    private suspend fun performSignInRequest(
        api: SeerrApiClient,
        client: HttpClient,
        serverUrl: String,
        username: String,
        password: String,
        mediaServerUrl: String,
        statusPayload: SeerrStatusResponse,
        includeMediaServerConfig: Boolean
    ): Result<SignInResult> {
        val response = api.login(
            SeerrLoginRequest(
                username = username,
                password = password,
                hostname = mediaServerUrl.takeIf { includeMediaServerConfig }
            )
        )

        return when {
            response.isSuccessful -> {
                val sessionCookie = response.headers().sessionCookie()
                    ?: return Result.failure(
                        Exception(string(R.string.seerr_error_missing_session_cookie))
                    )
                val authenticatedApi = SeerrApiClient(
                    serverUrl = serverUrl,
                    client = client,
                    sessionCookie = sessionCookie
                )
                Result.success(
                    SignInResult(
                        connection = buildConnectionInfo(
                            serverUrl = serverUrl,
                            serverVersion = statusPayload.version,
                            requestLimits = authenticatedApi.currentUser().body()?.id?.let { userId ->
                                fetchEffectiveRequestLimits(authenticatedApi, userId)
                            }
                        ),
                        sessionCookie = sessionCookie
                    )
                )
            }

            response.isAuthFailure() -> {
                Result.failure(
                    Exception(string(R.string.seerr_error_credentials_rejected))
                )
            }

            response.code() == 500 &&
                response.errorBody().orEmpty().contains("hostname already configured", ignoreCase = true) -> {
                Result.failure(SeerrHostnameAlreadyConfiguredException())
            }

            else -> {
                Result.failure(Exception(string(R.string.seerr_error_sign_in_http, response.code())))
            }
        }
    }

    private suspend fun verifyConnection(
        serverUrl: String,
        sessionCookie: String
    ): Result<SeerrConnectionInfo> {
        return runRequestCatching {
            val client = createHttpClient()
            val statusPayload = getStatusPayload(
                SeerrApiClient(
                    serverUrl = serverUrl,
                    client = client
                )
            ).getOrElse { error ->
                return Result.failure(error)
            }
            val api = SeerrApiClient(
                serverUrl = serverUrl,
                client = client,
                sessionCookie = sessionCookie
            )
            val currentUserResponse = api.currentUser()

            when {
                currentUserResponse.isSuccessful -> {
                    Result.success(
                        buildConnectionInfo(
                            serverUrl = serverUrl,
                            serverVersion = statusPayload.version,
                            requestLimits = currentUserResponse.body()?.id?.let { userId ->
                                fetchEffectiveRequestLimits(api, userId)
                            }
                        )
                    )
                }

                currentUserResponse.isAuthFailure() -> {
                    Result.failure(SeerrSessionExpiredException())
                }

                else -> {
                    Result.failure(
                        Exception(string(R.string.seerr_error_verify_session_http, currentUserResponse.code()))
                    )
                }
            }
        }
    }

    private suspend fun getStatusPayload(api: SeerrApiClient): Result<SeerrStatusResponse> {
        val response = api.status()
        return when {
            response.isSuccessful -> Result.success(response.body() ?: SeerrStatusResponse())
            response.code() == 404 -> {
                Result.failure(Exception(string(R.string.seerr_error_not_found_at_url)))
            }
            else -> {
                Result.failure(Exception(string(R.string.seerr_error_returned_http_verify_url, response.code())))
            }
        }
    }
    private fun createHttpClient(): HttpClient {
        val timeouts = networkPreferences.getTimeoutConfig()
        val okHttpClient = OkHttpClient.Builder()
            .callTimeout(timeouts.requestTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .connectTimeout(timeouts.connectionTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeouts.socketTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(timeouts.socketTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return HttpClient(OkHttp) {
            expectSuccess = false
            engine {
                preconfigured = okHttpClient
            }
            install(ContentNegotiation) {
                json(JellyCineJson)
            }
        }
    }
    private fun seerrServerUrl(serverUrl: String): Result<String> {
        if (serverUrl.isBlank()) {
            return Result.failure(Exception(string(R.string.seerr_error_url_required)))
        }
        val normalized = canonicalServerUrl(serverUrl)
        if (!normalized.startsWith("http://", ignoreCase = true) &&
            !normalized.startsWith("https://", ignoreCase = true)
        ) {
            return Result.failure(Exception(string(R.string.seerr_error_url_scheme_required)))
        }
        val validatedUrl = normalized.toHttpUrlOrNull()
            ?: return Result.failure(Exception(string(R.string.seerr_error_url_invalid)))

        return Result.success(canonicalServerUrl(validatedUrl.toString()))
    }

    private fun buildMediaServerUrl(serverUrl: String): Result<String> {
        if (serverUrl.isBlank()) {
            return Result.failure(Exception(string(R.string.seerr_error_media_server_url_missing)))
        }
        val validatedUrl = canonicalServerUrl(serverUrl).toHttpUrlOrNull()
            ?: return Result.failure(Exception(string(R.string.seerr_error_media_server_url_invalid)))

        return Result.success(canonicalServerUrl(validatedUrl.toString()))
    }

    private fun seerrApi(storedConnection: StoredConnection): SeerrApiClient {
        return SeerrApiClient(
            serverUrl = storedConnection.serverUrl,
            client = createHttpClient(),
            sessionCookie = storedConnection.sessionCookie
        )
    }
    private fun scopedKey(baseKey: String, scopeId: String): String {
        return "${baseKey}_${sha256(scopeId)}"
    }

    private fun buildConnectionInfo(
        serverUrl: String,
        serverVersion: String?,
        requestLimits: SeerrUserRequestLimits? = null
    ): SeerrConnectionInfo {
        return SeerrConnectionInfo(
            serverUrl = serverUrl,
            serverVersion = serverVersion.ifNotBlank(),
            requestLimits = requestLimits,
            isVerified = true
        )
    }

    private suspend fun fetchEffectiveRequestLimits(
        api: SeerrApiClient,
        userId: Int
    ): SeerrUserRequestLimits? {
        val payload = api.quota(userId).body() ?: return null
        return SeerrUserRequestLimits(
            movieQuotaLimit = payload.movie.limit,
            movieQuotaDays = payload.movie.days,
            tvQuotaLimit = payload.tv.limit,
            tvQuotaDays = payload.tv.days
        )
    }

    private fun <T> notConnectedFailure(): Result<T> =
        Result.failure(Exception(string(R.string.seerr_error_not_connected)))

    private inline fun <T, R> ApiResponse<T>.mapBody(
        parseError: String,
        httpError: String,
        notFoundError: String? = null,
        transform: (T) -> Result<R>
    ): Result<R> = when {
        isSuccessful -> body()?.let(transform) ?: Result.failure(Exception(parseError))
        isAuthFailure() -> Result.failure(SeerrSessionExpiredException())
        code() == 404 && notFoundError != null -> Result.failure(Exception(notFoundError))
        else -> Result.failure(Exception(string(R.string.seerr_error_http_response, httpError, code())))
    }

    private fun <T> ApiResponse<T>.isAuthFailure(): Boolean = code() == 401 || code() == 403

    private fun ApiHeaders.sessionCookie(): String? = getAll("Set-Cookie")
        .mapNotNull { header ->
            header.substringBefore(';')
                .trim()
                .takeIf { cookie -> cookie.contains('=') && cookie.isNotBlank() }
        }
        .takeIf { it.isNotEmpty() }
        ?.joinToString("; ")
    private inline fun <T> runRequestCatching(block: () -> Result<T>): Result<T> = try {
        block()
    } catch (error: Exception) {
        Result.failure(connectionError(error))
    }

    private fun connectionError(error: Exception): Exception = when (error) {
        is UnknownHostException -> Exception(string(R.string.seerr_error_unknown_host))
        is ConnectException -> Exception(string(R.string.seerr_error_connect))
        is SocketTimeoutException -> Exception(string(R.string.seerr_error_timeout))
        is SSLException -> Exception(string(R.string.seerr_error_ssl))
        is CertificateException -> Exception(string(R.string.seerr_error_certificate))
        is IOException -> Exception(error.message ?: string(R.string.seerr_error_unreachable))
        else -> Exception(error.message ?: string(R.string.seerr_error_connect_generic))
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                append("%02x".format(byte.toInt() and 0xff))
            }
        }
    }

    private fun String?.ifNotBlank(): String? = this?.takeIf { it.isNotBlank() }

    private fun String?.extractYear(): Int? =
        this?.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()

    private fun String?.crewRoleLabel(): String? {
        val seerJob = this?.trim()?.lowercase(Locale.US) ?: return null
        return when {
            seerJob == "director" -> string(R.string.seerr_credit_role_director)
            seerJob in setOf("writer", "screenplay", "story", "novel", "characters") -> string(R.string.seerr_credit_role_writer)
            seerJob in setOf("producer", "executive producer", "co-producer", "associate producer", "line producer") -> string(R.string.seerr_credit_role_producer)
            seerJob == "creator" -> string(R.string.seerr_credit_role_creator)
            else -> string(R.string.seerr_credit_role_other)
        }
    }

    private fun SeerrMediaInfo?.toRequestState(): SeerrRequestState {
        if (this == null) return SeerrRequestState.NONE

        val hasActiveRequest = requests.any { request ->
            request.status == SEERR_REQUEST_PENDING || request.status == SEERR_REQUEST_APPROVED
        }
        val hasRequestedMediaStatus =
            status == SEERR_MEDIA_PENDING || status == SEERR_MEDIA_PROCESSING

        return if (hasActiveRequest || hasRequestedMediaStatus) {
            SeerrRequestState.REQUESTED
        } else {
            SeerrRequestState.NONE
        }
    }

    private fun SeerrPersonCreditType.defaultRoleLabel(): String = when (this) {
        SeerrPersonCreditType.DIRECTOR -> string(R.string.seerr_credit_role_director)
        SeerrPersonCreditType.ACTOR -> string(R.string.seerr_credit_role_actor)
    }

    private fun string(resId: Int, vararg formatArgs: Any): String =
        appContext.getString(resId, *formatArgs)

    private fun SharedPreferences.Editor.putScopedIntOrRemove(
        baseKey: String,
        scopeId: String,
        value: Int?
    ) {
        val key = scopedKey(baseKey, scopeId)
        if (value == null) {
            remove(key)
        } else {
            putInt(key, value)
        }
    }

    private fun getSavedRequestLimits(scopeId: String): SeerrUserRequestLimits? {
        val movieQuotaLimit = prefs.getScopedIntOrNull(KEY_MOVIE_QUOTA_LIMIT, scopeId)
        val movieQuotaDays = prefs.getScopedIntOrNull(KEY_MOVIE_QUOTA_DAYS, scopeId)
        val tvQuotaLimit = prefs.getScopedIntOrNull(KEY_TV_QUOTA_LIMIT, scopeId)
        val tvQuotaDays = prefs.getScopedIntOrNull(KEY_TV_QUOTA_DAYS, scopeId)
        if (
            movieQuotaLimit == null &&
            movieQuotaDays == null &&
            tvQuotaLimit == null &&
            tvQuotaDays == null
        ) {
            return null
        }
        return SeerrUserRequestLimits(
            movieQuotaLimit = movieQuotaLimit,
            movieQuotaDays = movieQuotaDays,
            tvQuotaLimit = tvQuotaLimit,
            tvQuotaDays = tvQuotaDays
        )
    }

    private fun SharedPreferences.getScopedIntOrNull(baseKey: String, scopeId: String): Int? {
        val key = scopedKey(baseKey, scopeId)
        return if (contains(key)) getInt(key, 0) else null
    }

    private data class StoredConnection(
        val serverUrl: String,
        val sessionCookie: String
    )

    private data class SignInResult(
        val connection: SeerrConnectionInfo,
        val sessionCookie: String
    )
}