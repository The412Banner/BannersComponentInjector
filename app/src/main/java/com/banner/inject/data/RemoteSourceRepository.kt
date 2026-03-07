package com.banner.inject.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class RemoteSourceRepository(private val context: Context) {

    data class RemoteItem(
        val displayName: String,
        val versionName: String,
        val downloadUrl: String
    )

    // Arihany and StevenMXZ both use {type, verName, verCode, remoteUrl} format
    suspend fun fetchWcpJson(
        jsonUrl: String,
        componentType: String
    ): List<RemoteItem> = withContext(Dispatchers.IO) {
        val json = openUrl(jsonUrl).inputStream.bufferedReader().readText()
        val array = JSONArray(json)
        val all = mutableListOf<RemoteItem>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val type = obj.getString("type")
            val verName = obj.getString("verName")
            val url = obj.getString("remoteUrl")
            all.add(RemoteItem(displayName = "$type  $verName", versionName = verName, downloadUrl = url))
        }
        // Try to filter by component type; fall back to all if nothing matches
        val filtered = all.filter { it.displayName.startsWith(componentType, ignoreCase = true) }
        (if (filtered.isNotEmpty()) filtered else all).reversed()
    }

    // K11MCH1/AdrenoToolsDrivers — GitHub Releases API, Turnip ZIPs only
    suspend fun fetchTurnipReleases(): List<RemoteItem> = withContext(Dispatchers.IO) {
        val json = openUrl(
            "https://api.github.com/repos/K11MCH1/AdrenoToolsDrivers/releases"
        ).inputStream.bufferedReader().readText()
        val array = JSONArray(json)
        val result = mutableListOf<RemoteItem>()
        for (i in 0 until array.length()) {
            val release = array.getJSONObject(i)
            val releaseName = release.getString("name").trim()
            val assets = release.getJSONArray("assets")
            val turnipAssets = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .filter { it.getString("name").contains("turnip", ignoreCase = true) }
            for (asset in turnipAssets) {
                val assetName = asset.getString("name")
                val displayName = if (turnipAssets.size > 1) {
                    "$releaseName — ${assetName.removeSuffix(".zip")}"
                } else {
                    releaseName
                }
                result.add(
                    RemoteItem(
                        displayName = displayName,
                        versionName = assetName.removeSuffix(".zip"),
                        downloadUrl = asset.getString("browser_download_url")
                    )
                )
            }
        }
        result
    }

    // Download URL to a temp file in cacheDir, reporting 0-100 progress
    suspend fun downloadToTemp(
        url: String,
        onProgress: (String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "remote_dl_temp")
        val conn = openUrl(url)
        val total = conn.contentLengthLong
        var downloaded = 0L
        conn.inputStream.use { input ->
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(16384)
                var bytes: Int
                while (input.read(buffer).also { bytes = it } != -1) {
                    output.write(buffer, 0, bytes)
                    downloaded += bytes
                    val msg = if (total > 0) {
                        val pct = (downloaded * 100 / total).toInt()
                        val mb = downloaded / 1_048_576f
                        "Downloading... $pct% (${String.format("%.1f", mb)} MB)"
                    } else {
                        val mb = downloaded / 1_048_576f
                        "Downloading... ${String.format("%.1f", mb)} MB"
                    }
                    onProgress(msg)
                }
            }
        }
        tempFile
    }

    private fun openUrl(urlString: String): HttpURLConnection {
        var conn = URL(urlString).openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/json")
        conn.instanceFollowRedirects = true
        conn.connect()
        // Handle redirects manually in case of protocol change (http→https)
        val status = conn.responseCode
        if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
            status == HttpURLConnection.HTTP_MOVED_PERM ||
            status == 307 || status == 308
        ) {
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            conn = URL(location).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connect()
        }
        return conn
    }
}
