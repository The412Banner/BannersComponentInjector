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
import java.util.zip.ZipInputStream

class WcpExtractor(private val context: Context) {

    data class WcpProfile(
        val type: String,
        val versionName: String,
        val description: String
    )

    /** Opens the file and reads only the metadata without extracting anything. */
    suspend fun readProfile(wcpUri: Uri): Result<WcpProfile> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(wcpUri)!!.use { raw ->
                val buffered = BufferedInputStream(raw)
                if (isZip(buffered)) readProfileFromZip(buffered)
                else readProfileFromTar(buffered)
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
            context.contentResolver.openInputStream(wcpUri)!!.use { raw ->
                val buffered = BufferedInputStream(raw)
                if (isZip(buffered)) extractZipToDocumentFile(buffered, destDir, onProgress)
                else extractTarToDocumentFile(buffered, destDir, flattenToRoot, onProgress)
            }
        }
    }

    // ── Format detection ──────────────────────────────────────────────────────

    private fun isZip(buffered: BufferedInputStream): Boolean {
        buffered.mark(4)
        val magic = ByteArray(4)
        val read = buffered.read(magic)
        buffered.reset()
        return read >= 2 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte()
    }

    // ── ZIP (Turnip / ADRENOTOOLS format) ─────────────────────────────────────

    private fun readProfileFromZip(inputStream: BufferedInputStream): WcpProfile {
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.substringAfterLast('/') == "meta.json") {
                    val buf = ByteArrayOutputStream()
                    zip.copyTo(buf)
                    return parseMetaJson(buf.toString(Charsets.UTF_8))
                }
                entry = zip.nextEntry
            }
        }
        return WcpProfile("Unknown", "Unknown", "No meta.json found")
    }

    private fun extractZipToDocumentFile(
        inputStream: BufferedInputStream,
        destDir: DocumentFile,
        onProgress: (String) -> Unit
    ): WcpProfile {
        var profile: WcpProfile? = null
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.isDirectory -> { /* skip */ }
                    entry.name.substringAfterLast('/') == "meta.json" -> {
                        val buf = ByteArrayOutputStream()
                        zip.copyTo(buf)
                        profile = parseMetaJson(buf.toString(Charsets.UTF_8))
                    }
                    else -> {
                        val fileName = entry.name.substringAfterLast('/')
                        onProgress("Extracting $fileName...")
                        val destFile = destDir.createFile("application/octet-stream", fileName)
                            ?: throw Exception("Failed to create $fileName")
                        context.contentResolver.openOutputStream(destFile.uri)?.use { out ->
                            zip.copyTo(out)
                        } ?: throw Exception("Cannot write $fileName")
                    }
                }
                entry = zip.nextEntry
            }
        }
        return profile ?: WcpProfile("Unknown", "Unknown", "No meta.json found")
    }

    // ── TAR (WCP format — zstd / xz / gzip / bzip2 / lz4 / plain) ───────────

    private fun readProfileFromTar(inputStream: BufferedInputStream): WcpProfile {
        val decompressed = try {
            CompressorStreamFactory().createCompressorInputStream(inputStream)
        } catch (_: Exception) { inputStream }
        TarArchiveInputStream(decompressed).use { tar ->
            var entry = tar.nextTarEntry
            while (entry != null) {
                if (entry.name.removePrefix("./") == "profile.json") {
                    val buf = ByteArrayOutputStream()
                    tar.copyTo(buf)
                    return parseProfile(buf.toString(Charsets.UTF_8))
                }
                entry = tar.nextTarEntry
            }
        }
        return WcpProfile("Unknown", "Unknown", "No profile found")
    }

    private fun extractTarToDocumentFile(
        inputStream: BufferedInputStream,
        destDir: DocumentFile,
        flattenToRoot: Boolean,
        onProgress: (String) -> Unit
    ): WcpProfile {
        val decompressed = try {
            CompressorStreamFactory().createCompressorInputStream(inputStream)
        } catch (_: Exception) { inputStream }
        var profile: WcpProfile? = null
        TarArchiveInputStream(decompressed).use { tar ->
            var entry = tar.nextTarEntry
            while (entry != null) {
                when {
                    entry.isDirectory -> { /* skip — created on demand */ }

                    entry.name.removePrefix("./") == "profile.json" -> {
                        val buf = ByteArrayOutputStream()
                        tar.copyTo(buf)
                        profile = parseProfile(buf.toString(Charsets.UTF_8))
                    }

                    else -> {
                        val name = entry.name.removePrefix("./")
                        val fileName = name.substringAfterLast('/')
                        onProgress("Extracting $fileName...")

                        val destFile = if (flattenToRoot) {
                            destDir.createFile("application/octet-stream", fileName)
                                ?: throw Exception("Failed to create $fileName")
                        } else {
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
        }
        return profile ?: WcpProfile("Unknown", "Unknown", "No profile found")
    }

    // ── Metadata parsers ──────────────────────────────────────────────────────

    private fun parseProfile(json: String): WcpProfile {
        fun field(key: String): String =
            Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: ""
        return WcpProfile(
            type        = field("type"),
            versionName = field("versionName"),
            description = field("description")
        )
    }

    /** Parses the meta.json format used by Turnip / adrenotools GPU driver zips. */
    private fun parseMetaJson(json: String): WcpProfile {
        fun field(key: String): String =
            Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: ""
        return WcpProfile(
            type        = field("vendor").ifEmpty { "GPU Driver" },
            versionName = field("name"),
            description = field("description")
        )
    }
}
