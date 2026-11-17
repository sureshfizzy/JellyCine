package com.jellycine.ui.screens.dashboard.settings

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellycine.ui.components.common.AmoledMultiSelectionDialog
import com.jellycine.ui.components.common.SelectionOption
import com.jellycine.resources.*
import org.jetbrains.compose.resources.stringResource
import com.jellycine.preferences.Preferences

private val QUALITY_OPTIONS = listOf(0, 480, 720, 1080)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ClipsSettingsScreen(
    preferences: Preferences,
    onBackPressed: () -> Unit = {}
) {
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

    val autoLabel = stringResource(Res.string.clips_quality_auto)
    fun qualityLabel(height: Int) = if (height == 0) autoLabel else "${height}p"

    val genreOptions = listOf(
        16 to stringResource(Res.string.clips_genre_animation),
        35 to stringResource(Res.string.clips_genre_comedy),
        99 to stringResource(Res.string.clips_genre_documentary),
        10751 to stringResource(Res.string.clips_genre_family),
        27 to stringResource(Res.string.clips_genre_horror),
        10749 to stringResource(Res.string.clips_genre_romance),
        10764 to stringResource(Res.string.clips_genre_reality),
        10762 to stringResource(Res.string.clips_genre_kids)
    )
    var showGenreDialog by remember { mutableStateOf(false) }

    val windowOptions = listOf(
        SelectionOption(Preferences.CLIPS_WINDOW_DAY, stringResource(Res.string.clips_window_today), ""),
        SelectionOption(Preferences.CLIPS_WINDOW_WEEK, stringResource(Res.string.clips_window_week), "", isDefault = true)
    )
    val contentOptions = listOf(
        SelectionOption(Preferences.CLIPS_CONTENT_ALL, stringResource(Res.string.clips_content_all), "", isDefault = true),
        SelectionOption(Preferences.CLIPS_CONTENT_MOVIE, stringResource(Res.string.clips_content_movies), ""),
        SelectionOption(Preferences.CLIPS_CONTENT_TV, stringResource(Res.string.clips_content_shows), "")
    )

    BackHandler { onBackPressed() }

    Scaffold(
        topBar = {
            topbar(
                title = stringResource(Res.string.settings_clips),
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
            item { SectionLabel(stringResource(Res.string.clips_section_playback)) }
            item {
                SettingsSection {
                    SelectionDialogSettingsItem(
                        icon = Icons.Rounded.HighQuality,
                        title = stringResource(Res.string.clips_max_quality),
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

            item { SectionLabel(stringResource(Res.string.clips_section_content)) }
            item {
                SettingsSection {
                    SelectionDialogSettingsItem(
                        icon = Icons.Rounded.Schedule,
                        title = stringResource(Res.string.clips_trending_window),
                        subtitle = windowOptions.first { it.value == trendingWindow }.label,
                        selectedValue = trendingWindow,
                        options = windowOptions,
                        onOptionSelected = preferences::setClipsTrendingWindow,
                        accentColor = contentColor
                    )
                    SettingsDivider()
                    SelectionDialogSettingsItem(
                        icon = Icons.Rounded.Movie,
                        title = stringResource(Res.string.clips_content_type),
                        subtitle = contentOptions.first { it.value == contentType }.label,
                        selectedValue = contentType,
                        options = contentOptions,
                        onOptionSelected = preferences::setClipsContentType,
                        accentColor = contentColor
                    )
                    SettingsDivider()
                    BaseSettingsItem(
                        icon = Icons.Rounded.Block,
                        title = stringResource(Res.string.clips_exclude_genres),
                        subtitle = genreOptions
                            .filter { it.first in excludedGenres }
                            .joinToString { it.second }
                            .ifEmpty { stringResource(Res.string.clips_exclude_genres_subtitle) },
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
            title = stringResource(Res.string.clips_exclude_genres),
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

