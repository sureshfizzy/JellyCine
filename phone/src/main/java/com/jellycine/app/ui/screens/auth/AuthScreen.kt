package com.jellycine.app.ui.screens.auth

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jellycine.data.network.ServerDiscovery
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.shared.ui.screens.auth.AuthScreen as SharedAuthScreen
import androidx.compose.runtime.getValue

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
    val authRepository = remember { AuthRepositoryProvider.getInstance(context) }
    val serverSwitchViewModel: ServerSwitchViewModel = viewModel {
        ServerSwitchViewModel(context.applicationContext as Application)
    }
    val serverSwitchUiState by serverSwitchViewModel.uiState.collectAsState()

    SharedAuthScreen(
        authRepository = authRepository,
        serverUrl = serverUrl,
        serverName = serverName,
        startAtLogin = startAtLogin,
        preferSavedServers = preferSavedServers,
        serverDiscovery = { ServerDiscovery.discoverServers(context) },
        onOpenLanguageSettings = {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Intent(AndroidSettings.ACTION_APP_LOCALE_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            } else {
                Intent(AndroidSettings.ACTION_LOCALE_SETTINGS)
            }
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
        onAuthSuccess = onAuthSuccess
    ) {
        val dialogsState = rememberServerSwitchDialogsState()

        BackHandler(enabled = canNavigateBack, onBack = navigateBack)

        LaunchedEffect(Unit) {
            discoveredServerUrl?.let { url ->
                discoveredServerUrl = null
                connectTo(url)
            }
        }

        LaunchedEffect(displaySavedServers, isServerStep) {
            if (
                displaySavedServers &&
                isServerStep &&
                !dialogsState.showServerSwitchDialog &&
                !dialogsState.showUserSwitchDialog
            ) {
                dialogsState.openServers()
            }
        }

        ServerSwitchDialogsHost(
            state = dialogsState,
            savedServers = savedServers,
            activeServerId = activeServerId,
            currentServerName = selectedServerName,
            currentServerUrl = selectedServerUrl,
            isSwitching = serverSwitchUiState.isBusy,
            onAddServer = onAddServer,
            onAddUser = { restoredServerUrl, restoredServerName ->
                goToLogin(restoredServerUrl, restoredServerName)
            },
            onServerSelected = { savedServer, dismissDialog ->
                serverSwitchViewModel.switchServer(
                    serverId = savedServer.id,
                    activeServerId = activeServerId,
                    onSwitchComplete = {
                        dismissDialog()
                        onAuthSuccess()
                    },
                    onSwitchFailed = { error ->
                        prefillLogin(savedServer, error)
                        dismissDialog()
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
                    dialogsState.returnToServers()
                } else {
                    dialogsState.dismissUsers()
                }
            }
        )
    }
}
