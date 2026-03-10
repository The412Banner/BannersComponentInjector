package com.banner.inject.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupManager(private val context: Context) {

    companion object {
        private const val BACKUP_DIR = "BannersComponentInjector"
        private const val PREFS = "bci_settings"
        private const val KEY_CUSTOM_BACKUPS_URI = "custom_backups_uri"
    }

    // ── Custom location helpers ────────────────────────────────────────────────

    private fun customBackupRoot(): DocumentFile? {
        val uriStr = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_BACKUPS_URI, null) ?: return null
        return DocumentFile.fromTreeUri(context, Uri.parse(uriStr))
    }

    private fun baseRelPath(componentName: String) =
        "${Environment.DIRECTORY_DOWNLOADS}/$BACKUP_DIR/${sanitize(componentName)}"

    // ── Public API ─────────────────────────────────────────────────────────────

    fun hasBackup(componentName: String): Boolean {
        val root = customBackupRoot()
        return if (root != null) {
            root.findFile(sanitize(componentName)) != null
        } else {
            val cursor = context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                arrayOf("${baseRelPath(componentName)}%"),
                null
            )
            cursor?.use { it.count > 0 } ?: false
        }
    }

    fun deleteBackup(componentName: String) {
        val root = customBackupRoot()
        if (root != null) {
            root.findFile(sanitize(componentName))?.delete()
        } else {
            context.contentResolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                arrayOf("${baseRelPath(componentName)}%")
            )
        }
    }

    suspend fun backupFromDocumentFile(
        component: DocumentFile,
        componentName: String
    ) = withContext(Dispatchers.IO) {
        val root = customBackupRoot()
        if (root != null) {
            deleteBackup(componentName)
            val destDir = root.findFile(sanitize(componentName))
                ?: root.createDirectory(sanitize(componentName))
                ?: throw Exception("Could not create backup folder in custom location")
            copyDocumentToDocumentFile(component, destDir)
        } else {
            deleteBackup(componentName)
            copyDocumentToDownloads(component, sanitize(componentName), "")
        }
    }

    // Returns list of (uri, relativePathFromComponentRoot) pairs
    fun listAllBackupFiles(componentName: String): List<Pair<Uri, String>> {
        val root = customBackupRoot()
        return if (root != null) {
            val componentDir = root.findFile(sanitize(componentName)) ?: return emptyList()
            val result = mutableListOf<Pair<Uri, String>>()
            collectDocumentFiles(componentDir, "", result)
            result
        } else {
            val result = mutableListOf<Pair<Uri, String>>()
            val base = "${baseRelPath(componentName)}/"
            val cursor = context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Downloads._ID,
                    MediaStore.Downloads.RELATIVE_PATH,
                    MediaStore.Downloads.DISPLAY_NAME
                ),
                "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                arrayOf("$base%"),
                null
            )
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val pathCol = it.getColumnIndexOrThrow(MediaStore.Downloads.RELATIVE_PATH)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val relPath = it.getString(pathCol)
                    val name = it.getString(nameCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                    val subdir = relPath.removePrefix(base).trimEnd('/')
                    val fileRelPath = if (subdir.isEmpty()) name else "$subdir/$name"
                    result.add(uri to fileRelPath)
                }
            }
            result
        }
    }

    data class BackupInfo(val componentName: String, val fileCount: Int, val totalSize: Long)

    fun listAllBackups(): List<BackupInfo> {
        val root = customBackupRoot()
        return if (root != null) {
            (root.listFiles() ?: emptyArray())
                .filter { it.isDirectory }
                .map { dir ->
                    val name = dir.name ?: "Unknown"
                    val (count, size) = countFilesInDoc(dir)
                    BackupInfo(name, count, size)
                }
                .sortedBy { it.componentName.lowercase() }
        } else {
            val base = "${Environment.DIRECTORY_DOWNLOADS}/$BACKUP_DIR/"
            val cursor = context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads.RELATIVE_PATH, MediaStore.Downloads.SIZE),
                "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                arrayOf("$base%"),
                null
            )
            val grouped = mutableMapOf<String, Pair<Int, Long>>()
            cursor?.use {
                val pathCol = it.getColumnIndexOrThrow(MediaStore.Downloads.RELATIVE_PATH)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                while (it.moveToNext()) {
                    val relPath = it.getString(pathCol)
                    val size = it.getLong(sizeCol)
                    val componentName = relPath.removePrefix(base).split("/")
                        .firstOrNull { seg -> seg.isNotEmpty() } ?: continue
                    val current = grouped.getOrDefault(componentName, 0 to 0L)
                    grouped[componentName] = (current.first + 1) to (current.second + size)
                }
            }
            grouped.map { (name, data) -> BackupInfo(name, data.first, data.second) }
                .sortedBy { it.componentName.lowercase() }
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun copyDocumentToDownloads(source: DocumentFile, componentSanitized: String, subPath: String) {
        source.listFiles()?.forEach { item ->
            val itemName = item.name ?: return@forEach
            if (item.isDirectory) {
                val newSubPath = if (subPath.isEmpty()) itemName else "$subPath/$itemName"
                copyDocumentToDownloads(item, componentSanitized, newSubPath)
            } else {
                val relPath = "${Environment.DIRECTORY_DOWNLOADS}/$BACKUP_DIR/$componentSanitized" +
                    (if (subPath.isEmpty()) "" else "/$subPath") + "/"
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, itemName)
                    put(MediaStore.Downloads.RELATIVE_PATH, relPath)
                    put(MediaStore.Downloads.MIME_TYPE, item.type ?: "application/octet-stream")
                }
                val fileUri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                ) ?: return@forEach
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    context.contentResolver.openOutputStream(fileUri)?.use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun copyDocumentToDocumentFile(source: DocumentFile, destDir: DocumentFile) {
        source.listFiles()?.forEach { item ->
            val itemName = item.name ?: return@forEach
            if (item.isDirectory) {
                val subDir = destDir.findFile(itemName) ?: destDir.createDirectory(itemName) ?: return@forEach
                copyDocumentToDocumentFile(item, subDir)
            } else {
                destDir.findFile(itemName)?.delete()
                val destFile = destDir.createFile(item.type ?: "application/octet-stream", itemName) ?: return@forEach
                context.contentResolver.openInputStream(item.uri)?.use { input ->
                    context.contentResolver.openOutputStream(destFile.uri)?.use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun collectDocumentFiles(dir: DocumentFile, prefix: String, result: MutableList<Pair<Uri, String>>) {
        dir.listFiles()?.forEach { item ->
            val name = item.name ?: return@forEach
            val path = if (prefix.isEmpty()) name else "$prefix/$name"
            if (item.isDirectory) collectDocumentFiles(item, path, result)
            else result.add(item.uri to path)
        }
    }

    private fun countFilesInDoc(dir: DocumentFile): Pair<Int, Long> {
        var count = 0
        var size = 0L
        dir.listFiles()?.forEach { item ->
            if (item.isDirectory) {
                val (c, s) = countFilesInDoc(item)
                count += c; size += s
            } else {
                count++; size += item.length()
            }
        }
        return count to size
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
}
