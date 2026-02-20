package com.jellycine.app.ui.screens.dashboard.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.download.DownloadStatus
import com.jellycine.app.download.DownloadRepositoryProvider
import com.jellycine.app.download.TrackedDownload
import com.jellycine.app.ui.screens.player.PlayerScreen
import com.jellycine.app.util.image.JellyfinPosterImage
import com.jellycine.app.util.image.rememberImageUrl
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current
    val downloadRepository = remember { DownloadRepositoryProvider.getInstance(context) }
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val downloads by downloadRepository.observeTrackedDownloads().collectAsState()
    val scope = rememberCoroutineScope()

    val visibleDownloads = remember(downloads) {
        downloads.filter {
            it.isOfflineAvailable ||
                it.state.status == DownloadStatus.DOWNLOADING ||
                it.state.status == DownloadStatus.QUEUED
        }
    }
    val movieEntries = remember(visibleDownloads) {
        visibleDownloads.filter { (it.item?.type ?: it.mediaType).equals("Movie", ignoreCase = true) }
            .sortedWith(
                compareByDescending<TrackedDownload> {
                    it.state.status == DownloadStatus.DOWNLOADING || it.state.status == DownloadStatus.QUEUED
                }
                    .thenBy { it.title.lowercase(Locale.getDefault()) }
            )
    }
    val seriesGroups = remember(visibleDownloads) {
        buildSeriesGroups(
            visibleDownloads.filter { (it.item?.type ?: it.mediaType).equals("Episode", ignoreCase = true) }
        )
    }

    var selectedSeriesId by remember { mutableStateOf<String?>(null) }
    var playbackItemId by remember { mutableStateOf<String?>(null) }
    val deletingState = remember { mutableStateMapOf<String, Boolean>() }

    val selectedSeries = remember(selectedSeriesId, seriesGroups) {
        seriesGroups.firstOrNull { it.id == selectedSeriesId }
    }

    val activePlaybackId = playbackItemId
    BackHandler {
        when {
            !activePlaybackId.isNullOrBlank() -> playbackItemId = null
            selectedSeries != null -> selectedSeriesId = null
            else -> onBackPressed()
        }
    }

    if (!activePlaybackId.isNullOrBlank()) {
        PlayerScreen(
            mediaId = activePlaybackId,
            onBackPressed = { playbackItemId = null }
        )
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(selectedSeries?.title ?: "Downloads") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedSeries != null) {
                            selectedSeriesId = null
                        } else {
                            onBackPressed()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            visibleDownloads.isEmpty() -> {
                EmptyDownloadsState(innerPadding = innerPadding)
            }
            selectedSeries != null -> {
                SeriesDetailContent(
                    innerPadding = innerPadding,
                    group = selectedSeries,
                    mediaRepository = mediaRepository,
                    deletingState = deletingState,
                    onDeleteEpisode = { entry ->
                        val itemId = entry.itemId
                        scope.launch {
                            deletingState[itemId] = true
                            downloadRepository.deleteDownloadedItem(itemId)
                            deletingState.remove(itemId)
                        }
                    },
                    onPlayEpisode = { entry ->
                        playbackItemId = entry.item?.id ?: entry.itemId
                    },
                    onPause = { entry ->
                        if (isPausedState(entry)) {
                            downloadRepository.resumeDownload(entry.itemId)
                        } else {
                            downloadRepository.pauseDownload(entry.itemId)
                        }
                    },
                    onCancel = { entry ->
                        downloadRepository.cancelDownload(entry.itemId)
                    }
                )
            }
            else -> {
                DownloadsRootContent(
                    innerPadding = innerPadding,
                    seriesGroups = seriesGroups,
                    movieEntries = movieEntries,
                    mediaRepository = mediaRepository,
                    deletingState = deletingState,
                    onSeriesClick = { selectedSeriesId = it.id },
                    onDeleteMovie = { entry ->
                        val itemId = entry.itemId
                        scope.launch {
                            deletingState[itemId] = true
                            downloadRepository.deleteDownloadedItem(itemId)
                            deletingState.remove(itemId)
                        }
                    },
                    onPlayMovie = { entry ->
                        playbackItemId = entry.item?.id ?: entry.itemId
                    },
                    onPause = { entry ->
                        if (isPausedState(entry)) {
                            downloadRepository.resumeDownload(entry.itemId)
                        } else {
                            downloadRepository.pauseDownload(entry.itemId)
                        }
                    },
                    onCancel = { entry ->
                        downloadRepository.cancelDownload(entry.itemId)
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyDownloadsState(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No downloads available",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun DownloadsRootContent(
    innerPadding: PaddingValues,
    seriesGroups: List<OfflineSeriesGroup>,
    movieEntries: List<TrackedDownload>,
    mediaRepository: MediaRepository,
    deletingState: Map<String, Boolean>,
    onSeriesClick: (OfflineSeriesGroup) -> Unit,
    onDeleteMovie: (TrackedDownload) -> Unit,
    onPlayMovie: (TrackedDownload) -> Unit,
    onPause: (TrackedDownload) -> Unit,
    onCancel: (TrackedDownload) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        if (seriesGroups.isNotEmpty()) {
            item { SectionLabel("TV") }
            items(seriesGroups, key = { "series_${it.id}" }) { group ->
                SeriesSummaryRow(
                    group = group,
                    mediaRepository = mediaRepository,
                    onClick = { onSeriesClick(group) }
                )
            }
        }

        if (movieEntries.isNotEmpty()) {
            item { SectionLabel("Movies") }
            items(movieEntries, key = { "movie_${it.itemId}" }) { entry ->
                MovieRow(
                    entry = entry,
                    mediaRepository = mediaRepository,
                    deleting = deletingState[entry.itemId] == true,
                    onDelete = { onDeleteMovie(entry) },
                    onPlay = { onPlayMovie(entry) },
                    onPauseAction = { onPause(entry) },
                    onCancelAction = { onCancel(entry) }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun SeriesSummaryRow(
    group: OfflineSeriesGroup,
    mediaRepository: MediaRepository,
    onClick: () -> Unit
) {
    val primaryImageUrl = rememberImageUrl(
        itemId = group.posterItemId,
        imageType = "Primary",
        width = 1280,
        height = 720,
        quality = 100,
        enableImageEnhancers = false,
        mediaRepository = mediaRepository
    )
    val backdropImageUrl = rememberImageUrl(
        itemId = group.posterItemId,
        imageType = "Backdrop",
        width = 1280,
        height = 720,
        quality = 100,
        enableImageEnhancers = false,
        mediaRepository = mediaRepository
    )
    val imageUrl = primaryImageUrl ?: backdropImageUrl

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(148.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            JellyfinPosterImage(
                imageUrl = imageUrl,
                contentDescription = group.title,
                context = LocalContext.current,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${group.totalEpisodes} Episodes | ${formatBytes(group.totalSizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            val progressLabel = groupProgressLabel(group)
            if (!progressLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = progressLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun MovieRow(
    entry: TrackedDownload,
    mediaRepository: MediaRepository,
    deleting: Boolean,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    onPauseAction: () -> Unit,
    onCancelAction: () -> Unit
) {
    val posterId = entry.item?.id ?: entry.itemId
    val primaryImageUrl = rememberImageUrl(
        itemId = posterId,
        imageType = "Primary",
        width = 1280,
        height = 720,
        quality = 100,
        enableImageEnhancers = false,
        mediaRepository = mediaRepository
    )
    val backdropImageUrl = rememberImageUrl(
        itemId = posterId,
        imageType = "Backdrop",
        width = 1280,
        height = 720,
        quality = 100,
        enableImageEnhancers = false,
        mediaRepository = mediaRepository
    )
    val imageUrl = primaryImageUrl ?: backdropImageUrl
    val canPlay = entry.isOfflineAvailable
    val statusLabel = downloadStatusLabel(entry)
    val statusColor = if (entry.state.status == DownloadStatus.DOWNLOADING) Color.White else Color.White.copy(alpha = 0.82f)
    val metaText = "${entry.item?.productionYear ?: entry.year ?: ""} | ${formatBytes(entry.displayBytes())}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = canPlay, onClick = onPlay)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(148.dp)
                .height(84.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            JellyfinPosterImage(
                imageUrl = imageUrl,
                contentDescription = entry.title,
                context = LocalContext.current,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = metaText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (!statusLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        DownloadRowAction(
            entry = entry,
            deleting = deleting,
            onDelete = onDelete,
            onPause = onPauseAction,
            onCancel = onCancelAction
        )
    }
}

@Composable
private fun SeriesDetailContent(
    innerPadding: PaddingValues,
    group: OfflineSeriesGroup,
    mediaRepository: MediaRepository,
    deletingState: Map<String, Boolean>,
    onDeleteEpisode: (TrackedDownload) -> Unit,
    onPlayEpisode: (TrackedDownload) -> Unit,
    onPause: (TrackedDownload) -> Unit,
    onCancel: (TrackedDownload) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        items(group.seasons, key = { it.id }) { season ->
                Text(
                    text = season.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

            season.episodes.forEach { entry ->
                EpisodeRow(
                    entry = entry,
                    mediaRepository = mediaRepository,
                    deleting = deletingState[entry.itemId] == true,
                    onDelete = { onDeleteEpisode(entry) },
                    onPlay = { onPlayEpisode(entry) },
                    onPauseAction = { onPause(entry) },
                    onCancelAction = { onCancel(entry) }
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    entry: TrackedDownload,
    mediaRepository: MediaRepository,
    deleting: Boolean,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    onPauseAction: () -> Unit,
    onCancelAction: () -> Unit
) {
    val item = entry.item
    val primaryImageUrl = rememberImageUrl(
        itemId = item?.id ?: entry.itemId,
        imageType = "Primary",
        width = 1280,
        height = 720,
        quality = 100,
        enableImageEnhancers = false,
        mediaRepository = mediaRepository
    )
    val backdropImageUrl = rememberImageUrl(
        itemId = item?.id ?: entry.itemId,
        imageType = "Backdrop",
        width = 1280,
        height = 720,
        quality = 100,
        enableImageEnhancers = false,
        mediaRepository = mediaRepository
    )
    val seriesPrimaryImageUrl = rememberImageUrl(
        itemId = item?.seriesId,
        imageType = "Primary",
        width = 1280,
        height = 720,
        quality = 100,
        enableImageEnhancers = false,
        mediaRepository = mediaRepository
    )
    val imageUrl = primaryImageUrl ?: backdropImageUrl ?: seriesPrimaryImageUrl
    val episodeNumber = item?.indexNumber ?: 0
    val runtime = item?.runTimeTicks?.let { ticksToMinutes(it) } ?: "-"
    val size = formatBytes(entry.displayBytes())
    val canPlay = entry.isOfflineAvailable
    val statusLabel = downloadStatusLabel(entry)
    val statusColor = if (entry.state.status == DownloadStatus.DOWNLOADING) Color.White else Color.White.copy(alpha = 0.82f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = canPlay, onClick = onPlay)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(136.dp)
                .height(74.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            JellyfinPosterImage(
                imageUrl = imageUrl,
                contentDescription = entry.title,
                context = LocalContext.current,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$episodeNumber. ${entry.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$runtime | $size",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (!statusLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        DownloadRowAction(
            entry = entry,
            deleting = deleting,
            onDelete = onDelete,
            onPause = onPauseAction,
            onCancel = onCancelAction
        )
    }
}

@Composable
private fun DownloadRowAction(
    entry: TrackedDownload,
    deleting: Boolean,
    onDelete: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit
) {
    val isActive = entry.state.status == DownloadStatus.DOWNLOADING || entry.state.status == DownloadStatus.QUEUED
    val isPaused = isPausedState(entry)
    var menuExpanded by remember(entry.itemId, entry.state.status, entry.state.message) { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = {
                if (isActive) {
                    menuExpanded = true
                } else {
                    onDelete()
                }
            },
            enabled = !deleting
        ) {
            when {
                deleting -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                }

                entry.state.status == DownloadStatus.DOWNLOADING -> {
                    val progress = entry.state.progress.coerceIn(0.02f, 1f)
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Download options",
                            tint = Color.White.copy(alpha = 0.92f),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                entry.state.status == DownloadStatus.QUEUED -> {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(
                                width = 1.3.dp,
                                color = Color.White.copy(alpha = 0.85f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Queue options",
                            tint = Color.White.copy(alpha = 0.92f),
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                else -> {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete",
                        tint = Color.White.copy(alpha = 0.88f)
                    )
                }
            }
        }

        DropdownMenu(
            modifier = Modifier.widthIn(min = 108.dp, max = 136.dp),
            expanded = menuExpanded && isActive,
            onDismissRequest = { menuExpanded = false }
        ) {
            if (entry.state.status == DownloadStatus.DOWNLOADING) {
                DropdownMenuItem(
                    text = { Text("Pause", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    onClick = {
                        menuExpanded = false
                        onPause()
                    }
                )
            } else if (isPaused) {
                DropdownMenuItem(
                    text = { Text("Resume", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    onClick = {
                        menuExpanded = false
                        onPause()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Cancel download", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                onClick = {
                    menuExpanded = false
                    onCancel()
                }
            )
        }
    }
}

private data class OfflineSeriesGroup(
    val id: String,
    val title: String,
    val posterItemId: String,
    val seasons: List<OfflineSeasonGroup>,
    val totalEpisodes: Int,
    val totalSizeBytes: Long
)

private data class OfflineSeasonGroup(
    val id: String,
    val label: String,
    val episodes: List<TrackedDownload>
)

private fun buildSeriesGroups(entries: List<TrackedDownload>): List<OfflineSeriesGroup> {
    return entries
        .groupBy { entry ->
            val item = entry.item
            item?.seriesId ?: item?.parentId ?: entry.itemId
        }
        .map { (seriesId, episodes) ->
            val firstItem = episodes.firstNotNullOfOrNull { it.item }
            val seriesTitle = firstItem?.seriesName?.takeIf { it.isNotBlank() }
                ?: episodes.firstOrNull()?.title
                ?: "Series"
            val posterItemId = firstItem?.seriesId ?: firstItem?.id ?: seriesId

            val seasons = episodes
                .groupBy { ep ->
                    val item = ep.item
                    item?.seasonId ?: item?.parentIndexNumber?.toString() ?: "unknown"
                }
                .map { (_, seasonEpisodes) ->
                    val first = seasonEpisodes.firstOrNull()?.item
                    val label = when {
                        !first?.seasonName.isNullOrBlank() -> first?.seasonName.orEmpty()
                        first?.parentIndexNumber != null -> "Season ${first.parentIndexNumber}"
                        else -> "Season"
                    }
                    OfflineSeasonGroup(
                        id = first?.seasonId ?: label,
                        label = label,
                        episodes = seasonEpisodes.sortedBy { it.item?.indexNumber ?: Int.MAX_VALUE }
                    )
                }
                .sortedBy { it.label.lowercase(Locale.getDefault()) }

            OfflineSeriesGroup(
                id = seriesId,
                title = seriesTitle,
                posterItemId = posterItemId,
                seasons = seasons,
                totalEpisodes = episodes.size,
                totalSizeBytes = episodes.sumOf { it.displayBytes() ?: 0L }
            )
        }
        .sortedWith(
            compareByDescending<OfflineSeriesGroup> { group ->
                group.seasons.any { season ->
                    season.episodes.any { episode ->
                        episode.state.status == DownloadStatus.DOWNLOADING ||
                            episode.state.status == DownloadStatus.QUEUED
                    }
                }
            }
                .thenBy { it.title.lowercase(Locale.getDefault()) }
        )
}

private fun formatBytes(bytes: Long?): String {
    val safe = bytes ?: return "-"
    if (safe <= 0L) return "-"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        safe >= gb -> String.format(Locale.US, "%.1f GB", safe / gb)
        safe >= mb -> String.format(Locale.US, "%.1f MB", safe / mb)
        safe >= kb -> String.format(Locale.US, "%.0f KB", safe / kb)
        else -> "$safe B"
    }
}

private fun ticksToMinutes(ticks: Long): String {
    val minutes = ticks / 10_000_000L / 60L
    val hours = minutes / 60
    val remMin = minutes % 60
    return when {
        hours > 0L && remMin > 0L -> "${hours}h ${remMin}m"
        hours > 0L -> "${hours}h"
        minutes > 0L -> "${minutes}m"
        else -> "-"
    }
}

private fun TrackedDownload.displayBytes(): Long? {
    return fileSizeBytes
        ?: state.totalBytes.takeIf { it > 0L }
        ?: state.downloadedBytes.takeIf { it > 0L }
}

private fun isPausedState(entry: TrackedDownload): Boolean {
    return entry.state.status == DownloadStatus.QUEUED &&
        entry.state.message?.trim()?.equals("Paused", ignoreCase = true) == true
}

private fun downloadStatusLabel(entry: TrackedDownload): String? {
    return when (entry.state.status) {
        DownloadStatus.DOWNLOADING -> {
            val percent = (entry.state.progress.coerceIn(0f, 1f) * 100f).toInt()
            val size = if (entry.state.totalBytes > 0L) {
                " | ${formatProgressBytes(entry.state.downloadedBytes)} / ${formatProgressBytes(entry.state.totalBytes)}"
            } else if (entry.state.downloadedBytes > 0L) {
                " | ${formatProgressBytes(entry.state.downloadedBytes)}"
            } else {
                ""
            }
            "Downloading $percent%$size"
        }
        DownloadStatus.QUEUED -> {
            if (entry.state.message?.trim()?.equals("Paused", ignoreCase = true) == true) {
                "Paused"
            } else {
                "Queued"
            }
        }
        else -> null
    }
}

private fun formatProgressBytes(bytes: Long): String {
    return if (bytes <= 0L) "0 B" else formatBytes(bytes)
}

private fun groupProgressLabel(group: OfflineSeriesGroup): String? {
    val episodes = group.seasons.flatMap { it.episodes }
    val totalEpisodes = episodes.size
    val completedEpisodes = episodes.count { it.isOfflineAvailable }
    val downloadingEpisodes = episodes.count { it.state.status == DownloadStatus.DOWNLOADING }
    val pausedEpisodes = episodes.count {
        it.state.status == DownloadStatus.QUEUED && it.state.message?.trim()?.equals("Paused", ignoreCase = true) == true
    }
    val queuedEpisodes = episodes.count { it.state.status == DownloadStatus.QUEUED } - pausedEpisodes

    return when {
        downloadingEpisodes > 0 ->
            "Downloading ${completedEpisodes + downloadingEpisodes} of $totalEpisodes"
        queuedEpisodes > 0 ->
            "Queued $queuedEpisodes episode" + if (queuedEpisodes > 1) "s" else ""
        pausedEpisodes > 0 ->
            "Paused $pausedEpisodes episode" + if (pausedEpisodes > 1) "s" else ""
        else -> null
    }
}


