package com.jellycine.app.ui.components.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * Screen Wrapper
 * Provides transformations for entire screens
 */
@Composable
fun ScreenWrapper(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    
    // transformation states
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = 400,
            easing = FastOutSlowInEasing
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                alpha = alpha,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
            )
    ) {
        content()
    }
}

/**
 * Card Component
 * For interactive cards with hover/press effects
 */
@Composable
fun AnimatedCard(
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    colors: CardColors = CardDefaults.cardColors(),
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    var isHovered by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    
    // Animation states
    val scale by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.95f
            isPressed -> 0.95f
            isHovered -> 1.05f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    
    val rotationX by animateFloatAsState(
        targetValue = when {
            !enabled -> 0f
            isPressed -> 8f
            isHovered -> -12f
            else -> 0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    
    val rotationY by animateFloatAsState(
        targetValue = when {
            !enabled -> 0f
            isPressed -> -3f
            isHovered -> 5f
            else -> 0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    
    val dynamicElevation by animateDpAsState(
        targetValue = when {
            !enabled -> 2.dp
            isPressed -> 2.dp
            isHovered -> 16.dp
            else -> 4.dp
        },
        animationSpec = tween(300)
    )

    Card(
        onClick = if (enabled) {
            {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
        } else {
            {}
        },
        modifier = modifier
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                rotationX = rotationX,
                rotationY = rotationY,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center,
                cameraDistance = 12f * density.density
            )
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        }
                    )
                }
            },
        colors = colors,
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = dynamicElevation),
        content = content
    )
}

/**
 * List Item
 * For list items with subtle effects
 */
@Composable
fun ListItem(
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    
    // Subtle effects for list items
    val scale by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.98f
            isPressed -> 0.98f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        )
    )
    
    val rotationX by animateFloatAsState(
        targetValue = when {
            !enabled -> 0f
            isPressed -> 2f
            else -> 0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    Card(
        onClick = if (enabled) {
            {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
        } else {
            {}
        },
        modifier = modifier
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                rotationX = rotationX,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center,
                cameraDistance = 20f * density.density
            )
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        }
                    )
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}