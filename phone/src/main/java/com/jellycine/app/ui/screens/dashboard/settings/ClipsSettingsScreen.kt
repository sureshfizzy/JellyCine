package com.jellycine.app.ui.screens.dashboard.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellycine.app.ui.components.common.AmoledMultiSelectionDialog
import com.jellycine.app.ui.components.common.SelectionOption
import com.jellycine.shared.R
import com.jellycine.preferences.Preferences

private val QUALITY_OPTIONS = listOf(0, 480, 720, 1080)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipsSettingsScreen(
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current
    val preferences = remember { Preferences(context) }
    val maxQuality by preferences.clipsMaxQuality()
        .collectAsStateWithLifecycle(initialValue = preferences.getClipsMaxQuality())
    val contentType by preferences.clipsContentType()
        .collectAsStateWithLifecycle(initialValue = preferences.getClipsContentType())
    val trendingWindow by preferences.clipsTrendingWindow()
        .collectAsStateWithLifecycle(initialValue = preferences.getClipsTrendingWindow())
    val excludedGenres by preferences.clipsExcludedGenres()
        .collectAsStateWithLifecycle(initialValue = preferences.getClipsExcludedGenres())

    val playbackColor = Color(0xFFEC4899)
    val contentColor = Color(0xFF8B5CF6)

    val autoLabel = stringResource(R.string.clips_quality_auto)
    fun qualityLabel(height: Int) = if (height == 0) autoLabel else "${height}p"

    val genreOptions = listOf(
        16 to stringResource(R.string.clips_genre_animation),
        35 to stringResource(R.string.clips_genre_comedy),
        99 to stringResource(R.string.clips_genre_documentary),
        10751 to stringResource(R.string.clips_genre_family),
        27 to stringResource(R.string.clips_genre_horror),
        10749 to stringResource(R.string.clips_genre_romance),
        10764 to stringResource(R.string.clips_genre_reality),
        10762 to stringResource(R.string.clips_genre_kids)
    )
    var showGenreDialog by remember { mutableStateOf(false) }

    val windowOptions = listOf(
        SelectionOption(Preferences.CLIPS_WINDOW_DAY, stringResource(R.string.clips_window_today), ""),
        SelectionOption(Preferences.CLIPS_WINDOW_WEEK, stringResource(R.string.clips_window_week), "", isDefault = true)
    )
    val contentOptions = listOf(
        SelectionOption(Preferences.CLIPS_CONTENT_ALL, stringResource(R.string.clips_content_all), "", isDefault = true),
        SelectionOption(Preferences.CLIPS_CONTENT_MOVIE, stringResource(R.string.clips_content_movies), ""),
        SelectionOption(Preferences.CLIPS_CONTENT_TV, stringResource(R.string.clips_content_shows), "")
    )

    BackHandler { onBackPressed() }

    Scaffold(
        topBar = {
            topbar(
                title = stringResource(R.string.settings_clips),
                onBackPressed = onBackPressed
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item { SectionLabel(stringResource(R.string.clips_section_playback)) }
            item {
                SettingsSection {
                    SelectionDialogSettingsItem(
                        icon = Icons.Rounded.HighQuality,
                        title = stringResource(R.string.clips_max_quality),
                        subtitle = qualityLabel(maxQuality),
                        selectedValue = maxQuality.toString(),
                        options = QUALITY_OPTIONS.map {
                            SelectionOption(it.toString(), qualityLabel(it), "", isDefault = it == 0)
                        },
                        onOptionSelected = { preferences.setClipsMaxQuality(it.toInt()) },
                        accentColor = playbackColor
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.clips_section_content)) }
            item {
                SettingsSection {
                    SelectionDialogSettingsItem(
                        icon = Icons.Rounded.Schedule,
                        title = stringResource(R.string.clips_trending_window),
                        subtitle = windowOptions.first { it.value == trendingWindow }.label,
                        selectedValue = trendingWindow,
                        options = windowOptions,
                        onOptionSelected = preferences::setClipsTrendingWindow,
                        accentColor = contentColor
                    )
                    SettingsDivider()
                    SelectionDialogSettingsItem(
                        icon = Icons.Rounded.Movie,
                        title = stringResource(R.string.clips_content_type),
                        subtitle = contentOptions.first { it.value == contentType }.label,
                        selectedValue = contentType,
                        options = contentOptions,
                        onOptionSelected = preferences::setClipsContentType,
                        accentColor = contentColor
                    )
                    SettingsDivider()
                    BaseSettingsItem(
                        icon = Icons.Rounded.Block,
                        title = stringResource(R.string.clips_exclude_genres),
                        subtitle = genreOptions
                            .filter { it.first in excludedGenres }
                            .joinToString { it.second }
                            .ifEmpty { stringResource(R.string.clips_exclude_genres_subtitle) },
                        accentColor = contentColor,
                        onClick = { showGenreDialog = true },
                        trailing = {
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }
        }
    }

    if (showGenreDialog) {
        AmoledMultiSelectionDialog(
            title = stringResource(R.string.clips_exclude_genres),
            options = genreOptions.map { (id, label) -> SelectionOption(id.toString(), label, "") },
            selectedValues = excludedGenres.map { it.toString() }.toSet(),
            accentColor = contentColor,
            onSelectionChanged = { values ->
                preferences.setClipsExcludedGenres(values.mapNotNull { it.toIntOrNull() }.toSet())
            },
            onDismiss = { showGenreDialog = false }
        )
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun SettingsSection(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column { content() }
    }
}
