package com.jellycine.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.jellycine.app.ui.screens.dashboard.DashboardContainer
import com.jellycine.app.ui.screens.auth.AuthScreen
import com.jellycine.app.ui.screens.splash.SplashScreen
import com.jellycine.app.ui.screens.detail.DetailScreen
import com.jellycine.app.ui.screens.detail.DetailScreenContainer
import com.jellycine.data.model.BaseItemDto
import com.google.gson.Gson
import com.jellycine.auth.AuthStateManager
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import androidx.media3.common.util.UnstableApi

@UnstableApi
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val authStateManager = remember { AuthStateManager.getInstance(context) }

    // Determine start destination based on auth state to avoid unnecessary splash
    val startDestination = remember {
        if (authStateManager.checkAuthenticationStateSync()) {
            "dashboard"
        } else {
            "splash"
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.fillMaxSize()
    ) {
            composable("splash") {
                SplashScreen(
                    onNavigateToAuth = {
                        navController.navigate("server_connection") {
                            popUpTo("splash") { inclusive = true }
                        }
                    },
                    onNavigateToHome = {
                        navController.navigate("dashboard") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                )
            }

            composable(
                "server_connection",
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(400)
                    ) + fadeIn(animationSpec = tween(400))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(400)
                    ) + fadeOut(animationSpec = tween(400))
                }
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
                enterTransition = {
                    fadeIn(animationSpec = tween(200))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(200))
                }
            ) {
                DashboardContainer(
                    onLogout = {
                        navController.navigate("splash") {
                            popUpTo("dashboard") { inclusive = true }
                        }
                    },
                    onNavigateToDetail = { item ->
                        item.id?.let { itemId ->
                            navController.navigate("detail/$itemId")
                        }
                    },
                    onNavigateToViewAll = { contentType, parentId, title ->
                        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                        val route = if (parentId != null) {
                            "viewall/$contentType?parentId=$parentId&title=$encodedTitle"
                        } else {
                            "viewall/$contentType?title=$encodedTitle"
                        }
                        navController.navigate(route)
                    }
                )
            }

            composable(
                "detail/{itemId}",
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(300)
                    ) + fadeIn(animationSpec = tween(300))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(300)
                    ) + fadeOut(animationSpec = tween(300))
                }
            ) { backStackEntry ->
                val itemId = backStackEntry.arguments?.getString("itemId")

                if (itemId != null) {
                    DetailScreenContainer(
                        itemId = itemId,
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
                "viewall/{contentType}?parentId={parentId}&title={title}",
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
                    }
                ),
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(300)
                    ) + fadeIn(animationSpec = tween(300))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(300)
                    ) + fadeOut(animationSpec = tween(300))
                }
            ) { backStackEntry ->
                val contentTypeString = backStackEntry.arguments?.getString("contentType") ?: "ALL"
                val parentId = backStackEntry.arguments?.getString("parentId")
                val title = backStackEntry.arguments?.getString("title")?.let { 
                    java.net.URLDecoder.decode(it, "UTF-8") 
                } ?: "View All"

                val contentType = when (contentTypeString.uppercase()) {
                    "MOVIES" -> com.jellycine.app.ui.screens.dashboard.media.ContentType.MOVIES
                    "SERIES" -> com.jellycine.app.ui.screens.dashboard.media.ContentType.SERIES
                    "EPISODES" -> com.jellycine.app.ui.screens.dashboard.media.ContentType.EPISODES
                    else -> com.jellycine.app.ui.screens.dashboard.media.ContentType.ALL
                }

                com.jellycine.app.ui.screens.dashboard.media.ViewAllScreen(
                    contentType = contentType,
                    parentId = parentId,
                    title = title,
                    onBackPressed = {
                        navController.popBackStack()
                    },
                    onItemClick = { item ->
                        item.id?.let { itemId ->
                            navController.navigate("detail/$itemId")
                        }
                    }
                )
            }
        }
}