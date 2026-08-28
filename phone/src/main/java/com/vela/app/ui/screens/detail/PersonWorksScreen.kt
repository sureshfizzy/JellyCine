package com.vela.app.ui.screens.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vela.app.ui.screens.dashboard.home.ImageLoader
import com.vela.data.model.BaseItemDto
import com.vela.data.repository.MediaRepository
import com.vela.shared.R
import kotlin.random.Random

private val SheetBackground = Color(0xFF1C1C1E)
private val AccentBlue = Color(0xFF7EB8FF)
private val ToolbarIconTint = Color.White.copy(alpha = 0.92f)

private enum class PersonWorksSort(
    val key: String,
    @StringRes val labelRes: Int,
    val supportsOrder: Boolean = true,
    val defaultDescending: Boolean = true
) {
    DateCreated("DateCreated", R.string.library_sort_date_added),
    SortName("SortName", R.string.person_works_sort_title, defaultDescending = false),
    CommunityRating("CommunityRating", R.string.person_works_sort_community),
    CriticRating("CriticRating", R.string.library_sort_critic),
    ProductionYear("ProductionYear", R.string.person_works_sort_year),
    PremiereDate("PremiereDate", R.string.person_works_sort_premiere),
    OfficialRating("OfficialRating", R.string.person_works_sort_official, defaultDescending = false),
    DatePlayed("DatePlayed", R.string.library_sort_date_played),
    Runtime("Runtime", R.string.library_sort_runtime),
    Bitrate("Bitrate", R.string.person_works_sort_bitrate),
    Size("Size", R.string.person_works_sort_size),
    Random("Random", R.string.library_sort_random, supportsOrder = false)
}

private enum class PlayStatusFilter {
    Any,
    Played,
    Unplayed
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonWorksScreen(
    title: String,
    items: List<BaseItemDto>,
    mediaRepository: MediaRepository,
    onBackPressed: () -> Unit,
    onItemClick: (String) -> Unit,
    onPlayClick: (BaseItemDto) -> Unit
) {
    var landscapePosters by rememberSaveable { mutableStateOf(false) }
    var sortField by rememberSaveable { mutableStateOf(PersonWorksSort.SortName.name) }
    var sortDescending by rememberSaveable { mutableStateOf(false) }
    var randomSeed by rememberSaveable { mutableIntStateOf(0) }
    var playStatus by rememberSaveable { mutableStateOf(PlayStatusFilter.Any.name) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedYear by rememberSaveable { mutableStateOf<Int?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    val activeSort = remember(sortField) {
        PersonWorksSort.entries.firstOrNull { it.name == sortField } ?: PersonWorksSort.SortName
    }
    val activePlayStatus = remember(playStatus) {
        PlayStatusFilter.entries.firstOrNull { it.name == playStatus } ?: PlayStatusFilter.Any
    }

    val typeOptions = remember(items) {
        items.mapNotNull { it.type?.takeIf(String::isNotBlank) }.distinct()
    }
    val yearOptions = remember(items) {
        items.mapNotNull { it.productionYear }.distinct().sortedDescending()
    }

    val filteredItems = remember(items, activePlayStatus, favoritesOnly, selectedType, selectedYear) {
        items.filter { item ->
            val played = item.userData?.played == true
            val playStatusMatch = when (activePlayStatus) {
                PlayStatusFilter.Any -> true
                PlayStatusFilter.Played -> played
                PlayStatusFilter.Unplayed -> !played
            }
            val favoriteMatch = !favoritesOnly || item.userData?.isFavorite == true
            val typeMatch = selectedType.isNullOrBlank() ||
                item.type.equals(selectedType, ignoreCase = true)
            val yearMatch = selectedYear == null || item.productionYear == selectedYear
            playStatusMatch && favoriteMatch && typeMatch && yearMatch
        }
    }

    val displayItems = remember(filteredItems, activeSort, sortDescending, randomSeed) {
        if (activeSort == PersonWorksSort.Random) {
            filteredItems.shuffled(Random(randomSeed))
        } else {
            filteredItems.sortedWith(personWorksComparator(activeSort, sortDescending))
        }
    }

    val filtersActive = activePlayStatus != PlayStatusFilter.Any ||
        favoritesOnly ||
        !selectedType.isNullOrBlank() ||
        selectedYear != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        PersonWorksToolbar(
            sortLabel = personWorksSortLabel(
                sort = activeSort,
                descending = sortDescending
            ),
            sortMenuExpanded = showSortMenu,
            onSortClick = { showSortMenu = true },
            onDismissSort = { showSortMenu = false },
            selectedSort = activeSort,
            sortDescending = sortDescending,
            onSortSelected = { field ->
                if (field == PersonWorksSort.Random) {
                    sortField = field.name
                    randomSeed += 1
                } else if (activeSort == field && field.supportsOrder) {
                    sortDescending = !sortDescending
                } else {
                    sortField = field.name
                    sortDescending = field.defaultDescending
                }
                showSortMenu = false
            },
            landscapePosters = landscapePosters,
            onToggleLayout = { landscapePosters = !landscapePosters },
            filtersActive = filtersActive,
            onFilterClick = { showFilterSheet = true },
            shuffleEnabled = displayItems.isNotEmpty(),
            onShuffleClick = {
                displayItems.randomOrNull()?.let(onPlayClick)
            },
            itemCount = displayItems.size
        )

        if (displayItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.person_works_empty),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (landscapePosters) 2 else 3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 4.dp,
                    bottom = 28.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(
                    items = displayItems,
                    key = { index, item ->
                        "${item.id ?: "${item.name}-${item.type}-${item.productionYear}"}_$index"
                    }
                ) { _, item ->
                    PersonWorksCard(
                        item = item,
                        landscape = landscapePosters,
                        mediaRepository = mediaRepository,
                        onClick = { item.id?.let(onItemClick) }
                    )
                }
            }
        }
    }

    if (showFilterSheet) {
        PersonWorksFilterSheet(
            playStatus = activePlayStatus,
            favoritesOnly = favoritesOnly,
            selectedType = selectedType,
            selectedYear = selectedYear,
            typeOptions = typeOptions,
            yearOptions = yearOptions,
            onPlayStatusChange = { playStatus = it.name },
            onFavoritesOnlyChange = { favoritesOnly = it },
            onTypeChange = { selectedType = it },
            onYearChange = { selectedYear = it },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
private fun PersonWorksToolbar(
    sortLabel: String,
    sortMenuExpanded: Boolean,
    onSortClick: () -> Unit,
    onDismissSort: () -> Unit,
    selectedSort: PersonWorksSort,
    sortDescending: Boolean,
    onSortSelected: (PersonWorksSort) -> Unit,
    landscapePosters: Boolean,
    onToggleLayout: () -> Unit,
    filtersActive: Boolean,
    onFilterClick: () -> Unit,
    shuffleEnabled: Boolean,
    onShuffleClick: () -> Unit,
    itemCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSortClick)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Sort,
                    contentDescription = stringResource(R.string.view_all_sort),
                    tint = ToolbarIconTint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = sortLabel,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = onDismissSort,
                containerColor = Color(0xE61C1C1E),
                shape = RoundedCornerShape(12.dp)
            ) {
                PersonWorksSort.entries.forEach { field ->
                    val selected = field == selectedSort
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = personWorksSortMenuLabel(
                                    sort = field,
                                    selected = selected,
                                    descending = sortDescending
                                ),
                                color = if (selected) AccentBlue else Color.White,
                                fontSize = 15.sp
                            )
                        },
                        onClick = { onSortSelected(field) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = onToggleLayout) {
            Icon(
                imageVector = Icons.Rounded.GridView,
                contentDescription = stringResource(R.string.person_works_poster_layout),
                tint = if (landscapePosters) AccentBlue else ToolbarIconTint
            )
        }
        IconButton(onClick = onFilterClick) {
            Icon(
                imageVector = Icons.Rounded.FilterAlt,
                contentDescription = stringResource(R.string.person_works_filter),
                tint = if (filtersActive) AccentBlue else ToolbarIconTint
            )
        }
        IconButton(
            onClick = onShuffleClick,
            enabled = shuffleEnabled
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = stringResource(R.string.person_works_shuffle),
                tint = if (shuffleEnabled) ToolbarIconTint else ToolbarIconTint.copy(alpha = 0.35f)
            )
        }
        Text(
            text = stringResource(R.string.person_works_items, itemCount),
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonWorksFilterSheet(
    playStatus: PlayStatusFilter,
    favoritesOnly: Boolean,
    selectedType: String?,
    selectedYear: Int?,
    typeOptions: List<String>,
    yearOptions: List<Int>,
    onPlayStatusChange: (PlayStatusFilter) -> Unit,
    onFavoritesOnlyChange: (Boolean) -> Unit,
    onTypeChange: (String?) -> Unit,
    onYearChange: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val anyLabel = stringResource(R.string.person_works_any)
    var playStatusMenu by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    var yearMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.person_works_filter),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 18.dp)
            )

            FilterSelectRow(
                label = stringResource(R.string.person_works_play_status),
                value = playStatusLabel(playStatus),
                expanded = playStatusMenu,
                onExpand = { playStatusMenu = true },
                onDismiss = { playStatusMenu = false }
            ) {
                PlayStatusFilter.entries.forEach { status ->
                    DropdownMenuItem(
                        text = {
                            Text(playStatusLabel(status), color = Color.White)
                        },
                        onClick = {
                            onPlayStatusChange(status)
                            playStatusMenu = false
                        }
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = Color.White.copy(alpha = 0.08f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFavoritesOnlyChange(!favoritesOnly) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.favorites),
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                Checkbox(
                    checked = favoritesOnly,
                    onCheckedChange = onFavoritesOnlyChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = AccentBlue,
                        uncheckedColor = Color.White.copy(alpha = 0.55f)
                    )
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = Color.White.copy(alpha = 0.08f)
            )

            FilterSelectRow(
                label = stringResource(R.string.person_works_type),
                value = selectedType?.let { personWorksTypeLabel(it) } ?: anyLabel,
                expanded = typeMenu,
                onExpand = { typeMenu = true },
                onDismiss = { typeMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(anyLabel, color = Color.White) },
                    onClick = {
                        onTypeChange(null)
                        typeMenu = false
                    }
                )
                typeOptions.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(personWorksTypeLabel(type), color = Color.White) },
                        onClick = {
                            onTypeChange(type)
                            typeMenu = false
                        }
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = Color.White.copy(alpha = 0.08f)
            )

            FilterSelectRow(
                label = stringResource(R.string.person_works_year),
                value = selectedYear?.toString() ?: anyLabel,
                expanded = yearMenu,
                onExpand = { yearMenu = true },
                onDismiss = { yearMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(anyLabel, color = Color.White) },
                    onClick = {
                        onYearChange(null)
                        yearMenu = false
                    }
                )
                yearOptions.forEach { year ->
                    DropdownMenuItem(
                        text = { Text(year.toString(), color = Color.White) },
                        onClick = {
                            onYearChange(year)
                            yearMenu = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSelectRow(
    label: String,
    value: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    menuContent: @Composable () -> Unit
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 15.sp
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            containerColor = Color(0xFF2A2A2E)
        ) {
            menuContent()
        }
    }
}

@Composable
private fun PersonWorksCard(
    item: BaseItemDto,
    landscape: Boolean,
    mediaRepository: MediaRepository,
    onClick: () -> Unit
) {
    val subtitle = when {
        item.type.equals("Episode", ignoreCase = true) -> {
            val season = item.parentIndexNumber
            val episode = item.indexNumber
            val code = if (season != null && episode != null) "S${season}E${episode}" else null
            listOfNotNull(item.seriesName, code, item.productionYear?.toString())
                .joinToString(" · ")
                .ifBlank { null }
        }
        else -> item.productionYear?.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = !item.id.isNullOrBlank(),
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF242734)),
            contentAlignment = Alignment.Center
        ) {
            ImageLoader(
                itemId = item.id,
                seriesId = item.seriesId,
                imageType = if (landscape) "Thumb" else "Primary",
                fallbackImageType = if (landscape) "Backdrop" else null,
                extraFallbackImageTypes = if (landscape) listOf("Primary") else emptyList(),
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                cornerRadius = 12,
                crossfadeMillis = 0,
                mediaRepository = mediaRepository,
                imageMetadata = item,
                itemType = item.type
            )
            if (item.id.isNullOrBlank()) {
                Icon(
                    imageVector = if (item.type.equals("Series", ignoreCase = true)) {
                        Icons.Rounded.Tv
                    } else {
                        Icons.Rounded.Movie
                    },
                    contentDescription = item.name,
                    tint = Color.White.copy(alpha = 0.42f),
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Text(
            text = item.name ?: stringResource(R.string.detail_person_unknown),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )

        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, start = 2.dp, end = 2.dp)
            )
        }
    }
}

@Composable
private fun personWorksSortLabel(
    sort: PersonWorksSort,
    descending: Boolean
): String {
    val name = stringResource(sort.labelRes)
    if (!sort.supportsOrder) return name
    val arrow = if (descending) " ↓" else " ↑"
    return name + arrow
}

@Composable
private fun personWorksSortMenuLabel(
    sort: PersonWorksSort,
    selected: Boolean,
    descending: Boolean
): String {
    val name = stringResource(sort.labelRes)
    if (!selected || !sort.supportsOrder) return name
    return name + if (descending) " ↓" else " ↑"
}

@Composable
private fun playStatusLabel(status: PlayStatusFilter): String {
    return stringResource(
        when (status) {
            PlayStatusFilter.Any -> R.string.person_works_any
            PlayStatusFilter.Played -> R.string.person_works_played
            PlayStatusFilter.Unplayed -> R.string.person_works_unplayed
        }
    )
}

@Composable
private fun personWorksTypeLabel(type: String): String {
    return when {
        type.equals("Movie", ignoreCase = true) -> stringResource(R.string.movies)
        type.equals("Series", ignoreCase = true) -> stringResource(R.string.tv_shows)
        type.equals("Episode", ignoreCase = true) -> stringResource(R.string.person_works_episodes)
        else -> type
    }
}

private fun personWorksComparator(
    sort: PersonWorksSort,
    descending: Boolean
): Comparator<BaseItemDto> {
    val direction = if (descending) -1 else 1
    return Comparator { left, right ->
        val compared = when (sort) {
            PersonWorksSort.DateCreated -> compareValues(left.dateCreated, right.dateCreated)
            PersonWorksSort.SortName -> compareValues(
                (left.sortName ?: left.name).orEmpty().lowercase(),
                (right.sortName ?: right.name).orEmpty().lowercase()
            )
            PersonWorksSort.CommunityRating -> compareValues(left.communityRating, right.communityRating)
            PersonWorksSort.CriticRating -> compareValues(left.criticRating, right.criticRating)
            PersonWorksSort.ProductionYear -> compareValues(left.productionYear, right.productionYear)
            PersonWorksSort.PremiereDate -> compareValues(left.premiereDate, right.premiereDate)
            PersonWorksSort.OfficialRating -> compareValues(left.officialRating, right.officialRating)
            PersonWorksSort.DatePlayed -> compareValues(
                left.userData?.lastPlayedDate,
                right.userData?.lastPlayedDate
            )
            PersonWorksSort.Runtime -> compareValues(left.runTimeTicks, right.runTimeTicks)
            PersonWorksSort.Bitrate -> compareValues(left.maxBitrate(), right.maxBitrate())
            PersonWorksSort.Size -> compareValues(left.maxSize(), right.maxSize())
            PersonWorksSort.Random -> 0
        }
        if (compared != 0) {
            compared * direction
        } else {
            compareValues(left.sortName ?: left.name, right.sortName ?: right.name)
        }
    }
}

private fun BaseItemDto.maxBitrate(): Int? {
    return mediaSources?.mapNotNull { it.bitrate }?.maxOrNull()
}

private fun BaseItemDto.maxSize(): Long? {
    return mediaSources?.mapNotNull { it.size }?.maxOrNull()
}
