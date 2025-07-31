package com.jellycine.app.ui.components.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ModernFileInfoRow(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Label row with icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color(0xFF2A2A2A)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = Color(0xFF00BCD4),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = label,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }

        // Value text below
        Text(
            text = value,
            fontSize = 16.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            softWrap = true,
            modifier = Modifier.padding(start = 52.dp, top = 8.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ModernFileInfoRowPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ModernFileInfoRow(
                label = "File Size",
                value = "6.6 GB",
                icon = Icons.Rounded.Storage
            )

            ModernFileInfoRow(
                label = "Video Quality",
                value = "3840x1920 • HEVC • Dolby Vision",
                icon = Icons.Rounded.VideoFile
            )

            ModernFileInfoRow(
                label = "Available Audio",
                value = "eng DD+ 5.1 (Default), spa DD 2.0",
                icon = Icons.AutoMirrored.Rounded.VolumeUp
            )
        }
    }
}