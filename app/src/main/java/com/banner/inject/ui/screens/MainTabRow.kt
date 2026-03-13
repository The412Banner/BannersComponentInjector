package com.banner.inject.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.banner.inject.model.MainTab

/** Provided by MainActivity; controls whether the My Games tab is visible. */
val LocalShowGamesTab = staticCompositionLocalOf { false }

@Composable
fun MainTabRow(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    val showGamesTab = LocalShowGamesTab.current
    val visibleTabs = MainTab.values().filter { it != MainTab.GAMES || showGamesTab }
    val selectedIndex = visibleTabs.indexOf(currentTab).coerceAtLeast(0)

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        edgePadding = 0.dp
    ) {
        visibleTabs.forEach { tab ->
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
