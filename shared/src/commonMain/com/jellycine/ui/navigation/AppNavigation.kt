package com.jellycine.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.ContentType
import com.jellycine.data.network.DiscoveredServer
import com.jellycine.data.preferences.NetworkSettingsStore
import com.jellycine.data.repository.*
import com.jellycine.ui.screens.auth.AuthScreen
import com.jellycine.ui.screens.dashboard.home.HomeScreen
import com.jellycine.ui.screens.dashboard.media.Discover
import com.jellycine.ui.screens.dashboard.media.ViewAllScreen
import com.jellycine.preferences.Preferences
import com.jellycine.ui.screens.dashboard.search.SearchContainer
import com.jellycine.ui.theme.JellyCineTheme
import com.jellycine.ui.screens.dashboard.settings.*
import com.jellycine.ui.screens.dashboard.DashboardDestination
import com.jellycine.ui.screens.dashboard.DashboardShell
import com.jellycine.ui.screens.detail.DefaultDetailPlatform
import com.jellycine.ui.screens.detail.DetailPlatform
import com.jellycine.ui.screens.detail.DetailScreenContainer
import com.jellycine.ui.screens.detail.PersonScreenContainer
import kotlinx.coroutines.flow.Flow

@Composable
fun AppNavigation(
    authRepository: AuthRepository,
    mediaRepository: MediaRepository,
    awardsRepository: AwardsRepository? = null,
    preferences: Preferences? = null,
    networkSettings: NetworkSettingsStore? = null,
    serverDiscovery: (() -> Flow<DiscoveredServer>)? = null,
    appVersion: String = "",
    detailPlatform: DetailPlatform = remember { DefaultDetailPlatform() },
    onOpenLanguageSettings: () -> Unit = {}
) {
    val isAuthenticated by authRepository.isAuthenticated.collectAsState(initial = null)

    JellyCineTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AppContent(
                isAuthenticated = isAuthenticated,
                authRepository = authRepository,
                mediaRepository = mediaRepository,
                awardsRepository = awardsRepository,
                preferences = preferences,
                networkSettings = networkSettings,
                serverDiscovery = serverDiscovery,
                appVersion = appVersion,
                detailPlatform = detailPlatform,
                onOpenLanguageSettings = onOpenLanguageSettings
            )
        }
    }
}

@Composable
private fun AppContent(
    isAuthenticated: Boolean?,
    authRepository: AuthRepository,
    mediaRepository: MediaRepository,
    awardsRepository: AwardsRepository?,
    preferences: Preferences?,
    networkSettings: NetworkSettingsStore?,
    serverDiscovery: (() -> Flow<DiscoveredServer>)?,
    appVersion: String,
    detailPlatform: DetailPlatform,
    onOpenLanguageSettings: () -> Unit
) {
    when (isAuthenticated) {
        null -> Box(Modifier.fillMaxSize().background(Color.Black))
        false -> AuthScreen(
            authRepository = authRepository,
            serverDiscovery = serverDiscovery,
            onOpenLanguageSettings = onOpenLanguageSettings,
            onAuthSuccess = {}
        )
        true -> Dashboard(
            mediaRepository = mediaRepository,
            authRepository = authRepository,
            awardsRepository = awardsRepository,
            preferences = preferences,
            networkSettings = networkSettings,
            appVersion = appVersion,
            detailPlatform = detailPlatform,
            onOpenLanguageSettings = onOpenLanguageSettings
        )
    }
}

private sealed interface DetailRoute {
    data class Detail(val itemId: String) : DetailRoute
    data class Person(val personId: String) : DetailRoute
}

private data class ViewAllRoute(
    val contentType: ContentType,
    val parentId: String?,
    val title: String
)

private enum class SettingsPage { ABOUT, INTERFACE, CACHE }

@Composable
private fun Dashboard(
    mediaRepository: MediaRepository,
    authRepository: AuthRepository,
    awardsRepository: AwardsRepository?,
    preferences: Preferences?,
    networkSettings: NetworkSettingsStore?,
    appVersion: String,
    detailPlatform: DetailPlatform,
    onOpenLanguageSettings: () -> Unit
) {
    var detailStack by remember { mutableStateOf<List<DetailRoute>>(emptyList()) }
    val openDetail: (BaseItemDto) -> Unit = { item ->
        item.id?.takeIf { it.isNotBlank() }?.let { itemId ->
            detailStack = detailStack + DetailRoute.Detail(itemId)
        }
    }
    var viewAllStack by remember { mutableStateOf<List<ViewAllRoute>>(emptyList()) }
    val openViewAll: (ContentType, String?, String) -> Unit = { contentType, parentId, title ->
        viewAllStack = viewAllStack + ViewAllRoute(contentType, parentId, title)
    }
    var settingsPage by remember { mutableStateOf<SettingsPage?>(null) }
    val settingsViewModel: SettingsViewModel? = if (preferences != null && networkSettings != null) {
        viewModel { SettingsViewModel(authRepository, mediaRepository, preferences, networkSettings) }
    } else {
        null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        DashboardShell(
            isNetworkAvailable = true,
            isOnline = true
        ) { route ->
            if (route == DashboardDestination.Home.route) {
                HomeScreen(
                    mediaRepository = mediaRepository,
                    authRepository = authRepository,
                    preferences = preferences,
                    listState = homeScrollState,
                    onItemClick = openDetail,
                    onNavigateToViewAll = openViewAll
                )
            } else if (route == DashboardDestination.MyMedia.route) {
                Discover(
                    mediaRepository = mediaRepository,
                    authRepository = authRepository,
                    disablePosterEnhancers = false,
                    awardsRepository = awardsRepository,
                    onItemClick = openDetail,
                    onNavigateToViewAll = openViewAll
                )
            } else if (route == DashboardDestination.Search.route) {
                SearchContainer(
                    mediaRepository = mediaRepository,
                    authRepository = authRepository,
                    disablePosterEnhancers = false,
                    onNavigateToDetail = openDetail,
                    onCancel = { navigateTo(DashboardDestination.Home) }
                )
            } else if (route == DashboardDestination.Settings.route && settingsViewModel != null) {
                Settings(
                    viewModel = settingsViewModel,
                    onOpenLanguageSettings = onOpenLanguageSettings,
                    onNavigateToAbout = { settingsPage = SettingsPage.ABOUT },
                    onNavigateToInterfaceSettings = { settingsPage = SettingsPage.INTERFACE },
                    onNavigateToCacheSettings = { settingsPage = SettingsPage.CACHE }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    Text(
                        text = route,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        val viewAllRoute = viewAllStack.lastOrNull()
        if (viewAllRoute != null) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                key(viewAllRoute) {
                    ViewAllScreen(
                        mediaRepository = mediaRepository,
                        authRepository = authRepository,
                        awardsRepository = awardsRepository,
                        contentType = viewAllRoute.contentType,
                        parentId = viewAllRoute.parentId,
                        title = viewAllRoute.title,
                        onBackPressed = { viewAllStack = viewAllStack.dropLast(1) },
                        onItemClick = openDetail
                    )
                }
            }
        }

        val detailRoute = detailStack.lastOrNull()
        if (detailRoute != null && preferences != null) {
            val closeDetail = { detailStack = detailStack.dropLast(1) }
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                key(detailRoute) {
                    when (detailRoute) {
                        is DetailRoute.Detail -> DetailScreenContainer(
                            itemId = detailRoute.itemId,
                            mediaRepository = mediaRepository,
                            authRepository = authRepository,
                            preferences = preferences,
                            platform = detailPlatform,
                            forceMergeVersions = true,
                            onBackPressed = closeDetail,
                            onNavigateToDetail = { itemId ->
                                detailStack = detailStack + DetailRoute.Detail(itemId)
                            },
                            onNavigateToPerson = { personId ->
                                detailStack = detailStack + DetailRoute.Person(personId)
                            }
                        )

                        is DetailRoute.Person -> PersonScreenContainer(
                            personId = detailRoute.personId,
                            mediaRepository = mediaRepository,
                            authRepository = authRepository,
                            platform = detailPlatform,
                            onBackPressed = closeDetail,
                            onItemClick = { itemId ->
                                detailStack = detailStack + DetailRoute.Detail(itemId)
                            }
                        )
                    }
                }
            }
        }

        val page = settingsPage
        if (page != null && preferences != null && settingsViewModel != null) {
            val closePage = { settingsPage = null }
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                when (page) {
                    SettingsPage.ABOUT -> AboutScreen(
                        versionName = appVersion,
                        onBackPressed = closePage
                    )
                    SettingsPage.INTERFACE -> InterfaceSettingsScreen(
                        preferences = preferences,
                        authRepository = authRepository,
                        onBackPressed = closePage
                    )
                    SettingsPage.CACHE -> CacheSettingsScreen(
                        viewModel = settingsViewModel,
                        onBackPressed = closePage
                    )
                }
            }
        }
    }
}
