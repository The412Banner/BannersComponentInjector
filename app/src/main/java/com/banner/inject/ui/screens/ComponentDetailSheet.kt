package com.banner.inject.ui.screens

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banner.inject.data.RemoteSourceRepository
import com.banner.inject.model.ComponentEntry
import com.banner.inject.model.formatSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentDetailSheet(
    component: ComponentEntry,
    onDismiss: () -> Unit,
    onBackup: () -> Unit,
    onReplaceWcp: (Uri) -> Unit,
    onRestore: () -> Unit,
    onDeleteBackup: () -> Unit
) {
    var showNoBackupWarning by remember { mutableStateOf(false) }
    var pendingReplaceIsRemote by remember { mutableStateOf<Boolean?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showDeleteBackupConfirm by remember { mutableStateOf(false) }
    var showRemoteSourceSheet by remember { mutableStateOf(false) }
    var dontAskAgain by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bci_settings", Context.MODE_PRIVATE) }
    val skipWarning = remember { prefs.getBoolean("skip_backup_warning", false) }
    val remoteRepo = remember { RemoteSourceRepository(context) }

    val wcpPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) { onReplaceWcp(uri); onDismiss() }
    }

    fun onReplaceRequested(isRemote: Boolean) {
        if (!component.hasBackup && !skipWarning) {
            pendingReplaceIsRemote = isRemote
            showNoBackupWarning = true
        } else {
            if (isRemote) {
                showRemoteSourceSheet = true
            } else {
                wcpPicker.launch(arrayOf("*/*"))
            }
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
                    Icons.Default.Layers, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        component.folderName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${component.fileCount} files  •  ${component.formattedSize}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Current file list
            if (component.files.isNotEmpty()) {
                Text(
                    "Current Files",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(component.files) { file ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                file.relativePath.ifEmpty { file.name },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                formatSize(file.size),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            } else {
                Text(
                    "Component folder is empty",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))

            // Backup button
            OutlinedButton(
                onClick = { onBackup(); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (component.hasBackup) "Re-backup Current Contents" else "Backup Current Contents")
            }

            Spacer(Modifier.height(8.dp))

            // Replace with WCP (Local)
            Button(
                onClick = { onReplaceRequested(isRemote = false) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Select Local File")
            }
            
            Spacer(Modifier.height(8.dp))

            // Replace with Remote Source
            Button(
                onClick = { onReplaceRequested(isRemote = true) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Select Online Source")
            }

            // Restore + delete backup
            if (component.hasBackup) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showRestoreConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Restore, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Restore Original Backup")
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { showDeleteBackupConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.DeleteOutline, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Delete Backup", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        }
    }

    if (showRemoteSourceSheet) {
        RemoteSourceSheet(
            component = component,
            repo = remoteRepo,
            onDismiss = { showRemoteSourceSheet = false },
            onDownloadAndReplace = { uri -> 
                onReplaceWcp(uri)
                onDismiss()
            }
        )
    }

    // No backup warning
    if (showNoBackupWarning) {
        AlertDialog(
            onDismissRequest = { showNoBackupWarning = false; dontAskAgain = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("No Backup Found") },
            text = {
                Column {
                    Text("You haven't backed up ${component.folderName} yet. If you replace it now you won't be able to restore the originals.")
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = dontAskAgain,
                            onCheckedChange = { dontAskAgain = it }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Don't ask again", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dontAskAgain) prefs.edit().putBoolean("skip_backup_warning", true).apply()
                    showNoBackupWarning = false
                    dontAskAgain = false
                    if (pendingReplaceIsRemote == true) {
                        showRemoteSourceSheet = true
                    } else {
                        wcpPicker.launch(arrayOf("*/*"))
                    }
                    pendingReplaceIsRemote = null
                }) { Text("Replace Anyway", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNoBackupWarning = false
                    dontAskAgain = false
                    pendingReplaceIsRemote = null
                }) { Text("Cancel") }
            }
        )
    }

    // Restore confirm
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Restore Backup") },
            text = { Text("Replace current contents of ${component.folderName} with the backed-up originals?") },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    onRestore(); onDismiss()
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
            text = { Text("Permanently delete the saved backup for ${component.folderName}?") },
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
