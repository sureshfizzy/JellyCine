package com.jellycine.ui.components.screentime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jellycine.resources.Res
import com.jellycine.resources.screen_time_no_change
import org.jetbrains.compose.resources.stringResource

private val CardBackground = Color(0xFF1A1A1A)
private val CardBorder = Color.White.copy(alpha = 0.08f)
private val PositiveColor = Color(0xFF4ADE80)
private val NegativeColor = Color(0xFFFF6B6B)
private val AccentBlue = Color(0xFF67E8F9)
private val SubtextColor = Color.White.copy(alpha = 0.5f)

@Composable
fun StatCard(
    label: String,
    value: String,
    delta: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = AccentBlue
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        if (delta != null) {
            DeltaIndicator(delta = delta)
        }
    }
}


@Composable
fun DeltaIndicator(delta: String) {
    val noChangeLabel = stringResource(Res.string.screen_time_no_change)
    val isNoChange = delta == NO_CHANGE_SENTINEL
    val isPositive = delta.startsWith("+")
    val color = when {
        isNoChange -> SubtextColor
        isPositive -> PositiveColor
        else -> NegativeColor
    }
    Text(
        text = if (isNoChange) noChangeLabel else delta,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.Medium
    )
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

fun formatMinutes(minutes: Long): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours == 0L -> "${mins}m"
        mins == 0L -> "${hours}h"
        else -> "${hours}h ${mins}m"
    }
}

private const val NO_CHANGE_SENTINEL = "__no_change__"

fun formatDelta(deltaMinutes: Long): String {
    if (deltaMinutes == 0L) return NO_CHANGE_SENTINEL
    val prefix = if (deltaMinutes > 0) "+" else ""
    return "$prefix${formatMinutes(kotlin.math.abs(deltaMinutes))}"
}