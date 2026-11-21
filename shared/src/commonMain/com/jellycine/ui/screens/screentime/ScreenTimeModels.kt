package com.jellycine.ui.screens.screentime

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.ScreenTimeStats
import com.jellycine.data.repository.currentLocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

enum class ScreenTimePeriod {
    WEEK, MONTH, YEAR
}

data class ScreenTimeUiState(
    val period: ScreenTimePeriod = ScreenTimePeriod.WEEK,
    val year: Int = currentLocalDate().year,
    val monthOffset: Int = 0,
    val weekOffset: Int = 0,
    val stats: ScreenTimeStats? = null,
    val items: List<BaseItemDto> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

internal fun weekStartOf(date: LocalDate): LocalDate {
    val back = (date.dayOfWeek.ordinal - localeFirstDayOfWeek().ordinal + 7) % 7
    return date.minus(back, DateTimeUnit.DAY)
}

internal fun resolveRange(
    period: ScreenTimePeriod,
    today: LocalDate,
    year: Int,
    monthOffset: Int,
    weekOffset: Int
): Pair<LocalDate, LocalDate> = when (period) {
    ScreenTimePeriod.WEEK -> {
        val start = weekStartOf(today).plus(weekOffset, DateTimeUnit.WEEK)
        start to if (weekOffset == 0) today else start.plus(6, DateTimeUnit.DAY)
    }
    ScreenTimePeriod.MONTH -> {
        val target = today.plus(monthOffset, DateTimeUnit.MONTH)
        val start = LocalDate(target.year, target.month, 1)
        val end = if (monthOffset == 0) today else start.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        start to end
    }
    ScreenTimePeriod.YEAR -> LocalDate(year, 1, 1) to
        if (year == today.year) today else LocalDate(year, 12, 31)
}

/** 0-based week-of-month for [date], using the locale's first day of week. */
internal fun weekOfMonth(date: LocalDate): Int {
    val lead = (LocalDate(date.year, date.month, 1).dayOfWeek.ordinal - localeFirstDayOfWeek().ordinal + 7) % 7
    return (date.day - 1 + lead) / 7
}
