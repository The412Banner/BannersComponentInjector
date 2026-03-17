package com.banner.inject.data

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.banner.inject.model.GameEntry
import com.banner.inject.model.GameType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GameRepository(private val context: Context) {

    fun getRootDocument(uri: android.net.Uri): DocumentFile? =
        DocumentFile.fromTreeUri(context, uri)

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
     * Writes a `<name>.iso` file containing [localId] to Downloads/front end/.
     * Creates or overwrites any existing file with the same name.
     * Returns true on success.
     */
    fun writeIsoToFrontEnd(name: String, localId: String): Boolean {
        return try {
            val relativePath = Environment.DIRECTORY_DOWNLOADS + "/front end/"
            // Sanitize characters Android replaces in filenames (e.g. ':' → '_' in "Counter-Strike: Source")
            // so the query and insert both use the same string that MediaStore actually stores.
            val displayName = "$name.iso".replace(Regex("[/\\\\:*?\"<>|]"), "_")
            val selection = "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} = ?"
            val selArgs = arrayOf(relativePath, displayName)
            // Skip if already exists — avoids duplicate "(1)", "(2)" copies on every launch.
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                selection, selArgs, null
            )?.use { cursor -> if (cursor.moveToFirst()) return true }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(localId.toByteArray())
            }
            true
        } catch (_: Exception) { false }
    }

    /**
     * Deletes the `<name>.iso` file from Downloads/front end/ if it exists.
     */
    fun deleteIsoFromFrontEnd(name: String) {
        try {
            val relativePath = Environment.DIRECTORY_DOWNLOADS + "/front end/"
            val selection = "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} = ?"
            context.contentResolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                selection,
                arrayOf(relativePath, "$name.iso")
            )
        } catch (_: Exception) {}
    }

    /**
     * Launches a game in the given GameHub package.
     * For LOCAL games: localGameId = gameId, steamAppId = gameId
     * For STEAM games: steamAppId = gameId, localGameId = gameId
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
