package com.vela.app.ui.screens.dashboard.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vela.data.model.BaseItemDto
import com.vela.data.repository.MediaRepositoryProvider
import com.vela.shared.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageLibrariesSheet(
    onDismiss: () -> Unit,
    onLibrariesChanged: () -> Unit
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var libraries by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var hiddenIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val views = mediaRepository.getManageableUserViews().getOrNull().orEmpty()
        val excluded = mediaRepository.getCurrentUser().getOrNull()
            ?.configuration
            ?.myMediaExcludes
            .orEmpty()
            .toSet()
        libraries = views
        hiddenIds = excluded
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121214),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.VideoLibrary,
                    contentDescription = null,
                    tint = Color(0xFF5AA9FA),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = stringResource(R.string.library_manage_title),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = stringResource(R.string.library_manage_subtitle),
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            if (loading) {
                Text(
                    text = stringResource(R.string.library_manage_loading),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                libraries.forEach { library ->
                    val libraryId = library.id ?: return@forEach
                    val visible = libraryId !in hiddenIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    val nextHidden = !visible
                                    val result = mediaRepository.setLibraryHidden(libraryId, nextHidden)
                                    if (result.isSuccess) {
                                        hiddenIds = if (nextHidden) {
                                            hiddenIds + libraryId
                                        } else {
                                            hiddenIds - libraryId
                                        }
                                        onLibrariesChanged()
                                    }
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (visible) {
                                Icons.Rounded.CheckBox
                            } else {
                                Icons.Rounded.CheckBoxOutlineBlank
                            },
                            contentDescription = null,
                            tint = if (visible) Color(0xFF5AA9FA) else Color.White.copy(alpha = 0.4f)
                        )
                        Text(
                            text = library.name.orEmpty(),
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
