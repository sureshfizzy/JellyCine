package com.jellycine.app.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jellycine.app.ui.screens.auth.AuthScreenViewModel
import com.jellycine.app.ui.components.common.rememberBackgroundImageUrl
import com.jellycine.app.ui.theme.JellyBlue
import com.jellycine.app.ui.theme.JellyRed
import kotlinx.coroutines.delay

enum class AuthStep {
    SERVER_CONNECTION,
    LOGIN
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current
    val authViewModel: AuthScreenViewModel = viewModel {
        AuthScreenViewModel(context.applicationContext as android.app.Application)
    }
    val uiState by authViewModel.uiState.collectAsState()
    
    var currentStep by remember { mutableStateOf(AuthStep.SERVER_CONNECTION) }
    var serverName by remember { mutableStateOf<String?>(null) }
    var serverUrl by remember { mutableStateOf("") }
    
    val backgroundUrl = rememberBackgroundImageUrl()

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(backgroundUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn(
                        animationSpec = tween(500, easing = FastOutSlowInEasing)
                    ) togetherWith fadeOut(
                        animationSpec = tween(400, easing = LinearOutSlowInEasing)
                    )
                },
                label = "auth_step_transition"
            ) { step ->
                when (step) {
                    AuthStep.SERVER_CONNECTION -> {
                        ServerConnectionContent(
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
                    }
                    AuthStep.LOGIN -> {
                        LoginContent(
                            serverUrl = serverUrl,
                            serverName = serverName,
                            username = uiState.username,
                            password = uiState.password,
                            isLoading = uiState.isLoginLoading,
                            errorMessage = uiState.loginErrorMessage,
                            onUsernameChange = authViewModel::updateUsername,
                            onPasswordChange = authViewModel::updatePassword,
                            onLogin = { authViewModel.login(serverUrl, onAuthSuccess) },
                            onLoginSuccess = onAuthSuccess,
                            onBackPressed = {
                                currentStep = AuthStep.SERVER_CONNECTION
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerConnectionContent(
    serverUrl: String,
    isLoading: Boolean,
    errorMessage: String?,
    onServerUrlChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        WelcomeSection()
        
        ConnectionForm(
            serverUrl = serverUrl,
            isLoading = isLoading,
            errorMessage = errorMessage,
            onServerUrlChange = onServerUrlChange,
            onConnect = onConnect
        )
    }
}

@Composable
private fun LoginContent(
    serverUrl: String,
    serverName: String?,
    username: String,
    password: String,
    isLoading: Boolean,
    errorMessage: String?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onLoginSuccess: () -> Unit,
    onBackPressed: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(
                onClick = onBackPressed,
                modifier = Modifier
                    .background(
                        Color.Black.copy(alpha = 0.3f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }
        
        LoginWelcomeSection(serverName = serverName)

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
private fun WelcomeSection() {
    Text(
        text = "Ready to Watch?",
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp,
        style = TextStyle(
            shadow = Shadow(
                color = Color.Black,
                offset = Offset(2f, 2f),
                blurRadius = 4f
            )
        )
    )
    
    Text(
        text = "Connect to your Jellyfin server to start streaming your favorite movies and series.",
        fontSize = 16.sp,
        color = Color.White.copy(alpha = 0.8f),
        textAlign = TextAlign.Center,
        lineHeight = 22.sp
    )
}

@Composable
private fun LoginWelcomeSection(serverName: String?) {
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "welcome_alpha"
    )
    val slideOffset by animateFloatAsState(
        targetValue = 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "welcome_slide"
    )

    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .offset(y = slideOffset.dp)
            .background(
                Color.Black.copy(alpha = 0.25f),
                RoundedCornerShape(10.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = "Log in to",
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            lineHeight = 26.sp,
            letterSpacing = (-0.1).sp,
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black,
                    offset = Offset(1f, 1f),
                    blurRadius = 2f
                )
            )
        )

        Text(
            text = serverName ?: "Jellyfin Server",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = JellyBlue,
            lineHeight = 28.sp,
            letterSpacing = (-0.1).sp,
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black,
                    offset = Offset(1f, 1f),
                    blurRadius = 2f
                )
            )
        )
    }
}

@Composable
private fun ConnectionForm(
    serverUrl: String,
    isLoading: Boolean,
    errorMessage: String?,
    onServerUrlChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color.Black.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            )
            .border(
                0.5.dp,
                Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(12.dp)
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text("Server URL", color = Color.White.copy(alpha = 0.7f)) },
            placeholder = { Text("http://192.168.1.100:8096", color = Color.White.copy(alpha = 0.5f)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = JellyBlue,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                cursorColor = JellyBlue,
                selectionColors = TextSelectionColors(
                    handleColor = JellyBlue,
                    backgroundColor = JellyBlue.copy(alpha = 0.3f)
                )
            ),
            shape = RoundedCornerShape(10.dp)
        )

        androidx.compose.animation.AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Text(
                text = errorMessage ?: "",
                color = JellyRed,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = onConnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    if (isLoading || serverUrl.isBlank()) {
                        Color.Transparent
                    } else {
                        JellyBlue
                    },
                    RoundedCornerShape(16.dp)
                )
                .border(
                    2.dp,
                    if (isLoading || serverUrl.isBlank()) {
                        Color.White.copy(alpha = 0.3f)
                    } else {
                        JellyBlue
                    },
                    RoundedCornerShape(16.dp)
                ),
            enabled = !isLoading && serverUrl.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = Color.White.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp
                )
            } else {
                Text(
                    text = "Connect to Server",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    color = Color.White
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
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color.Black.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            )
            .border(
                0.5.dp,
                Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(12.dp)
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Username", color = Color.White.copy(alpha = 0.7f)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = JellyBlue,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                cursorColor = JellyBlue,
                selectionColors = TextSelectionColors(
                    handleColor = JellyBlue,
                    backgroundColor = JellyBlue.copy(alpha = 0.3f)
                )
            ),
            shape = RoundedCornerShape(10.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password", color = Color.White.copy(alpha = 0.7f)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = JellyBlue,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                cursorColor = JellyBlue,
                selectionColors = TextSelectionColors(
                    handleColor = JellyBlue,
                    backgroundColor = JellyBlue.copy(alpha = 0.3f)
                )
            ),
            shape = RoundedCornerShape(10.dp)
        )

        androidx.compose.animation.AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Text(
                text = errorMessage ?: "",
                color = JellyRed,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = onLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    Color(0xFF1e3c72),
                    RoundedCornerShape(16.dp)
                ),
            enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = Color.White.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp
                )
            } else {
                Text(
                    text = "Sign In",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AuthScreenPreview() {
    AuthScreen(
        onAuthSuccess = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun ServerConnectionContentPreview() {
    ServerConnectionContent(
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
        serverUrl = "http://192.168.1.100:8096",
        serverName = "Home Media Server",
        username = "john_doe",
        password = "",
        isLoading = false,
        errorMessage = null,
        onUsernameChange = {},
        onPasswordChange = {},
        onLogin = {},
        onLoginSuccess = {},
        onBackPressed = {}
    )
}
