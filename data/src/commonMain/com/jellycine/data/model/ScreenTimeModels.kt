package com.jellycine.data.model

data class ScreenTimeStats(
    val totalMinutes: Long,
    val dailyAverageMinutes: Long,
    val totalDelta: Long,
    val dailyAverageDelta: Long,
    val dailyBreakdown: List<DailyWatchTime>,
    val peakHours: List<PeakHourBucket>,
    val showMinutes: Long,
    val movieMinutes: Long
)

data class DailyWatchTime(
    val dateEpochDay: Long,
    val minutes: Long
)

data class PeakHourBucket(
    val label: String,
    val count: Int
)