package com.banner.inject.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.documentfile.provider.DocumentFile
import com.banner.inject.model.GameEntry
import com.banner.inject.model.GameType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GameRepository(private val context: Context) {

    fun getRootDocument(uri: android.net.Uri): DocumentFile? =
        DocumentFile.fromTreeUri(context, uri)

    /**
     * Navigates from the granted data root down to virtual_containers/:
     * data/ → files/usr/home/virtual_containers/
     */
    fun navigateToVirtualContainers(dataRoot: DocumentFile): DocumentFile? =
        dataRoot.findFile("files")
            ?.findFile("usr")
            ?.findFile("home")
            ?.findFile("virtual_containers")

    /**
     * Navigates from the granted data root down to the Steam shadercache:
     * data/ → files/Steam/steamapps/shadercache/
     */
    fun navigateToShadercache(dataRoot: DocumentFile): DocumentFile? =
        dataRoot.findFile("files")
            ?.findFile("Steam")
            ?.findFile("steamapps")
            ?.findFile("shadercache")

    /**
     * Scans virtual_containers/ for LOCAL game folders.
     * Includes all directories (GameHub uses any folder name for imported games).
     */
    fun scanLocalGames(rootDoc: DocumentFile): List<GameEntry> =
        rootDoc.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }
            ?.sorted()
            ?.map { GameEntry(it, GameType.LOCAL) }
            ?: emptyList()

    /**
     * Scans Steam/steamapps/shadercache/ for Steam game folders.
     * Every directory is a Steam App ID.
     */
    fun scanSteamGames(rootDoc: DocumentFile): List<GameEntry> =
        rootDoc.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }
            ?.sorted()
            ?.map { GameEntry(it, GameType.STEAM) }
            ?: emptyList()

    /**
     * Creates/updates a `<gameId>.iso` text file in the virtual_containers/ root
     * for each LOCAL game. Used by GameHub's own launcher to recognize local imports.
     * Returns the count of ISOs written.
     */
    suspend fun createIsoFiles(rootDoc: DocumentFile, localGames: List<GameEntry>): Int =
        withContext(Dispatchers.IO) {
            var written = 0
            localGames.filter { it.type == GameType.LOCAL }.forEach { game ->
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
     * Deletes the virtual container folder for [gameId] from [virtualContainersRoot].
     * Also removes the companion `<gameId>.iso` stub file if present.
     * Returns true if the folder was found and deleted successfully.
     */
    suspend fun deleteLocalGameFolder(virtualContainersRoot: DocumentFile, gameId: String): Boolean =
        withContext(Dispatchers.IO) {
            val folder = virtualContainersRoot.findFile(gameId) ?: return@withContext false
            val deleted = folder.delete()
            // Best-effort cleanup of the companion ISO stub
            virtualContainersRoot.findFile("$gameId.iso")?.delete()
            deleted
        }

    /**
     * Launches a game in the given GameHub package.
     * For LOCAL games: localGameId = gameId, steamAppId = gameId
     * For STEAM games: steamAppId = gameId, localGameId = gameId
     * (GameHub uses whichever is relevant; passing both as the same value matches
     *  the original am start commands used by the GameHub launcher)
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
