package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jellycine.data.model.SeerrRequestDestination
import com.jellycine.data.model.SeerrRequestOptions
import com.jellycine.data.model.SeerrRequestProfile
import com.jellycine.data.model.SeerrRequestRootFolder
import com.jellycine.data.model.SeerrRequestSelection
import com.jellycine.data.model.SeerrRequestState
import com.jellycine.data.model.SeerrSeasonRequestOption
import com.jellycine.shared.R
import com.jellycine.shared.util.image.JellyfinPosterImage

@Composable
internal fun SeerrRequestDialog(
    itemName: String,
    backdropImageUrl: String?,
    options: SeerrRequestOptions,
    initialRequest4K: Boolean = false,
    prefer4KRequest: Boolean = false,
    onDismissRequest: () -> Unit,
    onConfirm: (SeerrRequestSelection) -> Unit
) {
    val initialDestination = remember(options) {
        options.destinations.firstOrNull { it.isDefault } ?: options.destinations.firstOrNull()
    }
    var selectedDestinationId by remember(options) { mutableStateOf(initialDestination?.id) }
    val selectedDestination = remember(options, selectedDestinationId) {
        options.destinations.firstOrNull { it.id == selectedDestinationId } ?: initialDestination
    }
    var selectedProfileId by remember(options, selectedDestination?.id) {
        mutableStateOf(selectedDestination?.defaultQualityProfile()?.id)
    }
    var selectedRootFolder by remember(options, selectedDestination?.id) {
        mutableStateOf(selectedDestination?.defaultRootFolderOption()?.path)
    }
    var selectedSeasons by remember(options) {
        mutableStateOf(
            options.seasons
                .filter { season -> season.requestState == SeerrRequestState.NONE }
                .map { season -> season.seasonNumber }
                .toSet()
        )
    }
    var request4K by remember(options, initialRequest4K) {
        mutableStateOf(initialRequest4K && options.canRequest4K)
    }

    val showAdvanced = options.canUseAdvancedRequests && !request4K
    val showDestination = showAdvanced && options.destinations.isNotEmpty()
    val showQualityProfile = showAdvanced && selectedDestination?.qualityProfiles.orEmpty().size > 1
    val showRootFolder = showAdvanced && selectedDestination?.rootFolders.orEmpty().size > 1
    val visibleFieldCount = listOf(showDestination, showQualityProfile, showRootFolder).count { it }
    val isSeriesRequest = options.mediaType == "tv"
    val quota = options.quota
    val canRequest = if (isSeriesRequest) selectedSeasons.isNotEmpty() else true
    fun selectedSeasonNumbers() = selectedSeasons
        .takeIf { isSeriesRequest }
        ?.sorted()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 960.dp)
                .fillMaxWidth(0.96f)
                .wrapContentHeight()
                .padding(horizontal = 6.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF121923),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                RequestDialogHero(
                    imageUrl = backdropImageUrl,
                    itemName = itemName,
                    mediaType = options.mediaType
                )

                if (options.canRequest4K && (!prefer4KRequest || request4K)) {
                    Request4KToggle(
                        checked = request4K,
                        enabled = !prefer4KRequest,
                        onCheckedChange = { request4K = it }
                    )
                }

                if (isSeriesRequest && options.seasons.isNotEmpty()) {
                    SeasonSelectionSection(
                        seasons = options.seasons,
                        selectedSeasons = selectedSeasons,
                        onSelectedSeasonsChanged = { selectedSeasons = it }
                    )
                }

                if (!options.canUseAdvancedRequests && quota != null) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        RequestQuotaPanel(
                            mediaType = options.mediaType,
                            remaining = quota.remaining,
                            limit = quota.limit,
                            days = quota.days
                        )
                    }
                }

                if (showAdvanced && visibleFieldCount > 0) {
                    val onDestinationChanged: (SeerrRequestDestination) -> Unit = { destination ->
                        selectedDestinationId = destination.id
                        selectedProfileId = destination.defaultQualityProfile()?.id
                        selectedRootFolder = destination.defaultRootFolderOption()?.path
                    }

                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Advanced",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White.copy(alpha = 0.76f),
                            fontWeight = FontWeight.Bold
                        )

                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            AdvancedRequestFields(
                                useRow = maxWidth >= 700.dp && visibleFieldCount > 1,
                                destination = selectedDestination,
                                options = options,
                                selectedProfileId = selectedProfileId,
                                selectedRootFolder = selectedRootFolder,
                                showDestination = showDestination,
                                showQualityProfile = showQualityProfile,
                                showRootFolder = showRootFolder,
                                onDestinationSelected = onDestinationChanged,
                                onProfileSelected = { profile -> selectedProfileId = profile.id },
                                onRootFolderSelected = { folder -> selectedRootFolder = folder.path }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 22.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White.copy(alpha = 0.82f)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Button(
                        enabled = canRequest,
                        onClick = {
                            val destination = selectedDestination
                            onConfirm(
                                SeerrRequestSelection(
                                    serverId = if (request4K) null else destination?.id,
                                    profileId = if (request4K) {
                                        null
                                    } else {
                                        selectedProfileId ?: destination?.defaultQualityProfile()?.id
                                    },
                                    rootFolder = if (request4K) {
                                        null
                                    } else {
                                        selectedRootFolder ?: destination?.defaultRootFolderOption()?.path
                                    },
                                    languageProfileId = if (request4K) null else destination?.defaultLanguageProfileId,
                                    is4K = request4K,
                                    seasons = selectedSeasonNumbers()
                                )
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4F46E5),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF4F46E5).copy(alpha = 0.36f),
                            disabledContentColor = Color.White.copy(alpha = 0.42f)
                        ),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = if (isSeriesRequest && !canRequest) {
                                stringResource(R.string.detail_seerr_select_seasons)
                            } else if (request4K) {
                                stringResource(R.string.detail_seerr_request_4k)
                            } else {
                                stringResource(R.string.detail_seerr_request)
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedRequestFields(
    useRow: Boolean,
    destination: SeerrRequestDestination?,
    options: SeerrRequestOptions,
    selectedProfileId: Int?,
    selectedRootFolder: String?,
    showDestination: Boolean,
    showQualityProfile: Boolean,
    showRootFolder: Boolean,
    onDestinationSelected: (SeerrRequestDestination) -> Unit,
    onProfileSelected: (SeerrRequestProfile) -> Unit,
    onRootFolderSelected: (SeerrRequestRootFolder) -> Unit
) {
    if (useRow) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Top
        ) {
            RequestOptionFields(
                destination = destination,
                options = options,
                selectedProfileId = selectedProfileId,
                selectedRootFolder = selectedRootFolder,
                showDestination = showDestination,
                showQualityProfile = showQualityProfile,
                showRootFolder = showRootFolder,
                onDestinationSelected = onDestinationSelected,
                onProfileSelected = onProfileSelected,
                onRootFolderSelected = onRootFolderSelected,
                fieldModifier = Modifier.weight(1f)
            )
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            RequestOptionFields(
                destination = destination,
                options = options,
                selectedProfileId = selectedProfileId,
                selectedRootFolder = selectedRootFolder,
                showDestination = showDestination,
                showQualityProfile = showQualityProfile,
                showRootFolder = showRootFolder,
                onDestinationSelected = onDestinationSelected,
                onProfileSelected = onProfileSelected,
                onRootFolderSelected = onRootFolderSelected,
                fieldModifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Request4KToggle(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 14.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1D2634).copy(alpha = 0.88f),
        border = BorderStroke(1.dp, Color(0xFF8D7CFF).copy(alpha = if (checked) 0.58f else 0.20f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.detail_seerr_request_4k),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White.copy(alpha = if (enabled) 0.94f else 0.46f),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.detail_seerr_request_4k_subtitle),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White.copy(alpha = if (enabled) 0.62f else 0.34f),
                    fontWeight = FontWeight.Medium
                )
            }

            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null
            )
        }
    }
}

@Composable
private fun RequestDialogHero(
    imageUrl: String?,
    itemName: String,
    mediaType: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 176.dp),
        contentAlignment = Alignment.Center
    ) {
        RequestDialogBackdrop(
            imageUrl = imageUrl,
            contentDescription = itemName,
            modifier = Modifier.matchParentSize()
        )

        Column(
            modifier = Modifier.padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (mediaType == "movie") {
                    "Request Movie"
                } else {
                    "Request Series"
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 28.sp
                ),
                color = Color(0xFF9B7CFF),
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = itemName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                ),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SeasonSelectionSection(
    seasons: List<SeerrSeasonRequestOption>,
    selectedSeasons: Set<Int>,
    onSelectedSeasonsChanged: (Set<Int>) -> Unit
) {
    val requestableSeasons = seasons
        .filter { season -> season.requestState == SeerrRequestState.NONE }
        .map { season -> season.seasonNumber }
        .toSet()
    val allSelected = requestableSeasons.isNotEmpty() && selectedSeasons.containsAll(requestableSeasons)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = Color(0xFF1D2634).copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Column {
            SeasonRow(
                checked = allSelected,
                enabled = requestableSeasons.isNotEmpty(),
                title = stringResource(R.string.detail_seerr_season),
                episodeText = stringResource(R.string.detail_seerr_episode_count),
                status = stringResource(R.string.detail_seerr_status),
                isHeader = true,
                onCheckedChange = { checked ->
                    onSelectedSeasonsChanged(if (checked) requestableSeasons else emptySet())
                }
            )

            seasons.forEach { season ->
                val enabled = season.requestState == SeerrRequestState.NONE
                SeasonRow(
                    checked = selectedSeasons.contains(season.seasonNumber),
                    enabled = enabled,
                    title = stringResource(R.string.detail_seerr_season_number, season.seasonNumber),
                    episodeText = season.episodeCount?.toString().orEmpty(),
                    status = if (enabled) {
                        stringResource(R.string.detail_seerr_not_requested)
                    } else {
                        stringResource(R.string.detail_seerr_requested)
                    },
                    isHeader = false,
                    onCheckedChange = { checked ->
                        onSelectedSeasonsChanged(
                            if (checked) {
                                selectedSeasons + season.seasonNumber
                            } else {
                                selectedSeasons - season.seasonNumber
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SeasonRow(
    checked: Boolean,
    enabled: Boolean,
    title: String,
    episodeText: String,
    status: String,
    isHeader: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .background(if (isHeader) Color.White.copy(alpha = 0.05f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = if (isHeader) 9.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier
                .width(34.dp)
                .scale(0.72f)
        )

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = if (isHeader) 11.sp else 13.sp,
                fontWeight = if (isHeader) FontWeight.ExtraBold else FontWeight.Bold
            ),
            color = Color.White.copy(alpha = if (isHeader) 0.70f else 0.94f),
            fontWeight = if (isHeader) FontWeight.ExtraBold else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.25f)
        )

        Text(
            text = episodeText,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = if (isHeader) 11.sp else 13.sp,
                fontWeight = if (isHeader) FontWeight.ExtraBold else FontWeight.Bold
            ),
            color = Color.White.copy(alpha = if (isHeader) 0.70f else 0.94f),
            fontWeight = if (isHeader) FontWeight.ExtraBold else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.15f),
            textAlign = TextAlign.Center
        )

        if (isHeader) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = Color.White.copy(alpha = 0.70f),
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.4f),
                textAlign = TextAlign.Center
            )
        } else {
            Box(
                modifier = Modifier.weight(1.4f),
                contentAlignment = Alignment.Center
            ) {
                StatusChip(
                    text = status,
                    isRequested = !enabled
                )
            }
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    isRequested: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (isRequested) Color(0xFFF59E0B) else Color(0xFF8D7CFF)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.88f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            ),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun RequestQuotaPanel(
    mediaType: String,
    remaining: Int?,
    limit: Int?,
    days: Int?
) {
    val contentType = if (mediaType == "movie") {
        stringResource(R.string.detail_seerr_movie_requests)
    } else {
        stringResource(R.string.detail_seerr_series_requests)
    }
    val remainingText = remaining?.toString() ?: stringResource(R.string.settings_seerr_unlimited)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1D2634).copy(alpha = 0.82f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.detail_seerr_requests_remaining, remainingText, contentType),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White.copy(alpha = 0.84f),
                fontWeight = FontWeight.Bold
            )

            if (limit != null && days != null) {
                Text(
                    text = stringResource(R.string.detail_seerr_allowed_requests, limit, contentType, days),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color.White.copy(alpha = 0.72f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun RequestDialogBackdrop(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (!imageUrl.isNullOrBlank()) {
            JellyfinPosterImage(
                imageUrl = imageUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                context = LocalContext.current,
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121923).copy(alpha = if (imageUrl.isNullOrBlank()) 1f else 0.46f))
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF121923).copy(alpha = 0.88f),
                            Color(0xFF121923).copy(alpha = 0.70f),
                            Color(0xFF121923).copy(alpha = 0.52f)
                        )
                    )
                )
        )
    }
}

@Composable
private fun RequestOptionFields(
    destination: SeerrRequestDestination?,
    options: SeerrRequestOptions,
    selectedProfileId: Int?,
    selectedRootFolder: String?,
    showDestination: Boolean,
    showQualityProfile: Boolean,
    showRootFolder: Boolean,
    onDestinationSelected: (SeerrRequestDestination) -> Unit,
    onProfileSelected: (SeerrRequestProfile) -> Unit,
    onRootFolderSelected: (SeerrRequestRootFolder) -> Unit,
    fieldModifier: Modifier
) {
    if (showDestination) {
        RequestOptionsDropdown(
            label = stringResource(R.string.detail_seerr_destination_server),
            value = destination?.name.orEmpty(),
            options = options.destinations,
            optionLabel = { it.name },
            onOptionSelected = onDestinationSelected,
            modifier = fieldModifier
        )
    }

    if (destination != null && showQualityProfile) {
        RequestOptionsDropdown(
            label = stringResource(R.string.detail_seerr_quality_profile),
            value = destination.qualityProfiles
                .firstOrNull { it.id == selectedProfileId }
                ?.name
                .orEmpty(),
            options = destination.qualityProfiles,
            optionLabel = { it.name },
            onOptionSelected = onProfileSelected,
            modifier = fieldModifier
        )
    }

    if (destination != null && showRootFolder) {
        RequestOptionsDropdown(
            label = stringResource(R.string.detail_seerr_root_folder),
            value = selectedRootFolder.orEmpty(),
            options = destination.rootFolders,
            optionLabel = { it.path },
            onOptionSelected = onRootFolderSelected,
            modifier = fieldModifier
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun <T> RequestOptionsDropdown(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            ),
            color = Color.White.copy(alpha = 0.62f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1D2634),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = label,
                        tint = Color.White.copy(alpha = 0.62f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = Color(0xFF202633)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = optionLabel(option),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun SeerrRequestDestination.defaultQualityProfile(): SeerrRequestProfile? {
    return qualityProfiles.firstOrNull { it.id == defaultProfileId }
        ?: qualityProfiles.firstOrNull()
}

private fun SeerrRequestDestination.defaultRootFolderOption(): SeerrRequestRootFolder? {
    return rootFolders.firstOrNull { it.path == defaultRootFolder }
        ?: rootFolders.firstOrNull()
}