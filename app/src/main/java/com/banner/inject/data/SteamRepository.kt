package com.banner.inject.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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

    // In-memory cache — survives config changes via the singleton, cleared on process restart
    private val cache = ConcurrentHashMap<String, SteamGameInfo>()
    private val failed = ConcurrentHashMap<String, Boolean>()  // don't retry known-bad IDs

    /** Returns the portrait cover URL for a given App ID (no network call needed). */
    fun coverUrl(appId: String) =
        "https://cdn.akamai.steamstatic.com/steam/apps/$appId/library_600x900.jpg"

    fun headerUrl(appId: String) =
        "https://cdn.akamai.steamstatic.com/steam/apps/$appId/header.jpg"

    /** Returns cached info immediately, or null if not yet fetched. */
    fun getCached(appId: String): SteamGameInfo? = cache[appId]

    /** Fetches and caches info for [appId]. Returns null on failure. */
    suspend fun fetch(appId: String): SteamGameInfo? {
        cache[appId]?.let { return it }
        if (failed[appId] == true) return null

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
                        // Dates come as "21 Aug, 2012" or "2012" or "Q1 2024" etc.
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
    }
}
