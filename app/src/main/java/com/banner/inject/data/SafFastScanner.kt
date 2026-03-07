package com.banner.inject.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.banner.inject.model.FileInfo

/**
 * A highly optimized scanner that bypasses DocumentFile overhead by directly querying
 * the ContentResolver to recursively find all files inside a directory tree.
 */
object SafFastScanner {

    private val PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE
    )

    fun collectFilesRecursively(context: Context, rootDoc: DocumentFile): List<FileInfo> {
        val rootUri = rootDoc.uri
        val rootDocId = DocumentsContract.getDocumentId(rootUri)
        val treeUri = DocumentsContract.buildTreeDocumentUri(
            rootUri.authority,
            DocumentsContract.getTreeDocumentId(rootUri)
        )
        val result = mutableListOf<FileInfo>()
        
        // Use a queue to simulate recursion without call stack depth limits
        val queue = ArrayDeque<Pair<String, String>>() // Pair(documentId, relativePathPrefix)
        queue.add(Pair(rootDocId, ""))

        val resolver = context.contentResolver

        while (queue.isNotEmpty()) {
            val (currentDocId, currentPrefix) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)

            try {
                resolver.query(childrenUri, PROJECTION, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

                    while (cursor.moveToNext()) {
                        val childId = cursor.getString(idCol)
                        val childName = cursor.getString(nameCol) ?: "unknown"
                        val mimeType = cursor.getString(mimeCol)
                        val relPath = if (currentPrefix.isEmpty()) childName else "$currentPrefix/$childName"

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.add(Pair(childId, relPath))
                        } else {
                            val size = if (!cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                            result.add(
                                FileInfo(
                                    name = childName,
                                    relativePath = relPath,
                                    size = size,
                                    mimeType = mimeType ?: ""
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // If a query fails (e.g., permission loss during scan), we just skip that folder
                e.printStackTrace()
            }
        }
        return result
    }
}