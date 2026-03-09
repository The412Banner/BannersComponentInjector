package com.banner.inject.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banner.inject.data.RemoteSourceRepository
import com.banner.inject.model.ComponentEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteSourceSheet(
    component: ComponentEntry,
    repo: RemoteSourceRepository,
    onDismiss: () -> Unit,
    onDownloadAndReplace: (Uri) -> Unit
) {
    var selectedSource by remember { mutableStateOf<RemoteSourceRepository.RemoteSource?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    
    var items by remember { mutableStateOf<List<RemoteSourceRepository.RemoteItem>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    var fetchJob by remember { mutableStateOf<Job?>(null) }
    
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var sourceToDelete by remember { mutableStateOf<RemoteSourceRepository.RemoteSource?>(null) }
    var sources by remember { mutableStateOf(repo.getAllSources()) }
    var sortOrder by remember { mutableStateOf(SortOrder.NEWEST_FIRST) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    // Fixed list of common component types users can select when source allows anything
    val componentTypes = listOf("dxvk", "vkd3d", "box64", "fexcore", "wined3d", "turnip", "adreno", "drivers", "wine", "proton")

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

    ModalBottomSheet(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header with back button
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedSource != null) {
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
                
                if (selectedType != null && !isLoading && !isDownloading) {
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort", tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            listOf(
                                SortOrder.NEWEST_FIRST to "Newest First",
                                SortOrder.OLDEST_FIRST to "Oldest First",
                                SortOrder.NAME_ASC to "Name A\u2192Z",
                                SortOrder.NAME_DESC to "Name Z\u2192A"
                            ).forEach { (order, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { sortOrder = order; showSortMenu = false },
                                    trailingIcon = {
                                        if (sortOrder == order) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                )
                            }
                        }
                    }
                }
                if (selectedSource == null && !isDownloading && !isLoading) {
                    IconButton(
                        onClick = { showAddRepoDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Repository", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            when {
                isDownloading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(downloadProgress, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text("Fetching list...", fontSize = 14.sp)
                    }
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                    }
                }
                selectedSource == null -> {
                    // Step 1: List Sources
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sources) { source ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { selectedSource = source },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                    IconButton(
                                        onClick = { sourceToDelete = source },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
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
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text("Restore Default Repositories")
                            }
                        }
                    }
                }
                selectedType == null -> {
                    // Step 2: List Component Types
                    val typesToShow = if (selectedSource!!.supportedTypes.isNotEmpty()) selectedSource!!.supportedTypes else componentTypes
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
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
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    // Step 3: List Files — capture items in a local val to avoid Compose
                    // snapshot race where items becomes null between the when-check and
                    // LazyColumn's lazy content evaluation (causes NPE at items!!)
                    val currentItems = items ?: return@Column
                    val sortedItems = remember(currentItems, sortOrder) {
                        when (sortOrder) {
                            SortOrder.NEWEST_FIRST -> currentItems.sortedByDescending { it.publishedAt ?: "" }
                            SortOrder.OLDEST_FIRST -> currentItems.sortedBy { it.publishedAt ?: "9999-99-99" }
                            SortOrder.NAME_ASC -> currentItems.sortedBy { it.displayName }
                            SortOrder.NAME_DESC -> currentItems.sortedByDescending { it.displayName }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sortedItems) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    scope.launch {
                                        isDownloading = true
                                        try {
                                            val file = repo.downloadToTemp(item.downloadUrl) { progress ->
                                                downloadProgress = progress
                                            }
                                            onDownloadAndReplace(Uri.fromFile(file))
                                            onDismiss()
                                        } catch (e: Exception) {
                                            errorMessage = "Download failed: ${e.message}"
                                            isDownloading = false
                                        }
                                    }
                                },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = item.displayName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (item.publishedAt != null) "Uploaded ${item.publishedAt} · Tap to replace" else "Tap to download and replace",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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