package com.banner.inject.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.banner.inject.data.RemoteSourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRepoDialog(
    onDismiss: () -> Unit,
    onAdd: (RemoteSourceRepository.RemoteSource) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var isDetecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isDetecting) onDismiss() },
        title = { Text("Add Custom Repository", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Repository Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL (JSON or GitHub Releases)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (isDetecting) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Detecting repository format...")
                    }
                }
                
                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && url.isNotBlank()) {
                        isDetecting = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val finalUrl = sanitizeUrl(url)
                                val format = autoDetectFormat(finalUrl)
                                isDetecting = false
                                onAdd(
                                    RemoteSourceRepository.RemoteSource(
                                        name = name.trim(),
                                        url = finalUrl,
                                        format = format,
                                        supportedTypes = emptyList(), // empty means all types supported
                                        isCustom = true
                                    )
                                )
                            } catch (e: Exception) {
                                isDetecting = false
                                errorMessage = "Failed to add repo: ${e.message}"
                            }
                        }
                    }
                },
                enabled = name.isNotBlank() && url.isNotBlank() && !isDetecting
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDetecting
            ) { Text("Cancel") }
        }
    )
}

private fun sanitizeUrl(input: String): String {
    var url = input.trim()
    // Auto-convert github.com/.../releases to api.github.com/repos/.../releases
    val githubReleasesRegex = Regex("""https://github\.com/([^/]+)/([^/]+)/releases.*""")
    val match = githubReleasesRegex.find(url)
    if (match != null) {
        val owner = match.groupValues[1]
        val repo = match.groupValues[2]
        url = "https://api.github.com/repos/$owner/$repo/releases"
    }
    return url
}

private suspend fun autoDetectFormat(url: String): RemoteSourceRepository.SourceFormat = withContext(Dispatchers.IO) {
    if (url.endsWith(".json", ignoreCase = true) || url.contains("raw.githubusercontent.com")) {
        return@withContext RemoteSourceRepository.SourceFormat.WCP_JSON
    }
    
    if (url.contains("api.github.com/repos")) {
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
    
    // Default fallback
    return@withContext RemoteSourceRepository.SourceFormat.GITHUB_RELEASES_ZIP
}
