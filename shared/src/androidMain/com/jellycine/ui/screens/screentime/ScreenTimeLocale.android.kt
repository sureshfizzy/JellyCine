package com.jellycine.ui.screens.screentime

import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime

actual fun localeFirstDayOfWeek(): DayOfWeek =
    WeekFields.of(Locale.getDefault()).firstDayOfWeek.let { DayOfWeek(it.value) }

actual fun localizedShortDayName(day: DayOfWeek): String =
    java.time.DayOfWeek.of(day.ordinal + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault())

actual fun localizedShortMonthName(month: Month): String =
    java.time.Month.of(month.ordinal + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault())

actual fun formatFullDate(date: LocalDate): String =
    date.toJavaLocalDate().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

actual fun formatWatchedDateTime(dateTime: LocalDateTime): String {
    val java = dateTime.toJavaLocalDateTime()
    val date = java.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    val time = java.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    return "$date, $time"
}
