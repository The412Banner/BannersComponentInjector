package com.banner.inject.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.banner.inject.model.ComponentEntry
import com.banner.inject.model.FileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ComponentRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("component_notes", Context.MODE_PRIVATE)

    fun getRootDocument(uri: Uri): DocumentFile? =
        DocumentFile.fromTreeUri(context, uri)

    fun scanComponents(rootDoc: DocumentFile, backupManager: BackupManager): List<ComponentEntry> {
        return rootDoc.listFiles()
            .filter { it.isDirectory }
            .map { dir ->
                val folderName = dir.name ?: "unknown"
                val files = SafFastScanner.collectFilesRecursively(context, dir)
                ComponentEntry(
                    folderName = folderName,
                    documentFile = dir,
                    files = files,
                    hasBackup = backupManager.hasBackup(folderName),
                    totalSize = files.sumOf { it.size },
                    replacedWith = prefs.getString("replaced_with_$folderName", null)
                )
            }
            .sortedBy { it.folderName.lowercase() }
    }

    fun scanSingleComponent(dir: DocumentFile, backupManager: BackupManager): ComponentEntry {
        val folderName = dir.name ?: "unknown"
        val files = SafFastScanner.collectFilesRecursively(context, dir)
        return ComponentEntry(
            folderName = folderName,
            documentFile = dir,
            files = files,
            hasBackup = backupManager.hasBackup(folderName),
            totalSize = files.sumOf { it.size },
            replacedWith = prefs.getString("replaced_with_$folderName", null)
        )
    }

    private fun collectFilesRecursively(dir: DocumentFile, prefix: String): List<FileInfo> {
        val result = mutableListOf<FileInfo>()
        dir.listFiles().forEach { item ->
            val relPath = if (prefix.isEmpty()) item.name ?: "" else "$prefix/${item.name ?: ""}"
            if (item.isDirectory) {
                result.addAll(collectFilesRecursively(item, relPath))
            } else {
                result.add(
                    FileInfo(
                        name = item.name ?: "unknown",
                        relativePath = relPath,
                        size = item.length(),
                        mimeType = item.type ?: ""
                    )
                )
            }
        }
        return result
    }

    suspend fun replaceWithWcp(
        component: DocumentFile,
        wcpUri: Uri,
        backupManager: BackupManager,
        onProgress: (String) -> Unit
    ): Result<WcpExtractor.WcpProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val componentName = component.name ?: "component"

            // Read the WCP profile first to determine extraction mode.
            // FEXCore files are flat (no subdirs) so we strip the WCP's
            // system32/ prefix. All other types (DXVK, VKD3D, Box64, Turnip
            // etc.) preserve the WCP directory structure as-is.
            val extractor = WcpExtractor(context)
            val profile = extractor.readProfile(wcpUri).getOrThrow()
            val isFlat = profile.type.equals("FEXCore", ignoreCase = true)

            onProgress("Backing up $componentName...")
            backupManager.backupFromDocumentFile(component, componentName)

            onProgress("Clearing existing files...")
            component.listFiles().forEach { it.deleteRecursively() }

            WcpExtractor(context)
                .extractToDocumentFile(wcpUri, component, flattenToRoot = isFlat, onProgress)
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

            backupManager.listAllBackupFiles(componentName).forEach { (uri, relPath) ->
                val fileName = relPath.substringAfterLast('/')
                onProgress("Restoring $fileName...")
                val parts = relPath.split("/")
                var currentDir = component
                for (i in 0 until parts.size - 1) {
                    currentDir = currentDir.findFile(parts[i])
                        ?: currentDir.createDirectory(parts[i])
                        ?: throw Exception("Failed to create directory ${parts[i]}")
                }
                val destFile = currentDir.createFile("application/octet-stream", fileName)
                    ?: throw Exception("Failed to create $fileName")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    context.contentResolver.openOutputStream(destFile.uri)?.use { output ->
                        input.copyTo(output)
                    } ?: throw Exception("Cannot write to $fileName")
                } ?: throw Exception("Cannot read backup for $fileName")
            }
        }
    }

    private fun DocumentFile.deleteRecursively(): Boolean {
        if (isDirectory) listFiles().forEach { it.deleteRecursively() }
        return delete()
    }
}
