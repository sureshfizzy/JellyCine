package com.jellycine.shared.recommendations

import com.jellycine.data.model.WatchedFeedSection
import com.jellycine.data.model.WatchedFeedState
import com.jellycine.data.repository.MediaRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

suspend fun loadWatchedFeed(
    mediaRepository: MediaRepository,
    moviesTitle: String,
    showsTitle: String,
    episodesTitle: String
): WatchedFeedState {
    return try {
        val results = coroutineScope {
            val movies = async { mediaRepository.loadWatchedItems("Movie") }
            val episodes = async { mediaRepository.loadWatchedItems("Episode") }
            val episodeResult = episodes.await()
            val shows = async {
                episodeResult.fold(
                    onSuccess = { mediaRepository.loadSeriesForWatchedEpisodes(it) },
                    onFailure = { Result.failure(it) }
                )
            }

            listOf(
                moviesTitle to movies.await(),
                showsTitle to shows.await(),
                episodesTitle to episodeResult
            )
        }

        WatchedFeedState(
            sections = results.mapNotNull { (title, result) ->
                val items = result.getOrNull().orEmpty()
                if (items.isEmpty()) null else WatchedFeedSection(title, items)
            },
            error = results
                .asSequence()
                .mapNotNull { (_, result) -> result.exceptionOrNull()?.message }
                .firstOrNull()
        )
    } catch (e: Exception) {
        WatchedFeedState(emptyList(), e.message ?: "Unknown error")
    }
}