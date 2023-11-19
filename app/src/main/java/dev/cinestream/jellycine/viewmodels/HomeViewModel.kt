package dev.cinestream.jellycine.viewmodels


import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import androidx.lifecycle.*
import dev.cinestream.jellycine.api.JellyfinApi
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemDto
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
            val views: MutableList<View> = mutableListOf()
            val viewsResult = getViews(jellyfinApi.userId!!)
            for (view in viewsResult.items!!) {
                val items: MutableList<ViewItem> = mutableListOf()
                val latestItems = getLatestMedia(jellyfinApi.userId!!, view.id)
                if (latestItems.isEmpty()) continue
                val v = view.toView()
                for (item in latestItems) {
                    val i = jellyfinApi.api.baseUrl?.let { item.toViewItem(it) }
                    if (i != null) {
                        items.add(i)
                    }
                }
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

}

private fun BaseItemDto.toViewItem(baseUrl: String): ViewItem {
    return when (type) {
        "Episode" -> ViewItem(
            id = seriesId!!,
            name = seriesName,
            primaryImageUrl = baseUrl.plus("/items/${seriesId}/Images/Primary")
        )
        else -> ViewItem(
            id = id,
            name = name,
            primaryImageUrl = baseUrl.plus("/items/${id}/Images/Primary")
        )
    }
}

private fun BaseItemDto.toView(): View {
    return View(
        id = id,
        name = name
    )
}