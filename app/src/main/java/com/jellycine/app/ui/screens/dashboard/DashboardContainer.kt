package com.jellycine.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.sqrt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jellycine.app.ui.screens.dashboard.home.Dashboard
import com.jellycine.app.ui.screens.dashboard.settings.Settings
import com.jellycine.app.ui.screens.dashboard.media.MyMedia
import com.jellycine.app.ui.screens.dashboard.favorites.Favorites
import com.jellycine.app.ui.theme.JellyBlue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.scale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin

sealed class DashboardDestination(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : DashboardDestination(
        "dashboard_home",
        "Home",
        Icons.Rounded.Home,
        Icons.Outlined.Home
    )
    object MyMedia : DashboardDestination(
        "my_media",
        "My Media",
        Icons.Rounded.PlayArrow,
        Icons.Outlined.PlayArrow
    )
    object Search : DashboardDestination(
        "search",
        "Search",
        Icons.Rounded.Search,
        Icons.Outlined.Search
    )
    object Favorites : DashboardDestination(
        "favorites",
        "Favorites",
        Icons.Rounded.Favorite,
        Icons.Outlined.FavoriteBorder
    )
    object Settings : DashboardDestination(
        "settings",
        "Settings",
        Icons.Rounded.Settings,
        Icons.Outlined.Settings
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContainer(
    onLogout: () -> Unit = {},
    onNavigateToDetail: (com.jellycine.data.model.BaseItemDto) -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val sideDestinations = listOf(
        DashboardDestination.Home,
        DashboardDestination.MyMedia,
        DashboardDestination.Favorites,
        DashboardDestination.Settings
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Main content area
            NavHost(
                navController = navController,
                startDestination = DashboardDestination.Home.route,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(animationSpec = tween(200)) },
                exitTransition = { fadeOut(animationSpec = tween(200)) }
            ) {
                composable(DashboardDestination.Home.route) {
                    Dashboard(
                        onLogout = onLogout,
                        onNavigateToDetail = onNavigateToDetail
                    )
                }
                composable(DashboardDestination.MyMedia.route) {
                    MyMedia()
                }
                composable(DashboardDestination.Search.route) {
                    // TODO: Implement search screen
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Search Screen",
                            color = Color.White,
                            fontSize = 24.sp
                        )
                    }
                }
                composable(DashboardDestination.Favorites.route) {
                    Favorites()
                }
                composable(DashboardDestination.Settings.route) {
                    Settings(onLogout = onLogout)
                }
            }

            // Curved Bottom Navigation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                // Curved navigation bar background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .drawBehind {
                            drawCurvedNavigationBar(
                                width = size.width,
                                height = size.height
                            )
                        }
                )

                // Navigation items container
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                        sideDestinations.take(2).forEach { destination ->
                            val isSelected = currentRoute == destination.route
                            NavigationItem(
                                destination = destination,
                                isSelected = isSelected,
                                onClick = {
                                    if (currentRoute != destination.route) {
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                        sideDestinations.drop(2).forEach { destination ->
                            val isSelected = currentRoute == destination.route
                            NavigationItem(
                                destination = destination,
                                isSelected = isSelected,
                                onClick = {
                                    if (currentRoute != destination.route) {
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .offset(y = (-22).dp),
                    contentAlignment = Alignment.Center
                ) {
                    FloatingSearchButton(
                        isSelected = currentRoute == DashboardDestination.Search.route,
                        onClick = {
                            if (currentRoute != DashboardDestination.Search.route) {
                                navController.navigate(DashboardDestination.Search.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

// Function to draw the curved navigation bar
private fun DrawScope.drawCurvedNavigationBar(
    width: Float,
    height: Float
) {
    val centerWidth = width / 2f

    val fabRadius = 28.dp.toPx()
    val curveDepth = fabRadius + (fabRadius / 3f)
    val curveWidth = (fabRadius * 2) + (fabRadius * 1.5f)

    val curveStartX = centerWidth - (curveWidth / 2)
    val curveEndX = centerWidth + (curveWidth / 2)

    val controlPoint1X = curveStartX + curveWidth * 0.15f
    val controlPoint2X = centerWidth - curveWidth * 0.30f
    
    val controlPoint3X = centerWidth + curveWidth * 0.30f
    val controlPoint4X = curveEndX - curveWidth * 0.15f
    
    val backgroundPath = Path().apply {
        moveTo(0f, 0f)
        lineTo(curveStartX, 0f)
        
        cubicTo(
            x1 = controlPoint1X, y1 = 0f,
            x2 = controlPoint2X, y2 = curveDepth,
            x3 = centerWidth, y3 = curveDepth
        )
        
        cubicTo(
            x1 = controlPoint3X, y1 = curveDepth,
            x2 = controlPoint4X, y2 = 0f,
            x3 = curveEndX, y3 = 0f
        )
        
        lineTo(width, 0f)
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }

    drawPath(
        path = backgroundPath,
        color = Color.Black
    )
}

@Composable
private fun FloatingSearchButton(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                clip = false
            )
            .background(
                color = if (isSelected) JellyBlue.copy(alpha = 0.9f) else JellyBlue,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Filled.Search else Icons.Outlined.Search,
            contentDescription = "Search",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun NavigationItem(
    destination: DashboardDestination,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Animations for selection state
    val color by animateColorAsState(
        targetValue = if (isSelected) JellyBlue else Color.White.copy(alpha = 0.7f),
        animationSpec = tween(durationMillis = 300)
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 300f
        )
    )

    val offsetY by animateDpAsState(
        targetValue = if (isSelected) (-6).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 300f
        )
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(300)
    )

    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .height(60.dp)
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = destination.title,
            tint = color,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = offsetY.toPx()
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
        )

        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = destination.title,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.alpha(textAlpha)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardContainerPreview() {
    DashboardContainer()
}