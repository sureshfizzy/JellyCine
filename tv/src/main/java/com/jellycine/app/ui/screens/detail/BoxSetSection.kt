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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
        modifier = modifier.padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "${items.size} Movies",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(end = 48.dp)
        ) {
            items(
                items = items,
                key = { it.id ?: it.name.orEmpty() }
            ) { item ->
                BoxSetMovieCard(
                    item = item,
                    mediaRepository = mediaRepository,
                    onClick = { item.id?.let(onItemClick) }
                )
            }
        }
    }
}

@Composable
private fun BoxSetMovieCard(
    item: BaseItemDto,
    mediaRepository: MediaRepository,
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

    Card(
        onClick = onClick,
        modifier = modifier.width(150.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
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

                if (isWatched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .then(
                                Modifier.fillMaxSize()
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Surface(
                            modifier = Modifier.size(22.dp),
                            shape = RoundedCornerShape(11.dp),
                            color = Color(0xFF4CAF50)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Watched",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }

                if (progress != null && !isWatched) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter),
                        color = Color(0xFFE84B3C),
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = item.name ?: "Unknown",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item.communityRating?.let { rating ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = Color(0xFFE84B3C),
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = String.format("%.1f", rating),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    val year = item.productionYear
                        ?: item.premiereDate?.take(4)?.toIntOrNull()
                    if (year != null) {
                        Text(
                            text = year.toString(),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
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
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(end = 48.dp)
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
                    .width(80.dp)
                    .clickable { person.id?.let(onPersonClick) }
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
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
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Text(
                    text = person.name ?: "",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp,
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .height(26.dp)
                )
            }
        }
    }
}
