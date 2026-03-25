package com.jellycine.app.ui.screens.dashboard
import android.content.res.Configuration
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
import coil3.compose.AsyncImage
import coil3.request.*
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.sqrt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jellycine.app.preferences.Preferences
import com.jellycine.app.ui.components.common.ShimmerEffect
import com.jellycine.app.ui.screens.dashboard.home.Dashboard
import com.jellycine.app.ui.screens.dashboard.settings.Settings
import com.jellycine.app.ui.screens.dashboard.media.MyMedia
import com.jellycine.app.ui.screens.dashboard.media.ForYou
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import com.jellycine.app.R
import com.jellycine.data.network.NetworkModule

private fun DashboardEnterTransition(): EnterTransition {
    return fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing))
}

private fun DashboardExitTransition(): ExitTransition {
    return fadeOut(animationSpec = tween(300, easing = LinearOutSlowInEasing))
}

sealed class DashboardDestination(
    val route: String,
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : DashboardDestination(
        "dashboard_home",
        R.string.home,
        Icons.Filled.Home,
        Icons.Outlined.Home
    )
    object MyMedia : DashboardDestination(
        "my_media",
        R.string.dashboard_for_you,
        Icons.Filled.PlayArrow,
        Icons.Outlined.PlayArrow
    )
    object Search : DashboardDestination(
        "search",
        R.string.search,
        Icons.Filled.Search,
        Icons.Outlined.Search
    )
    object Favorites : DashboardDestination(
        "favorites",
        R.string.favorites,
        Icons.Filled.Favorite,
        Icons.Outlined.FavoriteBorder
    )
    object Settings : DashboardDestination(
        "settings",
        R.string.settings,
        Icons.Filled.Settings,
        Icons.Outlined.Settings
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContainer(
    onLogout: () -> Unit = {},
    onNavigateToDetail: (com.jellycine.data.model.BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> },
    onNavigateToPlayer: (String) -> Unit = {},
    onNavigateToPlayerSettings: () -> Unit = {},
    onNavigateToInterfaceSettings: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToCacheSettings: () -> Unit = {},
    onAddServer: () -> Unit = {},
    onAddUser: (serverUrl: String, serverName: String?) -> Unit = { _, _ -> }
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    val preferences = remember(context) { Preferences(context) }
    val appContext = remember(context) { context.applicationContext }
    val networkAvailabilityFlow = remember(appContext) {
        NetworkModule.observeNetworkAvailability(appContext)
    }
    val isNetworkAvailable by networkAvailabilityFlow.collectAsStateWithLifecycle(
        initialValue = NetworkModule.isInternetAvailable(appContext)
    )
    val useMyMediaTabEnabled by preferences.UseMyMediaTabEnabled()
        .collectAsStateWithLifecycle(
            initialValue = preferences.isUseMyMediaTabEnabled()
        )
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val bottomBarHeight = 68.dp
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.screenWidthDp >= 600
    val shouldUseMobileBarWidth = isLandscape || isTablet
    val mobileLikeBarWidth = (min(configuration.screenWidthDp, configuration.screenHeightDp) - 32)
        .dp
        .coerceIn(320.dp, 390.dp)
    val barOuterHorizontalPadding = if (shouldUseMobileBarWidth) 0.dp else 16.dp
    val barInnerHorizontalPadding = if (shouldUseMobileBarWidth) 10.dp else 16.dp
    val navGroupSpacing = if (shouldUseMobileBarWidth) 12.dp else 20.dp
    val innerItemOffset = if (shouldUseMobileBarWidth) 16.dp else 22.dp
    val outerItemOffset = if (shouldUseMobileBarWidth) 0.dp else 10.dp
    val navigationBarInsetPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val bottomBarHideDistancePx = with(density) { (bottomBarHeight + 36.dp).toPx() } + navigationBarInsetPx
    val hideThresholdPx = with(density) { 22.dp.toPx() }
    val showThresholdPx = with(density) { 14.dp.toPx() }
    var isBottomBarVisible by remember { mutableStateOf(true) }
    var accumulatedScrollPx by remember { mutableFloatStateOf(0f) }

    val bottomBarScrollConnection = remember(hideThresholdPx, showThresholdPx, isNetworkAvailable) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isNetworkAvailable) return Offset.Zero
                if (source != NestedScrollSource.Drag) return Offset.Zero

                val deltaY = available.y
                if (deltaY == 0f) return Offset.Zero

                if (abs(available.y) < abs(available.x)) return Offset.Zero

                if (deltaY < 0f) {
                    accumulatedScrollPx = min(0f, accumulatedScrollPx + deltaY)
                    if (isBottomBarVisible && -accumulatedScrollPx >= hideThresholdPx) {
                        isBottomBarVisible = false
                        accumulatedScrollPx = 0f
                    }
                } else {
                    accumulatedScrollPx = max(0f, accumulatedScrollPx + deltaY)
                    if (!isBottomBarVisible && accumulatedScrollPx >= showThresholdPx) {
                        isBottomBarVisible = true
                        accumulatedScrollPx = 0f
                    }
                }

                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!isNetworkAvailable) return Velocity.Zero
                if (available.y < -500f) {
                    isBottomBarVisible = false
                } else if (available.y > 500f) {
                    isBottomBarVisible = true
                }
                accumulatedScrollPx = 0f
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(currentRoute) {
        isBottomBarVisible = true
        accumulatedScrollPx = 0f
    }
    LaunchedEffect(isNetworkAvailable) {
        if (!isNetworkAvailable) {
            isBottomBarVisible = true
            accumulatedScrollPx = 0f
        }
    }

    val bottomBarTransition = updateTransition(
        targetState = isBottomBarVisible,
        label = "bottom_bar_visibility"
    )
    val bottomBarTranslationPx by bottomBarTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) 440 else 320,
                easing = FastOutSlowInEasing
            )
        },
        label = "bottom_bar_translation"
    ) { visible ->
        if (visible) 0f else bottomBarHideDistancePx
    }
    val bottomBarAlpha by bottomBarTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) 420 else 260,
                easing = LinearOutSlowInEasing
            )
        },
        label = "bottom_bar_alpha"
    ) { visible ->
        if (visible) 1f else 0f
    }



    val sideDestinations = if (isNetworkAvailable) {
        listOf(
            DashboardDestination.Home,
            DashboardDestination.MyMedia,
            DashboardDestination.Favorites,
            DashboardDestination.Settings
        )
    } else {
        listOf(
            DashboardDestination.Home,
            DashboardDestination.Settings
        )
    }
    val isOfflineTwoTabMode = !isNetworkAvailable && sideDestinations.size == 2
    val offlineItemSlotWidth = 72.dp
    val offlineItemSpacing = 18.dp
    val offlineRowHorizontalPadding = 12.dp
    val offlineBarWidth = (offlineRowHorizontalPadding * 2) +
        (offlineItemSlotWidth * sideDestinations.size) +
        (offlineItemSpacing * (sideDestinations.size - 1).coerceAtLeast(0))
    val offlineAllowedRoutes = remember {
        setOf(
            DashboardDestination.Home.route,
            DashboardDestination.Settings.route
        )
    }
    val navigateToDestination: (DashboardDestination) -> Unit = { destination ->
        if (currentRoute != destination.route) {
            navController.navigate(destination.route) {
                currentRoute?.let { route ->
                    popUpTo(route) {
                        inclusive = true
                    }
                }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(isNetworkAvailable, currentRoute) {
        if (!isNetworkAvailable && currentRoute != null && !offlineAllowedRoutes.contains(currentRoute)) {
            navigateToDestination(DashboardDestination.Home)
        }
    }

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
                    .then(
                        if (isNetworkAvailable) {
                            Modifier.nestedScroll(bottomBarScrollConnection)
                        } else {
                            Modifier
                        }
                    )
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
                            onNavigateToPlayer = onNavigateToPlayer,
                            onAddServer = onAddServer,
                            onAddUser = onAddUser,
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
                        if (useMyMediaTabEnabled) {
                            MyMedia(
                                onLibraryClick = { contentType, parentId, title ->
                                    onNavigateToViewAll(contentType.name, parentId, title)
                                }
                            )
                        } else {
                            ForYou(onItemClick = onNavigateToDetail)
                        }
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
                                navigateToDestination(DashboardDestination.Home)
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
                        Favorites(
                            onItemClick = onNavigateToDetail
                        )
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
                        Settings(
                            onLogout = onLogout,
                            onNavigateToPlayerSettings = onNavigateToPlayerSettings,
                            onNavigateToInterfaceSettings = onNavigateToInterfaceSettings,
                            onNavigateToDownloads = onNavigateToDownloads,
                            onNavigateToCacheSettings = onNavigateToCacheSettings,
                            onAddServer = onAddServer,
                            onAddUser = onAddUser
                        )
                    }
                }
            }

            // Curved Bottom Navigation
            Box(
                modifier = Modifier
                    .then(
                        if (isOfflineTwoTabMode) {
                            Modifier.width(offlineBarWidth)
                        } else if (shouldUseMobileBarWidth) {
                            Modifier.width(mobileLikeBarWidth)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    )
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(
                        horizontal = if (isOfflineTwoTabMode) 0.dp else barOuterHorizontalPadding,
                        vertical = 10.dp
                    )
                    .graphicsLayer {
                        translationY = bottomBarTranslationPx
                        alpha = bottomBarAlpha
                        clip = false
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bottomBarHeight)
                        .shadow(
                            elevation = 18.dp,
                            shape = RoundedCornerShape(30.dp),
                            clip = false
                        )
                        .graphicsLayer(
                            rotationX = if (isOfflineTwoTabMode) 0f else -3f,
                            transformOrigin = TransformOrigin(0.5f, 1f),
                            scaleY = 1f,
                            cameraDistance = 8f * density.density
                        )
                        .drawBehind {
                            if (isOfflineTwoTabMode) {
                                drawSimpleNavigationBar(
                                    width = size.width,
                                    height = size.height
                                )
                            } else {
                                draw3DCurvedNavigationBar(
                                    width = size.width,
                                    height = size.height
                                )
                            }
                        }
                )

                // Navigation items container
                if (isNetworkAvailable) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(bottomBarHeight)
                            .padding(horizontal = barInnerHorizontalPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left side items
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(navGroupSpacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            sideDestinations.take(2).forEach { destination ->
                                val isSelected = currentRoute == destination.route
                                NavigationItem(
                                    modifier = when (destination.route) {
                                        DashboardDestination.MyMedia.route -> Modifier.offset(x = -innerItemOffset)
                                        else -> Modifier.offset(x = -outerItemOffset)
                                    },
                                    destination = destination,
                                    title = if (destination == DashboardDestination.MyMedia) {
                                        if (useMyMediaTabEnabled) stringResource(R.string.my_media) else stringResource(R.string.dashboard_for_you)
                                    } else {
                                        stringResource(destination.titleRes)
                                    },
                                    selectedIcon = if (destination == DashboardDestination.MyMedia && !useMyMediaTabEnabled) Icons.Filled.AutoAwesome else destination.selectedIcon,
                                    unselectedIcon = if (destination == DashboardDestination.MyMedia && !useMyMediaTabEnabled) Icons.Outlined.AutoAwesome else destination.unselectedIcon,
                                    isSelected = isSelected,
                                    onClick = { navigateToDestination(destination) }
                                )
                            }
                        }

                        // Right side items
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(navGroupSpacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            sideDestinations.drop(2).forEach { destination ->
                                val isSelected = currentRoute == destination.route
                                NavigationItem(
                                    modifier = when (destination.route) {
                                        DashboardDestination.Favorites.route -> Modifier.offset(x = innerItemOffset)
                                        else -> Modifier.offset(x = outerItemOffset)
                                    },
                                    destination = destination,
                                    title = if (destination == DashboardDestination.MyMedia) {
                                        if (useMyMediaTabEnabled) stringResource(R.string.my_media) else stringResource(R.string.dashboard_for_you)
                                    } else {
                                        stringResource(destination.titleRes)
                                    },
                                    selectedIcon = if (destination == DashboardDestination.MyMedia && !useMyMediaTabEnabled) Icons.Filled.AutoAwesome else destination.selectedIcon,
                                    unselectedIcon = if (destination == DashboardDestination.MyMedia && !useMyMediaTabEnabled) Icons.Outlined.AutoAwesome else destination.unselectedIcon,
                                    isSelected = isSelected,
                                    onClick = { navigateToDestination(destination) }
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
                            onClick = { navigateToDestination(DashboardDestination.Search) }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(bottomBarHeight)
                            .padding(horizontal = offlineRowHorizontalPadding),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        sideDestinations.forEachIndexed { index, destination ->
                            val isSelected = currentRoute == destination.route
                            NavigationItem(
                                modifier = Modifier.width(offlineItemSlotWidth),
                                destination = destination,
                                title = if (destination == DashboardDestination.MyMedia) {
                                    if (useMyMediaTabEnabled) stringResource(R.string.my_media) else stringResource(R.string.dashboard_for_you)
                                } else {
                                    stringResource(destination.titleRes)
                                },
                                selectedIcon = if (destination == DashboardDestination.MyMedia && !useMyMediaTabEnabled) Icons.Filled.AutoAwesome else destination.selectedIcon,
                                unselectedIcon = if (destination == DashboardDestination.MyMedia && !useMyMediaTabEnabled) Icons.Outlined.AutoAwesome else destination.unselectedIcon,
                                isSelected = isSelected,
                                itemWidth = 56.dp,
                                onClick = { navigateToDestination(destination) }
                            )
                            if (index < sideDestinations.lastIndex) {
                                Spacer(modifier = Modifier.width(offlineItemSpacing))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Function to draw curved navigation bar with dynamic effects
private fun DrawScope.draw3DCurvedNavigationBar(
    width: Float,
    height: Float
) {
    val centerWidth = width / 2f
    val topCornerRadius = 24.dp.toPx()
    val bottomCornerRadius = 26.dp.toPx()

    // Use dimensions that are proportional to the FAB size for a good fit
    val fabRadius = 28.dp.toPx()
    val curveDepth = fabRadius + (fabRadius / 3f)
    val targetCurveWidth = (fabRadius * 2) + (fabRadius * 1.5f)
    val sideInset = topCornerRadius + 6.dp.toPx()
    val targetHalfWidth = targetCurveWidth / 2f
    fun snapX(value: Float): Float = round(value * 2f) / 2f
    val notchCenterX = snapX(centerWidth)
    val availableHalfWidth = min(
        notchCenterX - sideInset,
        (width - sideInset) - notchCenterX
    ).coerceAtLeast(0f)
    val curveHalfWidth = min(targetHalfWidth, availableHalfWidth)
    var curveStartX = snapX(notchCenterX - curveHalfWidth)
    var curveEndX = snapX(notchCenterX + (notchCenterX - curveStartX))
    val maxRightX = width - sideInset
    if (curveEndX > maxRightX) {
        curveEndX = maxRightX
        curveStartX = snapX(notchCenterX - (curveEndX - notchCenterX))
    }
    val adjustedCurveWidth = curveEndX - curveStartX

    // Build left control points first, then reflect for perfect symmetry.
    val controlPoint1X = snapX(curveStartX + adjustedCurveWidth * 0.12f)
    val controlPoint2X = snapX(notchCenterX - adjustedCurveWidth * 0.35f)
    val controlPoint3X = snapX(notchCenterX + (notchCenterX - controlPoint2X))
    val controlPoint4X = snapX(notchCenterX + (notchCenterX - controlPoint1X))
    
    val backgroundPath = Path().apply {
        moveTo(0f, topCornerRadius)
        quadraticTo(0f, 0f, topCornerRadius, 0f)
        lineTo(curveStartX, 0f)

        cubicTo(
            x1 = controlPoint1X, y1 = 0f,
            x2 = controlPoint2X, y2 = curveDepth,
            x3 = notchCenterX, y3 = curveDepth
        )
        
        cubicTo(
            x1 = controlPoint3X, y1 = curveDepth,
            x2 = controlPoint4X, y2 = 0f,
            x3 = curveEndX, y3 = 0f
        )
        
        lineTo(width - topCornerRadius, 0f)
        quadraticTo(width, 0f, width, topCornerRadius)
        lineTo(width, height - bottomCornerRadius)
        quadraticTo(width, height, width - bottomCornerRadius, height)
        lineTo(bottomCornerRadius, height)
        quadraticTo(0f, height, 0f, height - bottomCornerRadius)
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

    val notchGlowPath = Path().apply {
        moveTo(curveStartX, 0f)
        cubicTo(
            x1 = controlPoint1X, y1 = 0f,
            x2 = controlPoint2X, y2 = curveDepth,
            x3 = notchCenterX, y3 = curveDepth
        )
        cubicTo(
            x1 = controlPoint3X, y1 = curveDepth,
            x2 = controlPoint4X, y2 = 0f,
            x3 = curveEndX, y3 = 0f
        )
    }

    val glowCenterColor = Color(0xFFBEE8FF)
    val glowBrush = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            glowCenterColor.copy(alpha = 0.14f),
            Color.Transparent
        ),
        startX = curveStartX,
        endX = curveEndX
    )
    val glowCoreBrush = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            glowCenterColor.copy(alpha = 0.28f),
            Color.Transparent
        ),
        startX = curveStartX,
        endX = curveEndX
    )

    drawPath(
        path = notchGlowPath,
        brush = glowBrush,
        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
    )
    drawPath(
        path = notchGlowPath,
        brush = glowCoreBrush,
        style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawSimpleNavigationBar(
    width: Float,
    height: Float
) {
    val corner = 26.dp.toPx()
    drawRoundRect(
        color = Color.Black,
        size = androidx.compose.ui.geometry.Size(width, height),
        cornerRadius = CornerRadius(corner, corner)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.05f),
        size = androidx.compose.ui.geometry.Size(width, height),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = 1.dp.toPx())
    )
}

@Composable
private fun FloatingSearchButton(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Filled.Search else Icons.Outlined.Search,
            contentDescription = stringResource(R.string.search),
            tint = Color.Black,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun NavigationItem(
    modifier: Modifier = Modifier,
    destination: DashboardDestination,
    title: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    isSelected: Boolean,
    itemWidth: Dp = 44.dp,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val iconTint by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.76f),
        animationSpec = tween(durationMillis = 220),
        label = "nav_icon_tint"
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.55f,
        animationSpec = tween(durationMillis = 220),
        label = "nav_label_alpha"
    )
    val textTint by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.76f),
        animationSpec = tween(durationMillis = 220),
        label = "nav_text_tint"
    )

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .height(64.dp)
            .padding(horizontal = 8.dp)
            .width(itemWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) selectedIcon else unselectedIcon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Text(
            text = title,
            color = textTint,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.alpha(labelAlpha)
        )
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

// Poster Component
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
    height: Dp = 260.dp,
    cornerRadius: Float = 16f
) {
    Column(
        modifier = modifier
            .width(width)
            .height(height)
    ) {
        // Image skeleton
        ShimmerEffect(
            modifier = Modifier
                .width(width)
                .height(210.dp),
            cornerRadius = cornerRadius
        )

        // Title and metadata skeleton
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(top = 8.dp, start = 4.dp, end = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Title skeleton
            ShimmerEffect(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(16.dp),
                cornerRadius = 4f
            )
            // Year/type skeleton
            ShimmerEffect(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(12.dp),
                cornerRadius = 4f
            )
        }
    }
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
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .height(180.dp)
            ) {
                // Image skeleton
                ShimmerEffect(
                    modifier = Modifier
                        .width(200.dp)
                        .height(120.dp),
                    cornerRadius = 12f
                )

                // Title and info skeleton
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(top = 8.dp, start = 4.dp, end = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Title skeleton
                    ShimmerEffect(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(16.dp),
                        cornerRadius = 4f
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Year/type skeleton
                    ShimmerEffect(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(12.dp),
                        cornerRadius = 4f
                    )
                }
            }
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

