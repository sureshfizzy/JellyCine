package com.jellycine.app.ui.screens.auth

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
import androidx.compose.foundation.text.selection.TextSelectionColors
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jellycine.app.R
import com.jellycine.app.ui.theme.JellyBlue
import com.jellycine.app.ui.theme.JellyRed
import kotlinx.coroutines.launch

enum class AuthStep {
    SERVER_CONNECTION,
    LOGIN
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val authViewModel: AuthScreenViewModel = viewModel {
        AuthScreenViewModel(context.applicationContext as android.app.Application)
    }
    val uiState by authViewModel.uiState.collectAsState()

    var currentStep by remember { mutableStateOf(AuthStep.SERVER_CONNECTION) }
    var serverName by remember { mutableStateOf<String?>(null) }
    var serverUrl by remember { mutableStateOf("") }

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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            ) {
                when (currentStep) {
                    AuthStep.SERVER_CONNECTION -> ServerConnectionContent(
                        modifier = Modifier.fillMaxSize(),
                        serverUrl = uiState.serverUrl,
                        isLoading = uiState.isServerLoading,
                        errorMessage = uiState.serverErrorMessage,
                        onServerUrlChange = authViewModel::updateServerUrl,
                        onConnect = {
                            authViewModel.connectToServer { url, name ->
                                serverUrl = url
                                serverName = name
                                currentStep = AuthStep.LOGIN
                            }
                        }
                    )

                    AuthStep.LOGIN -> LoginContent(
                        modifier = Modifier.fillMaxSize(),
                        serverUrl = serverUrl,
                        serverName = serverName,
                        username = uiState.username,
                        password = uiState.password,
                        isLoading = uiState.isLoginLoading,
                        errorMessage = uiState.loginErrorMessage,
                        onUsernameChange = authViewModel::updateUsername,
                        onPasswordChange = authViewModel::updatePassword,
                        onLogin = { authViewModel.login(serverUrl, onAuthSuccess) }
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
            contentDescription = "JellyCine Logo",
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
            title = "Connect Your Server",
            subtitle = "Set your Jellyfin or Emby URL to start streaming.",
            modifier = Modifier.padding(bottom = 24.dp)
        )

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

@Composable
private fun LoginContent(
    modifier: Modifier = Modifier,
    serverUrl: String,
    serverName: String?,
    username: String,
    password: String,
    isLoading: Boolean,
    errorMessage: String?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedBrandHero(
            title = serverName ?: "Welcome Back",
            subtitle = if (serverUrl.isNotBlank()) serverUrl else "Sign in to continue",
            modifier = Modifier.padding(bottom = 20.dp)
        )

        LoginForm(
            username = username,
            password = password,
            isLoading = isLoading,
            errorMessage = errorMessage,
            onUsernameChange = onUsernameChange,
            onPasswordChange = onPasswordChange,
            onLogin = onLogin
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
                text = "Connection Settings",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = onServerUrlChange,
                label = { Text("Server URL") },
                placeholder = { Text("http://192.168.1.100:8096") },
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = JellyBlue,
                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                    focusedBorderColor = JellyBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.26f),
                    cursorColor = JellyBlue,
                    selectionColors = TextSelectionColors(
                        handleColor = JellyBlue,
                        backgroundColor = JellyBlue.copy(alpha = 0.28f)
                    )
                )
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
                    Text("Connect to Server")
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
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    val usernameBringIntoView = remember { BringIntoViewRequester() }
    val passwordBringIntoView = remember { BringIntoViewRequester() }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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
                label = { Text("Username") },
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
                enabled = !isLoading,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
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
            )

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
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
                                "Hide password"
                            } else {
                                "Show password"
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
                enabled = !isLoading,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
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
                enabled = !isLoading && username.isNotBlank(),
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
                    Text("Sign In")
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
        onUsernameChange = {},
        onPasswordChange = {},
        onLogin = {}
    )
}
