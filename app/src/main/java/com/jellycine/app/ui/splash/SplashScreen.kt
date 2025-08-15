package com.jellycine.app.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    var animationPhase by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    
    val logoAlpha by animateFloatAsState(
        targetValue = if (animationPhase >= 1) 1f else 0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "logoAlpha"
    )
    
    val logoScale by animateFloatAsState(
        targetValue = if (animationPhase >= 1) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "logoScale"
    )
    
    val logoRotationY by animateFloatAsState(
        targetValue = if (animationPhase >= 1) 0f else -15f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "logoRotationY"
    )
    
    val textAlpha by animateFloatAsState(
        targetValue = if (animationPhase >= 2) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "textAlpha"
    )

    LaunchedEffect(Unit) {
        delay(200)
        animationPhase = 1
        delay(800)
        animationPhase = 2
        delay(1500)
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .scale(logoScale)
                    .graphicsLayer(
                        rotationY = logoRotationY,
                        cameraDistance = 12f * density.density
                    )
            ) {
                repeat(3) { index ->
                    val shadowOffset = (index + 1) * 2f
                    val shadowAlpha = logoAlpha * (0.2f - index * 0.05f)
                    
                    Image(
                        painter = painterResource(id = R.drawable.jellycine_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(200.dp)
                            .alpha(shadowAlpha)
                            .graphicsLayer(
                                translationX = shadowOffset,
                                translationY = shadowOffset
                            )
                    )
                }
                
                Image(
                    painter = painterResource(id = R.drawable.jellycine_logo),
                    contentDescription = "JellyCine Logo",
                    modifier = Modifier
                        .size(200.dp)
                        .alpha(logoAlpha)
                        .graphicsLayer(
                            shadowElevation = 12.dp.value
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Box(
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "JellyCine",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black.copy(alpha = 0.3f),
                    modifier = Modifier
                        .alpha(textAlpha)
                        .graphicsLayer(
                            translationX = 2.dp.value,
                            translationY = 2.dp.value
                        )
                )
                
                Text(
                    text = "JellyCine",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier.alpha(textAlpha)
                )
            }
        }
    }
}