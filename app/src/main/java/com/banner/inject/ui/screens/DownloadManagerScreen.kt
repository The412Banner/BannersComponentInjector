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
import com.banner.inject.data.RemoteSourceRepository
import com.banner.inject.model.MainTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    onShowBackupManager: () -> Unit,
    onShowSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var downloads by remember { mutableStateOf(repo.getAllDownloads()) }
    var selectedRepo by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var recordToDelete by remember { mutableStateOf<RemoteSourceRepository.DownloadedFile?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    // Grouped: sourceName → componentType → List<DownloadedFile>
    val grouped = remember(downloads) {
        downloads.groupBy { it.sourceName }
            .mapValues { (_, files) -> files.groupBy { it.componentType } }
    }

    BackHandler(enabled = selectedRepo != null) {
        if (selectedType != null) selectedType = null else selectedRepo = null
    }

    fun deleteRecord(record: RemoteSourceRepository.DownloadedFile) {
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

    fun clearAll() {
        downloads.forEach { deleteRecord(it) }
        downloads = repo.getAllDownloads()
        selectedRepo = null
        selectedType = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("My Downloads", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    actions = {
                        if (downloads.isNotEmpty() && selectedRepo == null) {
                            IconButton(onClick = { showClearAllDialog = true }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All")
                            }
                        }
                        IconButton(onClick = onShowBackupManager) {
                            Icon(Icons.Default.Backup, contentDescription = "Backup Manager")
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
            // Navigation header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                if (selectedRepo != null) {
                    IconButton(
                        onClick = { if (selectedType != null) selectedType = null else selectedRepo = null },
                        modifier = Modifier.size(28.dp).padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
                Text(
                    text = when {
                        selectedType != null -> "${selectedType!!.uppercase()} — $selectedRepo"
                        selectedRepo != null -> selectedRepo!!
                        else -> "All Repositories"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                // Storage summary at root level
                if (selectedRepo == null && downloads.isNotEmpty()) {
                    val totalSize = downloads.sumOf { it.fileSizeBytes }
                    Text(
                        text = formatManagerSize(totalSize),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                downloads.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No downloads yet",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Components downloaded from the Download tab will appear here.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                selectedRepo == null -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(grouped.keys.toList()) { repoName ->
                            val repoTypes = grouped[repoName] ?: return@items
                            val totalFiles = repoTypes.values.sumOf { it.size }
                            val totalSize = repoTypes.values.flatten().sumOf { it.fileSizeBytes }
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { selectedRepo = repoName },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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

                selectedType == null -> {
                    val repoTypes = grouped[selectedRepo] ?: emptyMap()
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(repoTypes.keys.toList()) { typeName ->
                            val typeFiles = repoTypes[typeName] ?: emptyList()
                            val totalSize = typeFiles.sumOf { it.fileSizeBytes }
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { selectedType = typeName },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                                        Text(
                                            typeName.uppercase(),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        )
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

                else -> {
                    val fileList = grouped[selectedRepo]?.get(selectedType) ?: emptyList()
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(fileList) { record ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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

    // Delete single file confirmation
    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("Delete Download") },
            text = { Text("Remove '${record.fileName}' from your device?") },
            confirmButton = {
                TextButton(onClick = {
                    deleteRecord(record)
                    recordToDelete = null
                    // Pop back if the type folder is now empty
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

    // Clear all confirmation
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Downloads") },
            text = { Text("This will remove all ${downloads.size} downloaded file records and delete the files from your device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    clearAll()
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
