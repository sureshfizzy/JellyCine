package dev.cinestream.jellycine.viewmodels


import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import androidx.lifecycle.*
import dev.cinestream.jellycine.api.JellyfinApi
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.*
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

    private val _genres = MutableLiveData<List<BaseItemDto>>()
    val genres: LiveData<List<BaseItemDto>> = _genres

    private val _finishedLoading = MutableLiveData<Boolean>()
    val finishedLoading: LiveData<Boolean> = _finishedLoading

    init {
        viewModelScope.launch {
            val views: MutableList<View> = mutableListOf()
            val viewsResult = getViews(jellyfinApi.userId!!)
            for (view in viewsResult.items.orEmpty()) {
                val items: MutableList<ViewItem> = mutableListOf()
                val genres = getGenres(jellyfinApi.userId!!, view.id)

                genres?.let {
                    for (genres in it) {
                        val i = jellyfinApi.api.baseUrl?.let { genres.toViewItem(it) }
                        if (i != null) {
                            items.add(i)
                        }
                    }
                }

                val v = view.toView()
                v.items = items
                views.add(v)
            }

            _views.value = views
            _finishedLoading.value = true
        }
    }

    private suspend fun getViews(userId: UUID): BaseItemDtoQueryResult {
        val views: BaseItemDtoQueryResult
        withContext(Dispatchers.IO) {
            views = jellyfinApi.genresApi.getGenres(userId = userId, searchTerm = "Animation").content
        }
        return views
    }

    private suspend fun getGenres(userId: UUID, parentId: UUID): List<BaseItemDto>? {
        val items: List<BaseItemDto>?
        withContext(Dispatchers.IO) {
            items = jellyfinApi.userLibraryApi.getLatestMedia(userId = userId, parentId = parentId).content
        }
        return items
    }

}

private fun BaseItemDto.toViewItem(baseUrl: String): ViewItem {
    return when (type) {
        "Episode" -> ViewItem(
            id = seriesId!!,
            name = seriesName,
            genre = genres?.toList(),
            primaryImageUrl = baseUrl.plus("/items/${seriesId}/Images/Primary")
        )
        else -> ViewItem(
            id = id,
            name = name,
            genre = genres?.toList(),
            primaryImageUrl = baseUrl.plus("/items/${id}/Images/Primary")
        )
    }
}

private fun BaseItemDto.toView(): View {
    return View(
        id = id,
        genre = listOf(genres),
        name = name
    )
}