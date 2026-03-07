package com.banner.inject.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.banner.inject.model.ComponentEntry
import com.banner.inject.model.FileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ComponentRepository(private val context: Context) {

    fun getRootDocument(uri: Uri): DocumentFile? =
        DocumentFile.fromTreeUri(context, uri)

    fun scanComponents(rootDoc: DocumentFile, backupManager: BackupManager): List<ComponentEntry> {
        return rootDoc.listFiles()
            .filter { it.isDirectory }
            .map { dir ->
                val files = dir.listFiles()
                    .filter { it.isFile }
                    .map { f -> FileInfo(f.name ?: "unknown", f.length(), f.type ?: "") }
                ComponentEntry(
                    folderName = dir.name ?: "unknown",
                    documentFile = dir,
                    files = files,
                    hasBackup = backupManager.hasBackup(dir.name ?: ""),
                    totalSize = files.sumOf { it.size }
                )
            }
            .sortedBy { it.folderName.lowercase() }
    }

    suspend fun replaceWithWcp(
        component: DocumentFile,
        wcpUri: Uri,
        backupManager: BackupManager,
        onProgress: (String) -> Unit
    ): Result<WcpExtractor.WcpProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val componentName = component.name ?: "component"

            onProgress("Backing up $componentName...")
            backupManager.backupFromDocumentFile(component, componentName)

            onProgress("Clearing existing files...")
            component.listFiles().forEach { it.deleteRecursively() }

            WcpExtractor(context)
                .extractToDocumentFile(wcpUri, component, onProgress)
                .getOrThrow()
        }
    }

    suspend fun restoreComponent(
        component: DocumentFile,
        backupManager: BackupManager,
        onProgress: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val componentName = component.name ?: "component"
            if (!backupManager.hasBackup(componentName)) {
                throw Exception("No backup found for $componentName")
            }

            onProgress("Clearing current files...")
            component.listFiles().forEach { it.deleteRecursively() }

            backupManager.listAllBackupFiles(componentName).forEach { (file, relPath) ->
                onProgress("Restoring ${file.name}...")
                val parts = relPath.split("/")
                var currentDir = component
                for (i in 0 until parts.size - 1) {
                    currentDir = currentDir.findFile(parts[i])
                        ?: currentDir.createDirectory(parts[i])
                        ?: throw Exception("Failed to create directory ${parts[i]}")
                }
                val destFile = currentDir.createFile("application/octet-stream", file.name)
                    ?: throw Exception("Failed to create ${file.name}")
                file.inputStream().use { input ->
                    context.contentResolver.openOutputStream(destFile.uri)?.use { output ->
                        input.copyTo(output)
                    } ?: throw Exception("Cannot write to ${file.name}")
                }
            }
        }
    }

    private fun DocumentFile.deleteRecursively(): Boolean {
        if (isDirectory) listFiles().forEach { it.deleteRecursively() }
        return delete()
    }
}
