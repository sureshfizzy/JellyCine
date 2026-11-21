package com.jellycine.app.ui.screens.dashboard.settings

import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.ui.screens.dashboard.settings.PlayerSettingsViewModel

@Composable
fun PlayerSettingsScreen(
    onBackPressed: () -> Unit = {},
    onNavigateToSubtitleSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: PlayerSettingsViewModel = viewModel {
        PlayerSettingsViewModel(
            PlayerPreferences(context),
            MediaRepositoryProvider.getInstance(context)
        )
    }
    com.jellycine.ui.screens.dashboard.settings.PlayerSettingsScreen(
        viewModel = viewModel,
        showAsyncMediaCodec = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M,
        onBackPressed = onBackPressed,
        onNavigateToSubtitleSettings = onNavigateToSubtitleSettings
    )
}

@Composable
fun SubtitleSettingsScreen(onBackPressed: () -> Unit = {}) {
    val context = LocalContext.current
    val playerPreferences = remember { PlayerPreferences(context) }
    com.jellycine.ui.screens.dashboard.settings.SubtitleSettingsScreen(
        playerPreferences = playerPreferences,
        onBackPressed = onBackPressed
    )
}
