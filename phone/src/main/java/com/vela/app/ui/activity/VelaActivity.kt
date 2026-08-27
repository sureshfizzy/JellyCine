package com.vela.app.ui.activity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.vela.app.locale.AppLanguageManager
import com.vela.shared.ui.theme.VelaTheme
import com.vela.app.ui.navigation.AppNavigation
import com.vela.app.ui.splash.SplashScreen
import com.vela.app.ui.splash.SplashViewModel
import com.vela.auth.AuthStateManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@UnstableApi
@AndroidEntryPoint
class VelaActivity : ComponentActivity() {

    var userLeaveHintHandler: (() -> Unit)? = null

    private val pipMode = MutableStateFlow(false)
    val pictureInPictureMode: StateFlow<Boolean> = pipMode.asStateFlow()

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

        applyEdgeToEdgeSystemBars()

        setContent {
            VelaTheme {
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    firstComposeCommitted.set(true)
                }

                SideEffect {
                    applyEdgeToEdgeSystemBars()
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

    override fun onResume() {
        super.onResume()
        applyEdgeToEdgeSystemBars()
        window.decorView.post(::applyEdgeToEdgeSystemBars)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyEdgeToEdgeSystemBars()
        }
    }

    private fun applyEdgeToEdgeSystemBars() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        userLeaveHintHandler?.invoke()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipMode.value = isInPictureInPictureMode
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
        private const val PREFS_NAME = "vela_app_prefs"
        private const val KEY_NOTIFICATION_PERMISSION_PROMPTED = "notification_permission_prompted"
    }
}
