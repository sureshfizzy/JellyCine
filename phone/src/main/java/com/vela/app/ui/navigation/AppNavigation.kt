package com.vela.app.ui.navigation

import android.widget.Toast
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
import androidx.compose.material3.MaterialTheme
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.NavController
import com.vela.app.ui.screens.dashboard.DashboardContainer
import com.vela.app.ui.screens.dashboard.home.CachedData
import com.vela.data.network.ServerLineSwitchReason
import com.vela.data.repository.AuthRepositoryProvider
import com.vela.data.repository.MediaRepositoryProvider
import com.vela.shared.R
import com.vela.app.ui.screens.auth.AuthScreen
import com.vela.app.ui.screens.detail.DetailScreenContainer
import com.vela.app.ui.screens.detail.PersonScreenContainer
import com.vela.app.ui.screens.dashboard.settings.DownloadsScreen
import com.vela.app.ui.screens.dashboard.settings.CacheSettingsScreen
import com.vela.app.ui.screens.dashboard.settings.ConnectionsSettingsScreen
import com.vela.app.ui.screens.home.AppHomeContainer
import com.vela.app.ui.screens.admin.ServerInfoScreen
import com.vela.app.ui.screens.dashboard.settings.AboutScreen
import com.vela.app.ui.screens.dashboard.settings.PlayerSettingsScreen
import com.vela.app.ui.screens.dashboard.settings.SubtitleSettingsScreen
import com.vela.app.ui.screens.dashboard.settings.InterfaceSettingsScreen
import com.vela.app.ui.activity.PlayerActivity
import com.vela.app.player.mpv.MpvWarmPool
import com.vela.shared.ui.theme.velaMotion
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.delay

// 页面转场只组合主题 motion token；媒体内容保持空间稳定，避免大幅滑动造成眩晕。
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

private fun NavController.enterDashboard() {
    navigate("dashboard") {
        launchSingleTop = true
        popUpTo("servers") { inclusive = false }
    }
}

private fun NavController.openServerPicker() {
    if (!popBackStack("servers", inclusive = false)) {
        navigate("servers") {
            launchSingleTop = true
        }
    }
}

private fun NavController.openViewAll(
    contentType: String,
    parentId: String?,
    title: String,
    searchTerm: String? = null,
    tag: String? = null
) {
    val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
    val params = buildList {
        when {
            contentType.contains("GENRE") && parentId != null -> add("genreId=$parentId")
            parentId != null -> add("parentId=$parentId")
        }
        add("title=$encodedTitle")
        searchTerm?.takeIf { it.isNotBlank() }?.let {
            add("searchTerm=${java.net.URLEncoder.encode(it, "UTF-8")}")
        }
        tag?.takeIf { it.isNotBlank() }?.let {
            add("tag=${java.net.URLEncoder.encode(it, "UTF-8")}")
        }
    }
    navigate("viewall/$contentType?${params.joinToString("&")}")
}

@UnstableApi
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val authRepository = remember(context) { AuthRepositoryProvider.getInstance(context) }
    val mediaRepository = remember(context) { MediaRepositoryProvider.getInstance(context) }
    val motion = MaterialTheme.velaMotion
    val enterEffectsSpec = motion.defaultEffectsSpec<Float>()
    val enterSpatialSpec = motion.defaultSpatialSpec<Float>()
    val exitEffectsSpec = motion.fastEffectsSpec<Float>()

    LaunchedEffect(authRepository) {
        authRepository.lineSwitchEvents.collect { event ->
            mediaRepository.clearPersistedHomeSnapshot()
            CachedData.clearAllCache()
            val lineName = event.customName.ifBlank {
                context.getString(
                    if (event.isLan) R.string.settings_server_line_lan else R.string.settings_server_line_wan
                )
            }
            val message = context.getString(
                if (event.reason == ServerLineSwitchReason.FAILOVER) {
                    R.string.settings_server_line_switched_failover
                } else {
                    R.string.settings_server_line_switched_network
                },
                lineName
            )
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    NavHost(
        navController = navController,
        startDestination = "servers",
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
                        navController.enterDashboard()
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
                        navController.enterDashboard()
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
                        navController.enterDashboard()
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
                        navController.enterDashboard()
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
                LaunchedEffect(Unit) {
                    delay(750L)
                    MpvWarmPool.warmIfPreferred(context.applicationContext)
                }

                DashboardContainer(
                    onLogout = {
                        navController.openServerPicker()
                    },
                    onNavigateToPlayerSettings = {
                        navController.navigate("player_settings")
                    },
                    onNavigateToInterfaceSettings = {
                        navController.navigate("interface_settings")
                    },
                    onNavigateToConnections = {
                        navController.navigate("connections_settings")
                    },
                    onNavigateToServers = {
                        navController.openServerPicker()
                    },
                    onNavigateToDownloads = {
                        navController.navigate("downloads")
                    },
                    onNavigateToCacheSettings = {
                        navController.navigate("cache_settings")
                    },
                    onNavigateToAbout = {
                        navController.navigate("about")
                    },
                    onNavigateToServerInfo = {
                        navController.navigate("server_info")
                    },
                    onAddServer = {
                        navController.openServerPicker()
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
                        navController.openViewAll(contentType, parentId, title)
                    },
                    onNavigateToSearchCategory = { contentType, searchTerm, title ->
                        navController.openViewAll(
                            contentType = contentType,
                            parentId = null,
                            title = title,
                            searchTerm = searchTerm
                        )
                    },
                    onNavigateToPlayer = { itemId ->
                        PlayerActivity.start(context, itemId)
                    }
                )
            }

            composable(
                "player/{itemId}?fromStart={fromStart}",
                arguments = listOf(
                    navArgument("itemId") { type = NavType.StringType },
                    navArgument("fromStart") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                ),
                enterTransition = { contentEnterTransition(enterEffectsSpec, enterSpatialSpec) },
                exitTransition = { contentExitTransition(exitEffectsSpec) }
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId")
                val fromStart = backStackEntry.arguments?.getBoolean("fromStart") ?: false

                if (!itemId.isNullOrBlank()) {
                    val playerContext = LocalContext.current
                    LaunchedEffect(itemId, fromStart) {
                        PlayerActivity.start(
                            context = playerContext,
                            mediaId = itemId,
                            startFromBeginning = fromStart
                        )
                        navController.popBackStack()
                    }
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
                        onNavigateToTag = { tag ->
                            navController.openViewAll(
                                contentType = "ALL",
                                parentId = null,
                                title = tag,
                                tag = tag
                            )
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
                        onNavigateToTag = { tag ->
                            navController.openViewAll(
                                contentType = "ALL",
                                parentId = null,
                                title = tag,
                                tag = tag
                            )
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
                        },
                        onPlayItem = { itemId ->
                            PlayerActivity.start(context, itemId)
                        }
                    )
                } else {
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                }
            }

            composable(
                "viewall/{contentType}?parentId={parentId}&title={title}&genreId={genreId}&searchTerm={searchTerm}&tag={tag}",
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
                    },
                    navArgument("searchTerm") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("tag") {
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
                val searchTerm = backStackEntry.arguments?.getString("searchTerm")?.let {
                    java.net.URLDecoder.decode(it, "UTF-8")
                }?.takeIf { it.isNotBlank() }
                val tag = backStackEntry.arguments?.getString("tag")?.let {
                    java.net.URLDecoder.decode(it, "UTF-8")
                }?.takeIf { it.isNotBlank() }
                val title = backStackEntry.arguments?.getString("title")?.let {
                    java.net.URLDecoder.decode(it, "UTF-8")
                } ?: "View All"

                val contentType = when (contentTypeString.uppercase()) {
                    "MOVIES" -> com.vela.app.ui.screens.dashboard.media.ContentType.MOVIES
                    "SERIES" -> com.vela.app.ui.screens.dashboard.media.ContentType.SERIES
                    "EPISODES" -> com.vela.app.ui.screens.dashboard.media.ContentType.EPISODES
                    "MOVIES_GENRE" -> com.vela.app.ui.screens.dashboard.media.ContentType.MOVIES_GENRE
                    "TVSHOWS_GENRE" -> com.vela.app.ui.screens.dashboard.media.ContentType.TVSHOWS_GENRE
                    "SEERR_STUDIO" -> com.vela.app.ui.screens.dashboard.media.ContentType.SEERR_STUDIO
                    "SEERR_NETWORK" -> com.vela.app.ui.screens.dashboard.media.ContentType.SEERR_NETWORK
                    "AWARD" -> com.vela.app.ui.screens.dashboard.media.ContentType.AWARD
                    else -> com.vela.app.ui.screens.dashboard.media.ContentType.ALL
                }

                com.vela.app.ui.screens.dashboard.media.ViewAllScreen(
                    contentType = contentType,
                    parentId = parentId,
                    genreId = genreId,
                    searchTerm = searchTerm,
                    tag = tag,
                    title = title,
                    onBackPressed = { navController.popBackStack() },
                    onItemClick = { item ->
                        item.id?.let { itemId ->
                            val mergeVersions = parentId == com.vela.app.ui.screens.dashboard.media.WATCHED_VIEW_ALL_PARENT_ID
                            navController.navigate("detail/$itemId${if (mergeVersions) "?mergeVersions=true" else ""}")
                        }
                    },
                    onPlayFromBeginning = { itemId ->
                        PlayerActivity.start(
                            context = context,
                            mediaId = itemId,
                            startFromBeginning = true
                        )
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
                "downloads",
                enterTransition = { contentEnterTransition(enterEffectsSpec, enterSpatialSpec) },
                exitTransition = { contentExitTransition(exitEffectsSpec) }
            ) {
                DownloadsScreen(
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
                "servers",
                enterTransition = { contentEnterTransition(enterEffectsSpec, enterSpatialSpec) },
                exitTransition = { contentExitTransition(exitEffectsSpec) }
            ) {
                AppHomeContainer(
                    onServerSwitched = {
                        navController.enterDashboard()
                    },
                    onNavigateToDetail = { item ->
                        item.id?.let { itemId ->
                            navController.navigate("detail/$itemId")
                        }
                    },
                    onNavigateToViewAll = { contentType, parentId, title ->
                        navController.openViewAll(contentType, parentId, title)
                    },
                    onNavigateToPlayerSettings = {
                        navController.navigate("player_settings")
                    },
                    onNavigateToInterfaceSettings = {
                        navController.navigate("interface_settings")
                    },
                    onNavigateToConnections = {
                        navController.navigate("connections_settings")
                    },
                    onNavigateToDownloads = {
                        navController.navigate("downloads")
                    },
                    onNavigateToCacheSettings = {
                        navController.navigate("cache_settings")
                    },
                    onNavigateToAbout = {
                        navController.navigate("about")
                    },
                    onNavigateToServerInfo = {
                        navController.navigate("server_info")
                    },
                    onAddUser = { serverUrl, serverName ->
                        val encodedServerUrl = java.net.URLEncoder.encode(serverUrl, "UTF-8")
                        val encodedServerName = java.net.URLEncoder.encode(serverName.orEmpty(), "UTF-8")
                        navController.navigate(
                            "add_user?serverUrl=$encodedServerUrl&serverName=$encodedServerName"
                        ) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                "connections_settings",
                enterTransition = { contentEnterTransition(enterEffectsSpec, enterSpatialSpec) },
                exitTransition = { contentExitTransition(exitEffectsSpec) }
            ) {
                ConnectionsSettingsScreen(
                    onBackPressed = {
                        navController.popBackStack()
                    },
                    onNavigateToRequestedItem = { item ->
                        navController.navigate("detail/${item.id}")
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

            composable(
                "server_info",
                enterTransition = { contentEnterTransition(enterEffectsSpec, enterSpatialSpec) },
                exitTransition = { contentExitTransition(exitEffectsSpec) }
            ) {
                ServerInfoScreen(
                    onBackPressed = {
                        navController.popBackStack()
                    }
                )
            }
        }
}
