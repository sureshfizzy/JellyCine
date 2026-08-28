package com.jellycine.app.ui.screens.screentime

import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.jellycine.data.model.BaseItemDto
import com.jellycine.shared.R
import com.jellycine.shared.ui.components.common.ShimmerEffect
import com.jellycine.shared.ui.components.screentime.DailyBreakdownChart
import com.jellycine.shared.ui.components.screentime.PeakHoursChart
import com.jellycine.shared.ui.components.screentime.SectionCard
import com.jellycine.shared.ui.components.screentime.StatCard
import com.jellycine.shared.ui.components.screentime.YearNavigator
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

    val movieCount = remember(state.items) { state.items.count { it.type == "Movie" } }
    val episodeCount = remember(state.items) { state.items.count { it.type == "Episode" } }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DateRangeSubtitle(period = state.period, year = state.year)
            }
            item {
                PeriodSelector(
                    selected = state.period,
                    onSelect = { viewModel.loadScreenTime(it, state.year) }
                )
            }

            if (state.period == ScreenTimePeriod.YEAR) {
                item {
                    YearNavigator(
                        year = state.year,
                        onPreviousYear = { viewModel.loadScreenTime(state.period, state.year - 1) },
                        onNextYear = { viewModel.loadScreenTime(state.period, state.year + 1) }
                    )
                }
            }

            if (state.isLoading) {
                item { LoadingShimmer() }
            } else if (state.stats != null) {
                val stats = state.stats!!

                item {
                    SectionCard {
                        DailyBreakdownChart(dailyData = stats.dailyBreakdown)
                    }
                }

                item {
                    SectionCard {
                        PeakHoursChart(peakHours = stats.peakHours)
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(
                            label = stringResource(R.string.screen_time_total),
                            value = formatMinutes(stats.totalMinutes),
                            delta = formatDelta(stats.totalDelta),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = stringResource(R.string.screen_time_daily_average),
                            value = formatMinutes(stats.dailyAverageMinutes),
                            delta = formatDelta(stats.dailyAverageDelta),
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
                            value = formatMinutes(stats.showMinutes),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = stringResource(R.string.screen_time_movies),
                            value = formatMinutes(stats.movieMinutes),
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

                    val filteredItems = state.items.filter { it.type == selectedItemType }
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
private fun DateRangeSubtitle(period: ScreenTimePeriod, year: Int) {
    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

    val text = when (period) {
        ScreenTimePeriod.WEEK -> "${today.minusDays(6).format(formatter)}  –  Today"
        ScreenTimePeriod.MONTH -> "${today.minusDays(29).format(formatter)}  –  Today"
        ScreenTimePeriod.YEAR -> if (year == today.year) "$year  –  Today" else year.toString()
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.5f)
    )
}

@Composable
private fun LoadingShimmer() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
        ) { ShimmerEffect(modifier = Modifier.fillMaxSize()) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(16.dp))
        ) { ShimmerEffect(modifier = Modifier.fillMaxSize()) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) { ShimmerEffect(modifier = Modifier.fillMaxSize()) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) { ShimmerEffect(modifier = Modifier.fillMaxSize()) }
        }
    }
}

@Composable
private fun WatchedItemRow(item: BaseItemDto, viewModel: ScreenTimeViewModel) {
    val runtime = item.runTimeTicks?.let { it / 600_000_000L } ?: 0L
    val watchedDate = remember(item.userData?.lastPlayedDate, item.dateCreated) {
        parseWatchedDate(item.userData?.lastPlayedDate ?: item.dateCreated)
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


private fun parseWatchedDate(dateStr: String?): String? {
    if (dateStr.isNullOrBlank()) return null
    return try {
        val instant = java.time.Instant.parse(dateStr)
        val date = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    } catch (_: Exception) {
        null
    }
}
