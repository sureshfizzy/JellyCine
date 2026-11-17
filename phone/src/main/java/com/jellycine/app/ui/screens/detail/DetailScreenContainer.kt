package com.jellycine.app.ui.screens.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.preferences.Preferences
import com.jellycine.ui.screens.detail.DetailPlatform
import com.jellycine.ui.screens.detail.DetailScreenContainer as SharedDetailScreenContainer
import com.jellycine.ui.screens.detail.PersonScreenContainer as SharedPersonScreenContainer

@Composable
private fun rememberPhoneDetailPlatform(): DetailScreenPlatform {
    val context = LocalContext.current
    return remember(context) {
        DetailScreenPlatform(
            context = context,
            seerrRepository = com.jellycine.data.repository.SeerrRepository(context),
            playerPreferences = PlayerPreferences(context),
            preferences = Preferences(context),
            mediaRepository = MediaRepositoryProvider.getInstance(context),
            authRepository = AuthRepositoryProvider.getInstance(context)
        )
    }
}

@Composable
fun DetailScreenContainer(
    itemId: String,
    forceMergeVersions: Boolean = false,
    onBackPressed: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToPerson: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val platform = rememberPhoneDetailPlatform()
    SharedDetailScreenContainer(
        itemId = itemId,
        mediaRepository = remember(context) { MediaRepositoryProvider.getInstance(context) },
        authRepository = remember(context) { AuthRepositoryProvider.getInstance(context) },
        preferences = remember(context) { Preferences(context) },
        platform = platform as DetailPlatform,
        forceMergeVersions = forceMergeVersions,
        onBackPressed = onBackPressed,
        onNavigateToDetail = onNavigateToDetail,
        onNavigateToPerson = onNavigateToPerson
    )
}

@Composable
fun PersonScreenContainer(
    personId: String,
    onBackPressed: () -> Unit = {},
    onItemClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val platform = rememberPhoneDetailPlatform()
    SharedPersonScreenContainer(
        personId = personId,
        mediaRepository = remember(context) { MediaRepositoryProvider.getInstance(context) },
        authRepository = remember(context) { AuthRepositoryProvider.getInstance(context) },
        platform = platform,
        preferences = remember(context) { Preferences(context) },
        onBackPressed = onBackPressed,
        onItemClick = onItemClick
    )
}
