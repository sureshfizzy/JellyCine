package com.jellycine.app.ui.screens.dashboard
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil3.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import com.jellycine.preferences.Preferences
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jellycine.app.ui.screens.auth.ServerSwitchViewModel
import com.jellycine.app.ui.screens.auth.UserSwitchDialog
import com.jellycine.app.ui.screens.dashboard.home.Dashboard
import com.jellycine.app.ui.screens.dashboard.settings.Settings
import com.jellycine.app.ui.screens.dashboard.media.MyMedia
import com.jellycine.app.ui.screens.dashboard.media.Discover
import com.jellycine.app.ui.screens.dashboard.media.ClipsFeedScreen
import com.jellycine.app.ui.screens.dashboard.search.SearchContainer
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.*
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellycine.ui.screens.dashboard.DashboardDestination
import com.jellycine.ui.screens.dashboard.DashboardShell
import com.jellycine.data.network.NetworkModule
import com.jellycine.data.network.NetworkStatus
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.data.repository.SeerrRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.jellycine.ui.screens.dashboard.home.AccountOverview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContainer(
    onLogout: () -> Unit = {},
    onNavigateToDetail: (com.jellycine.data.model.BaseItemDto) -> Unit = {},
    onNavigateToMergedDetail: (com.jellycine.data.model.BaseItemDto) -> Unit = onNavigateToDetail,
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> },
    onNavigateToPlayer: (String) -> Unit = {},
    onNavigateToPlayerSettings: () -> Unit = {},
    onNavigateToInterfaceSettings: () -> Unit = {},
    onNavigateToClipsSettings: () -> Unit = {},
    onNavigateToConnections: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToCacheSettings: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToServerInfo: () -> Unit = {},
    onNavigateToScreenTime: () -> Unit = {},
    onAddServer: () -> Unit = {},
    onAddUser: (serverUrl: String, serverName: String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val preferences = remember(context) { Preferences(context) }
    val appContext = remember(context) { context.applicationContext }
    val authRepository = remember(appContext) { AuthRepositoryProvider.getInstance(appContext) }
    val seerrRepository = remember(appContext) { SeerrRepository(appContext) }
    val networkStatusFlow = remember(appContext, preferences) {
        combine(
            NetworkModule.observeNetworkAvailability(appContext),
            preferences.ForceOfflineEnabled()
        ) { status, forceOffline ->
            if (forceOffline) NetworkStatus.Offline else status
        }
    }
    val networkStatus by networkStatusFlow.collectAsStateWithLifecycle(
        initialValue = when {
            preferences.isForceOfflineEnabled() -> NetworkStatus.Offline
            NetworkModule.isInternetAvailable(appContext) -> NetworkStatus.Online
            NetworkModule.hasLocalNetworkTransport(appContext) -> NetworkStatus.LocalNetwork
            else -> NetworkStatus.Offline
        }
    )
    val isNetworkAvailable = networkStatus.isAvailable
    val isOnline = networkStatus == NetworkStatus.Online
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
                    onAddUser(serverUrl, accountActiveServer?.serverName)
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

    DashboardShell(
        isNetworkAvailable = isNetworkAvailable,
        isOnline = isOnline,
        overlay = {
            BackHandler(enabled = currentRoute != DashboardDestination.Home.route) {
                navigateTo(DashboardDestination.Home)
            }
        }
    ) { route ->
        when (route) {
            DashboardDestination.Home.route -> {
                ContentWrapper {
                    Dashboard(
                        onLogout = onLogout,
                        onNavigateToDetail = onNavigateToDetail,
                        onNavigateToViewAll = onNavigateToViewAll,
                        onNavigateToPlayer = onNavigateToPlayer,
                        onAddServer = onAddServer,
                        onAddUser = onAddUser,
                        isTabActive = currentRoute == DashboardDestination.Home.route,
                        dashboardScrollState = homeScrollState
                    )
                }
            }
            DashboardDestination.MyMedia.route -> {
                ContentWrapper {
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
            DashboardDestination.Search.route -> {
                ContentWrapper {
                    SearchContainer(
                        onNavigateToDetail = onNavigateToMergedDetail,
                        onCancel = {
                            navigateTo(DashboardDestination.Home)
                        }
                    )
                }
            }
            DashboardDestination.Favorites.route -> {
                ContentWrapper {
                    if (isOnline) {
                        ClipsFeedScreen(modifier = Modifier.fillMaxSize())
                    } else {
                        MyMedia(
                            onLibraryClick = { contentType, parentId, title ->
                                onNavigateToViewAll(contentType.name, parentId, title)
                            }
                        )
                    }
                }
            }
            DashboardDestination.Settings.route -> {
                ContentWrapper {
                    Settings(
                        onLogout = onLogout,
                        onNavigateToPlayerSettings = onNavigateToPlayerSettings,
                        onNavigateToInterfaceSettings = onNavigateToInterfaceSettings,
                        onNavigateToClipsSettings = onNavigateToClipsSettings,
                        onNavigateToConnections = onNavigateToConnections,
                        onNavigateToDownloads = onNavigateToDownloads,
                        onNavigateToCacheSettings = onNavigateToCacheSettings,
                        onNavigateToAbout = onNavigateToAbout,
                        onNavigateToServerInfo = onNavigateToServerInfo,
                        onNavigateToScreenTime = onNavigateToScreenTime,
                        onNavigateToRequestedItem = onNavigateToDetail,
                        onAddServer = onAddServer,
                        onAddUser = onAddUser
                    )
                }
            }
            else -> Unit
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
            .background(Color.Black)
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
