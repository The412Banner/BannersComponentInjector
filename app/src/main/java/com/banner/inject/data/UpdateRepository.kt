package com.banner.inject.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateRepository {

    private const val RELEASES_URL =
        "https://api.github.com/repos/The412Banner/BannersComponentInjector/releases"

    data class ReleaseInfo(
        val tagName: String,       // e.g. "v1.2.6-pre"
        val versionName: String,   // e.g. "1.2.6-pre" (tag without leading "v")
        val htmlUrl: String,
        val isPreRelease: Boolean,
        val apkUrl: String?,       // direct download URL for the APK asset
        val body: String? = null   // release notes / changelog
    )

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
                // Parse assets to find the APK download URL
                val assets = release.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        if (asset.getString("name").endsWith(".apk")) {
                            apkUrl = asset.getString("browser_download_url")
                            break
                        }
                    }
                }
                return@withContext ReleaseInfo(
                    tagName = tagName,
                    versionName = tagName.removePrefix("v"),
                    htmlUrl = release.getString("html_url"),
                    isPreRelease = isPreRelease,
                    apkUrl = apkUrl,
                    body = release.optString("body", null)?.takeIf { it.isNotBlank() }
                )
            }
            null
        }

    /**
     * Downloads the APK from [apkUrl] into the app cache dir.
     * Calls [onProgress] with values 0..1 as download proceeds.
     * Properly cancellable — cancelling the parent coroutine stops the download.
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val destFile = File(context.cacheDir, "update.apk")
        val conn = URL(apkUrl).openConnection() as HttpURLConnection
        conn.connect()
        val totalBytes = conn.contentLengthLong
        var downloaded = 0L
        destFile.outputStream().use { out ->
            conn.inputStream.use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    ensureActive()   // allow coroutine cancellation
                    out.write(buffer, 0, read)
                    downloaded += read
                    if (totalBytes > 0) onProgress(downloaded.toFloat() / totalBytes)
                }
            }
        }
        conn.disconnect()
        destFile
    }

    /** Hands the downloaded APK to the system package installer via FileProvider. */
    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
