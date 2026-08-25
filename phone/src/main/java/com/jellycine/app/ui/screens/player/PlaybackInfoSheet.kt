package com.jellycine.app.ui.screens.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.data.model.BaseItemDto
import com.jellycine.shared.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackInfoSheet(
    item: BaseItemDto?,
    title: String,
    onDismiss: () -> Unit,
    onPlayFromStart: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF111111)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = item?.name ?: title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            val meta = buildPlaybackMeta(item)
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            val overview = item?.overview.orEmpty()
            if (overview.isNotBlank()) {
                Text(
                    text = overview,
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onPlayFromStart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316))
            ) {
                Text(stringResource(R.string.player_play_from_start))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun buildPlaybackMeta(item: BaseItemDto?): String {
    if (item == null) return ""
    val parts = mutableListOf<String>()
    item.communityRating?.let { parts += String.format("%.1f", it) }
    item.productionYear?.let { parts += it.toString() }
    item.runTimeTicks?.let { ticks ->
        val minutes = (ticks / 10_000_000L / 60L).toInt()
        if (minutes > 0) {
            val hours = minutes / 60
            val remain = minutes % 60
            parts += if (hours > 0) "${hours}h ${remain}m" else "${remain}m"
        }
    }
    item.officialRating?.takeIf { it.isNotBlank() }?.let { parts += it }
    return parts.joinToString("  ·  ")
}
