package com.banner.inject.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banner.inject.data.UpdateRepository
import com.banner.inject.ui.theme.ThemePrefs
import kotlinx.coroutines.launch

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val release: UpdateRepository.ReleaseInfo) : UpdateState()
    object UpToDate : UpdateState()
    data class Error(val message: String) : UpdateState()
}

private fun Color.toHex(): String {
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return "#%02X%02X%02X".format(r, g, b)
}

private fun parseHex(hex: String): Color? {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6) return null
    return try {
        Color(
            red   = clean.substring(0, 2).toInt(16) / 255f,
            green = clean.substring(2, 4).toInt(16) / 255f,
            blue  = clean.substring(4, 6).toInt(16) / 255f
        )
    } catch (_: NumberFormatException) { null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    appVersion: String,
    accentColor: Color,
    onAccentColorChanged: (Color) -> Unit,
    onDismiss: () -> Unit,
    onOpenBackupManager: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("bci_settings", Context.MODE_PRIVATE) }
    val keyboard = LocalSoftwareKeyboardController.current

    var includePreReleases by remember {
        mutableStateOf(prefs.getBoolean("update_include_pre", false))
    }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }

    val isCustom = ThemePrefs.PRESETS.none { it.second == accentColor }
    var showCustomInput by remember(isCustom) { mutableStateOf(isCustom) }
    var hexInput by remember(accentColor) { mutableStateOf(accentColor.toHex()) }
    var hexError by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))

            // ── About ──────────────────────────────────────────────────────
            Text("About", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("BannersComponentInjector", fontSize = 14.sp)
                    Text("v$appVersion", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Appearance ─────────────────────────────────────────────────
            Text("Appearance", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("Accent Color", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(14.dp))

                    // Preset swatches — 2 rows of 4
                    val rows = ThemePrefs.PRESETS.chunked(4)
                    rows.forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { (name, color) ->
                                ColorSwatch(
                                    color = color,
                                    isSelected = accentColor == color,
                                    label = name,
                                    onClick = {
                                        onAccentColorChanged(color)
                                        ThemePrefs.save(context, color)
                                        showCustomInput = false
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    // Custom swatch
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ColorSwatch(
                            color = if (isCustom) accentColor else Color(0xFF707070),
                            isSelected = isCustom,
                            label = "Custom",
                            onClick = { showCustomInput = !showCustomInput }
                        )
                        // spacers to keep alignment with rows above (4-column grid)
                        repeat(3) { Spacer(Modifier.width(56.dp)) }
                    }

                    // Custom hex input
                    if (showCustomInput) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = hexInput,
                            onValueChange = { hexInput = it; hexError = false },
                            label = { Text("Hex color") },
                            placeholder = { Text("#FF6D00") },
                            singleLine = true,
                            isError = hexError,
                            supportingText = if (hexError) {{ Text("Enter a valid 6-digit hex e.g. #FF6D00") }} else null,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(parseHex(hexInput) ?: accentColor)
                                )
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val color = parseHex(hexInput)
                                if (color != null) {
                                    onAccentColorChanged(color)
                                    ThemePrefs.save(context, color)
                                    hexError = false
                                    keyboard?.hide()
                                } else {
                                    hexError = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Palette, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Apply Custom Color")
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Updates ────────────────────────────────────────────────────
            Text("Updates", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Include pre-releases", fontSize = 14.sp)
                            Text("Check for pre-release updates too", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = includePreReleases,
                            onCheckedChange = {
                                includePreReleases = it
                                prefs.edit().putBoolean("update_include_pre", it).apply()
                                updateState = UpdateState.Idle
                            }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            updateState = UpdateState.Checking
                            scope.launch {
                                updateState = try {
                                    val latest = UpdateRepository.fetchLatestRelease(includePreReleases)
                                    if (latest == null) UpdateState.Error("No releases found.")
                                    else if (latest.versionName == appVersion) UpdateState.UpToDate
                                    else UpdateState.Available(latest)
                                } catch (e: Exception) {
                                    UpdateState.Error(e.message ?: "Unknown error")
                                }
                            }
                        },
                        enabled = updateState !is UpdateState.Checking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (updateState is UpdateState.Checking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Checking...")
                        } else {
                            Icon(Icons.Default.SystemUpdate, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Check for Updates")
                        }
                    }
                    when (val s = updateState) {
                        is UpdateState.UpToDate -> {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("You're up to date (v$appVersion)", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        is UpdateState.Error -> {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(s.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        else -> {}
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onOpenBackupManager, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Backup, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Backup Manager")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { runCatching { context.startActivity(Intent("android.intent.action.VIEW_DOWNLOADS")) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Downloads Folder")
            }
        }
    }

    // Update available dialog
    val s = updateState
    if (s is UpdateState.Available) {
        AlertDialog(
            onDismissRequest = { updateState = UpdateState.Idle },
            icon = { Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Update Available") },
            text = {
                Column {
                    Text("A new version is available:")
                    Spacer(Modifier.height(8.dp))
                    Surface(color = MaterialTheme.colorScheme.background, shape = MaterialTheme.shapes.small) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Installed", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("v$appVersion", fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Available", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    s.release.tagName + if (s.release.isPreRelease) " (pre-release)" else "",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.release.htmlUrl))) }
                    updateState = UpdateState.Idle
                }) { Text("Open GitHub Release") }
            },
            dismissButton = {
                TextButton(onClick = { updateState = UpdateState.Idle }) { Text("Later") }
            }
        )
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                    else Modifier.border(1.dp, Color(0xFF444444), CircleShape)
                )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
