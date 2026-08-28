package com.vela.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.vela.app.ui.screens.dashboard.DashboardContainer
import com.vela.app.ui.screens.auth.AuthScreen
import com.vela.app.ui.screens.detail.DetailScreenContainer
import com.vela.app.ui.screens.detail.PersonScreenContainer
import com.vela.app.ui.screens.dashboard.settings.CacheSettingsScreen
import com.vela.app.ui.screens.dashboard.settings.AboutScreen
import com.vela.app.ui.screens.dashboard.settings.PlayerSettingsScreen
import com.vela.app.ui.screens.dashboard.settings.SubtitleSettingsScreen
import com.vela.app.ui.screens.dashboard.settings.InterfaceSettingsScreen
import com.vela.app.ui.screens.player.PlayerScreen
import com.vela.auth.AuthStateManager
import com.vela.shared.ui.theme.velaMotion
import androidx.compose.material3.MaterialTheme
import androidx.media3.common.util.UnstableApi

// TV 页面只使用轻微缩放与淡入，保留遥控器焦点变化作为主要空间反馈。
private fun contentEnterTransition(
    effectsSpec: FiniteAnimationSpec<Float>,
    spatialSpec: FiniteAnimationSpec<Float>
): EnterTransition {
    return fadeIn(animationSpec = effectsSpec) +
        scaleIn(animationSpec = spatialSpec, initialScale = 0.985f)
}

private fun contentExitTransition(effectsSpec: FiniteAnimationSpec<Float>): ExitTransition {
    return fadeOut(animationSpec = effectsSpec)
}

@UnstableApi
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val authStateManager = remember { AuthStateManager.getInstance(context) }
    val motion = MaterialTheme.velaMotion
    val enterEffectsSpec = motion.defaultEffectsSpec<Float>()
    val enterSpatialSpec = motion.defaultSpatialSpec<Float>()
    val exitEffectsSpec = motion.fastEffectsSpec<Float>()

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
                enterTransition = { contentEnterTransition(enterEffectsSpec, enterSpatialSpec) },
                exitTransition = {
                    if (targetState.destination.route == "server_connection") {
                        ExitTransition.None
                    } else {
                        contentExitTransition(exitEffectsSpec)
                    }
                }
            ) {
                AuthScreen(
                    preferSavedServers = true,
                    onAddServer = {
                        navController.navigate("server_connection") {
                            popUpTo("splash") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
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
                        contentEnterTransition(enterEffectsSpec, enterSpatialSpec)
                    }
                },
                exitTransition = {
                    if (targetState.destination.route == "server_connection") {
                        ExitTransition.None
                    } else {
                        contentExitTransition(exitEffectsSpec)
                    }
                }
            ) {
                AuthScreen(
                    preferSavedServers = true,
                    onAddServer = {
                        navController.navigate("server_connection") {
                            popUpTo("auth") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onAuthSuccess = {
                        navController.navigate("dashboard") {
                            popUpTo("auth") { inclusive = true }
                        }
                    }
                )
            }

            composable(
                "server_connection",
                enterTransition = {
                    if (
                        initialState.destination.route == "auth" ||
                        initialState.destination.route == "splash"
                    ) {
                        EnterTransition.None
                    } else {
                        contentEnterTransition(enterEffectsSpec, enterSpatialSpec)
                    }
                },
                exitTransition = { contentExitTransition(exitEffectsSpec) }
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
                "add_user?serverUrl={serverUrl}&serverName={serverName}",
                arguments = listOf(
                    navArgument("serverUrl") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("serverName") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                ),
                enterTransition = { contentEnterTransition(enterEffectsSpec, enterSpatialSpec) },
                exitTransition = { contentExitTransition(exitEffectsSpec) }
            ) { backStackEntry ->
                val encodedServerUrl = backStackEntry.arguments?.getString("serverUrl").orEmpty()
                val serverUrl = runCatching {
                    java.net.URLDecoder.decode(encodedServerUrl, "UTF-8")
                }.getOrDefault(encodedServerUrl)
                val encodedServerName = backStackEntry.arguments?.getString("serverName")
                val serverName = encodedServerName?.let { encodedName ->
                    runCatching { java.net.URLDecoder.decode(encodedName, "UTF-8") }
                        .getOrDefault(encodedName)
                }?.takeIf { it.isNotBlank() }

                AuthScreen(
                    serverUrl = serverUrl.takeIf { it.isNotBlank() },
                    serverName = serverName,
                    startAtLogin = serverUrl.isNotBlank(),
                    onAuthSuccess = {
                        val popped = navController.popBackStack()
                        if (!popped) {
                            navController.navigate("dashboard") {
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }

            composable(
                "dashboard",
                enterTransition = { contentEnterTransition(enterEffectsSpec, enterSpatialSpec) },
                exitTransition = {
                    if (targetState.destination.route == "auth") {
                        ExitTransition.None
                    } else {
                        contentExitTransition(exitEffectsSpec)
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
                    onNavigateToDownloads = {},
                    onNavigateToCacheSettings = {
                        navController.navigate("cache_settings")
                    },
                    onNavigateToAbout = {
                        navController.navigate("about")
                    },
                    onAddServer = {
                        navController.navigate("server_connection") {
                            launchSingleTop = true
                        }
                    },
                    onAddUser = { serverUrl, serverName ->
                        val encodedServerUrl = java.net.URLEncoder.encode(serverUrl, "UTF-8")
                        val encodedServerName = java.net.URLEncoder.encode(serverName.orEmpty(), "UTF-8")
                        navController.navigate(
                            "add_user?serverUrl=$encodedServerUrl&serverName=$encodedServerName"
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToDetail = { item ->
                        item.id?.let { itemId ->
                            navController.navigate("detail/$itemId")
                        }
                    },
                    onNavigateToMergedDetail = { item ->
                        item.id?.let { itemId ->
                            navController.navigate("detail/$itemId?mergeVersions=true")
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
                    },
                    onNavigateToPlayer = { itemId ->
                        navController.navigate("player/$itemId")
                    }
                )
            }

            composable(
                "player/{itemId}",
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
                enterTransition = { contentEnterTransition(enterEffectsSpec, enterSpatialSpec) },
                exitTransition = { contentExitTransition(exitEffectsSpec) }
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId")

                if (!itemId.isNullOrBlank()) {
                    PlayerScreen(
                        mediaId = itemId,
                        onBackPressed = { navController.popBackStack() }
                    )
                } else {
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                }
            }

            composable(
                "detail/{itemId}?mergeVersions={mergeVersions}",
                arguments = listOf(
                    navArgument("itemId") { type = NavType.StringType },
                    navArgument("mergeVersions") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                ),
                enterTransition = { contentEnterTransition(enterEffectsSpec, enterSpatialSpec) },
                exitTransition = { contentExitTransition(exitEffectsSpec) }
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId")
                val forceMergeVersions = backStackEntry.arguments?.getBoolean("mergeVersions") ?: false

                if (itemId != null) {
                    DetailScreenContainer(
                        itemId = itemId,
                        forceMergeVersions = forceMergeVersions,
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
                enterTransition = { contentEnterTransition(enterEffectsSpec, enterSpatialSpec) },
                exitTransition = { contentExitTransition(exitEffectsSpec) }
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
                enterTransition = { contentEnterTransition(enterEffectsSpec, enterSpatialSpec) },
                exitTransition = { contentExitTransition(exitEffectsSpec) }
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
                enterTransition = { contentEnterTransition(enterEffectsSpec, enterSpatialSpec) },
                exitTransition = { contentExitTransition(exitEffectsSpec) }
            ) { backStackEntry ->
                val contentTypeString = backStackEntry.arguments?.getString("contentType") ?: "ALL"
                val parentId = backStackEntry.arguments?.getString("parentId")
                val genreId = backStackEntry.arguments?.getString("genreId")
                val title = backStackEntry.arguments?.getString("title")?.let {
                    java.net.URLDecoder.decode(it, "UTF-8")
                } ?: "View All"

                val contentType = when (contentTypeString.uppercase()) {
                    "MOVIES" -> com.vela.app.ui.screens.dashboard.media.ContentType.MOVIES
                    "SERIES" -> com.vela.app.ui.screens.dashboard.media.ContentType.SERIES
                    "EPISODES" -> com.vela.app.ui.screens.dashboard.media.ContentType.EPISODES
                    "MOVIES_GENRE" -> com.vela.app.ui.screens.dashboard.media.ContentType.MOVIES_GENRE
                    "TVSHOWS_GENRE" -> com.vela.app.ui.screens.dashboard.media.ContentType.TVSHOWS_GENRE
                    else -> com.vela.app.ui.screens.dashboard.media.ContentType.ALL
                }

                com.vela.app.ui.screens.dashboard.media.ViewAllScreen(
                    contentType = contentType,
                    parentId = parentId,
                    genreId = genreId,
                    title = title,
                    onItemClick = { item ->
                        item.id?.let { itemId ->
                            val mergeVersions = parentId == com.vela.app.ui.screens.dashboard.media.WATCHED_VIEW_ALL_PARENT_ID
                            navController.navigate("detail/$itemId${if (mergeVersions) "?mergeVersions=true" else ""}")
                        }
                    }
                )
            }

            composable(
                "player_settings",
                enterTransition = { contentEnterTransition(enterEffectsSpec, enterSpatialSpec) },
                exitTransition = { contentExitTransition(exitEffectsSpec) }
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
                enterTransition = { contentEnterTransition(enterEffectsSpec, enterSpatialSpec) },
                exitTransition = { contentExitTransition(exitEffectsSpec) }
            ) {
                SubtitleSettingsScreen(
                    onBackPressed = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                "interface_settings",
                enterTransition = { contentEnterTransition(enterEffectsSpec, enterSpatialSpec) },
                exitTransition = { contentExitTransition(exitEffectsSpec) }
            ) {
                InterfaceSettingsScreen(
                    onBackPressed = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                "cache_settings",
                enterTransition = { contentEnterTransition(enterEffectsSpec, enterSpatialSpec) },
                exitTransition = { contentExitTransition(exitEffectsSpec) }
            ) {
                CacheSettingsScreen(
                    onBackPressed = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                "about",
                enterTransition = { contentEnterTransition(enterEffectsSpec, enterSpatialSpec) },
                exitTransition = { contentExitTransition(exitEffectsSpec) }
            ) {
                AboutScreen(
                    onBackPressed = {
                        navController.popBackStack()
                    }
                )
            }
        }
}
