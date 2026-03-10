package com.banner.inject.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banner.inject.data.BackupManager
import com.banner.inject.model.GameHubApp
import com.banner.inject.model.MainTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    apps: List<GameHubApp>,
    onAppSelected: (GameHubApp) -> Unit,       // app already has access
    onAccessGranted: (GameHubApp, Uri) -> Unit, // user just picked the SAF folder
    onRevokeAccess: (GameHubApp) -> Unit,
    onRefresh: () -> Unit,
    initialUriHintFor: (String) -> Uri,
    onListBackups: () -> List<BackupManager.BackupInfo>,
    onDeleteBackupByName: (String) -> Unit,
    appVersion: String,
    accentColor: Color,
    onAccentColorChanged: (Color) -> Unit,
    onAddCustomApp: (name: String, packageName: String) -> Unit,
    onRemoveCustomApp: (GameHubApp) -> Unit
) {
    // Track which app is pending an SAF grant so the launcher callback knows
    var pendingApp by remember { mutableStateOf<GameHubApp?>(null) }
    var showGrantGuide by remember { mutableStateOf<GameHubApp?>(null) }
    var showRevokeDialog by remember { mutableStateOf<GameHubApp?>(null) }
    var showDeleteCustomDialog by remember { mutableStateOf<GameHubApp?>(null) }
    var showAddCustomApp by remember { mutableStateOf(false) }
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
            Column {
                TopAppBar(
                    title = {
                        Text("Component Injector", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    },
                    actions = {
                        IconButton(onClick = { showAddCustomApp = true }) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = "Add Custom App")
                        }
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
                    onRevokeClick = { showRevokeDialog = app },
                    onDeleteClick = if (app.known.isCustom) ({ showDeleteCustomDialog = app }) else null
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
            accentColor = accentColor,
            onAccentColorChanged = onAccentColorChanged,
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

    showDeleteCustomDialog?.let { app ->
        AlertDialog(
            onDismissRequest = { showDeleteCustomDialog = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove Custom App") },
            text = { Text("Remove \"${app.known.displayName}\" from the app list? This will also revoke its folder access.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteCustomDialog = null
                    onRemoveCustomApp(app)
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCustomDialog = null }) { Text("Cancel") }
            }
        )
    }

    if (showAddCustomApp) {
        AddCustomAppDialog(
            existingPackageNames = apps.flatMap { it.known.packageNames }.toSet(),
            onDismiss = { showAddCustomApp = false },
            onAdd = { name, pkg ->
                showAddCustomApp = false
                onAddCustomApp(name, pkg)
            }
        )
    }
}

@Composable
private fun AppCard(
    app: GameHubApp,
    onClick: () -> Unit,
    onRevokeClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null
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
            // Delete button for custom apps
            if (onDeleteClick != null) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove custom app",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomAppDialog(
    existingPackageNames: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (name: String, packageName: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }

    val nameError = name.isNotBlank() && name.trim().isEmpty()
    val pkgError = packageName.isNotBlank() && packageName.trim() in existingPackageNames
    val canAdd = name.isNotBlank() && packageName.isNotBlank() && !pkgError

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AddCircleOutline, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Add Custom App", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Add a GameHub variant that isn't listed. You'll need to grant folder access after adding.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g. GameHub Custom Edition") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it.trim() },
                    label = { Text("Package Name") },
                    placeholder = { Text("e.g. com.example.gamehub") },
                    singleLine = true,
                    isError = pkgError,
                    supportingText = if (pkgError) {
                        { Text("This package is already in the list", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name.trim(), packageName.trim()) },
                enabled = canAdd
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
