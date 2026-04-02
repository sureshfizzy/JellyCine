package com.jellycine.app.ui.screens.dashboard

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jellycine.app.ui.screens.dashboard.favorites.Favorites
import com.jellycine.app.ui.screens.dashboard.home.Dashboard
import com.jellycine.app.ui.screens.dashboard.media.ForYou
import com.jellycine.app.ui.screens.dashboard.media.MyMedia
import com.jellycine.app.ui.screens.dashboard.search.SearchContainer
import com.jellycine.app.ui.screens.dashboard.settings.Settings
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.network.NetworkModule
import com.jellycine.shared.preferences.Preferences

private fun dashboardEnterTransition(): EnterTransition {
    return fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
}

private fun dashboardExitTransition(): ExitTransition {
    return fadeOut(animationSpec = tween(durationMillis = 160, easing = LinearOutSlowInEasing))
}

@Composable
fun DashboardContainer(
    onLogout: () -> Unit = {},
    onNavigateToDetail: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> },
    onNavigateToPlayer: (String) -> Unit = {},
    onNavigateToPlayerSettings: () -> Unit = {},
    onNavigateToInterfaceSettings: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToCacheSettings: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onAddServer: () -> Unit = {},
    onAddUser: (serverUrl: String, serverName: String?) -> Unit = { _, _ -> }
) {
    val navController = rememberNavController()
    val dashboardFocus = rememberTvDashboardFocusState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: DashboardDestination.Home.route
    var railHasFocus by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val preferences = remember(context) { Preferences(context) }
    val appContext = remember(context) { context.applicationContext }
    val networkAvailabilityFlow = remember(appContext) {
        NetworkModule.observeNetworkAvailability(appContext)
    }
    val isNetworkAvailable by networkAvailabilityFlow.collectAsStateWithLifecycle(
        initialValue = NetworkModule.isInternetAvailable(appContext)
    )
    val useMyMediaTabEnabled by preferences.UseMyMediaTabEnabled().collectAsStateWithLifecycle(
        initialValue = preferences.isUseMyMediaTabEnabled()
    )

    val destinations = remember(isNetworkAvailable) {
        if (isNetworkAvailable) onlineDestinations() else offlineDestinations()
    }

    val navigateToDestination: (DashboardDestination) -> Unit = { destination ->
        if (destination.route != currentRoute) {
            navController.navigate(destination.route) {
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    LaunchedEffect(isNetworkAvailable, currentRoute) {
        val allowedRoutes = destinations.map(DashboardDestination::route).toSet()
        if (!allowedRoutes.contains(currentRoute)) {
            navigateToDestination(DashboardDestination.Home)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF151822),
                        Color(0xFF0C0F16),
                        Color(0xFF050608)
                    )
                )
            )
    ) {
        val railSlotWidth by animateDpAsState(
            targetValue = if (railHasFocus) 128.dp else 48.dp,
            label = "dashboard_rail_slot_width"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            NavHost(
                navController = navController,
                startDestination = DashboardDestination.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = railSlotWidth)
            ) {
                composable(
                    route = DashboardDestination.Home.route,
                    enterTransition = { dashboardEnterTransition() },
                    exitTransition = { dashboardExitTransition() }
                ) {
                    Dashboard(
                        onLogout = onLogout,
                        onNavigateToDetail = onNavigateToDetail,
                        onNavigateToViewAll = onNavigateToViewAll,
                        onNavigateToPlayer = onNavigateToPlayer,
                        onAddServer = onAddServer,
                        onAddUser = onAddUser,
                        isTabActive = true,
                        sidebarFocusRequester = dashboardFocus.sidebar,
                        headerFocusRequester = dashboardFocus.homeHeader,
                        headerEndFocusRequester = dashboardFocus.homeHeaderEnd,
                        contentFocusRequester = dashboardFocus.homeContent,
                        onHomeReturnTargetChanged = dashboardFocus::updateHomeReturnFocus
                    )
                }

                composable(
                    route = DashboardDestination.Library.route,
                    enterTransition = { dashboardEnterTransition() },
                    exitTransition = { dashboardExitTransition() }
                ) {
                    if (useMyMediaTabEnabled) {
                        MyMedia(
                            onLibraryClick = { contentType, parentId, title ->
                                onNavigateToViewAll(contentType.name, parentId, title)
                            }
                        )
                    } else {
                        ForYou(onItemClick = onNavigateToDetail)
                    }
                }

                composable(
                    route = DashboardDestination.Search.route,
                    enterTransition = { dashboardEnterTransition() },
                    exitTransition = { dashboardExitTransition() }
                ) {
                    SearchContainer(
                        onNavigateToDetail = onNavigateToDetail,
                        onCancel = { navigateToDestination(DashboardDestination.Home) }
                    )
                }

                composable(
                    route = DashboardDestination.Favorites.route,
                    enterTransition = { dashboardEnterTransition() },
                    exitTransition = { dashboardExitTransition() }
                ) {
                    Favorites(onItemClick = onNavigateToDetail)
                }

                composable(
                    route = DashboardDestination.Settings.route,
                    enterTransition = { dashboardEnterTransition() },
                    exitTransition = { dashboardExitTransition() }
                ) {
                    Settings(
                        onLogout = onLogout,
                        onNavigateToPlayerSettings = onNavigateToPlayerSettings,
                        onNavigateToInterfaceSettings = onNavigateToInterfaceSettings,
                        onNavigateToDownloads = onNavigateToDownloads,
                        onNavigateToCacheSettings = onNavigateToCacheSettings,
                        onNavigateToAbout = onNavigateToAbout,
                        onAddServer = onAddServer,
                        onAddUser = onAddUser
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(railSlotWidth)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to Color(0xCC090B12),
                                0.32f to Color(0xA0121622),
                                0.72f to Color(0x400D1017),
                                1.0f to Color.Transparent
                            )
                        )
                    )
            ) {
                TvSidebarRail(
                    destinations = destinations,
                    currentRoute = currentRoute,
                    entryFocusRequester = dashboardFocus.sidebar,
                    onMoveRight = {
                        if (currentRoute == DashboardDestination.Home.route) {
                            val restored = dashboardFocus.requestHomeReturnFocus()
                            if (!restored) {
                                dashboardFocus.homeHeader.requestFocus()
                            }
                            true
                        } else {
                            false
                        }
                    },
                    onDestinationSelected = navigateToDestination,
                    onRailFocusChanged = { railHasFocus = it },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 8.dp, top = 112.dp)
                )
            }
        }
    }
}
