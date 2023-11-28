package dev.cinestream.jellycine.viewmodels

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cinestream.jellycine.api.JellyfinApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.*
import java.util.*

class MediaViewModel(
    val application: Application
) : ViewModel() {
    private val jellyfinApi = JellyfinApi.getInstance(application, "")

    private val _genres = MutableLiveData<List<BaseItemDto>?>()
    val genres : MutableLiveData<List<BaseItemDto>?> = _genres

    private val _finishedLoading = MutableLiveData<Boolean>()
    val finishedLoading: LiveData<Boolean> = _finishedLoading

    init {
        viewModelScope.launch {
            val items = getGenres(jellyfinApi.userId!!)
            _genres.value = items
            _finishedLoading.value = true
        }
    }

    private suspend fun getGenres(userId: UUID): List<BaseItemDto>? {
        var items: List<BaseItemDto>?
        withContext(Dispatchers.IO) {
            items = jellyfinApi.genresApi.getGenres(userId = userId).content.items
        }
        return items
    }
}