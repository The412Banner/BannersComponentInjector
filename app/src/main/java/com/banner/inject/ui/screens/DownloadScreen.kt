package com.banner.inject.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banner.inject.data.RemoteSourceRepository
import com.banner.inject.model.MainTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    repo: RemoteSourceRepository,
    onShowBackupManager: () -> Unit,
    onShowSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedSource by remember { mutableStateOf<RemoteSourceRepository.RemoteSource?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    
    var items by remember { mutableStateOf<List<RemoteSourceRepository.RemoteItem>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf("") }
    var fetchJob by remember { mutableStateOf<Job?>(null) }
    
    var isRefreshing by remember { mutableStateOf(false) }
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var sourceToDelete by remember { mutableStateOf<RemoteSourceRepository.RemoteSource?>(null) }
    var sourceMenuExpanded by remember { mutableStateOf<RemoteSourceRepository.RemoteSource?>(null) }
    // Force recomposition when sources change
    var sources by remember { mutableStateOf(repo.getAllSources()) }

    val componentTypes = listOf("dxvk", "vkd3d", "box64", "fexcore", "wined3d", "turnip", "adreno", "drivers")

    BackHandler(enabled = selectedSource != null && !isDownloading) {
        fetchJob?.cancel()
        fetchJob = null
        items = null
        isLoading = false
        errorMessage = null
        if (selectedType != null) {
            selectedType = null
        } else if (selectedSource != null) {
            selectedSource = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text("Download Components", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    },
                    actions = {
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
            // Navigation Header
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                if (selectedSource != null && !isDownloading) {
                    IconButton(
                        onClick = {
                            if (selectedType != null) {
                                fetchJob?.cancel()
                                fetchJob = null
                                selectedType = null
                                items = null
                                isLoading = false
                                errorMessage = null
                            } else if (selectedSource != null) {
                                selectedSource = null
                                errorMessage = null
                            }
                        },
                        modifier = Modifier.size(28.dp).padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
                Text(
                    when {
                        selectedType != null -> "${selectedType!!.uppercase()} from ${selectedSource?.name}"
                        selectedSource != null -> "Select Component Type"
                        else -> "Select Online Repository"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                
                if (selectedSource == null && !isDownloading && !isLoading) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(
                            onClick = {
                                isRefreshing = true
                                scope.launch {
                                    repo.refreshAllCache(sources, componentTypes)
                                    isRefreshing = false
                                    snackbarHostState.showSnackbar("All repositories refreshed and cached")
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh All", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { showAddRepoDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Repository", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            when {
                isDownloading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp).weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(downloadProgress, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp).weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text("Fetching list...", fontSize = 14.sp)
                    }
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                    }
                }
                selectedSource == null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sources) { source ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { selectedSource = source },
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
                                    Text(
                                        text = source.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box {
                                        IconButton(
                                            onClick = { sourceMenuExpanded = source },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                        }
                                        DropdownMenu(
                                            expanded = sourceMenuExpanded == source,
                                            onDismissRequest = { sourceMenuExpanded = null }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Open in Browser") },
                                                leadingIcon = { Icon(Icons.Default.OpenInBrowser, null) },
                                                onClick = {
                                                    sourceMenuExpanded = null
                                                    val browseUrl = repo.getBrowseUrl(source)
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(browseUrl)))
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Remove Repository", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    sourceMenuExpanded = null
                                                    sourceToDelete = source
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        item {
                            TextButton(
                                onClick = {
                                    repo.restoreDefaultSources()
                                    sources = repo.getAllSources()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Restore Default Repositories")
                            }
                        }
                    }
                }
                selectedType == null -> {
                    val typesToShow = if (selectedSource!!.supportedTypes.isNotEmpty()) selectedSource!!.supportedTypes else componentTypes
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(typesToShow) { type ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    fetchJob?.cancel()
                                    val source = selectedSource ?: return@clickable
                                    selectedType = type
                                    items = null
                                    isLoading = true
                                    errorMessage = null
                                    fetchJob = scope.launch {
                                        try {
                                            val fetched = repo.fetchFromSource(source, type)
                                            items = fetched
                                            if (fetched.isEmpty()) {
                                                errorMessage = "No components found for $type in this repository."
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Failed to fetch: ${e.message}"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
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
                                    Text(
                                        text = type.uppercase(),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    val currentItems = items ?: return@Column
                    val capturedSource = selectedSource?.name ?: "Unknown"
                    val capturedType = selectedType ?: "misc"
                    // Track downloaded state as recomposable set
                    var downloadedSet: Set<String> by remember(currentItems) {
                        mutableStateOf(
                            currentItems.mapNotNullTo(mutableSetOf()) { item ->
                                val fileName = item.downloadUrl.substringAfterLast("/").substringBefore("?")
                                    .ifEmpty { "${item.displayName.replace(" ", "_")}.zip" }
                                if (RemoteSourceRepository.isDownloaded(capturedSource, capturedType, fileName)) fileName else null
                            }
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(currentItems) { item ->
                            val fileName = item.downloadUrl.substringAfterLast("/").substringBefore("?")
                                .ifEmpty { "${item.displayName.replace(" ", "_")}.zip" }
                            val alreadyDownloaded = fileName in downloadedSet
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    scope.launch {
                                        isDownloading = true
                                        try {
                                            val file = repo.downloadToTemp(item.downloadUrl) { progress ->
                                                downloadProgress = progress
                                            }
                                            val (uriString, fileSize) = saveToDownloads(
                                                context, file, fileName, capturedSource, capturedType
                                            )
                                            repo.recordDownload(capturedSource, capturedType, fileName, fileSize, uriString)
                                            downloadedSet = downloadedSet + fileName
                                            snackbarHostState.showSnackbar("Saved $fileName to Downloads")
                                        } catch (e: Exception) {
                                            errorMessage = "Download failed: ${e.message}"
                                        } finally {
                                            isDownloading = false
                                        }
                                    }
                                },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (alreadyDownloaded) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = if (alreadyDownloaded) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.displayName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (alreadyDownloaded) "Already downloaded" else "Tap to download to device",
                                            fontSize = 12.sp,
                                            color = if (alreadyDownloaded) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
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
    
    if (showAddRepoDialog) {
        AddRepoDialog(
            onDismiss = { showAddRepoDialog = false },
            onAdd = { customSource ->
                repo.addCustomSource(customSource)
                sources = repo.getAllSources()
                showAddRepoDialog = false
            }
        )
    }

    sourceToDelete?.let { source ->
        AlertDialog(
            onDismissRequest = { sourceToDelete = null },
            title = { Text("Remove Repository") },
            text = { Text("Are you sure you want to remove '${source.name}' from your list?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        repo.removeSource(source)
                        sources = repo.getAllSources()
                        sourceToDelete = null
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { sourceToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private suspend fun saveToDownloads(
    context: Context,
    tempFile: File,
    fileName: String,
    sourceName: String,
    componentType: String
): Pair<String?, Long> = withContext(Dispatchers.IO) {
    val fileSize = tempFile.length()
    val safeSource = RemoteSourceRepository.sanitizeFolderName(sourceName)
    val safeType = RemoteSourceRepository.sanitizeFolderName(componentType)
    val relPath = "${Environment.DIRECTORY_DOWNLOADS}/BannersComponentInjector/$safeSource/$safeType/"
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.RELATIVE_PATH, relPath)
        put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
    }
    try {
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                tempFile.inputStream().use { input -> input.copyTo(output) }
            }
            tempFile.delete()
            Pair(uri.toString(), fileSize)
        } else {
            throw Exception("Could not insert into MediaStore")
        }
    } catch (e: Exception) {
        val fallbackDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "BannersComponentInjector/$safeSource/$safeType"
        )
        fallbackDir.mkdirs()
        val destFile = File(fallbackDir, fileName)
        tempFile.copyTo(destFile, overwrite = true)
        tempFile.delete()
        Pair(null, fileSize)
    }
}
