package com.jellycine.app.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortDropdown(
    currentSortBy: String,
    currentSortOrder: String,
    onSortChange: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    val sortOptions = listOf(
        "Name" to "SortName",
        "Date Added" to "DateCreated", 
        "Release Date" to "ProductionYear",
        "Rating" to "CommunityRating",
        "Runtime" to "Runtime"
    )
    
    val orderOptions = listOf(
        "Ascending" to "Ascending",
        "Descending" to "Descending"
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = "${sortOptions.find { it.second == currentSortBy }?.first ?: "Name"} (${if (currentSortOrder == "Ascending") "A-Z" else "Z-A"})",
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Sort",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF0080FF),
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1a1a1a))
        ) {
            sortOptions.forEach { (label, value) ->
                orderOptions.forEach { (orderLabel, orderValue) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "$label (${if (orderValue == "Ascending") "A-Z" else "Z-A"})",
                                color = Color.White
                            )
                        },
                        onClick = {
                            onSortChange(value, orderValue)
                            expanded = false
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = Color.White
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = if (isSelected) Color(0xFF0080FF) else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF0080FF) else Color.White.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

@Composable
fun FilterSection(
    title: String,
    filters: List<String>,
    selectedFilters: Set<String>,
    onFilterToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(filters) { filter ->
                FilterChip(
                    label = filter,
                    isSelected = selectedFilters.contains(filter),
                    onClick = { onFilterToggle(filter) }
                )
            }
        }
    }
}

@Composable
fun ViewModeToggle(
    currentMode: ViewMode,
    onModeChange: (ViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(12.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ViewModeButton(
            icon = Icons.Outlined.GridView,
            isSelected = currentMode == ViewMode.GRID,
            onClick = { onModeChange(ViewMode.GRID) }
        )
        ViewModeButton(
            icon = Icons.Outlined.List,
            isSelected = currentMode == ViewMode.LIST,
            onClick = { onModeChange(ViewMode.LIST) }
        )
    }
}

@Composable
private fun ViewModeButton(
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = if (isSelected) Color(0xFF0080FF) else Color.Transparent
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun LoadMoreButton(
    isLoading: Boolean,
    hasMorePages: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (hasMorePages) {
        Button(
            onClick = onLoadMore,
            enabled = !isLoading,
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0080FF),
                disabledContainerColor = Color(0xFF0080FF).copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = if (isLoading) "Loading..." else "Load More",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ItemCountIndicator(
    currentCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = "Showing $currentCount of $totalCount items",
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 14.sp,
        modifier = modifier
    )
}

enum class ViewMode {
    GRID, LIST
}