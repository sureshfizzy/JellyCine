package com.jellycine.shared.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.jellycine.data.network.DiscoveredServer
import com.jellycine.data.repository.AuthRepository
import com.jellycine.data.repository.MediaRepository
import com.jellycine.shared.ui.screens.auth.AuthScreen
import com.jellycine.shared.ui.screens.dashboard.home.HomeScreen
import com.jellycine.shared.ui.screens.dashboard.DashboardDestination
import com.jellycine.shared.ui.screens.dashboard.DashboardShell
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
fun AppNavigation(
    authRepository: AuthRepository,
    mediaRepository: MediaRepository,
    serverDiscovery: (() -> Flow<DiscoveredServer>)? = null,
    onOpenLanguageSettings: () -> Unit = {}
) {
    val isAuthenticated by authRepository.isAuthenticated.collectAsState(initial = null)

    when (isAuthenticated) {
        null -> Box(Modifier.fillMaxSize().background(Color.Black))
        false -> AuthScreen(
            authRepository = authRepository,
            serverDiscovery = serverDiscovery,
            onOpenLanguageSettings = onOpenLanguageSettings,
            onAuthSuccess = {}
        )
        true -> Dashboard(mediaRepository, authRepository)
    }
}

@Composable
private fun Dashboard(mediaRepository: MediaRepository, authRepository: AuthRepository) {
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
}
