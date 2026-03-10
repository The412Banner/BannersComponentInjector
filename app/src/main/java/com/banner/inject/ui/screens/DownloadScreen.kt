package com.banner.inject.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.banner.inject.data.RemoteSourceRepository
import com.banner.inject.model.MainTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SortOrder { NEWEST_FIRST, OLDEST_FIRST, NAME_ASC, NAME_DESC }

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
    var sourceToEdit by remember { mutableStateOf<RemoteSourceRepository.RemoteSource?>(null) }
    var sourceMenuExpanded by remember { mutableStateOf<RemoteSourceRepository.RemoteSource?>(null) }
    // Force recomposition when sources change
    var sources by remember { mutableStateOf(repo.getAllSources()) }
    var sortOrder by remember { mutableStateOf(SortOrder.NEWEST_FIRST) }
    var showSortMenu by remember { mutableStateOf(false) }
    var dynamicTypes by remember { mutableStateOf<List<String>?>(null) }
    var isLoadingTypes by remember { mutableStateOf(false) }
    var batchMode by remember { mutableStateOf(false) }
    var batchSelected by remember { mutableStateOf(emptySet<String>()) } // set of downloadUrls
    var lastFailedItem by remember { mutableStateOf<RemoteSourceRepository.RemoteItem?>(null) }
    var downloadedSet: Set<String> by remember { mutableStateOf(emptySet()) }
    // (item, componentType) for the detail sheet; null when closed
    var detailTarget by remember { mutableStateOf<Pair<RemoteSourceRepository.RemoteItem, String>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchCaching by remember { mutableStateOf(false) }

    val componentTypes = listOf("dxvk", "vkd3d", "box64", "fexcore", "wined3d", RemoteSourceRepository.GPU_DRIVER_TYPE, "drivers", "wine", "proton")

    // Auto-fetch folder names for GITHUB_REPO_CONTENTS sources when one is selected
    LaunchedEffect(selectedSource) {
        val source = selectedSource
        if (source != null && (source.format == RemoteSourceRepository.SourceFormat.GITHUB_REPO_CONTENTS || source.format == RemoteSourceRepository.SourceFormat.RANKING_EMULATORS_JSON)) {
            dynamicTypes = null
            isLoadingTypes = true
            try { dynamicTypes = repo.discoverTypes(source) } catch (_: Exception) { dynamicTypes = emptyList() }
            isLoadingTypes = false
        } else {
            dynamicTypes = null
            isLoadingTypes = false
        }
    }

    // Kick off background cache refresh when user starts typing so results populate quickly
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty() && !RemoteSourceRepository.hasCache()) {
            isSearchCaching = true
            try { repo.refreshAllCache(sources, componentTypes) } catch (_: Exception) {}
            isSearchCaching = false
        }
    }

    BackHandler(enabled = searchQuery.isNotEmpty() || (selectedSource != null && !isDownloading)) {
        if (searchQuery.isNotEmpty()) {
            searchQuery = ""
            return@BackHandler
        }
        if (batchMode) {
            batchMode = false
            batchSelected = emptySet()
            return@BackHandler
        }
        fetchJob?.cancel()
        fetchJob = null
        items = null
        isLoading = false
        errorMessage = null
        if (selectedType != null) {
            batchMode = false
            batchSelected = emptySet()
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
            // Hoist these for use in both header batch controls and file list
            val capturedSource = selectedSource?.name ?: ""
            val capturedType = selectedType ?: ""

            // Always-visible search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search all repositories...") },
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
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            if (searchQuery.isNotEmpty()) {
                SearchContent(
                    query = searchQuery,
                    isCaching = isSearchCaching,
                    onShowDetail = { item, componentType -> detailTarget = Pair(item, componentType) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {

            // Navigation Header
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                if (selectedSource != null && !isDownloading) {
                    IconButton(
                        onClick = {
                            if (batchMode) {
                                batchMode = false
                                batchSelected = emptySet()
                            } else if (selectedType != null) {
                                fetchJob?.cancel()
                                fetchJob = null
                                batchMode = false
                                batchSelected = emptySet()
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
                        batchMode -> "${batchSelected.size} selected"
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
                    if (batchMode) {
                        // Batch mode: select all + download button
                        val currentItemsList = items ?: emptyList()
                        IconButton(
                            onClick = {
                                batchSelected = if (batchSelected.size == currentItemsList.size)
                                    emptySet()
                                else
                                    currentItemsList.map { it.downloadUrl }.toSet()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(4.dp))
                        if (batchSelected.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    val toDownload = (items ?: emptyList()).filter { it.downloadUrl in batchSelected }
                                    batchMode = false
                                    batchSelected = emptySet()
                                    scope.launch {
                                        isDownloading = true
                                        var successCount = 0
                                        for ((index, item) in toDownload.withIndex()) {
                                            val itemFileName = item.downloadUrl.substringAfterLast("/").substringBefore("?")
                                                .ifEmpty { "${item.displayName.replace(" ", "_")}.zip" }
                                            try {
                                                downloadProgress = "Downloading ${index + 1} / ${toDownload.size}: ${item.displayName}"
                                                val file = repo.downloadToTemp(item.downloadUrl) { progress ->
                                                    downloadProgress = "${index + 1}/${toDownload.size}: $progress"
                                                }
                                                val (uriString, fileSize) = saveToDownloads(
                                                    context, file, itemFileName, capturedSource, capturedType
                                                )
                                                repo.recordDownload(capturedSource, capturedType, itemFileName, fileSize, uriString)
                                                downloadedSet = downloadedSet + itemFileName
                                                successCount++
                                            } catch (e: Exception) {
                                                errorMessage = "Batch download stopped at ${item.displayName}: ${e.message}"
                                                isDownloading = false
                                                return@launch
                                            }
                                        }
                                        isDownloading = false
                                        snackbarHostState.showSnackbar("Downloaded $successCount file${if (successCount != 1) "s" else ""}")
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Download ${batchSelected.size}", fontSize = 13.sp)
                            }
                        }
                    } else {
                        // Normal mode: sort + select buttons
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
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { batchMode = true; batchSelected = emptySet() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Checklist, contentDescription = "Batch select", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
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
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        if (lastFailedItem != null) {
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val item = lastFailedItem ?: return@Button
                                    val fileName = item.downloadUrl.substringAfterLast("/").substringBefore("?")
                                        .ifEmpty { "${item.displayName.replace(" ", "_")}.zip" }
                                    errorMessage = null
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
                                            lastFailedItem = null
                                            snackbarHostState.showSnackbar("Saved $fileName to Downloads")
                                        } catch (e: Exception) {
                                            errorMessage = "Download failed: ${e.message}"
                                        } finally {
                                            isDownloading = false
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry Download")
                            }
                        }
                    }
                }
                selectedSource == null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(sources) { index, source ->
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
                                                text = { Text("Move Up") },
                                                leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, null) },
                                                enabled = index > 0,
                                                onClick = {
                                                    sourceMenuExpanded = null
                                                    val newList = sources.toMutableList()
                                                    newList.add(index - 1, newList.removeAt(index))
                                                    repo.saveSourceOrder(newList.map { it.name })
                                                    sources = repo.getAllSources()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Move Down") },
                                                leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) },
                                                enabled = index < sources.lastIndex,
                                                onClick = {
                                                    sourceMenuExpanded = null
                                                    val newList = sources.toMutableList()
                                                    newList.add(index + 1, newList.removeAt(index))
                                                    repo.saveSourceOrder(newList.map { it.name })
                                                    sources = repo.getAllSources()
                                                }
                                            )
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
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Restore Default Repositories")
                            }
                        }
                    }
                }
                selectedType == null -> {
                    if (isLoadingTypes) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp).weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(16.dp))
                            Text("Detecting types...", fontSize = 14.sp)
                        }
                    } else {
                    val baseTypes = when {
                        selectedSource!!.format == RemoteSourceRepository.SourceFormat.GITHUB_REPO_CONTENTS ||
                        selectedSource!!.format == RemoteSourceRepository.SourceFormat.RANKING_EMULATORS_JSON -> dynamicTypes ?: emptyList()
                        selectedSource!!.supportedTypes.isNotEmpty() -> selectedSource!!.supportedTypes
                        else -> componentTypes
                    }
                    val typesToShow = if (selectedSource!!.releaseTags.isNotEmpty())
                        baseTypes + selectedSource!!.releaseTags
                    else
                        baseTypes
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
                    } // end else (not isLoadingTypes)
                }
                else -> {
                    val currentItems = items ?: return@Column
                    val sortedItems = remember(currentItems, sortOrder) {
                        when (sortOrder) {
                            SortOrder.NEWEST_FIRST -> currentItems.sortedByDescending { it.publishedAt ?: "" }
                            SortOrder.OLDEST_FIRST -> currentItems.sortedBy { it.publishedAt ?: "9999-99-99" }
                            SortOrder.NAME_ASC -> currentItems.sortedBy { it.displayName }
                            SortOrder.NAME_DESC -> currentItems.sortedByDescending { it.displayName }
                        }
                    }
                    // Initialize downloadedSet when currentItems changes
                    LaunchedEffect(currentItems) {
                        downloadedSet = currentItems.mapNotNullTo(mutableSetOf()) { item ->
                            val fileName = item.downloadUrl.substringAfterLast("/").substringBefore("?")
                                .ifEmpty { "${item.displayName.replace(" ", "_")}.zip" }
                            if (RemoteSourceRepository.isDownloaded(capturedSource, capturedType, fileName)) fileName else null
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sortedItems) { item ->
                            val fileName = item.downloadUrl.substringAfterLast("/").substringBefore("?")
                                .ifEmpty { "${item.displayName.replace(" ", "_")}.zip" }
                            val alreadyDownloaded = fileName in downloadedSet
                            val isSelected = item.downloadUrl in batchSelected
                            val sizeText = item.sizeBytes?.let { " · ${RemoteSourceRepository.formatFileSize(it)}" } ?: ""
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (batchMode) {
                                        batchSelected = if (isSelected)
                                            batchSelected - item.downloadUrl
                                        else
                                            batchSelected + item.downloadUrl
                                    } else {
                                        detailTarget = Pair(item, capturedType)
                                    }
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected && batchMode)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (batchMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = {
                                                batchSelected = if (isSelected)
                                                    batchSelected - item.downloadUrl
                                                else
                                                    batchSelected + item.downloadUrl
                                            },
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else {
                                        Icon(
                                            if (alreadyDownloaded) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            tint = if (alreadyDownloaded) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.displayName,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        val subtitle = when {
                                            batchMode -> if (item.publishedAt != null) "${item.publishedAt}$sizeText" else sizeText.trimStart(' ', '·').ifEmpty { "Tap to select" }
                                            alreadyDownloaded -> if (item.publishedAt != null) "Already downloaded · ${item.publishedAt}$sizeText" else "Already downloaded$sizeText"
                                            item.publishedAt != null -> "Uploaded ${item.publishedAt}$sizeText · Tap to download"
                                            sizeText.isNotEmpty() -> "$sizeText · Tap to download"
                                            else -> "Tap to download to device"
                                        }
                                        Text(
                                            text = subtitle,
                                            fontSize = 12.sp,
                                            color = when {
                                                batchMode -> MaterialTheme.colorScheme.onSurfaceVariant
                                                alreadyDownloaded -> MaterialTheme.colorScheme.tertiary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            } // end else (searchQuery empty)
        }
    }

    // Detail sheet — shown when user taps a file item
    detailTarget?.let { (detailItem, detailType) ->
        val detailFileName = detailItem.downloadUrl.substringAfterLast("/").substringBefore("?")
            .ifEmpty { "${detailItem.displayName.replace(" ", "_")}.zip" }
        val alreadyDownloaded = detailFileName in downloadedSet
        ModalBottomSheet(
            onDismissRequest = { if (!isDownloading) detailTarget = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Icon + title
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    Icon(
                        if (alreadyDownloaded) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = if (alreadyDownloaded) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        detailItem.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Source + type chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            detailItem.sourceName,
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
                            detailType.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                // Date + size
                val metaLine = buildString {
                    if (detailItem.publishedAt != null) append("Published ${detailItem.publishedAt}")
                    if (detailItem.publishedAt != null && detailItem.sizeBytes != null) append("  ·  ")
                    if (detailItem.sizeBytes != null) append(RemoteSourceRepository.formatFileSize(detailItem.sizeBytes))
                }
                if (metaLine.isNotEmpty()) {
                    Text(
                        metaLine,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                // Description
                if (detailItem.description != null) {
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
                        LinkedText(
                            text = detailItem.description,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))
                // Download button or progress
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
                                    val file = repo.downloadToTemp(detailItem.downloadUrl) { progress ->
                                        downloadProgress = progress
                                    }
                                    val (uriString, fileSize) = saveToDownloads(
                                        context, file, detailFileName, detailItem.sourceName, detailType
                                    )
                                    repo.recordDownload(detailItem.sourceName, detailType, detailFileName, fileSize, uriString)
                                    downloadedSet = downloadedSet + detailFileName
                                    lastFailedItem = null
                                    detailTarget = null
                                    snackbarHostState.showSnackbar("Saved $detailFileName to Downloads")
                                } catch (e: Exception) {
                                    lastFailedItem = detailItem
                                    errorMessage = "Download failed: ${e.message}"
                                } finally {
                                    isDownloading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !alreadyDownloaded
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (alreadyDownloaded) "Already Downloaded" else "Download to Device")
                    }
                }
            }
        }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchContent(
    query: String,
    isCaching: Boolean,
    onShowDetail: (RemoteSourceRepository.RemoteItem, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val results = remember(query) { RemoteSourceRepository.searchCache(query) }

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        when {
            query.isBlank() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (isCaching) "Loading repositories into cache..." else "Type to search across all repositories",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (isCaching) {
                        Spacer(Modifier.height(12.dp))
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
            results.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No results for \"$query\"",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (!RemoteSourceRepository.hasCache() || isCaching) {
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
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results) { result ->
                        val item = result.item
                        val fileName = item.downloadUrl.substringAfterLast("/").substringBefore("?")
                            .ifEmpty { "${item.displayName.replace(" ", "_")}.zip" }
                        val alreadyDownloaded = RemoteSourceRepository.isDownloaded(result.sourceName, result.componentType, fileName)
                        val sizeText = item.sizeBytes?.let { " · ${RemoteSourceRepository.formatFileSize(it)}" } ?: ""
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onShowDetail(item, result.componentType)
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
                                        text = "${result.sourceName} · ${result.componentType.uppercase()}$sizeText",
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

    // Check for custom downloads location
    val customUriStr = context.getSharedPreferences("bci_settings", Context.MODE_PRIVATE)
        .getString("custom_downloads_uri", null)
    if (customUriStr != null) {
        try {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(customUriStr))
                ?: throw Exception("Invalid custom downloads folder")
            val repoDir = root.findFile(safeSource) ?: root.createDirectory(safeSource)
                ?: throw Exception("Could not create repo folder")
            val typeDir = repoDir.findFile(safeType) ?: repoDir.createDirectory(safeType)
                ?: throw Exception("Could not create type folder")
            typeDir.findFile(fileName)?.delete()
            val fileDoc = typeDir.createFile("application/octet-stream", fileName)
                ?: throw Exception("Could not create file in custom folder")
            context.contentResolver.openOutputStream(fileDoc.uri)?.use { output ->
                tempFile.inputStream().use { input -> input.copyTo(output) }
            }
            tempFile.delete()
            return@withContext Pair(fileDoc.uri.toString(), fileSize)
        } catch (_: Exception) {
            // Fall through to default MediaStore on failure
        }
    }

    // Default: MediaStore
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

/**
 * Renders [text] with any embedded URLs highlighted and tappable.
 * URLs are detected via [android.util.Patterns.WEB_URL] and colored/underlined.
 */
@Composable
fun LinkedText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    lineHeight: TextUnit = 18.sp
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) {
        buildAnnotatedString {
            var lastEnd = 0
            val matcher = android.util.Patterns.WEB_URL.matcher(text)
            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                append(text.substring(lastEnd, start))
                pushStringAnnotation("URL", matcher.group())
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(text.substring(start, end))
                }
                pop()
                lastEnd = end
            }
            append(text.substring(lastEnd))
        }
    }
    ClickableText(
        text = annotated,
        style = TextStyle(
            fontSize = fontSize,
            color = color,
            lineHeight = lineHeight,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Default
        ),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { ann ->
                val url = ann.item.let { if (it.startsWith("http")) it else "https://$it" }
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        }
    )
}

