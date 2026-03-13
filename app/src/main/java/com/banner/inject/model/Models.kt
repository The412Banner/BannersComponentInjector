package com.banner.inject.model

import androidx.documentfile.provider.DocumentFile

// ── Known GameHub variants ────────────────────────────────────────────────────
data class KnownApp(val displayName: String, val packageNames: List<String>, val isCustom: Boolean = false)

val KNOWN_GAMEHUB_APPS = listOf(
    KnownApp("GameHub (Lite)",        listOf("gamehub.lite", "emuready.gamehub.lite")),
    KnownApp("GameHub Lite PuBG",     listOf("com.tencent.ig")),
    KnownApp("GameHub Lite AnTuTu",   listOf("com.antutu.ABenchMark", "com.antutu.benchmark.full")),
    KnownApp("GameHub Lite Ludashi",  listOf("com.ludashi.aibench", "com.ludashi.benchmark")),
    KnownApp("GameHub Lite Genshin",  listOf("com.mihoyo.genshinimpact")),
    KnownApp("GameHub Lite Original", listOf("com.xiaoji.egggame"))
)

// ── UI models ─────────────────────────────────────────────────────────────────
data class GameHubApp(
    val known: KnownApp,
    val isInstalled: Boolean,
    val hasAccess: Boolean,
    /** The installed package name, or first package with a stored URI, or first in list. */
    val activePackage: String,
    /** All package names from this group that are actually installed on the device. */
    val installedPackages: List<String> = emptyList()
)

data class ComponentEntry(
    val folderName: String,
    val documentFile: DocumentFile,
    val files: List<FileInfo>,
    val hasBackup: Boolean,
    val totalSize: Long,
    val replacedWith: String? = null
) {
    val fileCount: Int get() = files.size
    val formattedSize: String get() = formatSize(totalSize)
}

data class FileInfo(
    val name: String,
    val relativePath: String, // e.g. "system32/d3d11.dll"
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

enum class GameType { LOCAL, STEAM }

data class GameEntry(val gameId: String, val type: GameType)

enum class MainTab(val title: String) {
    INJECT("Inject Components"),
    DOWNLOAD("Download Components"),
    MANAGERS("My Downloads"),
    GAMES("My Games")
}
