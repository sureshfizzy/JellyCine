package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.BaseItemPerson
import com.jellycine.data.repository.MediaRepository
import com.jellycine.shared.ui.components.common.CastSection

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun DirectorCreditRow(
    label: String,
    directors: List<BaseItemPerson>,
    onPersonClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (directors.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.width(8.dp))

        FlowRow(
            modifier = Modifier.weight(1f)
        ) {
            directors.forEachIndexed { index, person ->
                val personId = person.id
                val canOpenPerson = !personId.isNullOrBlank()
                val name = person.name ?: "Unknown"
                Text(
                    text = name + if (index < directors.lastIndex) ", " else "",
                    fontSize = 13.sp,
                    color = Color(0xFF89ECFF),
                    modifier = Modifier.clickable(enabled = canOpenPerson) {
                        personId?.let(onPersonClick)
                    }
                )
            }
        }
    }
}

@Composable
internal fun CastCrewSection(
    item: BaseItemDto,
    mediaRepository: MediaRepository,
    onPersonClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    CastSection(
        item = item,
        mediaRepository = mediaRepository,
        modifier = modifier,
        onPersonClick = onPersonClick
    )
}