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
    }

    private fun baseRelPath(componentName: String) =
        "${Environment.DIRECTORY_DOWNLOADS}/$BACKUP_DIR/${sanitize(componentName)}"

    fun hasBackup(componentName: String): Boolean {
        val cursor = context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
            arrayOf("${baseRelPath(componentName)}%"),
            null
        )
        return cursor?.use { it.count > 0 } ?: false
    }

    fun deleteBackup(componentName: String) {
        context.contentResolver.delete(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
            arrayOf("${baseRelPath(componentName)}%")
        )
    }

    suspend fun backupFromDocumentFile(
        component: DocumentFile,
        componentName: String
    ) = withContext(Dispatchers.IO) {
        deleteBackup(componentName)
        copyDocumentToDownloads(component, sanitize(componentName), "")
    }

    // Returns list of (uri, relativePathFromComponentRoot) pairs
    fun listAllBackupFiles(componentName: String): List<Pair<Uri, String>> {
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
                val relPath = it.getString(pathCol)   // e.g. "Downloads/BannersComponentInjector/DXVK/system32/"
                val name = it.getString(nameCol)
                val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                val subdir = relPath.removePrefix(base).trimEnd('/')
                val fileRelPath = if (subdir.isEmpty()) name else "$subdir/$name"
                result.add(uri to fileRelPath)
            }
        }
        return result
    }

    private fun copyDocumentToDownloads(source: DocumentFile, componentSanitized: String, subPath: String) {
        source.listFiles().forEach { item ->
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

    private fun sanitize(name: String): String =
        name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
}
