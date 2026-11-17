package com.jellycine.app.ui.screens.dashboard.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jellycine.app.BuildConfig
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.preferences.Preferences
import com.jellycine.ui.screens.dashboard.settings.SettingsViewModel

private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.jellycine.app"

/** Android entry points for the shared settings sub-screens. */
@Composable
fun AboutScreen(onBackPressed: () -> Unit = {}) {
    com.jellycine.ui.screens.dashboard.settings.AboutScreen(
        versionName = BuildConfig.VERSION_NAME,
        rateAppUrl = PLAY_STORE_URL,
        onBackPressed = onBackPressed
    )
}

@Composable
fun CacheSettingsScreen(onBackPressed: () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel { createSettingsViewModel(context) }
    com.jellycine.ui.screens.dashboard.settings.CacheSettingsScreen(
        viewModel = viewModel,
        onBackPressed = onBackPressed
    )
}

@Composable
fun ClipsSettingsScreen(onBackPressed: () -> Unit = {}) {
    val context = LocalContext.current
    val preferences = remember { Preferences(context) }
    com.jellycine.ui.screens.dashboard.settings.ClipsSettingsScreen(
        preferences = preferences,
        onBackPressed = onBackPressed
    )
}

@Composable
fun InterfaceSettingsScreen(onBackPressed: () -> Unit = {}) {
    val context = LocalContext.current
    val preferences = remember { Preferences(context) }
    val authRepository = remember { AuthRepositoryProvider.getInstance(context) }
    com.jellycine.ui.screens.dashboard.settings.InterfaceSettingsScreen(
        preferences = preferences,
        authRepository = authRepository,
        onBackPressed = onBackPressed
    )
}