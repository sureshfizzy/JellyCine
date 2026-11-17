package com.jellycine.ui.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.data.model.SeerrItemIds
import com.jellycine.data.model.SeerrRecommendationTitle

fun LazyListScope.seerTitleItems(
    items: List<SeerrRecommendationTitle>,
    onItemClick: (String) -> Unit
) {
    itemsIndexed(
        items = items,
        key = { index, item ->
            val itemId = item.jellyfinMediaId?.takeIf { it.isNotBlank() }
                ?: SeerrItemIds.detailId(tmdbId = item.tmdbId, mediaType = item.mediaType)
            "${itemId}_$index"
        }
    ) { _, item ->
        SeerTitleCard(
            item = item,
            onClick = {
                val itemId = item.jellyfinMediaId?.takeIf { it.isNotBlank() }
                    ?: SeerrItemIds.detailId(tmdbId = item.tmdbId, mediaType = item.mediaType)
                onItemClick(itemId)
            }
        )
    }
}

@Composable
fun SeerTitlesRow(
    title: String,
    items: List<SeerrRecommendationTitle>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 24.dp,
    verticalSpacing: Dp = 12.dp,
    horizontalPadding: Dp = 0.dp,
    titleFontSize: TextUnit = 21.sp
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier.padding(top = topPadding),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        Text(
            text = title,
            fontSize = titleFontSize,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = horizontalPadding)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = horizontalPadding)
        ) {
            seerTitleItems(
                items = items,
                onItemClick = onItemClick
            )
        }
    }
}
