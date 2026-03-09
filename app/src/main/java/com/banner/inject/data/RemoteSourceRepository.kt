package com.banner.inject.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class RemoteSourceRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("remote_sources_prefs", Context.MODE_PRIVATE)

    companion object {
        // In-memory cache: "sourceName::componentType" → items
        private val cache = ConcurrentHashMap<String, List<RemoteItem>>()

        fun clearCache() = cache.clear()
        fun hasCache(): Boolean = cache.isNotEmpty()
        fun getFromCache(sourceName: String, componentType: String): List<RemoteItem>? =
            cache["$sourceName::$componentType"]
        fun putToCache(sourceName: String, componentType: String, items: List<RemoteItem>) {
            cache["$sourceName::$componentType"] = items
        }

        // In-memory set of downloaded file keys for fast indicator lookups
        private val downloadedKeys = ConcurrentHashMap.newKeySet<String>()

        fun isDownloaded(sourceName: String, componentType: String, fileName: String): Boolean =
            downloadedKeys.contains("$sourceName::$componentType::$fileName")

        private fun markDownloaded(sourceName: String, componentType: String, fileName: String) {
            downloadedKeys.add("$sourceName::$componentType::$fileName")
        }
        private fun unmarkDownloaded(sourceName: String, componentType: String, fileName: String) {
            downloadedKeys.remove("$sourceName::$componentType::$fileName")
        }

        /** Strip filesystem-unsafe characters from a folder name segment. */
        fun sanitizeFolderName(name: String): String =
            name.replace(Regex("""[/\\:*?"<>|]"""), "_").trim()
    }

    data class DownloadedFile(
        val sourceName: String,
        val componentType: String,
        val fileName: String,
        val fileSizeBytes: Long = 0L,
        val downloadedAt: Long = 0L,
        val uriString: String? = null
    )

    init {
        // Hydrate the in-memory downloaded keys from persisted records on first instantiation
        getAllDownloads().forEach {
            markDownloaded(it.sourceName, it.componentType, it.fileName)
        }
    }

    fun recordDownload(
        sourceName: String, componentType: String, fileName: String,
        fileSizeBytes: Long = 0L, uriString: String? = null
    ) {
        val record = DownloadedFile(sourceName, componentType, fileName, fileSizeBytes,
            System.currentTimeMillis(), uriString)
        val current = getAllDownloads().toMutableList()
        current.removeAll { it.sourceName == sourceName && it.componentType == componentType && it.fileName == fileName }
        current.add(record)
        saveDownloadRecords(current)
        markDownloaded(sourceName, componentType, fileName)
    }

    fun removeDownloadRecord(record: DownloadedFile) {
        val current = getAllDownloads().toMutableList()
        current.removeAll { it.sourceName == record.sourceName && it.componentType == record.componentType && it.fileName == record.fileName }
        saveDownloadRecords(current)
        unmarkDownloaded(record.sourceName, record.componentType, record.fileName)
    }

    fun getAllDownloads(): List<DownloadedFile> {
        val jsonStr = prefs.getString("download_records", "[]") ?: "[]"
        val list = mutableListOf<DownloadedFile>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(DownloadedFile(
                    sourceName = obj.getString("sourceName"),
                    componentType = obj.getString("componentType"),
                    fileName = obj.getString("fileName"),
                    fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                    downloadedAt = obj.optLong("downloadedAt", 0L),
                    uriString = obj.optString("uriString").takeIf { it.isNotEmpty() }
                ))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    private fun saveDownloadRecords(records: List<DownloadedFile>) {
        val array = JSONArray()
        for (r in records) {
            val obj = JSONObject()
            obj.put("sourceName", r.sourceName)
            obj.put("componentType", r.componentType)
            obj.put("fileName", r.fileName)
            obj.put("fileSizeBytes", r.fileSizeBytes)
            obj.put("downloadedAt", r.downloadedAt)
            if (r.uriString != null) obj.put("uriString", r.uriString)
            array.put(obj)
        }
        prefs.edit().putString("download_records", array.toString()).apply()
    }

    /** Convert an API or raw URL to a human-readable browser URL. */
    fun getBrowseUrl(source: RemoteSource): String {
        val url = source.url
        if (url.contains("api.github.com/repos/")) {
            val path = url.substringAfter("api.github.com/repos/")
                .substringBefore("/releases").substringBefore("/contents")
            return "https://github.com/$path"
        }
        if (url.contains("raw.githubusercontent.com/")) {
            val parts = url.substringAfter("raw.githubusercontent.com/").split("/")
            if (parts.size >= 2) return "https://github.com/${parts[0]}/${parts[1]}"
        }
        return url
    }

    data class RemoteItem(
        val displayName: String,
        val versionName: String,
        val downloadUrl: String,
        val sourceName: String,
        val publishedAt: String? = null  // "YYYY-MM-DD" from GitHub releases; null for WCP JSON sources
    )

    data class RemoteSource(
        val name: String,
        val url: String,
        val format: SourceFormat,
        val supportedTypes: List<String> = emptyList(), // Empty implies all types
        val isCustom: Boolean = false
    )

    enum class SourceFormat {
        WCP_JSON,
        GITHUB_RELEASES_TURNIP,
        GITHUB_RELEASES_WCP,
        GITHUB_RELEASES_ZIP,         // All .zip assets from each release, no name filter
        GITHUB_REPO_CONTENTS         // GitHub Contents API — folders = types, files inside = components
    }

    // Default built-in sources mapped strictly to the components they provide
    private val defaultSources = listOf(
        RemoteSource("StevenMXZ", "https://raw.githubusercontent.com/StevenMXZ/Winlator-Contents/main/contents.json", SourceFormat.WCP_JSON, listOf("dxvk", "vkd3d", "box64", "fex", "fexcore", "wine", "proton")),
        RemoteSource("Arihany WCPHub", "https://api.github.com/repos/Arihany/WinlatorWCPHub/releases", SourceFormat.GITHUB_RELEASES_WCP, listOf("dxvk", "vkd3d", "box64", "fex", "fexcore", "wine", "proton")),
        RemoteSource("Arihany WCPHub (Turnip)", "https://api.github.com/repos/Arihany/WinlatorWCPHub/releases", SourceFormat.GITHUB_RELEASES_TURNIP, listOf("turnip", "adreno")),
        RemoteSource("Xnick417x", "https://raw.githubusercontent.com/Xnick417x/Winlator-Bionic-Nightly-wcp/refs/heads/main/content.json", SourceFormat.WCP_JSON, listOf("dxvk", "vkd3d", "box64", "fex", "fexcore", "wine", "proton")),
        RemoteSource("AdrenoToolsDrivers (K11MCH1)", "https://api.github.com/repos/K11MCH1/AdrenoToolsDrivers/releases", SourceFormat.GITHUB_RELEASES_TURNIP, listOf("turnip", "adreno")),
        RemoteSource("Adreno Tools Drivers (StevenMXZ)", "https://api.github.com/repos/StevenMXZ/Adreno-Tools-Drivers/releases", SourceFormat.GITHUB_RELEASES_ZIP, listOf("turnip", "adreno")),
        RemoteSource("freedreno Turnip CI (whitebelyash)", "https://api.github.com/repos/whitebelyash/freedreno_turnip-CI/releases", SourceFormat.GITHUB_RELEASES_TURNIP, listOf("turnip", "adreno")),
        RemoteSource("MaxesTechReview (MTR)", "https://github.com/maxjivi05/Components", SourceFormat.GITHUB_REPO_CONTENTS, emptyList())
    )

    fun getAllSources(): List<RemoteSource> {
        val removedDefaults = getRemovedDefaultSources()
        val filteredDefaults = defaultSources.filter { it.name !in removedDefaults }
        return filteredDefaults + getCustomSources()
    }

    private fun getRemovedDefaultSources(): List<String> {
        val jsonStr = prefs.getString("removed_defaults", "[]") ?: "[]"
        val removed = mutableListOf<String>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                removed.add(array.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return removed
    }

    private fun saveRemovedDefaultSources(removed: List<String>) {
        val array = JSONArray()
        for (name in removed) {
            array.put(name)
        }
        prefs.edit().putString("removed_defaults", array.toString()).apply()
    }

    private fun getCustomSources(): List<RemoteSource> {
        val jsonStr = prefs.getString("custom_sources", "[]") ?: "[]"
        val customSources = mutableListOf<RemoteSource>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val format = try {
                    SourceFormat.valueOf(obj.getString("format"))
                } catch (e: Exception) {
                    SourceFormat.WCP_JSON
                }
                
                val typesArray = obj.optJSONArray("supportedTypes")
                val typesList = mutableListOf<String>()
                if (typesArray != null) {
                    for (j in 0 until typesArray.length()) {
                        typesList.add(typesArray.getString(j))
                    }
                }
                
                customSources.add(
                    RemoteSource(
                        name = obj.getString("name"),
                        url = obj.getString("url"),
                        format = format,
                        supportedTypes = typesList,
                        isCustom = true
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return customSources
    }

    fun addCustomSource(source: RemoteSource) {
        val current = getCustomSources().toMutableList()
        current.add(source.copy(isCustom = true))
        saveCustomSources(current)
    }

    fun removeSource(source: RemoteSource) {
        if (source.isCustom) {
            val current = getCustomSources().toMutableList()
            current.removeAll { it.name == source.name && it.url == source.url }
            saveCustomSources(current)
        } else {
            val removed = getRemovedDefaultSources().toMutableList()
            if (!removed.contains(source.name)) {
                removed.add(source.name)
                saveRemovedDefaultSources(removed)
            }
        }
    }

    fun restoreDefaultSources() {
        prefs.edit().remove("removed_defaults").apply()
    }

    private fun saveCustomSources(sources: List<RemoteSource>) {
        val array = JSONArray()
        for (source in sources) {
            val obj = JSONObject()
            obj.put("name", source.name)
            obj.put("url", source.url)
            obj.put("format", source.format.name)
            
            val typesArray = JSONArray()
            for (type in source.supportedTypes) {
                typesArray.put(type)
            }
            obj.put("supportedTypes", typesArray)
            array.put(obj)
        }
        prefs.edit().putString("custom_sources", array.toString()).apply()
    }

    suspend fun fetchFromSource(
        source: RemoteSource,
        componentType: String
    ): List<RemoteItem> = withContext(Dispatchers.IO) {
        getFromCache(source.name, componentType)?.let { return@withContext it }
        val result = when (source.format) {
            SourceFormat.WCP_JSON -> fetchWcpJson(source.url, componentType, source.name)
            SourceFormat.GITHUB_RELEASES_TURNIP -> fetchTurnipReleases(source.url, source.name)
            SourceFormat.GITHUB_RELEASES_WCP -> fetchGithubReleasesWcp(source.url, componentType, source.name)
            SourceFormat.GITHUB_RELEASES_ZIP -> fetchGithubReleasesZip(source.url, source.name)
            SourceFormat.GITHUB_REPO_CONTENTS -> fetchGithubRepoContents(source.url, componentType, source.name)
        }
        putToCache(source.name, componentType, result)
        result
    }

    /**
     * Refresh the entire cache by fetching all sources × all relevant component types in parallel.
     * Formats that don't filter by type (TURNIP, ZIP) are fetched once and cached under all
     * their supported types to avoid redundant API hits.
     */
    suspend fun refreshAllCache(sources: List<RemoteSource>, allTypes: List<String>) {
        clearCache()
        coroutineScope {
            sources.forEach { source ->
                launch(Dispatchers.IO) {
                    try {
                        when (source.format) {
                            SourceFormat.GITHUB_RELEASES_TURNIP -> {
                                val items = fetchTurnipReleases(source.url, source.name)
                                val types = if (source.supportedTypes.isNotEmpty()) source.supportedTypes else allTypes
                                types.forEach { putToCache(source.name, it, items) }
                            }
                            SourceFormat.GITHUB_RELEASES_ZIP -> {
                                val items = fetchGithubReleasesZip(source.url, source.name)
                                val types = if (source.supportedTypes.isNotEmpty()) source.supportedTypes else allTypes
                                types.forEach { putToCache(source.name, it, items) }
                            }
                            SourceFormat.WCP_JSON -> {
                                val types = if (source.supportedTypes.isNotEmpty()) source.supportedTypes else allTypes
                                types.forEach { type ->
                                    val items = fetchWcpJson(source.url, type, source.name)
                                    putToCache(source.name, type, items)
                                }
                            }
                            SourceFormat.GITHUB_RELEASES_WCP -> {
                                val types = if (source.supportedTypes.isNotEmpty()) source.supportedTypes else allTypes
                                types.forEach { type ->
                                    val items = fetchGithubReleasesWcp(source.url, type, source.name)
                                    putToCache(source.name, type, items)
                                }
                            }
                            SourceFormat.GITHUB_REPO_CONTENTS -> {
                                val folders = discoverTypes(source)
                                folders.forEach { folder ->
                                    val items = fetchGithubRepoContents(source.url, folder, source.name)
                                    putToCache(source.name, folder, items)
                                }
                            }
                        }
                    } catch (_: Exception) { /* skip failed sources silently */ }
                }
            }
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
            val publishedAt = release.optString("published_at").substringBefore("T").takeIf { it.isNotEmpty() }
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
                        sourceName = sourceName,
                        publishedAt = publishedAt
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
            val publishedAt = release.optString("published_at").substringBefore("T").takeIf { it.isNotEmpty() }
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
                            sourceName = sourceName,
                            publishedAt = publishedAt
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
            val publishedAt = release.optString("published_at").substringBefore("T").takeIf { it.isNotEmpty() }
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
                        sourceName = sourceName,
                        publishedAt = publishedAt
                    )
                )
            }
        }
        result
    }

    /**
     * Fetches the repository and returns all component type names it actually contains.
     * For WCP_JSON: extracts unique "type" field values from the JSON entries.
     * For GITHUB_RELEASES_WCP/ZIP: scans asset names and matches against known types.
     * For GITHUB_RELEASES_TURNIP: always returns ["turnip", "adreno"].
     * Falls back to the source's existing supportedTypes (or all known types) on error.
     */
    suspend fun discoverTypes(source: RemoteSource): List<String> = withContext(Dispatchers.IO) {
        val knownTypes = listOf("dxvk", "vkd3d", "box64", "fex", "fexcore", "wined3d", "turnip", "adreno", "drivers", "wine", "proton")
        fun sortByKnown(set: Set<String>) = set.sortedBy { t -> knownTypes.indexOf(t).let { if (it == -1) Int.MAX_VALUE else it } }
        try {
            when (source.format) {
                SourceFormat.WCP_JSON -> {
                    val conn = URL(source.url).openConnection() as java.net.HttpURLConnection
                    conn.setRequestProperty("Accept", "application/json")
                    conn.connectTimeout = 8000; conn.readTimeout = 8000; conn.connect()
                    val array = JSONArray(conn.inputStream.bufferedReader().readText())
                    val types = mutableSetOf<String>()
                    for (i in 0 until array.length()) {
                        val t = array.getJSONObject(i).optString("type").lowercase().trim()
                        if (t.isNotEmpty()) types.add(t)
                    }
                    sortByKnown(types)
                }
                SourceFormat.GITHUB_RELEASES_TURNIP -> listOf("turnip", "adreno")
                SourceFormat.GITHUB_RELEASES_WCP -> {
                    val conn = URL(source.url).openConnection() as java.net.HttpURLConnection
                    conn.setRequestProperty("Accept", "application/json")
                    conn.connectTimeout = 8000; conn.readTimeout = 8000; conn.connect()
                    val array = JSONArray(conn.inputStream.bufferedReader().readText())
                    val found = mutableSetOf<String>()
                    for (i in 0 until array.length()) {
                        val assets = array.getJSONObject(i).optJSONArray("assets") ?: continue
                        for (j in 0 until assets.length()) {
                            val name = assets.getJSONObject(j).optString("name").lowercase()
                            if (!name.endsWith(".wcp")) continue
                            knownTypes.forEach { if (name.contains(it)) found.add(it) }
                        }
                    }
                    sortByKnown(found)
                }
                SourceFormat.GITHUB_RELEASES_ZIP -> {
                    val conn = URL(source.url).openConnection() as java.net.HttpURLConnection
                    conn.setRequestProperty("Accept", "application/json")
                    conn.connectTimeout = 8000; conn.readTimeout = 8000; conn.connect()
                    val array = JSONArray(conn.inputStream.bufferedReader().readText())
                    val found = mutableSetOf<String>()
                    for (i in 0 until array.length()) {
                        val assets = array.getJSONObject(i).optJSONArray("assets") ?: continue
                        for (j in 0 until assets.length()) {
                            val name = assets.getJSONObject(j).optString("name").lowercase()
                            if (!name.endsWith(".zip")) continue
                            knownTypes.forEach { if (name.contains(it)) found.add(it) }
                        }
                    }
                    // ZIP repos often have generic filenames; fall back to full known list if nothing matched
                    if (found.isEmpty()) knownTypes else sortByKnown(found)
                }
                SourceFormat.GITHUB_REPO_CONTENTS -> {
                    // Return the actual folder names from the repo root (case-preserved for API calls)
                    val conn = URL(normalizeContentsUrl(source.url)).openConnection() as java.net.HttpURLConnection
                    conn.setRequestProperty("Accept", "application/json")
                    conn.connectTimeout = 8000; conn.readTimeout = 8000; conn.connect()
                    val array = JSONArray(conn.inputStream.bufferedReader().readText())
                    val folders = mutableListOf<String>()
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        if (item.getString("type") == "dir" && !item.getString("name").startsWith(".")) {
                            folders.add(item.getString("name")) // preserve original casing
                        }
                    }
                    folders
                }
            }
        } catch (_: Exception) {
            if (source.supportedTypes.isNotEmpty()) source.supportedTypes else knownTypes
        }
    }

    /**
     * Edit an existing source in-place.
     * Custom sources are updated directly. Built-in defaults are removed from the
     * active list and the edited version is saved as a new custom source.
     */
    fun editSource(oldSource: RemoteSource, newSource: RemoteSource) {
        if (oldSource.isCustom) {
            val current = getCustomSources().toMutableList()
            val idx = current.indexOfFirst { it.name == oldSource.name && it.url == oldSource.url }
            if (idx >= 0) current[idx] = newSource.copy(isCustom = true) else current.add(newSource.copy(isCustom = true))
            saveCustomSources(current)
        } else {
            removeSource(oldSource)   // marks as removed from defaults
            addCustomSource(newSource)
        }
    }

    /** Convert plain https://github.com/{owner}/{repo} to the GitHub Contents API URL. */
    private fun normalizeContentsUrl(url: String): String {
        val match = Regex("""https://github\.com/([^/]+)/([^/]+?)/?$""").find(url) ?: return url
        return "https://api.github.com/repos/${match.groupValues[1]}/${match.groupValues[2]}/contents"
    }

    private suspend fun fetchGithubRepoContents(
        url: String,
        folderName: String,
        sourceName: String
    ): List<RemoteItem> = withContext(Dispatchers.IO) {
        val apiUrl = normalizeContentsUrl(url)
        val folderUrl = "$apiUrl/$folderName"
        val json = openUrl(folderUrl).inputStream.bufferedReader().readText()
        val array = JSONArray(json)
        val result = mutableListOf<RemoteItem>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            if (item.getString("type") != "file") continue
            val name = item.getString("name")
            if (!name.endsWith(".wcp", ignoreCase = true) && !name.endsWith(".zip", ignoreCase = true)) continue
            val downloadUrl = item.optString("download_url").takeIf { it.isNotEmpty() } ?: continue
            result.add(RemoteItem(
                displayName = name.substringBeforeLast("."),
                versionName = name.substringBeforeLast("."),
                downloadUrl = downloadUrl,
                sourceName = sourceName,
                publishedAt = null
            ))
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