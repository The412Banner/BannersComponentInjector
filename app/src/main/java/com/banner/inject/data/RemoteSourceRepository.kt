package com.banner.inject.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class RemoteSourceRepository(private val context: Context) {

    data class RemoteItem(
        val displayName: String,
        val versionName: String,
        val downloadUrl: String,
        val sourceName: String
    )

    data class RemoteSource(
        val name: String,
        val url: String,
        val format: SourceFormat,
        val supportedTypes: List<String> = emptyList() // Empty implies all types
    )

    enum class SourceFormat {
        WCP_JSON,
        GITHUB_RELEASES_TURNIP,
        GITHUB_RELEASES_WCP,
        GITHUB_RELEASES_ZIP   // All .zip assets from each release, no name filter
    }

    // Default built-in sources mapped strictly to the components they provide
    val defaultSources = listOf(
        RemoteSource("StevenMXZ", "https://raw.githubusercontent.com/StevenMXZ/Winlator-Contents/main/contents.json", SourceFormat.WCP_JSON, listOf("dxvk", "vkd3d", "box64", "fex", "fexcore")),
        RemoteSource("Arihany", "https://raw.githubusercontent.com/arihany/wcp-json/main/wcp.json", SourceFormat.WCP_JSON, listOf("dxvk", "vkd3d", "box64", "fex", "fexcore")),
        RemoteSource("Xnick417x", "https://raw.githubusercontent.com/Xnick417x/Winlator-Bionic-Nightly-wcp/refs/heads/main/content.json", SourceFormat.WCP_JSON, listOf("dxvk", "vkd3d", "box64", "fex", "fexcore")),
        RemoteSource("AdrenoToolsDrivers (K11MCH1)", "https://api.github.com/repos/K11MCH1/AdrenoToolsDrivers/releases", SourceFormat.GITHUB_RELEASES_TURNIP, listOf("turnip", "adreno")),
        RemoteSource("Adreno Tools Drivers (StevenMXZ)", "https://api.github.com/repos/StevenMXZ/Adreno-Tools-Drivers/releases", SourceFormat.GITHUB_RELEASES_ZIP, listOf("turnip", "adreno")),
        RemoteSource("freedreno Turnip CI (whitebelyash)", "https://api.github.com/repos/whitebelyash/freedreno_turnip-CI/releases", SourceFormat.GITHUB_RELEASES_TURNIP, listOf("turnip", "adreno"))
    )

    suspend fun fetchFromSource(
        source: RemoteSource,
        componentType: String
    ): List<RemoteItem> = withContext(Dispatchers.IO) {
        when (source.format) {
            SourceFormat.WCP_JSON -> fetchWcpJson(source.url, componentType, source.name)
            SourceFormat.GITHUB_RELEASES_TURNIP -> fetchTurnipReleases(source.url, source.name)
            SourceFormat.GITHUB_RELEASES_WCP -> fetchGithubReleasesWcp(source.url, componentType, source.name)
            SourceFormat.GITHUB_RELEASES_ZIP -> fetchGithubReleasesZip(source.url, source.name)
        }
    }

    private suspend fun fetchWcpJson(
        jsonUrl: String,
        componentType: String,
        sourceName: String
    ): List<RemoteItem> = withContext(Dispatchers.IO) {
        val json = openUrl(jsonUrl).inputStream.bufferedReader().readText()
        val array = JSONArray(json)
        val all = mutableListOf<RemoteItem>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val type = obj.getString("type")
            val verName = obj.getString("verName")
            val url = obj.getString("remoteUrl")
            all.add(RemoteItem(displayName = "$type  $verName", versionName = verName, downloadUrl = url, sourceName = sourceName))
        }
        // Ensure we only return items strictly matching the requested component folder
        val term = if (componentType.equals("fex", ignoreCase = true)) "fex" else componentType
        val filtered = all.filter { it.displayName.contains(term, ignoreCase = true) }
        filtered.reversed()
    }

    private suspend fun fetchTurnipReleases(url: String, sourceName: String): List<RemoteItem> = withContext(Dispatchers.IO) {
        val json = openUrl(url).inputStream.bufferedReader().readText()
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
                        downloadUrl = asset.getString("browser_download_url"),
                        sourceName = sourceName
                    )
                )
            }
        }
        result
    }

    private suspend fun fetchGithubReleasesWcp(url: String, componentType: String, sourceName: String): List<RemoteItem> = withContext(Dispatchers.IO) {
        val json = openUrl(url).inputStream.bufferedReader().readText()
        val array = JSONArray(json)
        val result = mutableListOf<RemoteItem>()
        for (i in 0 until array.length()) {
            val release = array.getJSONObject(i)
            val releaseName = release.getString("name").trim()
            val assets = release.getJSONArray("assets")
            val wcpAssets = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .filter { it.getString("name").endsWith(".wcp", ignoreCase = true) }
            for (asset in wcpAssets) {
                val assetName = asset.getString("name")
                if (assetName.contains(componentType, ignoreCase = true) || componentType.isEmpty()) {
                    val displayName = if (wcpAssets.size > 1) "$releaseName — $assetName" else releaseName
                    result.add(
                        RemoteItem(
                            displayName = displayName,
                            versionName = assetName.removeSuffix(".wcp"),
                            downloadUrl = asset.getString("browser_download_url"),
                            sourceName = sourceName
                        )
                    )
                }
            }
        }
        result
    }

    private suspend fun fetchGithubReleasesZip(url: String, sourceName: String): List<RemoteItem> = withContext(Dispatchers.IO) {
        val json = openUrl(url).inputStream.bufferedReader().readText()
        val array = JSONArray(json)
        val result = mutableListOf<RemoteItem>()
        for (i in 0 until array.length()) {
            val release = array.getJSONObject(i)
            val releaseName = release.getString("name").trim()
            val assets = release.getJSONArray("assets")
            val zipAssets = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .filter { it.getString("name").endsWith(".zip", ignoreCase = true) }
            for (asset in zipAssets) {
                val assetName = asset.getString("name")
                val displayName = if (zipAssets.size > 1) "$releaseName — ${assetName.removeSuffix(".zip")}" else releaseName
                result.add(
                    RemoteItem(
                        displayName = displayName,
                        versionName = assetName.removeSuffix(".zip"),
                        downloadUrl = asset.getString("browser_download_url"),
                        sourceName = sourceName
                    )
                )
            }
        }
        result
    }

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