package com.jellycine.ui.screens.detail

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import com.jellycine.data.model.*
import com.jellycine.data.repository.*
import com.jellycine.resources.*
import com.jellycine.ui.components.common.*
import kotlinx.coroutines.*
import org.jetbrains.compose.resources.stringResource

/** Seerr loading hooks shared by every platform's [DetailPlatform]. */
class SeerrDetailSupport(val repository: SeerrRepository) {
    suspend fun loadItem(
        itemId: String,
        activeServerId: String?,
        mediaRepository: MediaRepository
    ): Result<BaseItemDto> = loadDetailItem(
        itemId = itemId,
        activeServerId = activeServerId,
        mediaRepository = mediaRepository,
        seerrRepository = repository
    )

    suspend fun loadLogoImage(
        item: BaseItemDto,
        activeServerId: String?,
        mediaRepository: MediaRepository
    ): String? = detailLogoImage(
        item = item,
        activeServerId = activeServerId,
        isSeerDetail = SeerrItemIds.isDetailId(item.id),
        mediaRepository = mediaRepository,
        seerrRepository = repository
    )

    suspend fun loadPerson(
        tmdbId: String,
        activeServerId: String?
    ): Pair<BaseItemDto?, List<SeerrRecommendationTitle>>? {
        val scopeId = activeServerId?.takeIf { it.isNotBlank() } ?: return null
        return coroutineScope {
            val person = async { repository.getPersonDetails(scopeId, tmdbId) }
            val directed = async {
                fetchSeerDirectedTitlesForTmdbPerson(
                    personTmdbId = tmdbId,
                    activeServerId = activeServerId,
                    seerrRepository = repository
                )
            }
            person.await().getOrNull() to directed.await()
        }
    }
}

/** Seerr request row, dialogs, seasons and recommendations for one detail item. */
class SeerrDetailExtras internal constructor(
    private val item: BaseItemDto,
    val isSeerDetail: Boolean,
    private val connected: Boolean,
    private val repository: SeerrRepository,
    private val requestState: SeerrRequestUiState,
    private val versions: SeerrVersions,
    private val busyLabel: String
) {
    val showRequestRow: Boolean get() = isSeerDetail && connected

    @Composable
    fun VersionRequestBadge() {
        if (!versions.visible) return
        CompactSeerrRequestButton(
            state = versions,
            isBusy = requestState.isBusy &&
                requestState.prefer4KRequest &&
                requestState.preferredRequest == versions.targetIs4K,
            onClick = { requestState.onLoadQualityRequestOptions(versions.targetIs4K) }
        )
    }

    @Composable
    fun RequestRow(
        modifier: Modifier,
        buttonHeight: Dp,
        hasTrailer: Boolean,
        onTrailerClick: () -> Unit
    ) {
        SeerrRequestButtonRow(
            requestState = requestState.requestState,
            isBusy = requestState.isBusy,
            busyLabel = busyLabel,
            onRequestClick = { requestState.onLoadRequestOptions(null) },
            buttonHeight = buttonHeight,
            hasTrailer = hasTrailer,
            onTrailerClick = onTrailerClick,
            modifier = modifier
        )
    }

    @Composable
    fun Seasons(
        series: BaseItemDto,
        seriesId: String,
        mediaRepository: MediaRepository,
        activeServerId: String?,
        onSeasonClick: (seriesId: String, seasonId: String, seasonName: String?) -> Unit
    ) {
        SeasonsSection(
            series = series,
            seriesId = seriesId,
            mediaRepository = mediaRepository,
            activeServerId = activeServerId,
            refreshKey = requestState.seasonRefreshKey,
            loadingSeasonNumber = requestState.pendingSeasonRequestNumber?.takeIf { requestState.isBusy },
            onSeasonRequest = { seasonNumber -> requestState.onLoadRequestOptions(seasonNumber) },
            loadSeerrSeasons = { scopeId, tmdbId ->
                repository.getTitleSeasons(scopeId = scopeId, tmdbId = tmdbId).getOrDefault(emptyList())
            },
            onSeasonClick = onSeasonClick
        )
    }

    @Composable
    fun Recommendations(
        directors: List<BaseItemPerson>,
        mediaRepository: MediaRepository,
        activeServerId: String?,
        onItemClick: (String) -> Unit
    ) {
        SeerrRecommendations(
            item = item,
            directors = directors,
            isSeerDetail = isSeerDetail,
            activeServerId = activeServerId,
            mediaRepository = mediaRepository,
            seerrRepository = repository,
            onItemClick = onItemClick
        )
    }

    @Composable
    fun Dialogs(backdropImageUrl: String?) {
        SeerrRequestDialogs(
            state = requestState,
            itemName = item.name?.takeIf { it.isNotBlank() } ?: "Unknown",
            backdropImageUrl = backdropImageUrl
        )
    }
}

@Composable
fun rememberSeerrDetailExtras(
    item: BaseItemDto,
    repository: SeerrRepository,
    activeServerId: String?
): SeerrDetailExtras {
    val coroutineScope = rememberCoroutineScope()
    val isSeerDetail = SeerrItemIds.isDetailId(item.id)
    val connected = remember(activeServerId) {
        !activeServerId.isNullOrBlank() &&
            repository.getSavedConnectionInfo(activeServerId)?.isVerified == true
    }
    val requestState = seerrRequestState(
        item = item,
        isSeerDetail = isSeerDetail,
        activeServerId = activeServerId,
        seerrRepository = repository,
        coroutineScope = coroutineScope
    )
    val versions = versionRequestState(
        item = item,
        isSeerDetail = isSeerDetail,
        activeServerId = activeServerId,
        seerrRepository = repository,
        requestOptions = requestState.requestOptions
    )
    val busyLabel = stringResource(
        if (requestState.optionsLoading) {
            Res.string.detail_seerr_loading_options
        } else {
            Res.string.detail_seerr_requesting
        }
    )
    return SeerrDetailExtras(
        item = item,
        isSeerDetail = isSeerDetail,
        connected = connected,
        repository = repository,
        requestState = requestState,
        versions = versions,
        busyLabel = busyLabel
    )
}

/** Detail extras backed by [SeerrDetailExtras]. Platforms with more extras (downloads, cast) extend it. */
open class SeerrDelegatingDetailExtras(private val seerr: SeerrDetailExtras) : DetailExtras {
    override val isSeerDetail: Boolean get() = seerr.isSeerDetail
    override val showSeerrRequestRow: Boolean get() = seerr.showRequestRow

    @Composable
    override fun VersionRequestBadge() = seerr.VersionRequestBadge()

    @Composable
    override fun SeerrRequestRow(
        modifier: Modifier,
        buttonHeight: Dp,
        hasTrailer: Boolean,
        onTrailerClick: () -> Unit
    ) = seerr.RequestRow(modifier, buttonHeight, hasTrailer, onTrailerClick)

    @Composable
    override fun Seasons(
        series: BaseItemDto,
        seriesId: String,
        mediaRepository: MediaRepository,
        activeServerId: String?,
        onSeasonClick: (seriesId: String, seasonId: String, seasonName: String?) -> Unit
    ) = seerr.Seasons(series, seriesId, mediaRepository, activeServerId, onSeasonClick)

    @Composable
    override fun Recommendations(
        item: BaseItemDto,
        directors: List<BaseItemPerson>,
        mediaRepository: MediaRepository,
        activeServerId: String?,
        onItemClick: (String) -> Unit
    ) = seerr.Recommendations(directors, mediaRepository, activeServerId, onItemClick)

    @Composable
    override fun Dialogs(backdropImageUrl: String?) = seerr.Dialogs(backdropImageUrl)
}

@Composable
internal fun CompactSeerrRequestButton(
    state: SeerrVersions,
    isBusy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRequested = state.requestState == SeerrRequestState.REQUESTED
    val contentDescription = if (state.targetIs4K) {
        stringResource(Res.string.detail_seerr_request_4k)
    } else {
        stringResource(Res.string.detail_seerr_request_1080p)
    }
    val enabled = !isBusy && !isRequested
    val accentColor = if (state.targetIs4K) Color(0xFF9B7CFF) else Color(0xFF22D3EE)

    Surface(
        modifier = modifier
            .size(28.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = if (isRequested) Color(0xFF1F1F24) else accentColor.copy(alpha = 0.18f),
        border = BorderStroke(
            1.dp,
            if (isRequested) Color.White.copy(alpha = 0.18f) else accentColor.copy(alpha = 0.55f)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                isBusy -> CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = accentColor
                )

                isRequested -> Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = stringResource(Res.string.detail_seerr_requested),
                    tint = Color(0xFF9CDCFE),
                    modifier = Modifier.size(16.dp)
                )

                else -> Icon(
                    imageVector = Icons.Rounded.AddCircle,
                    contentDescription = contentDescription,
                    tint = accentColor,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}