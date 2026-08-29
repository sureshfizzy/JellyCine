package com.jellycine.app.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.jellycine.shared.R
import com.jellycine.shared.util.image.JellyfinPosterImage
import com.jellycine.shared.util.image.imageTagFor
import com.jellycine.shared.util.image.rememberImageUrl
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.SeerrRecommendationTitle
import com.jellycine.data.model.SeerrItemIds
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.SeerrRepository
import com.jellycine.app.ui.components.common.BackButton
import com.jellycine.app.ui.components.common.SeerTitlesRow
import com.jellycine.app.ui.components.common.fetchSeerDirectedTitlesForTmdbPerson
import com.jellycine.app.ui.components.common.CompactTopText
import com.jellycine.app.ui.components.common.rememberCompactProgress
import com.jellycine.shared.ui.components.common.PosterCountBadge
import com.jellycine.shared.ui.components.common.ShimmerEffect
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object PersonImageCache {
    private var cachedUrl: String? = null
    private var cachedId: String? = null

    fun put(personId: String, imageUrl: String?) {
        cachedId = personId
        cachedUrl = imageUrl
    }

    fun get(personId: String): String? {
        return if (cachedId == personId) cachedUrl else null
    }
}

@UnstableApi
@Composable
fun PersonScreenContainer(
    personId: String,
    onBackPressed: () -> Unit = {},
    onItemClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val cachedImageUrl = remember(personId) { PersonImageCache.get(personId) }
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val authRepository = remember { AuthRepositoryProvider.getInstance(context) }
    val seerrRepository = remember(context) { SeerrRepository(context) }
    val activeServerId by authRepository.getActiveServerId()
        .collectAsState(initial = authRepository.getActiveSessionSnapshot().activeServerId)
    val seerTmdbId = remember(personId) { SeerrItemIds.personTmdbId(personId) }

    var person by remember(personId) { mutableStateOf<BaseItemDto?>(null) }
    var relatedTitles by remember(personId) { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var seerrRelatedTitles by remember(personId) { mutableStateOf<List<SeerrRecommendationTitle>>(emptyList()) }
    var isLoading by remember(personId) { mutableStateOf(true) }
    var hasError by remember(personId) { mutableStateOf(false) }

    LaunchedEffect(personId, activeServerId) {
        isLoading = true
        hasError = false

        try {
            if (seerTmdbId != null) {
                val scopeId = activeServerId?.takeIf { it.isNotBlank() }
                if (scopeId == null) {
                    person = null
                    relatedTitles = emptyList()
                    seerrRelatedTitles = emptyList()
                    hasError = true
                } else {
                    val (personResult, directedTitles) = coroutineScope {
                        val personDeferred = async { seerrRepository.getPersonDetails(scopeId, seerTmdbId) }
                        val directedTitlesDeferred = async {
                            fetchSeerDirectedTitlesForTmdbPerson(
                                personTmdbId = seerTmdbId,
                                activeServerId = activeServerId,
                                seerrRepository = seerrRepository
                            )
                        }
                        personDeferred.await() to directedTitlesDeferred.await()
                    }

                    person = personResult.getOrNull()
                    relatedTitles = emptyList()
                    seerrRelatedTitles = directedTitles
                    hasError = person == null
                }
            } else {
                val (personResult, relatedResult) = coroutineScope {
                    val personDeferred = async { mediaRepository.getItemById(personId) }
                    val relatedDeferred = async { mediaRepository.getItemsForPerson(personId) }
                    personDeferred.await() to relatedDeferred.await()
                }

                person = personResult.getOrNull()
                relatedTitles = relatedResult.getOrDefault(emptyList())
                seerrRelatedTitles = emptyList()
                hasError = person == null
            }
        } catch (_: Exception) {
            hasError = true
            person = null
            relatedTitles = emptyList()
            seerrRelatedTitles = emptyList()
        } finally {
            isLoading = false
        }
    }

    BackHandler(onBack = onBackPressed)

    PersonScreen(
        person = person,
        relatedTitles = relatedTitles,
        seerrRelatedTitles = seerrRelatedTitles,
        isLoading = isLoading,
        hasError = hasError,
        initialImageUrl = cachedImageUrl,
        mediaRepository = mediaRepository,
        onBackPressed = onBackPressed,
        onItemClick = onItemClick
    )
}

@Composable
private fun PersonScreen(
    person: BaseItemDto?,
    relatedTitles: List<BaseItemDto>,
    seerrRelatedTitles: List<SeerrRecommendationTitle>,
    isLoading: Boolean,
    hasError: Boolean,
    initialImageUrl: String?,
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

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val compactProgress = rememberCompactProgress(
        state = listState,
        compactDistance = 160.dp
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                PersonHeader(
                    person = person,
                    mediaRepository = mediaRepository,
                    isLoading = isLoading,
                    initialImageUrl = initialImageUrl,
                    compactProgress = compactProgress
                )
            }

        if (isLoading && person == null) {
            item { PersonShimmer() }
        } else if (hasError) {
            item {
                Text(
                    text = stringResource(R.string.detail_person_load_failed),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                )
            }
        }

        person?.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            item {
                var expanded by remember(overview) { mutableStateOf(false) }
                val cleanOverview = remember(overview) { overview.replace("\r\n", "\n").trim() }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = cleanOverview,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (expanded) "Read less" else "Read more",
                        color = Color(0xFF89ECFF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clickable { expanded = !expanded }
                    )
                }
            }
        }

        mediaSections.forEach { (title, items) ->
            item {
                FilmographySection(
                    title = title,
                    items = items,
                    mediaRepository = mediaRepository,
                    onItemClick = onItemClick
                )
            }
        }

        if (seerrRelatedTitles.isNotEmpty()) {
            item {
                SeerTitlesRow(
                    title = "Directed",
                    items = seerrRelatedTitles,
                    onItemClick = onItemClick,
                    topPadding = 24.dp,
                    verticalSpacing = 10.dp,
                    horizontalPadding = 20.dp,
                    titleFontSize = 18.sp
                )
            }
        }

        if (!isLoading && movies.isEmpty() && shows.isEmpty() && episodes.isEmpty() && seerrRelatedTitles.isEmpty() && !hasError) {
            item {
                Text(
                    text = stringResource(R.string.detail_person_no_related_titles),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                )
            }
        }
        }

        BackButton(
            onClick = onBackPressed,
            modifier = Modifier.align(Alignment.TopStart)
        )

        person?.name?.let { name ->
            CompactTopText(
                text = name,
                progress = compactProgress,
                isTablet = false,
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 48.dp)
            )
        }
    }
}

@Composable
private fun PersonHeader(
    person: BaseItemDto?,
    mediaRepository: MediaRepository,
    isLoading: Boolean,
    initialImageUrl: String? = null,
    compactProgress: Float = 0f
) {
    val context = LocalContext.current
    val personId = person?.id
    val personImageUrl = when {
        !initialImageUrl.isNullOrBlank() -> initialImageUrl
        !person?.imageUrl.isNullOrBlank() -> person?.imageUrl
        personId.isNullOrBlank() -> null
        else -> rememberImageUrl(
            itemId = personId,
            imageType = "Primary",
            width = 480,
            height = 480,
            quality = 90,
            imageTag = person?.imageTagFor("Primary"),
            mediaRepository = mediaRepository
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 56.dp, bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A)),
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
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val nameAlpha = (1f - compactProgress * 2f).coerceIn(0f, 1f)
        Text(
            text = person?.name ?: if (isLoading) "" else stringResource(R.string.detail_person_unknown),
            color = Color.White.copy(alpha = nameAlpha),
            fontSize = 26.sp,
            lineHeight = 30.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FilmographySection(
    title: String,
    items: List<BaseItemDto>,
    mediaRepository: MediaRepository,
    onItemClick: (String) -> Unit
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            items(items, key = { it.id ?: "${it.name}-${it.productionYear}" }) { item ->
                FilmographyCard(
                    item = item,
                    mediaRepository = mediaRepository,
                    onClick = { item.id?.let(onItemClick) }
                )
            }
        }
    }
}

@Composable
private fun FilmographyCard(
    item: BaseItemDto,
    mediaRepository: MediaRepository,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageItemId = when {
        item.type?.equals("Episode", ignoreCase = true) == true && !item.seriesId.isNullOrBlank() -> item.seriesId
        else -> item.id
    }
    val imageUrl = if (imageItemId.isNullOrBlank()) null else rememberImageUrl(
        itemId = imageItemId,
        imageType = "Primary",
        width = 320,
        height = 480,
        quality = 90,
        imageTag = item.imageTagFor(imageType = "Primary", targetItemId = imageItemId),
        mediaRepository = mediaRepository
    )

    val subtitle = when {
        item.type?.equals("Episode", ignoreCase = true) == true -> {
            val s = item.parentIndexNumber
            val e = item.indexNumber
            val code = if (s != null && e != null) "S${s}E${e}" else null
            listOfNotNull(item.seriesName, code).joinToString(" · ").ifBlank { null }
        }
        else -> item.productionYear?.toString()
    }

    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(enabled = !item.id.isNullOrBlank(), onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1A1A))
        ) {
            if (!imageUrl.isNullOrBlank()) {
                JellyfinPosterImage(
                    imageUrl = imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    context = context,
                    contentScale = ContentScale.Crop
                )
            }

            val isSeries = item.type?.equals("Series", ignoreCase = true) == true
            val count = when {
                isSeries && (item.episodeCount ?: 0) > 0 -> item.episodeCount
                isSeries && (item.recursiveItemCount ?: 0) > 0 -> item.recursiveItemCount
                else -> null
            }
            count?.let {
                PosterCountBadge(
                    count = it,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                )
            }
        }

        Column(
            modifier = Modifier.padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = item.name ?: "Unknown",
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            subtitle?.let {
                Text(
                    text = it,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PersonShimmer() {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        ShimmerEffect(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(14.dp)
                .align(Alignment.CenterHorizontally),
            cornerRadius = 6f
        )
        Spacer(Modifier.height(8.dp))
        ShimmerEffect(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(12.dp)
                .align(Alignment.CenterHorizontally),
            cornerRadius = 6f
        )
        Spacer(Modifier.height(24.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            ShimmerEffect(
                modifier = Modifier.fillMaxWidth().height(13.dp),
                cornerRadius = 6f
            )
            Spacer(Modifier.height(6.dp))
            ShimmerEffect(
                modifier = Modifier.fillMaxWidth(0.8f).height(13.dp),
                cornerRadius = 6f
            )
            Spacer(Modifier.height(6.dp))
            ShimmerEffect(
                modifier = Modifier.fillMaxWidth(0.5f).height(13.dp),
                cornerRadius = 6f
            )
        }

        Spacer(Modifier.height(28.dp))

        ShimmerEffect(
            modifier = Modifier
                .padding(start = 20.dp)
                .width(100.dp)
                .height(16.dp),
            cornerRadius = 6f
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            userScrollEnabled = false
        ) {
            items(5) {
                Column(modifier = Modifier.width(120.dp)) {
                    ShimmerEffect(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f),
                        cornerRadius = 10f
                    )
                    Spacer(Modifier.height(4.dp))
                    ShimmerEffect(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(12.dp)
                            .align(Alignment.CenterHorizontally),
                        cornerRadius = 5f
                    )
                }
            }
        }
    }
}
