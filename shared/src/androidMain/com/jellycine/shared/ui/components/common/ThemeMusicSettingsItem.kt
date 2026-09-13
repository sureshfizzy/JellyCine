package com.jellycine.shared.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jellycine.shared.R
import com.jellycine.shared.preferences.Preferences
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeMusicSettingsItem(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    accentColor: Color,
    volume: Float = 1f,
    onVolumeChanged: (Float) -> Unit = {}
) {
    val options = listOf(
        Preferences.THEME_MUSIC_NO to stringResource(R.string.interface_theme_music_no),
        Preferences.THEME_MUSIC_ONCE to stringResource(R.string.interface_theme_music_once),
        Preferences.THEME_MUSIC_ENDLESS to stringResource(R.string.interface_theme_music_endless)
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MusicNote, null, tint = accentColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(stringResource(R.string.interface_theme_music), style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    stringResource(R.string.interface_theme_music_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
        ) {
            options.forEachIndexed { index, (mode, label) ->
                val selected = mode == selectedMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (selected) accentColor else Color.Transparent)
                        .clickable { onModeSelected(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.78f)
                    )
                }
                if (index < options.lastIndex) {
                    Spacer(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    )
                }
            }
        }
        AnimatedVisibility(visible = selectedMode != Preferences.THEME_MUSIC_NO) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.interface_theme_music_volume),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${(volume * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.VolumeDown,
                        null,
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(18.dp)
                    )
                    val sliderColors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.20f)
                    )
                    Slider(
                        value = volume,
                        onValueChange = onVolumeChanged,
                        valueRange = 0f..1f,
                        colors = sliderColors,
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(accentColor, CircleShape)
                            )
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                colors = sliderColors,
                                modifier = Modifier.height(4.dp),
                                thumbTrackGapSize = 0.dp,
                                drawStopIndicator = null
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Icon(
                        Icons.Rounded.VolumeUp,
                        null,
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
