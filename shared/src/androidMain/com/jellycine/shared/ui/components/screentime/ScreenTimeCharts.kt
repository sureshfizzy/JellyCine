package com.jellycine.shared.ui.components.screentime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.data.model.DailyWatchTime
import com.jellycine.data.model.PeakHourBucket
import com.jellycine.shared.R
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

private val BarColor = Color(0xFF22D3EE)
private val BarColorDark = Color(0xFF0891B2)
private val SubtextColor = Color.White.copy(alpha = 0.68f)

private data class ChartBucket(val label: String, val minutes: Long)

@Composable
fun DailyBreakdownChart(
    dailyData: List<DailyWatchTime>,
    selectedIndex: Int? = null,
    onBucketClick: ((bucketIndex: Int) -> Unit)? = null,
    onClearSelection: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val buckets = aggregateBuckets(dailyData)
    val maxMinutes = buckets.maxOfOrNull { it.minutes } ?: 1L

    val barStartX = remember { mutableFloatStateOf(0f) }
    val barWidthPx = remember { mutableFloatStateOf(0f) }
    val barSpacingPx = remember { mutableFloatStateOf(0f) }
    val barCountState = remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.screen_time_daily_breakdown),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            if (selectedIndex != null && onClearSelection != null) {
                Text(
                    text = "✕",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { onClearSelection() }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .then(
                    if (onBucketClick != null) Modifier.pointerInput(buckets.size) {
                        detectTapGestures { offset ->
                            val count = barCountState.intValue
                            if (count == 0) return@detectTapGestures
                            val bw = barWidthPx.floatValue
                            val bs = barSpacingPx.floatValue
                            val sx = barStartX.floatValue
                            val tappedIndex = ((offset.x - sx) / (bw + bs)).toInt()
                            if (tappedIndex in 0 until count) onBucketClick(tappedIndex)
                        }
                    } else Modifier
                )
        ) {
            val barCount = buckets.size
            if (barCount == 0) return@Canvas

            val labelHeight = 24.dp.toPx()
            val topLabelHeight = 20.dp.toPx()
            val chartHeight = size.height - labelHeight - topLabelHeight
            val barSpacing = if (barCount <= 7) 6.dp.toPx() else 3.dp.toPx()
            val totalSpacing = barSpacing * (barCount - 1)
            val barWidth = ((size.width - totalSpacing) / barCount).coerceAtMost(40.dp.toPx())
            val totalBarsWidth = barWidth * barCount + totalSpacing
            val startX = (size.width - totalBarsWidth) / 2

            barStartX.floatValue = startX
            barWidthPx.floatValue = barWidth
            barSpacingPx.floatValue = barSpacing
            barCountState.intValue = barCount

            buckets.forEachIndexed { index, bucket ->
                val x = startX + index * (barWidth + barSpacing)
                val barHeight = if (maxMinutes > 0)
                    (bucket.minutes.toFloat() / maxMinutes) * chartHeight
                else 0f

                if (barHeight > 0) {
                    val barY = topLabelHeight + chartHeight - barHeight
                    val isSelected = selectedIndex == null || selectedIndex == index
                    val barColor = if (bucket.minutes == maxMinutes) BarColor else BarColorDark
                    drawRoundRect(
                        color = if (isSelected) barColor else barColor.copy(alpha = 0.25f),
                        topLeft = Offset(x, barY),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )

                    if (barCount <= 12) {
                        val timeLabel = formatMinutesShort(bucket.minutes)
                        val timeMeasured = textMeasurer.measure(
                            timeLabel,
                            style = TextStyle(fontSize = 9.sp, color = Color.White)
                        )
                        drawText(
                            textLayoutResult = timeMeasured,
                            topLeft = Offset(
                                x + (barWidth - timeMeasured.size.width) / 2,
                                barY - timeMeasured.size.height - 2.dp.toPx()
                            )
                        )
                    }
                }

                val dayMeasured = textMeasurer.measure(
                    bucket.label,
                    style = TextStyle(fontSize = 10.sp, color = SubtextColor)
                )
                drawText(
                    textLayoutResult = dayMeasured,
                    topLeft = Offset(
                        x + (barWidth - dayMeasured.size.width) / 2,
                        topLabelHeight + chartHeight + 6.dp.toPx()
                    )
                )
            }
        }
    }
}

private fun aggregateBuckets(dailyData: List<DailyWatchTime>): List<ChartBucket> {
    if (dailyData.isEmpty()) return emptyList()

    val days = dailyData.size

    return when {
        days <= 7 -> {
            dailyData.map { data ->
                val date = LocalDate.ofEpochDay(data.dateEpochDay)
                val label = date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.getDefault())
                ChartBucket(label, data.minutes)
            }
        }
        days <= 31 -> {
            dailyData.groupBy { data ->
                val date = LocalDate.ofEpochDay(data.dateEpochDay)
                val weekField = java.time.temporal.WeekFields.of(Locale.getDefault()).weekOfMonth()
                date.get(weekField)
            }.toSortedMap().entries.mapIndexed { index, (_, weekDays) ->
                val totalMinutes = weekDays.sumOf { it.minutes }
                ChartBucket("W${index + 1}", totalMinutes)
            }
        }
        else -> {
            dailyData.groupBy { data ->
                val date = LocalDate.ofEpochDay(data.dateEpochDay)
                date.month
            }.entries.sortedBy { it.key }.map { (month, monthDays) ->
                val totalMinutes = monthDays.sumOf { it.minutes }
                val label = month.getDisplayName(DateTextStyle.SHORT, Locale.getDefault())
                ChartBucket(label, totalMinutes)
            }
        }
    }
}

@Composable
fun PeakHoursChart(
    peakHours: List<PeakHourBucket>,
    selectedIndex: Int? = null,
    onBucketClick: ((bucketIndex: Int) -> Unit)? = null,
    onClearSelection: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val maxCount = peakHours.maxOfOrNull { it.count } ?: 1

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.screen_time_peak_hours),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            if (selectedIndex != null && onClearSelection != null) {
                Text(
                    text = "✕",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { onClearSelection() }
                )
            }
        }

        peakHours.forEachIndexed { index, bucket ->
            val localizedLabel = when (bucket.label) {
                "Morning" -> stringResource(R.string.screen_time_morning)
                "Afternoon" -> stringResource(R.string.screen_time_afternoon)
                "Evening" -> stringResource(R.string.screen_time_evening)
                "Night" -> stringResource(R.string.screen_time_night)
                else -> bucket.label
            }
            val isActive = selectedIndex == null || selectedIndex == index
            Row(
                modifier = Modifier.fillMaxWidth()
                    .then(if (onBucketClick != null) Modifier.clickable { onBucketClick(index) } else Modifier)
                    .then(if (!isActive) Modifier.alpha(0.3f) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = localizedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(0.28f)
                )

                Box(modifier = Modifier.weight(0.58f)) {
                    val fraction = if (maxCount > 0) bucket.count.toFloat() / maxCount else 0f
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                    ) {
                        if (fraction > 0f) {
                            drawRoundRect(
                                color = BarColor,
                                topLeft = Offset.Zero,
                                size = Size(size.width * fraction, size.height),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                        } else {
                            drawRoundRect(
                                color = BarColor.copy(alpha = 0.15f),
                                topLeft = Offset.Zero,
                                size = Size(4.dp.toPx(), size.height),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                        }
                    }
                }

                Text(
                    text = bucket.count.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(0.14f)
                )
            }
        }
    }
}

private fun formatMinutesShort(minutes: Long): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours == 0L -> "${mins}m"
        mins == 0L -> "${hours}h"
        else -> "${hours}h${mins}m"
    }
}