package com.jellycine.shared.ui.components.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.SeerrItemIds
import com.jellycine.data.model.SeerrRecommendationTitle
import com.jellycine.data.model.SeerrRequestState
import com.jellycine.data.repository.MediaRepository
import com.jellycine.shared.R
import com.jellycine.shared.util.image.JellyfinPosterImage
import com.jellycine.shared.util.image.imageTagFor
import com.jellycine.shared.util.image.rememberImageUrl

@Composable
fun SimilarItemsSection(
    similarItems: List<BaseItemDto>,
    mediaRepository: MediaRepository,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    seerrItems: List<SeerrRecommendationTitle> = emptyList()
) {
    if (similarItems.isEmpty() && seerrItems.isEmpty()) return
    val sectionTitle = title ?: stringResource(R.string.detail_similar_items_title)

    Column(
        modifier = modifier.padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = sectionTitle,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            itemsIndexed(
                items = similarItems,
                key = { index, similarItem ->
                    "${similarItem.id ?: "${similarItem.name}-${similarItem.type}"}_$index"
                }
            ) { _, similarItem ->
                SimilarTitleCard(
                    item = similarItem,
                    mediaRepository = mediaRepository,
                    onClick = {
                        similarItem.id?.let(onItemClick)
                    }
                )
            }

            seerrTitleItems(
                items = seerrItems,
                onItemClick = onItemClick
            )
        }
    }
}

private fun LazyListScope.seerrTitleItems(
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
        SeerrRecommendationCard(
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
private fun SimilarTitleCard(
    item: BaseItemDto,
    mediaRepository: MediaRepository,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageItemId = remember(item.id, item.type, item.seriesId) {
        when {
            item.type == "Episode" && !item.seriesId.isNullOrBlank() -> item.seriesId
            else -> item.id
        }
    }
    val imageUrl = rememberImageUrl(
        itemId = imageItemId,
        imageType = "Primary",
        width = 320,
        height = 480,
        quality = 90,
        imageTag = item.imageTagFor(
            imageType = "Primary",
            targetItemId = imageItemId
        ),
        mediaRepository = mediaRepository
    )

    Column(
        modifier = Modifier
            .width(116.dp)
            .clickable(
                enabled = !item.id.isNullOrBlank(),
                onClick = onClick
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(166.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2A2A2A)
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    JellyfinPosterImage(
                        imageUrl = imageUrl,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        context = context,
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Movie,
                        contentDescription = item.name,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.name ?: stringResource(R.string.detail_similar_item_unknown),
            fontSize = 12.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            lineHeight = 14.sp
        )

        val subtitle = item.productionYear?.toString()
            ?: item.type?.takeIf { it.isNotBlank() }

        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

@Composable
private fun SeerrRecommendationCard(
    item: SeerrRecommendationTitle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val posterUrl = remember(item.posterUrl) { item.posterUrl?.takeIf { it.isNotBlank() } }

    Column(
        modifier = modifier
            .width(116.dp)
            .clickable(onClick = onClick)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(166.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF23232A)
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
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

                if (item.jellyfinMediaId.isNullOrBlank()) {
                    SeerrTopBadges(
                        requestState = item.requestState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                } else if (item.requestState == SeerrRequestState.REQUESTED) {
                    SeerrRequestBadge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 6.dp)
                    )
                }

                item.roleLabel
                    ?.takeIf { it.isNotBlank() }
                    ?.let { roleLabel ->
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = Color.Black.copy(alpha = 0.72f)
                        ) {
                            Text(
                                text = roleLabel,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.92f),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            fontSize = 12.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            lineHeight = 14.sp
        )

        val mediaTypeLabel = when {
            item.mediaType.equals("movie", ignoreCase = true) -> stringResource(R.string.suggestions_type_movie)
            item.mediaType.equals("tv", ignoreCase = true) -> stringResource(R.string.suggestions_type_tv_series)
            else -> item.mediaType
        }
        val subtitle = item.productionYear?.toString() ?: mediaTypeLabel

        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

@Composable
private fun SeerrTopBadges(
    requestState: SeerrRequestState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        SeerrSourceBadge()

        if (requestState == SeerrRequestState.REQUESTED) {
            SeerrRequestBadge()
        }
    }
}

@Composable
private fun SeerrSourceBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(20.dp),
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.72f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.settings_seerr),
                fontSize = 9.sp,
                color = Color(0xFF9CDCFE),
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
        }
    }
}

@Composable
private fun SeerrRequestBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color(0xFFE4E5FF),
        border = BorderStroke(1.dp, Color(0xFF8E90FF))
    ) {
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = stringResource(R.string.detail_requested_on_seerr),
                tint = Color(0xFF5F65E8),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}