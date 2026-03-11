package com.banner.inject.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banner.inject.data.UpdateRepository
import com.banner.inject.ui.theme.ThemePrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val release: UpdateRepository.ReleaseInfo) : UpdateState()
    object UpToDate : UpdateState()
    data class Error(val message: String) : UpdateState()
    data class Downloading(val progress: Float, val release: UpdateRepository.ReleaseInfo) : UpdateState()
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

enum class SettingsPage {
    MAIN, APPEARANCE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    appVersion: String,
    accentColor: Color,
    onAccentColorChanged: (Color) -> Unit,
    onDismiss: () -> Unit,
    onOpenBackupManager: () -> Unit,
    isDarkMode: Boolean = true,
    onDarkModeChanged: (Boolean) -> Unit = {},
    isAmoled: Boolean = false,
    onAmoledChanged: (Boolean) -> Unit = {},
    isDynamicColor: Boolean = false,
    onDynamicColorChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("bci_settings", Context.MODE_PRIVATE) }

    var currentPage by remember { mutableStateOf(SettingsPage.MAIN) }

    var includePreReleases by remember {
        mutableStateOf(prefs.getBoolean("update_include_pre", false))
    }
    var checkOnLaunch by remember {
        mutableStateOf(prefs.getBoolean("update_check_on_launch", false))
    }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var warnBeforeReplace by remember {
        mutableStateOf(!prefs.getBoolean("skip_backup_warning", false))
    }
    
    var defaultStartTab by remember {
        mutableStateOf(prefs.getString("default_start_tab", "INJECT") ?: "INJECT")
    }

    var customDownloadsUri by remember {
        mutableStateOf(prefs.getString("custom_downloads_uri", null))
    }
    var customBackupsUri by remember {
        mutableStateOf(prefs.getString("custom_backups_uri", null))
    }

    val downloadsFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            customDownloadsUri = uri.toString()
            prefs.edit().putString("custom_downloads_uri", uri.toString()).apply()
        }
    }
    val backupsFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            customBackupsUri = uri.toString()
            prefs.edit().putString("custom_backups_uri", uri.toString()).apply()
        }
    }

    val isCustom = ThemePrefs.PRESETS.none { it.second == accentColor }
    var showCustomInput by remember(isCustom) { mutableStateOf(isCustom) }

    // Intercept device back button
    BackHandler(onBack = {
        if (currentPage == SettingsPage.APPEARANCE) {
            currentPage = SettingsPage.MAIN
        } else {
            onDismiss()
        }
    })

    // Full-screen overlay — drawn on top of the calling screen
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (currentPage == SettingsPage.MAIN) {
                                Icon(Icons.Default.Settings, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            } else {
                                Icon(Icons.Default.Palette, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (currentPage == SettingsPage.APPEARANCE) {
                                currentPage = SettingsPage.MAIN
                            } else {
                                onDismiss()
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        ) { padding ->
            if (currentPage == SettingsPage.MAIN) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {

                    // ── About ────────────────────────────────────────────────
                    item {
                        SectionLabel("About")
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
                    }

                    // ── General ──────────────────────────────────────────────
                    item {
                        SectionLabel("General")
                        Spacer(Modifier.height(8.dp))
                        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                Text("Default Start Tab", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(8.dp))
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    SegmentedButton(
                                        selected = defaultStartTab == "INJECT",
                                        onClick = {
                                            defaultStartTab = "INJECT"
                                            prefs.edit().putString("default_start_tab", "INJECT").apply()
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                    ) {
                                        Text("Inject Components", fontSize = 12.sp)
                                    }
                                    SegmentedButton(
                                        selected = defaultStartTab == "DOWNLOAD",
                                        onClick = {
                                            defaultStartTab = "DOWNLOAD"
                                            prefs.edit().putString("default_start_tab", "DOWNLOAD").apply()
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                    ) {
                                        Text("Download Components", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // ── Storage ──────────────────────────────────────────────
                    item {
                        SectionLabel("Storage")
                        Spacer(Modifier.height(8.dp))
                        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                StorageLocationRow(
                                    label = "Downloads Location",
                                    defaultLabel = "Default  (Downloads/BannersComponentInjector/…)",
                                    customUri = customDownloadsUri,
                                    context = context,
                                    onSelect = { downloadsFolderLauncher.launch(null) },
                                    onReset = {
                                        customDownloadsUri = null
                                        prefs.edit().remove("custom_downloads_uri").apply()
                                    }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                                StorageLocationRow(
                                    label = "Backups Location",
                                    defaultLabel = "Default  (Downloads/BannersComponentInjector/…)",
                                    customUri = customBackupsUri,
                                    context = context,
                                    onSelect = { backupsFolderLauncher.launch(null) },
                                    onReset = {
                                        customBackupsUri = null
                                        prefs.edit().remove("custom_backups_uri").apply()
                                    }
                                )
                            }
                        }
                    }

                    // ── Appearance Menu ──────────────────────────────────────
                    item {
                        SectionLabel("Appearance")
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().clickable { currentPage = SettingsPage.APPEARANCE }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(accentColor)
                                            .border(1.dp, Color(0xFF444444), CircleShape)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text("Theme & Accent Color", fontSize = 14.sp)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // ── Prompts ──────────────────────────────────────────────
                    item {
                        SectionLabel("Prompts")
                        Spacer(Modifier.height(8.dp))
                        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Backup warning", fontSize = 14.sp)
                                    Text("Warn before replacing without a backup", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = warnBeforeReplace,
                                    onCheckedChange = {
                                        warnBeforeReplace = it
                                        prefs.edit().putBoolean("skip_backup_warning", !it).apply()
                                    }
                                )
                            }
                        }
                    }

                    // ── Updates ──────────────────────────────────────────────
                    item {
                        SectionLabel("Updates")
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
                                            if (updateState !is UpdateState.Downloading) {
                                                updateState = UpdateState.Idle
                                            }
                                        }
                                    )
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Check for updates on launch", fontSize = 14.sp)
                                        Text("Automatically check when the app opens", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = checkOnLaunch,
                                        onCheckedChange = {
                                            checkOnLaunch = it
                                            prefs.edit().putBoolean("update_check_on_launch", it).apply()
                                        }
                                    )
                                }
                                Spacer(Modifier.height(12.dp))

                                // Check for updates button — hidden while downloading
                                if (updateState !is UpdateState.Downloading) {
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
                                }

                                // State-specific UI
                                when (val s = updateState) {
                                    is UpdateState.UpToDate -> {
                                        Spacer(Modifier.height(10.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("You're up to date (v$appVersion)", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }

                                    is UpdateState.Error -> {
                                        Spacer(Modifier.height(10.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(s.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }

                                    is UpdateState.Available -> {
                                        Spacer(Modifier.height(12.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.NewReleases, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(8.dp))
                                                    Column {
                                                        Text("Update available", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                                        Text(
                                                            "${s.release.tagName}${if (s.release.isPreRelease) " (pre-release)" else ""}",
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text("Installed: v$appVersion", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))

                                                if (!s.release.body.isNullOrBlank()) {
                                                    Spacer(Modifier.height(10.dp))
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                                                    Spacer(Modifier.height(8.dp))
                                                    Text(
                                                        "What's new",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                                    )
                                                    Spacer(Modifier.height(4.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(max = 150.dp)
                                                            .verticalScroll(rememberScrollState())
                                                    ) {
                                                        Text(
                                                            s.release.body,
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                                            lineHeight = 16.sp
                                                        )
                                                    }
                                                }

                                                Spacer(Modifier.height(12.dp))

                                                if (s.release.apkUrl != null) {
                                                    Button(
                                                        onClick = {
                                                            val release = s.release
                                                            updateState = UpdateState.Downloading(0f, release)
                                                            val job = scope.launch {
                                                                try {
                                                                    val file = UpdateRepository.downloadApk(context, release.apkUrl) { progress ->
                                                                        updateState = UpdateState.Downloading(progress, release)
                                                                    }
                                                                    UpdateRepository.installApk(context, file)
                                                                    updateState = UpdateState.Idle
                                                                } catch (e: kotlinx.coroutines.CancellationException) {
                                                                    updateState = UpdateState.Available(release)
                                                                } catch (e: Exception) {
                                                                    updateState = UpdateState.Error("Download failed: ${e.message ?: "Unknown error"}")
                                                                }
                                                            }
                                                            downloadJob = job
                                                        },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(18.dp))
                                                        Spacer(Modifier.width(8.dp))
                                                        Text("Download & Install")
                                                    }
                                                    Spacer(Modifier.height(6.dp))
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        runCatching {
                                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.release.htmlUrl)))
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("View on GitHub")
                                                }
                                            }
                                        }
                                    }

                                    is UpdateState.Downloading -> {
                                        Spacer(Modifier.height(12.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = MaterialTheme.shapes.medium
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Spacer(Modifier.width(10.dp))
                                                    Text(
                                                        if (s.progress > 0f) "Downloading… ${(s.progress * 100).toInt()}%"
                                                        else "Downloading…",
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                                Spacer(Modifier.height(8.dp))
                                                LinearProgressIndicator(
                                                    progress = { s.progress },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                TextButton(
                                                    onClick = {
                                                        downloadJob?.cancel()
                                                        downloadJob = null
                                                    },
                                                    modifier = Modifier.align(Alignment.End)
                                                ) {
                                                    Text("Cancel", color = MaterialTheme.colorScheme.onPrimaryContainer)
                                                }
                                            }
                                        }
                                    }

                                    else -> {}
                                }
                            }
                        }
                    }

                    // ── Utilities ────────────────────────────────────────────
                    item {
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
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/The412Banner/BannersComponentInjector/issues"))
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.BugReport, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Report Issue / Feedback")
                        }
                    }
                }
            } else {
                // ── Appearance Page ──────────────────────────────────────────
                val isAndroid12Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ── Display Mode card ────────────────────────────────────
                    item {
                        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                Text("Display Mode", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(12.dp))

                                // Dark mode toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Dark Mode", fontSize = 14.sp)
                                        Text(
                                            "Dark background for low-light use",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = isDarkMode,
                                        onCheckedChange = { value ->
                                            onDarkModeChanged(value)
                                            ThemePrefs.saveDarkMode(context, value)
                                            // Disable AMOLED when switching to light
                                            if (!value && isAmoled) {
                                                onAmoledChanged(false)
                                                ThemePrefs.saveAmoled(context, false)
                                            }
                                        }
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.outline
                                )

                                // AMOLED checkbox — indented, dimmed when dark mode is off
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (!isDarkMode) Modifier else Modifier),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.Contrast,
                                        contentDescription = null,
                                        tint = if (isDarkMode) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "AMOLED Black",
                                            fontSize = 14.sp,
                                            color = if (isDarkMode) MaterialTheme.colorScheme.onSurface
                                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                        Text(
                                            "True black background for OLED screens",
                                            fontSize = 11.sp,
                                            color = if (isDarkMode) MaterialTheme.colorScheme.onSurfaceVariant
                                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                    }
                                    Checkbox(
                                        checked = isAmoled,
                                        onCheckedChange = { value ->
                                            if (isDarkMode) {
                                                onAmoledChanged(value)
                                                ThemePrefs.saveAmoled(context, value)
                                            }
                                        },
                                        enabled = isDarkMode
                                    )
                                }
                            }
                        }
                    }

                    // ── Material You card ────────────────────────────────────
                    item {
                        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (isAndroid12Plus) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Dynamic Color",
                                            fontSize = 14.sp,
                                            color = if (isAndroid12Plus) MaterialTheme.colorScheme.onSurface
                                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                        Text(
                                            if (isAndroid12Plus) "Use colors from your wallpaper (Material You)"
                                            else "Requires Android 12+",
                                            fontSize = 11.sp,
                                            color = if (isAndroid12Plus) MaterialTheme.colorScheme.onSurfaceVariant
                                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                    }
                                    Switch(
                                        checked = isDynamicColor,
                                        enabled = isAndroid12Plus,
                                        onCheckedChange = { value ->
                                            onDynamicColorChanged(value)
                                            ThemePrefs.saveDynamicColor(context, value)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // ── Accent Color card (hidden when Dynamic Color is on) ──
                    if (!isDynamicColor) {
                        item {
                            Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                    Text("Accent Color", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.height(14.dp))

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

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        ColorSwatch(
                                            color = if (isCustom) accentColor else Color(0xFF707070),
                                            isSelected = isCustom,
                                            label = "Custom",
                                            onClick = { showCustomInput = !showCustomInput }
                                        )
                                        repeat(3) { Spacer(Modifier.width(56.dp)) }
                                    }

                                    if (showCustomInput) {
                                        Spacer(Modifier.height(12.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                        Spacer(Modifier.height(12.dp))
                                        HsvColorWheelPicker(
                                            color = accentColor,
                                            onColorChanged = { color -> onAccentColorChanged(color) },
                                            onApply = { color ->
                                                onAccentColorChanged(color)
                                                ThemePrefs.save(context, color)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageLocationRow(
    label: String,
    defaultLabel: String,
    customUri: String?,
    context: Context,
    onSelect: () -> Unit,
    onReset: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                val pathText = if (customUri != null) {
                    val doc = runCatching {
                        DocumentFile.fromTreeUri(context, Uri.parse(customUri))
                    }.getOrNull()
                    val name = doc?.name
                    val segment = Uri.parse(customUri).lastPathSegment?.removePrefix("primary:")
                    name ?: segment ?: "Custom folder"
                } else {
                    defaultLabel
                }
                Text(
                    pathText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSelect, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (customUri != null) "Change" else "Select Folder", fontSize = 12.sp)
            }
            if (customUri != null) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Restore, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Use Default", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun HsvColorWheelPicker(
    color: Color,
    onColorChanged: (Color) -> Unit,
    onApply: (Color) -> Unit
) {
    val context = LocalContext.current

    val hueState = remember { mutableStateOf(0f) }
    val satState = remember { mutableStateOf(0f) }
    val briState = remember { mutableStateOf(1f) }

    LaunchedEffect(color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hueState.value = hsv[0]
        satState.value = hsv[1]
        briState.value = hsv[2]
    }

    fun colorFromHsv(): Color = Color(
        android.graphics.Color.HSVToColor(floatArrayOf(hueState.value, satState.value, briState.value))
    )

    val density = LocalDensity.current
    val wheelDp = 240.dp
    val wheelPx = with(density) { wheelDp.toPx() }

    fun updateFromOffset(offset: Offset) {
        val cx = wheelPx / 2f
        val cy = wheelPx / 2f
        val dx = offset.x - cx
        val dy = offset.y - cy
        hueState.value = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
        satState.value = (sqrt(dx * dx + dy * dy) / (wheelPx / 2f)).coerceIn(0f, 1f)
        onColorChanged(colorFromHsv())
    }

    var hexInput by remember(color) { mutableStateOf(color.toHex()) }
    var hexError by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Canvas(
            modifier = Modifier
                .size(wheelDp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        updateFromOffset(offset)
                        ThemePrefs.save(context, colorFromHsv())
                        hexInput = colorFromHsv().toHex()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> updateFromOffset(offset) },
                        onDrag = { change, _ ->
                            change.consume()
                            updateFromOffset(change.position)
                            hexInput = colorFromHsv().toHex()
                        },
                        onDragEnd = { ThemePrefs.save(context, colorFromHsv()) },
                        onDragCancel = { ThemePrefs.save(context, colorFromHsv()) }
                    )
                }
        ) {
            val radius = size.minDimension / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f

            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas

                val sweepPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    shader = android.graphics.SweepGradient(
                        cx, cy,
                        intArrayOf(
                            0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(),
                            0xFF00FFFF.toInt(), 0xFF0000FF.toInt(), 0xFFFF00FF.toInt(),
                            0xFFFF0000.toInt()
                        ), null
                    )
                }
                nc.drawCircle(cx, cy, radius, sweepPaint)

                val satPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    shader = android.graphics.RadialGradient(
                        cx, cy, radius,
                        intArrayOf(0xFFFFFFFF.toInt(), 0x00FFFFFF), null,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                }
                nc.drawCircle(cx, cy, radius, satPaint)

                val darkPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                darkPaint.color = android.graphics.Color.argb(
                    ((1f - briState.value) * 255).toInt(), 0, 0, 0
                )
                nc.drawCircle(cx, cy, radius, darkPaint)
            }

            val angle = hueState.value * (PI.toFloat() / 180f)
            val thumbDist = satState.value * (size.minDimension / 2f)
            val tx = size.width / 2f + thumbDist * cos(angle)
            val ty = size.height / 2f + thumbDist * sin(angle)
            drawCircle(Color.White, 12.dp.toPx(), Offset(tx, ty))
            drawCircle(Color.Black, 12.dp.toPx(), Offset(tx, ty), style = Stroke(2.dp.toPx()))
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Brightness", fontSize = 12.sp, modifier = Modifier.width(72.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = briState.value,
                onValueChange = { v ->
                    briState.value = v
                    hexInput = colorFromHsv().toHex()
                    onColorChanged(colorFromHsv())
                },
                onValueChangeFinished = { ThemePrefs.save(context, colorFromHsv()) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

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
                        .background(parseHex(hexInput) ?: color)
                )
            }
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val c = parseHex(hexInput)
                if (c != null) {
                    onApply(c)
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
