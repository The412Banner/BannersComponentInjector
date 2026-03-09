package com.banner.inject.ui.screens

import android.net.Uri
import android.os.Environment
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banner.inject.data.BackupManager
import com.banner.inject.data.RemoteSourceRepository
import com.banner.inject.model.MainTab
import com.banner.inject.model.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerScreen(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    repo: RemoteSourceRepository,
    onListBackups: () -> List<BackupManager.BackupInfo>,
    onDeleteBackup: (String) -> Unit,
    onShowSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Downloads navigation state
    var downloads by remember { mutableStateOf(repo.getAllDownloads()) }
    var selectedRepo by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var recordToDelete by remember { mutableStateOf<RemoteSourceRepository.DownloadedFile?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    // Backups section state
    var showingBackups by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf<List<BackupManager.BackupInfo>>(emptyList()) }
    var backupsLoading by remember { mutableStateOf(false) }
    var backupToDelete by remember { mutableStateOf<String?>(null) }

    val grouped = remember(downloads) {
        downloads.groupBy { it.sourceName }
            .mapValues { (_, files) -> files.groupBy { it.componentType } }
    }

    BackHandler(enabled = showingBackups || selectedRepo != null) {
        when {
            selectedType != null -> selectedType = null
            selectedRepo != null -> selectedRepo = null
            showingBackups -> showingBackups = false
        }
    }

    fun loadBackups() {
        backupsLoading = true
        scope.launch {
            backups = withContext(Dispatchers.IO) { onListBackups() }
            backupsLoading = false
        }
    }

    fun deleteDownloadRecord(record: RemoteSourceRepository.DownloadedFile) {
        scope.launch(Dispatchers.IO) {
            try {
                if (record.uriString != null) {
                    context.contentResolver.delete(Uri.parse(record.uriString), null, null)
                } else {
                    val safeSource = RemoteSourceRepository.sanitizeFolderName(record.sourceName)
                    val safeType = RemoteSourceRepository.sanitizeFolderName(record.componentType)
                    File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "BannersComponentInjector/$safeSource/$safeType/${record.fileName}"
                    ).delete()
                }
            } catch (_: Exception) { }
        }
        repo.removeDownloadRecord(record)
        downloads = repo.getAllDownloads()
    }

    fun clearAllDownloads() {
        val snapshot = downloads.toList()
        snapshot.forEach { deleteDownloadRecord(it) }
        downloads = repo.getAllDownloads()
        selectedRepo = null
        selectedType = null
    }

    // Navigation header title and back-button visibility
    val showBackButton = showingBackups || selectedRepo != null
    val headerTitle = when {
        selectedType != null -> "${selectedType!!.uppercase()} — $selectedRepo"
        selectedRepo != null -> selectedRepo!!
        showingBackups -> "Backups"
        else -> "My Downloads"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("My Downloads", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    actions = {
                        // Clear All only makes sense for the downloads root
                        if (!showingBackups && selectedRepo == null && downloads.isNotEmpty()) {
                            IconButton(onClick = { showClearAllDialog = true }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All Downloads")
                            }
                        }
                        IconButton(onClick = onShowSettings) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Navigation header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                if (showBackButton) {
                    IconButton(
                        onClick = {
                            when {
                                selectedType != null -> selectedType = null
                                selectedRepo != null -> selectedRepo = null
                                showingBackups -> showingBackups = false
                            }
                        },
                        modifier = Modifier.size(28.dp).padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
                Text(
                    text = headerTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                // Storage summary at the downloads root
                if (!showingBackups && selectedRepo == null && downloads.isNotEmpty()) {
                    Text(
                        text = formatManagerSize(downloads.sumOf { it.fileSizeBytes }),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                // ── Backups section ──────────────────────────────────────────
                showingBackups -> {
                    when {
                        backupsLoading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        backups.isEmpty() -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Backup,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "No backups yet",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Backups created from the Inject Components tab will appear here.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        else -> {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(backups, key = { it.componentName }) { info ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.FolderOpen,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    info.componentName,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    "${info.fileCount} file${if (info.fileCount == 1) "" else "s"}  ·  ${formatSize(info.totalSize)}",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(
                                                onClick = { backupToDelete = info.componentName },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete backup",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Root: Backups folder card + repo download cards ──────────
                selectedRepo == null -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Always-visible Backups folder
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    showingBackups = true
                                    loadBackups()
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Backup,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Text(
                                        "Backups",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (downloads.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "No downloads yet",
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Components downloaded from the Download tab will appear here.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            items(grouped.keys.toList()) { repoName ->
                                val repoTypes = grouped[repoName] ?: return@items
                                val totalFiles = repoTypes.values.sumOf { it.size }
                                val totalSize = repoTypes.values.flatten().sumOf { it.fileSizeBytes }
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { selectedRepo = repoName },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Source,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(repoName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                            Text(
                                                "$totalFiles file${if (totalFiles != 1) "s" else ""} · ${formatManagerSize(totalSize)}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            Icons.Default.ArrowForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Repo → Type list ─────────────────────────────────────────
                selectedType == null -> {
                    val repoTypes = grouped[selectedRepo] ?: emptyMap()
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(repoTypes.keys.toList()) { typeName ->
                            val typeFiles = repoTypes[typeName] ?: emptyList()
                            val totalSize = typeFiles.sumOf { it.fileSizeBytes }
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { selectedType = typeName },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(typeName.uppercase(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        Text(
                                            "${typeFiles.size} file${if (typeFiles.size != 1) "s" else ""} · ${formatManagerSize(totalSize)}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Type → File list ─────────────────────────────────────────
                else -> {
                    val fileList = grouped[selectedRepo]?.get(selectedType) ?: emptyList()
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(fileList) { record ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            record.fileName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            maxLines = 2
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            buildString {
                                                if (record.fileSizeBytes > 0) {
                                                    append(formatManagerSize(record.fileSizeBytes))
                                                    append(" · ")
                                                }
                                                if (record.downloadedAt > 0) append(formatManagerDate(record.downloadedAt))
                                            },
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { recordToDelete = record },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete download confirmation
    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("Delete Download") },
            text = { Text("Remove '${record.fileName}' from your device?") },
            confirmButton = {
                TextButton(onClick = {
                    deleteDownloadRecord(record)
                    recordToDelete = null
                    if ((grouped[selectedRepo]?.get(selectedType)?.size ?: 0) <= 1) {
                        selectedType = null
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Delete backup confirmation
    backupToDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { backupToDelete = null },
            icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Backup") },
            text = { Text("Permanently delete the backup for $name?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteBackup(name)
                    backups = backups.filter { it.componentName != name }
                    backupToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { backupToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Clear all downloads confirmation
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Downloads") },
            text = { Text("This will remove all ${downloads.size} downloaded file records and delete the files from your device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    clearAllDownloads()
                    showClearAllDialog = false
                }) { Text("Clear All", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatManagerSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes < 1_024L -> "$bytes B"
    bytes < 1_048_576L -> "${bytes / 1_024L} KB"
    else -> "${"%.1f".format(bytes / 1_048_576f)} MB"
}

private fun formatManagerDate(epochMs: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMs))
