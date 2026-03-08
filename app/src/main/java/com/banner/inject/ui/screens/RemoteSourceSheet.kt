package com.banner.inject.ui.screens

import android.net.Uri
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
    
    // Fixed list of common component types users can select when source allows anything
    val componentTypes = listOf("dxvk", "vkd3d", "box64", "fexcore", "wined3d")

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
                    color = MaterialTheme.colorScheme.onSurface
                )
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
                        items(repo.defaultSources) { source ->
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
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
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
                items != null -> {
                    // Step 3: List Files
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items!!) { item ->
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
                                            text = "Tap to download and replace",
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
}