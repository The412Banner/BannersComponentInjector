package com.banner.inject.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banner.inject.data.RemoteSourceRepository
import com.banner.inject.model.ComponentEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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

    val context = LocalContext.current
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var sourceToDelete by remember { mutableStateOf<RemoteSourceRepository.RemoteSource?>(null) }
    var sourceMenuExpanded by remember { mutableStateOf<RemoteSourceRepository.RemoteSource?>(null) }
    var sourceToEdit by remember { mutableStateOf<RemoteSourceRepository.RemoteSource?>(null) }
    var detailItem by remember { mutableStateOf<Pair<RemoteSourceRepository.RemoteItem, String>?>(null) }
    var sources by remember { mutableStateOf(repo.getAllSources()) }
    var sortOrder by remember { mutableStateOf(SortOrder.NEWEST_FIRST) }
    var showSortMenu by remember { mutableStateOf(false) }
    var dynamicTypes by remember { mutableStateOf<List<String>?>(null) }
    var isLoadingTypes by remember { mutableStateOf(false) }

    // Cross-repo search state (step 1 — before any repo is selected)
    var searchQuery by remember { mutableStateOf("") }
    var isSearchCaching by remember { mutableStateOf(false) }

    // Fixed list of common component types users can select when source allows anything
    val componentTypes = listOf("dxvk", "vkd3d", "box64", "fexcore", "wined3d", "turnip", "adreno", "drivers", "wine", "proton")

    // Auto-fetch folder names for GITHUB_REPO_CONTENTS sources when one is selected
    LaunchedEffect(selectedSource) {
        val source = selectedSource
        if (source != null && source.format == RemoteSourceRepository.SourceFormat.GITHUB_REPO_CONTENTS) {
            dynamicTypes = null
            isLoadingTypes = true
            try { dynamicTypes = repo.discoverTypes(source) } catch (_: Exception) { dynamicTypes = emptyList() }
            isLoadingTypes = false
        } else {
            dynamicTypes = null
            isLoadingTypes = false
        }
    }

    // Background cache-fill when user starts typing a search query
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty() && !RemoteSourceRepository.hasCache()) {
            isSearchCaching = true
            try { repo.refreshAllCache(sources, componentTypes) } catch (_: Exception) {}
            isSearchCaching = false
        }
    }

    BackHandler(enabled = (searchQuery.isNotEmpty() || selectedSource != null) && !isDownloading) {
        when {
            searchQuery.isNotEmpty() -> searchQuery = ""
            selectedType != null -> {
                fetchJob?.cancel()
                fetchJob = null
                selectedType = null
                items = null
                isLoading = false
                errorMessage = null
            }
            selectedSource != null -> {
                selectedSource = null
                errorMessage = null
            }
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
            Spacer(Modifier.height(12.dp))

            // Cross-repo search field — only shown at step 1 (no repo selected yet)
            if (selectedSource == null && !isDownloading) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search across all repositories...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }

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
                selectedSource == null && searchQuery.isNotEmpty() -> {
                    // Cross-repo search results
                    val results = remember(searchQuery) { RemoteSourceRepository.searchCache(searchQuery) }
                    when {
                        isSearchCaching && results.isEmpty() -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(12.dp))
                                Text("Loading repositories into cache...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            }
                        }
                        results.isEmpty() -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "No results for \"$searchQuery\"",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                if (!RemoteSourceRepository.hasCache() || isSearchCaching) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("Still loading — try again in a moment", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        else -> {
                            Text(
                                "${results.size} result${if (results.size != 1) "s" else ""}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(results) { result ->
                                    val item = result.item
                                    val sizeText = item.sizeBytes?.let { " · ${RemoteSourceRepository.formatFileSize(it)}" } ?: ""
                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            detailItem = Pair(item, result.componentType)
                                        },
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CloudDownload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                            Spacer(Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                                Text(
                                                    "${result.sourceName} · ${result.componentType.uppercase()}$sizeText",
                                                    fontSize = 11.sp,
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
                                                text = { Text("Edit Repository") },
                                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                                onClick = {
                                                    sourceMenuExpanded = null
                                                    sourceToEdit = source
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
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text("Restore Default Repositories")
                            }
                        }
                    }
                }
                selectedType == null -> {
                    // Step 2: List Component Types
                    if (isLoadingTypes) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(16.dp))
                            Text("Detecting types...", fontSize = 14.sp)
                        }
                    } else {
                    val typesToShow = when {
                        selectedSource!!.format == RemoteSourceRepository.SourceFormat.GITHUB_REPO_CONTENTS -> dynamicTypes ?: emptyList()
                        selectedSource!!.supportedTypes.isNotEmpty() -> selectedSource!!.supportedTypes
                        else -> componentTypes
                    }
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
                    } // end else (not isLoadingTypes)
                }
                else -> {
                    // Step 3: List Files — capture items in a local val to avoid Compose
                    // snapshot race where items becomes null between the when-check and
                    // LazyColumn's lazy content evaluation (causes NPE at items!!)
                    val currentItems = items ?: return@Column
                    val capturedType = selectedType ?: return@Column
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
                                    detailItem = Pair(item, capturedType)
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
                                        val sizeStr = item.sizeBytes?.let { " · ${RemoteSourceRepository.formatFileSize(it)}" } ?: ""
                                        Text(
                                            text = if (item.publishedAt != null) "Published ${item.publishedAt}$sizeStr" else "Tap for details$sizeStr",
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

    detailItem?.let { (dItem, dType) ->
        ModalBottomSheet(
            onDismissRequest = { if (!isDownloading) detailItem = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        dItem.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            dItem.sourceName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            dType.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                val metaLine = buildString {
                    if (dItem.publishedAt != null) append("Published ${dItem.publishedAt}")
                    if (dItem.publishedAt != null && dItem.sizeBytes != null) append("  ·  ")
                    if (dItem.sizeBytes != null) append(RemoteSourceRepository.formatFileSize(dItem.sizeBytes))
                }
                if (metaLine.isNotEmpty()) {
                    Text(
                        metaLine,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                if (dItem.description != null) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                    Text(
                        "Release Notes",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            dItem.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))
                if (isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(downloadProgress, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                isDownloading = true
                                try {
                                    val file = repo.downloadToTemp(dItem.downloadUrl) { progress ->
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Download & Replace")
                    }
                }
            }
        }
    }

    sourceToEdit?.let { source ->
        EditRepoDialog(
            source = source,
            repo = repo,
            onDismiss = { sourceToEdit = null },
            onSave = { edited ->
                repo.editSource(source, edited)
                sources = repo.getAllSources()
                sourceToEdit = null
            }
        )
    }

    if (showAddRepoDialog) {
        AddRepoDialog(
            repo = repo,
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
