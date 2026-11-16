package com.jellycine.shared.util

fun formatRuntime(ticks: Long?): String {
    if (ticks == null) return ""
    val minutes = (ticks / 10000000) / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m"
}
