package com.jellycine.data.repository

import com.jellycine.data.model.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

private const val TICKS_PER_MINUTE = 600_000_000L

fun BaseItemDto.runMinutes(): Long = (runTimeTicks ?: 0L) / TICKS_PER_MINUTE

fun currentLocalDate(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

suspend fun loadScreenTimeItems(mediaRepository: MediaRepository): List<BaseItemDto> {
    val result = mediaRepository.getUserItems(
        includeItemTypes = "Movie,Episode",
        recursive = true,
        sortBy = "DatePlayed",
        sortOrder = "Descending",
        filters = "IsPlayed",
        fields = "SeriesName,SeriesId,IndexNumber,ParentIndexNumber,RunTimeTicks,UserData,UserDataLastPlayedDate,DateCreated,ProviderIds",
        enableUserData = true
    ).getOrNull() ?: return emptyList()

    return result.items.orEmpty()
        .filter { it.id != null && it.type in setOf("Movie", "Episode") && it.userData?.played == true }
        .filter { item -> !isBriefReopen(item) }
        .distinctBy { item -> dedupKey(item) ?: item.id }
}

fun filterScreenTimeItems(items: List<BaseItemDto>, startDate: LocalDate, endDate: LocalDate): List<BaseItemDto> {
    return items.filter { item ->
        val itemDate = resolveItemDate(item) ?: return@filter false
        itemDate >= startDate && itemDate <= endDate
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
    val daysInPeriod = startDate.daysUntil(endDate) + 1L

    val totalMinutes = current.sumOf { it.runMinutes() }
    val prevTotalMinutes = previous.sumOf { it.runMinutes() }

    val dailyAvg = if (daysInPeriod > 0) totalMinutes / daysInPeriod else 0L
    val prevDailyAvg = if (daysInPeriod > 0) prevTotalMinutes / daysInPeriod else 0L

    val dailyBreakdown = buildDailyBreakdown(current, startDate, endDate)
    val peakHours = buildPeakHours(current)

    val movieMinutes = current.filter { it.type == "Movie" }
        .sumOf { it.runMinutes() }
    val showMinutes = current.filter { it.type == "Episode" }
        .sumOf { it.runMinutes() }

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
        .mapValues { (_, dayItems) -> dayItems.sumOf { it.runMinutes() } }

    val result = mutableListOf<DailyWatchTime>()
    var date = startDate
    while (date <= endDate) {
        result.add(DailyWatchTime(dateEpochDay = date.toEpochDays().toLong(), minutes = minutesByDate[date] ?: 0L))
        date = date.plus(1, DateTimeUnit.DAY)
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

fun BaseItemDto.screenTimeDateString(): String? = userData?.lastPlayedDate ?: dateCreated

private fun resolveItemDate(item: BaseItemDto): LocalDate? {
    return parseLocalDate(item.userData?.lastPlayedDate)
        ?: parseLocalDate(item.dateCreated)
}

private fun resolveItemHour(item: BaseItemDto): Int? {
    return parseHour(item.userData?.lastPlayedDate)
        ?: parseHour(item.dateCreated)
}

fun parseScreenTimeDateTime(dateStr: String?): LocalDateTime? {
    if (dateStr.isNullOrBlank()) return null
    val zone = TimeZone.currentSystemDefault()
    return try {
        Instant.parse(dateStr).toLocalDateTime(zone)
    } catch (_: Exception) {
        try {
            LocalDateTime.parse(dateStr)
        } catch (_: Exception) {
            null
        }
    }
}

private fun isBriefReopen(item: BaseItemDto): Boolean {
    val position = item.userData?.playbackPositionTicks ?: return false
    return position > 0L
}

private fun parseLocalDate(dateStr: String?): LocalDate? = parseScreenTimeDateTime(dateStr)?.date

private fun parseHour(dateStr: String?): Int? = parseScreenTimeDateTime(dateStr)?.hour
