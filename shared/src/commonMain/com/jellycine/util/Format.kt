package com.jellycine.util

import kotlin.math.pow
import kotlin.math.roundToLong

/** Locale independent fixed decimal formatting, e.g. formatDecimal(7.64, 1) == "7.6". */
fun formatDecimal(value: Double, decimals: Int): String {
    if (value.isNaN() || value.isInfinite()) return value.toString()
    if (decimals <= 0) return value.roundToLong().toString()
    val factor = 10.0.pow(decimals)
    val scaled = (kotlin.math.abs(value) * factor).roundToLong()
    val whole = scaled / factor.toLong()
    val fraction = (scaled % factor.toLong()).toString().padStart(decimals, '0')
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    return "$sign$whole.$fraction"
}

fun formatDecimal(value: Float, decimals: Int): String = formatDecimal(value.toDouble(), decimals)