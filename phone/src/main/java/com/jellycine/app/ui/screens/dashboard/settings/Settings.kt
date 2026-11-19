package com.jellycine.app.ui.screens.dashboard.settings

import android.content.Context
import android.content.Intent
import android.media.MediaCodecList
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jellycine.app.ui.screens.auth.*
import com.jellycine.data.model.*
import com.jellycine.data.preferences.NetworkPreferences
import com.jellycine.data.repository.*
import com.jellycine.player.discord.DiscordRpcManager
import com.jellycine.preferences.Preferences
import com.jellycine.shared.R
import com.jellycine.ui.screens.dashboard.settings.*
import kotlinx.coroutines.launch

/** Android entry point for the shared settings tab. */
@Composable
fun Settings(
    onLogout: () -> Unit = {},
    onNavigateToPlayerSettings: () -> Unit = {},
    onNavigateToInterfaceSettings: () -> Unit = {},
    onNavigateToClipsSettings: () -> Unit = {},
    onNavigateToConnections: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToCacheSettings: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToServerInfo: () -> Unit = {},
    onNavigateToScreenTime: () -> Unit = {},
    onNavigateToRequestedItem: (BaseItemDto) -> Unit = {},
    onAddServer: () -> Unit = {},
    onAddUser: (serverUrl: String, serverName: String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val logsScope = rememberCoroutineScope()
    val viewModel: SettingsViewModel = viewModel { createSettingsViewModel(context) }
    val serverSwitchViewModel: ServerSwitchViewModel = viewModel {
        ServerSwitchViewModel(context.applicationContext as android.app.Application)
    }
    val uiState by viewModel.uiState.collectAsState()
    val serverSwitchUiState by serverSwitchViewModel.uiState.collectAsState()
    val serverSwitchDialogsState = rememberServerSwitchDialogsState()
    val supportedCodecs = remember(context) { getSupportedCodecsSummary(context) }
    val discordSdkAvailable = remember { DiscordRpcManager.isSdkAvailable() }

    com.jellycine.ui.screens.dashboard.settings.Settings(
        viewModel = viewModel,
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
        onOpenLanguageSettings = { openAppLanguageSettings(context) },
        onExportLogs = { logsScope.launch { LogsExporter.shareAll(context) } },
        onUserClick = serverSwitchDialogsState::openUsers,
        onServerClick = serverSwitchDialogsState::openServers,
        discordAvailable = discordSdkAvailable,
        showOfflineOptions = true,
        deviceInfo = {
            SettingsItem(
                icon = Icons.Rounded.Smartphone,
                title = androidx.compose.ui.res.stringResource(R.string.settings_device_model),
                subtitle = androidx.compose.ui.res.stringResource(
                    R.string.settings_device_model_value,
                    Build.MANUFACTURER,
                    Build.MODEL
                ),
                accentColor = Color(0xFF14B8A6)
            )
            DeviceInfoDivider()
            SettingsItem(
                icon = Icons.Rounded.Android,
                title = androidx.compose.ui.res.stringResource(R.string.settings_android_version),
                subtitle = androidx.compose.ui.res.stringResource(
                    R.string.settings_android_version_value,
                    Build.VERSION.RELEASE,
                    Build.VERSION.SDK_INT
                ),
                accentColor = Color(0xFF10B981)
            )
            DeviceInfoDivider()
            SettingsItem(
                icon = Icons.Rounded.VideoLibrary,
                title = androidx.compose.ui.res.stringResource(R.string.settings_video_codecs),
                subtitle = supportedCodecs,
                accentColor = Color(0xFFF59E0B)
            )
        }
    )

    if (uiState.seerrRequestedItems.mediaType != null) {
        SeerrRequestedItemsDialog(
            state = uiState.seerrRequestedItems,
            onDismiss = viewModel::clearSeerrRequestedItems,
            onItemClick = { item ->
                viewModel.clearSeerrRequestedItems()
                onNavigateToRequestedItem(item.toBaseItem())
            }
        )
    }

    ServerSwitchDialogsHost(
        state = serverSwitchDialogsState,
        savedServers = uiState.savedServers,
        activeServerId = uiState.activeServerId,
        currentServerName = uiState.serverName,
        currentServerUrl = uiState.serverUrl,
        isSwitching = serverSwitchUiState.isBusy,
        onAddServer = onAddServer,
        onAddUser = onAddUser,
        onServerSelected = { server, dismissDialog ->
            serverSwitchViewModel.switchServer(
                serverId = server.id,
                activeServerId = uiState.activeServerId,
                onSwitchComplete = dismissDialog
            )
        },
        onRequestRemoveServer = serverSwitchDialogsState::requestRemoval,
        onRequestRemoveUser = serverSwitchDialogsState::requestRemoval,
        onRemoveServer = { serverId, onRemoveComplete ->
            serverSwitchViewModel.removeServer(
                serverId = serverId,
                onRemoveComplete = onRemoveComplete
            )
        }
    )
}

/** Builds the settings view model from the Android singletons. */
fun createSettingsViewModel(
    context: Context,
    includeProfileData: Boolean = true,
    includeLocalSettings: Boolean = true
): SettingsViewModel {
    return SettingsViewModel(
        authRepository = AuthRepositoryProvider.getInstance(context),
        mediaRepository = MediaRepositoryProvider.getInstance(context),
        preferences = Preferences(context),
        networkPreferences = NetworkPreferences(context),
        seerr = RepositorySettingsSeerrSource(SeerrRepository(context)),
        includeProfileData = includeProfileData,
        includeLocalSettings = includeLocalSettings
    )
}

@Composable
private fun DeviceInfoDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

private fun openAppLanguageSettings(context: Context) {
    val appLanguageIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Intent(AndroidSettings.ACTION_APP_LOCALE_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    } else {
        Intent(AndroidSettings.ACTION_LOCALE_SETTINGS)
    }

    val fallbackIntent = Intent(AndroidSettings.ACTION_LOCALE_SETTINGS)
    val intentToLaunch = when {
        appLanguageIntent.resolveActivity(context.packageManager) != null -> appLanguageIntent
        fallbackIntent.resolveActivity(context.packageManager) != null -> fallbackIntent
        else -> return
    }

    context.startActivity(intentToLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun getSupportedCodecsSummary(context: Context): String {
    return try {
        val mediaCodecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val videoCodecs = mutableSetOf<String>()
        mediaCodecList.codecInfos.forEach { codecInfo ->
            if (!codecInfo.isEncoder) {
                codecInfo.supportedTypes.forEach { type ->
                    if (type.startsWith("video/")) {
                        videoCodecs.add(readableCodecName(type))
                    }
                }
            }
        }

        if (videoCodecs.isEmpty()) context.getString(R.string.settings_unavailable) else videoCodecs.sorted().joinToString(", ")
    } catch (_: Exception) {
        context.getString(R.string.settings_unavailable)
    }
}

private fun readableCodecName(mimeType: String): String {
    return when (mimeType.lowercase()) {
        "video/avc" -> "H.264"
        "video/hevc" -> "H.265"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/av01" -> "AV1"
        "video/dolby-vision" -> "Dolby Vision"
        "video/mp4v-es" -> "MPEG-4"
        "video/3gpp" -> "H.263"
        "video/mpeg2" -> "MPEG-2"
        "video/raw" -> "RAW"
        else -> {
            val subtype = mimeType.substringAfter('/', mimeType)
            subtype.substringAfterLast('.').uppercase()
        }
    }
}