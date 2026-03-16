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
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banner.inject.data.BackupManager
import com.banner.inject.data.RemoteSourceRepository
import com.banner.inject.model.ComponentEntry
import com.banner.inject.model.GameHubApp
import com.banner.inject.model.MainTab
import com.banner.inject.model.OpState
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
    onShowSettings: () -> Unit,
    apps: List<GameHubApp> = emptyList(),
    onGetComponentsForApp: suspend (GameHubApp) -> List<ComponentEntry> = { emptyList() },
    onInjectInto: (ComponentEntry, Uri) -> Unit = { _, _ -> },
    opState: OpState = OpState.Idle,
    onClearOpState: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isVerifying by remember { mutableStateOf(false) }

    val pullRefreshState = rememberPullToRefreshState()

    // Downloads navigation state
    var downloads by remember { mutableStateOf(repo.getAllDownloads()) }

    // Auto-refresh every time this tab is opened
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { repo.pruneStaleDownloadRecords(context) }
        downloads = repo.getAllDownloads()
    }

    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            val removed = withContext(Dispatchers.IO) { repo.pruneStaleDownloadRecords(context) }
            downloads = repo.getAllDownloads()
            pullRefreshState.endRefresh()
            if (removed > 0) snackbarHostState.showSnackbar(
                "Removed $removed stale record${if (removed != 1) "s" else ""}"
            )
        }
    }
    var selectedRepo by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var recordToDelete by remember { mutableStateOf<RemoteSourceRepository.DownloadedFile?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var pendingInjectFile by remember { mutableStateOf<RemoteSourceRepository.DownloadedFile?>(null) }
    var hadRecentInject by remember { mutableStateOf(false) }

    // Show inject result when ViewModel finishes
    LaunchedEffect(opState) {
        if (!hadRecentInject) return@LaunchedEffect
        when (opState) {
            is OpState.Done -> {
                snackbarHostState.showSnackbar(opState.message)
                onClearOpState()
                hadRecentInject = false
            }
            is OpState.Error -> {
                snackbarHostState.showSnackbar("Inject failed: ${opState.message}")
                onClearOpState()
                hadRecentInject = false
            }
            else -> {}
        }
    }

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
                        // Verify + Clear All only make sense for the downloads root
                        if (!showingBackups && selectedRepo == null) {
                            if (isVerifying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp).padding(end = 4.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                IconButton(onClick = {
                                    isVerifying = true
                                    scope.launch {
                                        val removed = withContext(Dispatchers.IO) {
                                            repo.pruneStaleDownloadRecords(context)
                                        }
                                        downloads = repo.getAllDownloads()
                                        isVerifying = false
                                        snackbarHostState.showSnackbar(
                                            if (removed == 0) "All download records are up to date"
                                            else "Removed $removed stale record${if (removed != 1) "s" else ""}"
                                        )
                                    }
                                }) {
                                    Icon(Icons.Default.CloudSync, contentDescription = "Verify Downloads")
                                }
                            }
                            if (downloads.isNotEmpty()) {
                                IconButton(onClick = { showClearAllDialog = true }) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All Downloads")
                                }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
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
                                    // Inject into GameHub
                                    IconButton(
                                        onClick = { pendingInjectFile = record },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.SystemUpdateAlt,
                                            contentDescription = "Inject into GameHub",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
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
        PullToRefreshContainer(
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
        } // end Box
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

    // Inject from My Downloads picker
    pendingInjectFile?.let { record ->
        InjectPickerSheet(
            record = record,
            apps = apps,
            onGetComponentsForApp = onGetComponentsForApp,
            onDismiss = { pendingInjectFile = null },
            onConfirm = { component, uri ->
                onInjectInto(component, uri)
                pendingInjectFile = null
                hadRecentInject = true
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InjectPickerSheet(
    record: RemoteSourceRepository.DownloadedFile,
    apps: List<GameHubApp>,
    onGetComponentsForApp: suspend (GameHubApp) -> List<ComponentEntry>,
    onDismiss: () -> Unit,
    onConfirm: (ComponentEntry, Uri) -> Unit
) {
    val context = LocalContext.current

    // step: "app" | "loading" | "component" | "error"
    var step by remember { mutableStateOf("app") }
    var selectedApp by remember { mutableStateOf<GameHubApp?>(null) }
    var componentList by remember { mutableStateOf<List<ComponentEntry>>(emptyList()) }

    // Auto-skip app selection if only one accessible app
    val accessibleApps = remember(apps) { apps.filter { it.hasAccess } }

    LaunchedEffect(selectedApp) {
        val app = selectedApp ?: return@LaunchedEffect
        step = "loading"
        val comps = onGetComponentsForApp(app)
        componentList = comps
        step = if (comps.isEmpty()) "error" else "component"
    }

    val resolvedUri = remember(record) {
        if (record.uriString != null) Uri.parse(record.uriString) else null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step == "component") {
                    IconButton(
                        onClick = { step = "app"; selectedApp = null; componentList = emptyList() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    Icons.Default.SystemUpdateAlt, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        when (step) {
                            "component" -> "Select Component"
                            else -> "Inject into GameHub"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        record.fileName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            when (step) {
                "app" -> {
                    if (accessibleApps.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.AppBlocking, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No accessible apps",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Grant folder access to a GameHub app in the Inject tab first.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Text(
                            "Select which app to inject into:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(accessibleApps) { app ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { selectedApp = app },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.PhoneAndroid, null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            app.known.displayName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            Icons.Default.ArrowForward, null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                "loading" -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Loading components…",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                "component" -> {
                    Text(
                        "Select which component to replace:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(componentList) { component ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    val uri = resolvedUri ?: resolveManagerDownloadUri(context, record)
                                    if (uri != null) onConfirm(component, uri)
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Layers, null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            component.folderName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            "${component.fileCount} files · ${component.formattedSize}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ArrowForward, null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                "error" -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.FolderOff, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No components found",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "The selected app's components folder is empty or unreadable.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/** Resolves a [RemoteSourceRepository.DownloadedFile] to a usable content [Uri]. */
private fun resolveManagerDownloadUri(
    context: android.content.Context,
    record: RemoteSourceRepository.DownloadedFile
): Uri? {
    if (record.uriString != null) return Uri.parse(record.uriString)
    val proj = arrayOf(android.provider.MediaStore.Downloads._ID)
    val sel = "${android.provider.MediaStore.Downloads.DISPLAY_NAME} = ?"
    context.contentResolver.query(
        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, proj, sel, arrayOf(record.fileName), null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val id = cursor.getLong(0)
            return android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
            )
        }
    }
    return null
}

private fun formatManagerSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes < 1_024L -> "$bytes B"
    bytes < 1_048_576L -> "${bytes / 1_024L} KB"
    else -> "${"%.1f".format(bytes / 1_048_576f)} MB"
}

private fun formatManagerDate(epochMs: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMs))
