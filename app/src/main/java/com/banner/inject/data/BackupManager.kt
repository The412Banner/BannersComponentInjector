package com.banner.inject.data

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class BackupManager(private val context: Context) {

    private val backupsDir = File(context.filesDir, "backups")

    fun getBackupDir(componentName: String): File =
        File(backupsDir, sanitize(componentName))

    fun hasBackup(componentName: String): Boolean {
        val dir = getBackupDir(componentName)
        return dir.exists() && dir.walkTopDown().any { it.isFile }
    }

    fun deleteBackup(componentName: String) {
        getBackupDir(componentName).deleteRecursively()
    }

    suspend fun backupFromDocumentFile(
        component: DocumentFile,
        componentName: String
    ) = withContext(Dispatchers.IO) {
        val backupDir = getBackupDir(componentName)
        backupDir.deleteRecursively()
        backupDir.mkdirs()
        copyDocumentToLocal(component, backupDir)
    }

    // Returns list of (file, relativePathFromComponentRoot) pairs
    fun listAllBackupFiles(componentName: String): List<Pair<File, String>> {
        val backupDir = getBackupDir(componentName)
        if (!backupDir.exists()) return emptyList()
        return collectFiles(backupDir, "")
    }

    fun backupFileCount(componentName: String): Int =
        listAllBackupFiles(componentName).size

    private fun copyDocumentToLocal(source: DocumentFile, destDir: File) {
        source.listFiles().forEach { item ->
            if (item.isDirectory) {
                val subDir = File(destDir, item.name ?: return@forEach)
                subDir.mkdirs()
                copyDocumentToLocal(item, subDir)
            } else {
                val name = item.name ?: return@forEach
                val destFile = File(destDir, name)
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun collectFiles(dir: File, prefix: String): List<Pair<File, String>> {
        val result = mutableListOf<Pair<File, String>>()
        dir.listFiles()?.forEach { item ->
            val relPath = if (prefix.isEmpty()) item.name else "$prefix/${item.name}"
            if (item.isDirectory) {
                result.addAll(collectFiles(item, relPath))
            } else {
                result.add(item to relPath)
            }
        }
        return result
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
}
