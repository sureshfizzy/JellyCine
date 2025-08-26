package com.jellycine.app.ui.screens.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.player.core.PlayerConstants.GESTURE_INDICATOR_PADDING_DP
import com.jellycine.player.core.PlayerState

enum class SeekSide {
    LEFT, CENTER, RIGHT
}

@Composable
fun GestureIndicators(
    modifier: Modifier = Modifier,
    volumeLevel: Float? = null,
    brightnessLevel: Float? = null,
    seekPosition: String? = null,
    seekSide: SeekSide = SeekSide.CENTER
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Volume indicator (right side)
        AnimatedVisibility(
            visible = volumeLevel != null,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 2 }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it / 2 }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            volumeLevel?.let { level ->
                VolumeIndicator(
                    level = level,
                    modifier = Modifier.padding(end = GESTURE_INDICATOR_PADDING_DP.dp)
                )
            }
        }

        // Brightness indicator (left side)
        AnimatedVisibility(
            visible = brightnessLevel != null,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it / 2 }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it / 2 }),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            brightnessLevel?.let { level ->
                BrightnessIndicator(
                    level = level,
                    modifier = Modifier.padding(start = GESTURE_INDICATOR_PADDING_DP.dp)
                )
            }
        }

        // Seek indicator (positioned based on seekSide)
        AnimatedVisibility(
            visible = seekPosition != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(
                when (seekSide) {
                    SeekSide.LEFT -> Alignment.CenterStart
                    SeekSide.CENTER -> Alignment.Center
                    SeekSide.RIGHT -> Alignment.CenterEnd
                }
            )
        ) {
            seekPosition?.let { position ->
                SeekIndicator(
                    position = position,
                    modifier = Modifier.padding(
                        start = if (seekSide == SeekSide.LEFT) GESTURE_INDICATOR_PADDING_DP.dp else 0.dp,
                        end = if (seekSide == SeekSide.RIGHT) GESTURE_INDICATOR_PADDING_DP.dp else 0.dp
                    )
                )
            }
        }
    }
}

@Composable
private fun VolumeIndicator(
    level: Float,
    modifier: Modifier = Modifier
) {
    val volumeIcon = when {
        level <= 0f -> Icons.AutoMirrored.Filled.VolumeOff
        level <= 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
        else -> Icons.AutoMirrored.Filled.VolumeUp
    }

    GestureIndicatorCard(
        icon = volumeIcon,
        value = "${(level * 100).toInt()}%",
        progress = level,
        modifier = modifier
    )
}

@Composable
private fun BrightnessIndicator(
    level: Float,
    modifier: Modifier = Modifier
) {
    val brightnessIcon = when {
        level <= 0.3f -> Icons.Filled.BrightnessLow
        level <= 0.7f -> Icons.Filled.BrightnessMedium
        else -> Icons.Filled.BrightnessHigh
    }

    GestureIndicatorCard(
        icon = brightnessIcon,
        value = "${(level * 100).toInt()}%",
        progress = level,
        modifier = modifier
    )
}

@Composable
private fun SeekIndicator(
    position: String,
    modifier: Modifier = Modifier
) {
    // No background - transparent like volume/brightness indicators
    Row(
        modifier = modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Show different icon based on seek direction
        val icon = if (position.startsWith("+")) {
            Icons.Filled.FastForward
        } else {
            Icons.Filled.FastRewind
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = position,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun GestureIndicatorCard(
    icon: ImageVector,
    value: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    // No background - transparent overlay like 
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        // Vertical progress bar like 
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(120.dp)
                .background(
                    Color.White.copy(alpha = 0.3f),
                    RoundedCornerShape(3.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(progress)
                    .align(Alignment.BottomCenter)
                    .background(
                        Color.White,
                        RoundedCornerShape(3.dp)
                    )
            )
        }

        // Icon below the progress bar - smaller size
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun RippleAnimation(
    isVisible: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ripple_scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ripple_alpha"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .alpha(alpha)
                .background(
                    Color.White.copy(alpha = 0.2f),
                    RoundedCornerShape(60.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(48.dp)
                    .alpha(1f)
            )
        }
    }
}
