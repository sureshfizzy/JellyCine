package com.vela.app.ui.screens.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vela.player.preferences.PlayerPreferences
import com.vela.shared.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleStyleSheet(
    playerPreferences: PlayerPreferences,
    onChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sizeOptions = PlayerPreferences.SUBTITLE_TEXT_SIZE_OPTIONS
    var sizeIndex by remember {
        mutableFloatStateOf(
            sizeOptions.indexOf(playerPreferences.getSubtitleTextSize())
                .coerceAtLeast(0)
                .toFloat()
        )
    }
    var position by remember {
        mutableFloatStateOf(playerPreferences.getSubtitlePosition().toFloat())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121214),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.player_subtitle_scale_position),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.player_subtitle_scale),
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp
            )
            Slider(
                value = sizeIndex,
                onValueChange = { value ->
                    sizeIndex = value
                    val selected = sizeOptions.getOrNull(value.toInt()) ?: return@Slider
                    playerPreferences.setSubtitleTextSize(selected)
                    onChanged()
                },
                valueRange = 0f..(sizeOptions.lastIndex.toFloat()),
                steps = (sizeOptions.size - 2).coerceAtLeast(0),
                colors = subtitleSliderColors()
            )
            Text(
                text = sizeOptions.getOrNull(sizeIndex.toInt()).orEmpty(),
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.player_subtitle_position),
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp
            )
            Slider(
                value = position,
                onValueChange = { value ->
                    position = value
                    playerPreferences.setSubtitleBottomEdgePositionPercent(value.toInt())
                    onChanged()
                },
                valueRange = 0f..50f,
                colors = subtitleSliderColors()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleDelaySheet(
    playerPreferences: PlayerPreferences,
    onChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var delayMs by remember {
        mutableFloatStateOf(playerPreferences.getSubtitleDelayMs().toFloat())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121214),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.player_subtitle_time_offset),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(
                    R.string.player_subtitle_delay_value,
                    (delayMs / 1000f)
                ),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 14.sp
            )
            Slider(
                value = delayMs,
                onValueChange = { value ->
                    delayMs = value
                    playerPreferences.setSubtitleDelayMs(value.toInt())
                    onChanged()
                },
                valueRange = PlayerPreferences.MIN_SUBTITLE_DELAY_MS.toFloat()..
                    PlayerPreferences.MAX_SUBTITLE_DELAY_MS.toFloat(),
                colors = subtitleSliderColors()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        delayMs = 0f
                        playerPreferences.setSubtitleDelayMs(0)
                        onChanged()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.player_subtitle_delay_reset),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun subtitleSliderColors() = SliderDefaults.colors(
    thumbColor = Color.White,
    activeTrackColor = Color.White,
    inactiveTrackColor = Color.White.copy(alpha = 0.18f)
)
