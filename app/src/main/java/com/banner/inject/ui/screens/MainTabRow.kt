package com.banner.inject.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.banner.inject.model.MainTab

/** Provided by MainActivity; controls whether the My Games tab is visible. */
val LocalShowGamesTab = staticCompositionLocalOf { false }

/** Provided by MainActivity; true when sources contain items newer than the last Download-tab visit. */
val LocalHasNewDownloads = staticCompositionLocalOf { false }

@Composable
fun MainTabRow(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    val showGamesTab = LocalShowGamesTab.current
    val hasNewDownloads = LocalHasNewDownloads.current
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tab.title,
                            fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                        if (tab == MainTab.DOWNLOAD && hasNewDownloads) {
                            Spacer(Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            )
        }
    }
}
