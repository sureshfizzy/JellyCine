package com.jellycine.app.ui.components.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ActionButtonsSection(
    modifier: Modifier = Modifier,
    onPlayClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {}
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        // Play Button
        Button(
            onClick = onPlayClick,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0080FF)
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Play",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Play",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Download Button
        OutlinedButton(
            onClick = onDownloadClick,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White,
                containerColor = Color.Transparent
            ),
            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = "Download",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Download",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}