package com.jellycine.app.ui.screens.dashboard.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jellycine.app.ui.components.common.SeerTitleCard
import com.jellycine.app.ui.components.common.fetchSeerCreditTitles
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.SeerrPersonRole
import com.jellycine.data.model.SeerrRecommendationTitle
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.AwardsRepositoryProvider
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.data.repository.SeerrRepository
import com.jellycine.shared.R
import com.jellycine.ui.components.common.AwardsCompactHeader
import com.jellycine.data.model.ContentType
import com.jellycine.ui.screens.dashboard.media.DiscoverSeerrBridge
import com.jellycine.util.image.disableEmbyPosterEnhancers

internal const val WATCHED_VIEW_ALL_PARENT_ID =
    com.jellycine.ui.screens.dashboard.media.WATCHED_VIEW_ALL_PARENT_ID

/** Android entry point for the shared Discover screen. */
@Composable
fun Discover(
    onItemClick: (BaseItemDto) -> Unit = {},
    onWatchedItemClick: (BaseItemDto) -> Unit = onItemClick,
    onNavigateToViewAll: (ContentType, String?, String) -> Unit = { _, _, _ -> },
    onProfileClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepositoryProvider.getInstance(context) }
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val seerrRepository = remember(context) { SeerrRepository(context) }
    val awardsRepository = remember(context) { AwardsRepositoryProvider.getInstance(context) }
    val disablePosterEnhancers = disableEmbyPosterEnhancers()

    val seerr = remember(mediaRepository, seerrRepository) {
        AndroidDiscoverSeerrBridge(mediaRepository, seerrRepository)
    }
    val sessionSnapshot by authRepository.observeActiveSession()
        .collectAsState(initial = authRepository.getActiveSessionSnapshot())
    val activeServerId = sessionSnapshot.activeServerId
    val seerrConnected = remember(activeServerId) {
        activeServerId?.let { seerrRepository.getSavedConnectionInfo(it)?.isVerified == true } ?: false
    }

    var awardsHeaderTitle by remember { mutableStateOf<String?>(null) }
    var awardsBack by remember { mutableStateOf<(() -> Unit)?>(null) }

    com.jellycine.ui.screens.dashboard.media.Discover(
        mediaRepository = mediaRepository,
        authRepository = authRepository,
        disablePosterEnhancers = disablePosterEnhancers,
        seerr = seerr,
        awardsContent = { feedPills, profileAvatar ->
            AwardsCompactHeader(
                title = awardsHeaderTitle ?: stringResource(R.string.dashboard_discover_awards),
                onBack = awardsBack,
                trailing = profileAvatar
            )
            if (awardsBack == null) feedPills()
            Box(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalAwardSeerrConnected provides seerrConnected) {
                    AwardsContent(
                        awardsRepository = awardsRepository,
                        onItemClick = onItemClick,
                        onViewAllCategory = { qid, mode, title ->
                            onNavigateToViewAll(ContentType.AWARD, "${qid}_${mode.name}", title)
                        },
                        onHeaderTitleChange = { awardsHeaderTitle = it },
                        onBackChange = { awardsBack = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        },
        onItemClick = onItemClick,
        onWatchedItemClick = onWatchedItemClick,
        onNavigateToViewAll = onNavigateToViewAll,
        onProfileClick = onProfileClick
    )
}

private class AndroidDiscoverSeerrBridge(
    private val mediaRepository: MediaRepository,
    private val seerrRepository: SeerrRepository
) : DiscoverSeerrBridge {
    override suspend fun creditTitles(
        seedItem: BaseItemDto,
        personId: String,
        role: SeerrPersonRole,
        activeServerId: String
    ): List<SeerrRecommendationTitle> = fetchSeerCreditTitles(
        item = seedItem,
        personId = personId,
        role = role,
        activeServerId = activeServerId,
        mediaRepository = mediaRepository,
        seerrRepository = seerrRepository
    )

    @Composable
    override fun TitleCard(item: SeerrRecommendationTitle, onClick: () -> Unit) {
        SeerTitleCard(item = item, onClick = onClick)
    }
}
