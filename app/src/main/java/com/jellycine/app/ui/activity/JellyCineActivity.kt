package com.jellycine.app.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.jellycine.app.ui.theme.JellyCineTheme
import com.jellycine.app.ui.navigation.AppNavigation
import com.jellycine.app.ui.splash.SplashScreen
import com.jellycine.app.ui.splash.SplashViewModel
import com.jellycine.auth.AuthStateManager
import com.jellycine.data.repository.MediaRepositoryProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

@UnstableApi
@AndroidEntryPoint
class JellyCineActivity : ComponentActivity() {

        override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        val authStateManager = AuthStateManager.getInstance(this)
        var isReady = false
        splashScreen.setKeepOnScreenCondition { !isReady }

        lifecycleScope.launch {
            val isAuthenticated = authStateManager.checkAuthenticationState()

            if (isAuthenticated) {
                val mediaRepository = MediaRepositoryProvider.getInstance(this@JellyCineActivity)
                // Keep splash minimal; perform heavy preloading in background.
                kotlinx.coroutines.delay(150)
                isReady = true

                launch(Dispatchers.IO) {
                    try {
                        mediaRepository.getResumeItems(limit = 12)
                        mediaRepository.getLatestItems(
                            includeItemTypes = "Movie,Series",
                            limit = 5
                        )
                        mediaRepository.getUserViews()
                        mediaRepository.getRecentlyAddedMovies(limit = 5)
                        mediaRepository.getRecentlyAddedSeries(limit = 5)
                    } catch (_: Exception) {
                    }
                }
            } else {
                kotlinx.coroutines.delay(120)
                isReady = true
            }
        }

        // Use modern edge-to-edge approach
        enableEdgeToEdge()

        setContent {
            JellyCineTheme {
                // Handle system bar colors for edge-to-edge
                val view = LocalView.current
                SideEffect {
                    val window = (view.context as ComponentActivity).window
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = android.graphics.Color.BLACK
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                    WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val splashViewModel: SplashViewModel = hiltViewModel()
                    val shouldShowSplash by splashViewModel.shouldShowSplash.collectAsState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppNavigation()
                        if (shouldShowSplash) {
                            SplashScreen(
                                onSplashComplete = {
                                    splashViewModel.onSplashComplete()
                                }
                            )
                        } 
                    }
                }
            }
        }
    }
}
