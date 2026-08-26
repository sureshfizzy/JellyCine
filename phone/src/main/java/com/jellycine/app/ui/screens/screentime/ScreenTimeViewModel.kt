package com.jellycine.app.ui.screens.screentime

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.data.repository.computeScreenTimeStats
import com.jellycine.data.repository.loadScreenTimeItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ScreenTimeViewModel(context: Context) : ViewModel() {

    private val mediaRepository = MediaRepositoryProvider.getInstance(context)

    private val _screenTimeState = MutableStateFlow(ScreenTimeUiState())
    val screenTimeState: StateFlow<ScreenTimeUiState> = _screenTimeState.asStateFlow()

    init {
        loadScreenTime()
    }

    fun loadScreenTime(period: ScreenTimePeriod = ScreenTimePeriod.WEEK, year: Int = LocalDate.now().year) {
        _screenTimeState.value = ScreenTimeUiState(period = period, year = year, isLoading = true)
        viewModelScope.launch {
            val today = LocalDate.now()
            val (startDate, endDate) = when (period) {
                ScreenTimePeriod.WEEK -> today.minusDays(6) to today
                ScreenTimePeriod.MONTH -> today.minusDays(29) to today
                ScreenTimePeriod.YEAR -> {
                    val start = LocalDate.of(year, 1, 1)
                    val end = if (year == today.year) today else LocalDate.of(year, 12, 31)
                    start to end
                }
            }

            val prevDays = ChronoUnit.DAYS.between(startDate, endDate) + 1
            val prevEnd = startDate.minusDays(1)
            val prevStart = prevEnd.minusDays(prevDays - 1)

            val currentItems = loadScreenTimeItems(mediaRepository, startDate, endDate)
            val previousItems = loadScreenTimeItems(mediaRepository, prevStart, prevEnd)

            val stats = withContext(Dispatchers.Default) {
                computeScreenTimeStats(currentItems, previousItems, startDate, endDate)
            }
            _screenTimeState.value = _screenTimeState.value.copy(
                stats = stats,
                isLoading = false
            )
        }
    }
}

class ScreenTimeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ScreenTimeViewModel(context) as T
    }
}
