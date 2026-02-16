package com.jellycine.app.ui.screens.dashboard.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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

    val offlineAvailable = remember(downloads) { downloads.filter { it.isOfflineAvailable } }
    val movieEntries = remember(offlineAvailable) {
        offlineAvailable.filter { (it.item?.type ?: it.mediaType).equals("Movie", ignoreCase = true) }
            .sortedBy { it.title.lowercase(Locale.getDefault()) }
    }
    val seriesGroups = remember(offlineAvailable) {
        buildSeriesGroups(
            offlineAvailable.filter { (it.item?.type ?: it.mediaType).equals("Episode", ignoreCase = true) }
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
            offlineAvailable.isEmpty() -> {
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
                text = "No offline downloads",
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
    onPlayMovie: (TrackedDownload) -> Unit
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
                    onPlay = { onPlayMovie(entry) }
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
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(170.dp)
                .height(94.dp)
                .clip(RoundedCornerShape(12.dp))
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
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${group.totalEpisodes} Episodes | ${formatBytes(group.totalSizeBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun MovieRow(
    entry: TrackedDownload,
    mediaRepository: MediaRepository,
    deleting: Boolean,
    onDelete: () -> Unit,
    onPlay: () -> Unit
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onPlay)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(170.dp)
                .height(94.dp)
                .clip(RoundedCornerShape(12.dp))
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
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${entry.item?.productionYear ?: entry.year ?: ""} | ${formatBytes(entry.fileSizeBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        IconButton(onClick = onDelete, enabled = !deleting) {
            if (deleting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete",
                    tint = Color.White.copy(alpha = 0.86f)
                )
            }
        }
    }
}

@Composable
private fun SeriesDetailContent(
    innerPadding: PaddingValues,
    group: OfflineSeriesGroup,
    mediaRepository: MediaRepository,
    deletingState: Map<String, Boolean>,
    onDeleteEpisode: (TrackedDownload) -> Unit,
    onPlayEpisode: (TrackedDownload) -> Unit
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
                style = MaterialTheme.typography.titleMedium,
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
                    onPlay = { onPlayEpisode(entry) }
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
    onPlay: () -> Unit
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
    val size = formatBytes(entry.fileSizeBytes)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onPlay)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(80.dp)
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
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$episodeNumber. ${entry.title}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$runtime | $size",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        IconButton(onClick = onDelete, enabled = !deleting) {
            if (deleting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete episode",
                    tint = Color.White.copy(alpha = 0.9f)
                )
            }
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
                totalSizeBytes = episodes.sumOf { it.fileSizeBytes ?: 0L }
            )
        }
        .sortedBy { it.title.lowercase(Locale.getDefault()) }
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


