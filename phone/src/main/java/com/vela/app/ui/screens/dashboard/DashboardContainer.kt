package com.vela.app.ui.screens.dashboard
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
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
import com.vela.shared.preferences.Preferences
import com.vela.shared.ui.components.common.ShimmerEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vela.app.ui.screens.auth.ServerSwitchViewModel
import com.vela.app.ui.screens.auth.UserSwitchDialog
import com.vela.app.ui.screens.dashboard.home.AccountOverview
import com.vela.app.ui.screens.dashboard.home.Dashboard
import com.vela.app.ui.screens.dashboard.home.ManageLibrariesSheet
import com.vela.app.ui.screens.dashboard.settings.Settings
import com.vela.app.ui.screens.dashboard.media.ContentType
import com.vela.app.ui.screens.dashboard.media.MyMedia
import com.vela.app.ui.screens.dashboard.media.Discover
import com.vela.app.ui.screens.dashboard.favorites.Favorites
import com.vela.app.ui.screens.dashboard.search.SearchContainer
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.vela.shared.R
import com.vela.data.network.NetworkModule
import com.vela.data.repository.AuthRepositoryProvider
import com.vela.data.repository.MediaRepositoryProvider
import com.vela.data.repository.SeerrRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.vela.shared.ui.theme.velaMotion

private fun dashboardEnterTransition(
    effectsSpec: FiniteAnimationSpec<Float>
): EnterTransition {
    return fadeIn(animationSpec = effectsSpec)
}

private fun dashboardExitTransition(
    effectsSpec: FiniteAnimationSpec<Float>
): ExitTransition {
    return fadeOut(animationSpec = effectsSpec)
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
        R.string.dashboard_discover,
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
    onNavigateToDetail: (com.vela.data.model.BaseItemDto) -> Unit = {},
    onNavigateToMergedDetail: (com.vela.data.model.BaseItemDto) -> Unit = onNavigateToDetail,
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> },
    onNavigateToSearchCategory: (String, String, String) -> Unit = { _, _, _ -> },
    onNavigateToPlayer: (String) -> Unit = {},
    onNavigateToPlayerSettings: () -> Unit = {},
    onNavigateToInterfaceSettings: () -> Unit = {},
    onNavigateToConnections: () -> Unit = {},
    onNavigateToServers: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToCacheSettings: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToServerInfo: () -> Unit = {},
    onAddServer: () -> Unit = {},
    onAddUser: (serverUrl: String, serverName: String?) -> Unit = { _, _ -> }
) {
    val navController = rememberNavController()
    val motion = MaterialTheme.velaMotion
    val tabEnterEffectsSpec = motion.defaultEffectsSpec<Float>()
    val tabExitEffectsSpec = motion.fastEffectsSpec<Float>()
    val homeScrollState = rememberLazyListState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    val preferences = remember(context) { Preferences(context) }
    val appContext = remember(context) { context.applicationContext }
    val authRepository = remember(appContext) { AuthRepositoryProvider.getInstance(appContext) }
    val seerrRepository = remember(appContext) { SeerrRepository(appContext) }
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
    val navigationBarInsetPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val bottomBarHideDistancePx = with(density) { (bottomBarHeight + 36.dp).toPx() } + navigationBarInsetPx
    val hideThresholdPx = with(density) { 22.dp.toPx() }
    val showThresholdPx = with(density) { 14.dp.toPx() }
    var isBottomBarVisible by remember { mutableStateOf(true) }
    var accumulatedScrollPx by remember { mutableFloatStateOf(0f) }
    var homeScrollToTop by remember { mutableStateOf(0) }

    val bottomBarScrollConnection = remember(hideThresholdPx, showThresholdPx, isNetworkAvailable) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isNetworkAvailable) return Offset.Zero
                if (source != NestedScrollSource.UserInput) return Offset.Zero

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
            if (targetState) motion.defaultSpatialSpec() else motion.fastSpatialSpec()
        },
        label = "bottom_bar_translation"
    ) { visible ->
        if (visible) 0f else bottomBarHideDistancePx
    }
    val bottomBarAlpha by bottomBarTransition.animateFloat(
        transitionSpec = {
            if (targetState) motion.defaultEffectsSpec() else motion.fastEffectsSpec()
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
    val offlineAllowedRoutes = remember {
        setOf(
            DashboardDestination.Home.route,
            DashboardDestination.Settings.route
        )
    }
    val navigateToDestination: (DashboardDestination) -> Unit = { destination ->
        if (
            destination == DashboardDestination.Home &&
            currentRoute == DashboardDestination.Home.route
        ) {
            homeScrollToTop += 1
        }
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

    LaunchedEffect(currentRoute, homeScrollToTop) {
        if (
            currentRoute != DashboardDestination.Home.route ||
            homeScrollToTop == 0
        ) {
            return@LaunchedEffect
        }

        if (
            homeScrollState.firstVisibleItemIndex != 0 ||
            homeScrollState.firstVisibleItemScrollOffset != 0
        ) {
            homeScrollState.animateScrollToItem(0)
        }
        homeScrollToTop = 0
    }

    LaunchedEffect(isNetworkAvailable, currentRoute) {
        if (!isNetworkAvailable && currentRoute != null && !offlineAllowedRoutes.contains(currentRoute)) {
            navigateToDestination(DashboardDestination.Home)
        }
    }

    LaunchedEffect(authRepository, seerrRepository, isNetworkAvailable) {
        if (!isNetworkAvailable) return@LaunchedEffect

        authRepository.observeActiveSession()
            .map { snapshot -> snapshot.activeServerId }
            .distinctUntilChanged()
            .collect { scopeId ->
                if (!scopeId.isNullOrBlank() && seerrRepository.getSavedConnectionInfo(scopeId) != null) {
                    seerrRepository.refreshConnection(scopeId)
                }
            }
    }

    var showAccountSheet by remember { mutableStateOf(false) }
    var showManageLibraries by remember { mutableStateOf(false) }
    var showUserSwitchDialog by remember { mutableStateOf(false) }
    val accountSessionSnapshot by authRepository.observeActiveSession()
        .collectAsStateWithLifecycle(initialValue = authRepository.getActiveSessionSnapshot())
    val accountActiveServer = remember(accountSessionSnapshot.savedServers, accountSessionSnapshot.activeServerId) {
        accountSessionSnapshot.savedServers.firstOrNull { it.id == accountSessionSnapshot.activeServerId }
    }
    val accountUsersForServer = remember(accountSessionSnapshot.savedServers, accountActiveServer) {
        val serverUrl = accountActiveServer?.serverUrl
        if (serverUrl != null) {
            accountSessionSnapshot.savedServers.filter { it.serverUrl == serverUrl }
        } else emptyList()
    }
    val mediaRepository = remember(appContext) { MediaRepositoryProvider.getInstance(appContext) }
    var accountProfileImageUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val url = withContext(Dispatchers.IO) {
            mediaRepository.getUserProfileImageUrl()
        }
        if (!url.isNullOrBlank()) accountProfileImageUrl = url
    }
    val scope = rememberCoroutineScope()
    val serverSwitchViewModel: ServerSwitchViewModel = viewModel {
        ServerSwitchViewModel(appContext as android.app.Application)
    }
    val serverSwitchUiState by serverSwitchViewModel.uiState.collectAsStateWithLifecycle()

    if (showAccountSheet) {
        AccountOverview(
            userName = accountSessionSnapshot.username,
            serverName = accountActiveServer?.serverName,
            profileImageUrl = accountProfileImageUrl,
            serverTypeRaw = accountSessionSnapshot.serverType,
            canChangeUser = accountUsersForServer.isNotEmpty(),
            onDismiss = { showAccountSheet = false },
            onManageLibraries = {
                showAccountSheet = false
                showManageLibraries = true
            },
            onChangeUser = {
                showAccountSheet = false
                showUserSwitchDialog = true
            },
            onLogout = {
                showAccountSheet = false
                scope.launch {
                    authRepository.logout()
                    onLogout()
                }
            }
        )
    }

    if (showManageLibraries) {
        ManageLibrariesSheet(
            onDismiss = { showManageLibraries = false },
            onLibrariesChanged = {
                scope.launch {
                    mediaRepository.clearPersistedHomeSnapshot()
                }
            }
        )
    }

    if (showUserSwitchDialog) {
        UserSwitchDialog(
            users = accountUsersForServer,
            activeServerId = accountSessionSnapshot.activeServerId,
            serverName = accountActiveServer?.serverName,
            isSwitching = serverSwitchUiState.isBusy,
            showRemoveAction = false,
            onDismiss = { showUserSwitchDialog = false },
            onAddUser = {
                showUserSwitchDialog = false
                val serverUrl = accountActiveServer?.serverUrl
                if (serverUrl != null) {
                    onAddUser(serverUrl, accountActiveServer.serverName)
                }
            },
            onRequestRemoveUser = {},
            onUserSelected = { server ->
                serverSwitchViewModel.switchServer(
                    serverId = server.id,
                    activeServerId = accountSessionSnapshot.activeServerId,
                    onSwitchComplete = { showUserSwitchDialog = false }
                )
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    enterTransition = { dashboardEnterTransition(tabEnterEffectsSpec) },
                    exitTransition = { dashboardExitTransition(tabExitEffectsSpec) }
                ) {
                    // Track when Home tab becomes active
                    val isHomeActive = currentRoute == DashboardDestination.Home.route

                    ContentWrapper {
                        Dashboard(
                            onLogout = onLogout,
                            onNavigateToDetail = onNavigateToDetail,
                            onNavigateToViewAll = onNavigateToViewAll,
                            onNavigateToPlayer = onNavigateToPlayer,
                            onAddServer = onAddServer,
                            onNavigateToServers = onNavigateToServers,
                            onAddUser = onAddUser,
                            isTabActive = isHomeActive,
                            dashboardScrollState = homeScrollState
                        )
                    }
                }
                composable(
                    DashboardDestination.MyMedia.route,
                    enterTransition = { dashboardEnterTransition(tabEnterEffectsSpec) },
                    exitTransition = { dashboardExitTransition(tabExitEffectsSpec) }
                ) {
                    ContentWrapper {
                        if (useMyMediaTabEnabled) {
                            MyMedia(
                                onLibraryClick = { contentType, parentId, title ->
                                    onNavigateToViewAll(contentType.name, parentId, title)
                                }
                            )
                        } else {
                            Discover(
                                onItemClick = onNavigateToDetail,
                                onWatchedItemClick = onNavigateToMergedDetail,
                                onNavigateToViewAll = { contentType, parentId, title ->
                                    onNavigateToViewAll(contentType.name, parentId, title)
                                },
                                onProfileClick = { showAccountSheet = true }
                            )
                        }
                    }
                }
                composable(
                    DashboardDestination.Search.route,
                    enterTransition = { dashboardEnterTransition(tabEnterEffectsSpec) },
                    exitTransition = { dashboardExitTransition(tabExitEffectsSpec) }
                ) {
                    ContentWrapper {
                        SearchContainer(
                            onNavigateToDetail = onNavigateToDetail,
                            onNavigateToSearchCategory = { mediaType, query, title ->
                                val contentType = when (mediaType) {
                                    com.vela.data.model.SearchMediaType.MOVIE -> "MOVIES"
                                    com.vela.data.model.SearchMediaType.SERIES -> "SERIES"
                                    com.vela.data.model.SearchMediaType.EPISODE -> "EPISODES"
                                }
                                onNavigateToSearchCategory(contentType, query, title)
                            },
                            onCancel = {
                                navigateToDestination(DashboardDestination.Home)
                            }
                        )
                    }
                }
                composable(
                    DashboardDestination.Favorites.route,
                    enterTransition = { dashboardEnterTransition(tabEnterEffectsSpec) },
                    exitTransition = { dashboardExitTransition(tabExitEffectsSpec) }
                ) {
                    ContentWrapper {
                        Favorites(
                            onItemClick = onNavigateToDetail,
                            onNavigateToViewAll = { contentType, parentId, title ->
                                onNavigateToViewAll(contentType.name, parentId, title)
                            },
                            onProfileClick = { showAccountSheet = true }
                        )
                    }
                }
                composable(
                    DashboardDestination.Settings.route,
                    enterTransition = { dashboardEnterTransition(tabEnterEffectsSpec) },
                    exitTransition = { dashboardExitTransition(tabExitEffectsSpec) }
                ) {
                    ContentWrapper {
                        Settings(
                            onLogout = onLogout,
                            onNavigateToPlayerSettings = onNavigateToPlayerSettings,
                            onNavigateToInterfaceSettings = onNavigateToInterfaceSettings,
                            onNavigateToConnections = onNavigateToConnections,
                            onNavigateToServers = onNavigateToServers,
                            onNavigateToDownloads = onNavigateToDownloads,
                            onNavigateToCacheSettings = onNavigateToCacheSettings,
                            onNavigateToAbout = onNavigateToAbout,
                            onNavigateToServerInfo = onNavigateToServerInfo,
                            onNavigateToRequestedItem = onNavigateToDetail,
                            onAddServer = onAddServer,
                            onAddUser = onAddUser
                        )
                    }
                }
            }

            val navigationDestinations = if (isNetworkAvailable) {
                listOf(
                    DashboardDestination.Home,
                    DashboardDestination.MyMedia,
                    DashboardDestination.Search,
                    DashboardDestination.Favorites,
                    DashboardDestination.Settings
                )
            } else {
                sideDestinations
            }
            NavigationBar(
                // 标准 M3 导航栏统一选中态、语义和系统栏 inset；不再维护自绘凹槽与独立触摸逻辑。
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(if (shouldUseMobileBarWidth) Modifier.width(mobileLikeBarWidth) else Modifier.fillMaxWidth())
                    .graphicsLayer {
                        translationY = bottomBarTranslationPx
                        alpha = bottomBarAlpha
                    },
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                navigationDestinations.forEach { destination ->
                    val isSelected = currentRoute == destination.route
                    val title = if (destination == DashboardDestination.MyMedia) {
                        stringResource(
                            if (useMyMediaTabEnabled) R.string.my_media else R.string.dashboard_discover
                        )
                    } else {
                        stringResource(destination.titleRes)
                    }
                    val selectedIcon = if (
                        destination == DashboardDestination.MyMedia && !useMyMediaTabEnabled
                    ) Icons.Filled.Explore else destination.selectedIcon
                    val unselectedIcon = if (
                        destination == DashboardDestination.MyMedia && !useMyMediaTabEnabled
                    ) Icons.Outlined.Explore else destination.unselectedIcon

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { navigateToDestination(destination) },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) selectedIcon else unselectedIcon,
                                contentDescription = title
                            )
                        },
                        label = { Text(title, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentWrapper(
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
    mediaRepository: com.vela.data.repository.MediaRepository,
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
                .aspectRatio(0.67f),
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
