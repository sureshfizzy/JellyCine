package com.jellycine.app.ui.screens.dashboard.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.LocalMovies
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jellycine.data.model.SeerrRequestedItem
import com.jellycine.shared.R

@Composable
internal fun SeerrRequestedItemsDialog(
    state: SeerrRequestedItemsUiState,
    onDismiss: () -> Unit,
    onItemClick: (SeerrRequestedItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.78f),
        title = {
            Text(
                text = stringResource(
                    if (state.mediaType == "tv") {
                        R.string.settings_seerr_requested_shows
                    } else {
                        R.string.settings_seerr_requested_movies
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        },
        text = {
            when {
                state.isLoading -> SeerrRequestedItemsLoading()
                state.error != null -> Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFCA5A5)
                )

                state.items.isEmpty() -> Text(
                    text = stringResource(R.string.settings_seerr_no_requested_items),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f)
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = state.items,
                        key = { item -> item.requestId }
                    ) { item ->
                        SeerrRequestedItemRow(
                            item = item,
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_close))
            }
        }
    )
}

@Composable
private fun SeerrRequestedItemsLoading() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = Color(0xFF06B6D4)
        )
        Text(
            text = stringResource(R.string.settings_seerr_loading_requests),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.78f)
        )
    }
}

@Composable
private fun SeerrRequestedItemRow(
    item: SeerrRequestedItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SeerrRequestedPoster(item = item)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                SeerrRequestedItemSubtitle(item = item)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SeerrRequestStatusChip(
                        requestStatus = item.requestStatus,
                        mediaStatus = item.mediaStatus
                    )
                    if (item.is4K) {
                        SeerrSmallChip(
                            text = stringResource(R.string.settings_seerr_4k_badge),
                            color = Color(0xFF8B5CF6)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeerrRequestedPoster(item: SeerrRequestedItem) {
    Surface(
        modifier = Modifier.size(width = 54.dp, height = 78.dp),
        shape = RoundedCornerShape(9.dp),
        color = Color.White.copy(alpha = 0.08f)
    ) {
        if (!item.posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.mediaType == "tv") Icons.Rounded.LiveTv else Icons.Rounded.LocalMovies,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.68f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun SeerrRequestedItemSubtitle(item: SeerrRequestedItem) {
    val seasonText = item.seasonCount
        ?.takeIf { count -> count > 0 }
        ?.let { count -> pluralStringResource(R.plurals.settings_seerr_seasons_count, count, count) }
    val requestedAtText = item.requestedAt
        ?.takeIf { it.isNotBlank() }
        ?.let { requestedAt ->
            stringResource(R.string.settings_seerr_requested_on, requestedAt.substringBefore('T'))
        }
    val details = listOfNotNull(
        item.productionYear?.toString(),
        seasonText,
        requestedAtText
    )

    Text(
        text = details.joinToString(" • ").ifBlank {
            item.mediaType.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.68f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun SeerrRequestStatusChip(
    requestStatus: Int?,
    mediaStatus: Int?
) {
    val mediaStatusLabel = when (mediaStatus) {
        5 -> stringResource(R.string.settings_seerr_request_available) to Color(0xFF10B981)
        4 -> stringResource(R.string.settings_seerr_request_partially_available) to Color(0xFF14B8A6)
        3 -> stringResource(R.string.settings_seerr_request_processing) to Color(0xFF8B5CF6)
        2 -> stringResource(R.string.settings_seerr_request_pending) to Color(0xFFF59E0B)
        else -> null
    }
    val (label, color) = mediaStatusLabel ?: when (requestStatus) {
        1 -> stringResource(R.string.settings_seerr_request_pending) to Color(0xFFF59E0B)
        2 -> stringResource(R.string.settings_seerr_request_approved) to Color(0xFF06B6D4)
        3 -> stringResource(R.string.settings_seerr_request_declined) to Color(0xFFEF4444)
        null -> stringResource(R.string.settings_seerr_request_pending) to Color(0xFFF59E0B)
        else -> stringResource(R.string.settings_seerr_request_unknown_status, requestStatus) to Color(0xFF94A3B8)
    }
    SeerrSmallChip(text = label, color = color)
}

@Composable
private fun SeerrSmallChip(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.24f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}