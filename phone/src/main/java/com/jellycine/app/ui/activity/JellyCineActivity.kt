package com.jellycine.app.ui.activity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.jellycine.app.locale.AppLanguageManager
import com.jellycine.app.ui.theme.JellyCineTheme
import com.jellycine.app.ui.navigation.AppNavigation
import com.jellycine.app.ui.splash.SplashScreen
import com.jellycine.app.ui.splash.SplashViewModel
import com.jellycine.auth.AuthStateManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@UnstableApi
@AndroidEntryPoint
class JellyCineActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    private val appPrefs by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        AppLanguageManager.applySavedLanguage(this)
        requestNotificationPermission()

        val authStateManager = AuthStateManager.getInstance(this)
        val authCheckCompleted = AtomicBoolean(false)
        val firstComposeCommitted = AtomicBoolean(false)
        splashScreen.setKeepOnScreenCondition {
            !authCheckCompleted.get() ||
                !firstComposeCommitted.get()
        }

        lifecycleScope.launch {
            authStateManager.checkAuthenticationState()
            authCheckCompleted.set(true)
        }

        // Use modern edge-to-edge approach
        enableEdgeToEdge()

        setContent {
            JellyCineTheme {
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    firstComposeCommitted.set(true)
                }

                // Handle system bar colors for edge-to-edge
                val view = LocalView.current
                SideEffect {
                    val window = (view.context as ComponentActivity).window
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

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        if (appPrefs.getBoolean(KEY_NOTIFICATION_PERMISSION_PROMPTED, false)) return

        appPrefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_PROMPTED, true).apply()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        private const val PREFS_NAME = "jellycine_app_prefs"
        private const val KEY_NOTIFICATION_PERMISSION_PROMPTED = "notification_permission_prompted"
    }
}
