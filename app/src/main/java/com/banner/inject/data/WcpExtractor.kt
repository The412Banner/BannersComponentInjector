package com.banner.inject.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.CompressorStreamFactory
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream

class WcpExtractor(private val context: Context) {

    data class WcpProfile(
        val type: String,
        val versionName: String,
        val description: String
    )

    /** Opens the WCP and reads only profile.json without extracting any files. */
    suspend fun readProfile(wcpUri: Uri): Result<WcpProfile> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(wcpUri)!!.use { raw ->
                val buffered = BufferedInputStream(raw)
                val decompressed = try {
                    CompressorStreamFactory().createCompressorInputStream(buffered)
                } catch (_: Exception) { buffered }
                TarArchiveInputStream(decompressed).use { tar ->
                    var entry = tar.nextTarEntry
                    while (entry != null) {
                        if (entry.name == "profile.json") {
                            val buf = ByteArrayOutputStream()
                            tar.copyTo(buf)
                            return@runCatching parseProfile(buf.toString(Charsets.UTF_8))
                        }
                        entry = tar.nextTarEntry
                    }
                    WcpProfile("Unknown", "Unknown", "No profile found")
                }
            }
        }
    }

    suspend fun extractToDocumentFile(
        wcpUri: Uri,
        destDir: DocumentFile,
        flattenToRoot: Boolean = false,
        onProgress: (String) -> Unit
    ): Result<WcpProfile> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(wcpUri)
                ?: throw Exception("Cannot open WCP file")

            context.contentResolver.openInputStream(wcpUri)!!.use { raw ->
                // BufferedInputStream required for CompressorStreamFactory.detect() mark/reset
                val buffered = BufferedInputStream(raw)
                val decompressed = try {
                    CompressorStreamFactory().createCompressorInputStream(buffered)
                } catch (_: Exception) {
                    // Not compressed — treat as plain tar
                    buffered
                }
                TarArchiveInputStream(decompressed).use { tar ->
                        var profile: WcpProfile? = null
                        var entry = tar.nextTarEntry

                        while (entry != null) {
                            when {
                                entry.isDirectory -> { /* skip — created on demand */ }

                                entry.name == "profile.json" -> {
                                    val buf = ByteArrayOutputStream()
                                    tar.copyTo(buf)
                                    profile = parseProfile(buf.toString(Charsets.UTF_8))
                                }

                                else -> {
                                    val name = entry.name
                                    val fileName = name.substringAfterLast('/')
                                    onProgress("Extracting $fileName...")

                                    val destFile = if (flattenToRoot) {
                                        // Flat component — strip all subdirectory structure,
                                        // place every file directly at the component root
                                        destDir.createFile("application/octet-stream", fileName)
                                            ?: throw Exception("Failed to create $fileName")
                                    } else {
                                        // Preserve full directory structure from WCP
                                        val parts = name.split("/")
                                        var currentDir = destDir
                                        for (i in 0 until parts.size - 1) {
                                            val dirName = parts[i]
                                            currentDir = currentDir.findFile(dirName)
                                                ?: currentDir.createDirectory(dirName)
                                                ?: throw Exception("Failed to create directory $dirName")
                                        }
                                        currentDir.createFile("application/octet-stream", parts.last())
                                            ?: throw Exception("Failed to create ${parts.last()}")
                                    }

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
