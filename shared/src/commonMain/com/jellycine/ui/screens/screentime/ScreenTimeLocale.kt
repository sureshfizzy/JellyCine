package com.jellycine.ui.screens.screentime

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month

expect fun localeFirstDayOfWeek(): DayOfWeek

expect fun localizedShortDayName(day: DayOfWeek): String

expect fun localizedShortMonthName(month: Month): String

/** e.g. "5 Nov 2026" */
expect fun formatFullDate(date: LocalDate): String

/** e.g. "5 Nov, 9:30 PM" */
expect fun formatWatchedDateTime(dateTime: LocalDateTime): String
