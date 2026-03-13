package com.banner.inject.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class SteamGameInfo(
    val appId: String,
    val name: String,
    val genres: List<String>,
    val releaseYear: String?,
    val metacriticScore: Int?,          // null if not available
    val shortDescription: String?,
    val coverUrl: String,               // library_600x900.jpg portrait
    val headerUrl: String               // header.jpg fallback
)

object SteamRepository {

    // In-memory cache — survives config changes, cleared on process restart
    private val cache = ConcurrentHashMap<String, SteamGameInfo>()
    private val failed = ConcurrentHashMap<String, Boolean>()  // don't retry known-bad IDs

    // Disk cache — persists across restarts; null until init() is called
    private var prefs: SharedPreferences? = null

    /**
     * Must be called once with the application context before any fetch/getCached calls.
     * Safe to call multiple times (idempotent).
     */
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext
                .getSharedPreferences("steam_meta_cache", Context.MODE_PRIVATE)
        }
    }

    /** Returns the portrait cover URL for a given App ID (no network call needed). */
    fun coverUrl(appId: String) =
        "https://cdn.akamai.steamstatic.com/steam/apps/$appId/library_600x900.jpg"

    fun headerUrl(appId: String) =
        "https://cdn.akamai.steamstatic.com/steam/apps/$appId/header.jpg"

    /**
     * Returns cached info immediately — checks memory first, then disk.
     * Returns null if the app ID has never been successfully fetched.
     */
    fun getCached(appId: String): SteamGameInfo? =
        cache[appId] ?: readFromDisk(appId)?.also { cache[appId] = it }

    /** Fetches and caches info for [appId]. Returns null on failure. */
    suspend fun fetch(appId: String): SteamGameInfo? {
        // Memory hit
        cache[appId]?.let { return it }
        if (failed[appId] == true) return null

        // Disk hit — avoids network when offline after a previous successful fetch
        readFromDisk(appId)?.let {
            cache[appId] = it
            return it
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = "https://store.steampowered.com/api/appdetails?appids=$appId&filters=basic,genres,metacritic,release_date"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.connect()

                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val root = JSONObject(text)
                val entry = root.optJSONObject(appId) ?: run { failed[appId] = true; return@withContext null }
                if (!entry.optBoolean("success", false)) { failed[appId] = true; return@withContext null }

                val data = entry.optJSONObject("data") ?: run { failed[appId] = true; return@withContext null }

                val name = data.optString("name", appId).takeIf { it.isNotBlank() } ?: appId

                val genres = buildList {
                    val arr = data.optJSONArray("genres")
                    if (arr != null) {
                        for (i in 0 until minOf(arr.length(), 3)) {
                            arr.optJSONObject(i)?.optString("description")
                                ?.takeIf { it.isNotBlank() }
                                ?.let { add(it) }
                        }
                    }
                }

                val releaseYear = data.optJSONObject("release_date")
                    ?.optString("date", "")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { dateStr ->
                        Regex("\\b(19|20)\\d{2}\\b").find(dateStr)?.value
                    }

                val metacriticScore = data.optJSONObject("metacritic")
                    ?.optInt("score", -1)
                    ?.takeIf { it > 0 }

                val shortDesc = data.optString("short_description", "")
                    .takeIf { it.isNotBlank() }

                val info = SteamGameInfo(
                    appId = appId,
                    name = name,
                    genres = genres,
                    releaseYear = releaseYear,
                    metacriticScore = metacriticScore,
                    shortDescription = shortDesc,
                    coverUrl = coverUrl(appId),
                    headerUrl = headerUrl(appId)
                )
                cache[appId] = info
                writeToDisk(info)
                info
            } catch (_: Exception) {
                // Network error — don't mark as permanently failed so we can retry later
                null
            }
        }
    }

    fun clearCache() {
        cache.clear()
        failed.clear()
        prefs?.edit()?.clear()?.apply()
    }

    // ── Disk persistence ──────────────────────────────────────────────────────

    private fun writeToDisk(info: SteamGameInfo) {
        prefs?.edit()?.putString(diskKey(info.appId), info.toJson())?.apply()
    }

    private fun readFromDisk(appId: String): SteamGameInfo? =
        prefs?.getString(diskKey(appId), null)?.let { fromJson(it) }

    private fun diskKey(appId: String) = "meta_$appId"

    private fun SteamGameInfo.toJson(): String = JSONObject().apply {
        put("appId", appId)
        put("name", name)
        put("genres", JSONArray(genres))
        putOpt("releaseYear", releaseYear)
        putOpt("metacriticScore", metacriticScore)
        putOpt("shortDescription", shortDescription)
    }.toString()

    private fun fromJson(json: String): SteamGameInfo? = try {
        val obj = JSONObject(json)
        val appId = obj.getString("appId")
        val genres = buildList {
            val arr = obj.optJSONArray("genres")
            if (arr != null) for (i in 0 until arr.length()) add(arr.getString(i))
        }
        SteamGameInfo(
            appId = appId,
            name = obj.getString("name"),
            genres = genres,
            releaseYear = obj.optString("releaseYear", "").takeIf { it.isNotBlank() },
            metacriticScore = obj.optInt("metacriticScore", -1).takeIf { it > 0 },
            shortDescription = obj.optString("shortDescription", "").takeIf { it.isNotBlank() },
            coverUrl = coverUrl(appId),
            headerUrl = headerUrl(appId)
        )
    } catch (_: Exception) { null }

    // ── Search by name ────────────────────────────────────────────────────────

    data class SearchResult(val appId: String, val name: String)

    /** Searches the Steam Store for [query], returns up to 10 results. */
    suspend fun searchByName(query: String): List<SearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = "https://store.steampowered.com/api/storesearch/?term=$encoded&l=english&cc=US"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.connect()
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val root = JSONObject(text)
                val items = root.optJSONArray("items") ?: return@withContext emptyList()
                buildList {
                    for (i in 0 until minOf(items.length(), 10)) {
                        val item = items.optJSONObject(i) ?: continue
                        val id = item.optInt("id", -1).takeIf { it > 0 } ?: continue
                        val name = item.optString("name", "").takeIf { it.isNotBlank() } ?: continue
                        add(SearchResult(id.toString(), name))
                    }
                }
            } catch (_: Exception) { emptyList() }
        }
}
