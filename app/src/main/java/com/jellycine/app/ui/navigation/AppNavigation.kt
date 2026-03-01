package com.jellycine.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.jellycine.app.ui.screens.dashboard.DashboardContainer
import com.jellycine.app.ui.screens.auth.AuthScreen
import com.jellycine.app.ui.screens.detail.DetailScreenContainer
import com.jellycine.app.ui.screens.detail.PersonScreenContainer
import com.jellycine.app.ui.screens.dashboard.settings.DownloadsScreen
import com.jellycine.app.ui.screens.dashboard.settings.CacheSettingsScreen
import com.jellycine.app.ui.screens.dashboard.settings.PlayerSettingsScreen
import com.jellycine.app.ui.screens.dashboard.settings.SubtitleSettingsScreen
import com.jellycine.app.ui.screens.dashboard.settings.InterfaceSettingsScreen
import com.jellycine.auth.AuthStateManager
import androidx.media3.common.util.UnstableApi

// Pure Animation helpers - no sliding, only fade with content transforms
private fun textTransition(duration: Int = 400): EnterTransition {
    return fadeIn(animationSpec = tween(duration, easing = FastOutSlowInEasing))
}

private fun textExitTransition(duration: Int = 300): ExitTransition {
    return fadeOut(animationSpec = tween(duration, easing = LinearOutSlowInEasing))
}

@UnstableApi
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val authStateManager = remember { AuthStateManager.getInstance(context) }

    var startDestination by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        val isAuthenticated = authStateManager.checkAuthenticationState()
        startDestination = if (isAuthenticated) "dashboard" else "auth"
    }

    if (startDestination == null) return

    NavHost(
        navController = navController,
        startDestination = startDestination!!,
        modifier = Modifier.fillMaxSize()
    ) {
            composable(
                "splash",
                enterTransition = { textTransition(500) },
                exitTransition = { textExitTransition(400) }
            ) {
                AuthScreen(
                    onAuthSuccess = {
                        navController.navigate("dashboard") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                )
            }

            composable(
                "auth",
                enterTransition = {
                    if (initialState.destination.route == "dashboard") {
                        EnterTransition.None
                    } else {
                        textTransition(500)
                    }
                },
                exitTransition = { textExitTransition(400) }
            ) {
                AuthScreen(
                    onAuthSuccess = {
                        navController.navigate("dashboard") {
                            popUpTo("auth") { inclusive = true }
                        }
                    }
                )
            }

            composable(
                "server_connection",
                enterTransition = { textTransition(500) },
                exitTransition = { textExitTransition(400) }
            ) {
                AuthScreen(
                    onAuthSuccess = {
                        navController.navigate("dashboard") {
                            popUpTo("server_connection") { inclusive = true }
                        }
                    }
                )
            }

            composable(
                "dashboard",
                enterTransition = { textTransition(400) },
                exitTransition = {
                    if (targetState.destination.route == "auth") {
                        ExitTransition.None
                    } else {
                        textExitTransition(300)
                    }
                }
            ) {
                DashboardContainer(
                    onLogout = {
                        navController.navigate("auth") {
                            popUpTo("dashboard") { inclusive = true }
                        }
                    },
                    onNavigateToPlayerSettings = {
                        navController.navigate("player_settings")
                    },
                    onNavigateToInterfaceSettings = {
                        navController.navigate("interface_settings")
                    },
                    onNavigateToDownloads = {
                        navController.navigate("downloads")
                    },
                    onNavigateToCacheSettings = {
                        navController.navigate("cache_settings")
                    },
                    onNavigateToDetail = { item ->
                        item.id?.let { itemId ->
                            navController.navigate("detail/$itemId")
                        }
                    },
                    onNavigateToViewAll = { contentType, parentId, title ->
                        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                        val route = when {
                            contentType.contains("GENRE") && parentId != null -> {
                                "viewall/$contentType?genreId=$parentId&title=$encodedTitle"
                            }
                            parentId != null -> {
                                "viewall/$contentType?parentId=$parentId&title=$encodedTitle"
                            }
                            else -> {
                                "viewall/$contentType?title=$encodedTitle"
                            }
                        }
                        navController.navigate(route)
                    }
                )
            }

            composable(
                "detail/{itemId}",
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
                enterTransition = { textTransition(500) },
                exitTransition = { textExitTransition(400) }
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId")

                if (itemId != null) {
                    DetailScreenContainer(
                        itemId = itemId,
                        onNavigateToDetail = { selectedItemId ->
                            if (selectedItemId != itemId) {
                                navController.navigate("detail/$selectedItemId")
                            }
                        },
                        onNavigateToPerson = { personId ->
                            if (personId != itemId) {
                                navController.navigate("person/$personId")
                            }
                        },
                        onBackPressed = {
                            navController.popBackStack()
                        }
                    )
                } else {
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                }
            }

            composable(
                "episode/{episodeId}",
                arguments = listOf(navArgument("episodeId") { type = NavType.StringType }),
                enterTransition = { textTransition(500) },
                exitTransition = { textExitTransition(400) }
            ) { backStackEntry ->
                val episodeId = backStackEntry.arguments?.getString("episodeId")

                if (episodeId != null) {
                    DetailScreenContainer(
                        itemId = episodeId,
                        onNavigateToDetail = { selectedItemId ->
                            if (selectedItemId != episodeId) {
                                navController.navigate("detail/$selectedItemId")
                            }
                        },
                        onNavigateToPerson = { personId ->
                            if (personId != episodeId) {
                                navController.navigate("person/$personId")
                            }
                        },
                        onBackPressed = {
                            navController.popBackStack()
                        }
                    )
                } else {
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                }
            }

            composable(
                "person/{personId}",
                arguments = listOf(navArgument("personId") { type = NavType.StringType }),
                enterTransition = { textTransition(500) },
                exitTransition = { textExitTransition(400) }
            ) { backStackEntry ->
                val personId = backStackEntry.arguments?.getString("personId")

                if (personId != null) {
                    PersonScreenContainer(
                        personId = personId,
                        onBackPressed = {
                            navController.popBackStack()
                        },
                        onItemClick = { selectedItemId ->
                            navController.navigate("detail/$selectedItemId")
                        }
                    )
                } else {
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                }
            }

            composable(
                "viewall/{contentType}?parentId={parentId}&title={title}&genreId={genreId}",
                arguments = listOf(
                    navArgument("contentType") { type = NavType.StringType },
                    navArgument("parentId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("title") {
                        type = NavType.StringType
                        defaultValue = "View All"
                    },
                    navArgument("genreId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                ),
                enterTransition = { textTransition(450) },
                exitTransition = { textExitTransition(350) }
            ) { backStackEntry ->
                val contentTypeString = backStackEntry.arguments?.getString("contentType") ?: "ALL"
                val parentId = backStackEntry.arguments?.getString("parentId")
                val genreId = backStackEntry.arguments?.getString("genreId")
                val title = backStackEntry.arguments?.getString("title")?.let {
                    java.net.URLDecoder.decode(it, "UTF-8")
                } ?: "View All"

                val contentType = when (contentTypeString.uppercase()) {
                    "MOVIES" -> com.jellycine.app.ui.screens.dashboard.media.ContentType.MOVIES
                    "SERIES" -> com.jellycine.app.ui.screens.dashboard.media.ContentType.SERIES
                    "EPISODES" -> com.jellycine.app.ui.screens.dashboard.media.ContentType.EPISODES
                    "MOVIES_GENRE" -> com.jellycine.app.ui.screens.dashboard.media.ContentType.MOVIES_GENRE
                    "TVSHOWS_GENRE" -> com.jellycine.app.ui.screens.dashboard.media.ContentType.TVSHOWS_GENRE
                    else -> com.jellycine.app.ui.screens.dashboard.media.ContentType.ALL
                }

                com.jellycine.app.ui.screens.dashboard.media.ViewAllScreen(
                    contentType = contentType,
                    parentId = parentId,
                    genreId = genreId,
                    title = title,
                    onItemClick = { item ->
                        item.id?.let { itemId ->
                            navController.navigate("detail/$itemId")
                        }
                    }
                )
            }

            composable(
                "player_settings",
                enterTransition = { textTransition(450) },
                exitTransition = { textExitTransition(350) }
            ) {
                PlayerSettingsScreen(
                    onBackPressed = {
                        navController.popBackStack()
                    },
                    onNavigateToSubtitleSettings = {
                        navController.navigate("subtitle_settings")
                    }
                )
            }

            composable(
                "subtitle_settings",
                enterTransition = { textTransition(450) },
                exitTransition = { textExitTransition(350) }
            ) {
                SubtitleSettingsScreen(
                    onBackPressed = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                "downloads",
                enterTransition = { textTransition(450) },
                exitTransition = { textExitTransition(350) }
            ) {
                DownloadsScreen(
                    onBackPressed = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                "interface_settings",
                enterTransition = { textTransition(450) },
                exitTransition = { textExitTransition(350) }
            ) {
                InterfaceSettingsScreen(
                    onBackPressed = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                "cache_settings",
                enterTransition = { textTransition(450) },
                exitTransition = { textExitTransition(350) }
            ) {
                CacheSettingsScreen(
                    onBackPressed = {
                        navController.popBackStack()
                    }
                )
            }
        }
}
