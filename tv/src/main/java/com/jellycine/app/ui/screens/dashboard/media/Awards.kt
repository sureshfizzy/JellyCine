package com.jellycine.app.ui.screens.dashboard.media

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jellycine.data.model.AwardDefinition
import com.jellycine.data.model.AwardRefsState
import com.jellycine.data.model.AwardRow
import com.jellycine.data.model.AwardTitleRef
import com.jellycine.data.model.AwardsCatalog
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.SeerrRecommendationTitle
import com.jellycine.data.model.toSeerDetailItem
import com.jellycine.data.repository.AwardsRepository

internal enum class DiscoverFeed { RECOMMENDATIONS, WATCHED, AWARDS }

private const val ACCENT = 0xFFE86E2F
private const val GRID_HYDRATE_LIMIT = 80

private sealed interface AwardView {
    data object Browse : AwardView
    data class CategoryAll(val rowKey: String, val decade: Int?) : AwardView
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
                onOpenCategory = { row -> push(AwardView.CategoryAll(row.key, null)) },
                onItemClick = onItemClick
            )

            is AwardView.Decade -> DecadeScreen(
                state = state,
                decade = view.decade,
                awardsRepository = awardsRepository,
                onOpenYear = { push(AwardView.Year(it)) },
                onOpenCategory = { row -> push(AwardView.CategoryAll(row.key, view.decade)) },
                onItemClick = onItemClick
            )

            is AwardView.Year -> YearScreen(
                state = state,
                year = view.year,
                awardsRepository = awardsRepository,
                onOpenCategory = { row -> push(AwardView.CategoryAll(row.key, null)) },
                onItemClick = onItemClick
            )

            is AwardView.CategoryAll -> {
                val row = state?.rows?.firstOrNull { it.key == view.rowKey }
                if (row == null) {
                    LoadingBox()
                } else {
                    CategoryAllScreen(
                        row = row,
                        decade = view.decade,
                        awardsRepository = awardsRepository,
                        onItemClick = onItemClick
                    )
                }
            }
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
    onOpenCategory: (AwardRow) -> Unit,
    onItemClick: (BaseItemDto) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        PillRow {
            items(items = awards, key = { it.id }) { award ->
                Pill(award.shortLabel, award.id == selectedId) { onSelectAward(award.id) }
            }
        }

        when {
            state == null -> LoadingBox()
            state.rows.isEmpty() -> AwardsNotice("No award data available right now.")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 110.dp)
            ) {
                if (state.decades.isNotEmpty()) {
                    item(key = "decades") {
                        NavPillSection(
                            title = "Browse by Decade",
                            labels = state.decades.map { "${it}s" to it },
                            onClick = onOpenDecade
                        )
                    }
                }
                items(items = state.rows, key = { it.key }) { row ->
                    CategoryRail(
                        title = row.title,
                        refs = row.refs,
                        awardsRepository = awardsRepository,
                        onViewAll = { onOpenCategory(row) },
                        onItemClick = onItemClick
                    )
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
    onOpenCategory: (AwardRow) -> Unit,
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 110.dp)
    ) {
        if (years.isNotEmpty()) {
            item(key = "years") {
                NavPillSection(
                    title = "Browse by Year",
                    labels = years.map { it.toString() to it },
                    onClick = onOpenYear
                )
            }
        }
        items(items = decadeRows, key = { it.key }) { row ->
            CategoryRail(
                title = row.title,
                refs = row.refs,
                awardsRepository = awardsRepository,
                onViewAll = { onOpenCategory(row) },
                onItemClick = onItemClick
            )
        }
    }
}

@Composable
private fun YearScreen(
    state: AwardRefsState?,
    year: Int,
    awardsRepository: AwardsRepository,
    onOpenCategory: (AwardRow) -> Unit,
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 110.dp)
    ) {
        items(items = yearRows, key = { it.key }) { row ->
            CategoryRail(
                title = row.title,
                refs = row.refs,
                awardsRepository = awardsRepository,
                onViewAll = { onOpenCategory(row) },
                onItemClick = onItemClick
            )
        }
    }
}

@Composable
private fun CategoryAllScreen(
    row: AwardRow,
    decade: Int?,
    awardsRepository: AwardsRepository,
    onItemClick: (BaseItemDto) -> Unit
) {
    val refs = remember(row.key, decade) {
        if (decade == null) row.refs
        else row.refs.filter { it.year?.let { y -> y in decade..(decade + 9) } == true }
    }
    val items by produceState<List<SeerrRecommendationTitle>?>(initialValue = null, key1 = row.key, key2 = decade) {
        value = awardsRepository.hydrate(refs, limit = GRID_HYDRATE_LIMIT)
    }
    TitleGrid(items = items, onItemClick = onItemClick)
}

@Composable
private fun CategoryRail(
    title: String,
    refs: List<AwardTitleRef>,
    awardsRepository: AwardsRepository,
    onViewAll: () -> Unit,
    onItemClick: (BaseItemDto) -> Unit
) {
    if (refs.isEmpty()) return

    val items by produceState<List<SeerrRecommendationTitle>?>(initialValue = null, key1 = title, key2 = refs) {
        value = null
        value = awardsRepository.hydrate(refs)
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
                .clickable(onClick = onViewAll)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
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
                    AwardTvCard(item = item, onClick = { onItemClick(item.toAwardClickItem()) })
                }
            }
        }
    }
}

@Composable
private fun TitleGrid(
    items: List<SeerrRecommendationTitle>?,
    onItemClick: (BaseItemDto) -> Unit
) {
    if (items == null) {
        LoadingBox(); return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp)
    ) {
        gridItems(
            items = items,
            key = { item -> "${item.mediaType}:${item.tmdbId}:${item.roleLabel ?: ""}" }
        ) { item ->
            AwardTvCard(item = item, onClick = { onItemClick(item.toAwardClickItem()) })
        }
    }
}

@Composable
private fun AwardTvCard(
    item: SeerrRecommendationTitle,
    onClick: () -> Unit
) {
    val posterUrl = remember(item.posterUrl) { item.posterUrl?.takeIf { it.isNotBlank() } }
    val cardShape = RoundedCornerShape(10.dp)

    Column(modifier = Modifier.width(116.dp).clickable(onClick = onClick)) {
        Card(
            modifier = Modifier.fillMaxWidth().height(166.dp),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = Color(0xFF23232A))
        ) {
            Box(modifier = Modifier.fillMaxSize().clip(cardShape)) {
                if (posterUrl != null) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = item.title,
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }

        Text(
            text = item.title,
            fontSize = 12.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp)
        )
        item.productionYear?.let { year ->
            Text(
                text = year.toString(),
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1
            )
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
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color(ACCENT), strokeWidth = 2.dp, modifier = Modifier.padding(4.dp))
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
        is AwardView.CategoryAll -> {
            val row = state?.rows?.firstOrNull { it.key == view.rowKey }
            if (row != null) "${award.shortLabel}: ${row.categoryLabel}" else award.shortLabel
        }
    }
}