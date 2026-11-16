package com.jellycine.ui.components.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.data.model.SeerrRequestState
import com.jellycine.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun SeerrTopBadges(
    requestState: SeerrRequestState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        SeerrSourceBadge()

        if (requestState == SeerrRequestState.REQUESTED) {
            SeerrRequestBadge()
        }
    }
}

@Composable
fun SeerrSourceBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(20.dp),
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.72f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(Res.string.settings_seerr),
                fontSize = 9.sp,
                color = Color(0xFF9CDCFE),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SeerrRequestBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color(0xFFE4E5FF),
        border = BorderStroke(1.dp, Color(0xFF8E90FF))
    ) {
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = stringResource(Res.string.detail_requested_on_seerr),
                tint = Color(0xFF5F65E8),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
