package com.jellycine.app.ui.screens.dashboard.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.jellycine.data.model.*
import com.jellycine.data.repository.*
import com.jellycine.ui.screens.dashboard.search.SearchSeerrSource
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
    val seerr = remember(context) { AndroidSearchSeerrSource(SeerrRepository(context)) }

    com.jellycine.ui.screens.dashboard.search.SearchContainer(
        mediaRepository = mediaRepository,
        authRepository = authRepository,
        disablePosterEnhancers = disableEmbyPosterEnhancers(),
        seerr = seerr,
        onNavigateToDetail = onNavigateToDetail,
        onCancel = onCancel
    )
}

private class AndroidSearchSeerrSource(
    private val seerrRepository: SeerrRepository
) : SearchSeerrSource {
    override fun isConnected(scopeId: String?): Boolean =
        seerrRepository.getSavedConnectionInfo(scopeId)?.isVerified == true

    override suspend fun searchTitles(
        scopeId: String,
        query: String,
        limit: Int
    ): Result<List<SeerrRecommendationTitle>> =
        seerrRepository.searchTitles(scopeId = scopeId, query = query, limit = limit)

    override suspend fun discoveryTitles(
        scopeId: String,
        category: SeerrDiscoveryCategory,
        limit: Int
    ): Result<List<SeerrRecommendationTitle>> =
        seerrRepository.getDiscoveryTitles(scopeId = scopeId, category = category, limit = limit)
}
