package com.banner.inject.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.banner.inject.model.MainTab

@Composable
fun MainTabRow(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = currentTab.ordinal,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        edgePadding = 0.dp
    ) {
        MainTab.values().forEach { tab ->
            Tab(
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = tab.title,
                        fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
    }
}
