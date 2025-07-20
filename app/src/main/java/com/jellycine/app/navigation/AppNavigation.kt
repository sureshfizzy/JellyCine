package com.jellycine.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.jellycine.app.feature.home.HomeScreen
import com.jellycine.app.feature.auth.AuthScreen
import com.jellycine.app.feature.splash.SplashScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()


    NavHost(
        navController = navController,
        startDestination = "splash",
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
                        navController.navigate("home") {
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
                        navController.navigate("home") {
                            popUpTo("server_connection") { inclusive = true }
                        }
                    }
                )
            }

            composable("home") {
                HomeScreen(
                    onLogout = {
                        navController.navigate("splash") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                )
            }
        }
}
