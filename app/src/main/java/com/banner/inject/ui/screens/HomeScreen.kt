package com.banner.inject.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banner.inject.data.BackupManager
import com.banner.inject.model.ComponentEntry
import com.banner.inject.model.GameHubApp
import com.banner.inject.model.OpState

import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.banner.inject.model.MainTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentListScreen(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    app: GameHubApp,
    components: List<ComponentEntry>,
    isLoading: Boolean,
    totalComponentCount: Int,
    loadedComponentCount: Int,
    opState: OpState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onBackupComponent: (ComponentEntry) -> Unit,
    onReplaceWcp: (ComponentEntry, android.net.Uri) -> Unit,
    onRestoreComponent: (ComponentEntry) -> Unit,
    onDeleteBackup: (ComponentEntry) -> Unit,
    onClearOpState: () -> Unit,
    onListBackups: () -> List<BackupManager.BackupInfo>,
    onDeleteBackupByName: (String) -> Unit,
    appVersion: String,
    accentColor: Color,
    onAccentColorChanged: (Color) -> Unit
) {
    var selectedComponent by remember { mutableStateOf<ComponentEntry?>(null) }
    var showBackupManager by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val pullRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) onRefresh()
    }
    LaunchedEffect(isLoading) {
        if (!isLoading) pullRefreshState.endRefresh()
    }

    LaunchedEffect(opState) {
        when (opState) {
            is OpState.Done -> { snackbarHostState.showSnackbar(opState.message); onClearOpState() }
            is OpState.Error -> { snackbarHostState.showSnackbar("Error: ${opState.message}"); onClearOpState() }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        Column {
                            Text(app.known.displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Components", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = { showBackupManager = true }) {
                            Icon(Icons.Default.Backup, contentDescription = "Backup Manager")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                MainTabRow(currentTab = currentTab, onTabSelected = onTabSelected)
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).nestedScroll(pullRefreshState.nestedScrollConnection)) {
            when {
                isLoading && components.isEmpty() -> {
                    // Nothing loaded yet — show centered spinner
                    Card(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 48.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(16.dp))
                            Text("Loading components...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                !isLoading && components.isEmpty() -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.FolderOff, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No component folders found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isLoading && totalComponentCount > 0)
                                    "Loading $loadedComponentCount / $totalComponentCount components..."
                                else
                                    "${components.size} components",
                                fontSize = 12.sp,
                                color = if (isLoading) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isLoading) FontWeight.Medium else FontWeight.Normal
                            )
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            "Tap a component to backup or replace its contents",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                        )
                    }
                    items(components, key = { it.folderName }) { component ->
                        ComponentCard(component = component, onClick = { selectedComponent = component })
                    }
                }
            }

            PullToRefreshContainer(
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Progress overlay
            if (opState is OpState.InProgress) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(opState.message, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    selectedComponent?.let { comp ->
        ComponentDetailSheet(
            component = comp,
            onDismiss = { selectedComponent = null },
            onBackup = { onBackupComponent(comp) },
            onReplaceWcp = { uri -> onReplaceWcp(comp, uri) },
            onRestore = { onRestoreComponent(comp) },
            onDeleteBackup = { onDeleteBackup(comp) }
        )
    }

    if (showBackupManager) {
        BackupManagerSheet(
            onDismiss = { showBackupManager = false },
            onListBackups = onListBackups,
            onDeleteBackup = onDeleteBackupByName
        )
    }

    if (showSettings) {
        SettingsSheet(
            appVersion = appVersion,
            accentColor = accentColor,
            onAccentColorChanged = onAccentColorChanged,
            onDismiss = { showSettings = false },
            onOpenBackupManager = { showSettings = false; showBackupManager = true }
        )
    }
}

@Composable
private fun ComponentCard(component: ComponentEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Layers,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = component.folderName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${component.fileCount} files  •  ${component.formattedSize}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (component.replacedWith != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Replaced: ${component.replacedWith}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.width(8.dp))

            if (component.hasBackup) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CloudDone, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "Backup",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }

            Icon(
                Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
