package com.banner.inject.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.banner.inject.data.SteamGameInfo
import com.banner.inject.data.SteamRepository
import com.banner.inject.model.GameEntry
import com.banner.inject.model.GameHubApp
import com.banner.inject.model.GameType
import com.banner.inject.model.MainTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyGamesScreen(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    apps: List<GameHubApp>,
    selectedApp: GameHubApp?,
    games: List<GameEntry>,
    isLoadingGames: Boolean,
    hasLocalGamesAccess: (String) -> Boolean,
    hasSteamGamesAccess: (String) -> Boolean,
    onSelectApp: (GameHubApp) -> Unit,
    onLocalAccessGranted: (GameHubApp, Uri) -> Unit,
    onSteamAccessGranted: (GameHubApp, Uri) -> Unit,
    onRevokeLocalAccess: (GameHubApp) -> Unit,
    onRevokeSteamAccess: (GameHubApp) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLaunchGame: (packageName: String, gameId: String) -> Unit,
    onCreateIsos: () -> Unit,
    initialLocalUriHintFor: (String) -> Uri,
    initialSteamUriHintFor: (String) -> Uri
) {
    var pendingLocalApp by remember { mutableStateOf<GameHubApp?>(null) }
    var pendingSteamApp by remember { mutableStateOf<GameHubApp?>(null) }
    var showSetupDialog by remember { mutableStateOf<GameHubApp?>(null) }
    var showManageAccessDialog by remember { mutableStateOf(false) }

    val localFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val app = pendingLocalApp
        pendingLocalApp = null
        if (uri != null && app != null) onLocalAccessGranted(app, uri)
    }

    val steamFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val app = pendingSteamApp
        pendingSteamApp = null
        if (uri != null && app != null) onSteamAccessGranted(app, uri)
    }

    val localGames = games.filter { it.type == GameType.LOCAL }
    val steamGames = games.filter { it.type == GameType.STEAM }

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
                            // Create ISOs only makes sense for local games
                            if (selectedApp.known.packageNames.any { hasLocalGamesAccess(it) }) {
                                IconButton(onClick = onCreateIsos) {
                                    Icon(Icons.Default.SaveAlt, contentDescription = "Create ISOs for local games")
                                }
                            }
                            IconButton(onClick = { showManageAccessDialog = true }) {
                                Icon(Icons.Default.FolderShared, contentDescription = "Manage folder access")
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
            // ── App selector ───────────────────────────────────────────────────
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
                val eligible = apps.filter { it.isInstalled || it.known.packageNames.any { p -> hasLocalGamesAccess(p) || hasSteamGamesAccess(p) } }
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
                            hasLocalAccess = app.known.packageNames.any { hasLocalGamesAccess(it) },
                            hasSteamAccess = app.known.packageNames.any { hasSteamGamesAccess(it) },
                            onClick = {
                                val hasLocal = app.known.packageNames.any { hasLocalGamesAccess(it) }
                                val hasSteam = app.known.packageNames.any { hasSteamGamesAccess(it) }
                                if (hasLocal || hasSteam) onSelectApp(app)
                                else showSetupDialog = app
                            }
                        )
                    }
                }
            }
        } else {
            // ── Games list ────────────────────────────────────────────────────
            if (isLoadingGames) {
                Box(
                    Modifier.fillMaxSize().padding(padding),
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
                    Modifier.fillMaxSize().padding(padding),
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
                            "No games found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Install games in GameHub, or grant access to both folders.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // ── Local Games section ────────────────────────────────────
                    if (localGames.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Local Games",
                                count = localGames.size,
                                icon = Icons.Default.Computer
                            )
                        }
                        items(localGames, key = { "local_${it.gameId}" }) { game ->
                            GameCard(
                                game = game,
                                onLaunch = { onLaunchGame(selectedApp.activePackage, game.gameId) }
                            )
                        }
                    }

                    // ── Steam Games section ────────────────────────────────────
                    if (steamGames.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Steam Games",
                                count = steamGames.size,
                                icon = Icons.Default.Games,
                                topPadding = if (localGames.isNotEmpty()) 8.dp else 0.dp
                            )
                        }
                        items(steamGames, key = { "steam_${it.gameId}" }) { game ->
                            GameCard(
                                game = game,
                                onLaunch = { onLaunchGame(selectedApp.activePackage, game.gameId) }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Setup dialog (no access yet) ──────────────────────────────────────────
    showSetupDialog?.let { app ->
        AlertDialog(
            onDismissRequest = { showSetupDialog = null },
            icon = { Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Grant Folder Access", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Grant access to one or both game folders for ${app.known.displayName}.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Local games folder
                    FolderGrantRow(
                        label = "Local Games",
                        path = "data/files/usr/home/virtual_containers",
                        isGranted = app.known.packageNames.any { hasLocalGamesAccess(it) },
                        onGrant = {
                            pendingLocalApp = app
                            localFolderPicker.launch(initialLocalUriHintFor(app.activePackage))
                        },
                        onRevoke = { onRevokeLocalAccess(app) }
                    )
                    // Steam games folder
                    FolderGrantRow(
                        label = "Steam Games",
                        path = "data/files/Steam/steamapps/shadercache",
                        isGranted = app.known.packageNames.any { hasSteamGamesAccess(it) },
                        onGrant = {
                            pendingSteamApp = app
                            steamFolderPicker.launch(initialSteamUriHintFor(app.activePackage))
                        },
                        onRevoke = { onRevokeSteamAccess(app) }
                    )
                }
            },
            confirmButton = {
                val canProceed = app.known.packageNames.any { hasLocalGamesAccess(it) || hasSteamGamesAccess(it) }
                TextButton(
                    onClick = {
                        showSetupDialog = null
                        onSelectApp(app)
                    },
                    enabled = canProceed
                ) { Text("View Games") }
            },
            dismissButton = {
                TextButton(onClick = { showSetupDialog = null }) { Text("Cancel") }
            }
        )
    }

    // ── Manage Access dialog (in-games-view) ─────────────────────────────────
    if (showManageAccessDialog && selectedApp != null) {
        AlertDialog(
            onDismissRequest = { showManageAccessDialog = false },
            icon = { Icon(Icons.Default.FolderShared, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Manage Folder Access", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FolderGrantRow(
                        label = "Local Games",
                        path = "data/files/usr/home/virtual_containers",
                        isGranted = selectedApp.known.packageNames.any { hasLocalGamesAccess(it) },
                        onGrant = {
                            showManageAccessDialog = false
                            pendingLocalApp = selectedApp
                            localFolderPicker.launch(initialLocalUriHintFor(selectedApp.activePackage))
                        },
                        onRevoke = {
                            showManageAccessDialog = false
                            onRevokeLocalAccess(selectedApp)
                        }
                    )
                    FolderGrantRow(
                        label = "Steam Games",
                        path = "data/files/Steam/steamapps/shadercache",
                        isGranted = selectedApp.known.packageNames.any { hasSteamGamesAccess(it) },
                        onGrant = {
                            showManageAccessDialog = false
                            pendingSteamApp = selectedApp
                            steamFolderPicker.launch(initialSteamUriHintFor(selectedApp.activePackage))
                        },
                        onRevoke = {
                            showManageAccessDialog = false
                            onRevokeSteamAccess(selectedApp)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageAccessDialog = false }) { Text("Done") }
            }
        )
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun FolderGrantRow(
    label: String,
    path: String,
    isGranted: Boolean,
    onGrant: () -> Unit,
    onRevoke: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (isGranted) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            "Granted",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(path, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isGranted) {
                    FilledTonalButton(
                        onClick = onGrant,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Grant Access", fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = onRevoke,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Revoke", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = topPadding + 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "($count)",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
}

@Composable
private fun GamesAppCard(
    app: GameHubApp,
    hasLocalAccess: Boolean,
    hasSteamAccess: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp),
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
                        Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = if (app.isInstalled) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.known.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AccessChip(label = "Local", isGranted = hasLocalAccess)
                    AccessChip(label = "Steam", isGranted = hasSteamAccess)
                }
            }
            Icon(
                if (hasLocalAccess || hasSteamAccess) Icons.Default.ChevronRight
                else Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AccessChip(label: String, isGranted: Boolean) {
    Surface(
        color = if (isGranted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                if (isGranted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(10.dp)
            )
            Text(
                label,
                fontSize = 10.sp,
                color = if (isGranted) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isGranted) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun GameCard(
    game: GameEntry,
    onLaunch: () -> Unit
) {
    if (game.type == GameType.STEAM) {
        SteamGameCard(game = game, onLaunch = onLaunch)
    } else {
        LocalGameCard(game = game, onLaunch = onLaunch)
    }
}

@Composable
private fun LocalGameCard(game: GameEntry, onLaunch: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Computer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(game.gameId, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2)
                Text("Local ID: ${game.gameId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun SteamGameCard(game: GameEntry, onLaunch: () -> Unit) {
    var info by remember(game.gameId) { mutableStateOf(SteamRepository.getCached(game.gameId)) }
    val scope = rememberCoroutineScope()

    // Fetch metadata when card first appears
    LaunchedEffect(game.gameId) {
        if (info == null) {
            scope.launch {
                info = SteamRepository.fetch(game.gameId)
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // ── Cover image ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(130.dp)
            ) {
                SubcomposeAsyncImage(
                    model = SteamRepository.coverUrl(game.gameId),
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(
                        RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                    ),
                    loading = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    },
                    error = {
                        // Fallback to header.jpg if portrait cover not available
                        SubcomposeAsyncImage(
                            model = SteamRepository.headerUrl(game.gameId),
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            loading = {},
                            error = {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Games,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        )
                    }
                )
            }

            // ── Info column ───────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Game name
                Text(
                    text = info?.name ?: game.gameId,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (info != null) {
                    // Genres row
                    if (info!!.genres.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            info!!.genres.take(3).forEach { genre ->
                                GenreChip(genre)
                            }
                        }
                    }

                    // Short description
                    if (!info!!.shortDescription.isNullOrBlank()) {
                        Text(
                            text = info!!.shortDescription!!,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 15.sp
                        )
                    }

                    // Bottom row: App ID + release year + Metacritic
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "App ID: ${game.gameId}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        info!!.releaseYear?.let { year ->
                            Text(
                                year,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        info!!.metacriticScore?.let { score ->
                            MetacriticBadge(score)
                        }
                    }
                } else {
                    // Still loading or fetch failed — show App ID as subtitle
                    Text(
                        "Steam App ID: ${game.gameId}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(2.dp))
                FilledTonalButton(
                    onClick = onLaunch,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun GenreChip(genre: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = genre,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MetacriticBadge(score: Int) {
    val bgColor = when {
        score >= 75 -> Color(0xFF6AB04C)   // green
        score >= 50 -> Color(0xFFFFBE76)   // yellow
        else        -> Color(0xFFEB4D4B)   // red
    }
    Surface(color = bgColor, shape = MaterialTheme.shapes.extraSmall) {
        Text(
            text = score.toString(),
            fontSize = 10.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}
