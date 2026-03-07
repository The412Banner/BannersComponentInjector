package com.banner.inject.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banner.inject.model.ComponentEntry
import com.banner.inject.model.formatSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentDetailSheet(
    component: ComponentEntry,
    onDismiss: () -> Unit,
    onReplaceFiles: (List<Uri>) -> Unit,
    onReplaceFolder: (Uri) -> Unit,
    onRestore: () -> Unit,
    onDeleteBackup: () -> Unit
) {
    var showImportDialog by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showDeleteBackupConfirm by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onReplaceFiles(uris)
            onDismiss()
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            onReplaceFolder(uri)
            onDismiss()
        }
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
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = component.folderName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${component.fileCount} files  •  ${component.formattedSize}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // File list
            if (component.files.isNotEmpty()) {
                Text(
                    text = "Current Files",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(component.files) { file ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = file.name,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatSize(file.size),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Text(
                    text = "No files in this component folder",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))

            // Replace button
            Button(
                onClick = { showImportDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Replace Component")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Restore button
            if (component.hasBackup) {
                OutlinedButton(
                    onClick = { showRestoreConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restore Original Backup")
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = { showDeleteBackupConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete Backup", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        }
    }

    // Import type dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Method") },
            text = { Text("Replace with individual files or a whole folder?") },
            confirmButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    folderPicker.launch(null)
                }) { Text("Import Folder") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    filePicker.launch(arrayOf("*/*"))
                }) { Text("Import Files") }
            }
        )
    }

    // Restore confirm dialog
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Restore Backup") },
            text = { Text("This will replace the current files in ${component.folderName} with the backed-up originals.") },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    onRestore()
                    onDismiss()
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Delete backup confirm
    if (showDeleteBackupConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteBackupConfirm = false },
            title = { Text("Delete Backup") },
            text = { Text("Permanently delete the backup for ${component.folderName}? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteBackupConfirm = false
                    onDeleteBackup()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBackupConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
