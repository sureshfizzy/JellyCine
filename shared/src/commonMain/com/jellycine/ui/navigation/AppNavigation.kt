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
import com.jellycine.data.network.DiscoveredServer
import com.jellycine.data.preferences.NetworkSettingsStore
import com.jellycine.data.repository.*
import com.jellycine.ui.screens.auth.AuthScreen
import com.jellycine.ui.screens.dashboard.home.HomeScreen
import com.jellycine.ui.screens.dashboard.media.Discover
import com.jellycine.preferences.Preferences
import com.jellycine.ui.screens.dashboard.search.SearchContainer
import com.jellycine.ui.theme.JellyCineTheme
import com.jellycine.ui.screens.dashboard.settings.*
import com.jellycine.ui.screens.dashboard.DashboardDestination
import com.jellycine.ui.screens.dashboard.DashboardShell
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
            onOpenLanguageSettings = onOpenLanguageSettings
        )
    }
}

private enum class SettingsPage { ABOUT, INTERFACE, CACHE }

@Composable
private fun Dashboard(
    mediaRepository: MediaRepository,
    authRepository: AuthRepository,
    awardsRepository: AwardsRepository?,
    preferences: Preferences?,
    networkSettings: NetworkSettingsStore?,
    appVersion: String,
    onOpenLanguageSettings: () -> Unit
) {
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
                    listState = homeScrollState
                )
            } else if (route == DashboardDestination.MyMedia.route) {
                Discover(
                    mediaRepository = mediaRepository,
                    authRepository = authRepository,
                    disablePosterEnhancers = false,
                    awardsRepository = awardsRepository
                )
            } else if (route == DashboardDestination.Search.route) {
                SearchContainer(
                    mediaRepository = mediaRepository,
                    authRepository = authRepository,
                    disablePosterEnhancers = false,
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
