package com.jellycine.app.ui.screens.dashboard.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.jellycine.data.model.BaseItemDto

@Composable
fun SearchScreen(
    onNavigateToDetail: (BaseItemDto) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    SearchContainer(
        onNavigateToDetail = onNavigateToDetail,
        onCancel = onNavigateBack
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun SearchScreenPreview() {
    SearchScreen()
}