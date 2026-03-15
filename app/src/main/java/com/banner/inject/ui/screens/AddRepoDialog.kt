package com.banner.inject.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banner.inject.data.RemoteSourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRepoDialog(
    repo: RemoteSourceRepository,
    onDismiss: () -> Unit,
    onAdd: (RemoteSourceRepository.RemoteSource) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var urlEntries by remember { mutableStateOf(listOf("")) }
    var isDetecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isDetecting) onDismiss() },
        title = { Text("Add Custom Repository", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Repository Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                urlEntries.forEachIndexed { index, urlValue ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedTextField(
                            value = urlValue,
                            onValueChange = { newVal ->
                                urlEntries = urlEntries.toMutableList().also { it[index] = newVal }
                            },
                            label = { Text(if (index == 0) "URL" else "URL ${index + 1}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        if (urlEntries.size > 1) {
                            IconButton(
                                onClick = {
                                    urlEntries = urlEntries.toMutableList().also { it.removeAt(index) }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Remove,
                                    contentDescription = "Remove URL",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = { urlEntries = urlEntries + "" },
                    enabled = !isDetecting,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add another URL", fontSize = 13.sp)
                }

                if (isDetecting) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Detecting repository formats…")
                    }
                }

                if (errorMessage != null) {
                    Text(
                        errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val validUrls = urlEntries.filter { it.isNotBlank() }
                    if (name.isNotBlank() && validUrls.isNotEmpty()) {
                        isDetecting = true
                        errorMessage = null
                        scope.launch {
                            try {
                                if (validUrls.size == 1) {
                                    // Single URL — simple source, no type discovery needed
                                    val finalUrl = sanitizeUrl(validUrls.first())
                                    val format = autoDetectFormat(finalUrl)
                                    isDetecting = false
                                    onAdd(
                                        RemoteSourceRepository.RemoteSource(
                                            name = name.trim(),
                                            url = finalUrl,
                                            format = format,
                                            supportedTypes = emptyList(),
                                            isCustom = true
                                        )
                                    )
                                } else {
                                    // Multiple URLs — detect format for each, then discover types
                                    val detected = validUrls.map { raw ->
                                        val url = sanitizeUrl(raw)
                                        val format = autoDetectFormat(url)
                                        Pair(url, format)
                                    }
                                    // Discover types for each URL (network calls)
                                    val withTypes = detected.map { (url, format) ->
                                        val tempSource = RemoteSourceRepository.RemoteSource(
                                            name = name.trim(), url = url, format = format
                                        )
                                        val types = try {
                                            repo.discoverTypes(tempSource)
                                        } catch (_: Exception) {
                                            emptyList()
                                        }
                                        Triple(url, format, types)
                                    }
                                    // Primary = first URL; subsequent = extra endpoints
                                    // Each URL claims types it discovered not yet claimed by an earlier URL
                                    val claimed = mutableSetOf<String>()
                                    val (primaryUrl, primaryFormat, primaryDiscovered) = withTypes.first()
                                    val primaryTypes = primaryDiscovered.filter { it !in claimed }
                                        .also { claimed.addAll(it) }
                                    val extras = withTypes.drop(1).mapNotNull { (url, format, types) ->
                                        val myTypes = types.filter { it !in claimed }
                                        claimed.addAll(myTypes)
                                        if (myTypes.isEmpty()) null
                                        else RemoteSourceRepository.ExtraEndpoint(url, format, myTypes)
                                    }
                                    val allTypes = (primaryTypes + extras.flatMap { it.types }).distinct()
                                    isDetecting = false
                                    onAdd(
                                        RemoteSourceRepository.RemoteSource(
                                            name = name.trim(),
                                            url = primaryUrl,
                                            format = primaryFormat,
                                            supportedTypes = allTypes,
                                            isCustom = true,
                                            extraEndpoints = extras
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                isDetecting = false
                                errorMessage = "Failed to add repo: ${e.message}"
                            }
                        }
                    }
                },
                enabled = name.isNotBlank() && urlEntries.any { it.isNotBlank() } && !isDetecting
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDetecting) { Text("Cancel") }
        }
    )
}

private fun sanitizeUrl(input: String): String {
    val url = input.trim()
    // Auto-convert github.com/.../releases to api.github.com/repos/.../releases
    val githubReleasesRegex = Regex("""https://github\.com/([^/]+)/([^/]+)/releases.*""")
    val releasesMatch = githubReleasesRegex.find(url)
    if (releasesMatch != null) {
        val owner = releasesMatch.groupValues[1]
        val repoName = releasesMatch.groupValues[2]
        return "https://api.github.com/repos/$owner/$repoName/releases"
    }
    // Auto-convert plain github.com/{owner}/{repo} to api.github.com/repos/{owner}/{repo}/contents
    val plainRepoRegex = Regex("""https://github\.com/([^/]+)/([^/]+?)/?$""")
    val repoMatch = plainRepoRegex.find(url)
    if (repoMatch != null) {
        val owner = repoMatch.groupValues[1]
        val repoName = repoMatch.groupValues[2]
        return "https://api.github.com/repos/$owner/$repoName/contents"
    }
    return url
}

private suspend fun autoDetectFormat(url: String): RemoteSourceRepository.SourceFormat =
    withContext(Dispatchers.IO) {
        if (url.contains("t3st31.github.io") || url.contains("rankings.json", ignoreCase = true)) {
            return@withContext RemoteSourceRepository.SourceFormat.RANKING_EMULATORS_JSON
        }
        if (url.contains("gamenative", ignoreCase = true) && url.endsWith("manifest.json", ignoreCase = true)) {
            return@withContext RemoteSourceRepository.SourceFormat.GAMENATIVE_MANIFEST
        }
        if (url.endsWith(".json", ignoreCase = true) || url.contains("raw.githubusercontent.com")) {
            return@withContext RemoteSourceRepository.SourceFormat.WCP_JSON
        }
        if (url.contains("api.github.com/repos")) {
            if (url.endsWith("/contents") || url.contains("/contents/")) {
                return@withContext RemoteSourceRepository.SourceFormat.GITHUB_REPO_CONTENTS
            }
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.connect()
                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().readText()
                    if (json.contains(".wcp\"", ignoreCase = true)) {
                        return@withContext RemoteSourceRepository.SourceFormat.GITHUB_RELEASES_WCP
                    } else if (json.contains("turnip", ignoreCase = true)) {
                        return@withContext RemoteSourceRepository.SourceFormat.GITHUB_RELEASES_TURNIP
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        RemoteSourceRepository.SourceFormat.GITHUB_RELEASES_ZIP
    }
