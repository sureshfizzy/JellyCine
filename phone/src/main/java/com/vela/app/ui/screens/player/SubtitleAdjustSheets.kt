package com.vela.app.ui.screens.player

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vela.player.preferences.PlayerPreferences
import com.vela.shared.R
import kotlin.math.roundToInt

private val SubtitleSheetAccent = Color(0xFF7DD3FC)
private val SubtitlePanelColor = Color(0xE6161618)
private val SubtitlePanelShape = RoundedCornerShape(20.dp)

@Composable
fun SubtitleStyleSheet(
    playerPreferences: PlayerPreferences,
    onChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    var scale by remember {
        mutableFloatStateOf(playerPreferences.getSubtitleScale())
    }
    var position by remember {
        mutableFloatStateOf(playerPreferences.getSubtitlePosition().toFloat())
    }
    var assCompatible by remember {
        mutableStateOf(playerPreferences.isSubtitleAssCompatible())
    }

    BackHandler(onBack = onDismiss)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )

        val videoHeight = minOf(
            maxHeight,
            maxWidth / PlayerPreferences.DEFAULT_VIDEO_ASPECT
        )
        val bottomLetterbox = ((maxHeight - videoHeight) / 2).coerceAtLeast(0.dp)
        val portraitPanelMaxHeight = if (bottomLetterbox > 24.dp) {
            bottomLetterbox - 12.dp
        } else {
            maxHeight * 0.42f
        }
        val panelModifier = if (isPortrait) {
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 28.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .heightIn(max = portraitPanelMaxHeight)
                .navigationBarsPadding()
                .padding(bottom = 10.dp)
        } else {
            Modifier
                .align(Alignment.CenterEnd)
                .windowInsetsPadding(
                    WindowInsets.displayCutout.only(WindowInsetsSides.End)
                )
                .padding(end = 16.dp)
                .width(280.dp)
        }

        Column(
            modifier = panelModifier
                .clip(SubtitlePanelShape)
                .background(SubtitlePanelColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
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
            SubtitleValueSlider(
                label = stringResource(R.string.player_subtitle_position),
                valueText = stringResource(
                    R.string.player_subtitle_position_value,
                    position.roundToInt()
                ),
                value = position,
                valueRange = 0f..PlayerPreferences.MAX_SUBTITLE_EDGE_PERCENT.toFloat(),
                onValueChange = { value ->
                    position = value
                    playerPreferences.setSubtitleBottomEdgePositionPercent(value.roundToInt())
                    onChanged()
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.player_subtitle_ass_compatible),
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
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
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.86f),
                fontSize = 13.sp
            )
            Text(
                text = valueText,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = subtitleSliderColors()
        )
    }
}

@Composable
private fun subtitleSliderColors() = SliderDefaults.colors(
    thumbColor = SubtitleSheetAccent,
    activeTrackColor = SubtitleSheetAccent,
    inactiveTrackColor = Color.White.copy(alpha = 0.18f)
)
