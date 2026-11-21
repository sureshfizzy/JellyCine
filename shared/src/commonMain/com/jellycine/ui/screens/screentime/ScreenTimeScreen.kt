package com.jellycine.ui.screens.screentime

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jellycine.data.model.*
import com.jellycine.data.repository.*
import com.jellycine.resources.*
import com.jellycine.ui.components.common.ShimmerEffect
import com.jellycine.ui.components.common.containerWidthDp
import com.jellycine.ui.components.common.isTabletLayout
import com.jellycine.ui.components.screentime.*
import kotlinx.datetime.*
import org.jetbrains.compose.resources.stringResource

private const val TYPE_MOVIE = "Movie"
private const val TYPE_EPISODE = "Episode"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ScreenTimeScreen(
    viewModel: ScreenTimeViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.screenTimeState.collectAsState()
    var selectedItemType by remember { mutableStateOf(TYPE_MOVIE) }
    var dayFilter by remember { mutableStateOf<Int?>(null) }
    var hourFilter by remember { mutableStateOf<Int?>(null) }

    val timeFilteredItems = remember(state.items, dayFilter, hourFilter, state.stats?.dailyBreakdown) {
        var items = state.items
        dayFilter?.let { items = itemsInDayBucket(items, it, state.stats?.dailyBreakdown.orEmpty()) }
        hourFilter?.let { index ->
            val range = hourRanges.getOrNull(index)
            if (range != null) {
                items = items.filter { item -> parseItemHour(item.screenTimeDateString())?.let { it in range } == true }
            }
        }
        items
    }
    val movieCount = remember(timeFilteredItems) { timeFilteredItems.count { it.type == TYPE_MOVIE } }
    val episodeCount = remember(timeFilteredItems) { timeFilteredItems.count { it.type == TYPE_EPISODE } }
    if (selectedItemType == TYPE_MOVIE && movieCount == 0 && episodeCount > 0) selectedItemType = TYPE_EPISODE
    else if (selectedItemType == TYPE_EPISODE && episodeCount == 0 && movieCount > 0) selectedItemType = TYPE_MOVIE

    BackHandler { onBack() }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.settings_screen_time)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        val screenWidth = containerWidthDp()
        val isTablet = isTabletLayout(screenWidth)
        val horizontalPadding = if (isTablet) 32.dp else 16.dp

        Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .then(if (isTablet) Modifier.widthIn(max = 720.dp) else Modifier.fillMaxWidth())
                .padding(horizontal = horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DateRangeSubtitle(
                    period = state.period,
                    year = state.year,
                    monthOffset = state.monthOffset,
                    weekOffset = state.weekOffset,
                    onPrevious = {
                        when (state.period) {
                            ScreenTimePeriod.WEEK -> viewModel.loadScreenTime(state.period, state.year, weekOffset = state.weekOffset - 1)
                            ScreenTimePeriod.MONTH -> viewModel.loadScreenTime(state.period, state.year, state.monthOffset - 1)
                            ScreenTimePeriod.YEAR -> viewModel.loadScreenTime(state.period, state.year - 1)
                        }
                    },
                    onNext = {
                        when (state.period) {
                            ScreenTimePeriod.WEEK -> viewModel.loadScreenTime(state.period, state.year, weekOffset = state.weekOffset + 1)
                            ScreenTimePeriod.MONTH -> viewModel.loadScreenTime(state.period, state.year, state.monthOffset + 1)
                            ScreenTimePeriod.YEAR -> viewModel.loadScreenTime(state.period, state.year + 1)
                        }
                    }
                )
            }
            item {
                PeriodSelector(
                    selected = state.period,
                    onSelect = { dayFilter = null; hourFilter = null; viewModel.loadScreenTime(it, state.year) }
                )
            }

            if (state.isLoading) {
                item { LoadingShimmer() }
            } else if (state.stats != null) {
                val stats = state.stats!!

                val peakHoursData = dayFilter?.let {
                    computePeakHoursFromItems(itemsInDayBucket(state.items, it, stats.dailyBreakdown))
                } ?: stats.peakHours

                if (isTablet) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionCard(modifier = Modifier.weight(1f)) {
                                DailyBreakdownChart(
                                    dailyData = stats.dailyBreakdown,
                                    selectedIndex = dayFilter,
                                    onBucketClick = { index -> dayFilter = if (dayFilter == index) null else index },
                                    onClearSelection = { dayFilter = null }
                                )
                            }
                            SectionCard(modifier = Modifier.weight(1f)) {
                                PeakHoursChart(
                                    peakHours = peakHoursData,
                                    selectedIndex = hourFilter,
                                    onBucketClick = { index -> hourFilter = if (hourFilter == index) null else index },
                                    onClearSelection = { hourFilter = null }
                                )
                            }
                        }
                    }
                } else {
                    item {
                        SectionCard {
                            DailyBreakdownChart(
                                dailyData = stats.dailyBreakdown,
                                selectedIndex = dayFilter,
                                onBucketClick = { index -> dayFilter = if (dayFilter == index) null else index },
                                onClearSelection = { dayFilter = null }
                            )
                        }
                    }
                    item {
                        SectionCard {
                            PeakHoursChart(
                                peakHours = peakHoursData,
                                selectedIndex = hourFilter,
                                onBucketClick = { index -> hourFilter = if (hourFilter == index) null else index },
                                onClearSelection = { hourFilter = null }
                            )
                        }
                    }
                }

                val hasFilter = dayFilter != null || hourFilter != null
                val filteredTotal = if (hasFilter) timeFilteredItems.sumOf { it.runMinutes() } else stats.totalMinutes
                val filteredShows = if (hasFilter) timeFilteredItems.filter { it.type == TYPE_EPISODE }.sumOf { it.runMinutes() } else stats.showMinutes
                val filteredMovies = if (hasFilter) timeFilteredItems.filter { it.type == TYPE_MOVIE }.sumOf { it.runMinutes() } else stats.movieMinutes
                val filteredAvg = if (hasFilter) {
                    val days = timeFilteredItems.mapNotNull { parseItemDate(it.screenTimeDateString()) }.distinct().size
                    if (days > 0) filteredTotal / days else filteredTotal
                } else stats.dailyAverageMinutes

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(
                            label = stringResource(Res.string.screen_time_total),
                            value = formatMinutes(filteredTotal),
                            delta = if (hasFilter) null else formatDelta(stats.totalDelta),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = stringResource(Res.string.screen_time_daily_average),
                            value = formatMinutes(filteredAvg),
                            delta = if (hasFilter) null else formatDelta(stats.dailyAverageDelta),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(
                            label = stringResource(Res.string.screen_time_shows),
                            value = formatMinutes(filteredShows),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = stringResource(Res.string.screen_time_movies),
                            value = formatMinutes(filteredMovies),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (state.items.isNotEmpty()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (movieCount > 0) ScreenTimeChip("${stringResource(Res.string.screen_time_movies)} ($movieCount)", selectedItemType == TYPE_MOVIE) { selectedItemType = TYPE_MOVIE }
                            if (episodeCount > 0) ScreenTimeChip("${stringResource(Res.string.screen_time_episodes)} ($episodeCount)", selectedItemType == TYPE_EPISODE) { selectedItemType = TYPE_EPISODE }
                        }
                    }

                    val filteredItems = timeFilteredItems.filter { it.type == selectedItemType }
                    items(filteredItems, key = { it.id ?: it.hashCode().toString() }) { item ->
                        WatchedItemRow(item, viewModel)
                    }
                }

            } else if (state.error != null) {
                item {
                    Text(
                        text = state.error!!,
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenTimeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF22D3EE).copy(alpha = 0.2f),
            selectedLabelColor = Color(0xFF67E8F9),
            containerColor = Color(0xFF1A1A1A),
            labelColor = Color.White.copy(alpha = 0.7f)
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Color.White.copy(alpha = 0.1f),
            selectedBorderColor = Color(0xFF22D3EE).copy(alpha = 0.5f),
            enabled = true,
            selected = selected
        )
    )
}

@Composable
private fun PeriodSelector(selected: ScreenTimePeriod, onSelect: (ScreenTimePeriod) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenTimePeriod.entries.forEach { period ->
            val label = when (period) {
                ScreenTimePeriod.WEEK -> stringResource(Res.string.screen_time_period_week)
                ScreenTimePeriod.MONTH -> stringResource(Res.string.screen_time_period_month)
                ScreenTimePeriod.YEAR -> stringResource(Res.string.screen_time_period_year)
            }
            ScreenTimeChip(label, selected == period) { onSelect(period) }
        }
    }
}

@Composable
private fun DateRangeSubtitle(
    period: ScreenTimePeriod,
    year: Int,
    monthOffset: Int = 0,
    weekOffset: Int = 0,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {}
) {
    val today = currentLocalDate()
    val todayLabel = stringResource(Res.string.screen_time_today)
    val (rangeStart, rangeEnd) = resolveRange(period, today, year, monthOffset, weekOffset)

    val text = when (period) {
        ScreenTimePeriod.WEEK, ScreenTimePeriod.MONTH -> {
            val isCurrent = if (period == ScreenTimePeriod.WEEK) weekOffset == 0 else monthOffset == 0
            "${formatFullDate(rangeStart)}  –  ${if (isCurrent) todayLabel else formatFullDate(rangeEnd)}"
        }
        ScreenTimePeriod.YEAR -> if (year == today.year) "$year – $todayLabel" else year.toString()
    }
    val canGoNext = when (period) {
        ScreenTimePeriod.WEEK -> weekOffset < 0
        ScreenTimePeriod.MONTH -> monthOffset < 0
        ScreenTimePeriod.YEAR -> year < today.year
    }
    val canGoPrevious = when (period) {
        ScreenTimePeriod.WEEK -> {
            val currentWeekStart = weekStartOf(today)
            val monthFirstWeekStart = weekStartOf(LocalDate(today.year, today.month, 1))
            val minWeekOffset = -(monthFirstWeekStart.daysUntil(currentWeekStart) / 7)
            weekOffset > minWeekOffset
        }
        else -> true
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = if (canGoPrevious) Color.White else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp).clickable(enabled = canGoPrevious) { onPrevious() })
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = if (canGoNext) Color.White else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp).clickable(enabled = canGoNext) { onNext() })
    }
}

@Composable
private fun LoadingShimmer() {
    val shape = RoundedCornerShape(16.dp)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(200, 140).forEach { h ->
            Box(Modifier.fillMaxWidth().height(h.dp).clip(shape)) { ShimmerEffect(Modifier.fillMaxSize()) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { Box(Modifier.weight(1f).height(100.dp).clip(shape)) { ShimmerEffect(Modifier.fillMaxSize()) } }
        }
    }
}

@Composable
private fun WatchedItemRow(item: BaseItemDto, viewModel: ScreenTimeViewModel) {
    val runtime = item.runMinutes()
    val watchedDate = remember(item.userData?.lastPlayedDate) {
        parseWatchedDate(item.userData?.lastPlayedDate)
    }

    var posterUrl by remember(item.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.id) {
        val itemId = item.id ?: return@LaunchedEffect
        posterUrl = viewModel.getItemImageUrl(itemId, item.seriesId, item.type)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A1A))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = posterUrl,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 50.dp, height = 72.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            val title = if (item.type == TYPE_EPISODE) {
                val ep = buildString {
                    item.parentIndexNumber?.let { append("S$it") }
                    item.indexNumber?.let { append("E$it") }
                }
                listOfNotNull(item.seriesName, ep.ifEmpty { null }).joinToString(" · ").ifEmpty { item.name ?: "" }
            } else item.name ?: ""
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1)
            if (item.type == TYPE_EPISODE && !item.name.isNullOrBlank()) {
                Text(item.name!!, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
            }
            val meta = listOfNotNull(if (runtime > 0) formatMinutes(runtime) else null, watchedDate).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
        }
    }
}


private val hourRanges = listOf(6..11, 12..17, 18..23, 0..5)

private fun parseWatchedDate(dateStr: String?): String? =
    parseScreenTimeDateTime(dateStr)?.let(::formatWatchedDateTime)

private fun parseItemDate(dateStr: String?) = parseScreenTimeDateTime(dateStr)?.date

private fun parseItemHour(dateStr: String?) = parseScreenTimeDateTime(dateStr)?.hour

private fun itemsInDayBucket(
    items: List<BaseItemDto>,
    bucketIndex: Int,
    dailyBreakdown: List<DailyWatchTime>
): List<BaseItemDto> {
    val epochDays = getDaysForBucket(bucketIndex, dailyBreakdown)
    return items.filter { item ->
        parseItemDate(item.screenTimeDateString())?.toEpochDays()?.toLong()?.let { it in epochDays } == true
    }
}

private fun getDaysForBucket(
    bucketIndex: Int,
    dailyBreakdown: List<DailyWatchTime>
): Set<Long> {
    if (dailyBreakdown.isEmpty()) return emptySet()
    fun dateOf(day: DailyWatchTime) = LocalDate.fromEpochDays(day.dateEpochDay.toInt())
    val grouped = when {
        dailyBreakdown.size <= 7 -> return dailyBreakdown.getOrNull(bucketIndex)?.let { setOf(it.dateEpochDay) } ?: emptySet()
        dailyBreakdown.size <= 31 ->
            dailyBreakdown.groupBy { weekOfMonth(dateOf(it)) }.entries.sortedBy { it.key }.map { it.value }
        else ->
            dailyBreakdown.groupBy { dateOf(it).month }.entries.sortedBy { it.key }.map { it.value }
    }
    return grouped.getOrNull(bucketIndex)?.map { it.dateEpochDay }?.toSet() ?: emptySet()
}

private val peakLabels = arrayOf("Morning", "Afternoon", "Evening", "Night")

private fun computePeakHoursFromItems(items: List<BaseItemDto>): List<PeakHourBucket> {
    val hours = items.mapNotNull { parseItemHour(it.screenTimeDateString()) }
    return hourRanges.mapIndexed { i, range -> PeakHourBucket(peakLabels[i], hours.count { it in range }) }
}
