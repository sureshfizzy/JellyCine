package com.vela.app.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vela.data.model.BaseItemDto
import com.vela.data.repository.MediaRepository
import com.vela.shared.R
import com.vela.shared.ui.components.common.WatchedIndicatorBadge
import com.vela.shared.util.image.JellyfinPosterImage
import com.vela.shared.util.image.getBackdrop
import kotlinx.coroutines.flow.first

internal const val EpisodeJumpThreshold = 40
private const val EpisodeRangeGroupLarge = 50
private const val EpisodeRangeGroupSmall = 20

internal data class EpisodeRange(
    val fromIndex: Int,
    val toIndexExclusive: Int,
    val startNumber: Int,
    val endNumber: Int
)

internal fun episodeRangeGroupSize(count: Int): Int {
    return when {
        count > 100 -> EpisodeRangeGroupLarge
        count > EpisodeJumpThreshold -> EpisodeRangeGroupSmall
        else -> count.coerceAtLeast(1)
    }
}

internal fun episodeRanges(episodes: List<BaseItemDto>): List<EpisodeRange> {
    if (episodes.isEmpty()) return emptyList()
    val groupSize = episodeRangeGroupSize(episodes.size)
    if (episodes.size <= EpisodeJumpThreshold) {
        val first = episodes.first().indexNumber ?: 1
        val last = episodes.last().indexNumber ?: episodes.size
        return listOf(
            EpisodeRange(
                fromIndex = 0,
                toIndexExclusive = episodes.size,
                startNumber = first,
                endNumber = last
            )
        )
    }
    return episodes.indices.chunked(groupSize).map { chunk ->
        val from = chunk.first()
        val to = chunk.last() + 1
        EpisodeRange(
            fromIndex = from,
            toIndexExclusive = to,
            startNumber = episodes[from].indexNumber ?: (from + 1),
            endNumber = episodes[to - 1].indexNumber ?: to
        )
    }
}

internal fun defaultEpisodeRangeIndex(episodes: List<BaseItemDto>): Int {
    val ranges = episodeRanges(episodes)
    if (ranges.size <= 1) return 0
    val focusIndex = episodes.indexOfFirst { episode ->
        val runtime = episode.runTimeTicks ?: 0L
        val position = episode.userData?.playbackPositionTicks ?: 0L
        position > 0L && runtime > 0L && position < runtime && episode.userData?.played != true
    }.takeIf { it >= 0 } ?: episodes.indexOfFirst { episode ->
        episode.userData?.played != true
    }
    if (focusIndex < 0) return 0
    return ranges.indexOfFirst { range ->
        focusIndex in range.fromIndex until range.toIndexExclusive
    }.coerceAtLeast(0)
}

internal fun matchEpisodes(episodes: List<BaseItemDto>, query: String): List<BaseItemDto> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    val number = trimmed.toIntOrNull()
    if (number != null) {
        val exact = episodes.filter { it.indexNumber == number }
        if (exact.isNotEmpty()) return exact
    }
    val lowered = trimmed.lowercase()
    return episodes.filter { episode ->
        episode.indexNumber?.toString()?.contains(trimmed) == true ||
            episode.name.orEmpty().lowercase().contains(lowered)
    }.take(30)
}

@Composable
internal fun EpisodeRangeChips(
    ranges: List<EpisodeRange>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (ranges.size <= 1) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        itemsIndexed(ranges) { index, range ->
            val selected = index == selectedIndex
            Surface(
                onClick = { onSelected(index) },
                shape = RoundedCornerShape(16.dp),
                color = if (selected) Color.White else Color(0xFF1F1F24),
                modifier = Modifier.height(32.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.detail_episode_range,
                            range.startNumber,
                            range.endNumber
                        ),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) Color.Black else Color.White.copy(alpha = 0.88f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
internal fun EpisodeJumpDialog(
    episodes: List<BaseItemDto>,
    onDismiss: () -> Unit,
    onJump: (BaseItemDto) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val matches = remember(episodes, query) { matchEpisodes(episodes, query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161618),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.92f),
        shape = RoundedCornerShape(18.dp),
        title = {
            Text(
                text = stringResource(R.string.detail_jump_episode),
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(stringResource(R.string.detail_jump_episode_hint))
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { matches.firstOrNull()?.let(onJump) }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = 0.45f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                        cursorColor = Color.White,
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f)
                    )
                )
                if (query.isNotBlank() && matches.isEmpty()) {
                    Text(
                        text = stringResource(R.string.detail_jump_episode_empty),
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.62f)
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    matches.forEach { episode ->
                        Text(
                            text = episodeJumpLabel(episode),
                            color = Color.White,
                            fontSize = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onJump(episode) }
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { matches.firstOrNull()?.let(onJump) },
                enabled = matches.isNotEmpty()
            ) {
                Text(
                    text = stringResource(R.string.detail_jump_confirm),
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Color.White.copy(alpha = 0.8f))
            }
        }
    )
}

@Composable
internal fun EpisodeJumpHeaderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onClick, modifier = modifier.size(36.dp)) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = stringResource(R.string.detail_jump_episode),
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun MoreFromSeasonSection(
    episodes: List<BaseItemDto>,
    currentEpisodeId: String?,
    seriesId: String?,
    currentSeasonId: String?,
    seasonName: String?,
    seasonNumber: Int?,
    mediaRepository: MediaRepository,
    onEpisodeClick: (String) -> Unit,
    onOpenSeason: (String, String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var seasons by remember(seriesId) { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var selectedSeasonId by remember(currentSeasonId, seriesId) {
        mutableStateOf(currentSeasonId)
    }
    var remoteEpisodes by remember(selectedSeasonId) {
        mutableStateOf<List<BaseItemDto>>(emptyList())
    }
    var seasonMenu by remember { mutableStateOf(false) }
    var showJump by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(seriesId) {
        val id = seriesId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        seasons = mediaRepository.getSeasons(id)
            .getOrNull()
            .orEmpty()
            .sortedBy { it.indexNumber ?: 0 }
    }

    LaunchedEffect(seriesId, selectedSeasonId, currentSeasonId) {
        val id = seriesId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val seasonId = selectedSeasonId
        if (seasonId.isNullOrBlank() || seasonId == currentSeasonId) {
            remoteEpisodes = emptyList()
            return@LaunchedEffect
        }
        remoteEpisodes = mediaRepository.getEpisodes(seriesId = id, seasonId = seasonId)
            .getOrNull()
            .orEmpty()
            .sortedWith(
                compareBy(
                    { it.indexNumber ?: Int.MAX_VALUE },
                    { it.name.orEmpty() }
                )
            )
    }

    val displayedEpisodes = if (
        selectedSeasonId.isNullOrBlank() || selectedSeasonId == currentSeasonId
    ) {
        episodes
    } else {
        remoteEpisodes
    }
    if (displayedEpisodes.isEmpty() && episodes.isEmpty()) return

    val selectedSeason = seasons.firstOrNull { it.id == selectedSeasonId }
    val headerSeasonNumber = selectedSeason?.indexNumber ?: seasonNumber
    val headerSeasonName = selectedSeason?.name?.takeIf { it.isNotBlank() } ?: seasonName
    val title = if (headerSeasonNumber != null) {
        stringResource(R.string.detail_more_from_season, headerSeasonNumber)
    } else {
        stringResource(
            R.string.detail_more_from_season_named,
            headerSeasonName ?: stringResource(R.string.detail_season_fallback)
        )
    }

    LaunchedEffect(displayedEpisodes, currentEpisodeId, selectedSeasonId) {
        val index = displayedEpisodes.indexOfFirst { it.id == currentEpisodeId }
        if (index >= 0) {
            listState.scrollToItem(index)
        }
    }

    Column(
        modifier = modifier.padding(top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = seasons.size > 1) { seasonMenu = true }
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (seasons.size > 1) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            DropdownMenu(
                expanded = seasonMenu,
                onDismissRequest = { seasonMenu = false },
                containerColor = Color(0xFF1A1A1C)
            ) {
                seasons.forEach { season ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = seasonDisplayName(season),
                                color = Color.White
                            )
                        },
                        onClick = {
                            selectedSeasonId = season.id
                            seasonMenu = false
                        }
                    )
                }
            }
            if (displayedEpisodes.size >= EpisodeJumpThreshold) {
                EpisodeJumpHeaderButton(onClick = { showJump = true })
            }
            val openSeriesId = seriesId
            val openSeasonId = selectedSeasonId ?: currentSeasonId
            if (!openSeriesId.isNullOrBlank() && !openSeasonId.isNullOrBlank()) {
                IconButton(
                    onClick = {
                        onOpenSeason(
                            openSeriesId,
                            openSeasonId,
                            headerSeasonName
                        )
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = stringResource(R.string.detail_open_season),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(
                items = displayedEpisodes,
                key = { episode -> episode.id ?: "${episode.name}-${episode.indexNumber}" }
            ) { episode ->
                SeasonEpisodeStripCard(
                    episode = episode,
                    selected = episode.id == currentEpisodeId,
                    mediaRepository = mediaRepository,
                    onClick = { episode.id?.let(onEpisodeClick) }
                )
            }
        }
    }

    if (showJump) {
        EpisodeJumpDialog(
            episodes = displayedEpisodes,
            onDismiss = { showJump = false },
            onJump = { episode ->
                showJump = false
                episode.id?.let(onEpisodeClick)
            }
        )
    }
}

@Composable
internal fun SeasonEpisodeStripCard(
    episode: BaseItemDto,
    selected: Boolean,
    mediaRepository: MediaRepository,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var imageUrl by remember(episode.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(episode.id) {
        imageUrl = getBackdrop(
            episode = episode,
            mediaRepository = mediaRepository,
            width = 400,
            height = 225,
            quality = 85
        )
    }
    val runtime = episode.runTimeTicks ?: 0L
    val position = episode.userData?.playbackPositionTicks ?: 0L
    val progress = if (runtime > 0L) {
        (position.toFloat() / runtime.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val label = episodeStripTitle(episode)

    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(10.dp))
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 1.5.dp,
                            color = Color.White.copy(alpha = 0.82f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .background(Color(0xFF2A2A2A))
        ) {
            JellyfinPosterImage(
                context = context,
                imageUrl = imageUrl,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (episode.userData?.played == true) {
                WatchedIndicatorBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                )
            }
            if (progress > 0f && episode.userData?.played != true) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.18f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(Color.White)
                    )
                }
            }
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

internal suspend fun scrollToEpisode(
    listState: LazyListState,
    headerCount: Int,
    indexInVisible: Int
) {
    if (indexInVisible < 0) return
    listState.scrollToItem(headerCount + indexInVisible)
}

internal fun episodeJumpLabel(episode: BaseItemDto): String {
    val number = episode.indexNumber
    val name = episode.name?.takeIf { it.isNotBlank() } ?: return number?.toString().orEmpty()
    return if (number != null) "$number. $name" else name
}

private fun episodeStripTitle(episode: BaseItemDto): String {
    val name = episode.name?.takeIf { it.isNotBlank() }.orEmpty()
    val number = episode.indexNumber
    return if (number != null && name.isNotBlank()) {
        "$number.$name"
    } else {
        name.ifBlank { number?.toString().orEmpty() }
    }
}

private fun seasonDisplayName(season: BaseItemDto): String {
    val number = season.indexNumber
    val name = season.name?.takeIf { it.isNotBlank() }
    return when {
        number != null && (name.isNullOrBlank() || name.equals("Season $number", true)) ->
            "Season $number"
        !name.isNullOrBlank() -> name
        number != null -> "Season $number"
        else -> "Season"
    }
}
