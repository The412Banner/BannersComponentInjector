package com.banner.inject.model

import androidx.documentfile.provider.DocumentFile

// ── Known GameHub variants ────────────────────────────────────────────────────
// TODO: Replace placeholder package names with the real GameHub package names.
data class KnownApp(val displayName: String, val packageName: String)

val KNOWN_GAMEHUB_APPS = listOf(
    KnownApp("GameHub",          "com.gamehub.app"),
    KnownApp("GameHub Pro",      "com.gamehub.pro"),
    KnownApp("GameHub Lite",     "com.gamehub.lite"),
    KnownApp("GameHub Emulator", "com.gamehub.emulator"),
    KnownApp("GameHub Beta",     "com.gamehub.beta")
)

// ── UI models ─────────────────────────────────────────────────────────────────
data class GameHubApp(
    val known: KnownApp,
    val isInstalled: Boolean,
    val hasAccess: Boolean       // whether a persisted SAF URI exists for this package
)

data class ComponentEntry(
    val folderName: String,
    val documentFile: DocumentFile,
    val files: List<FileInfo>,
    val hasBackup: Boolean,
    val totalSize: Long
) {
    val fileCount: Int get() = files.size
    val formattedSize: String get() = formatSize(totalSize)
}

data class FileInfo(
    val name: String,
    val size: Long,
    val mimeType: String
)

sealed class OpState {
    object Idle : OpState()
    data class InProgress(val message: String) : OpState()
    data class Done(val message: String) : OpState()
    data class Error(val message: String) : OpState()
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024L          -> "%.1f KB".format(bytes / 1024.0)
    else                    -> "$bytes B"
}
