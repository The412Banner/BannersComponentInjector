package com.banner.inject.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.github.luben.zstd.ZstdInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.ByteArrayOutputStream

class WcpExtractor(private val context: Context) {

    data class WcpProfile(
        val type: String,
        val versionName: String,
        val description: String
    )

    suspend fun extractToDocumentFile(
        wcpUri: Uri,
        destDir: DocumentFile,
        onProgress: (String) -> Unit
    ): Result<WcpProfile> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(wcpUri)
                ?: throw Exception("Cannot open WCP file")

            context.contentResolver.openInputStream(wcpUri)!!.use { raw ->
                ZstdInputStream(raw).use { zstd ->
                    TarArchiveInputStream(zstd).use { tar ->
                        var profile: WcpProfile? = null
                        var entry = tar.nextTarEntry

                        while (entry != null) {
                            when {
                                entry.isDirectory -> { /* skip — we create dirs on demand */ }

                                entry.name == "profile.json" -> {
                                    val buf = ByteArrayOutputStream()
                                    tar.copyTo(buf)
                                    profile = parseProfile(buf.toString(Charsets.UTF_8))
                                }

                                else -> {
                                    val name = entry.name
                                    onProgress("Extracting ${name.substringAfterLast('/')}...")

                                    // Navigate / create intermediate directories
                                    val parts = name.split("/")
                                    var currentDir = destDir
                                    for (i in 0 until parts.size - 1) {
                                        val dirName = parts[i]
                                        currentDir = currentDir.findFile(dirName)
                                            ?: currentDir.createDirectory(dirName)
                                            ?: throw Exception("Failed to create directory $dirName")
                                    }

                                    val fileName = parts.last()
                                    val destFile = currentDir.createFile("application/octet-stream", fileName)
                                        ?: throw Exception("Failed to create $fileName in destination")

                                    context.contentResolver.openOutputStream(destFile.uri)?.use { out ->
                                        tar.copyTo(out)
                                    } ?: throw Exception("Cannot write $fileName")
                                }
                            }
                            entry = tar.nextTarEntry
                        }

                        profile ?: WcpProfile("Unknown", "Unknown", "No profile found")
                    }
                }
            }
        }
    }

    private fun parseProfile(json: String): WcpProfile {
        fun field(key: String): String =
            Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: ""
        return WcpProfile(
            type        = field("type"),
            versionName = field("versionName"),
            description = field("description")
        )
    }
}
