package com.banner.inject.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.banner.inject.data.GameOverrideRepository
import com.banner.inject.data.SteamGameInfo
import com.banner.inject.data.SteamRepository
import com.banner.inject.model.GameEntry
import com.banner.inject.model.GameHubApp
import com.banner.inject.model.GameOverride
import com.banner.inject.model.GameType
import com.banner.inject.model.MainTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyGamesScreen(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    apps: List<GameHubApp>,
    selectedApp: GameHubApp?,
    importedGames: List<GameEntry>,
    steamGames: List<GameEntry>,
    isLoadingSteam: Boolean,
    hasDataAccess: (String) -> Boolean,
    onSelectApp: (GameHubApp) -> Unit,
    onAccessGranted: (GameHubApp, Uri) -> Unit,
    onRevokeAccess: (GameHubApp) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onExportIsos: () -> Unit,
    onLaunchGame: (packageName: String, gameId: String) -> Unit,
    onAddImport: (name: String, localId: String) -> Unit,
    onRemoveImport: (GameEntry) -> Unit,
    initialDataUriHintFor: (String) -> Uri
) {
    val context = LocalContext.current
    val overrideRepo = remember { GameOverrideRepository(context) }
    val overrides = remember { mutableStateMapOf<String, GameOverride>() }
    val scope = rememberCoroutineScope()

    // Load stored overrides whenever game lists change
    val allGameIds = remember(importedGames, steamGames) { (importedGames + steamGames).map { it.gameId } }
    LaunchedEffect(allGameIds) {
        val loaded = withContext(Dispatchers.IO) {
            allGameIds.mapNotNull { id ->
                overrideRepo.get(id)?.let { id to it }
            }
        }
        loaded.forEach { (id, override) -> overrides[id] = override }
    }

    var pendingApp by remember { mutableStateOf<GameHubApp?>(null) }
    var showSetupDialog by remember { mutableStateOf<GameHubApp?>(null) }
    var showManageAccessDialog by remember { mutableStateOf(false) }
    var editingGame by remember { mutableStateOf<GameEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var confirmRemoveImport by remember { mutableStateOf<GameEntry?>(null) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val app = pendingApp; pendingApp = null
        if (uri != null && app != null) onAccessGranted(app, uri)
    }

    val eligibleApps = apps.filter {
        it.isInstalled || it.known.packageNames.any { p -> hasDataAccess(p) }
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
                            fontWeight = FontWeight.Bold, fontSize = 18.sp
                        )
                    },
                    actions = {
                        if (selectedApp != null) {
                            IconButton(onClick = onExportIsos, enabled = steamGames.isNotEmpty()) {
                                Icon(Icons.Default.FileDownload, contentDescription = "Export ISOs")
                            }
                            IconButton(onClick = onRefresh) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Steam")
                            }
                            IconButton(onClick = { showManageAccessDialog = true }) {
                                Icon(Icons.Default.FolderShared, contentDescription = "Manage access")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                MainTabRow(currentTab = currentTab, onTabSelected = onTabSelected)
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add imported game")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // ── Imported Games section ────────────────────────────────────────
            item {
                SectionHeader("Imported Games", importedGames.size, Icons.Default.Computer)
            }
            if (importedGames.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddCircleOutline, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No imported games yet.",
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Tap + to add one.",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            } else {
                items(importedGames, key = { "local_${it.gameId}" }) { game ->
                    LocalGameCard(
                        game = game,
                        override = overrides[game.gameId],
                        onLaunch = {
                            selectedApp?.let { onLaunchGame(it.activePackage, game.gameId) }
                                ?: run { /* no app selected, play button still works if any app has access */ }
                        },
                        onEdit = { editingGame = game },
                        onResetOverride = if (overrides.containsKey(game.gameId)) ({
                            overrideRepo.clear(game.gameId)
                            overrides.remove(game.gameId)
                        }) else null,
                        onRemove = { confirmRemoveImport = game }
                    )
                }
            }

            // ── Steam Games section ───────────────────────────────────────────
            item {
                SectionHeader(
                    title = "Steam Games",
                    count = steamGames.size,
                    icon = Icons.Default.Games,
                    topPadding = 8.dp
                )
            }
            if (selectedApp == null) {
                if (eligibleApps.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
                            contentAlignment = Alignment.Center) {
                            Text("No GameHub variants installed.",
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    item {
                        Text(
                            "Select your GameHub version to browse Steam games.",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(eligibleApps, key = { "app_${it.known.packageNames.first()}" }) { app ->
                        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            GamesAppCard(
                                app = app,
                                hasAccess = app.known.packageNames.any { hasDataAccess(it) },
                                onClick = {
                                    if (app.known.packageNames.any { hasDataAccess(it) }) onSelectApp(app)
                                    else showSetupDialog = app
                                }
                            )
                        }
                    }
                }
            } else if (isLoadingSteam) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Scanning Steam games…", fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else if (steamGames.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center) {
                        Text("No Steam games found in ${selectedApp.known.displayName}.",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(steamGames, key = { "steam_${it.gameId}" }) { game ->
                    SteamGameCard(
                        game = game,
                        override = overrides[game.gameId],
                        onLaunch = { onLaunchGame(selectedApp.activePackage, game.gameId) },
                        onEdit = { editingGame = game },
                        onResetOverride = if (overrides.containsKey(game.gameId)) ({
                            overrideRepo.clear(game.gameId)
                            overrides.remove(game.gameId)
                        }) else null
                    )
                }
            }
        }
    }

    // ── Add Import dialog ─────────────────────────────────────────────────────
    if (showAddDialog) {
        var nameInput by remember { mutableStateOf("") }
        var localIdInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            icon = { Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Add Imported Game", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Game Name") },
                        placeholder = { Text("e.g. Halo Infinite") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = localIdInput,
                        onValueChange = { localIdInput = it },
                        label = { Text("Local ID") },
                        placeholder = { Text("e.g. halo-infinite") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("The game's ID used by GameHub") }
                    )
                    Text(
                        "A .iso file named after the game will be saved to Downloads/front end/",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddImport(nameInput.trim(), localIdInput.trim())
                        showAddDialog = false
                    },
                    enabled = nameInput.isNotBlank() && localIdInput.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Remove confirmation dialog ────────────────────────────────────────────
    confirmRemoveImport?.let { game ->
        val displayName = overrides[game.gameId]?.customName ?: game.gameId
        AlertDialog(
            onDismissRequest = { confirmRemoveImport = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove Game?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "\"$displayName\" will be removed from your import list and its .iso file deleted from Downloads/front end/.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { onRemoveImport(game); confirmRemoveImport = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveImport = null }) { Text("Cancel") }
            }
        )
    }

    // ── Edit sheet ────────────────────────────────────────────────────────────
    editingGame?.let { game ->
        val steamInfo = remember(game.gameId) { SteamRepository.getCached(game.gameId) }
        GameEditSheet(
            game = game,
            initialOverride = overrides[game.gameId],
            steamInfo = steamInfo,
            onSave = { override ->
                overrideRepo.save(override)
                overrides[override.gameId] = override
                override.linkedSteamAppId?.let { linkedId ->
                    scope.launch { SteamRepository.fetch(linkedId) }
                }
                editingGame = null
            },
            onDismiss = { editingGame = null }
        )
    }

    // ── Setup dialog (no access yet) ─────────────────────────────────────────
    showSetupDialog?.let { app ->
        AlertDialog(
            onDismissRequest = { showSetupDialog = null },
            icon = { Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Grant Folder Access", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Grant access to ${app.known.displayName}'s data folder to browse its Steam games.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FolderGrantRow(
                        label = "GameHub Data Folder",
                        path = "Android/data/${app.activePackage}",
                        isGranted = app.known.packageNames.any { hasDataAccess(it) },
                        onGrant = { pendingApp = app; folderPicker.launch(initialDataUriHintFor(app.activePackage)) },
                        onRevoke = { onRevokeAccess(app) }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showSetupDialog = null; onSelectApp(app) },
                    enabled = app.known.packageNames.any { hasDataAccess(it) }
                ) { Text("View Steam Games") }
            },
            dismissButton = { TextButton(onClick = { showSetupDialog = null }) { Text("Cancel") } }
        )
    }

    // ── Manage Access dialog ──────────────────────────────────────────────────
    if (showManageAccessDialog && selectedApp != null) {
        AlertDialog(
            onDismissRequest = { showManageAccessDialog = false },
            icon = { Icon(Icons.Default.FolderShared, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Manage Folder Access", fontWeight = FontWeight.Bold) },
            text = {
                FolderGrantRow(
                    label = "GameHub Data Folder",
                    path = "Android/data/${selectedApp.activePackage}",
                    isGranted = selectedApp.known.packageNames.any { hasDataAccess(it) },
                    onGrant = {
                        showManageAccessDialog = false
                        pendingApp = selectedApp
                        folderPicker.launch(initialDataUriHintFor(selectedApp.activePackage))
                    },
                    onRevoke = { showManageAccessDialog = false; onRevokeAccess(selectedApp) }
                )
            },
            confirmButton = { TextButton(onClick = { showManageAccessDialog = false }) { Text("Done") } }
        )
    }
}

// ── Local game card ───────────────────────────────────────────────────────────

@Composable
private fun LocalGameCard(
    game: GameEntry,
    override: GameOverride?,
    onLaunch: () -> Unit,
    onEdit: () -> Unit,
    onResetOverride: (() -> Unit)?,
    onRemove: () -> Unit
) {
    val linkedId = override?.linkedSteamAppId
    val displayName = override?.customName ?: game.gameId
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            // Cover: linked Steam art or fallback icon
            Box(modifier = Modifier.width(80.dp).height(130.dp)) {
                if (linkedId != null) {
                    SubcomposeAsyncImage(
                        model = SteamRepository.coverUrl(linkedId),
                        contentDescription = "Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
                        loading = {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        },
                        error = { LocalCoverFallback() }
                    )
                } else {
                    LocalCoverFallback()
                }
            }

            Column(
                modifier = Modifier.weight(1f).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (override?.customGenres?.isNotEmpty() == true) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        override.customGenres!!.take(3).forEach { GenreChip(it) }
                    }
                }
                if (!override?.customDescription.isNullOrBlank()) {
                    Text(override!!.customDescription!!, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Local ID: ${game.gameId}", fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    override?.customReleaseYear?.let { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) }
                    override?.customMetacriticScore?.let { MetacriticBadge(it) }
                }
                Spacer(Modifier.height(2.dp))
                FilledTonalButton(onClick = onLaunch,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play", fontSize = 12.sp)
                }
            }

            // Hamburger menu
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options",
                        modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp)) },
                        onClick = { menuExpanded = false; onEdit() }
                    )
                    if (onResetOverride != null) {
                        DropdownMenuItem(
                            text = { Text("Reset to defaults") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp)) },
                            onClick = { menuExpanded = false; onResetOverride() }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null,
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                        onClick = { menuExpanded = false; onRemove() }
                    )
                }
            }
        }
    }
}

// Thumbnail for search results — tries portrait cover, falls back to header image.
// Disk cache disabled so Coil never serves a cached 404.
@Composable
private fun SearchResultThumbnail(appId: String) {
    val context = LocalContext.current
    var useFallbackUrl by remember(appId) { mutableStateOf(false) }
    val url = if (useFallbackUrl) SteamRepository.headerUrl(appId) else SteamRepository.coverUrl(appId)
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .diskCachePolicy(CachePolicy.DISABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(width = 30.dp, height = 45.dp)
            .clip(MaterialTheme.shapes.extraSmall),
        loading = {
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            )
        },
        error = {
            if (!useFallbackUrl) {
                useFallbackUrl = true
            } else {
                Box(
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Games, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp))
                }
            }
        }
    )
}

@Composable
private fun LocalCoverFallback() {
    Box(
        modifier = Modifier.fillMaxSize()
            .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Computer, null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(28.dp))
    }
}

// ── Steam game card ───────────────────────────────────────────────────────────

@Composable
private fun SteamGameCard(
    game: GameEntry,
    override: GameOverride?,
    onLaunch: () -> Unit,
    onEdit: () -> Unit,
    onResetOverride: (() -> Unit)?
) {
    var info by remember(game.gameId) { mutableStateOf<SteamGameInfo?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(game.gameId) {
        if (info == null) info = SteamRepository.fetch(game.gameId)
    }

    val displayName     = override?.customName        ?: info?.name             ?: game.gameId
    val displayGenres   = override?.customGenres      ?: info?.genres           ?: emptyList()
    val displayDesc     = override?.customDescription ?: info?.shortDescription
    val displayYear     = override?.customReleaseYear ?: info?.releaseYear
    val displayMeta     = override?.customMetacriticScore ?: info?.metacriticScore
    val coverAppId      = override?.linkedSteamAppId  ?: game.gameId

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.width(80.dp).height(130.dp)) {
                SubcomposeAsyncImage(
                    model = SteamRepository.coverUrl(coverAppId),
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
                    loading = {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    },
                    error = {
                        SubcomposeAsyncImage(
                            model = SteamRepository.headerUrl(coverAppId),
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            loading = {},
                            error = {
                                Box(Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                                    contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Games, null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(28.dp))
                                }
                            }
                        )
                    }
                )
            }

            Column(
                modifier = Modifier.weight(1f).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (displayGenres.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        displayGenres.take(3).forEach { GenreChip(it) }
                    }
                }
                if (!displayDesc.isNullOrBlank()) {
                    Text(displayDesc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("App ID: ${game.gameId}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    displayYear?.let { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) }
                    displayMeta?.let { MetacriticBadge(it) }
                }
                Spacer(Modifier.height(2.dp))
                FilledTonalButton(onClick = onLaunch,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play", fontSize = 12.sp)
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options", modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp)) },
                        onClick = { menuExpanded = false; onEdit() }
                    )
                    if (onResetOverride != null) {
                        DropdownMenuItem(
                            text = { Text("Reset to defaults") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp)) },
                            onClick = { menuExpanded = false; onResetOverride() }
                        )
                    }
                }
            }
        }
    }
}

// ── Edit sheet ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameEditSheet(
    game: GameEntry,
    initialOverride: GameOverride?,
    steamInfo: SteamGameInfo?,
    onSave: (GameOverride) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var nameField   by remember { mutableStateOf(initialOverride?.customName        ?: steamInfo?.name             ?: "") }
    var genresField by remember { mutableStateOf(
        (initialOverride?.customGenres ?: steamInfo?.genres)?.joinToString(", ") ?: ""
    )}
    var descField   by remember { mutableStateOf(initialOverride?.customDescription ?: steamInfo?.shortDescription ?: "") }
    var yearField   by remember { mutableStateOf(initialOverride?.customReleaseYear ?: steamInfo?.releaseYear      ?: "") }
    var metaField   by remember { mutableStateOf(
        (initialOverride?.customMetacriticScore ?: steamInfo?.metacriticScore)?.toString() ?: ""
    )}
    var linkedId    by remember { mutableStateOf(initialOverride?.linkedSteamAppId) }

    var searchResults by remember { mutableStateOf<List<SteamRepository.SearchResult>>(emptyList()) }
    var isSearching   by remember { mutableStateOf(false) }
    var searchError   by remember { mutableStateOf<String?>(null) }
    var isFillingFromSearch by remember { mutableStateOf(false) }

    fun doSearch() {
        val query = nameField.trim()
        if (query.isBlank()) return
        scope.launch {
            isSearching = true
            searchError = null
            searchResults = emptyList()
            val results = SteamRepository.searchByName(query)
            if (results.isEmpty()) searchError = "No results found for \"$query\""
            else searchResults = results
            isSearching = false
        }
    }

    fun fillFromSteam(appId: String) {
        scope.launch {
            isFillingFromSearch = true
            val info = SteamRepository.fetch(appId)
            if (info != null) {
                if (nameField.isBlank() || nameField == game.gameId) nameField = info.name
                if (genresField.isBlank()) genresField = info.genres.joinToString(", ")
                if (descField.isBlank()) descField = info.shortDescription ?: ""
                if (yearField.isBlank()) yearField = info.releaseYear ?: ""
                if (metaField.isBlank()) metaField = info.metacriticScore?.toString() ?: ""
                linkedId = appId
            }
            searchResults = emptyList()
            isFillingFromSearch = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    title = {
                        Text("Edit Game", fontWeight = FontWeight.Bold, fontSize = 17.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    actions = {
                        TextButton(onClick = {
                            val metaInt = metaField.trim().toIntOrNull()?.takeIf { it in 1..100 }
                            val genreList = genresField.split(",")
                                .map { it.trim() }.filter { it.isNotBlank() }
                                .takeIf { it.isNotEmpty() }
                            onSave(GameOverride(
                                gameId = game.gameId,
                                customName = nameField.trim().takeIf { it.isNotBlank() },
                                customGenres = genreList,
                                customDescription = descField.trim().takeIf { it.isNotBlank() },
                                customReleaseYear = yearField.trim().takeIf { it.isNotBlank() },
                                customMetacriticScore = metaInt,
                                linkedSteamAppId = linkedId
                            ))
                        }) {
                            Text("Save", fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                linkedId?.let { id ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SubcomposeAsyncImage(
                            model = SteamRepository.coverUrl(id),
                            contentDescription = "Cover preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 60.dp, height = 90.dp)
                                .clip(MaterialTheme.shapes.small),
                            loading = {
                                Box(Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)))
                            },
                            error = {}
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Linked to Steam App ID:", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(id, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary)
                            TextButton(
                                onClick = { linkedId = null },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(28.dp)
                            ) { Text("Unlink", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    HorizontalDivider()
                }

                Text("Game Name", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = nameField,
                    onValueChange = { nameField = it; searchResults = emptyList(); searchError = null },
                    placeholder = { Text("Enter game name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (nameField.isNotBlank()) {
                            IconButton(onClick = { nameField = ""; searchResults = emptyList() }) {
                                Icon(Icons.Default.Clear, null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { doSearch() },
                        enabled = nameField.isNotBlank() && !isSearching,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Search Steam", fontSize = 13.sp)
                    }
                    if (isFillingFromSearch) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Loading…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                searchError?.let {
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }

                if (searchResults.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column {
                            Text("Tap a result to auto-fill all fields:",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            HorizontalDivider()
                            searchResults.forEachIndexed { index, result ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { fillFromSteam(result.appId) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SearchResultThumbnail(appId = result.appId)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(result.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("App ID: ${result.appId}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronRight, null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                                if (index < searchResults.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text("Genres", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = genresField,
                    onValueChange = { genresField = it },
                    placeholder = { Text("e.g. Action, RPG, Strategy") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Comma-separated", fontSize = 10.sp) }
                )

                Text("Description", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = descField,
                    onValueChange = { descField = it },
                    placeholder = { Text("Short description shown on the card") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Release Year", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = yearField,
                            onValueChange = { if (it.length <= 4) yearField = it.filter { c -> c.isDigit() } },
                            placeholder = { Text("e.g. 2023") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Metacritic Score", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = metaField,
                            onValueChange = { if (it.length <= 3) metaField = it.filter { c -> c.isDigit() } },
                            placeholder = { Text("1–100") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            supportingText = { Text("Leave blank to hide", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(
                            if (game.type == GameType.STEAM) "Launched with Steam App ID: ${game.gameId}"
                            else "Launched with Local ID: ${game.gameId}",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ── Reusable small composables ────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = topPadding + 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Text("($count)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
}

@Composable
private fun GamesAppCard(
    app: GameHubApp,
    hasAccess: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.small,
                color = if (app.isInstalled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SportsEsports, null,
                        tint = if (app.isInstalled) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.known.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (hasAccess) {
                    Spacer(Modifier.height(4.dp))
                    AccessChip("Access Granted", granted = true)
                }
            }
            Icon(
                if (hasAccess) Icons.Default.ChevronRight else Icons.Default.FolderOpen,
                null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AccessChip(label: String, granted: Boolean) {
    Surface(
        color = if (granted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(if (granted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
                tint = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(10.dp))
            Text(label, fontSize = 10.sp,
                color = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (granted) FontWeight.Medium else FontWeight.Normal)
        }
    }
}

@Composable
private fun FolderGrantRow(
    label: String, path: String, isGranted: Boolean, onGrant: () -> Unit, onRevoke: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (isGranted) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                        Text("Granted", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(path, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            if (!isGranted) {
                FilledTonalButton(onClick = onGrant, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)) { Text("Grant Access", fontSize = 12.sp) }
            } else {
                OutlinedButton(onClick = onRevoke, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)) {
                    Text("Revoke", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun GenreChip(genre: String) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.extraSmall) {
        Text(genre, fontSize = 9.sp, color = MaterialTheme.colorScheme.onTertiaryContainer,
            fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
    }
}

@Composable
private fun MetacriticBadge(score: Int) {
    val bgColor = when {
        score >= 75 -> Color(0xFF6AB04C)
        score >= 50 -> Color(0xFFFFBE76)
        else        -> Color(0xFFEB4D4B)
    }
    Surface(color = bgColor, shape = MaterialTheme.shapes.extraSmall) {
        Text(score.toString(), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
    }
}
