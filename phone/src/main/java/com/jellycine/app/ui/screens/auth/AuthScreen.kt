package com.jellycine.app.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jellycine.app.R
import com.jellycine.app.ui.components.common.amoledAuthFieldColors
import com.jellycine.shared.ui.theme.JellyBlue
import com.jellycine.shared.ui.theme.JellyRed
import com.jellycine.data.repository.AuthRepositoryProvider
import kotlinx.coroutines.launch

enum class AuthStep {
    SERVER_CONNECTION,
    LOGIN
}

@Composable
fun AuthScreen(
    serverUrl: String? = null,
    serverName: String? = null,
    startAtLogin: Boolean = false,
    preferSavedServers: Boolean = false,
    onAddServer: () -> Unit = {},
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val authViewModel: AuthScreenViewModel = viewModel {
        AuthScreenViewModel(context.applicationContext as android.app.Application)
    }
    val authRepository = remember { AuthRepositoryProvider.getInstance(context) }
    val serverSwitchViewModel: ServerSwitchViewModel = viewModel {
        ServerSwitchViewModel(context.applicationContext as android.app.Application)
    }
    val initialSessionSnapshot = remember { authRepository.getActiveSessionSnapshot() }
    val uiState by authViewModel.uiState.collectAsState()
    val serverSwitchUiState by serverSwitchViewModel.uiState.collectAsState()
    val sessionSnapshot by authRepository.observeActiveSession().collectAsState(
        initial = initialSessionSnapshot
    )
    val serverSwitchDialogsState = rememberServerSwitchDialogsState()

    val login = startAtLogin && !serverUrl.isNullOrBlank()
    val displaySavedServers = preferSavedServers && !login && sessionSnapshot.savedServers.isNotEmpty()
    var currentStep by remember(login) {
        mutableStateOf(if (login) AuthStep.LOGIN else AuthStep.SERVER_CONNECTION)
    }
    val showServerConnection = currentStep == AuthStep.SERVER_CONNECTION && displaySavedServers
    var selectedServerName by remember(serverName) { mutableStateOf(serverName) }
    var selectedServerUrl by remember(serverUrl) { mutableStateOf(serverUrl.orEmpty()) }
    val canNavigateBackToServerStep = currentStep == AuthStep.LOGIN && !login

    LaunchedEffect(displaySavedServers, currentStep) {
        if (
            displaySavedServers &&
            currentStep == AuthStep.SERVER_CONNECTION &&
            !serverSwitchDialogsState.showServerSwitchDialog &&
            !serverSwitchDialogsState.showUserSwitchDialog
        ) {
            serverSwitchDialogsState.openServers()
        }
    }

    BackHandler(enabled = canNavigateBackToServerStep && !uiState.isLoginLoading) {
        authViewModel.clearLoginError()
        currentStep = AuthStep.SERVER_CONNECTION
    }

    LaunchedEffect(currentStep, selectedServerUrl) {
        if (currentStep == AuthStep.LOGIN) {
            authViewModel.refreshQuickConnectVisibility(selectedServerUrl)
        }
    }

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
            ServerSwitchDialogsHost(
                state = serverSwitchDialogsState,
                savedServers = sessionSnapshot.savedServers,
                activeServerId = sessionSnapshot.activeServerId,
                currentServerName = selectedServerName,
                currentServerUrl = selectedServerUrl,
                isSwitching = serverSwitchUiState.isBusy,
                onAddServer = onAddServer,
                onAddUser = { restoredServerUrl, restoredServerName ->
                    selectedServerUrl = restoredServerUrl
                    selectedServerName = restoredServerName
                    currentStep = AuthStep.LOGIN
                },
                onServerSelected = { savedServer, dismissDialog ->
                    serverSwitchViewModel.switchServer(
                        serverId = savedServer.id,
                        activeServerId = sessionSnapshot.activeServerId,
                        onSwitchComplete = {
                            dismissDialog()
                            onAuthSuccess()
                        },
                        onSwitchFailed = { error ->
                            authViewModel.updateServerUrl(savedServer.serverUrl)
                            authViewModel.updateUsername(savedServer.username)
                            authViewModel.updatePassword("")
                            authViewModel.setLoginError(error)
                            selectedServerUrl = savedServer.serverUrl
                            selectedServerName = savedServer.serverName
                            dismissDialog()
                            currentStep = AuthStep.LOGIN
                        }
                    )
                },
                showRemoveAction = false,
                dismissServerDialogOnRequest = false,
                dismissUserDialogOnRequest = true,
                showServerCloseAction = false,
                onServerDialogDismiss = {},
                onUserDialogDismiss = {
                    if (displaySavedServers) {
                        serverSwitchDialogsState.returnToServers()
                    } else {
                        serverSwitchDialogsState.dismissUsers()
                    }
                }
            )

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
                        onServerUrlChange = authViewModel::updateServerUrl,
                        onConnect = {
                            authViewModel.connectToServer { url, name ->
                                selectedServerUrl = url
                                selectedServerName = name
                                currentStep = AuthStep.LOGIN
                            }
                        }
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
            painter = painterResource(id = R.drawable.jellycine_logo),
            contentDescription = stringResource(
                R.string.feature_logo_content_description,
                stringResource(R.string.app_name)
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
    onServerUrlChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedBrandHero(
            title = stringResource(R.string.auth_connect_title),
            subtitle = stringResource(R.string.auth_connect_subtitle),
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
            title = serverName ?: stringResource(R.string.auth_welcome_back),
            subtitle = if (serverUrl.isNotBlank()) serverUrl else stringResource(R.string.auth_sign_in_subtitle),
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
                text = stringResource(R.string.auth_connection_settings),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = onServerUrlChange,
                label = { Text(stringResource(R.string.server_url)) },
                placeholder = {
                    Text(
                        stringResource(R.string.auth_server_url_placeholder),
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
                shape = RoundedCornerShape(14.dp),
                colors = amoledAuthFieldColors()
            )

            AnimatedVisibility(
                visible = errorMessage != null,
                enter = androidx.compose.animation.fadeIn(animationSpec = tween(240)),
                exit = androidx.compose.animation.fadeOut(animationSpec = tween(180))
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
                    Text(stringResource(R.string.auth_connect_to_server))
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
                label = { Text(stringResource(R.string.username)) },
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
                shape = RoundedCornerShape(14.dp),
                colors = amoledAuthFieldColors(hasLeadingIcon = true)
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.password)) },
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
                                stringResource(R.string.auth_hide_password)
                            } else {
                                stringResource(R.string.auth_show_password)
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
                shape = RoundedCornerShape(14.dp),
                colors = amoledAuthFieldColors(hasLeadingIcon = true)
            )

            AnimatedVisibility(
                visible = errorMessage != null,
                enter = androidx.compose.animation.fadeIn(animationSpec = tween(240)),
                exit = androidx.compose.animation.fadeOut(animationSpec = tween(180))
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
                    Text(stringResource(R.string.auth_sign_in))
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
                                    stringResource(R.string.auth_quick_connect_code, it)
                                } ?: if (isQuickConnectLoading) {
                                    stringResource(R.string.auth_generating_code)
                                } else {
                                    stringResource(R.string.auth_quick_connect)
                                },
                                color = Color.White
                            )
                        }
                    }

                    if (quickConnectCode != null) {
                        Text(
                            text = stringResource(R.string.auth_quick_connect_approval_hint),
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

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AuthScreenPreview() {
    AuthScreen(onAuthSuccess = {})
}

@Preview(showBackground = true)
@Composable
private fun ServerConnectionContentPreview() {
    ServerConnectionContent(
        modifier = Modifier,
        serverUrl = "http://192.168.1.100:8096",
        isAwaitingSavedServers = false,
        isLoading = false,
        errorMessage = null,
        onServerUrlChange = {},
        onConnect = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun LoginContentPreview() {
    LoginContent(
        modifier = Modifier,
        serverUrl = "http://192.168.1.100:8096",
        serverName = "Home Media Server",
        username = "john_doe",
        password = "",
        isLoading = false,
        errorMessage = null,
        showQuickConnect = true,
        isQuickConnectLoading = false,
        quickConnectCode = null,
        onUsernameChange = {},
        onPasswordChange = {},
        onLogin = {},
        onQuickConnect = {}
    )
}
