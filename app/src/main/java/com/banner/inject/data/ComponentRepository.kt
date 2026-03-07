package com.banner.inject.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
                val totalSize = files.sumOf { it.size }
                ComponentEntry(
                    folderName = dir.name ?: "unknown",
                    documentFile = dir,
                    files = files,
                    hasBackup = backupManager.hasBackup(dir.name ?: ""),
                    totalSize = totalSize
                )
            }
            .sortedBy { it.folderName.lowercase() }
    }

    suspend fun replaceWithFiles(
        component: DocumentFile,
        sourceUris: List<Uri>,
        backupManager: BackupManager,
        onProgress: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val componentName = component.name ?: "component"

            onProgress("Backing up $componentName...")
            backupManager.backupFromDocumentFile(component, componentName)

            onProgress("Clearing existing files...")
            component.listFiles().forEach { it.deleteRecursively() }

            sourceUris.forEachIndexed { i, uri ->
                val fileName = getFileName(uri) ?: "file_$i"
                onProgress("Copying $fileName...")
                val destFile = component.createFile("application/octet-stream", fileName)
                    ?: throw Exception("Failed to create $fileName in destination")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    context.contentResolver.openOutputStream(destFile.uri)?.use { output ->
                        input.copyTo(output)
                    }
                } ?: throw Exception("Could not read $fileName")
            }
        }
    }

    suspend fun replaceWithFolder(
        component: DocumentFile,
        sourceFolderUri: Uri,
        backupManager: BackupManager,
        onProgress: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val componentName = component.name ?: "component"
            val sourceDoc = DocumentFile.fromTreeUri(context, sourceFolderUri)
                ?: throw Exception("Cannot access selected folder")

            onProgress("Backing up $componentName...")
            backupManager.backupFromDocumentFile(component, componentName)

            onProgress("Clearing existing files...")
            component.listFiles().forEach { it.deleteRecursively() }

            copyDocumentFolderContents(sourceDoc, component, onProgress)
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

            val backupFiles = backupManager.listAllBackupFiles(componentName)
            backupFiles.forEach { (file, relPath) ->
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

    private fun copyDocumentFolderContents(
        source: DocumentFile,
        dest: DocumentFile,
        onProgress: (String) -> Unit
    ) {
        source.listFiles().forEach { item ->
            if (item.isDirectory) {
                val newDir = dest.createDirectory(item.name ?: return@forEach)
                    ?: throw Exception("Failed to create directory ${item.name}")
                copyDocumentFolderContents(item, newDir, onProgress)
            } else {
                val name = item.name ?: return@forEach
                onProgress("Copying $name...")
                val destFile = dest.createFile("application/octet-stream", name)
                    ?: throw Exception("Failed to create $name")
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    context.contentResolver.openOutputStream(destFile.uri)?.use { output ->
                        input.copyTo(output)
                    }
                } ?: throw Exception("Could not read $name")
            }
        }
    }

    private fun DocumentFile.deleteRecursively(): Boolean {
        if (isDirectory) listFiles().forEach { it.deleteRecursively() }
        return delete()
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name ?: uri.lastPathSegment
    }
}
