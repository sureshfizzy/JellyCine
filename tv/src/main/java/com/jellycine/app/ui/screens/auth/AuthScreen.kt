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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jellycine.shared.R
import com.jellycine.shared.ui.theme.JellyBlue
import com.jellycine.shared.ui.theme.JellyRed
import com.jellycine.data.repository.AuthRepositoryProvider

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
    val uiState by authViewModel.uiState.collectAsState()
    val serverSwitchUiState by serverSwitchViewModel.uiState.collectAsState()
    val sessionSnapshot by authRepository.observeActiveSession().collectAsState(
        initial = authRepository.getActiveSessionSnapshot()
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
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0D1117),
                            Color.Black
                        ),
                        radius = 1200f
                    )
                )
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

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side - Branding
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedBrandHero(
                        title = when (currentStep) {
                            AuthStep.SERVER_CONNECTION -> stringResource(R.string.auth_connect_title)
                            AuthStep.LOGIN -> selectedServerName ?: stringResource(R.string.auth_welcome_back)
                        },
                        subtitle = when (currentStep) {
                            AuthStep.SERVER_CONNECTION -> stringResource(R.string.auth_connect_subtitle)
                            AuthStep.LOGIN -> if (selectedServerUrl.isNotBlank()) selectedServerUrl
                                else stringResource(R.string.auth_sign_in_subtitle)
                        }
                    )
                }

                Spacer(modifier = Modifier.width(48.dp))

                // Right side - Form
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    when (currentStep) {
                        AuthStep.SERVER_CONNECTION -> {
                            if (showServerConnection) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                ConnectionForm(
                                    serverUrl = uiState.serverUrl,
                                    isLoading = uiState.isServerLoading,
                                    errorMessage = uiState.serverErrorMessage,
                                    onServerUrlChange = authViewModel::updateServerUrl,
                                    onConnect = {
                                        authViewModel.connectToServer { url, name ->
                                            selectedServerUrl = url
                                            selectedServerName = name
                                            currentStep = AuthStep.LOGIN
                                        }
                                    },
                                    modifier = Modifier.widthIn(max = 420.dp)
                                )
                            }
                        }
                        AuthStep.LOGIN -> {
                            LoginForm(
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
                                },
                                modifier = Modifier.widthIn(max = 420.dp)
                            )
                        }
                    }
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
        initialValue = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "drift_x"
    )
    val driftY by logoMotion.animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "drift_y"
    )
    val pulse by logoMotion.animateFloat(
        initialValue = 0.99f, targetValue = 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.jellycine_logo),
            contentDescription = stringResource(
                R.string.feature_logo_content_description,
                stringResource(R.string.app_name)
            ),
            modifier = Modifier
                .size(100.dp)
                .graphicsLayer {
                    translationX = driftX
                    translationY = driftY
                }
                .scale(pulse),
            contentScale = ContentScale.Fit
        )

        Text(
            text = title,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.8f)
        )
    }
}

@Composable
private fun ConnectionForm(
    serverUrl: String,
    isLoading: Boolean,
    errorMessage: String?,
    onServerUrlChange: (String) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.auth_connection_settings),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text(stringResource(R.string.server_url)) },
            placeholder = {
                Text(
                    stringResource(R.string.auth_server_url_placeholder),
                    color = Color.White.copy(alpha = 0.5f)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = tvTextFieldColors()
        )

        AnimatedVisibility(visible = errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = JellyRed,
                fontSize = 13.sp
            )
        }

        Button(
            onClick = onConnect,
            enabled = !isLoading && serverUrl.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
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
                Text(
                    text = stringResource(R.string.auth_connect_to_server),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
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
    onQuickConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPasswordVisible by remember { mutableStateOf(false) }
    val isBusy = isLoading || isQuickConnectLoading

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text(stringResource(R.string.username)) },
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.Person, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBusy,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = tvTextFieldColors()
        )

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.password)) },
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.Lock, contentDescription = null)
            },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Rounded.VisibilityOff
                            else Icons.Rounded.Visibility,
                        contentDescription = if (isPasswordVisible)
                            stringResource(R.string.auth_hide_password)
                        else stringResource(R.string.auth_show_password)
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBusy,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = tvTextFieldColors()
        )

        AnimatedVisibility(visible = errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = JellyRed,
                fontSize = 13.sp
            )
        }

        Button(
            onClick = onLogin,
            enabled = !isBusy && username.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
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
                Text(
                    text = stringResource(R.string.auth_sign_in),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (showQuickConnect) {
            OutlinedButton(
                onClick = onQuickConnect,
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
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
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
            }

            if (quickConnectCode != null) {
                Text(
                    text = stringResource(R.string.auth_quick_connect_approval_hint),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun tvTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = JellyBlue,
    unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
    focusedBorderColor = JellyBlue,
    unfocusedBorderColor = Color.White.copy(alpha = 0.26f),
    focusedLeadingIconColor = JellyBlue,
    unfocusedLeadingIconColor = Color.White.copy(alpha = 0.65f),
    cursorColor = JellyBlue,
    selectionColors = TextSelectionColors(
        handleColor = JellyBlue,
        backgroundColor = JellyBlue.copy(alpha = 0.28f)
    )
)