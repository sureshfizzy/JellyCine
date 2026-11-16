package com.jellycine.ui.components.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jellycine.data.model.SeerrRecommendationTitle
import com.jellycine.data.model.SeerrRequestState

@Composable
fun SeerTitleCard(
    item: SeerrRecommendationTitle,
    modifier: Modifier = Modifier,
    showSourceBadge: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val posterUrl = remember(item.posterUrl) { item.posterUrl?.takeIf { it.isNotBlank() } }

    val clickableModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    val cardShape = RoundedCornerShape(10.dp)

    Column(
        modifier = clickableModifier.width(116.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(166.dp),
            shape = cardShape,
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF23232A)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
            ) {
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

                when {
                    showSourceBadge && item.jellyfinMediaId.isNullOrBlank() -> {
                        SeerrTopBadges(
                            requestState = item.requestState,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                    item.requestState == SeerrRequestState.REQUESTED -> {
                        SeerrRequestBadge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 6.dp)
                        )
                    }
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
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        val subtitle = item.productionYear?.toString()
            ?: item.mediaType.replaceFirstChar { char -> char.titlecase() }

        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 1.dp)
            )
        }
    }
}
