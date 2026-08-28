package com.jellycine.data.repository

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.DailyWatchTime
import com.jellycine.data.model.PeakHourBucket
import com.jellycine.data.model.ScreenTimeStats
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val TICKS_PER_MINUTE = 600_000_000L

suspend fun loadScreenTimeItems(
    mediaRepository: MediaRepository,
    startDate: LocalDate,
    endDate: LocalDate
): List<BaseItemDto> {
    val result = mediaRepository.getUserItems(
        includeItemTypes = "Movie,Episode",
        recursive = true,
        sortBy = "DatePlayed",
        sortOrder = "Descending",
        filters = "IsPlayed",
        fields = "SeriesName,SeriesId,IndexNumber,ParentIndexNumber,RunTimeTicks,UserData,UserDataLastPlayedDate,DateCreated,ProviderIds",
        enableUserData = true
    ).getOrNull() ?: return emptyList()

    val allItems = result.items.orEmpty()
        .filter { it.id != null && it.type in setOf("Movie", "Episode") && it.userData?.played == true }
        .filter { item -> !isBriefReopen(item) }
        .distinctBy { item -> dedupKey(item) ?: item.id }

    return allItems.filter { item ->
        val itemDate = resolveItemDate(item) ?: return@filter false
        !itemDate.isBefore(startDate) && !itemDate.isAfter(endDate)
    }
}

private fun dedupKey(item: BaseItemDto): String? {
    return when (item.type) {
        "Episode" -> {
            val tvdbId = item.providerIds?.entries
                ?.firstOrNull { (key, value) -> key.equals("Tvdb", ignoreCase = true) && value.isNotBlank() }
                ?.value
            if (tvdbId != null) return "Episode:tvdb:$tvdbId"
            val seriesId = item.seriesId ?: return null
            val season = item.parentIndexNumber ?: return null
            val episode = item.indexNumber ?: return null
            "Episode:$seriesId:s$season:e$episode"
        }
        else -> {
            val tmdbId = item.providerIds?.entries
                ?.firstOrNull { (key, value) -> key.equals("Tmdb", ignoreCase = true) && value.isNotBlank() }
                ?.value ?: return null
            "${item.type}:tmdb:$tmdbId"
        }
    }
}

fun computeScreenTimeStats(
    current: List<BaseItemDto>,
    previous: List<BaseItemDto>,
    startDate: LocalDate,
    endDate: LocalDate
): ScreenTimeStats {
    val daysInPeriod = ChronoUnit.DAYS.between(startDate, endDate) + 1

    val totalMinutes = current.sumOf { it.runTimeTicks ?: 0L } / TICKS_PER_MINUTE
    val prevTotalMinutes = previous.sumOf { it.runTimeTicks ?: 0L } / TICKS_PER_MINUTE

    val dailyAvg = if (daysInPeriod > 0) totalMinutes / daysInPeriod else 0L
    val prevDailyAvg = if (daysInPeriod > 0) prevTotalMinutes / daysInPeriod else 0L

    val dailyBreakdown = buildDailyBreakdown(current, startDate, endDate)
    val peakHours = buildPeakHours(current)

    val movieMinutes = current.filter { it.type == "Movie" }
        .sumOf { it.runTimeTicks ?: 0L } / TICKS_PER_MINUTE
    val showMinutes = current.filter { it.type == "Episode" }
        .sumOf { it.runTimeTicks ?: 0L } / TICKS_PER_MINUTE

    return ScreenTimeStats(
        totalMinutes = totalMinutes,
        dailyAverageMinutes = dailyAvg,
        totalDelta = totalMinutes - prevTotalMinutes,
        dailyAverageDelta = dailyAvg - prevDailyAvg,
        dailyBreakdown = dailyBreakdown,
        peakHours = peakHours,
        showMinutes = showMinutes,
        movieMinutes = movieMinutes
    )
}

private fun buildDailyBreakdown(
    items: List<BaseItemDto>,
    startDate: LocalDate,
    endDate: LocalDate
): List<DailyWatchTime> {
    val minutesByDate = items.groupBy { resolveItemDate(it) }
        .filterKeys { it != null }
        .mapKeys { it.key!! }
        .mapValues { (_, dayItems) -> dayItems.sumOf { it.runTimeTicks ?: 0L } / TICKS_PER_MINUTE }

    val result = mutableListOf<DailyWatchTime>()
    var date = startDate
    while (!date.isAfter(endDate)) {
        result.add(DailyWatchTime(dateEpochDay = date.toEpochDay(), minutes = minutesByDate[date] ?: 0L))
        date = date.plusDays(1)
    }
    return result
}

private fun buildPeakHours(items: List<BaseItemDto>): List<PeakHourBucket> {
    val hours = items.mapNotNull { resolveItemHour(it) }
    val morning = hours.count { it in 6..11 }
    val afternoon = hours.count { it in 12..17 }
    val evening = hours.count { it in 18..23 }
    val night = hours.count { it in 0..5 }

    return listOf(
        PeakHourBucket("Morning", morning),
        PeakHourBucket("Afternoon", afternoon),
        PeakHourBucket("Evening", evening),
        PeakHourBucket("Night", night)
    )
}

private fun resolveItemDate(item: BaseItemDto): LocalDate? {
    return parseLocalDate(item.userData?.lastPlayedDate)
        ?: parseLocalDate(item.dateCreated)
}

private fun resolveItemHour(item: BaseItemDto): Int? {
    return parseHour(item.userData?.lastPlayedDate)
        ?: parseHour(item.dateCreated)
}

private fun parseZoned(dateStr: String?): java.time.ZonedDateTime? {
    if (dateStr.isNullOrBlank()) return null
    val zone = ZoneId.systemDefault()
    return try {
        Instant.parse(dateStr).atZone(zone)
    } catch (_: Exception) {
        try {
            java.time.ZonedDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
        } catch (_: Exception) {
            try {
                java.time.LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone)
            } catch (_: Exception) { null }
        }
    }
}

private fun isBriefReopen(item: BaseItemDto): Boolean {
    val position = item.userData?.playbackPositionTicks ?: return false
    return position > 0L
}

private fun parseLocalDate(dateStr: String?): LocalDate? {
    return parseZoned(dateStr)?.toLocalDate()
}

private fun parseHour(dateStr: String?): Int? {
    return parseZoned(dateStr)?.hour
}