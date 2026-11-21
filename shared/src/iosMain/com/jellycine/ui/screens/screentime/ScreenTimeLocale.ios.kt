package com.jellycine.ui.screens.screentime

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import platform.Foundation.*

actual fun localeFirstDayOfWeek(): DayOfWeek {
    val first = NSCalendar.currentCalendar.firstWeekday.toInt()
    return if (first == 1) DayOfWeek.SUNDAY else DayOfWeek(first - 1)
}

actual fun localizedShortDayName(day: DayOfWeek): String {
    val symbols = NSDateFormatter().apply { locale = NSLocale.currentLocale }.shortWeekdaySymbols
    val index = if (day == DayOfWeek.SUNDAY) 0 else day.ordinal + 1
    return symbols.getOrNull(index) as? String ?: day.name.take(3)
}

actual fun localizedShortMonthName(month: Month): String {
    val symbols = NSDateFormatter().apply { locale = NSLocale.currentLocale }.shortMonthSymbols
    return symbols.getOrNull(month.ordinal) as? String ?: month.name.take(3)
}

actual fun formatFullDate(date: LocalDate): String = format("d MMM yyyy", date, null)

actual fun formatWatchedDateTime(dateTime: LocalDateTime): String =
    "${format("d MMM", dateTime.date, null)}, ${format("h:mm a", dateTime.date, dateTime)}"

private fun format(pattern: String, date: LocalDate, time: LocalDateTime?): String {
    val calendar = NSCalendar.currentCalendar
    val components = NSDateComponents().apply {
        year = date.year.toLong()
        month = date.month.ordinal.toLong() + 1
        day = date.day.toLong()
        hour = (time?.hour ?: 0).toLong()
        minute = (time?.minute ?: 0).toLong()
    }
    val nsDate = calendar.dateFromComponents(components) ?: return date.toString()
    val formatter = NSDateFormatter().apply {
        locale = NSLocale.currentLocale
        dateFormat = pattern
    }
    return formatter.stringFromDate(nsDate)
}