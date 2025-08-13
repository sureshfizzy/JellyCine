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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import kotlinx.coroutines.flow.first
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
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
import com.jellycine.app.ui.screens.dashboard.search.SearchContainer
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition

private fun DashboardEnterTransition(): EnterTransition {
    return fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing))
}

private fun DashboardExitTransition(): ExitTransition {
    return fadeOut(animationSpec = tween(300, easing = LinearOutSlowInEasing))
}

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
    val density = LocalDensity.current



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
            // Main content area with transitions and parallax effect
            NavHost(
                navController = navController,
                startDestination = DashboardDestination.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        translationY = when (currentRoute) {
                            DashboardDestination.Search.route -> -2f
                            else -> 0f
                        },
                        transformOrigin = TransformOrigin.Center
                    )
            ) {
                composable(
                    DashboardDestination.Home.route,
                    enterTransition = { DashboardEnterTransition() },
                    exitTransition = { DashboardExitTransition() }
                ) {
                    // Track when Home tab becomes active
                    val isHomeActive = currentRoute == DashboardDestination.Home.route

                    ContentWrapper(
                        isActive = isHomeActive,
                        route = DashboardDestination.Home.route
                    ) {
                        Dashboard(
                            onLogout = onLogout,
                            onNavigateToDetail = onNavigateToDetail,
                            onNavigateToViewAll = onNavigateToViewAll,
                            isTabActive = isHomeActive
                        )
                    }
                }
                composable(
                    DashboardDestination.MyMedia.route,
                    enterTransition = { DashboardEnterTransition() },
                    exitTransition = { DashboardExitTransition() }
                ) {
                    ContentWrapper(
                        isActive = currentRoute == DashboardDestination.MyMedia.route,
                        route = DashboardDestination.MyMedia.route
                    ) {
                        MyMedia(
                            onLibraryClick = { contentType, parentId, title ->
                                onNavigateToViewAll(contentType.name, parentId, title)
                            }
                        )
                    }
                }
                composable(
                    DashboardDestination.Search.route,
                    enterTransition = { DashboardEnterTransition() },
                    exitTransition = { DashboardExitTransition() }
                ) {
                    ContentWrapper(
                        isActive = currentRoute == DashboardDestination.Search.route,
                        route = DashboardDestination.Search.route,
                        isSearchScreen = true
                    ) {
                        SearchContainer(
                            onNavigateToDetail = onNavigateToDetail,
                            onCancel = {
                                // Navigate back to home when cancel is pressed
                                navController.navigate(DashboardDestination.Home.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
                composable(
                    DashboardDestination.Favorites.route,
                    enterTransition = { DashboardEnterTransition() },
                    exitTransition = { DashboardExitTransition() }
                ) {
                    ContentWrapper(
                        isActive = currentRoute == DashboardDestination.Favorites.route,
                        route = DashboardDestination.Favorites.route
                    ) {
                        Favorites()
                    }
                }
                composable(
                    DashboardDestination.Settings.route,
                    enterTransition = { DashboardEnterTransition() },
                    exitTransition = { DashboardExitTransition() }
                ) {
                    ContentWrapper(
                        isActive = currentRoute == DashboardDestination.Settings.route,
                        route = DashboardDestination.Settings.route
                    ) {
                        Settings(onLogout = onLogout)
                    }
                }
            }

            // Curved Bottom Navigation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(Color.Black)
                        .graphicsLayer(
                            rotationX = -3f,
                            transformOrigin = TransformOrigin(0.5f, 1f),
                            scaleY = when (currentRoute) {
                                DashboardDestination.Search.route -> 1.2f
                                else -> 1f
                            },
                            cameraDistance = 8f * density.density
                        )
                        .drawBehind {
                            draw3DCurvedNavigationBar(
                                width = size.width,
                                height = size.height,
                                currentRoute = currentRoute
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

// Function to draw curved navigation bar with dynamic effects
private fun DrawScope.draw3DCurvedNavigationBar(
    width: Float,
    height: Float,
    currentRoute: String?
) {
    val centerWidth = width / 2f

    // Use dimensions that are proportional to the FAB size for a good fit
    val fabRadius = 28.dp.toPx()
    val curveDepth = fabRadius + (fabRadius / 3f)
    val curveWidth = (fabRadius * 2) + (fabRadius * 1.5f)

    val curveStartX = centerWidth - (curveWidth / 2)
    val curveEndX = centerWidth + (curveWidth / 2)

    // Control points for smoother curve
    val controlPoint1X = curveStartX + curveWidth * 0.12f
    val controlPoint2X = centerWidth - curveWidth * 0.35f
    val controlPoint3X = centerWidth + curveWidth * 0.35f
    val controlPoint4X = curveEndX - curveWidth * 0.12f
    
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

    // AMOLED Black background with subtle depth
    drawPath(
        path = backgroundPath,
        color = Color.Black
    )

    drawPath(
        path = backgroundPath,
        color = Color.White.copy(alpha = 0.05f),
        style = Stroke(width = 1.dp.toPx())
    )
}

@Composable
private fun FloatingSearchButton(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    // Animation states with breathing effect
    val infiniteTransition = rememberInfiniteTransition(label = "search_breathing")
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_scale"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f * breathingScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val elevation by animateDpAsState(
        targetValue = if (isSelected) 16.dp else 8.dp,
        animationSpec = tween(300)
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.95f),
        animationSpec = tween(300)
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                transformOrigin = TransformOrigin.Center
            )
            .shadow(
                elevation = elevation,
                shape = CircleShape,
                clip = false
            )
            .background(
                color = backgroundColor,
                shape = CircleShape
            )
            .clickable { 
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick() 
            },
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

    // animations for selection state
    val color by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
    )
    
    val iconSize by animateDpAsState(
        targetValue = if (isSelected) 26.dp else 22.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    
    val textAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(400, easing = LinearOutSlowInEasing)
    )
    
    // transformation effects
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    
    val rotationX by animateFloatAsState(
        targetValue = if (isSelected) -10f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing)
    )
    
    val translationY by animateFloatAsState(
        targetValue = if (isSelected) -2f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
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
            .padding(horizontal = 8.dp)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                rotationX = rotationX,
                translationY = translationY,
                transformOrigin = TransformOrigin.Center
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon with glow effect when selected
        Box(
            contentAlignment = Alignment.Center
        ) {
            // Glow effect background
            if (isSelected) {
                Icon(
                    imageVector = destination.selectedIcon,
                    contentDescription = null,
                    tint = color.copy(alpha = 0.3f),
                    modifier = Modifier
                        .size(iconSize + 8.dp)
                        .blur(4.dp)
                )
            }
            
            // Main icon
            Icon(
                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                contentDescription = destination.title,
                tint = color,
                modifier = Modifier.size(iconSize)
            )
        }

        if (isSelected) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = destination.title,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .alpha(textAlpha)
                    .graphicsLayer(
                        scaleX = textAlpha,
                        scaleY = textAlpha
                    )
            )
        }
    }
}

@Composable
private fun ContentWrapper(
    isActive: Boolean,
    route: String,
    isSearchScreen: Boolean = false,
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
            durationMillis = if (isSearchScreen) 400 else 300,
            easing = if (isSearchScreen) FastOutSlowInEasing else LinearOutSlowInEasing
        )
    )
    
    // Removed tilt effects - keeping only scale and fade

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                alpha = alpha,
                transformOrigin = TransformOrigin.Center
            )
    ) {
        content()
    }
}

// Poster Component for general use
@Composable
fun PosterCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    var isHovered by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    
    // Animation states
    val scale by animateFloatAsState(
        targetValue = when {
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
            isPressed -> -3f
            isHovered -> 5f
            else -> 0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    
    val elevation by animateDpAsState(
        targetValue = when {
            isPressed -> 2.dp
            isHovered -> 16.dp
            else -> 4.dp
        },
        animationSpec = tween(300)
    )

    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                rotationX = rotationX,
                rotationY = rotationY,
                transformOrigin = TransformOrigin.Center,
                cameraDistance = 12f * density.density
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        content()
    }
}

// List Item Component
@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    
    // Subtle effects for list items
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        )
    )
    
    val rotationX by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                rotationX = rotationX,
                transformOrigin = TransformOrigin.Center,
                cameraDistance = 20f * density.density
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        content()
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardContainerPreview() {
    DashboardContainer()
}

@Composable
fun ShimmerEffect(
    modifier: Modifier = Modifier,
    cornerRadius: Float = 12f,
    shape: Shape = RoundedCornerShape(cornerRadius.dp)
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha = transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Canvas(
        modifier = modifier.graphicsLayer(
            compositingStrategy = CompositingStrategy.Offscreen
        )
    ) {
        drawRoundRect(
            color = Color(0xFF2A2A2A).copy(alpha = alpha.value),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )
    }
}

/**
 * Skeleton for poster/card items in horizontal rows
 * Used in: Dashboard sections, Continue Watching, etc.
 */
@Composable
fun ActualImageBlurPlaceholder(
    itemId: String,
    mediaRepository: com.jellycine.data.repository.MediaRepository,
    modifier: Modifier = Modifier,
    width: Dp = 140.dp,
    height: Dp = 210.dp,
    cornerRadius: Float = 16f,
    imageType: String = "Primary"
) {
    var blurImageUrl by remember(itemId) { mutableStateOf<String?>(null) }

    LaunchedEffect(itemId) {
        try {
            val url = mediaRepository.getImageUrl(
                itemId = itemId,
                imageType = imageType,
                width = if (imageType == "Thumb") 50 else 30,
                height = if (imageType == "Thumb") 30 else 45,
                quality = 5
            ).first()
            blurImageUrl = url
        } catch (e: Exception) {
        }
    }

    if (blurImageUrl != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(blurImageUrl)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .allowHardware(true)
                .allowRgb565(true)
                .crossfade(0)
                .build(),
            contentDescription = null,
            modifier = modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(cornerRadius.dp))
                .blur(radius = 8.dp),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(cornerRadius.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        )
    }
}

@Composable
fun PosterSkeleton(
    modifier: Modifier = Modifier,
    width: Dp = 140.dp,
    height: Dp = 210.dp,
    cornerRadius: Float = 16f
) {
    ShimmerEffect(
        modifier = modifier
            .width(width)
            .height(height),
        cornerRadius = cornerRadius
    )
}

/**
 * Skeleton for continue watching items (landscape orientation)
 */
@Composable
fun ContinueWatchingSkeleton(
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(5) {
            ShimmerEffect(
                modifier = Modifier
                    .width(200.dp)
                    .height(120.dp),
                cornerRadius = 12f
            )
        }
    }
}

/**
 * Skeleton for library/poster grid sections
 */
@Composable
fun LibrarySkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 6
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(
                compositingStrategy = CompositingStrategy.Offscreen
            )
    ) {
        items(itemCount) {
            PosterSkeleton()
        }
    }
}

/**
 * Skeleton for search results (list view with poster + text)
 */
@Composable
fun SearchResultsSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 8
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 100.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(itemCount) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Poster skeleton
                ShimmerEffect(
                    modifier = Modifier.size(70.dp),
                    cornerRadius = 12f
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Text content skeleton
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    ShimmerEffect(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(16.dp),
                        cornerRadius = 4f
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ShimmerEffect(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(12.dp),
                        cornerRadius = 4f
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ShimmerEffect(
                        modifier = Modifier
                            .width(60.dp)
                            .height(12.dp),
                        cornerRadius = 4f
                    )
                }
            }
        }
    }
}

/**
 * Skeleton for grid view (search results, view all screens)
 */
@Composable
fun GridSkeleton(
    modifier: Modifier = Modifier,
    columns: Int = 2,
    itemCount: Int = 6,
    aspectRatio: Float = 0.65f
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        items(itemCount) {
            ShimmerEffect(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio),
                cornerRadius = 16f
            )
        }
    }
}

/**
 * Skeleton for section titles
 */
@Composable
fun SectionTitleSkeleton(
    modifier: Modifier = Modifier,
    width: Dp = 150.dp
) {
    ShimmerEffect(
        modifier = modifier
            .width(width)
            .height(24.dp),
        cornerRadius = 4f
    )
}

/**
 * Skeleton for genre sections with title + horizontal list
 */
@Composable
fun GenreSectionSkeleton(
    modifier: Modifier = Modifier,
    sectionCount: Int = 3
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        repeat(sectionCount) {
            Column {
                // Genre title skeleton
                SectionTitleSkeleton(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Genre items skeleton
                LibrarySkeleton()

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}