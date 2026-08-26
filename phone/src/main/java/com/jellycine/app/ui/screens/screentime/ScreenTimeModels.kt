package com.jellycine.app.ui.screens.screentime

import com.jellycine.data.model.ScreenTimeStats
import java.time.LocalDate

enum class ScreenTimePeriod {
    WEEK, MONTH, YEAR
}

data class ScreenTimeUiState(
    val period: ScreenTimePeriod = ScreenTimePeriod.WEEK,
    val year: Int = LocalDate.now().year,
    val stats: ScreenTimeStats? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)
