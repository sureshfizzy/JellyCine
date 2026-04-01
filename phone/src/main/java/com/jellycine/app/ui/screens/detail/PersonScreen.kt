package com.jellycine.app.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.jellycine.app.R
import com.jellycine.app.ui.components.common.PosterCountBadge
import com.jellycine.app.ui.components.common.ShimmerEffect
import com.jellycine.app.util.image.JellyfinPosterImage
import com.jellycine.app.util.image.imageTagFor
import com.jellycine.app.util.image.rememberImageUrl
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@UnstableApi
@Composable
fun PersonScreenContainer(
    personId: String,
    onBackPressed: () -> Unit = {},
    onItemClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }

    var person by remember(personId) { mutableStateOf<BaseItemDto?>(null) }
    var relatedTitles by remember(personId) { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isLoading by remember(personId) { mutableStateOf(true) }
    var hasError by remember(personId) { mutableStateOf(false) }

    LaunchedEffect(personId) {
        isLoading = true
        hasError = false

        try {
            val (personResult, relatedResult) = coroutineScope {
                val personDeferred = async { mediaRepository.getItemById(personId) }
                val relatedDeferred = async { mediaRepository.getItemsForPerson(personId) }
                personDeferred.await() to relatedDeferred.await()
            }

            person = personResult.getOrNull()
            relatedTitles = relatedResult.getOrDefault(emptyList())
            hasError = person == null
        } catch (_: Exception) {
            hasError = true
            person = null
            relatedTitles = emptyList()
        } finally {
            isLoading = false
        }
    }

    BackHandler(onBack = onBackPressed)

    PersonScreen(
        person = person,
        relatedTitles = relatedTitles,
        isLoading = isLoading,
        hasError = hasError,
        mediaRepository = mediaRepository,
        onBackPressed = onBackPressed,
        onItemClick = onItemClick
    )
}

@Composable
private fun PersonScreen(
    person: BaseItemDto?,
    relatedTitles: List<BaseItemDto>,
    isLoading: Boolean,
    hasError: Boolean,
    mediaRepository: MediaRepository,
    onBackPressed: () -> Unit,
    onItemClick: (String) -> Unit
) {
    val movies = remember(relatedTitles) {
        relatedTitles
            .filter { it.type?.equals("Movie", ignoreCase = true) == true }
            .sortedByDescending { it.productionYear ?: Int.MIN_VALUE }
    }
    val shows = remember(relatedTitles) {
        relatedTitles
            .filter { it.type?.equals("Series", ignoreCase = true) == true }
            .sortedByDescending { it.productionYear ?: Int.MIN_VALUE }
    }
    val episodes = remember(relatedTitles) {
        relatedTitles
            .filter { it.type?.equals("Episode", ignoreCase = true) == true }
            .sortedByDescending { it.productionYear ?: Int.MIN_VALUE }
    }
    val mediaSections = remember(movies, shows, episodes) {
        listOf(
            "Movies" to movies,
            "TV Shows" to shows,
            "Episodes" to episodes
        ).filter { (_, items) -> items.isNotEmpty() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            PersonHero(
                person = person,
                mediaRepository = mediaRepository,
                isLoading = isLoading,
                onBackPressed = onBackPressed
            )
        }

        item {
            if (isLoading && person == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF22D3EE),
                        strokeWidth = 2.5.dp
                    )
                }
            } else if (hasError) {
                Text(
                    text = stringResource(R.string.detail_person_load_failed),
                    color = Color.White.copy(alpha = 0.84f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp)
                )
            }
        }

        person?.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            item {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
                    Text(
                        text = stringResource(R.string.detail_person_about),
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF161A22),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        ExpandableOverviewText(
                            overview = overview,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }

        mediaSections.forEach { (title, items) ->
            item {
                RelatedTitlesSection(
                    title = title,
                    items = items,
                    mediaRepository = mediaRepository,
                    onItemClick = onItemClick
                )
            }
        }

        if (!isLoading && movies.isEmpty() && shows.isEmpty() && episodes.isEmpty() && !hasError) {
            item {
                Text(
                    text = stringResource(R.string.detail_person_no_related_titles),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp)
                )
            }
        }
    }
}

@Composable
private fun ExpandableOverviewText(
    overview: String,
    modifier: Modifier = Modifier
) {
    val normalizedOverview = remember(overview) { overview.replace("\r\n", "\n").trim() }
    val paragraphs = remember(normalizedOverview) {
        normalizedOverview
            .split(Regex("\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    val firstParagraph = paragraphs.firstOrNull().orEmpty()
    val remainingParagraphs = remember(paragraphs) {
        if (paragraphs.size > 1) paragraphs.drop(1).joinToString("\n\n") else ""
    }
    var expanded by remember(normalizedOverview) { mutableStateOf(false) }

    val visibleText = if (expanded && remainingParagraphs.isNotBlank()) {
        "$firstParagraph\n\n$remainingParagraphs"
    } else {
        firstParagraph
    }

    Column(modifier = modifier) {
        Text(
            text = visibleText,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 14.sp,
            lineHeight = 22.sp
        )

        if (remainingParagraphs.isNotBlank()) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text(
                    text = if (expanded) {
                        stringResource(R.string.detail_person_read_less)
                    } else {
                        stringResource(R.string.detail_person_read_more)
                    },
                    color = Color(0xFF89ECFF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PersonHero(
    person: BaseItemDto?,
    mediaRepository: MediaRepository,
    isLoading: Boolean,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val personId = person?.id
    val personImageUrl = if (personId.isNullOrBlank()) {
        null
    } else {
        rememberImageUrl(
            itemId = personId,
            imageType = "Primary",
            width = 900,
            height = 1350,
            quality = 95,
            imageTag = person?.imageTagFor("Primary"),
            mediaRepository = mediaRepository
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(390.dp)
            .clipToBounds()
    ) {
        if (!personImageUrl.isNullOrBlank()) {
            JellyfinPosterImage(
                imageUrl = personImageUrl,
                contentDescription = person?.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                context = context
            )
        } else {
            ShimmerEffect(modifier = Modifier.fillMaxSize(), cornerRadius = 0f)
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.22f),
                            Color.Black.copy(alpha = 0.62f),
                            Color.Black.copy(alpha = 0.92f),
                            Color.Black
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Card(
                modifier = Modifier
                    .width(126.dp)
                    .height(184.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1E29))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (!personImageUrl.isNullOrBlank()) {
                        JellyfinPosterImage(
                            imageUrl = personImageUrl,
                            contentDescription = person?.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            context = context
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 6.dp)
            ) {
                Text(
                    text = person?.name ?: if (isLoading) {
                        stringResource(R.string.detail_person_loading)
                    } else {
                        stringResource(R.string.detail_person_unknown)
                    },
                    color = Color.White,
                    fontSize = 29.sp,
                    lineHeight = 33.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF22D3EE).copy(alpha = 0.23f)
                ) {
                    Text(
                        text = stringResource(R.string.detail_cast_and_crew),
                        color = Color(0xFF89ECFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RelatedTitlesSection(
    title: String,
    items: List<BaseItemDto>,
    mediaRepository: MediaRepository,
    onItemClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            items(
                items = items,
                key = { item -> item.id ?: "${item.name}-${item.type}-${item.productionYear}" }
            ) { item ->
                PersonTitleCard(
                    item = item,
                    mediaRepository = mediaRepository,
                    onClick = {
                        item.id?.let(onItemClick)
                    }
                )
            }
        }
    }
}

@Composable
private fun PersonTitleCard(
    item: BaseItemDto,
    mediaRepository: MediaRepository,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageItemId = when {
        item.type?.equals("Episode", ignoreCase = true) == true && !item.seriesId.isNullOrBlank() -> item.seriesId
        else -> item.id
    }
    val imageUrl = if (imageItemId.isNullOrBlank()) {
        null
    } else {
        rememberImageUrl(
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
    }

    val subtitle = when {
        item.type?.equals("Episode", ignoreCase = true) == true -> {
            val season = item.parentIndexNumber
            val episode = item.indexNumber
            val code = if (season != null && episode != null) "S${season}E${episode}" else null
            listOfNotNull(item.seriesName, code).joinToString(" - ").ifBlank { null }
        }
        else -> item.productionYear?.toString()
    }
    val isSeries = item.type?.equals("Series", ignoreCase = true) == true
    val episodeCount = item.episodeCount
    val recursiveItemCount = item.recursiveItemCount
    val seriesCount = when {
        isSeries && (episodeCount ?: 0) > 0 -> episodeCount
        isSeries && (recursiveItemCount ?: 0) > 0 -> recursiveItemCount
        else -> null
    }

    Column(
        modifier = Modifier
            .width(128.dp)
            .clickable(
                enabled = !item.id.isNullOrBlank(),
                onClick = onClick
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(186.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF242734))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
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
                        imageVector = if (item.type?.equals("Series", ignoreCase = true) == true) {
                            Icons.Rounded.Tv
                        } else {
                            Icons.Rounded.Movie
                        },
                        contentDescription = item.name,
                        tint = Color.White.copy(alpha = 0.42f),
                        modifier = Modifier
                            .size(34.dp)
                            .align(Alignment.Center)
                    )
                }

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.66f)
                                )
                            )
                        )
                )

                seriesCount?.let { count ->
                    PosterCountBadge(
                        count = count,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.name ?: "Unknown",
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )

        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}
