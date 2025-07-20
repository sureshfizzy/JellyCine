package com.jellycine.app.feature.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.manager.AuthStateManager
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToAuth: () -> Unit = {},
    onNavigateToHome: () -> Unit = {}
) {
    val context = LocalContext.current
    val authStateManager = remember { AuthStateManager.getInstance(context) }

    LaunchedEffect(Unit) {
        delay(800)
        val isAuthenticated = authStateManager.checkAuthenticationState()
        if (isAuthenticated) {
            onNavigateToHome()
        } else {
            onNavigateToAuth()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black,
                        Color(0xFF1a1a2e),
                        Color.Black
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "JellyCine",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Modern Jellyfin Client",
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Normal
            )
        }
    }
}
