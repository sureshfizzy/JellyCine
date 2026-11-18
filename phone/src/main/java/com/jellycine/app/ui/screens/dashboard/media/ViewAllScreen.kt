package com.jellycine.app.ui.screens.dashboard.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.ContentType
import com.jellycine.data.model.QueryResult
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.AwardsRepositoryProvider
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.data.repository.SeerrRepository
import com.jellycine.ui.screens.dashboard.media.ViewAllSeerrSource
import com.jellycine.util.image.disableEmbyPosterEnhancers
import com.jellycine.ui.screens.dashboard.media.ViewAllScreen as SharedViewAllScreen

/** Android entry point for the shared View All screen. */
@Composable
fun ViewAllScreen(
    contentType: ContentType,
    parentId: String? = null,
    title: String = "",
    genreId: String? = null,
    onBackPressed: () -> Unit,
    onItemClick: (BaseItemDto) -> Unit
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val authRepository = remember { AuthRepositoryProvider.getInstance(context) }
    val awardsRepository = remember { AwardsRepositoryProvider.getInstance(context) }
    val seerr = remember(context) { AndroidViewAllSeerrSource(SeerrRepository(context)) }

    SharedViewAllScreen(
        mediaRepository = mediaRepository,
        authRepository = authRepository,
        contentType = contentType,
        parentId = parentId,
        title = title,
        genreId = genreId,
        disablePosterEnhancers = disableEmbyPosterEnhancers(),
        awardsRepository = awardsRepository,
        seerr = seerr,
        onBackPressed = onBackPressed,
        onItemClick = onItemClick
    )
}

private class AndroidViewAllSeerrSource(
    private val repository: SeerrRepository
) : ViewAllSeerrSource {
    override fun isConnected(scopeId: String): Boolean =
        repository.getSavedConnectionInfo(scopeId)?.isVerified == true

    override suspend fun getStudios(
        scopeId: String,
        studioId: String,
        limit: Int,
        startIndex: Int
    ): Result<QueryResult<BaseItemDto>> =
        repository.getStudios(scopeId, studioId, limit, startIndex)

    override suspend fun getNetworks(
        scopeId: String,
        networkId: String,
        limit: Int,
        startIndex: Int
    ): Result<QueryResult<BaseItemDto>> =
        repository.getNetworks(scopeId, networkId, limit, startIndex)
}
