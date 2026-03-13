package com.banner.inject.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.documentfile.provider.DocumentFile
import com.banner.inject.model.GameEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GameRepository(private val context: Context) {

    fun getRootDocument(uri: android.net.Uri): DocumentFile? =
        DocumentFile.fromTreeUri(context, uri)

    /** Scans virtual_containers/ for game folder names. Skips .iso files. */
    fun scanGames(rootDoc: DocumentFile): List<GameEntry> =
        rootDoc.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }
            ?.sorted()
            ?.map { GameEntry(it) }
            ?: emptyList()

    /**
     * Creates/updates a `<gameId>.iso` text file in the virtual_containers/ root for each game.
     * Each file contains the gameId as plain text — used by GameHub's own launcher.
     * Returns the count of ISOs written.
     */
    suspend fun createIsoFiles(rootDoc: DocumentFile, games: List<GameEntry>): Int =
        withContext(Dispatchers.IO) {
            var written = 0
            games.forEach { game ->
                val isoName = "${game.gameId}.iso"
                var isoFile = rootDoc.findFile(isoName)
                if (isoFile == null) {
                    isoFile = rootDoc.createFile("application/octet-stream", isoName)
                }
                if (isoFile != null) {
                    try {
                        context.contentResolver.openOutputStream(isoFile.uri, "wt")?.use { out ->
                            out.write(game.gameId.toByteArray())
                        }
                        written++
                    } catch (_: Exception) {}
                }
            }
            written
        }

    /**
     * Launches a game in the given GameHub package.
     * Maps to: am start -n <pkg>/com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity
     *          -a <pkg>.LAUNCH_GAME --es localGameId <gameId> --es steamAppId <gameId> --ez autoStartGame true
     */
    fun launchGame(packageName: String, gameId: String): Result<Unit> = runCatching {
        val intent = Intent().apply {
            component = ComponentName(
                packageName,
                "com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity"
            )
            action = "$packageName.LAUNCH_GAME"
            putExtra("localGameId", gameId)
            putExtra("steamAppId", gameId)
            putExtra("autoStartGame", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
