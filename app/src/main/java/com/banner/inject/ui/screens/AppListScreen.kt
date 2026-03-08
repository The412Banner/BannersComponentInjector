package com.banner.inject.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banner.inject.data.BackupManager
import com.banner.inject.data.UpdateRepository
import com.banner.inject.model.GameHubApp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    apps: List<GameHubApp>,
    onAppSelected: (GameHubApp) -> Unit,       // app already has access
    onAccessGranted: (GameHubApp, Uri) -> Unit, // user just picked the SAF folder
    onRevokeAccess: (GameHubApp) -> Unit,
    onRefresh: () -> Unit,
    initialUriHintFor: (String) -> Uri,
    onListBackups: () -> List<BackupManager.BackupInfo>,
    onDeleteBackupByName: (String) -> Unit,
    appVersion: String
) {
    // Track which app is pending an SAF grant so the launcher callback knows
    var pendingApp by remember { mutableStateOf<GameHubApp?>(null) }
    var showGrantGuide by remember { mutableStateOf<GameHubApp?>(null) }
    var showRevokeDialog by remember { mutableStateOf<GameHubApp?>(null) }
    var showBackupManager by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val app = pendingApp
        pendingApp = null
        if (uri != null && app != null) onAccessGranted(app, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Component Injector", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Select your GameHub version",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }

            items(apps, key = { it.known.packageNames.first() }) { app ->
                AppCard(
                    app = app,
                    onClick = {
                        if (app.hasAccess) {
                            onAppSelected(app)
                        } else {
                            showGrantGuide = app
                        }
                    },
                    onRevokeClick = { showRevokeDialog = app }
                )
            }
        }
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
            onDismiss = { showSettings = false },
            onOpenBackupManager = { showSettings = false; showBackupManager = true }
        )
    }

    showGrantGuide?.let { app ->
        AlertDialog(
            onDismissRequest = { showGrantGuide = null },
            icon = { Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Grant Folder Access") },
            text = {
                Column {
                    Text(
                        "In the folder picker, select ${app.known.displayName} from the sidebar, then navigate to the components folder and tap \"Use this folder\".",
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
                                "data/files/usr/home/components",
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
                    folderPicker.launch(initialUriHintFor(app.activePackage))
                }) { Text("Open Folder Picker") }
            },
            dismissButton = {
                TextButton(onClick = { showGrantGuide = null }) { Text("Cancel") }
            }
        )
    }

    showRevokeDialog?.let { app ->
        AlertDialog(
            onDismissRequest = { showRevokeDialog = null },
            title = { Text("Remove Access") },
            text = { Text("Remove folder access for ${app.known.displayName}? You can re-grant it any time.") },
            confirmButton = {
                TextButton(onClick = {
                    showRevokeDialog = null
                    onRevokeAccess(app)
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeDialog = null }) { Text("Cancel") }
            }
        )
    }
}

private sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val release: UpdateRepository.ReleaseInfo) : UpdateState()
    object UpToDate : UpdateState()
    data class Error(val message: String) : UpdateState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(appVersion: String, onDismiss: () -> Unit, onOpenBackupManager: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("bci_settings", Context.MODE_PRIVATE) }

    var includePreReleases by remember {
        mutableStateOf(prefs.getBoolean("update_include_pre", false))
    }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Settings, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))

            // ── About ──────────────────────────────────────────────────────
            Text(
                "About",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("BannersComponentInjector", fontSize = 14.sp)
                        Text(
                            "v$appVersion",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Updates ────────────────────────────────────────────────────
            Text(
                "Updates",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Include pre-releases", fontSize = 14.sp)
                            Text(
                                "Check for pre-release updates too",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = includePreReleases,
                            onCheckedChange = {
                                includePreReleases = it
                                prefs.edit().putBoolean("update_include_pre", it).apply()
                                updateState = UpdateState.Idle
                            }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            updateState = UpdateState.Checking
                            scope.launch {
                                updateState = try {
                                    val latest = UpdateRepository.fetchLatestRelease(includePreReleases)
                                    if (latest == null) {
                                        UpdateState.Error("No releases found.")
                                    } else if (latest.versionName == appVersion) {
                                        UpdateState.UpToDate
                                    } else {
                                        UpdateState.Available(latest)
                                    }
                                } catch (e: Exception) {
                                    UpdateState.Error(e.message ?: "Unknown error")
                                }
                            }
                        },
                        enabled = updateState !is UpdateState.Checking,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (updateState is UpdateState.Checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Checking...")
                        } else {
                            Icon(Icons.Default.SystemUpdate, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Check for Updates")
                        }
                    }
                    when (val state = updateState) {
                        is UpdateState.UpToDate -> {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "You're up to date (v$appVersion)",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        is UpdateState.Error -> {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Warning,
                                    null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    state.message,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onOpenBackupManager,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Backup, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Backup Manager")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent("android.intent.action.VIEW_DOWNLOADS"))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Downloads Folder")
            }
        }
    }

    // Update available dialog
    val state = updateState
    if (state is UpdateState.Available) {
        AlertDialog(
            onDismissRequest = { updateState = UpdateState.Idle },
            icon = {
                Icon(
                    Icons.Default.SystemUpdate, null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Update Available") },
            text = {
                Column {
                    Text("A new version is available:")
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Installed", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("v$appVersion", fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Available", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    state.release.tagName + if (state.release.isPreRelease) " (pre-release)" else "",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(state.release.htmlUrl))
                        )
                    }
                    updateState = UpdateState.Idle
                }) { Text("Open GitHub Release") }
            },
            dismissButton = {
                TextButton(onClick = { updateState = UpdateState.Idle }) { Text("Later") }
            }
        )
    }
}

@Composable
private fun AppCard(
    app: GameHubApp,
    onClick: () -> Unit,
    onRevokeClick: () -> Unit
) {
    val isClickable = app.isInstalled || app.hasAccess

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isClickable) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !app.isInstalled && !app.hasAccess -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isClickable) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon placeholder
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (app.isInstalled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (app.isInstalled) Icons.Default.VideogameAsset
                                      else Icons.Default.VideogameAssetOff,
                        contentDescription = null,
                        tint = if (app.isInstalled) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.known.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = if (isClickable) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        app.hasAccess && app.isInstalled -> "Tap to manage components"
                        app.hasAccess && !app.isInstalled -> "Access granted (not installed)"
                        app.isInstalled && !app.hasAccess -> "Installed  —  tap to grant access"
                        else -> "Not installed"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Status badge
            when {
                app.hasAccess -> {
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
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                "Access",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    // Revoke button (small icon)
                    IconButton(
                        onClick = onRevokeClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.LinkOff,
                            contentDescription = "Revoke access",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                app.isInstalled -> {
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
}
