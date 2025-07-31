package com.jellycine.app.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.jellycine.app.ui.theme.JellyCineTheme
import com.jellycine.app.ui.navigation.AppNavigation
import dagger.hilt.android.AndroidEntryPoint
import androidx.media3.common.util.UnstableApi

@UnstableApi
@AndroidEntryPoint
class JellyCineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use modern edge-to-edge approach
        enableEdgeToEdge()

        setContent {
            JellyCineTheme {
                AppNavigation()
            }
        }
    }
}
