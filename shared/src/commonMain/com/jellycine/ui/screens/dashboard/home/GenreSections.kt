package com.jellycine.ui.screens.dashboard.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.util.currentTimeMillis
import com.jellycine.data.util.ioDispatcher
import com.jellycine.resources.*
import com.jellycine.ui.screens.dashboard.GenreSectionSkeleton
import com.jellycine.ui.screens.dashboard.PosterSkeleton
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private object Cache {
    private val cache = mutableMapOf<String, CacheEntry>()

    private class CacheEntry(val data: Any, val timestamp: Long)

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = cache[key]?.data as? T

    fun put(key: String, data: Any) {
        cache[key] = CacheEntry(data, currentTimeMillis())
    }

    fun isStale(key: String, staleTime: Long): Boolean {
        val entry = cache[key] ?: return true
        return currentTimeMillis() - entry.timestamp > staleTime
    }

    fun clear() {
        cache.clear()
    }
}

object GenreCache {
    fun clear() {
        Cache.clear()
    }

    fun RefreshMovieGenres(): Boolean {
        return Cache.isStale("movie_genres", 600_000L)
    }

    fun RefreshTVGenres(): Boolean {
        return Cache.isStale("tv_genres", 600_000L)
    }

    fun RefreshGenreItems(genreId: String): Boolean {
        val cacheKey = "genre_items_$genreId"
        if (Cache.isStale(cacheKey, 300_000L)) {
            return true
        }
        val cachedItems = Cache.get<List<BaseItemDto>>(cacheKey) ?: return true
        if (cachedItems.isEmpty()) {
            return true
        }
        val hasYearMetadata = cachedItems.any { item ->
            item.productionYear != null ||
                !item.premiereDate.isNullOrBlank() ||
                !item.endDate.isNullOrBlank()
        }
        return !hasYearMetadata
    }

    fun updateMovieGenres(genres: List<BaseItemDto>) {
        Cache.put("movie_genres", genres)
    }

    fun updateTVGenres(genres: List<BaseItemDto>) {
        Cache.put("tv_genres", genres)
    }

    fun updateGenreItems(genreId: String, items: List<BaseItemDto>) {
        Cache.put("genre_items_$genreId", items)
    }

    fun getGenreItems(genreId: String): List<BaseItemDto> {
        return Cache.get("genre_items_$genreId") ?: emptyList()
    }

    val movieGenres: List<BaseItemDto>
        get() = Cache.get("movie_genres") ?: emptyList()

    val tvGenres: List<BaseItemDto>
        get() = Cache.get("tv_genres") ?: emptyList()
}

@Composable
fun MovieGenreSections(
    mediaRepository: MediaRepository,
    imageHeaders: Map<String, String>,
    disablePosterEnhancers: Boolean,
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    var movieGenres by remember { mutableStateOf(GenreCache.movieGenres) }
    var isLoading by remember { mutableStateOf(GenreCache.RefreshMovieGenres()) }

    LaunchedEffect(Unit) {
        if (GenreCache.RefreshMovieGenres()) {
            isLoading = true
            try {
                val result = withContext(ioDispatcher) {
                    mediaRepository.getFilteredGenres(includeItemTypes = "Movie")
                }
                result.fold(
                    onSuccess = { genres ->
                        GenreCache.updateMovieGenres(genres)
                        movieGenres = genres
                        isLoading = false
                    },
                    onFailure = {
                        isLoading = false
                    }
                )
            } catch (e: Exception) {
                isLoading = false
            }
        } else {
            movieGenres = GenreCache.movieGenres
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        if (isLoading) {
            GenreSectionSkeleton(sectionCount = 6)
        } else {
            movieGenres.forEach { genre ->
                ProgressiveMovieGenreSection(
                    genre = genre,
                    mediaRepository = mediaRepository,
                    imageHeaders = imageHeaders,
                    disablePosterEnhancers = disablePosterEnhancers,
                    onItemClick = onItemClick,
                    onNavigateToViewAll = onNavigateToViewAll
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun TVShowGenreSections(
    mediaRepository: MediaRepository,
    imageHeaders: Map<String, String>,
    disablePosterEnhancers: Boolean,
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    var tvGenres by remember { mutableStateOf(GenreCache.tvGenres) }
    var isLoading by remember { mutableStateOf(GenreCache.RefreshTVGenres()) }

    LaunchedEffect(Unit) {
        if (GenreCache.RefreshTVGenres()) {
            isLoading = true
            try {
                val result = mediaRepository.getFilteredGenres(includeItemTypes = "Series")
                result.fold(
                    onSuccess = { genres ->
                        GenreCache.updateTVGenres(genres)
                        tvGenres = genres
                        isLoading = false
                    },
                    onFailure = {
                        isLoading = false
                    }
                )
            } catch (e: Exception) {
                isLoading = false
            }
        } else {
            tvGenres = GenreCache.tvGenres
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        if (isLoading) {
            GenreSectionSkeleton(sectionCount = 6)
        } else {
            tvGenres.forEach { genre ->
                ProgressiveTVShowGenreSection(
                    genre = genre,
                    mediaRepository = mediaRepository,
                    imageHeaders = imageHeaders,
                    disablePosterEnhancers = disablePosterEnhancers,
                    onItemClick = onItemClick,
                    onNavigateToViewAll = onNavigateToViewAll
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ProgressiveMovieGenreSection(
    genre: BaseItemDto,
    mediaRepository: MediaRepository,
    imageHeaders: Map<String, String>,
    disablePosterEnhancers: Boolean,
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    val genreId = genre.id ?: return
    var genreMovies by remember(genreId) { mutableStateOf(GenreCache.getGenreItems(genreId)) }
    var isLoading by remember(genreId) { mutableStateOf(GenreCache.RefreshGenreItems(genreId)) }
    val genreTitle = genre.name ?: stringResource(Res.string.movies)

    LaunchedEffect(genreId) {
        if (GenreCache.RefreshGenreItems(genreId)) {
            isLoading = true
            try {
                val result = withContext(ioDispatcher) {
                    mediaRepository.getItemsByGenre(
                        genreId = genreId,
                        includeItemTypes = "Movie",
                        limit = 20
                    )
                }

                result.fold(
                    onSuccess = { items ->
                        val validItems = items.filter {
                            it.id != null && !it.name.isNullOrBlank()
                        }
                        GenreCache.updateGenreItems(genreId, validItems)
                        genreMovies = validItems
                    },
                    onFailure = {
                        genreMovies = emptyList()
                    }
                )
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
            }
        } else {
            genreMovies = GenreCache.getGenreItems(genreId)
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = genreTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            IconButton(
                onClick = {
                    onNavigateToViewAll("MOVIES_GENRE", genre.id, genreTitle)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(Res.string.dashboard_view_all),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        when {
            isLoading -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    items(4) {
                        PosterSkeleton()
                    }
                }
            }
            genreMovies.isNotEmpty() -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    items(
                        count = genreMovies.size,
                        key = { index -> "${genreMovies[index].id ?: "genre_movie"}_$index" }
                    ) { index ->
                        LibraryItemCard(
                            item = genreMovies[index],
                            mediaRepository = mediaRepository,
                            imageHeaders = imageHeaders,
                            disableImageEnhancers = disablePosterEnhancers,
                            onClick = { onItemClick(genreMovies[index]) }
                        )
                    }
                }
            }
            else -> {
                Text(
                    text = stringResource(Res.string.dashboard_no_movies_found),
                    color = Color.Gray.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ProgressiveTVShowGenreSection(
    genre: BaseItemDto,
    mediaRepository: MediaRepository,
    imageHeaders: Map<String, String>,
    disablePosterEnhancers: Boolean,
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    val genreId = genre.id ?: return
    var genreShows by remember(genreId) { mutableStateOf(GenreCache.getGenreItems("tv_$genreId")) }
    var isLoading by remember(genreId) { mutableStateOf(GenreCache.RefreshGenreItems("tv_$genreId")) }
    val genreTitle = genre.name ?: stringResource(Res.string.tv_shows)

    LaunchedEffect(genreId) {
        val cacheKey = "tv_$genreId"
        if (GenreCache.RefreshGenreItems(cacheKey)) {
            isLoading = true
            try {
                val result = withContext(ioDispatcher) {
                    mediaRepository.getItemsByGenre(
                        genreId = genreId,
                        includeItemTypes = "Series",
                        limit = 20
                    )
                }

                result.fold(
                    onSuccess = { items ->
                        val validItems = items.filter {
                            it.id != null && !it.name.isNullOrBlank()
                        }
                        GenreCache.updateGenreItems(cacheKey, validItems)
                        genreShows = validItems
                    },
                    onFailure = {
                        genreShows = emptyList()
                    }
                )
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
            }
        } else {
            genreShows = GenreCache.getGenreItems("tv_$genreId")
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = genreTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            IconButton(
                onClick = {
                    onNavigateToViewAll("TVSHOWS_GENRE", genre.id, genreTitle)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(Res.string.dashboard_view_all),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        when {
            isLoading -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(4) {
                        PosterSkeleton()
                    }
                }
            }
            genreShows.isNotEmpty() -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(
                        items = genreShows,
                        key = { index, show -> "${show.id ?: "${show.name}_${show.type}"}_$index" }
                    ) { _, show ->
                        LibraryItemCard(
                            item = show,
                            mediaRepository = mediaRepository,
                            imageHeaders = imageHeaders,
                            disableImageEnhancers = disablePosterEnhancers,
                            onClick = { onItemClick(show) }
                        )
                    }
                }
            }
            else -> {
                Text(
                    text = stringResource(Res.string.dashboard_no_tv_shows_found),
                    color = Color.Gray.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}