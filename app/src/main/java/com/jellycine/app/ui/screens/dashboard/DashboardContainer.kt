package com.jellycine.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback


sealed class DashboardDestination(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : DashboardDestination(
        "dashboard_home",
        "Home",
        Icons.Filled.Home,
        Icons.Outlined.Home
    )
    object MyMedia : DashboardDestination(
        "my_media",
        "My Media",
        Icons.Filled.PlayArrow,
        Icons.Outlined.PlayArrow
    )
    object Search : DashboardDestination(
        "search",
        "Search",
        Icons.Filled.Search,
        Icons.Outlined.Search
    )
    object Favorites : DashboardDestination(
        "favorites",
        "Favorites",
        Icons.Filled.Favorite,
        Icons.Outlined.FavoriteBorder
    )
    object Settings : DashboardDestination(
        "settings",
        "Settings",
        Icons.Filled.Settings,
        Icons.Outlined.Settings
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContainer(
    onLogout: () -> Unit = {},
    onNavigateToDetail: (com.jellycine.data.model.BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
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
                    // Track when Home tab becomes active
                    val isHomeActive = currentRoute == DashboardDestination.Home.route

                    Dashboard(
                        onLogout = onLogout,
                        onNavigateToDetail = onNavigateToDetail,
                        onNavigateToViewAll = onNavigateToViewAll,
                        isTabActive = isHomeActive
                    )
                }
                composable(DashboardDestination.MyMedia.route) {
                    MyMedia(
                        onLibraryClick = { contentType, parentId, title ->
                            onNavigateToViewAll(contentType.name, parentId, title)
                        }
                    )
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

            // Curved Bottom Navigation with Glass Effect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                // AMOLED black background layer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(Color.Black)
                        .drawBehind {
                            drawCurvedNavigationBarGlass(
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
                    // Left side items
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
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

                    // Right side items
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
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

                // Center search button positioned in the curve
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

// Function to draw the curved navigation bar with glass effect
private fun DrawScope.drawCurvedNavigationBarGlass(
    width: Float,
    height: Float
) {
    val centerWidth = width / 2f

    // Use dimensions that are proportional to the FAB size for a good fit
    val fabRadius = 28.dp.toPx()
    val curveDepth = fabRadius + (fabRadius / 3f)
    val curveWidth = (fabRadius * 2) + (fabRadius * 1.5f)

    val curveStartX = centerWidth - (curveWidth / 2)
    val curveEndX = centerWidth + (curveWidth / 2)

    // Control points for a wide, smooth curve
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

    // Glass morphism effect with multiple layers
    // Base glass layer with blur effect
    drawPath(
        path = backgroundPath,
        color = Color.White.copy(alpha = 0.05f)
    )

    // Secondary layer for depth
    drawPath(
        path = backgroundPath,
        color = Color.Black.copy(alpha = 0.4f)
    )

    // Subtle border for glass effect
    drawPath(
        path = backgroundPath,
        color = Color.White.copy(alpha = 0.1f),
        style = Stroke(width = 1.dp.toPx())
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
                color = Color.White,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Filled.Search else Icons.Outlined.Search,
            contentDescription = "Search",
            tint = Color.Black,
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
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
        animationSpec = tween(durationMillis = 300)
    )
    val iconSize by animateDpAsState(
        targetValue = if (isSelected) 24.dp else 22.dp,
        animationSpec = tween(durationMillis = 300)
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f, // 2. Only show text when selected
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
            modifier = Modifier.size(iconSize)
        )

        if (isSelected) {
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
}

@Preview(showBackground = true)
@Composable
fun DashboardContainerPreview() {
    DashboardContainer()
}

