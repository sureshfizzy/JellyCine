package com.jellycine.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.jellycine.data.network.DiscoveredServer
import com.jellycine.data.repository.AuthRepository
import com.jellycine.shared.auth.AuthScreen
import com.jellycine.shared.ui.navigation.DashboardDestination
import com.jellycine.shared.ui.navigation.DashboardNavigationBar
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
fun JellyCineApp(
    authRepository: AuthRepository,
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
        true -> DashboardPlaceholder()
    }
}

@Composable
private fun DashboardPlaceholder() {
    var currentRoute by remember { mutableStateOf(DashboardDestination.Home.route) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Text(
            text = currentRoute,
            color = Color.White,
            modifier = Modifier.align(Alignment.Center)
        )
        DashboardNavigationBar(
            currentRoute = currentRoute,
            isNetworkAvailable = true,
            isOnline = true,
            onNavigate = { currentRoute = it.route },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
