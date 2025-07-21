package com.jellycine.app.navigation

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
import com.jellycine.app.feature.dashboard.DashboardContainer
import com.jellycine.app.feature.auth.AuthScreen
import com.jellycine.app.feature.splash.SplashScreen
import com.jellycine.app.feature.detail.DetailScreen
import com.jellycine.app.feature.detail.DetailScreenContainer
import com.jellycine.data.model.BaseItemDto
import com.google.gson.Gson
import com.jellycine.app.manager.AuthStateManager
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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
                        },
                        onPlayClick = {
                        }
                    )
                } else {
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                }
            }
        }
}
