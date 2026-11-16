package com.jellycine.app.ui.screens.dashboard.home
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.download.DownloadRepositoryProvider
import com.jellycine.shared.preferences.Preferences
import com.jellycine.shared.util.image.JellyfinPosterImage
import com.jellycine.app.cast.CastController
import com.jellycine.app.ui.screens.cast.CastPlayback
import com.jellycine.app.ui.screens.cast.loadCastPlaybackData
import com.jellycine.app.ui.components.common.ScreenCastButton
import com.jellycine.shared.ui.components.common.*
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaStream
import com.jellycine.data.model.HomeLibrarySectionData
import com.jellycine.data.model.PersistedHomeSnapshot
import com.jellycine.data.model.SeerrCatalog
import com.jellycine.data.model.SeerrCatalogItem
import com.jellycine.data.network.NetworkModule
import com.jellycine.data.network.NetworkStatus
import com.jellycine.data.network.isAuthenticationStatusFailure
import com.jellycine.data.network.sameServerUrl
import com.jellycine.data.network.trimTrailingSlash
import com.jellycine.data.preferences.NetworkPreferences
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.data.repository.SeerrRepository
import com.jellycine.app.ui.screens.auth.ServerSwitchDialogsHost
import com.jellycine.app.ui.screens.auth.ServerSwitchViewModel
import com.jellycine.app.ui.screens.auth.rememberServerSwitchDialogsState
import com.jellycine.app.ui.screens.dashboard.settings.DownloadsScreen
import com.jellycine.player.preferences.PlayerPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.res.stringResource
import com.jellycine.shared.R
import com.jellycine.shared.resources.Res
import com.jellycine.shared.resources.dashboard_failed_next_up
import com.jellycine.shared.resources.dashboard_next_up
import com.jellycine.app.ui.screens.dashboard.PosterSkeleton
import com.jellycine.app.ui.screens.dashboard.GenreSectionSkeleton
import com.jellycine.detail.CodecUtils
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Immutable
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.draw.drawWithCache
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import coil3.request.*
import com.jellycine.shared.playback.UserDataRefreshSignals
import com.jellycine.shared.ui.screens.dashboard.home.*
import com.jellycine.data.repository.SavedServer

@Stable
@Immutable
data class QueryState<T>(
    val data: T? = null,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val error: String? = null,
    val isStale: Boolean = false,
    val lastFetched: Long = 0L
) {
    val isSuccess: Boolean get() = data != null && !isError
    val isEmpty: Boolean get() = data == null && !isLoading && !isError
}

@Stable
@Immutable
data class QueryConfig(
    val staleTime: Long = 300_000L,
    val retryCount: Int = 1,
    val retryDelay: Long = 250L,
    val enabled: Boolean = true,
    val requestTimeoutMs: Long = NetworkPreferences.DEFAULT_REQUEST_TIMEOUT_MS.toLong()
)

class QueryManager(private val scope: CoroutineScope) {
    private val queries = mutableMapOf<String, QueryState<*>>()
    private val jobs = mutableMapOf<String, Job>()

    @Suppress("UNCHECKED_CAST")
    fun <T> getQuery(key: String): QueryState<T> {
        return queries[key] as? QueryState<T> ?: QueryState()
    }

    fun <T> setQuery(key: String, state: QueryState<T>) {
        queries[key] = state
    }

    fun <T> executeQuery(
        key: String,
        config: QueryConfig = QueryConfig(),
        fetcher: suspend () -> T
    ): QueryState<T> {
        val currentState = getQuery<T>(key)

        if (!config.enabled) {
            return currentState
        }

        val isStale = System.currentTimeMillis() - currentState.lastFetched > config.staleTime
        val hasNonEmptyData = when (val data = currentState.data) {
            is Collection<*> -> data.isNotEmpty()
            is Map<*, *> -> data.isNotEmpty()
            else -> data != null
        }
        if (hasNonEmptyData && !isStale) {
            return currentState
        }

        val isAlreadyFetching = jobs[key]?.isActive == true
        if (isAlreadyFetching) {
            return currentState
        }

        val newState = currentState.copy(isLoading = true, isError = false, error = null)
        setQuery(key, newState)

        jobs[key] = scope.launch {
            var lastException: Exception? = null
            var retryCount = 0

            while (retryCount <= config.retryCount) {
                try {
                    val result = withTimeout(config.requestTimeoutMs) {
                        withContext(Dispatchers.IO) { fetcher() }
                    }
                    setQuery(key, QueryState(
                        data = result,
                        isLoading = false,
                        isError = false,
                        error = null,
                        lastFetched = System.currentTimeMillis()
                    ))
                    break
                } catch (e: Exception) {
                    lastException = e
                    retryCount++

                    val Retry = when {
                        e.message?.contains("timeout", ignoreCase = true) == true -> true
                        e.message?.contains("connection", ignoreCase = true) == true -> true
                        e.message?.contains("network", ignoreCase = true) == true -> true
                        e.message?.contains("failed to connect", ignoreCase = true) == true -> true
                        e.message?.contains("unable to resolve host", ignoreCase = true) == true -> true
                        e is TimeoutCancellationException -> false
                        retryCount <= config.retryCount -> true
                        else -> false
                    }

                    if (Retry && retryCount <= config.retryCount) {
                        delay(config.retryDelay * retryCount)
                    } else {
                        setQuery(key, QueryState<T>(
                            data = currentState.data,
                            isLoading = false,
                            isError = true,
                            error = lastException.message ?: "Unknown error",
                            lastFetched = currentState.lastFetched
                        ))
                        break
                    }
                }
            }
        }

        return newState
    }

    fun invalidateQuery(key: String) {
        val currentState = queries[key]
        if (currentState != null) {
            queries[key] = currentState.copy(isStale = true, lastFetched = 0L)
        }
    }

    fun cancelQuery(key: String) {
        jobs[key]?.cancel()
        jobs.remove(key)
    }

    fun refreshStaleQueries() {
        val currentTime = System.currentTimeMillis()
        queries.forEach { (key, state) ->
            val isStale = currentTime - state.lastFetched > 300_000L
            if (isStale && !state.isLoading) {
                invalidateQuery(key)
            }
        }
    }

    fun cleanup() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        queries.clear()
    }
}

@Composable
fun <T> useQuery(
    key: String,
    config: QueryConfig = QueryConfig(),
    fetcher: suspend () -> T
): QueryState<T> {
    val queryManager = LocalQueryManager.current
    var state by remember(key, queryManager) { mutableStateOf(queryManager.getQuery<T>(key)) }
    var hasInitiated by remember(key, queryManager) { mutableStateOf(false) }

    LaunchedEffect(key, config.enabled, queryManager) {
        if (!config.enabled) return@LaunchedEffect

        hasInitiated = true
        state = queryManager.executeQuery(key, config, fetcher)

        while (true) {
            delay(24)

            var currentState = queryManager.getQuery<T>(key)
            if (currentState.isStale && !currentState.isLoading) {
                currentState = queryManager.executeQuery(key, config, fetcher)
            }

            if (currentState != state) {
                state = currentState
            }
        }
    }

    return if (!hasInitiated && state.data == null && !state.isError) {
        state.copy(isLoading = true)
    } else {
        state
    }
}

val LocalQueryManager = compositionLocalOf<QueryManager> {
    error("QueryManager not provided")
}

private object DashboardHomeQueryStore {
    private var ownerSessionKey: String? = null
    private var manager: QueryManager? = null

    @Synchronized
    fun get(sessionKey: String?): QueryManager {
        val existing = manager
        if (existing != null) {
            if (sessionKey == null) {
                return existing
            }
            if (ownerSessionKey == sessionKey) {
                return existing
            }
        }
        manager?.cleanup()
        ownerSessionKey = sessionKey
        manager = QueryManager(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        return manager!!
    }

}

data class Movie(
    val id: String,
    val title: String,
    val year: String,
    val posterUrl: String? = null
)

@Composable
fun Dashboard(
    onLogout: () -> Unit = {},
    onNavigateToDetail: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> },
    onNavigateToPlayer: (String) -> Unit = {},
    onAddServer: () -> Unit = {},
    onAddUser: (serverUrl: String, serverName: String?) -> Unit = { _, _ -> },
    isTabActive: Boolean = true,
    dashboardScrollState: LazyListState? = null
) {
    var selectedCategory by rememberSaveable { mutableStateOf(HomeCategory.HOME) }
    var showAccountOverview by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val mediaRepository = remember { com.jellycine.data.repository.MediaRepositoryProvider.getInstance(context) }
    val seerrRepository = remember(appContext) { SeerrRepository(appContext) }
    val downloadRepository = remember { DownloadRepositoryProvider.getInstance(context) }
    val preferences = remember { Preferences(context) }
    val authRepository = remember { com.jellycine.data.repository.AuthRepositoryProvider.getInstance(context) }
    val scope = rememberCoroutineScope()
    val serverSwitchViewModel: ServerSwitchViewModel = viewModel {
        ServerSwitchViewModel(context.applicationContext as android.app.Application)
    }
    val serverSwitchUiState by serverSwitchViewModel.uiState.collectAsStateWithLifecycle()
    val networkRequestTimeoutMs = NetworkPreferences(context).getTimeoutConfig().requestTimeoutMs.toLong()
    val networkAvailabilityFlow = remember(appContext, preferences) {
        combine(
            NetworkModule.observeNetworkAvailability(appContext),
            preferences.ForceOfflineEnabled()
        ) { status, forceOffline ->
            if (forceOffline) false else status.isAvailable
        }
    }
    val isNetworkAvailable by networkAvailabilityFlow.collectAsStateWithLifecycle(
        initialValue = (NetworkModule.isInternetAvailable(appContext) ||
            NetworkModule.hasLocalNetworkTransport(appContext)) &&
            !preferences.isForceOfflineEnabled()
    )
    val featureCarouselEnabled by preferences.FeatureCarouselEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isFeatureCarouselEnabled()
        )
    val featureCarouselHeight by preferences.FeatureCarouselHeight()
        .collectAsStateWithLifecycle(
            initialValue = preferences.getFeatureCarouselHeight()
        )
    val autoplayTrailersEnabled by preferences.autoplayTrailersEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isAutoplayTrailersEnabled()
        )
    val continueWatchingEnabled by preferences.ContinueWatchingEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isContinueWatchingEnabled()
        )
    val nextUpEnabled by preferences.NextUpEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isNextUpEnabled()
        )
    val seerrStudiosEnabled by preferences.SeerrStudiosEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isSeerrStudiosEnabled()
        )
    val seerrNetworksEnabled by preferences.SeerrNetworksEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isSeerrNetworksEnabled()
        )
    val posterEnhancersEnabled by preferences.PosterEnhancersEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isPosterEnhancersEnabled()
        )
    val trackedDownloads by downloadRepository.observeTrackedDownloads().collectAsState(initial = emptyList())
    val serverSwitchDialogsState = rememberServerSwitchDialogsState()
    val userDataRefreshEvent by UserDataRefreshSignals.refreshEvent.collectAsState()

    LaunchedEffect(featureCarouselEnabled) {
        if (!featureCarouselEnabled && selectedCategory != HomeCategory.HOME) {
            selectedCategory = HomeCategory.HOME
        }
    }

    var persistedHomeSnapshot by remember {
        mutableStateOf<PersistedHomeSnapshot?>(mediaRepository.getPersistedHomeSnapshot())
    }
    val sessionSnapshot by authRepository.observeActiveSession().collectAsState(
        initial = authRepository.getActiveSessionSnapshot()
    )
    val activeSavedServer = remember(sessionSnapshot.savedServers, sessionSnapshot.activeServerId) {
        sessionSnapshot.savedServers.firstOrNull { savedServer ->
            savedServer.id == sessionSnapshot.activeServerId
        }
    }

    val currentUsername = sessionSnapshot.username ?: persistedHomeSnapshot?.username
    val currentServerName = sessionSnapshot.serverName ?: persistedHomeSnapshot?.serverName
    val currentServerUrl = sessionSnapshot.serverUrl ?: persistedHomeSnapshot?.serverUrl
    val currentServerType = sessionSnapshot.serverType
    val currentProfileImageUrl = activeSavedServer?.profileImageUrl ?: persistedHomeSnapshot?.profileImageUrl
    val isEmbyServer = currentServerType.equals("EMBY", ignoreCase = true)
    val disablePosterEnhancers = isEmbyServer && posterEnhancersEnabled
    val dashboardSessionKey = remember(currentServerUrl, currentUsername) {
        "${currentServerUrl?.let(::trimTrailingSlash).orEmpty()}|${currentUsername.orEmpty()}"
    }
    val HeaderUserName = currentUsername
        ?.takeIf { it.isNotBlank() }
        ?: "User"
    var noCarouselProfileImageUrl by remember(dashboardSessionKey) {
        mutableStateOf(persistedHomeSnapshot?.profileImageUrl)
    }
    val usersForCurrentServer = remember(
        sessionSnapshot.savedServers,
        currentServerUrl,
        sessionSnapshot.activeServerId
    ) {
        sessionSnapshot.savedServers
            .filter { savedServer ->
                currentServerUrl != null &&
                    sameServerUrl(savedServer.serverUrl, currentServerUrl)
            }
            .sortedWith(
                compareByDescending<com.jellycine.data.repository.SavedServer> {
                    it.id == sessionSnapshot.activeServerId
                }.thenBy { it.username.lowercase() }
            )
    }
    LaunchedEffect(featureCarouselEnabled, dashboardSessionKey, HeaderUserName) {
        if (featureCarouselEnabled) return@LaunchedEffect

        if (!noCarouselProfileImageUrl.isNullOrBlank()) return@LaunchedEffect

        val user = withContext(Dispatchers.IO) {
            try {
                mediaRepository.getCurrentUser().getOrNull()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
        currentCoroutineContext().ensureActive()
        val profileUrl = withContext(Dispatchers.IO) {
            try {
                mediaRepository.getUserProfileImageUrl(user?.primaryImageTag)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
        currentCoroutineContext().ensureActive()
        noCarouselProfileImageUrl = profileUrl
    }
    val queryManager = remember(dashboardSessionKey) {
        DashboardHomeQueryStore.get(dashboardSessionKey)
    }
    var previousSessionKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dashboardSessionKey, isNetworkAvailable) {
        if (previousSessionKey != null && previousSessionKey != dashboardSessionKey) {
            Cache.clear()
        }
        previousSessionKey = dashboardSessionKey
        persistedHomeSnapshot = mediaRepository.loadPersistedHomeSnapshot()
    }

    LaunchedEffect(dashboardSessionKey, persistedHomeSnapshot) {
        val snapshot = persistedHomeSnapshot ?: return@LaunchedEffect
        val persistedProfileUrl = snapshot.profileImageUrl
        if (!persistedProfileUrl.isNullOrBlank() && noCarouselProfileImageUrl.isNullOrBlank()) {
            noCarouselProfileImageUrl = persistedProfileUrl
        }
    }

    LaunchedEffect(dashboardSessionKey, isNetworkAvailable) {
        if (!isNetworkAvailable) return@LaunchedEffect

        val userResult = mediaRepository.getCurrentUser()
        currentCoroutineContext().ensureActive()
        val forceLogout = if (userResult.isSuccess) {
            val user = userResult.getOrNull()
            val isVideoTranscodingEnabled = user?.policy?.enableVideoPlaybackTranscoding ?: user?.let { true }
            val isAudioTranscodingEnabled = user?.policy?.enableAudioPlaybackTranscoding ?: user?.let { true }
            val isSyncTranscodingEnabled = user?.policy?.enableSyncTranscoding ?: user?.let { true }
            val canManageSubtitlesEnabled = user?.policy?.let {
                it.enableSubtitleDownloading ?: it.enableSubtitleManagement
            }
            val profileUrl = try {
                mediaRepository.getUserProfileImageUrl(user?.primaryImageTag)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            currentCoroutineContext().ensureActive()
            if (!profileUrl.isNullOrBlank()) {
                noCarouselProfileImageUrl = profileUrl
            }
            authRepository.updateActiveServerProfileImage(
                profileImageUrl = profileUrl ?: persistedHomeSnapshot?.profileImageUrl
            )
            val activeUsername = currentUsername ?: persistedHomeSnapshot?.username
            mediaRepository.persistHomeSnapshot(
                username = activeUsername,
                serverName = currentServerName,
                serverUrl = currentServerUrl,
                profileImageUrl = profileUrl,
                isAdministrator = user?.policy?.isAdministrator,
                isVideoTranscodingAllowed = isVideoTranscodingEnabled,
                isAudioTranscodingAllowed = isAudioTranscodingEnabled,
                isSyncTranscodingAllowed = isSyncTranscodingEnabled,
                canManageSubtitles = canManageSubtitlesEnabled,
                subtitleLanguagePreference = user?.configuration?.subtitleLanguagePreference
            )
            user?.policy?.isDisabled == true
        } else {
            userResult.exceptionOrNull().isAuthenticationStatusFailure()
        }

        if (forceLogout) {
            authRepository.logout()
            mediaRepository.clearPersistedHomeSnapshot()
            CachedData.clearAllCache()
            onLogout()
        }
    }

    val lazyColumnState = dashboardScrollState ?: rememberLazyListState()

    CompositionLocalProvider(LocalQueryManager provides queryManager) {

        val featuredQuery = useQuery(
            key = "featured_$selectedCategory",
            config = QueryConfig(
                staleTime = 300_000L,
                enabled = isTabActive && isNetworkAvailable && featureCarouselEnabled,
                requestTimeoutMs = networkRequestTimeoutMs
            )
        ) {
            val result = when (selectedCategory) {
                HomeCategory.MOVIES -> mediaRepository.getUserItems(
                    parentId = null,
                    includeItemTypes = "Movie",
                    sortBy = "Random",
                    sortOrder = null,
                    limit = 10,
                    startIndex = 0,
                    recursive = true,
                    fields = "BasicSyncInfo,Genres,CommunityRating,CriticRating,ProductionYear,PremiereDate,OfficialRating,Overview,RemoteTrailers"
                )
                HomeCategory.TV_SHOWS -> mediaRepository.getUserItems(
                    parentId = null,
                    includeItemTypes = "Series",
                    sortBy = "Random",
                    sortOrder = null,
                    limit = 10,
                    startIndex = 0,
                    recursive = true,
                    fields = "BasicSyncInfo,Genres,CommunityRating,CriticRating,ProductionYear,PremiereDate,OfficialRating,Overview,RemoteTrailers"
                )
                else -> mediaRepository.getUserItems(
                    parentId = null,
                    includeItemTypes = "Movie,Series",
                    sortBy = "Random",
                    sortOrder = null,
                    limit = 10,
                    startIndex = 0,
                    recursive = true,
                    fields = "BasicSyncInfo,Genres,CommunityRating,CriticRating,ProductionYear,PremiereDate,OfficialRating,Overview,RemoteTrailers"
                )
            }

            result.fold(
                onSuccess = { query ->
                    query.items
                        .orEmpty()
                        .filter { it.id != null && !it.name.isNullOrBlank() }
                },
                onFailure = { throw it }
            )
        }

        val continueWatchingQuery = useQuery(
            key = "continue_watching_resume_api_v2",
            config = QueryConfig(
                staleTime = 60_000L,
                enabled = isTabActive && selectedCategory == HomeCategory.HOME && isNetworkAvailable && continueWatchingEnabled,
                retryCount = 2,
                retryDelay = 250L,
                requestTimeoutMs = networkRequestTimeoutMs
            )
        ) {
            val result = mediaRepository.getResumeItems(limit = 24)
            result.fold(
                onSuccess = { items ->
                    val seenKeys = mutableSetOf<String>()
                    val validItems = items.filter { item ->
                        item.id != null && !item.name.isNullOrBlank()
                    }.filter { item ->
                        val keys = when {
                            item.type.equals("Episode", ignoreCase = true) -> buildSet {
                                item.providerIds?.get("Tvdb")?.let { add("tvdb:$it") }
                                add("${item.seriesName ?: item.name}:S${item.parentIndexNumber}E${item.indexNumber}")
                            }
                            else -> buildSet {
                                item.providerIds?.get("Tmdb")?.let { add("tmdb:$it") }
                                item.providerIds?.get("Imdb")?.let { add("imdb:$it") }
                                add("${item.name}:${item.productionYear}")
                            }
                        }
                        if (keys.any { it in seenKeys }) {
                            false
                        } else {
                            seenKeys.addAll(keys)
                            true
                        }
                    }
                    if (validItems.isNotEmpty()) {
                        ImagePreloader.continueWatchingImages(
                            items = validItems,
                            mediaRepository = mediaRepository,
                            context = context,
                            maxItems = minOf(validItems.size, 12),
                            priorityCount = minOf(validItems.size, 6)
                        )
                    }
                    validItems
                },
                onFailure = { throw it }
            )
        }

        val nextUpQuery = useQuery(
            key = "home_next_up_api_v1",
            config = QueryConfig(
                staleTime = 60_000L,
                enabled = isTabActive && selectedCategory == HomeCategory.HOME && isNetworkAvailable && nextUpEnabled,
                retryCount = 2,
                retryDelay = 250L,
                requestTimeoutMs = networkRequestTimeoutMs
            )
        ) {
            val result = mediaRepository.getNextUpItems(limit = 24)
            result.fold(
                onSuccess = { items ->
                    val seenNextUpKeys = mutableSetOf<String>()
                    val validItems = items
                        .filter { item ->
                            item.id != null &&
                                (
                                    !item.name.isNullOrBlank() ||
                                        !item.episodeTitle.isNullOrBlank() ||
                                        !item.seriesName.isNullOrBlank()
                                    )
                        }
                        .filter { item ->
                            val keys = when {
                                item.type.equals("Episode", ignoreCase = true) -> buildSet {
                                    item.providerIds?.get("Tvdb")?.let { add("tvdb:$it") }
                                    add("${item.seriesName ?: item.name}:S${item.parentIndexNumber}E${item.indexNumber}")
                                }
                                else -> buildSet {
                                    item.providerIds?.get("Tmdb")?.let { add("tmdb:$it") }
                                    item.providerIds?.get("Imdb")?.let { add("imdb:$it") }
                                    add("${item.name}:${item.productionYear}")
                                }
                            }
                            if (keys.any { it in seenNextUpKeys }) {
                                false
                            } else {
                                seenNextUpKeys.addAll(keys)
                                true
                            }
                        }
                    if (validItems.isNotEmpty()) {
                        ImagePreloader.continueWatchingImages(
                            items = validItems,
                            mediaRepository = mediaRepository,
                            context = context,
                            maxItems = minOf(validItems.size, 12),
                            priorityCount = minOf(validItems.size, 6)
                        )
                    }
                    validItems
                },
                onFailure = { throw it }
            )
        }

        val homeLibraryBurstQuery = useQuery(
            key = "home_library_burst",
            config = QueryConfig(
                staleTime = 300_000L,
                enabled = isTabActive && selectedCategory == HomeCategory.HOME && isNetworkAvailable,
                retryCount = 1,
                retryDelay = 200L,
                requestTimeoutMs = networkRequestTimeoutMs
            )
        ) {
            val result = mediaRepository.getHomeLibrarySections(
                maxLibraries = null,
                itemsPerLibrary = 14
            )
            result.fold(
                onSuccess = { sections ->
                    sections.mapNotNull { section ->
                        val libraryId = section.library.id ?: return@mapNotNull null
                        HomeLibrarySectionUi(
                            libraryId = libraryId,
                            libraryName = section.library.name ?: "Library",
                            collectionType = section.library.collectionType,
                            items = section.items
                        )
                    }
                },
                onFailure = { throw it }
            )
        }

        val homeMyMediaLibrariesQuery = useQuery(
            key = "home_my_media_libraries",
            config = QueryConfig(
                staleTime = 300_000L,
                enabled = isTabActive && selectedCategory == HomeCategory.HOME && isNetworkAvailable,
                retryCount = 1,
                retryDelay = 200L,
                requestTimeoutMs = networkRequestTimeoutMs
            )
        ) {
            val result = mediaRepository.getUserViews()
            result.fold(
                onSuccess = { queryResult ->
                    queryResult.items
                        .orEmpty()
                        .asSequence()
                        .filter { library ->
                            val libraryId = library.id
                            val libraryName = library.name
                            val type = library.type
                            val collectionType = library.collectionType
                            libraryId != null &&
                                !libraryName.isNullOrBlank() &&
                                collectionType != "playlists" &&
                                collectionType != "folders" &&
                                (type == "CollectionFolder" || type == "Folder")
                        }
                        .distinctBy { it.id }
                        .toList()
                },
                onFailure = { throw it }
            )
        }

        val localStudioNamesQuery = useQuery(
            key = "home_local_studio_names",
            config = QueryConfig(
                staleTime = 300_000L,
                enabled = isTabActive && selectedCategory == HomeCategory.HOME && isNetworkAvailable,
                retryCount = 1,
                retryDelay = 200L,
                requestTimeoutMs = networkRequestTimeoutMs
            )
        ) {
            mediaRepository.getAvailableStudioNames()
        }
        val localStudioNames = localStudioNamesQuery.data

        val persistedFeaturedItems = if (selectedCategory == HomeCategory.HOME && isNetworkAvailable) {
            persistedHomeSnapshot?.featuredHomeItems.orEmpty()
        } else {
            emptyList()
        }
        val FeaturedItems = featuredQuery.data ?: persistedFeaturedItems

        val persistedContinueWatchingItems = if (selectedCategory == HomeCategory.HOME && isNetworkAvailable && continueWatchingEnabled) {
            persistedHomeSnapshot?.continueWatchingItems.orEmpty()
        } else {
            emptyList()
        }
        val ContinueWatchingItems = continueWatchingQuery.data ?: persistedContinueWatchingItems

        val persistedNextUpItems = if (selectedCategory == HomeCategory.HOME && isNetworkAvailable && nextUpEnabled) {
            persistedHomeSnapshot?.nextUpItems.orEmpty()
        } else {
            emptyList()
        }
        val NextUpItems = nextUpQuery.data ?: persistedNextUpItems

        val persistedLibrarySections = if (selectedCategory == HomeCategory.HOME && isNetworkAvailable) {
            persistedHomeSnapshot?.homeLibrarySections
                .orEmpty()
                .map { it.toUiSection() }
        } else {
            emptyList()
        }
        val LibrarySections = homeLibraryBurstQuery.data ?: persistedLibrarySections

        val persistedMyMediaLibraries = if (selectedCategory == HomeCategory.HOME && isNetworkAvailable) {
            persistedHomeSnapshot?.myMediaLibraries.orEmpty()
        } else {
            emptyList()
        }
        val MyMediaLibraries = if (selectedCategory == HomeCategory.HOME) {
            homeMyMediaLibrariesQuery.data ?: persistedMyMediaLibraries
        } else {
            emptyList()
        }
        val seerrStudios = remember { SeerrCatalog.popularStudios(limit = 12) }
        val seerrNetworks = remember { SeerrCatalog.popularNetworks() }

        fun availableInLibrary(items: List<SeerrCatalogItem>): List<SeerrCatalogItem> {
            val names = localStudioNames ?: return emptyList()
            return items.filter { item ->
                item.localMatchNames.any { it.trim().lowercase() in names }
            }
        }
        val SeerrStudios = if (
            selectedCategory == HomeCategory.HOME && seerrStudiosEnabled
        ) {
            availableInLibrary(seerrStudios)
        } else {
            emptyList()
        }
        val SeerrNetworks = if (
            selectedCategory == HomeCategory.HOME && seerrNetworksEnabled
        ) {
            availableInLibrary(seerrNetworks)
        } else {
            emptyList()
        }

        LaunchedEffect(isTabActive, isNetworkAvailable) {
            if (isTabActive && isNetworkAvailable) {
                queryManager.refreshStaleQueries()
            }
        }
        LaunchedEffect(isTabActive, selectedCategory, isNetworkAvailable) {
            if (isTabActive && selectedCategory == HomeCategory.HOME && isNetworkAvailable) {
                queryManager.invalidateQuery("home_library_burst")
                if (continueWatchingEnabled) {
                    val cachedContinueWatching =
                        queryManager.getQuery<List<BaseItemDto>>("continue_watching_resume_api_v2")
                    if (cachedContinueWatching.data.isNullOrEmpty()) {
                        queryManager.invalidateQuery("continue_watching_resume_api_v2")
                    }
                }
                if (nextUpEnabled) {
                    val cachedNextUp = queryManager.getQuery<List<BaseItemDto>>("home_next_up_api_v1")
                    if (cachedNextUp.data.isNullOrEmpty()) {
                        queryManager.invalidateQuery("home_next_up_api_v1")
                    }
                }
            }
        }
        LaunchedEffect(
            userDataRefreshEvent,
            continueWatchingEnabled,
            nextUpEnabled
        ) {
            if (userDataRefreshEvent == null) return@LaunchedEffect

            queryManager.invalidateQuery("home_library_burst")
            if (continueWatchingEnabled) {
                queryManager.invalidateQuery("continue_watching_resume_api_v2")
            }
            if (nextUpEnabled) {
                queryManager.invalidateQuery("home_next_up_api_v1")
            }
        }
        LaunchedEffect(continueWatchingQuery.data?.hashCode(), isNetworkAvailable) {
            if (!isNetworkAvailable) return@LaunchedEffect
            val items = continueWatchingQuery.data ?: return@LaunchedEffect
            mediaRepository.persistHomeSnapshot(continueWatchingItems = items)
            if (items.isEmpty()) return@LaunchedEffect
            ImagePreloader.continueWatchingImages(
                items = items,
                mediaRepository = mediaRepository,
                context = context,
                maxItems = items.size.coerceAtMost(4),
                priorityCount = 4
            )
        }

        LaunchedEffect(nextUpQuery.data?.hashCode(), isNetworkAvailable) {
            if (!isNetworkAvailable) return@LaunchedEffect
            val items = nextUpQuery.data ?: return@LaunchedEffect
            mediaRepository.persistHomeSnapshot(nextUpItems = items)
            if (items.isEmpty()) return@LaunchedEffect
            ImagePreloader.continueWatchingImages(
                items = items,
                mediaRepository = mediaRepository,
                context = context,
                maxItems = items.size.coerceAtMost(4),
                priorityCount = 4
            )
        }

        LaunchedEffect(featuredQuery.data?.hashCode(), selectedCategory, isNetworkAvailable) {
            if (selectedCategory != HomeCategory.HOME || !isNetworkAvailable) return@LaunchedEffect
            val items = featuredQuery.data ?: return@LaunchedEffect
            mediaRepository.persistHomeSnapshot(featuredHomeItems = items)
        }

        LaunchedEffect(homeLibraryBurstQuery.data?.hashCode(), isNetworkAvailable) {
            if (!isNetworkAvailable) return@LaunchedEffect
            val sections = homeLibraryBurstQuery.data ?: return@LaunchedEffect
            if (sections.isEmpty()) return@LaunchedEffect
            mediaRepository.persistHomeSnapshot(
                homeLibrarySections = sections.map { it.toPersistedSection() }
            )

            val orderedItems = sections
                .flatMap { section -> section.items }
                .distinctBy { it.id }
            if (orderedItems.isEmpty()) return@LaunchedEffect

            ImagePreloader.preloadCriticalImages(
                items = orderedItems,
                mediaRepository = mediaRepository,
                context = context,
                maxItems = orderedItems.size
            )
        }

        LaunchedEffect(homeMyMediaLibrariesQuery.data?.hashCode(), isNetworkAvailable) {
            if (!isNetworkAvailable) return@LaunchedEffect
            val libraries = homeMyMediaLibrariesQuery.data ?: return@LaunchedEffect
            mediaRepository.persistHomeSnapshot(myMediaLibraries = libraries)
        }

        LaunchedEffect(
            selectedCategory,
            MyMediaLibraries.hashCode(),
            isNetworkAvailable
        ) {
            if (selectedCategory != HomeCategory.HOME || !isNetworkAvailable) return@LaunchedEffect
            if (MyMediaLibraries.isEmpty()) return@LaunchedEffect
            ImagePreloader.MyMedia(
                libraries = MyMediaLibraries,
                mediaRepository = mediaRepository,
                context = context,
                maxItems = MyMediaLibraries.size
            )
        }

        val featureParallaxOffsetPx by remember {
            derivedStateOf {
                if (lazyColumnState.firstVisibleItemIndex == 0) {
                    lazyColumnState.firstVisibleItemScrollOffset.toFloat()
                } else {
                    0f
                }
            }
        }

        if (!isNetworkAvailable) {
            if (trackedDownloads.any { it.isOfflineAvailable }) {
                DownloadsScreen(
                    onBackPressed = {},
                    embedded = true,
                    onPlayItem = onNavigateToPlayer
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    OfflineState(serverName = currentServerName)
                }
            }
        }

        if (isNetworkAvailable) {
            LazyColumn(
                state = lazyColumnState,
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(1.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                if (featureCarouselEnabled) {
                    item(key = "feature_tab") {
                        FeatureTab(
                            featuredItems = FeaturedItems,
                            isLoading = featuredQuery.isLoading && FeaturedItems.isEmpty(),
                            error = featuredQuery.error,
                            selectedCategory = selectedCategory,
                            carouselHeight = featureCarouselHeight,
                            verticalParallaxOffsetPx = featureParallaxOffsetPx,
                            onItemClick = onNavigateToDetail,
                            onLogout = onLogout,
                            onProfileClick = { showAccountOverview = true },
                            onCategorySelected = { category ->
                                selectedCategory = category
                            }
                        )
                    }
                } else {
                    item(key = "no_carousel_top_header") {
                        TopHeader(
                            serverName = currentServerName,
                            serverTypeRaw = currentServerType,
                            userName = HeaderUserName,
                            userImageUrl = noCarouselProfileImageUrl,
                            onProfileClick = { showAccountOverview = true },
                            onServerClick = serverSwitchDialogsState::openServers
                        )
                    }
                }

                val ShowMyMediaSection =
                    selectedCategory == HomeCategory.HOME && (
                        MyMediaLibraries.isNotEmpty() ||
                            (homeMyMediaLibrariesQuery.isLoading && MyMediaLibraries.isEmpty())
                        )

                val ShowContinueWatchingSection =
                    continueWatchingEnabled && selectedCategory == HomeCategory.HOME && (
                        ContinueWatchingItems.isNotEmpty() ||
                            continueWatchingQuery.isError ||
                            (continueWatchingQuery.isLoading && persistedContinueWatchingItems.isNotEmpty())
                        )

                val ShowNextUpSection =
                    nextUpEnabled && selectedCategory == HomeCategory.HOME && (
                        NextUpItems.isNotEmpty() ||
                            nextUpQuery.isError ||
                            (nextUpQuery.isLoading && persistedNextUpItems.isNotEmpty())
                        )

                if (ShowMyMediaSection) {
                    item(key = "my_media_section") {
                        HomeMyMediaSection(
                            libraries = MyMediaLibraries,
                            isLoading = homeMyMediaLibrariesQuery.isLoading && MyMediaLibraries.isEmpty(),
                            error = if (homeMyMediaLibrariesQuery.isError) homeMyMediaLibrariesQuery.error else null,
                            mediaRepository = mediaRepository,
                            disablePosterEnhancers = disablePosterEnhancers,
                            onLibraryClick = { library ->
                                val contentType = when (library.collectionType) {
                                    "movies" -> "MOVIES"
                                    "tvshows" -> "SERIES"
                                    else -> "ALL"
                                }
                                onNavigateToViewAll(
                                    contentType,
                                    library.id,
                                    library.name ?: "Library"
                                )
                            }
                        )
                    }
                }

                if (SeerrStudios.isNotEmpty()) {
                    item(key = "seerr_studios_section") {
                        SeerrCatalogSection(
                            title = "Studios",
                            items = SeerrStudios,
                            onItemClick = { studio ->
                                onNavigateToViewAll(
                                    "SEERR_STUDIO",
                                    studio.id,
                                    studio.name
                                )
                            }
                        )
                    }
                }

                if (ShowContinueWatchingSection) {
                    item(key = "continue_watching_section") {
                        Column(
                            modifier = Modifier
                                .padding(top = 0.dp)
                                .offset(y = if (SeerrStudios.isNotEmpty()) 0.dp else (-12).dp)
                        ) {
                            ContinueWatchingSection(
                                items = ContinueWatchingItems,
                                isLoading = continueWatchingQuery.isLoading && ContinueWatchingItems.isEmpty(),
                                error = if (continueWatchingQuery.isError) continueWatchingQuery.error else null,
                                mediaRepository = mediaRepository,
                                onItemClick = onNavigateToDetail
                            )
                        }
                    }
                }

                if (SeerrNetworks.isNotEmpty()) {
                    item(key = "seerr_networks_section") {
                        SeerrCatalogSection(
                            title = "Networks",
                            items = SeerrNetworks,
                            onItemClick = { network ->
                                onNavigateToViewAll(
                                    "SEERR_NETWORK",
                                    network.id,
                                    network.name
                                )
                            }
                        )
                    }
                }

                if (ShowNextUpSection) {
                    item(key = "next_up_section") {
                        Column(
                            modifier = Modifier
                                .padding(top = 0.dp)
                                .offset(y = (-12).dp)
                        ) {
                            ContinueWatchingSection(
                                titleRes = Res.string.dashboard_next_up,
                                errorMessageRes = Res.string.dashboard_failed_next_up,
                                items = NextUpItems,
                                isLoading = nextUpQuery.isLoading && NextUpItems.isEmpty(),
                                error = if (nextUpQuery.isError) nextUpQuery.error else null,
                                mediaRepository = mediaRepository,
                                onItemClick = onNavigateToDetail
                            )
                        }
                    }
                }

                if (selectedCategory == HomeCategory.HOME) {
                    val topPadding = if (!ShowMyMediaSection && !ShowContinueWatchingSection && !ShowNextUpSection) 16.dp else 0.dp

                    if (topPadding > 0.dp) {
                        item(key = "home_libraries_top_padding") {
                            Spacer(modifier = Modifier.height(topPadding))
                        }
                    }

                    val libraries = LibrarySections
                    if (homeLibraryBurstQuery.isError && libraries.isEmpty()) {
                        item(key = "home_libraries_error") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.dashboard_failed_load_libraries),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else if (libraries.isNotEmpty()) {
                        itemsIndexed(
                            items = libraries,
                            key = { index, section -> section.libraryId.ifBlank { "library_$index" } }
                        ) { index, section ->
                            BurstLibrarySection(
                                section = section,
                                mediaRepository = mediaRepository,
                                disablePosterEnhancers = disablePosterEnhancers,
                                onItemClick = onNavigateToDetail,
                                onNavigateToViewAll = onNavigateToViewAll
                            )

                            if (index < libraries.lastIndex) {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                } else if (selectedCategory == HomeCategory.MOVIES) {
                    val topPadding = if (ContinueWatchingItems.isEmpty() && !continueWatchingQuery.isLoading) 16.dp else 0.dp

                    item(key = "movies_genres") {
                        Column(
                            modifier = Modifier.padding(top = topPadding)
                        ) {
                            MovieGenreSections(
                                disablePosterEnhancers = disablePosterEnhancers,
                                onItemClick = onNavigateToDetail,
                                onNavigateToViewAll = onNavigateToViewAll
                            )
                        }
                    }
                } else if (selectedCategory == HomeCategory.TV_SHOWS) {
                    val topPadding = if (ContinueWatchingItems.isEmpty() && !continueWatchingQuery.isLoading) 16.dp else 0.dp

                    item(key = "tv_genres") {
                        Column(
                            modifier = Modifier.padding(top = topPadding)
                        ) {
                            TVShowGenreSections(
                                disablePosterEnhancers = disablePosterEnhancers,
                                onItemClick = onNavigateToDetail,
                                onNavigateToViewAll = onNavigateToViewAll
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAccountOverview) {
        AccountOverview(
            userName = currentUsername,
            serverName = currentServerName,
            profileImageUrl = currentProfileImageUrl ?: noCarouselProfileImageUrl,
            serverTypeRaw = currentServerType,
            canChangeUser = usersForCurrentServer.isNotEmpty(),
            onDismiss = { showAccountOverview = false },
            onChangeUser = {
                showAccountOverview = false
                serverSwitchDialogsState.openUsers(currentServerName, usersForCurrentServer)
            },
            onLogout = {
                showAccountOverview = false
                scope.launch {
                    authRepository.logout()
                    mediaRepository.clearPersistedHomeSnapshot()
                    CachedData.clearAllCache()
                    onLogout()
                }
            }
        )
    }

    ServerSwitchDialogsHost(
        state = serverSwitchDialogsState,
        savedServers = sessionSnapshot.savedServers,
        activeServerId = sessionSnapshot.activeServerId,
        currentServerName = sessionSnapshot.serverName,
        currentServerUrl = sessionSnapshot.serverUrl,
        isSwitching = serverSwitchUiState.isBusy,
        onAddServer = onAddServer,
        onAddUser = onAddUser,
        onServerSelected = { server, dismissDialog ->
            serverSwitchViewModel.switchServer(
                serverId = server.id,
                activeServerId = sessionSnapshot.activeServerId,
                onSwitchComplete = dismissDialog
            )
        },
        onRequestRemoveServer = serverSwitchDialogsState::requestRemoval,
        onRequestRemoveUser = serverSwitchDialogsState::requestRemoval,
        onRemoveServer = { serverId, onRemoveComplete ->
            serverSwitchViewModel.removeServer(
                serverId = serverId,
                onRemoveComplete = onRemoveComplete
            )
        }
    )
}

@Composable
private fun TopHeader(
    serverName: String?,
    serverTypeRaw: String?,
    userName: String?,
    userImageUrl: String?,
    onProfileClick: (() -> Unit)? = null,
    onServerClick: (() -> Unit)? = null
) {
    BrandHeader(
        serverName = serverName,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        onServerClick = onServerClick
    ) {
        HeaderCastActions()
        UserProfileAvatar(
            imageUrl = userImageUrl,
            serverTypeRaw = serverTypeRaw,
            onClick = { onProfileClick?.invoke() },
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
private fun HeaderCastActions() {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val playerPreferences = remember { PlayerPreferences(context) }
    val scope = rememberCoroutineScope()
    val castPlaybackState by CastController.playbackState.collectAsState()
    var showCastPlaybackSheet by remember { mutableStateOf(false) }
    var castPlaybackArtworkUrl by remember { mutableStateOf<String?>(null) }
    var castPlaybackStreams by remember { mutableStateOf<List<MediaStream>>(emptyList()) }
    var castPlaybackAudioStreamIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var castPlaybackSubtitleStreamIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var castTracks by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        CastController.ensureInitialized(context)
    }

    fun clearCastPlaybackState() {
        castPlaybackArtworkUrl = null
        castPlaybackStreams = emptyList()
        castPlaybackAudioStreamIndex = null
        castPlaybackSubtitleStreamIndex = null
        castTracks = false
    }

    suspend fun prepareCastPlaybackState(castItemId: String) {
        val playbackData = loadCastPlaybackData(
            mediaRepository = mediaRepository,
            playerPreferences = playerPreferences,
            itemId = castItemId
        )
        castPlaybackArtworkUrl = playbackData.artworkUrl
        castPlaybackStreams = playbackData.streams
        castPlaybackAudioStreamIndex = playbackData.selectedAudioStreamIndex
        castPlaybackSubtitleStreamIndex = playbackData.selectedSubtitleStreamIndex
    }

    fun openCastPlayback() {
        showCastPlaybackSheet = true
        val castItemId = castPlaybackState.currentItemId
        if (castItemId.isNullOrBlank()) return
        scope.launch {
            prepareCastPlaybackState(castItemId)
        }
    }

    fun updateCastTracks(audioStreamIndex: Int?, subtitleStreamIndex: Int?) {
        if (castTracks) return
        val castItemId = castPlaybackState.currentItemId ?: return

        scope.launch {
            castTracks = true
            try {
                val castResult = CastController.castItem(
                    context = context,
                    mediaRepository = mediaRepository,
                    itemId = castItemId,
                    title = castPlaybackState.mediaTitle,
                    subtitle = castPlaybackState.mediaSubtitle,
                    itemType = null,
                    artworkUrl = castPlaybackState.artworkUrl ?: castPlaybackArtworkUrl,
                    startPositionMs = castPlaybackState.positionMs,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex
                )

                if (castResult.isSuccess) {
                    castPlaybackAudioStreamIndex = audioStreamIndex
                    castPlaybackSubtitleStreamIndex = subtitleStreamIndex
                    playerPreferences.setPreferredAudioStreamIndex(castItemId, audioStreamIndex)
                    playerPreferences.setPreferredSubtitleStreamIndex(castItemId, subtitleStreamIndex)
                }
            } finally {
                castTracks = false
            }
        }
    }

    LaunchedEffect(castPlaybackState.isConnected, castPlaybackState.isCastingMedia) {
        if (showCastPlaybackSheet && !castPlaybackState.isConnected) {
            showCastPlaybackSheet = false
        }
        if (!castPlaybackState.isConnected) {
            clearCastPlaybackState()
        }
    }

    ScreenCastButton(
        onConnectedClick = { openCastPlayback() },
        size = 34.dp
    )

    if (showCastPlaybackSheet) {
        CastPlayback(
            castState = castPlaybackState,
            streams = castPlaybackStreams,
            fallbackArtworkUrl = castPlaybackArtworkUrl,
            selectedAudioStreamIndex = castPlaybackAudioStreamIndex,
            selectedSubtitleStreamIndex = castPlaybackSubtitleStreamIndex,
            isTrackSelectionUpdating = castTracks,
            onDismissRequest = { showCastPlaybackSheet = false },
            onTogglePlayPause = { CastController.togglePlayPause(context) },
            onStopCasting = { CastController.stopPlayback(context) },
            onDisconnect = {
                CastController.disconnect(context)
                showCastPlaybackSheet = false
            },
            onSeekTo = { seekPosition -> CastController.seekTo(context, seekPosition) },
            onTrackSelectionChanged = { audioStreamIndex, subtitleStreamIndex ->
                updateCastTracks(audioStreamIndex, subtitleStreamIndex)
            }
        )
    }
}

@Composable
private fun OfflineState(
    serverName: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(top = 12.dp, bottom = 70.dp)
    ) {
        BrandHeader(serverName = serverName)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.dashboard_offline_empty_title),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.dashboard_offline_empty_message),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun HomeMyMediaSection(
    libraries: List<BaseItemDto>,
    isLoading: Boolean,
    error: String?,
    mediaRepository: MediaRepository,
    disablePosterEnhancers: Boolean,
    onLibraryClick: (BaseItemDto) -> Unit = {}
) {
    val lazyRowState = rememberLazyListState()
    val flingBehavior = ScrollOptimization.rememberUltraSmoothFlingBehavior()
    val firstLibraryId = libraries.firstOrNull()?.id

    LaunchedEffect(firstLibraryId, libraries.size) {
        val hasScrolled = lazyRowState.firstVisibleItemIndex > 0 ||
            lazyRowState.firstVisibleItemScrollOffset > 0
        if (hasScrolled) {
            lazyRowState.scrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        Text(
            text = stringResource(R.string.libraries),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when {
            isLoading -> Unit
            error != null && libraries.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_failed_load_my_media),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
            }
            libraries.isNotEmpty() -> {
                LazyRow(
                    state = lazyRowState,
                    horizontalArrangement = Arrangement.spacedBy(ScrollOptimization.listItemSpacing),
                    contentPadding = ScrollOptimization.optimizedContentPadding,
                    flingBehavior = flingBehavior,
                    modifier = ScrollOptimization.getScrollContainerModifier()
                ) {
                    itemsIndexed(
                        items = libraries,
                        key = { index, library ->
                            "${library.id ?: library.name ?: "library_item"}_$index"
                        }
                    ) { _, library ->
                        val stableOnClick = remember(library.id, library.name) { { onLibraryClick(library) } }
                        LibraryItemCard(
                            item = library,
                            mediaRepository = mediaRepository,
                            disableImageEnhancers = disablePosterEnhancers,
                            useLandscapeLayout = true,
                            onClick = stableOnClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeerrCatalogSection(
    title: String,
    items: List<SeerrCatalogItem>,
    modifier: Modifier = Modifier,
    onItemClick: (SeerrCatalogItem) -> Unit = {}
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp)
        ) {
            itemsIndexed(
                items = items,
                key = { index, item -> "${item.id ?: "genre_${item.name}"}_$index" }
            ) { _, item ->
                Surface(
                    modifier = Modifier
                        .size(width = 124.dp, height = 62.dp)
                        .clickable { onItemClick(item) }
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.10f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(7.dp)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        if (!item.logoUrl.isNullOrBlank()) {
                            JellyfinPosterImage(
                                imageUrl = item.logoUrl,
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                context = context
                            )
                        } else {
                            Text(
                                text = item.name,
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun DashboardPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Dashboard()
    }
}

private object Cache {
    private val cache = mutableMapOf<String, CacheEntry<Any>>()
    private const val MAX_CACHE_SIZE = 50
    private const val MEMORY_CLEANUP_INTERVAL = 300_000L // 5 minutes
    private var lastCleanup = 0L

    @Stable
    @Immutable
    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long,
        val accessCount: Int = 0,
        val lastAccessed: Long = System.currentTimeMillis()
    )

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        cleanupIfNeeded()
        val entry = cache[key] as? CacheEntry<T>
        if (entry != null) {
            cache[key] = entry.copy(
                accessCount = entry.accessCount + 1,
                lastAccessed = System.currentTimeMillis()
            ) as CacheEntry<Any>
        }
        return entry?.data
    }

    fun <T> put(key: String, data: T) {
        cleanupIfNeeded()

        if (cache.size >= MAX_CACHE_SIZE) {
            val oldestKey = cache.entries
                .minByOrNull { it.value.lastAccessed }
                ?.key
            oldestKey?.let { cache.remove(it) }
        }

        cache[key] = CacheEntry(
            data = data as Any,
            timestamp = System.currentTimeMillis()
        )
    }

    fun isStale(key: String, staleTime: Long): Boolean {
        val entry = cache[key] ?: return true
        return System.currentTimeMillis() - entry.timestamp > staleTime
    }

    fun invalidate(key: String) {
        cache.remove(key)
    }

    fun invalidatePattern(pattern: String) {
        val keysToRemove = cache.keys.filter { it.contains(pattern) }
        keysToRemove.forEach { cache.remove(it) }
    }

    private fun cleanupIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastCleanup > MEMORY_CLEANUP_INTERVAL) {
            val cutoff = now - 3_600_000L
            val keysToRemove = cache.entries
                .filter { it.value.lastAccessed < cutoff }
                .map { it.key }
            keysToRemove.forEach { cache.remove(it) }
            lastCleanup = now
        }
    }

    fun clear() {
        cache.clear()
    }

    fun getStats(): String {
        return "Cache size: ${cache.size}/$MAX_CACHE_SIZE"
    }
}

private object GenreCache {
    fun RefreshMovieGenres(): Boolean {
        return Cache.isStale("movie_genres", 600_000L)
    }

    fun RefreshTVGenres(): Boolean {
        return Cache.isStale("tv_genres", 600_000L)
    }

    fun RefreshGenreItems(genreId: String): Boolean {
        val cacheKey = "genre_items_$genreId"
        if (Cache.isStale(cacheKey, 300_000L)) {
            return true
        }
        val cachedItems = Cache.get<List<BaseItemDto>>(cacheKey) ?: return true
        if (cachedItems.isEmpty()) {
            return true
        }
        val hasYearMetadata = cachedItems.any { item ->
            item.productionYear != null ||
                !item.premiereDate.isNullOrBlank() ||
                !item.endDate.isNullOrBlank()
        }
        return !hasYearMetadata
    }

    fun updateMovieGenres(genres: List<BaseItemDto>) {
        Cache.put("movie_genres", genres)
    }

    fun updateTVGenres(genres: List<BaseItemDto>) {
        Cache.put("tv_genres", genres)
    }

    fun updateGenreItems(genreId: String, items: List<BaseItemDto>) {
        Cache.put("genre_items_$genreId", items)
    }

    fun getGenreItems(genreId: String): List<BaseItemDto> {
        return Cache.get("genre_items_$genreId") ?: emptyList()
    }

    val movieGenres: List<BaseItemDto>
        get() = Cache.get("movie_genres") ?: emptyList()

    val tvGenres: List<BaseItemDto>
        get() = Cache.get("tv_genres") ?: emptyList()
}

private object LibraryCache {
    fun RefreshLibraryViews(): Boolean {
        return Cache.isStale("library_views", 600_000L)
    }

    fun updateLibraryViews(views: List<BaseItemDto>) {
        Cache.put("library_views", views)
    }

    fun RefreshLibraryItems(libraryId: String): Boolean {
        return Cache.isStale("library_$libraryId", 300_000L)
    }

    fun updateLibraryItems(libraryId: String, items: List<BaseItemDto>) {
        Cache.put("library_$libraryId", items)
    }

    fun getLibraryItems(libraryId: String): List<BaseItemDto> {
        return Cache.get("library_$libraryId") ?: emptyList()
    }

    val libraryViews: List<BaseItemDto>
        get() = Cache.get("library_views") ?: emptyList()
}

@Composable
private fun MovieGenreSections(
    disablePosterEnhancers: Boolean,
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }

    var movieGenres by remember { mutableStateOf(GenreCache.movieGenres) }
    var isLoading by remember { mutableStateOf(GenreCache.RefreshMovieGenres()) }

    LaunchedEffect(Unit) {
        if (GenreCache.RefreshMovieGenres()) {
            isLoading = true
            try {
                withContext(Dispatchers.IO) {
                    val result = mediaRepository.getFilteredGenres(includeItemTypes = "Movie")

                    withContext(Dispatchers.Main) {
                        result.fold(
                            onSuccess = { genres ->
                                GenreCache.updateMovieGenres(genres)
                                movieGenres = genres
                                isLoading = false
                            },
                            onFailure = { error ->
                                isLoading = false
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                isLoading = false
            }
        } else {
            movieGenres = GenreCache.movieGenres
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        if (isLoading) {
            GenreSectionSkeleton(sectionCount = 6)
        } else {
            movieGenres.forEachIndexed { index, genre ->
                ProgressiveMovieGenreSection(
                    genre = genre,
                    mediaRepository = mediaRepository,
                    disablePosterEnhancers = disablePosterEnhancers,
                    onItemClick = onItemClick,
                    onNavigateToViewAll = onNavigateToViewAll
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TVShowGenreSections(
    disablePosterEnhancers: Boolean,
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }

    var tvGenres by remember { mutableStateOf(GenreCache.tvGenres) }
    var isLoading by remember { mutableStateOf(GenreCache.RefreshTVGenres()) }

    LaunchedEffect(Unit) {
        if (GenreCache.RefreshTVGenres()) {
            isLoading = true
            try {
                val result = mediaRepository.getFilteredGenres(includeItemTypes = "Series")
                result.fold(
                    onSuccess = { genres ->
                        GenreCache.updateTVGenres(genres)
                        tvGenres = genres
                        isLoading = false
                    },
                    onFailure = { error ->
                        isLoading = false
                    }
                )
            } catch (e: Exception) {
                isLoading = false
            }
        } else {
            tvGenres = GenreCache.tvGenres
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        if (isLoading) {
            GenreSectionSkeleton(sectionCount = 6)
        } else {
            tvGenres.forEachIndexed { index, genre ->
                ProgressiveTVShowGenreSection(
                    genre = genre,
                    mediaRepository = mediaRepository,
                    disablePosterEnhancers = disablePosterEnhancers,
                    onItemClick = onItemClick,
                    onNavigateToViewAll = onNavigateToViewAll
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ProgressiveMovieGenreSection(
    genre: BaseItemDto,
    mediaRepository: MediaRepository,
    disablePosterEnhancers: Boolean,
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    val genreId = genre.id ?: return // Skip if no genre ID
    var genreMovies by remember(genreId) { mutableStateOf(GenreCache.getGenreItems(genreId)) }
    var isLoading by remember(genreId) { mutableStateOf(GenreCache.RefreshGenreItems(genreId)) }
    val genreTitle = genre.name ?: stringResource(R.string.movies)

    LaunchedEffect(genreId) {
        if (GenreCache.RefreshGenreItems(genreId)) {
            isLoading = true
            try {
                val result = withContext(Dispatchers.IO) {
                    mediaRepository.getItemsByGenre(
                        genreId = genreId,
                        includeItemTypes = "Movie",
                        limit = 20
                    )
                }

                result.fold(
                    onSuccess = { items ->
                        val validItems = items.filter {
                            it.id != null && !it.name.isNullOrBlank()
                        }
                        GenreCache.updateGenreItems(genreId, validItems)
                        genreMovies = validItems
                    },
                    onFailure = {
                        genreMovies = emptyList()
                    }
                )
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
            }
        } else {
            genreMovies = GenreCache.getGenreItems(genreId)
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = genreTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            IconButton(
                onClick = {
                    onNavigateToViewAll("MOVIES_GENRE", genre.id, genreTitle)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.dashboard_view_all),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        when {
            isLoading -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    items(4) { index ->
                        PosterSkeleton()
                    }
                }
            }
            genreMovies.isNotEmpty() -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    items(
                        count = genreMovies.size,
                        key = { index -> "${genreMovies[index].id ?: "genre_movie"}_$index" }
                    ) { index ->
                        LibraryItemCard(
                            item = genreMovies[index],
                            mediaRepository = mediaRepository,
                            disableImageEnhancers = disablePosterEnhancers,
                            onClick = { onItemClick(genreMovies[index]) }
                        )
                    }
                }
            }
            else -> {
                if (!isLoading && genreMovies.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dashboard_no_movies_found),
                        color = Color.Gray.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressiveTVShowGenreSection(
    genre: BaseItemDto,
    mediaRepository: MediaRepository,
    disablePosterEnhancers: Boolean,
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    val genreId = genre.id ?: return // Skip if no genre ID
    var genreShows by remember(genreId) { mutableStateOf(GenreCache.getGenreItems("tv_$genreId")) }
    var isLoading by remember(genreId) { mutableStateOf(GenreCache.RefreshGenreItems("tv_$genreId")) }
    val genreTitle = genre.name ?: stringResource(R.string.tv_shows)

    LaunchedEffect(genreId) {
        val cacheKey = "tv_$genreId"
        if (GenreCache.RefreshGenreItems(cacheKey)) {
            isLoading = true
            try {
                val result = withContext(Dispatchers.IO) {
                    mediaRepository.getItemsByGenre(
                        genreId = genreId,
                        includeItemTypes = "Series",
                        limit = 20
                    )
                }

                result.fold(
                    onSuccess = { items ->
                        val validItems = items.filter {
                            it.id != null && !it.name.isNullOrBlank()
                        }
                        GenreCache.updateGenreItems(cacheKey, validItems)
                        genreShows = validItems
                    },
                    onFailure = {
                        genreShows = emptyList()
                    }
                )
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
            }
        } else {
            genreShows = GenreCache.getGenreItems("tv_$genreId")
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = genreTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            IconButton(
                onClick = {
                    onNavigateToViewAll("TVSHOWS_GENRE", genre.id, genreTitle)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.dashboard_view_all),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        when {
            isLoading -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(4) {
                        PosterSkeleton()
                    }
                }
            }
            genreShows.isNotEmpty() -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(
                        items = genreShows,
                        key = { index, show -> "${show.id ?: "${show.name}_${show.type}"}_$index" }
                    ) { _, show ->
                        LibraryItemCard(
                            item = show,
                            mediaRepository = mediaRepository,
                            disableImageEnhancers = disablePosterEnhancers,
                            onClick = { onItemClick(show) }
                        )
                    }
                }
            }
            else -> {
                if (!isLoading && genreShows.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dashboard_no_tv_shows_found),
                        color = Color.Gray.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}