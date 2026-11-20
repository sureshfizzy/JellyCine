package com.jellycine.app.ui.screens.dashboard.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.jellycine.data.model.*
import com.jellycine.data.repository.*
import com.jellycine.ui.screens.dashboard.search.SeerSearch
import com.jellycine.util.image.disableEmbyPosterEnhancers

/** Android entry point for the shared search screen. */
@Composable
fun SearchContainer(
    onNavigateToDetail: (BaseItemDto) -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepositoryProvider.getInstance(context) }
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val seerr = remember(context) { SeerSearch(SeerrRepository(context)) }

    com.jellycine.ui.screens.dashboard.search.SearchContainer(
        mediaRepository = mediaRepository,
        authRepository = authRepository,
        disablePosterEnhancers = disableEmbyPosterEnhancers(),
        seerr = seerr,
        onNavigateToDetail = onNavigateToDetail,
        onCancel = onCancel
    )
}
