package com.banner.inject.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banner.inject.data.RemoteSourceRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRepoDialog(
    source: RemoteSourceRepository.RemoteSource,
    repo: RemoteSourceRepository,
    onDismiss: () -> Unit,
    onSave: (RemoteSourceRepository.RemoteSource) -> Unit
) {
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(source.name) }
    var url by remember { mutableStateOf(source.url) }
    var discoveredTypes by remember { mutableStateOf<List<String>>(emptyList()) }
    var isDetecting by remember { mutableStateOf(false) }
    var detectError by remember { mutableStateOf<String?>(null) }

    // selectedTypes: pre-check what the source currently has configured
    var selectedTypes by remember { mutableStateOf(source.supportedTypes.toSet()) }

    // Union of current supportedTypes + anything discovered beyond that — current types always
    // appear first so existing config is never silently dropped, new ones append below
    val typesToShow by remember(discoveredTypes) {
        derivedStateOf {
            val combined = source.supportedTypes.toMutableList()
            discoveredTypes.forEach { if (it !in combined) combined.add(it) }
            combined
        }
    }

    fun detect(targetUrl: String) {
        isDetecting = true
        detectError = null
        scope.launch {
            try {
                val tempSource = source.copy(url = targetUrl.trim())
                val found = repo.discoverTypes(tempSource)
                discoveredTypes = found
                // If source was "all types" (empty list), pre-check everything discovered
                if (source.supportedTypes.isEmpty()) selectedTypes = found.toSet()
            } catch (e: Exception) {
                detectError = "Could not detect types: ${e.message}"
            }
            isDetecting = false
        }
    }

    // Auto-detect on first open
    LaunchedEffect(Unit) { detect(url) }

    AlertDialog(
        onDismissRequest = { if (!isDetecting) onDismiss() },
        title = { Text("Edit Repository", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Repository Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                // Types section header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Component Types",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (isDetecting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(
                            onClick = { detect(url) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Re-detect types",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (detectError != null) {
                    Text(detectError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                if (!isDetecting && typesToShow.isEmpty() && detectError == null) {
                    Text(
                        "No types detected. Tap ⟳ to retry after updating the URL.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (typesToShow.isNotEmpty()) {
                    Text(
                        "Select which categories this repository provides:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    typesToShow.forEach { type ->
                        val isNew = type !in source.supportedTypes
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTypes = if (type in selectedTypes)
                                        selectedTypes - type else selectedTypes + type
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = type in selectedTypes,
                                onCheckedChange = { checked ->
                                    selectedTypes = if (checked) selectedTypes + type else selectedTypes - type
                                }
                            )
                            Text(type.uppercase(), fontSize = 13.sp, modifier = Modifier.weight(1f))
                            if (isNew) {
                                Text(
                                    "new",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Select all / Deselect all shortcuts
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { selectedTypes = typesToShow.toSet() },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) { Text("Select All", fontSize = 12.sp) }
                        TextButton(
                            onClick = { selectedTypes = emptySet() },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) { Text("Deselect All", fontSize = 12.sp) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // If all shown types are selected (nothing excluded), save as empty list (= all types)
                    val finalTypes = if (typesToShow.isNotEmpty() && selectedTypes.containsAll(typesToShow))
                        emptyList()
                    else
                        typesToShow.filter { it in selectedTypes }
                    onSave(
                        source.copy(
                            name = name.trim(),
                            url = url.trim(),
                            supportedTypes = finalTypes
                        )
                    )
                },
                enabled = name.isNotBlank() && url.isNotBlank() && !isDetecting
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDetecting) { Text("Cancel") }
        }
    )
}
