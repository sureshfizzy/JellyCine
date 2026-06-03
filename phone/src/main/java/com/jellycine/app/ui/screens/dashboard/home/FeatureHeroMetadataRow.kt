package com.jellycine.app.ui.screens.dashboard.home

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.data.model.BaseItemDto

@Composable
internal fun FeatureHeroMetadataRow(
    item: BaseItemDto,
    isCompactHero: Boolean,
    metaAlpha: Float,
    metaOffset: Float
) {
    val ratingText = item.communityRating?.let { String.format("%.1f", it) }
    val resolvedYear = item.productionYear ?: item.premiereDate
        ?.take(4)
        ?.toIntOrNull()
    val genres = item.genres.orEmpty().take(3)
    val certificateText = item.officialRating?.takeIf { it.isNotBlank() }
    val hasMetaRow = !ratingText.isNullOrBlank() ||
        resolvedYear != null ||
        genres.isNotEmpty() ||
        !certificateText.isNullOrBlank()

    if (!hasMetaRow) return

    val metaSpacing = if (isCompactHero) 8.dp else 10.dp

    Row(
        horizontalArrangement = Arrangement.spacedBy(
            space = metaSpacing,
            alignment = Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(
                alpha = metaAlpha,
                translationY = metaOffset
            )
    ) {
        if (!ratingText.isNullOrBlank()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = Color(0xFFE84B3C),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = ratingText,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        if (resolvedYear != null) {
            Text(
                text = resolvedYear.toString(),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }
        if (genres.isNotEmpty()) {
            Text(
                text = genres.joinToString(separator = "/"),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                modifier = if (isCompactHero) {
                    Modifier.widthIn(max = 140.dp)
                } else {
                    Modifier.weight(1f, fill = false)
                }
            )
        }
        if (!certificateText.isNullOrBlank()) {
            val certificationShape = RoundedCornerShape(4.dp)
            Box(
                modifier = Modifier
                    .clip(certificationShape)
                    .border(0.75.dp, Color.White.copy(alpha = 0.55f), certificationShape)
                    .padding(horizontal = 5.dp, vertical = 0.dp)
            ) {
                Text(
                    text = certificateText,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    softWrap = false
                )
            }
        }
    }
}