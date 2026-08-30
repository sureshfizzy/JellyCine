package com.vela.app.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import kotlin.math.roundToInt

private val SubtitleSheetAccent = Color(0xFF7DD3FC)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleStyleSheet(
    playerPreferences: PlayerPreferences,
    onChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var scale by remember {
        mutableFloatStateOf(playerPreferences.getSubtitleScale())
    }
    var position by remember {
        mutableFloatStateOf(playerPreferences.getSubtitlePosition().toFloat())
    }
    var assCompatible by remember {
        mutableStateOf(playerPreferences.isSubtitleAssCompatible())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121214),
        scrimColor = Color.Black.copy(alpha = 0.18f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 4.dp)
                .padding(bottom = 20.dp)
        ) {
            SubtitleValueSlider(
                label = stringResource(R.string.player_subtitle_scale),
                valueText = stringResource(R.string.player_subtitle_scale_factor, scale),
                value = scale,
                valueRange = PlayerPreferences.MIN_SUBTITLE_SCALE..PlayerPreferences.MAX_SUBTITLE_SCALE,
                onValueChange = { value ->
                    scale = value
                    playerPreferences.setSubtitleScale(value)
                    onChanged()
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            SubtitleValueSlider(
                label = stringResource(R.string.player_subtitle_position),
                valueText = stringResource(
                    R.string.player_subtitle_position_value,
                    position.roundToInt()
                ),
                value = position,
                valueRange = 0f..PlayerPreferences.MAX_SUBTITLE_EDGE_PERCENT.toFloat(),
                startHint = stringResource(R.string.player_subtitle_position_bottom),
                endHint = stringResource(R.string.player_subtitle_position_top),
                onValueChange = { value ->
                    position = value
                    playerPreferences.setSubtitleBottomEdgePositionPercent(value.roundToInt())
                    onChanged()
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.player_subtitle_ass_compatible),
                        color = Color.White.copy(alpha = 0.86f),
                        fontSize = 14.sp
                    )
                    Text(
                        text = stringResource(R.string.player_subtitle_ass_compatible_summary),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = assCompatible,
                    onCheckedChange = { enabled ->
                        assCompatible = enabled
                        playerPreferences.setSubtitleAssCompatible(enabled)
                        onChanged()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SubtitleSheetAccent,
                        checkedBorderColor = SubtitleSheetAccent,
                        uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.18f)
                    )
                )
            }
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
        scrimColor = Color.Black.copy(alpha = 0.18f),
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
private fun SubtitleValueSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    startHint: String? = null,
    endHint: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = subtitleSliderColors()
        )
        if (startHint != null || endHint != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = startHint.orEmpty(),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp
                )
                Text(
                    text = endHint.orEmpty(),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun subtitleSliderColors() = SliderDefaults.colors(
    thumbColor = SubtitleSheetAccent,
    activeTrackColor = SubtitleSheetAccent,
    inactiveTrackColor = Color.White.copy(alpha = 0.18f)
)
