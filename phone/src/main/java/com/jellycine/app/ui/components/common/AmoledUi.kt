package com.jellycine.app.ui.components.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jellycine.shared.ui.theme.JellyBlue

@Composable
fun AmoledDialogFrame(
    dismissOnRequest: Boolean,
    onDismiss: () -> Unit,
    topPadding: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val scrimInteractionSource = remember { MutableInteractionSource() }
    val sheetInteractionSource = remember { MutableInteractionSource() }

    Dialog(
        onDismissRequest = {
            if (dismissOnRequest) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = dismissOnRequest,
            dismissOnClickOutside = dismissOnRequest,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.76f))
                .clickable(
                    enabled = dismissOnRequest,
                    indication = null,
                    interactionSource = scrimInteractionSource
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = topPadding)
                    .widthIn(max = 880.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .blur(64.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0x2211B6FF),
                                    Color(0x1400E5FF),
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(36.dp)
                        )
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = sheetInteractionSource
                        ) {},
                    shape = RoundedCornerShape(30.dp),
                    color = Color(0xFF020202),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF090909),
                                        Color(0xFF020202)
                                    )
                                )
                            )
                            .padding(horizontal = 24.dp, vertical = 28.dp)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
fun amoledAuthFieldColors(
    hasLeadingIcon: Boolean = false
) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = JellyBlue,
    unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
    focusedBorderColor = JellyBlue,
    unfocusedBorderColor = Color.White.copy(alpha = 0.26f),
    focusedLeadingIconColor = if (hasLeadingIcon) JellyBlue else Color.Unspecified,
    unfocusedLeadingIconColor = if (hasLeadingIcon) Color.White.copy(alpha = 0.65f) else Color.Unspecified,
    cursorColor = JellyBlue,
    selectionColors = TextSelectionColors(
        handleColor = JellyBlue,
        backgroundColor = JellyBlue.copy(alpha = 0.28f)
    )
)