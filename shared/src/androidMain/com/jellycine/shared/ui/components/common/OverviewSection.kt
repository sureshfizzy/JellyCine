package com.jellycine.shared.ui.components.common

import androidx.compose.foundation.layout.*
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
    overview: String?,
    tagline: String? = null,
    modifier: Modifier = Modifier,
    title: String? = null
) {
    var showFullOverview by remember(overview) { mutableStateOf(false) }
    var canExpandOverview by remember(overview) { mutableStateOf(false) }
    val visibleTagline = tagline?.takeIf { it.isNotBlank() }
    val visibleOverview = overview?.takeIf { it.isNotBlank() }
    val visibleTitle = title?.takeIf { it.isNotBlank() }

    Column(modifier = modifier) {
        visibleTitle?.let {
            Text(
                text = it,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        ) {
            visibleTagline?.let {
                Text(
                    text = it,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(bottom = if (visibleOverview != null) 10.dp else 0.dp)
                )
            }

            visibleOverview?.let {
                Text(
                    text = it,
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    lineHeight = 22.sp,
                    maxLines = if (showFullOverview) Int.MAX_VALUE else 4,
                    overflow = if (showFullOverview) TextOverflow.Visible else TextOverflow.Ellipsis,
                    onTextLayout = { layoutResult ->
                        if (!showFullOverview) {
                            canExpandOverview = layoutResult.hasVisualOverflow
                        }
                    }
                )

                if (canExpandOverview) {
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
