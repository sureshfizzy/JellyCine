package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.BaseItemPerson
import com.jellycine.data.repository.MediaRepository
import com.jellycine.detail.CodecUtils
import com.jellycine.shared.util.image.JellyfinPosterImage
import com.jellycine.shared.util.image.imageTagFor
import com.jellycine.shared.util.image.primaryImageTagOrNull
import kotlinx.coroutines.flow.first

@Composable
internal fun BoxSetItemsSection(
    items: List<BaseItemDto>,
    mediaRepository: MediaRepository,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier.padding(top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEachIndexed { index, item ->
            BoxSetMovieCard(
                item = item,
                mediaRepository = mediaRepository,
                posterOnLeft = index % 2 == 0,
                onClick = { item.id?.let(onItemClick) }
            )
        }
    }
}

@Composable
private fun BoxSetMovieCard(
    item: BaseItemDto,
    mediaRepository: MediaRepository,
    posterOnLeft: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var posterUrl by remember(item.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(item.id) {
        val itemId = item.id ?: return@LaunchedEffect
        posterUrl = mediaRepository.getImageUrl(
            itemId = itemId,
            imageType = "Primary",
            width = 300,
            height = 450,
            quality = 90,
            enableImageEnhancers = false,
            imageTag = item.imageTagFor(imageType = "Primary", targetItemId = itemId)
        ).first()
    }

    val progress = item.userData?.let { userData ->
        val position = userData.playbackPositionTicks ?: 0L
        val runtime = item.runTimeTicks ?: 0L
        if (runtime > 0L && position > 0L) {
            (position.toFloat() / runtime.toFloat()).coerceIn(0f, 1f)
        } else null
    }
    val isWatched = item.userData?.played == true

    val posterBox = @Composable {
        Box(
            modifier = Modifier
                .width(90.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E1E1E))
        ) {
            posterUrl?.let { url ->
                JellyfinPosterImage(
                    context = context,
                    imageUrl = url,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (posterOnLeft) posterBox()

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = item.name ?: "Unknown",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item.communityRating?.let { rating ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = Color(0xFFE84B3C),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = String.format("%.1f", rating),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                val year = item.productionYear
                    ?: item.premiereDate?.take(4)?.toIntOrNull()
                if (year != null) {
                    Text(
                        text = year.toString(),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }

                item.runTimeTicks?.let { ticks ->
                    Text(
                        text = CodecUtils.formatRuntime(ticks),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }

                item.officialRating?.takeIf { it.isNotBlank() }?.let { cert ->
                    val shape = RoundedCornerShape(3.dp)
                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .border(0.75.dp, Color.White.copy(alpha = 0.35f), shape)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = cert,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            if (progress != null && !isWatched) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFFE84B3C),
                    trackColor = Color.White.copy(alpha = 0.15f)
                )
            }

            if (isWatched) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Watched",
                        fontSize = 11.sp,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (!posterOnLeft) posterBox()
    }
}

@Composable
internal fun BoxSetCastRow(
    people: List<BaseItemPerson>,
    mediaRepository: MediaRepository,
    onPersonClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (people.isEmpty()) return

    LazyRow(
        modifier = modifier.padding(top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(end = 16.dp)
    ) {
        items(people.take(20), key = { it.id ?: it.name.orEmpty() }) { person ->
            var imageUrl by remember(person.id) { mutableStateOf<String?>(null) }
            LaunchedEffect(person.id) {
                val personId = person.id ?: return@LaunchedEffect
                imageUrl = mediaRepository.getImageUrl(
                    itemId = personId,
                    imageType = "Primary",
                    width = 160,
                    height = 160,
                    quality = 85,
                    imageTag = person.primaryImageTagOrNull()
                ).first()
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .clickable { person.id?.let(onPersonClick) }
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2A2A)),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = person.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Text(
                    text = person.name ?: "",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
