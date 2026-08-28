package com.jellycine.app.ui.screens.screentime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.jellycine.app.ui.components.common.containerWidthDp
import com.jellycine.app.ui.components.common.isTabletLayout
import com.jellycine.data.model.BaseItemDto
import com.jellycine.shared.R
import com.jellycine.shared.ui.components.common.ShimmerEffect
import com.jellycine.shared.ui.components.screentime.DailyBreakdownChart
import com.jellycine.shared.ui.components.screentime.PeakHoursChart
import com.jellycine.shared.ui.components.screentime.SectionCard
import com.jellycine.shared.ui.components.screentime.StatCard
import com.jellycine.shared.ui.components.screentime.formatDelta
import com.jellycine.shared.ui.components.screentime.formatMinutes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTimeScreen(
    onBack: () -> Unit,
    viewModel: ScreenTimeViewModel = viewModel(
        factory = ScreenTimeViewModelFactory(LocalContext.current.applicationContext)
    )
) {
    val state by viewModel.screenTimeState.collectAsState()
    var selectedItemType by remember { mutableStateOf("Movie") }
    var dayFilter by remember { mutableStateOf<Int?>(null) }
    var hourFilter by remember { mutableStateOf<Int?>(null) }

    val timeFilteredItems = remember(state.items, dayFilter, hourFilter, state.stats?.dailyBreakdown, state.period) {
        var items = state.items
        if (dayFilter != null) {
            val epochDays = getDaysForBucket(dayFilter!!, state.stats?.dailyBreakdown.orEmpty(), state.period)
            items = items.filter { item ->
                val date = parseItemDate(item.userData?.lastPlayedDate ?: item.dateCreated)
                date != null && date.toEpochDay() in epochDays
            }
        }
        if (hourFilter != null) {
            val range = hourRanges.getOrNull(hourFilter!!)
            if (range != null) {
                items = items.filter { item ->
                    val hour = parseItemHour(item.userData?.lastPlayedDate ?: item.dateCreated)
                    hour != null && hour in range
                }
            }
        }
        items
    }
    val movieCount = remember(timeFilteredItems) { timeFilteredItems.count { it.type == "Movie" } }
    val episodeCount = remember(timeFilteredItems) { timeFilteredItems.count { it.type == "Episode" } }
    if (selectedItemType == "Movie" && movieCount == 0 && episodeCount > 0) selectedItemType = "Episode"
    else if (selectedItemType == "Episode" && episodeCount == 0 && movieCount > 0) selectedItemType = "Movie"

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_screen_time)) },
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

                val peakHoursData = if (dayFilter != null) {
                    val dayItems = state.items.filter { item ->
                        val date = parseItemDate(item.userData?.lastPlayedDate ?: item.dateCreated)
                        date != null && date.toEpochDay() in getDaysForBucket(dayFilter!!, stats.dailyBreakdown, state.period)
                    }
                    computePeakHoursFromItems(dayItems)
                } else stats.peakHours

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
                val filteredTotal = if (hasFilter) timeFilteredItems.sumOf { (it.runTimeTicks ?: 0L) / 600_000_000L } else stats.totalMinutes
                val filteredShows = if (hasFilter) timeFilteredItems.filter { it.type == "Episode" }.sumOf { (it.runTimeTicks ?: 0L) / 600_000_000L } else stats.showMinutes
                val filteredMovies = if (hasFilter) timeFilteredItems.filter { it.type == "Movie" }.sumOf { (it.runTimeTicks ?: 0L) / 600_000_000L } else stats.movieMinutes
                val filteredAvg = if (hasFilter) {
                    val days = timeFilteredItems.mapNotNull { parseItemDate(it.userData?.lastPlayedDate ?: it.dateCreated) }.distinct().size
                    if (days > 0) filteredTotal / days else filteredTotal
                } else stats.dailyAverageMinutes

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(
                            label = stringResource(R.string.screen_time_total),
                            value = formatMinutes(filteredTotal),
                            delta = if (hasFilter) null else formatDelta(stats.totalDelta),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = stringResource(R.string.screen_time_daily_average),
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
                            label = stringResource(R.string.screen_time_shows),
                            value = formatMinutes(filteredShows),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = stringResource(R.string.screen_time_movies),
                            value = formatMinutes(filteredMovies),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (state.items.isNotEmpty()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (movieCount > 0) ScreenTimeChip("Movies ($movieCount)", selectedItemType == "Movie") { selectedItemType = "Movie" }
                            if (episodeCount > 0) ScreenTimeChip("Episodes ($episodeCount)", selectedItemType == "Episode") { selectedItemType = "Episode" }
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
                ScreenTimePeriod.WEEK -> stringResource(R.string.screen_time_period_week)
                ScreenTimePeriod.MONTH -> stringResource(R.string.screen_time_period_month)
                ScreenTimePeriod.YEAR -> stringResource(R.string.screen_time_period_year)
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
    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    val text = when (period) {
        ScreenTimePeriod.WEEK -> {
            val end = today.plusWeeks(weekOffset.toLong())
            val start = end.minusDays(6)
            "${start.format(formatter)}  –  ${if (weekOffset == 0) "Today" else end.format(formatter)}"
        }
        ScreenTimePeriod.MONTH -> {
            val target = today.plusMonths(monthOffset.toLong())
            val start = target.withDayOfMonth(1)
            val end = if (monthOffset == 0) today else target.withDayOfMonth(target.lengthOfMonth())
            "${start.format(formatter)}  –  ${if (monthOffset == 0) "Today" else end.format(formatter)}"
        }
        ScreenTimePeriod.YEAR -> if (year == today.year) "$year – Today" else year.toString()
    }
    val canGoNext = when (period) {
        ScreenTimePeriod.WEEK -> weekOffset < 0
        ScreenTimePeriod.MONTH -> monthOffset < 0
        ScreenTimePeriod.YEAR -> year < today.year
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp).clickable { onPrevious() })
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
    val runtime = item.runTimeTicks?.let { it / 600_000_000L } ?: 0L
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
            val title = if (item.type == "Episode") {
                val ep = buildString {
                    item.parentIndexNumber?.let { append("S$it") }
                    item.indexNumber?.let { append("E$it") }
                }
                listOfNotNull(item.seriesName, ep.ifEmpty { null }).joinToString(" · ").ifEmpty { item.name ?: "" }
            } else item.name ?: ""
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1)
            if (item.type == "Episode" && !item.name.isNullOrBlank()) {
                Text(item.name!!, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
            }
            val meta = listOfNotNull(if (runtime > 0) formatMinutes(runtime) else null, watchedDate).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
        }
    }
}


private val hourRanges = listOf(6..11, 12..17, 18..23, 0..5)

private fun parseZoned(dateStr: String?): java.time.ZonedDateTime? {
    if (dateStr.isNullOrBlank()) return null
    val zone = java.time.ZoneId.systemDefault()
    return try {
        java.time.Instant.parse(dateStr).atZone(zone)
    } catch (_: Exception) {
        try {
            java.time.ZonedDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
        } catch (_: Exception) {
            try {
                java.time.LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone)
            } catch (_: Exception) { null }
        }
    }
}

private fun parseWatchedDate(dateStr: String?): String? {
    val zdt = parseZoned(dateStr) ?: return null
    val date = zdt.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    val time = zdt.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    return "$date, $time"
}

private fun parseItemDate(dateStr: String?) = parseZoned(dateStr)?.toLocalDate()

private fun parseItemHour(dateStr: String?) = parseZoned(dateStr)?.hour

private fun getDaysForBucket(
    bucketIndex: Int,
    dailyBreakdown: List<com.jellycine.data.model.DailyWatchTime>,
    period: ScreenTimePeriod
): Set<Long> {
    if (dailyBreakdown.isEmpty()) return emptySet()
    val grouped = when {
        dailyBreakdown.size <= 7 -> return dailyBreakdown.getOrNull(bucketIndex)?.let { setOf(it.dateEpochDay) } ?: emptySet()
        dailyBreakdown.size <= 31 -> {
            val weekField = java.time.temporal.WeekFields.of(Locale.getDefault()).weekOfMonth()
            dailyBreakdown.groupBy { LocalDate.ofEpochDay(it.dateEpochDay).get(weekField) }.toSortedMap().values.toList()
        }
        else -> dailyBreakdown.groupBy { LocalDate.ofEpochDay(it.dateEpochDay).month }.entries.sortedBy { it.key }.map { it.value }
    }
    return grouped.getOrNull(bucketIndex)?.map { it.dateEpochDay }?.toSet() ?: emptySet()
}

private val peakLabels = arrayOf("Morning", "Afternoon", "Evening", "Night")

private fun computePeakHoursFromItems(items: List<BaseItemDto>): List<com.jellycine.data.model.PeakHourBucket> {
    val hours = items.mapNotNull { parseItemHour(it.userData?.lastPlayedDate ?: it.dateCreated) }
    return hourRanges.mapIndexed { i, range -> com.jellycine.data.model.PeakHourBucket(peakLabels[i], hours.count { it in range }) }
}
