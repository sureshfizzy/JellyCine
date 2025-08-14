package com.jellycine.app.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.jellycine.app.ui.theme.JellyCineTheme
import com.jellycine.app.ui.navigation.AppNavigation
import com.jellycine.auth.AuthStateManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@UnstableApi
@AndroidEntryPoint
class JellyCineActivity : ComponentActivity() {

        override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        val authStateManager = AuthStateManager.getInstance(this)
        var isAuthReady = false

        splashScreen.setKeepOnScreenCondition { 
            !isAuthReady
        }

        lifecycleScope.launch {
            kotlinx.coroutines.delay(300)
            isAuthReady = true
        }

        // Use modern edge-to-edge approach
        enableEdgeToEdge()

        setContent {
            JellyCineTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
