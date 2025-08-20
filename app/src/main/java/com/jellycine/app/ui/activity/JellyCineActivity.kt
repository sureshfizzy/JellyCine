package com.jellycine.app.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

                try {
                    withContext(Dispatchers.IO) {
                        val preloadJobs = listOf(
                            async {
                                mediaRepository.getResumeItems(limit = 12)
                            },
                            async {
                                mediaRepository.getLatestItems(
                                    includeItemTypes = "Movie,Series",
                                    limit = 5
                                )
                            },
                            async {
                                mediaRepository.getUserViews()
                            },
                            async {
                                mediaRepository.getRecentlyAddedMovies(limit = 5)
                            },
                            async {
                                mediaRepository.getRecentlyAddedSeries(limit = 5)
                            }
                        )

                        preloadJobs.awaitAll()
                    }
                } catch (e: Exception) {
                }

                kotlinx.coroutines.delay(600)
            } else {
                kotlinx.coroutines.delay(300)
            }

            isReady = true
        }

        // Use modern edge-to-edge approach
        enableEdgeToEdge()

        setContent {
            JellyCineTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val splashViewModel: SplashViewModel = hiltViewModel()
                    val shouldShowSplash by splashViewModel.shouldShowSplash.collectAsState()

                    if (shouldShowSplash) {
                        SplashScreen(
                            onSplashComplete = {
                                splashViewModel.onSplashComplete()
                            }
                        )
                    } else {
                        AppNavigation()
                    }
                }
            }
        }
    }
}
