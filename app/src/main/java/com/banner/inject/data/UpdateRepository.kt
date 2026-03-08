package com.banner.inject.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object UpdateRepository {

    private const val RELEASES_URL =
        "https://api.github.com/repos/The412Banner/BannersComponentInjector/releases"

    data class ReleaseInfo(
        val tagName: String,       // e.g. "v1.2.6-pre"
        val versionName: String,   // e.g. "1.2.6-pre" (tag without leading "v")
        val htmlUrl: String,
        val isPreRelease: Boolean
    )

    /**
     * Fetches the latest release from GitHub.
     * @param includePreReleases if false, only stable (non-pre) releases are considered.
     * @return the newest matching [ReleaseInfo], or null if none found.
     */
    suspend fun fetchLatestRelease(includePreReleases: Boolean): ReleaseInfo? =
        withContext(Dispatchers.IO) {
            val conn = URL(RELEASES_URL).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connect()
            val json = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val release = array.getJSONObject(i)
                val isPreRelease = release.getBoolean("prerelease")
                if (!includePreReleases && isPreRelease) continue
                val tagName = release.getString("tag_name")
                return@withContext ReleaseInfo(
                    tagName = tagName,
                    versionName = tagName.removePrefix("v"),
                    htmlUrl = release.getString("html_url"),
                    isPreRelease = isPreRelease
                )
            }
            null
        }
}
