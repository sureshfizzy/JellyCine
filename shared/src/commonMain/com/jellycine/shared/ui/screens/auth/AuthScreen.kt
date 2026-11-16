package com.jellycine.shared.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jellycine.data.network.DiscoveredServer
import com.jellycine.data.repository.AuthRepository
import com.jellycine.data.repository.SavedServer
import com.jellycine.shared.resources.*
import com.jellycine.shared.ui.theme.JellyBlue
import com.jellycine.shared.ui.theme.JellyRed
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

enum class AuthStep {
    SERVER_CONNECTION,
    LOGIN
}

class AuthHostScope internal constructor(
    val savedServers: List<SavedServer>,
    val activeServerId: String?,
    val selectedServerName: String?,
    val selectedServerUrl: String,
    val displaySavedServers: Boolean,
    val isServerStep: Boolean,
    val canNavigateBack: Boolean,
    val navigateBack: () -> Unit,
    val goToLogin: (serverUrl: String, serverName: String?) -> Unit,
    val prefillLogin: (server: SavedServer, error: Throwable) -> Unit,
    val connectTo: (serverUrl: String) -> Unit
)

@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    serverUrl: String? = null,
    serverName: String? = null,
    startAtLogin: Boolean = false,
    preferSavedServers: Boolean = false,
    serverDiscovery: (() -> kotlinx.coroutines.flow.Flow<DiscoveredServer>)? = null,
    onOpenLanguageSettings: () -> Unit = {},
    onAuthSuccess: () -> Unit,
    hostContent: @Composable AuthHostScope.() -> Unit = {}
) {
    val authViewModel: AuthScreenViewModel = viewModel {
        AuthScreenViewModel(authRepository, serverDiscovery)
    }
    val uiState by authViewModel.uiState.collectAsState()
    val discoveredServers by authViewModel.discoveredServers.collectAsState()
    val isDiscovering by authViewModel.isDiscovering.collectAsState()
    val sessionSnapshot by authRepository.observeActiveSession().collectAsState(
        initial = authRepository.getActiveSessionSnapshot()
    )

    val login = startAtLogin && !serverUrl.isNullOrBlank()
    val displaySavedServers = preferSavedServers && !login && sessionSnapshot.savedServers.isNotEmpty()
    var currentStep by remember(login) {
        mutableStateOf(if (login) AuthStep.LOGIN else AuthStep.SERVER_CONNECTION)
    }
    val showServerConnection = currentStep == AuthStep.SERVER_CONNECTION && displaySavedServers
    var selectedServerName by remember(serverName) { mutableStateOf(serverName) }
    var selectedServerUrl by remember(serverUrl) { mutableStateOf(serverUrl.orEmpty()) }
    val canNavigateBackToServerStep = currentStep == AuthStep.LOGIN && !login

    val goBackToServerStep = {
        authViewModel.cancelQuickConnect()
        authViewModel.clearLoginError()
        currentStep = AuthStep.SERVER_CONNECTION
    }

    DisposableEffect(Unit) {
        onDispose { authViewModel.cancelQuickConnect() }
    }

    LaunchedEffect(currentStep, selectedServerUrl) {
        if (currentStep == AuthStep.LOGIN) {
            authViewModel.refreshQuickConnectVisibility(selectedServerUrl)
        }
    }

    val hostScope = AuthHostScope(
        savedServers = sessionSnapshot.savedServers,
        activeServerId = sessionSnapshot.activeServerId,
        selectedServerName = selectedServerName,
        selectedServerUrl = selectedServerUrl,
        displaySavedServers = displaySavedServers,
        isServerStep = currentStep == AuthStep.SERVER_CONNECTION,
        canNavigateBack = canNavigateBackToServerStep && !uiState.isLoginLoading,
        navigateBack = goBackToServerStep,
        goToLogin = { url, name ->
            selectedServerUrl = url
            selectedServerName = name
            currentStep = AuthStep.LOGIN
        },
        prefillLogin = { server, error ->
            authViewModel.updateServerUrl(server.serverUrl)
            authViewModel.updateUsername(server.username)
            authViewModel.updatePassword("")
            authViewModel.setLoginError(error)
            selectedServerUrl = server.serverUrl
            selectedServerName = server.serverName
            currentStep = AuthStep.LOGIN
        },
        connectTo = { url ->
            authViewModel.updateServerUrl(url)
            authViewModel.connectToServer { connectedUrl, name ->
                selectedServerUrl = connectedUrl
                selectedServerName = name
                currentStep = AuthStep.LOGIN
            }
        }
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black,
                            Color.Black,
                            Color(0xFF030406),
                            Color.Black
                        )
                    )
                )
                .imePadding()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            hostScope.hostContent()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            ) {
                when (currentStep) {
                    AuthStep.SERVER_CONNECTION -> ServerConnectionContent(
                        modifier = Modifier.fillMaxSize(),
                        serverUrl = uiState.serverUrl,
                        isAwaitingSavedServers = showServerConnection,
                        isLoading = uiState.isServerLoading,
                        errorMessage = uiState.serverErrorMessage,
                        discoveredServers = discoveredServers,
                        isDiscovering = isDiscovering,
                        supportsDiscovery = authViewModel.supportsDiscovery,
                        onServerUrlChange = authViewModel::updateServerUrl,
                        onConnect = {
                            authViewModel.connectToServer { url, name ->
                                selectedServerUrl = url
                                selectedServerName = name
                                currentStep = AuthStep.LOGIN
                            }
                        },
                        onSelectDiscoveredServer = { server ->
                            authViewModel.updateServerUrl(server.address)
                            authViewModel.connectToServer { url, name ->
                                selectedServerUrl = url
                                selectedServerName = name
                                currentStep = AuthStep.LOGIN
                            }
                        },
                        onRescanServers = { authViewModel.discoverServers() }
                    )

                    AuthStep.LOGIN -> LoginContent(
                        modifier = Modifier.fillMaxSize(),
                        serverUrl = selectedServerUrl,
                        serverName = selectedServerName,
                        username = uiState.username,
                        password = uiState.password,
                        isLoading = uiState.isLoginLoading,
                        errorMessage = uiState.loginErrorMessage,
                        showQuickConnect = uiState.showQuickConnect,
                        isQuickConnectLoading = uiState.isQuickConnectLoading,
                        quickConnectCode = uiState.quickConnectCode,
                        onUsernameChange = authViewModel::updateUsername,
                        onPasswordChange = authViewModel::updatePassword,
                        onLogin = { authViewModel.login(selectedServerUrl, onAuthSuccess) },
                        onQuickConnect = {
                            authViewModel.loginWithQuickConnect(selectedServerUrl, onAuthSuccess)
                        }
                    )
                }
            }

            if (canNavigateBackToServerStep) {
                IconButton(
                    onClick = goBackToServerStep,
                    enabled = !uiState.isLoginLoading,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 4.dp, start = 4.dp)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(Res.string.cd_back_button),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            IconButton(
                onClick = onOpenLanguageSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Translate,
                    contentDescription = stringResource(Res.string.settings_language),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun AnimatedBrandHero(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val logoMotion = rememberInfiniteTransition(label = "logo_motion")
    val driftX by logoMotion.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_drift_x"
    )
    val driftY by logoMotion.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_drift_y"
    )
    val tilt by logoMotion.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_tilt"
    )
    val pulse by logoMotion.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_pulse"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(Res.drawable.jellycine_logo),
            contentDescription = stringResource(
                Res.string.feature_logo_content_description,
                stringResource(Res.string.app_name)
            ),
            modifier = Modifier
                .size(132.dp)
                .graphicsLayer {
                    translationX = driftX
                    translationY = driftY
                    rotationZ = tilt
                }
                .scale(pulse),
            contentScale = ContentScale.Fit
        )

        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.88f)
        )
    }
}

@Composable
private fun ServerConnectionContent(
    modifier: Modifier = Modifier,
    serverUrl: String,
    isAwaitingSavedServers: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    discoveredServers: List<DiscoveredServer> = emptyList(),
    isDiscovering: Boolean = false,
    supportsDiscovery: Boolean = false,
    onServerUrlChange: (String) -> Unit,
    onConnect: () -> Unit,
    onSelectDiscoveredServer: (DiscoveredServer) -> Unit = {},
    onRescanServers: () -> Unit = {}
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedBrandHero(
            title = stringResource(Res.string.auth_connect_title),
            subtitle = stringResource(Res.string.auth_connect_subtitle),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (isAwaitingSavedServers) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                }
            }
        } else {
            ConnectionForm(
                serverUrl = serverUrl,
                isLoading = isLoading,
                errorMessage = errorMessage,
                onServerUrlChange = onServerUrlChange,
                onConnect = onConnect,
                modifier = Modifier.fillMaxWidth()
            )

            if (supportsDiscovery) DiscoveredServersSection(
                servers = discoveredServers,
                isDiscovering = isDiscovering,
                isLoading = isLoading,
                onSelectServer = onSelectDiscoveredServer,
                onRescan = onRescanServers,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun DiscoveredServersSection(
    servers: List<DiscoveredServer>,
    isDiscovering: Boolean,
    isLoading: Boolean,
    onSelectServer: (DiscoveredServer) -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.auth_discovered_servers),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (isDiscovering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = JellyBlue,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(
                        onClick = onRescan,
                        enabled = !isLoading,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(Res.string.auth_discover_servers),
                            tint = JellyBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (isDiscovering && servers.isEmpty()) {
                Text(
                    text = stringResource(Res.string.auth_discovering),
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (!isDiscovering && servers.isEmpty()) {
                Text(
                    text = stringResource(Res.string.auth_no_servers_found),
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            servers.forEach { server ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isLoading) { onSelectServer(server) },
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.06f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Dns,
                            contentDescription = null,
                            tint = JellyBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = server.name,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = server.address,
                                color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginContent(
    modifier: Modifier = Modifier,
    serverUrl: String,
    serverName: String?,
    username: String,
    password: String,
    isLoading: Boolean,
    errorMessage: String?,
    showQuickConnect: Boolean,
    isQuickConnectLoading: Boolean,
    quickConnectCode: String?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onQuickConnect: () -> Unit
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedBrandHero(
            title = serverName ?: stringResource(Res.string.auth_welcome_back),
            subtitle = if (serverUrl.isNotBlank()) serverUrl else stringResource(Res.string.auth_sign_in_subtitle),
            modifier = Modifier.padding(bottom = 20.dp)
        )

        LoginForm(
            username = username,
            password = password,
            isLoading = isLoading,
            errorMessage = errorMessage,
            showQuickConnect = showQuickConnect,
            isQuickConnectLoading = isQuickConnectLoading,
            quickConnectCode = quickConnectCode,
            onUsernameChange = onUsernameChange,
            onPasswordChange = onPasswordChange,
            onLogin = onLogin,
            onQuickConnect = onQuickConnect
        )
    }
}

@Composable
private fun ConnectionForm(
    modifier: Modifier = Modifier,
    serverUrl: String,
    isLoading: Boolean,
    errorMessage: String?,
    onServerUrlChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    val serverUrlBringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(Res.string.auth_connection_settings),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = onServerUrlChange,
                label = { Text(stringResource(Res.string.server_url)) },
                placeholder = {
                    Text(
                        stringResource(Res.string.auth_server_url_placeholder),
                        color = Color.White.copy(alpha = 0.6f)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(serverUrlBringIntoView)
                    .onFocusEvent { state ->
                        if (state.isFocused) {
                            scope.launch { serverUrlBringIntoView.bringIntoView() }
                        }
                    },
                enabled = !isLoading,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { if (serverUrl.isNotBlank()) onConnect() }),
                shape = RoundedCornerShape(14.dp),
                colors = amoledAuthFieldColors()
            )

            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(animationSpec = tween(240)),
                exit = fadeOut(animationSpec = tween(180))
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = JellyRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = onConnect,
                enabled = !isLoading && serverUrl.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = JellyBlue,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF1E1E1E),
                    disabledContentColor = Color.White.copy(alpha = 0.4f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(Res.string.auth_connect_to_server))
                }
            }
        }
    }
}

@Composable
private fun LoginForm(
    username: String,
    password: String,
    isLoading: Boolean,
    errorMessage: String?,
    showQuickConnect: Boolean,
    isQuickConnectLoading: Boolean,
    quickConnectCode: String?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onQuickConnect: () -> Unit
) {
    val usernameBringIntoView = remember { BringIntoViewRequester() }
    val passwordBringIntoView = remember { BringIntoViewRequester() }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isBusy = isLoading || isQuickConnectLoading
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text(stringResource(Res.string.username)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(usernameBringIntoView)
                    .onFocusEvent { state ->
                        if (state.isFocused) {
                            scope.launch { usernameBringIntoView.bringIntoView() }
                        }
                    },
                enabled = !isBusy,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(14.dp),
                colors = amoledAuthFieldColors(hasLeadingIcon = true)
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(Res.string.password)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null
                    )
                },
                visualTransformation = if (isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) {
                                Icons.Rounded.VisibilityOff
                            } else {
                                Icons.Rounded.Visibility
                            },
                            contentDescription = if (isPasswordVisible) {
                                stringResource(Res.string.auth_hide_password)
                            } else {
                                stringResource(Res.string.auth_show_password)
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(passwordBringIntoView)
                    .onFocusEvent { state ->
                        if (state.isFocused) {
                            scope.launch { passwordBringIntoView.bringIntoView() }
                        }
                    },
                enabled = !isBusy,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { if (username.isNotBlank() && !isBusy) onLogin() }),
                shape = RoundedCornerShape(14.dp),
                colors = amoledAuthFieldColors(hasLeadingIcon = true)
            )

            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(animationSpec = tween(240)),
                exit = fadeOut(animationSpec = tween(180))
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = JellyRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = onLogin,
                enabled = !isBusy && username.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = JellyBlue,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF1E1E1E),
                    disabledContentColor = Color.White.copy(alpha = 0.4f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(Res.string.auth_sign_in))
                }
            }

            if (showQuickConnect) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onQuickConnect,
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isQuickConnectLoading && quickConnectCode == null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text(
                                text = quickConnectCode?.let {
                                    stringResource(Res.string.auth_quick_connect_code, it)
                                } ?: if (isQuickConnectLoading) {
                                    stringResource(Res.string.auth_generating_code)
                                } else {
                                    stringResource(Res.string.auth_quick_connect)
                                },
                                color = Color.White
                            )
                        }
                    }

                    if (quickConnectCode != null) {
                        Text(
                            text = stringResource(Res.string.auth_quick_connect_approval_hint),
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun amoledAuthFieldColors(
    hasLeadingIcon: Boolean = false
) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = JellyBlue,
    unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
    focusedBorderColor = JellyBlue,
    unfocusedBorderColor = Color.White.copy(alpha = 0.26f),
    focusedLeadingIconColor = if (hasLeadingIcon) JellyBlue else Color.Unspecified,
    unfocusedLeadingIconColor = if (hasLeadingIcon) Color.White.copy(alpha = 0.65f) else Color.Unspecified,
    cursorColor = JellyBlue,
    selectionColors = TextSelectionColors(
        handleColor = JellyBlue,
        backgroundColor = JellyBlue.copy(alpha = 0.28f)
    )
)
