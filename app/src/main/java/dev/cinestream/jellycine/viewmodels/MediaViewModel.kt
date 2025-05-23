package dev.cinestream.jellycine.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cinestream.jellycine.api.JellyfinApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.*

class MediaViewModel(
    val application: Application
) : ViewModel() {
    private val jellyfinApi = JellyfinApi.getInstance(application, "")

    private val _mediaItemDetails = MutableLiveData<BaseItemDto?>()
    val mediaItemDetails: LiveData<BaseItemDto?> = _mediaItemDetails

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    val formattedMetadata: LiveData<String> = Transformations.map(_mediaItemDetails) { details ->
        formatMetadata(details)
    }

    init {
        // Init block is now empty or used for other setup if needed, not for initial data load.
    }

    private fun formatMetadata(item: BaseItemDto?): String {
        if (item == null) return ""
        val parts = mutableListOf<String>()

        item.productionYear?.let {
            if (it > 0) parts.add(it.toString())
        }

        item.genres?.takeIf { it.isNotEmpty() }?.let {
            parts.add(it.joinToString(", "))
        }

        item.runTimeTicks?.let { ticks ->
            if (ticks > 0) {
                val totalSeconds = ticks / 10_000_000 // 1 tick = 100ns, so 10^7 ticks = 1 second
                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                val runtimeString = when {
                    hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
                    hours > 0 -> "${hours}h"
                    minutes > 0 -> "${minutes}m"
                    else -> null // Or "0m" if you prefer
                }
                runtimeString?.let { parts.add(it) }
            }
        }
        return parts.joinToString(" • ")
    }

    fun loadMediaDetails(itemId: String) {
        if (jellyfinApi.userId == null) {
            _error.value = "User not logged in."
            _isLoading.value = false // Ensure loading state is reset
            return
        }
        if (itemId.isBlank()) {
            _error.value = "Item ID is missing."
            _isLoading.value = false // Ensure loading state is reset
            return
        }

        _isLoading.value = true
        _error.value = null // Clear previous errors
        _mediaItemDetails.value = null // Clear previous details

        viewModelScope.launch {
            try {
                val itemUUID = UUID.fromString(itemId)
                // Using withContext for IO operation
                val result = withContext(Dispatchers.IO) {
                    jellyfinApi.userLibraryApi.getItem(userId = jellyfinApi.userId!!, itemId = itemUUID)
                }

                if (result.isSuccess) {
                    _mediaItemDetails.postValue(result.content)
                } else {
                    _mediaItemDetails.postValue(null)
                    val errorMessage = "Error fetching details: ${result.message ?: "Unknown error"}"
                    _error.postValue(errorMessage)
                    Log.e("MediaViewModel", "Error fetching item $itemId: ${result.error} - ${result.message}")
                }
            } catch (e: IllegalArgumentException) {
                Log.e("MediaViewModel", "Invalid Item ID format: $itemId", e)
                _error.postValue("Invalid Item ID format.")
            } catch (e: Exception) { // Catch any other unexpected exceptions
                Log.e("MediaViewModel", "Exception loading media details for $itemId", e)
                _error.postValue("An unexpected error occurred while loading details.")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun toggleFavorite() {
        val currentItem = _mediaItemDetails.value ?: return
        val userId = jellyfinApi.userId ?: return // Ensure user is logged in

        val itemId = currentItem.id
        val isCurrentlyFavorite = currentItem.userData?.isFavorite ?: false

        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    if (isCurrentlyFavorite) {
                        Log.d("MediaViewModel", "Unmarking item $itemId as favorite for user $userId")
                        jellyfinApi.userLibraryApi.unmarkFavoriteItem(userId = userId, itemId = itemId)
                    } else {
                        Log.d("MediaViewModel", "Marking item $itemId as favorite for user $userId")
                        jellyfinApi.userLibraryApi.markFavoriteItem(userId = userId, itemId = itemId)
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaViewModel", "Error toggling favorite state for item $itemId", e)
                // Optionally update _error LiveData
                // _error.postValue("Failed to update favorite status.")
                null // Indicate error
            }

            if (result != null && result.isSuccess) {
                Log.d("MediaViewModel", "Favorite status toggled successfully for $itemId. Refreshing details.")
                // Refresh the item details to get updated userData
                loadMediaDetails(itemId.toString())
            } else if (result != null) {
                Log.e("MediaViewModel", "API call to toggle favorite for $itemId failed: ${result.message}")
                // Optionally update _error LiveData
                 _error.postValue("Failed to update favorite status: ${result.message}")
            }
            // If result is null (due to exception), error is already logged.
        }
    }
}