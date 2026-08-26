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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    seekSide: SeekSide = SeekSide.CENTER,
    swipeSeekPositionMs: Long? = null,
    swipeSeekDurationMs: Long = 0L,
    holdSpeedLabel: String? = null
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
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

        AnimatedVisibility(
            visible = seekPosition != null && swipeSeekPositionMs == null,
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

        AnimatedVisibility(
            visible = swipeSeekPositionMs != null && swipeSeekDurationMs > 0L,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp)
        ) {
            val position = swipeSeekPositionMs ?: 0L
            SwipeSeekHud(
                positionMs = position,
                durationMs = swipeSeekDurationMs
            )
        }

        AnimatedVisibility(
            visible = holdSpeedLabel != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = maxHeight * 0.25f)
        ) {
            holdSpeedLabel?.let { label ->
                PlayerGlass(shape = RoundedCornerShape(999.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SeekTimeHud(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    PlayerGlass(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = "${formatPlaybackTime(positionMs)} / ${formatPlaybackTime(durationMs)}",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SwipeSeekHud(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SeekTimeHud(positionMs = positionMs, durationMs = durationMs)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(4.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.28f),
        )
    }
}

internal fun formatPlaybackTime(timeMs: Long): String {
    val totalSeconds = timeMs.coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
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
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(52.dp)
        )

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
