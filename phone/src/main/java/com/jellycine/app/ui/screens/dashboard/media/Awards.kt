package com.jellycine.app.ui.screens.dashboard.media

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.ui.components.common.SeerTitleCard
import com.jellycine.data.model.AwardDefinition
import com.jellycine.data.model.AwardMode
import com.jellycine.data.model.AwardRefsState
import com.jellycine.data.model.AwardRow
import com.jellycine.data.model.AwardsCatalog
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.SeerrRecommendationTitle
import com.jellycine.data.model.toSeerDetailItem
import com.jellycine.data.repository.AwardsRepository
import com.jellycine.shared.R

internal enum class DiscoverFeed { RECOMMENDATIONS, WATCHED, AWARDS }

private const val ACCENT = 0xFFE86E2F

internal val LocalAwardSeerrConnected = staticCompositionLocalOf { false }

private sealed interface AwardView {
    data object Browse : AwardView
    data class Decade(val decade: Int) : AwardView
    data class Year(val year: Int) : AwardView
}

internal fun awardHeaderTitle(award: AwardDefinition, minYear: Int?, maxYear: Int?): String {
    val range = when {
        minYear != null && maxYear != null && minYear != maxYear -> "$minYear–$maxYear"
        maxYear != null -> "$maxYear"
        else -> null
    }
    return if (range != null) "${award.shortLabel}: $range" else award.shortLabel
}

@Composable
internal fun AwardsContent(
    awardsRepository: AwardsRepository,
    onItemClick: (BaseItemDto) -> Unit,
    onViewAllCategory: (categoryQid: String, mode: AwardMode, title: String) -> Unit,
    onHeaderTitleChange: (String?) -> Unit = {},
    onBackChange: ((() -> Unit)?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val awards = remember { AwardsCatalog.awards }
    var selectedAwardId by rememberSaveable { mutableStateOf(awards.first().id) }
    val selectedAward = remember(selectedAwardId) {
        AwardsCatalog.byId(selectedAwardId) ?: awards.first()
    }

    val refsState by produceState<AwardRefsState?>(initialValue = null, key1 = selectedAwardId) {
        value = null
        value = awardsRepository.loadAwardRows(selectedAward)
    }

    val backStack = remember(selectedAwardId) { mutableStateListOf<AwardView>() }
    val current: AwardView = backStack.lastOrNull() ?: AwardView.Browse
    val push: (AwardView) -> Unit = { backStack.add(it) }
    val pop: () -> Unit = { if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex) }

    BackHandler(enabled = backStack.isNotEmpty(), onBack = pop)

    val state = refsState

    LaunchedEffect(selectedAward, current, refsState) {
        onHeaderTitleChange(headerTitleFor(selectedAward, current, state))
    }
    LaunchedEffect(backStack.size) {
        onBackChange(if (backStack.isNotEmpty()) pop else null)
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (val view = current) {
            AwardView.Browse -> BrowseScreen(
                awards = awards,
                selectedId = selectedAwardId,
                onSelectAward = { selectedAwardId = it },
                state = state,
                awardsRepository = awardsRepository,
                onOpenDecade = { push(AwardView.Decade(it)) },
                onViewAllCategory = onViewAllCategory,
                onItemClick = onItemClick
            )

            is AwardView.Decade -> DecadeScreen(
                state = state,
                decade = view.decade,
                awardsRepository = awardsRepository,
                onOpenYear = { push(AwardView.Year(it)) },
                onViewAllCategory = onViewAllCategory,
                onItemClick = onItemClick
            )

            is AwardView.Year -> YearScreen(
                state = state,
                year = view.year,
                awardsRepository = awardsRepository,
                onViewAllCategory = onViewAllCategory,
                onItemClick = onItemClick
            )
        }
    }
}

@Composable
private fun BrowseScreen(
    awards: List<AwardDefinition>,
    selectedId: String,
    onSelectAward: (String) -> Unit,
    state: AwardRefsState?,
    awardsRepository: AwardsRepository,
    onOpenDecade: (Int) -> Unit,
    onViewAllCategory: (String, AwardMode, String) -> Unit,
    onItemClick: (BaseItemDto) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PillRow {
            items(items = awards, key = { it.id }) { award ->
                Pill(award.shortLabel, award.id == selectedId) { onSelectAward(award.id) }
            }
        }

        when {
            state == null -> Column(modifier = Modifier.fillMaxSize().padding(top = 6.dp)) {
                repeat(3) { AwardRailSkeleton() }
            }
            state.rows.isEmpty() -> AwardsNotice("No award data available right now.")
            else -> AwardRailColumn(state.rows, awardsRepository, onViewAllCategory, onItemClick) {
                if (state.decades.isNotEmpty()) {
                    item(key = "decades") {
                        NavPillSection("Browse by Decade", state.decades.map { "${it}s" to it }, onOpenDecade)
                    }
                }
            }
        }
    }
}

@Composable
private fun DecadeScreen(
    state: AwardRefsState?,
    decade: Int,
    awardsRepository: AwardsRepository,
    onOpenYear: (Int) -> Unit,
    onViewAllCategory: (String, AwardMode, String) -> Unit,
    onItemClick: (BaseItemDto) -> Unit
) {
    if (state == null) {
        LoadingBox(); return
    }
    val years = remember(state, decade) {
        state.rows.flatMap { it.refs }.mapNotNull { it.year }
            .filter { it in decade..(decade + 9) }.distinct().sortedDescending()
    }
    val decadeRows = remember(state, decade) {
        state.rows
            .map { it.copy(refs = it.refs.filter { ref -> ref.year?.let { y -> y in decade..(decade + 9) } == true }) }
            .filter { it.refs.isNotEmpty() }
    }

    AwardRailColumn(decadeRows, awardsRepository, onViewAllCategory, onItemClick) {
        if (years.isNotEmpty()) {
            item(key = "years") {
                NavPillSection("Browse by Year", years.map { it.toString() to it }, onOpenYear)
            }
        }
    }
}

@Composable
private fun YearScreen(
    state: AwardRefsState?,
    year: Int,
    awardsRepository: AwardsRepository,
    onViewAllCategory: (String, AwardMode, String) -> Unit,
    onItemClick: (BaseItemDto) -> Unit
) {
    if (state == null) {
        LoadingBox(); return
    }
    val yearRows = remember(state, year) {
        state.rows
            .map { it.copy(refs = it.refs.filter { ref -> ref.year == year }) }
            .filter { it.refs.isNotEmpty() }
    }
    AwardRailColumn(yearRows, awardsRepository, onViewAllCategory, onItemClick)
}

@Composable
private fun AwardRailColumn(
    rows: List<AwardRow>,
    awardsRepository: AwardsRepository,
    onViewAllCategory: (String, AwardMode, String) -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    leading: LazyListScope.() -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 120.dp)
    ) {
        leading()
        items(items = rows, key = { it.key }) { row ->
            AwardCategoryRail(row, awardsRepository, onViewAllCategory, onItemClick)
        }
    }
}

@Composable
private fun AwardCategoryRail(
    row: AwardRow,
    awardsRepository: AwardsRepository,
    onViewAllCategory: (String, AwardMode, String) -> Unit,
    onItemClick: (BaseItemDto) -> Unit
) {
    if (row.refs.isEmpty()) return

    val showSourceBadge = LocalAwardSeerrConnected.current
    val items by produceState<List<SeerrRecommendationTitle>?>(initialValue = null, key1 = row.key, key2 = row.refs) {
        value = null
        value = awardsRepository.hydrate(row.refs)
    }

    val resolved = items
    if (resolved != null && resolved.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onViewAllCategory(row.categoryQid, row.mode, row.title) }
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = row.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.dashboard_view_all),
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.padding(start = 4.dp).size(18.dp)
            )
        }

        if (resolved == null) {
            RailLoading()
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                itemsIndexed(
                    items = resolved,
                    key = { index, item -> "${item.mediaType}:${item.tmdbId}_$index" }
                ) { _, item ->
                    SeerTitleCard(
                        item = item,
                        showSourceBadge = showSourceBadge,
                        onClick = { onItemClick(item.toAwardClickItem()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavPillSection(
    title: String,
    labels: List<Pair<String, Int>>,
    onClick: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(items = labels, key = { it.second }) { (label, value) ->
                Pill(label = label, selected = false) { onClick(value) }
            }
        }
    }
}

@Composable
private fun PillRow(content: LazyListScope.() -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        content = content
    )
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Color.White else Color.White.copy(alpha = 0.12f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color(ACCENT))
    }
}

@Composable
private fun RailLoading() {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        items(count = 5) {
            Box(modifier = Modifier.width(116.dp)) { SkeletonPosterCard() }
        }
    }
}

@Composable
private fun AwardRailSkeleton() {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        Box(
            modifier = Modifier
                .padding(start = 16.dp)
                .width(150.dp)
                .height(18.dp)
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
        )
        RailLoading()
    }
}

@Composable
private fun AwardsNotice(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text = text, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

private fun SeerrRecommendationTitle.toAwardClickItem(): BaseItemDto =
    jellyfinMediaId?.takeIf { it.isNotBlank() }?.let { BaseItemDto(id = it) } ?: toSeerDetailItem()

private fun headerTitleFor(award: AwardDefinition, view: AwardView, state: AwardRefsState?): String {
    return when (view) {
        AwardView.Browse -> awardHeaderTitle(award, state?.minYear, state?.maxYear)
        is AwardView.Decade -> {
            val end = state?.rows?.flatMap { it.refs }?.mapNotNull { it.year }
                ?.filter { it in view.decade..(view.decade + 9) }?.maxOrNull() ?: (view.decade + 9)
            "${award.shortLabel}: ${view.decade}–$end"
        }
        is AwardView.Year -> "${award.shortLabel}: ${view.year}"
    }
}