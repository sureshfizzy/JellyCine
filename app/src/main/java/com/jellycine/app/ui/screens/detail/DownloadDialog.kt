package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Locale

internal data class StorageSelectionOption(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val requiredBytes: Long
)

@Composable
internal fun DownloadDialog(
    title: String,
    subtitle: String,
    availableBytes: Long,
    options: List<StorageSelectionOption>,
    initialSelection: Set<String>,
    confirmLabel: String = "Download Selected",
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val selectableIds = remember(options) { options.map { it.id }.toSet() }
    var selectedIds by remember(options, initialSelection) {
        val seed = initialSelection.intersect(selectableIds)
        mutableStateOf(if (seed.isEmpty()) selectableIds else seed)
    }
    fun setSelected(id: String, selected: Boolean) {
        selectedIds = if (selected) selectedIds + id else selectedIds - id
    }

    val selectedBytes = remember(selectedIds, options) {
        options.sumOf { option -> if (selectedIds.contains(option.id)) option.requiredBytes else 0L }
    }
    val exceedsAvailable = selectedBytes > availableBytes
    val overflowBytes = (selectedBytes - availableBytes).coerceAtLeast(0L)
    val remainingBytes = (availableBytes - selectedBytes).coerceAtLeast(0L)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF10131A)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF22D3EE),
                                        Color(0xFF0284C7)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.74f),
                            fontSize = 12.sp
                        )
                    }
                }

                StorageMetricBar(
                    label = "Available",
                    value = formatStorageBytesUi(availableBytes),
                    accent = Color(0xFF38BDF8)
                )
                StorageMetricBar(
                    label = "Selected",
                    value = formatStorageBytesUi(selectedBytes),
                    accent = Color(0xFF67E8F9)
                )
                StorageMetricBar(
                    label = if (exceedsAvailable) "Over By" else "Will Remain",
                    value = formatStorageBytesUi(if (exceedsAvailable) overflowBytes else remainingBytes),
                    accent = if (exceedsAvailable) Color(0xFFF97316) else Color(0xFF34D399)
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(options, key = { it.id }) { option ->
                        val isSelected = selectedIds.contains(option.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) Color(0xFF1E293B)
                                    else Color(0xFF141923)
                                )
                                .clickable {
                                    setSelected(option.id, !isSelected)
                                }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    setSelected(option.id, checked)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF22D3EE),
                                    uncheckedColor = Color.White.copy(alpha = 0.5f),
                                    checkmarkColor = Color.Black
                                )
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = option.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subtitle = option.subtitle
                                if (!subtitle.isNullOrBlank()) {
                                    Text(
                                        text = subtitle,
                                        color = Color.White.copy(alpha = 0.68f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = formatStorageBytesUi(option.requiredBytes),
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White.copy(alpha = 0.8f)
                        )
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.size(6.dp))
                    Button(
                        onClick = { onConfirm(selectedIds) },
                        enabled = selectedIds.isNotEmpty() && !exceedsAvailable,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF22D3EE),
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF23313C),
                            disabledContentColor = Color.White.copy(alpha = 0.55f)
                        )
                    ) {
                        Text(
                            text = confirmLabel,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageMetricBar(
    label: String,
    value: String,
    accent: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF141923)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

internal fun formatStorageBytesUi(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    val value = safeBytes.toDouble()
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        value >= gb -> String.format(Locale.US, "%.2f GB", value / gb)
        value >= mb -> String.format(Locale.US, "%.1f MB", value / mb)
        value >= kb -> String.format(Locale.US, "%.1f KB", value / kb)
        else -> "$safeBytes B"
    }
}
