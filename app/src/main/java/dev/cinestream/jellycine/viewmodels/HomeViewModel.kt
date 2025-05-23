package dev.cinestream.jellycine.viewmodels


import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.ImageType
import androidx.lifecycle.*
import dev.cinestream.jellycine.api.JellyfinApi
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.SortOrder
import dev.cinestream.jellycine.models.View
import dev.cinestream.jellycine.models.ViewItem
import java.util.*

class HomeViewModel(
    application: Application
) : ViewModel() {
    private val jellyfinApi = JellyfinApi.getInstance(application, "")

    private val _views = MutableLiveData<List<View>>()
    val views: LiveData<List<View>> = _views

    private val _items = MutableLiveData<List<BaseItemDto>>()
    val items: LiveData<List<BaseItemDto>> = _items

    private val _finishedLoading = MutableLiveData<Boolean>()
    val finishedLoading: LiveData<Boolean> = _finishedLoading

    init {
        viewModelScope.launch {
            _finishedLoading.value = false // Or use a dedicated _isLoading LiveData
            val newViews: MutableList<View> = mutableListOf()
            val userId = jellyfinApi.userId
            val baseUrl = jellyfinApi.api.baseUrl

            if (userId == null || baseUrl == null) {
                // Handle missing user ID or base URL, perhaps log an error or post an error state
                _finishedLoading.value = true
                return@launch
            }

            try {
                // Continue Watching
                val continueWatchingItemsDto = getContinueWatchingItems(userId)
                if (continueWatchingItemsDto.isNotEmpty()) {
                    val continueWatchingViewItems = continueWatchingItemsDto.mapNotNull { it.toViewItem(baseUrl) }
                    if (continueWatchingViewItems.isNotEmpty()) {
                        newViews.add(View(id = UUID.randomUUID(), name = "Continue Watching", items = continueWatchingViewItems))
                    }
                }

                // Favorites
                val favoriteItemsDto = getFavoriteItems(userId)
                if (favoriteItemsDto.isNotEmpty()) {
                    val favoriteViewItems = favoriteItemsDto.mapNotNull { it.toViewItem(baseUrl) }
                    if (favoriteViewItems.isNotEmpty()) {
                        newViews.add(View(id = UUID.randomUUID(), name = "Favorites", items = favoriteViewItems))
                    }
                }

                // Recently Added Movies
                val movieLibraries = getMovieLibraries(userId)
                if (movieLibraries.isNotEmpty()) {
                    // Assuming the first movie library, or you could iterate or let user choose
                    val recentlyAddedMoviesDto = getRecentlyAddedMovies(userId, movieLibraries.first().id)
                    if (recentlyAddedMoviesDto.isNotEmpty()) {
                        val recentlyAddedMovieViewItems = recentlyAddedMoviesDto.mapNotNull { it.toViewItem(baseUrl) }
                        if (recentlyAddedMovieViewItems.isNotEmpty()) {
                            newViews.add(View(id = UUID.randomUUID(), name = "Latest Movies", items = recentlyAddedMovieViewItems))
                        }
                    }
                }

                // Recently Added Shows
                val showLibraries = getShowLibraries(userId)
                if (showLibraries.isNotEmpty()) {
                    // Assuming the first show library
                    val recentlyAddedShowsDto = getRecentlyAddedShows(userId, showLibraries.first().id)
                    if (recentlyAddedShowsDto.isNotEmpty()) {
                         // Filter out duplicates by seriesId and take the most recent episode for each series
                        val distinctSeriesEpisodes = recentlyAddedShowsDto
                            .distinctBy { it.seriesId }
                            .mapNotNull { it.toViewItem(baseUrl) }

                        if (distinctSeriesEpisodes.isNotEmpty()) {
                            newViews.add(View(id = UUID.randomUUID(), name = "Latest Shows", items = distinctSeriesEpisodes))
                        }
                    }
                }

            } catch (e: Exception) {
                // Log error or update an error LiveData
                // For now, just print stack trace for debugging
                e.printStackTrace()
            } finally {
                _views.postValue(newViews)
                _finishedLoading.value = true
            }
        }
    }

    private suspend fun getViews(userId: UUID): BaseItemDtoQueryResult {
        val views: BaseItemDtoQueryResult
        withContext(Dispatchers.IO) {
            views = jellyfinApi.viewsApi.getUserViews(userId).content
        }
        return views
    }

    private suspend fun getLatestMedia(userId: UUID, parentId: UUID): List<BaseItemDto> {
        val items: List<BaseItemDto>
        withContext(Dispatchers.IO) {
            items = jellyfinApi.userLibraryApi.getLatestMedia(userId, parentId = parentId).content
        }
        return items
    }

    private suspend fun getContinueWatchingItems(userId: UUID): List<BaseItemDto> {
        return try {
            withContext(Dispatchers.IO) {
                jellyfinApi.userLibraryApi.getResumableItems(userId = userId).content.items ?: emptyList()
            }
        } catch (e: Exception) {
            // Log error or handle otherwise
            emptyList()
        }
    }

    private suspend fun getFavoriteItems(userId: UUID): List<BaseItemDto> {
        return try {
            withContext(Dispatchers.IO) {
                jellyfinApi.itemsApi.getItems(
                    userId = userId,
                    includeItemTypes = arrayOf("Movie", "Series", "Episode"), // Added Episode
                    recursive = true,
                    sortBy = arrayOf("SortName"),
                    sortOrder = arrayOf(org.jellyfin.sdk.model.SortOrder.ASCENDING),
                    filters = arrayOf(org.jellyfin.sdk.model.api.ItemFilter.IS_FAVORITE)
                ).content.items ?: emptyList()
            }
        } catch (e: Exception) {
            // Log error or handle otherwise
            emptyList()
        }
    }

    private suspend fun getMovieLibraries(userId: UUID): List<BaseItemDto> {
        return try {
            withContext(Dispatchers.IO) {
                jellyfinApi.viewsApi.getUserViews(userId = userId)
                    .content.items?.filter { it.collectionType == "movies" } ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getShowLibraries(userId: UUID): List<BaseItemDto> {
        return try {
            withContext(Dispatchers.IO) {
                jellyfinApi.viewsApi.getUserViews(userId = userId)
                    .content.items?.filter { it.collectionType == "tvshows" } ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getRecentlyAddedMovies(userId: UUID, movieLibraryId: UUID): List<BaseItemDto> {
        return try {
            withContext(Dispatchers.IO) {
                jellyfinApi.userLibraryApi.getLatestMedia(userId = userId, parentId = movieLibraryId, includeItemTypes = arrayOf("Movie")).content
            }
        } catch (e: Exception) {
            // Log error or handle otherwise
            emptyList()
        }
    }

    private suspend fun getRecentlyAddedShows(userId: UUID, showLibraryId: UUID): List<BaseItemDto> {
        return try {
            withContext(Dispatchers.IO) {
                // Fetches recent episodes, then we map to series ViewItem in the main logic
                jellyfinApi.userLibraryApi.getLatestMedia(userId = userId, parentId = showLibraryId, includeItemTypes = arrayOf("Episode")).content
            }
        } catch (e: Exception) {
            // Log error or handle otherwise
            emptyList()
        }
    }
}

private fun BaseItemDto.toViewItem(baseUrl: String): ViewItem {
    val primaryImageTag = this.imageTags?.get(org.jellyfin.sdk.model.api.ImageType.PRIMARY)
    val backdropImageTag = this.imageTags?.get(org.jellyfin.sdk.model.api.ImageType.BACKDROP)

    val primaryImageUrlString = if (this.type == "Episode" && this.seriesId != null) {
        baseUrl.plus("/Items/${this.seriesId}/Images/Primary?tag=${this.parentPrimaryImageTag ?: primaryImageTag}")
    } else {
        baseUrl.plus("/Items/${this.id}/Images/Primary?tag=${primaryImageTag}")
    }

    val backdropImageUrlString = if (this.id != null && backdropImageTag != null) {
        baseUrl.plus("/Items/${this.id}/Images/Backdrop?tag=$backdropImageTag")
    } else if (this.parentBackdropImageItemId != null && this.parentBackdropImageTags?.isNotEmpty() == true) {
        // For episodes, try to use parent backdrop if available
        baseUrl.plus("/Items/${this.parentBackdropImageItemId}/Images/Backdrop?tag=${this.parentBackdropImageTags?.firstOrNull()}")
    } else null

    return ViewItem(
        id = this.id,
        name = this.name,
        primaryImageUrl = primaryImageUrlString,
        itemType = this.type?.toString(),
        productionYear = this.productionYear,
        runTimeTicks = this.runTimeTicks,
        playbackPositionTicks = this.userData?.playbackPositionTicks,
        playedPercentage = this.userData?.playedPercentage,
        isFavorite = this.userData?.isFavorite ?: false,
        overview = this.overview,
        backdropImageUrl = backdropImageUrlString,
        seriesId = if (this.type == "Episode") this.seriesId else null,
        seriesName = if (this.type == "Episode") this.seriesName else null
    )
}

private fun BaseItemDto.toView(): View {
    return View(
        id = id,
        name = name
    )
}