package com.banner.inject.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banner.inject.model.GameEntry
import com.banner.inject.model.GameHubApp
import com.banner.inject.model.MainTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyGamesScreen(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    apps: List<GameHubApp>,
    selectedApp: GameHubApp?,
    games: List<GameEntry>,
    isLoadingGames: Boolean,
    hasGamesAccess: (String) -> Boolean,
    onSelectApp: (GameHubApp) -> Unit,
    onGamesAccessGranted: (GameHubApp, Uri) -> Unit,
    onRevokeGamesAccess: (GameHubApp) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLaunchGame: (packageName: String, gameId: String) -> Unit,
    onCreateIsos: () -> Unit,
    initialGamesUriHintFor: (String) -> Uri
) {
    var pendingApp by remember { mutableStateOf<GameHubApp?>(null) }
    var showGrantGuide by remember { mutableStateOf<GameHubApp?>(null) }
    var showRevokeDialog by remember { mutableStateOf<GameHubApp?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val app = pendingApp
        pendingApp = null
        if (uri != null && app != null) onGamesAccessGranted(app, uri)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        if (selectedApp != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    title = {
                        Text(
                            if (selectedApp != null) selectedApp.known.displayName else "My Games",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    actions = {
                        if (selectedApp != null) {
                            IconButton(onClick = onRefresh) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                            IconButton(onClick = onCreateIsos) {
                                Icon(Icons.Default.SaveAlt, contentDescription = "Create ISOs")
                            }
                            IconButton(onClick = { showRevokeDialog = selectedApp }) {
                                Icon(Icons.Default.LinkOff, contentDescription = "Revoke access")
                            }
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
        if (selectedApp == null) {
            // App selector list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "Select your GameHub version to browse installed games.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
                val eligible = apps.filter { it.isInstalled || it.known.packageNames.any { p -> hasGamesAccess(p) } }
                if (eligible.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No GameHub variants installed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(eligible, key = { it.known.packageNames.first() }) { app ->
                        GamesAppCard(
                            app = app,
                            hasGamesAccess = hasGamesAccess,
                            onClick = {
                                val access = app.known.packageNames.any { hasGamesAccess(it) }
                                if (access) {
                                    onSelectApp(app)
                                } else {
                                    showGrantGuide = app
                                }
                            },
                            onRevokeClick = { showRevokeDialog = app }
                        )
                    }
                }
            }
        } else {
            // Games list
            if (isLoadingGames) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Scanning games…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (games.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No games found in virtual_containers.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Install and set up a game in GameHub first.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "${games.size} game${if (games.size != 1) "s" else ""} found",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    items(games, key = { it.gameId }) { game ->
                        GameCard(
                            game = game,
                            onLaunch = { onLaunchGame(selectedApp.activePackage, game.gameId) }
                        )
                    }
                }
            }
        }
    }

    // Grant guide dialog
    showGrantGuide?.let { app ->
        AlertDialog(
            onDismissRequest = { showGrantGuide = null },
            icon = { Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Grant Games Folder Access") },
            text = {
                Column {
                    Text(
                        "In the folder picker, select ${app.known.displayName} from the sidebar, then navigate to the virtual_containers folder and tap \"Use this folder\".",
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "Path to select:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "data/files/usr/home/virtual_containers",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showGrantGuide = null
                    pendingApp = app
                    folderPicker.launch(initialGamesUriHintFor(app.activePackage))
                }) { Text("Open Folder Picker") }
            },
            dismissButton = {
                TextButton(onClick = { showGrantGuide = null }) { Text("Cancel") }
            }
        )
    }

    // Revoke access dialog
    showRevokeDialog?.let { app ->
        AlertDialog(
            onDismissRequest = { showRevokeDialog = null },
            title = { Text("Remove Games Access") },
            text = { Text("Remove games folder access for ${app.known.displayName}? You can re-grant it any time.") },
            confirmButton = {
                TextButton(onClick = {
                    showRevokeDialog = null
                    onRevokeGamesAccess(app)
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeDialog = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun GamesAppCard(
    app: GameHubApp,
    hasGamesAccess: (String) -> Boolean,
    onClick: () -> Unit,
    onRevokeClick: () -> Unit
) {
    val accessGranted = app.known.packageNames.any { hasGamesAccess(it) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (app.isInstalled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = if (app.isInstalled) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.known.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        accessGranted -> "Tap to view games"
                        app.isInstalled -> "Installed — tap to grant access"
                        else -> "Not installed"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (accessGranted) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "Access",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                IconButton(onClick = onRevokeClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.LinkOff,
                        contentDescription = "Revoke access",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun GameCard(
    game: GameEntry,
    onLaunch: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Games,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    game.gameId,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 2
                )
                Text(
                    "ID: ${game.gameId}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onLaunch,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Play", fontSize = 13.sp)
            }
        }
    }
}
