package com.jellycine.ui.screens.screentime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellycine.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus

class ScreenTimeViewModel(private val mediaRepository: MediaRepository) : ViewModel() {

    suspend fun getItemImageUrl(itemId: String, seriesId: String? = null, type: String? = null): String? {
        val targetId = if (type.equals("Episode", ignoreCase = true) && !seriesId.isNullOrBlank()) seriesId else itemId
        return mediaRepository.getImageUrlString(
            itemId = targetId,
            imageType = "Primary",
            width = 200,
            quality = 85,
            enableImageEnhancers = false
        )
    }

    private val _screenTimeState = MutableStateFlow(ScreenTimeUiState())
    val screenTimeState: StateFlow<ScreenTimeUiState> = _screenTimeState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadScreenTime()
    }

    fun loadScreenTime(
        period: ScreenTimePeriod = ScreenTimePeriod.WEEK,
        year: Int = currentLocalDate().year,
        monthOffset: Int = 0,
        weekOffset: Int = 0
    ) {
        _screenTimeState.value = ScreenTimeUiState(
            period = period,
            year = year,
            monthOffset = monthOffset,
            weekOffset = weekOffset,
            isLoading = true
        )
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val (startDate, endDate) = resolveRange(period, currentLocalDate(), year, monthOffset, weekOffset)

            val prevDays = startDate.daysUntil(endDate) + 1
            val prevEnd = startDate.minus(1, DateTimeUnit.DAY)
            val prevStart = prevEnd.minus(prevDays - 1, DateTimeUnit.DAY)

            val playedItems = loadScreenTimeItems(mediaRepository)
            val currentItems = filterScreenTimeItems(playedItems, startDate, endDate)
            val previousItems = filterScreenTimeItems(playedItems, prevStart, prevEnd)

            val stats = withContext(Dispatchers.Default) {
                computeScreenTimeStats(currentItems, previousItems, startDate, endDate)
            }
            _screenTimeState.value = _screenTimeState.value.copy(
                stats = stats,
                items = currentItems,
                isLoading = false
            )
        }
    }
}
