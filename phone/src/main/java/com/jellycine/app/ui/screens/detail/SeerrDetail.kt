package com.jellycine.app.ui.screens.detail

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.ui.components.common.fetchSeerTmdbPersonId
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.BaseItemPerson
import com.jellycine.data.model.SeerrItemIds
import com.jellycine.data.model.SeerrPersonRole
import com.jellycine.data.model.SeerrRecommendationTitle
import com.jellycine.data.model.SeerrRequestOptions
import com.jellycine.data.model.SeerrRequestSelection
import com.jellycine.data.model.SeerrRequestState
import com.jellycine.data.model.filterSeerTitlesForRow
import com.jellycine.data.model.seerTitleParams
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.SeerrRepository
import com.jellycine.shared.R
import com.jellycine.shared.ui.components.common.SeerrRequestActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

internal data class SeerrRelatedItemsState(
    val localSimilarItems: List<BaseItemDto>,
    val seerrSimilarItems: List<SeerrRecommendationTitle>,
    val seerrRecommendedItems: List<SeerrRecommendationTitle>
)

internal data class SeerrDirectorItemsState(
    val localDirectorItems: List<BaseItemDto>,
    val seerrDirectorItems: List<SeerrRecommendationTitle>
)

internal data class SeerrRequestUiState(
    val requestState: SeerrRequestState,
    val requestInProgress: Boolean,
    val optionsLoading: Boolean,
    val requestOptions: SeerrRequestOptions?,
    val requestErrorMessage: String?,
    val pendingSeasonRequestNumber: Int?,
    val seasonRefreshKey: Int,
    val onLoadRequestOptions: (Int?) -> Unit,
    val onSubmitRequest: (SeerrRequestSelection?) -> Unit,
    val onHideRequestOptions: () -> Unit,
    val onDismissRequestOptions: () -> Unit,
    val onClearRequestError: () -> Unit
) {
    val isBusy: Boolean
        get() = requestInProgress || optionsLoading
}

@Composable
internal fun rememberSeerrRepository(context: Context): SeerrRepository {
    return remember(context) { SeerrRepository(context) }
}

internal suspend fun loadDetailItem(
    itemId: String,
    activeServerId: String?,
    mediaRepository: MediaRepository,
    seerrRepository: SeerrRepository,
    seerrNotConnectedMessage: String
): Result<BaseItemDto> {
    val seerParams = SeerrItemIds.detailParams(itemId)
        ?: return mediaRepository.getItemById(itemId)
    val scopeId = activeServerId?.takeIf { it.isNotBlank() }
        ?: return Result.failure(IllegalStateException(seerrNotConnectedMessage))
    val (mediaType, tmdbId) = seerParams
    return seerrRepository.getTitleDetails(
        scopeId = scopeId,
        tmdbId = tmdbId,
        mediaType = mediaType
    )
}

@Composable
internal fun seerrRequestState(
    item: BaseItemDto,
    isSeerDetail: Boolean,
    activeServerId: String?,
    seerrRepository: SeerrRepository,
    coroutineScope: CoroutineScope
): SeerrRequestUiState {
    val context = androidx.compose.ui.platform.LocalContext.current
    var seerrRequestState by remember(item.id, item.seerrRequestState) {
        mutableStateOf(item.seerrRequestState ?: SeerrRequestState.NONE)
    }
    var seerrRequestInProgress by remember(item.id) { mutableStateOf(false) }
    var seerrOptionsLoading by remember(item.id) { mutableStateOf(false) }
    var seerrRequestOptions by remember(item.id) { mutableStateOf<SeerrRequestOptions?>(null) }
    var seerrRequestErrorMessage by remember(item.id) { mutableStateOf<String?>(null) }
    var pendingSeasonRequestNumber by remember(item.id) { mutableStateOf<Int?>(null) }
    var seerrSeasonRefreshKey by remember(item.id) { mutableIntStateOf(0) }

    fun submitSeerrRequest(selection: SeerrRequestSelection?) {
        val requestingSeasonCard = pendingSeasonRequestNumber != null
        if (
            seerrRequestInProgress ||
            (!requestingSeasonCard && seerrRequestState == SeerrRequestState.REQUESTED)
        ) {
            return
        }
        val (mediaType, tmdbId) = item.seerTitleParams() ?: return
        val scopeId = activeServerId?.takeIf { it.isNotBlank() }
        if (scopeId == null) {
            seerrRequestErrorMessage = context.getString(R.string.detail_seerr_not_connected)
            return
        }

        coroutineScope.launch {
            seerrRequestInProgress = true
            try {
                seerrRepository.requestTitle(
                    scopeId = scopeId,
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    selection = selection
                ).fold(
                    onSuccess = {
                        if (requestingSeasonCard) {
                            seerrSeasonRefreshKey += 1
                        } else {
                            seerrRequestState = SeerrRequestState.REQUESTED
                        }
                        seerrRequestOptions = null
                        pendingSeasonRequestNumber = null
                    },
                    onFailure = { throwable ->
                        pendingSeasonRequestNumber = null
                        seerrRequestErrorMessage = throwable.message
                            ?: context.getString(R.string.detail_seerr_request_failed)
                    }
                )
            } finally {
                seerrRequestInProgress = false
            }
        }
    }

    fun loadSeerrRequestOptions(seasonNumber: Int? = null) {
        val isSeasonRequest = seasonNumber != null
        if ((!isSeasonRequest && !isSeerDetail) || seerrOptionsLoading || seerrRequestInProgress) return
        val (mediaType, tmdbId) = item.seerTitleParams() ?: return
        if (isSeasonRequest && !mediaType.equals("tv", ignoreCase = true)) return
        val scopeId = activeServerId?.takeIf { it.isNotBlank() }
        if (scopeId == null) {
            seerrRequestErrorMessage = context.getString(R.string.detail_seerr_not_connected)
            return
        }

        coroutineScope.launch {
            pendingSeasonRequestNumber = seasonNumber
            seerrOptionsLoading = true
            try {
                seerrRepository.getRequestOptions(
                    scopeId = scopeId,
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    seasonNumber = seasonNumber
                ).fold(
                    onSuccess = { options ->
                        seerrRequestOptions = options
                    },
                    onFailure = { throwable ->
                        pendingSeasonRequestNumber = null
                        seerrRequestErrorMessage = throwable.message
                            ?: context.getString(R.string.detail_seerr_request_failed)
                    }
                )
            } finally {
                seerrOptionsLoading = false
            }
        }
    }

    return SeerrRequestUiState(
        requestState = seerrRequestState,
        requestInProgress = seerrRequestInProgress,
        optionsLoading = seerrOptionsLoading,
        requestOptions = seerrRequestOptions,
        requestErrorMessage = seerrRequestErrorMessage,
        pendingSeasonRequestNumber = pendingSeasonRequestNumber,
        seasonRefreshKey = seerrSeasonRefreshKey,
        onLoadRequestOptions = ::loadSeerrRequestOptions,
        onSubmitRequest = ::submitSeerrRequest,
        onHideRequestOptions = {
            seerrRequestOptions = null
        },
        onDismissRequestOptions = {
            seerrRequestOptions = null
            pendingSeasonRequestNumber = null
        },
        onClearRequestError = {
            seerrRequestErrorMessage = null
        }
    )
}

@Composable
internal fun seerrRelatedItemsState(
    item: BaseItemDto,
    isSeerDetail: Boolean,
    activeServerId: String?,
    mediaRepository: MediaRepository,
    seerrRepository: SeerrRepository
): SeerrRelatedItemsState {
    var localSimilarItems by remember(item.id) { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var seerrSimilarItems by remember(item.id) {
        mutableStateOf<List<SeerrRecommendationTitle>>(emptyList())
    }
    var seerrRecommendedItems by remember(item.id) {
        mutableStateOf<List<SeerrRecommendationTitle>>(emptyList())
    }

    LaunchedEffect(item.id, activeServerId, isSeerDetail) {
        val currentItemId = item.id
        if (currentItemId.isNullOrBlank()) {
            localSimilarItems = emptyList()
            seerrSimilarItems = emptyList()
            seerrRecommendedItems = emptyList()
            return@LaunchedEffect
        }

        seerrSimilarItems = emptyList()
        seerrRecommendedItems = emptyList()
        val loadedLocalSimilarItems = if (isSeerDetail) {
            emptyList()
        } else {
            mediaRepository.getSimilarItems(itemId = currentItemId, limit = 16)
                .getOrDefault(emptyList())
                .filter { !it.id.isNullOrBlank() }
        }
        localSimilarItems = loadedLocalSimilarItems

        val params = if (isSeerDetail) {
            SeerrItemIds.detailParams(currentItemId)
        } else {
            item.seerTitleParams()
        }
        val scopeId = activeServerId?.takeIf { it.isNotBlank() }
        if (params == null || scopeId == null) {
            return@LaunchedEffect
        }

        val (seerrMediaType, seerrTmdbId) = params
        val similarSeerrTitles = if (isSeerDetail) {
            seerrRepository.getSimilarTitles(
                scopeId = scopeId,
                mediaType = seerrMediaType,
                tmdbId = seerrTmdbId,
                limit = 16
            ).getOrDefault(emptyList())
        } else {
            emptyList()
        }
        seerrSimilarItems = filterSeerTitlesForRow(
            seerrTitles = similarSeerrTitles,
            baseTitles = emptyList(),
            item = item
        )

        seerrRepository.getRecommendedTitles(
            scopeId = scopeId,
            mediaType = seerrMediaType,
            tmdbId = seerrTmdbId,
            limit = 16
        ).fold(
            onSuccess = { titles ->
                val filteredTitles = titles.filterNot { title ->
                    seerrSimilarItems.any { similar ->
                        similar.mediaType == title.mediaType && similar.tmdbId == title.tmdbId
                    }
                }
                seerrRecommendedItems = filterSeerTitlesForRow(
                    seerrTitles = filteredTitles,
                    baseTitles = loadedLocalSimilarItems,
                    item = item
                )
            },
            onFailure = {
                seerrRecommendedItems = emptyList()
            }
        )
    }

    return SeerrRelatedItemsState(
        localSimilarItems = localSimilarItems,
        seerrSimilarItems = seerrSimilarItems,
        seerrRecommendedItems = seerrRecommendedItems
    )
}

@Composable
internal fun seerrDirectorItemsState(
    item: BaseItemDto,
    directors: List<BaseItemPerson>,
    isSeerDetail: Boolean,
    activeServerId: String?,
    mediaRepository: MediaRepository,
    seerrRepository: SeerrRepository
): SeerrDirectorItemsState {
    val directorsKey = remember(directors) { directors.map { it.id }.joinToString() }
    var directorTitles by remember(item.id, directorsKey) {
        mutableStateOf<List<BaseItemDto>>(emptyList())
    }
    var seerrDirectorTitles by remember(item.id, directorsKey) {
        mutableStateOf<List<SeerrRecommendationTitle>>(emptyList())
    }
    val primaryDirector = directors.firstOrNull()

    LaunchedEffect(item.id, primaryDirector?.id, activeServerId, isSeerDetail) {
        val directorId = primaryDirector?.id
        if (directorId.isNullOrBlank()) {
            directorTitles = emptyList()
            seerrDirectorTitles = emptyList()
            return@LaunchedEffect
        }

        val targetType = if (item.type.equals("Movie", ignoreCase = true)) "Movie" else "Series"
        val localTitlesDeferred = if (isSeerDetail) {
            null
        } else {
            async {
                mediaRepository.getItemsForPerson(directorId).getOrNull()
                    ?.filter { it.type.equals(targetType, ignoreCase = true) }
            }
        }
        val serrTitlesDeferred = async {
            fetchSeerTmdbPersonId(
                item = item,
                personId = directorId,
                role = SeerrPersonRole.DIRECTOR,
                activeServerId = activeServerId,
                mediaRepository = mediaRepository,
                seerrRepository = seerrRepository
            )
        }

        val localDirectorTitles = localTitlesDeferred?.await()
        if (!isSeerDetail && localDirectorTitles == null) {
            serrTitlesDeferred.cancel()
            directorTitles = emptyList()
            seerrDirectorTitles = emptyList()
            return@LaunchedEffect
        }
        val baseDirectorTitles = localDirectorTitles.orEmpty()

        directorTitles = baseDirectorTitles
        val pendingSeerrTitles = filterSeerTitlesForRow(
            seerrTitles = serrTitlesDeferred.await(),
            baseTitles = baseDirectorTitles,
            item = item
        )
        seerrDirectorTitles = pendingSeerrTitles

        pendingSeerrTitles
            .mapNotNull { seerrTitle ->
                val jellyfinMediaId = seerrTitle.jellyfinMediaId
                    ?.takeIf { it.isNotBlank() && it != item.id }
                    ?: return@mapNotNull null
                jellyfinMediaId to seerrTitle
            }
            .distinctBy { (jellyfinMediaId, _) -> jellyfinMediaId }
            .forEach { (jellyfinMediaId, seerrTitle) ->
                launch {
                    val localItem = mediaRepository.getItemById(jellyfinMediaId).getOrNull()
                        ?: return@launch
                    val localItemId = localItem.id ?: return@launch
                    if (directorTitles.any { it.id == localItemId }) {
                        seerrDirectorTitles =
                            seerrDirectorTitles.filterNot { it.tmdbId == seerrTitle.tmdbId }
                        return@launch
                    }

                    directorTitles = directorTitles + localItem
                    seerrDirectorTitles =
                        seerrDirectorTitles.filterNot { it.tmdbId == seerrTitle.tmdbId }
                }
            }
    }

    return SeerrDirectorItemsState(
        localDirectorItems = directorTitles,
        seerrDirectorItems = seerrDirectorTitles
    )
}

internal suspend fun detailLogoImage(
    item: BaseItemDto,
    activeServerId: String?,
    isSeerDetail: Boolean,
    mediaRepository: MediaRepository,
    seerrRepository: SeerrRepository
): String? {
    return if (isSeerDetail) {
        seerrRepository.getTitleLogoUrl(activeServerId, item.id)
    } else {
        logoImage(
            item = item,
            mediaRepository = mediaRepository
        )
    }
}

@Composable
internal fun SeerrRequestButton(
    state: SeerrRequestUiState,
    modifier: Modifier = Modifier
) {
    SeerrRequestActionButton(
        requestState = state.requestState,
        isBusy = state.isBusy,
        busyLabel = if (state.optionsLoading) {
            stringResource(R.string.detail_seerr_loading_options)
        } else {
            stringResource(R.string.detail_seerr_requesting)
        },
        onClick = { state.onLoadRequestOptions(null) },
        modifier = modifier
    )
}

@Composable
internal fun SeerrRequestDialogs(
    state: SeerrRequestUiState,
    itemName: String,
    backdropImageUrl: String?
) {
    state.requestOptions?.let { options ->
        SeerrRequestDialog(
            itemName = itemName,
            backdropImageUrl = backdropImageUrl,
            options = options,
            onDismissRequest = state.onDismissRequestOptions,
            onConfirm = { selection ->
                state.onHideRequestOptions()
                state.onSubmitRequest(selection)
            }
        )
    }

    state.requestErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = state.onClearRequestError,
            containerColor = Color(0xFF1A1C22),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.92f),
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = stringResource(R.string.detail_seerr_request_failed),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp
                )
            },
            text = {
                Text(text = message)
            },
            confirmButton = {
                TextButton(
                    onClick = state.onClearRequestError,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF22D3EE)
                    )
                ) {
                    Text(stringResource(R.string.ok), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

internal fun BaseItemDto.isSeerDetailItem(): Boolean {
    return SeerrItemIds.isDetailId(id)
}

internal fun isSeerDetailItemId(itemId: String): Boolean {
    return SeerrItemIds.isDetailId(itemId)
}