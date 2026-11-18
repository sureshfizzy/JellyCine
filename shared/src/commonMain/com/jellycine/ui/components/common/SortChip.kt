package com.jellycine.ui.components.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.resources.*
import org.jetbrains.compose.resources.StringResource

@Composable
fun SortChip(
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

internal fun sortOptionLabelRes(sortBy: String, sortOrder: String): StringResource {
    return when (sortBy to sortOrder) {
        "DateCreated" to "Descending" -> Res.string.view_all_sort_recently_added
        "SortName" to "Ascending" -> Res.string.view_all_sort_name_az
        "SortName" to "Descending" -> Res.string.view_all_sort_name_za
        "ProductionYear" to "Descending" -> Res.string.view_all_sort_year_newest
        "ProductionYear" to "Ascending" -> Res.string.view_all_sort_year_oldest
        "CommunityRating" to "Descending" -> Res.string.view_all_sort_rating_high
        "CommunityRating" to "Ascending" -> Res.string.view_all_sort_rating_low
        else -> Res.string.view_all_sort_recently_added
    }
}