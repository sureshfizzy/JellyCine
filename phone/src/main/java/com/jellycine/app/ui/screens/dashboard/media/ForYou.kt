package com.jellycine.app.ui.screens.dashboard.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.R
import com.jellycine.app.ui.screens.dashboard.home.LibraryItemCard
import androidx.compose.foundation.layout.statusBarsPadding
import com.jellycine.shared.util.image.disableEmbyPosterEnhancers
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.RecommendationDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private data class RecommendationSectionUi(
    val title: String,
    val items: List<BaseItemDto>
)

private data class RecommendationFeedState(
    val sections: List<RecommendationSectionUi>,
    val error: String?
)

@Composable
fun ForYou(onItemClick: (BaseItemDto) -> Unit = {}) {
    var sections by remember { mutableStateOf<List<RecommendationSectionUi>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val disablePosterEnhancers = disableEmbyPosterEnhancers()
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            isLoading = true
            val result = loadRecommendationFeed(mediaRepository)
            sections = result.sections
            error = result.error
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = Color.Black
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_for_you),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFFE86E2F))
                        }
                    }

                    sections.isNotEmpty() -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 110.dp)
                        ) {
                            items(
                                items = sections,
                                key = { section -> section.title }
                            ) { section ->
                                RecommendationRail(
                                    section = section,
                                    mediaRepository = mediaRepository,
                                    disablePosterEnhancers = disablePosterEnhancers,
                                    onItemClick = onItemClick
                                )
                            }
                        }
                    }

                    error != null -> {
                        ErrorCard(
                            message = error.orEmpty(),
                            onRetry = ::refresh
                        )
                    }

                    else -> {
                        EmptyCard()
                    }
                }
            }
        }
    }
}

private suspend fun loadRecommendationFeed(mediaRepository: MediaRepository): RecommendationFeedState {
    return try {
        val viewsResult = mediaRepository.getUserViews()
        val movieLibraries = viewsResult.getOrNull()?.items
            .orEmpty()
            .filter { view ->
                view.id != null &&
                    view.collectionType.equals("movies", ignoreCase = true)
            }

        val requests = if (movieLibraries.isNotEmpty()) {
            movieLibraries
        } else {
            listOf(BaseItemDto(name = null, id = null, collectionType = "movies"))
        }

        val results = coroutineScope {
            requests.map { library ->
                async {
                    mediaRepository.getMovieRecommendations(
                        parentId = library.id,
                        categoryLimit = 8,
                        itemLimit = 18
                    )
                }
            }.awaitAll()
        }

        val rawSections = results.flatMap { result ->
            result.getOrNull()
                .orEmpty()
                .mapNotNull { recommendation ->
                    val title = recommendation.title()
                    val items = recommendation.items.orEmpty()
                    if (title == null || items.isEmpty()) {
                        null
                    } else {
                        RecommendationSectionUi(
                            title = title,
                            items = items
                        )
                    }
                }
        }

        val sections = rawSections
            .groupBy { it.title }
            .map { (title, groupedSections) ->
                RecommendationSectionUi(
                    title = title,
                    items = groupedSections
                        .flatMap { it.items }
                        .distinctBy { item -> item.id ?: item.name.orEmpty() }
                )
            }
            .filter { it.items.isNotEmpty() }

        if (sections.isNotEmpty()) {
            RecommendationFeedState(sections = sections, error = null)
        } else {
            val fallback = mediaRepository.getSuggestions(
                mediaType = "Movie",
                limit = 24
            )
            val fallbackItems = fallback.getOrNull().orEmpty()
            if (fallbackItems.isNotEmpty()) {
                RecommendationFeedState(
                    sections = listOf(
                        RecommendationSectionUi(
                            title = "Suggestions",
                            items = fallbackItems
                        )
                    ),
                    error = null
                )
            } else {
                val recommendationError = results
                    .asSequence()
                    .mapNotNull { it.exceptionOrNull()?.message }
                    .firstOrNull()
                RecommendationFeedState(
                    sections = emptyList(),
                    error = listOfNotNull(
                        recommendationError,
                        fallback.exceptionOrNull()?.message,
                        viewsResult.exceptionOrNull()?.message
                    ).firstOrNull()
                )
            }
        }
    } catch (e: Exception) {
        RecommendationFeedState(emptyList(), e.message ?: "Unknown error")
    }
}

private fun RecommendationDto.title(): String? {
    val baseline = baselineItemName?.takeIf { it.isNotBlank() }
    return when (recommendationType) {
        "HasDirectorFromRecentlyPlayed",
        "HasLikedDirector" -> baseline?.let { "Directed by $it" }

        "HasActorFromRecentlyPlayed",
        "HasLikedActor" -> baseline?.let { "Starring $it" }

        "SimilarToLikedItem" -> baseline?.let { "Because you like $it" }
        "SimilarToRecentlyPlayed" -> baseline?.let { "Because you watched $it" }
        else -> baseline
    }
}

@Composable
private fun RecommendationRail(
    section: RecommendationSectionUi,
    mediaRepository: MediaRepository,
    disablePosterEnhancers: Boolean,
    onItemClick: (BaseItemDto) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        Text(
            text = section.title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        ) {
            items(
                items = section.items,
                key = { item -> item.id ?: item.name ?: section.title }
            ) { item ->
                LibraryItemCard(
                    item = item,
                    mediaRepository = mediaRepository,
                    disableImageEnhancers = disablePosterEnhancers,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.dashboard_for_you_load_error),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE86E2F))
            ) {
                Text(text = stringResource(R.string.try_again))
            }
        }
    }
}

@Composable
private fun EmptyCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.42f),
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.dashboard_for_you_empty_title),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.dashboard_for_you_empty_message),
                color = Color.White.copy(alpha = 0.64f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}