package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.R
import com.jellycine.app.download.DownloadRepositoryProvider
import com.jellycine.app.ui.components.common.DownloadActionMenu
import com.jellycine.app.ui.components.common.DownloadContent
import com.jellycine.app.ui.components.common.canResumeDownloads
import com.jellycine.app.ui.components.common.downloadButtonVisualState
import com.jellycine.app.ui.components.common.hasActiveDownloads
import com.jellycine.app.ui.components.common.pausableItemIds
import com.jellycine.app.ui.components.common.rememberDownloadPanelProgress
import com.jellycine.app.ui.components.common.rememberDownloadPanelState
import com.jellycine.app.util.image.JellyfinPosterImage
import com.jellycine.app.util.image.imageTagFor
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.detail.CodecUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SeasonDetailScreen(
    seriesId: String,
    seasonId: String,
    seasonName: String? = null,
    onBackPressed: () -> Unit = {},
    onEpisodeClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val downloadRepository = remember { DownloadRepositoryProvider.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    
    var episodes by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var seasonQueueInProgress by remember(seasonId) { mutableStateOf(false) }
    var downloadErrorDialogMessage by remember(seasonId) { mutableStateOf<String?>(null) }
    var storageSelectionDialogState by remember(seasonId) { mutableStateOf<SeasonEpisodeSelectionDialogState?>(null) }
    var seriesTitle by remember(seriesId) { mutableStateOf<String?>(null) }
    var seasonMetadata by remember(seasonId) { mutableStateOf<BaseItemDto?>(null) }
    var seriesMetadata by remember(seriesId) { mutableStateOf<BaseItemDto?>(null) }
    var heroImageCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var heroImageIndex by remember { mutableIntStateOf(0) }
    var logoImageCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var logoImageIndex by remember { mutableIntStateOf(0) }
    var logoCandidateLookup by remember(seasonId, seriesId) { mutableStateOf(true) }
    var logoLoadError by remember(seasonId, seriesId) { mutableStateOf(false) }
    val trackedDownloads by downloadRepository.observeTrackedDownloads().collectAsState(initial = emptyList())

    // Load episodes for this season
    LaunchedEffect(seasonId) {
        isLoading = true
        try {
            val result = mediaRepository.getEpisodes(
                seriesId = seriesId,
                seasonId = seasonId,
                limit = 100
            )
            result.fold(
                onSuccess = { episodeList ->
                    episodes = episodeList.sortedBy { it.indexNumber ?: 0 }
                    isLoading = false
                },
                onFailure = { exception ->
                    error = exception.message
                    isLoading = false
                }
            )
        } catch (e: Exception) {
            error = e.message
            isLoading = false
        }
    }

    LaunchedEffect(seriesId) {
        seriesTitle = null
        try {
            mediaRepository.getItemById(seriesId).fold(
                onSuccess = { seriesItem ->
                    seriesMetadata = seriesItem
                    seriesTitle = seriesItem.name?.takeIf { it.isNotBlank() }
                },
                onFailure = {
                    seriesMetadata = null
                }
            )
        } catch (_: Exception) {
            seriesMetadata = null
            seriesTitle = null
        }
    }

    LaunchedEffect(seasonId) {
        seasonMetadata = try {
            mediaRepository.getItemById(seasonId).getOrNull()
        } catch (_: Exception) {
            null
        }
    }

    // Prepare hero image candidates in fallback order
    LaunchedEffect(seasonId, seriesId, seasonMetadata, seriesMetadata) {
        try {
            heroImageCandidates = listOfNotNull(
                mediaRepository.getBackdropImageUrl(
                    itemId = seasonId,
                    imageIndex = 0,
                    width = 1200,
                    height = 675,
                    quality = 92,
                    imageTag = seasonMetadata?.imageTagFor(
                        imageType = "Backdrop",
                        targetItemId = seasonId
                    )
                ).first(),
                mediaRepository.getBackdropImageUrl(
                    itemId = seriesId,
                    imageIndex = 0,
                    width = 1200,
                    height = 675,
                    quality = 92,
                    imageTag = seasonMetadata?.imageTagFor(
                        imageType = "Backdrop",
                        targetItemId = seriesId
                    ) ?: seriesMetadata?.imageTagFor(
                        imageType = "Backdrop",
                        targetItemId = seriesId
                    )
                ).first(),
                mediaRepository.getImageUrl(
                    itemId = seasonId,
                    imageType = "Primary",
                    width = 900,
                    height = 1200,
                    quality = 92,
                    imageTag = seasonMetadata?.imageTagFor(
                        imageType = "Primary",
                        targetItemId = seasonId
                    )
                ).first(),
                mediaRepository.getImageUrl(
                    itemId = seriesId,
                    imageType = "Primary",
                    width = 900,
                    height = 1200,
                    quality = 92,
                    imageTag = seasonMetadata?.imageTagFor(
                        imageType = "Primary",
                        targetItemId = seriesId
                    ) ?: seriesMetadata?.imageTagFor(
                        imageType = "Primary",
                        targetItemId = seriesId
                    )
                ).first()
            ).distinct()
            heroImageIndex = 0
        } catch (e: Exception) {
            heroImageCandidates = emptyList()
            heroImageIndex = 0
        }
    }

    // Prepare logo candidates (season first, then series)
    LaunchedEffect(seasonId, seriesId, seasonMetadata, seriesMetadata) {
        logoCandidateLookup = true
        logoLoadError = false
        try {
            logoImageCandidates = listOfNotNull(
                mediaRepository.getImageUrl(
                    itemId = seasonId,
                    imageType = "Logo",
                    width = 1200,
                    quality = 95,
                    imageTag = seasonMetadata?.imageTagFor(
                        imageType = "Logo",
                        targetItemId = seasonId
                    )
                ).first(),
                mediaRepository.getImageUrl(
                    itemId = seriesId,
                    imageType = "Logo",
                    width = 1200,
                    quality = 95,
                    imageTag = seasonMetadata?.imageTagFor(
                        imageType = "Logo",
                        targetItemId = seriesId
                    ) ?: seriesMetadata?.imageTagFor(
                        imageType = "Logo",
                        targetItemId = seriesId
                    )
                ).first()
            ).distinct()
            logoImageIndex = 0
        } catch (e: Exception) {
            logoImageCandidates = emptyList()
            logoImageIndex = 0
        } finally {
            logoCandidateLookup = false
        }
    }

    val currentHeroImageUrl = heroImageCandidates.getOrNull(heroImageIndex)
    val currentLogoImageUrl = logoImageCandidates.getOrNull(logoImageIndex)
    val showLogoImage = currentLogoImageUrl != null && !logoLoadError
    val reserveLogoSpace = showLogoImage || logoCandidateLookup
    val fallbackHeaderTitle = seriesTitle
        ?: episodes.firstOrNull()?.seriesName?.takeIf { it.isNotBlank() }
        ?: seasonName
        ?: "Season"
    val seasonEpisodeIds = remember(episodes) { episodes.mapNotNull { it.id }.toSet() }
    val seasonDownloadEntries = remember(trackedDownloads, seasonEpisodeIds) {
        trackedDownloads.filter { seasonEpisodeIds.contains(it.itemId) }
    }
    val seasonDownload = rememberDownloadPanelState(
        entries = seasonDownloadEntries,
        expectedCount = seasonEpisodeIds.size
    )
    val hasActiveSeasonDownloads = seasonDownload.hasActiveDownloads
    val canResumeSeasonDownloads = seasonDownload.canResumeDownloads
    var seasonDownloadActionMenu by remember(
        seasonId,
        seasonDownload.status,
        seasonDownload.activeItemIds.size,
        seasonDownload.pausedItemIds.size
    ) { mutableStateOf(false) }
    val animatedSeasonDownloadProgress = rememberDownloadPanelProgress(
        panelState = seasonDownload,
        label = "season_download_progress"
    )

    when {
        error != null -> {
            LaunchedEffect(Unit) {
                onBackPressed()
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp)
                        ) {
                            if (currentHeroImageUrl != null) {
                                JellyfinPosterImage(
                                    context = context,
                                    imageUrl = currentHeroImageUrl,
                                    contentDescription = seasonName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    onErrorStateChange = { hasError ->
                                        if (hasError) {
                                            if (heroImageIndex < heroImageCandidates.lastIndex) {
                                                heroImageIndex += 1
                                            }
                                        }
                                    }
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF20202A))
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.0f to Color.Transparent,
                                                0.62f to Color.Transparent,
                                                0.86f to Color.Black.copy(alpha = 0.58f),
                                                1.0f to Color.Black
                                            )
                                        )
                                    )
                            )

                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = (-22).dp)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.86f)
                                    .height(58.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (showLogoImage) {
                                    JellyfinPosterImage(
                                        context = context,
                                        imageUrl = currentLogoImageUrl,
                                        contentDescription = seasonName,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit,
                                        alignment = Alignment.CenterStart,
                                        onErrorStateChange = { hasError ->
                                            if (hasError) {
                                                if (logoImageIndex < logoImageCandidates.lastIndex) {
                                                    logoImageIndex += 1
                                                } else {
                                                    logoLoadError = true
                                                }
                                            } else {
                                                logoLoadError = false
                                            }
                                        }
                                    )
                                } else if (!reserveLogoSpace) {
                                    Text(
                                        text = fallbackHeaderTitle,
                                        fontSize = 17.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Text(
                                text = seasonName ?: "Season",
                                fontSize = 15.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.96f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = "${episodes.size} episodes",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.86f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        episodes.firstOrNull()?.id?.let { firstEpisodeId ->
                                            onEpisodeClick(firstEpisodeId)
                                        }
                                    },
                                    enabled = episodes.isNotEmpty(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(23.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Play season",
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Play",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            when {
                                                hasActiveSeasonDownloads -> seasonDownloadActionMenu = true
                                                else -> {
                                                    coroutineScope.launch {
                                                        seasonQueueInProgress = true
                                                        try {
                                                            val estimateResult = downloadRepository.buildEpisodeBatchEstimate(episodes)
                                                            estimateResult.fold(
                                                                onSuccess = { estimate ->
                                                                    storageSelectionDialogState = SeasonEpisodeSelectionDialogState.fromEstimate(estimate)
                                                                },
                                                                onFailure = { throwable ->
                                                                    downloadErrorDialogMessage = throwable.message
                                                                        ?: "Failed to prepare season download."
                                                                }
                                                            )
                                                        } finally {
                                                            seasonQueueInProgress = false
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        enabled = episodes.isNotEmpty() && (!seasonQueueInProgress || hasActiveSeasonDownloads),
                                        modifier = Modifier.fillMaxSize(),
                                        shape = RoundedCornerShape(23.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color(0xFF1F1F24),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        DownloadContent(
                                            visualState = downloadButtonVisualState(
                                                panelState = seasonDownload,
                                                isQueueing = seasonQueueInProgress,
                                                supportsCompleted = true
                                            ),
                                            progress = animatedSeasonDownloadProgress,
                                            idleLabelRes = R.string.downloads_action_download,
                                            fontSize = 13.sp,
                                            iconSize = 18.dp,
                                            progressSize = 16.dp
                                        )
                                    }

                                    DownloadActionMenu(
                                        expanded = seasonDownloadActionMenu,
                                        canResume = canResumeSeasonDownloads,
                                        hasActiveDownloads = hasActiveSeasonDownloads,
                                        onDismissRequest = { seasonDownloadActionMenu = false },
                                        onPauseResume = {
                                            seasonDownloadActionMenu = false
                                            if (canResumeSeasonDownloads) {
                                                seasonDownload.pausedItemIds.forEach(downloadRepository::resumeDownload)
                                            } else {
                                                seasonDownload.pausableItemIds.forEach(downloadRepository::pauseDownload)
                                            }
                                        },
                                        onCancel = {
                                            seasonDownloadActionMenu = false
                                            seasonDownload.activeItemIds.forEach(downloadRepository::cancelDownload)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Episodes",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 10.dp)
                    )
                }

                if (isLoading) {
                    items(4) {
                        EpisodeCardSkeleton(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    items(episodes) { episode ->
                        EpisodeListItem(
                            episode = episode,
                            mediaRepository = mediaRepository,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            onClick = {
                                episode.id?.let { episodeId ->
                                    onEpisodeClick(episodeId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    storageSelectionDialogState?.let { dialogState ->
        DownloadDialog(
            title = "Choose Episodes",
            subtitle = "Pick episodes to download. Selected total must fit available storage.",
            availableBytes = dialogState.availableBytes,
            options = dialogState.options,
            initialSelection = dialogState.options.map { it.id }.toSet(),
            confirmLabel = "Download Episodes",
            onDismiss = { storageSelectionDialogState = null },
            onConfirm = { selectedIds ->
                val selectedEpisodes = dialogState.options
                    .filter { selectedIds.contains(it.id) }
                    .mapNotNull { option -> dialogState.episodesById[option.id] }
                storageSelectionDialogState = null
                coroutineScope.launch {
                    seasonQueueInProgress = true
                    try {
                        downloadRepository.enqueueEpisodeDownloads(selectedEpisodes).onFailure { throwable ->
                            downloadErrorDialogMessage = throwable.message
                                ?: "Failed to queue selected episodes."
                        }
                    } finally {
                        seasonQueueInProgress = false
                    }
                }
            }
        )
    }

    downloadErrorDialogMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { downloadErrorDialogMessage = null },
            containerColor = Color(0xFF1A1C22),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.92f),
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Download Failed",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp
                )
            },
            text = {
                Text(text = message)
            },
            confirmButton = {
                TextButton(
                    onClick = { downloadErrorDialogMessage = null },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF22D3EE)
                    )
                ) {
                    Text("OK", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

private data class SeasonEpisodeSelectionDialogState(
    val availableBytes: Long,
    val options: List<StorageSelectionOption>,
    val episodesById: Map<String, BaseItemDto>
) {
    companion object {
        fun fromEstimate(estimate: com.jellycine.app.download.BatchDownloadEstimate): SeasonEpisodeSelectionDialogState {
            val candidates = estimate.candidates
                .filter { !it.item.id.isNullOrBlank() }
                .sortedWith(
                    compareBy<com.jellycine.app.download.BatchDownloadCandidate>(
                        { it.item.parentIndexNumber ?: Int.MAX_VALUE },
                        { it.item.indexNumber ?: Int.MAX_VALUE },
                        { it.item.name.orEmpty() }
                    )
                )

            val options = candidates.map { candidate ->
                val episode = candidate.item
                val episodeId = episode.id.orEmpty()
                val episodeCode = buildString {
                    episode.parentIndexNumber?.let { season ->
                        episode.indexNumber?.let { episodeNumber ->
                            append("S$season:E$episodeNumber")
                        }
                    }
                }
                val episodeSubtitle = buildString {
                    if (episodeCode.isNotBlank()) append(episodeCode)
                    if (!episode.seriesName.isNullOrBlank()) {
                        if (isNotBlank()) append("  |  ")
                        append(episode.seriesName)
                    }
                }.ifBlank { null }

                StorageSelectionOption(
                    id = episodeId,
                    title = episode.name?.takeIf { it.isNotBlank() } ?: "Episode",
                    subtitle = episodeSubtitle,
                    requiredBytes = candidate.remainingBytes ?: 0L
                )
            }

            return SeasonEpisodeSelectionDialogState(
                availableBytes = estimate.availableBytes,
                options = options,
                episodesById = candidates.associate { it.item.id.orEmpty() to it.item }
            )
        }
    }
}

@Composable
private fun EpisodeListItem(
    episode: BaseItemDto,
    mediaRepository: MediaRepository,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var episodeImageUrl by remember(episode.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(episode.id) {
        episodeImageUrl = resolveEpisodePrimaryOrSeriesBackdrop(
            episode = episode,
            mediaRepository = mediaRepository,
            width = 1280,
            height = 720,
            quality = 95
        )
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(74.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E1E))
                ) {
                    JellyfinPosterImage(
                        context = context,
                        imageUrl = episodeImageUrl,
                        contentDescription = episode.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = buildString {
                            episode.indexNumber?.let { append("$it. ") }
                            append(episode.name ?: "Unknown Episode")
                        },
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    episode.runTimeTicks?.let { ticks ->
                        Text(
                            text = CodecUtils.formatRuntime(ticks),
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.82f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Text(
                    text = overview,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
