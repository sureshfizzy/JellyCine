package com.jellycine.app.ui.components.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OverviewSection(
    overview: String,
    modifier: Modifier = Modifier,
    title: String = "Overview"
) {
    var showFullOverview by remember { mutableStateOf(false) }
    
    Column(modifier = modifier) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = overview,
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = 22.sp,
                    maxLines = if (showFullOverview) Int.MAX_VALUE else 4,
                    overflow = if (showFullOverview) TextOverflow.Visible else TextOverflow.Ellipsis
                )

                if (overview.length > 200) {
                    TextButton(
                        onClick = { showFullOverview = !showFullOverview },
                        modifier = Modifier.padding(top = 8.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFF0080FF)
                        )
                    ) {
                        Text(
                            text = if (showFullOverview) "Show Less" else "Read More",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
