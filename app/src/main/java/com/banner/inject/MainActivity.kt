package com.banner.inject

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Snackbar
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banner.inject.data.RemoteSourceRepository
import com.banner.inject.data.UpdateRepository
import com.banner.inject.model.MainTab
import com.banner.inject.ui.screens.AppListScreen
import com.banner.inject.ui.screens.BackupManagerSheet
import com.banner.inject.ui.screens.ComponentListScreen
import com.banner.inject.ui.screens.DownloadManagerScreen
import com.banner.inject.ui.screens.DownloadScreen
import com.banner.inject.ui.screens.LandscapeNavRail
import com.banner.inject.ui.screens.LocalHasNewDownloads
import com.banner.inject.ui.screens.LocalIsLandscape
import com.banner.inject.ui.screens.LocalShowGamesTab
import com.banner.inject.ui.screens.MyGamesScreen
import com.banner.inject.ui.screens.SettingsSheet
import com.banner.inject.ui.theme.BannersComponentInjectorTheme
import com.banner.inject.ui.theme.ThemePrefs
import com.banner.inject.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted or denied — SAF still works regardless */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStoragePermissionsIfNeeded()
        enableEdgeToEdge()
        val settingsPrefs = getSharedPreferences("bci_settings", Context.MODE_PRIVATE)
        var accentColor by mutableStateOf(ThemePrefs.load(this))
        var isDarkMode by mutableStateOf(ThemePrefs.loadDarkMode(this))
        var isAmoled by mutableStateOf(ThemePrefs.loadAmoled(this))
        var isDynamicColor by mutableStateOf(ThemePrefs.loadDynamicColor(this))
        var showMyGamesTab by mutableStateOf(settingsPrefs.getBoolean("show_my_games_tab", false))
        setContent {
            BannersComponentInjectorTheme(
                accentColor = accentColor,
                darkMode = isDarkMode,
                amoled = isAmoled,
                dynamicColor = isDynamicColor
            ) {
                val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                var hasNewDownloads by remember { mutableStateOf(false) }
                CompositionLocalProvider(
                    LocalShowGamesTab provides showMyGamesTab,
                    LocalHasNewDownloads provides hasNewDownloads,
                    LocalIsLandscape provides isLandscape
                ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val prefs = remember { getSharedPreferences("bci_settings", Context.MODE_PRIVATE) }
                    var currentTab by remember {
                        val savedTab = prefs.getString("default_start_tab", "INJECT") ?: "INJECT"
                        val initialTab = when (savedTab) {
                            "DOWNLOAD" -> MainTab.DOWNLOAD
                            "MANAGERS" -> MainTab.MANAGERS
                            "GAMES"    -> MainTab.GAMES
                            else       -> MainTab.INJECT
                        }
                        mutableStateOf(initialTab)
                    }
                    // Fall back to INJECT if Games tab gets hidden while on it
                    LaunchedEffect(showMyGamesTab) {
                        if (!showMyGamesTab && currentTab == MainTab.GAMES) currentTab = MainTab.INJECT
                    }

                    val vm: MainViewModel = viewModel()
                    val uiState by vm.uiState.collectAsState()
                    val repo = remember { RemoteSourceRepository(this@MainActivity) }
                    val scope = rememberCoroutineScope()

                    // Background source refresh on launch — populates cache and checks for new items
                    LaunchedEffect(Unit) {
                        val allTypes = listOf(
                            "dxvk", "vkd3d", "box64", "fexcore", "wined3d",
                            RemoteSourceRepository.GPU_DRIVER_TYPE, "wine", "proton"
                        )
                        try { repo.refreshAllCache(repo.getAllSources(), allTypes) } catch (_: Exception) {}
                        hasNewDownloads = repo.hasNewItems()
                    }

                    // Clear badge when user opens the Download tab
                    LaunchedEffect(currentTab) {
                        if (currentTab == MainTab.DOWNLOAD) {
                            repo.markAllAsSeen()
                            hasNewDownloads = false
                        }
                    }

                    val appVersion = remember {
                        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
                    }

                    // ── Launch update check ────────────────────────────────────
                    var launchUpdateRelease by remember { mutableStateOf<UpdateRepository.ReleaseInfo?>(null) }
                    var launchUpdateDownloading by remember { mutableStateOf(false) }
                    var launchUpdateProgress by remember { mutableFloatStateOf(0f) }

                    LaunchedEffect(Unit) {
                        val checkOnLaunch = prefs.getBoolean("update_check_on_launch", false)
                        if (!checkOnLaunch) return@LaunchedEffect
                        val includePreReleases = prefs.getBoolean("update_include_pre", false)
                        try {
                            val latest = UpdateRepository.fetchLatestRelease(includePreReleases)
                            if (latest != null && latest.versionName != appVersion) {
                                launchUpdateRelease = latest
                            }
                        } catch (_: Exception) {}
                    }

                    launchUpdateRelease?.let { release ->
                        AlertDialog(
                            onDismissRequest = {
                                if (!launchUpdateDownloading) launchUpdateRelease = null
                            },
                            icon = {
                                Icon(
                                    Icons.Default.NewReleases,
                                    contentDescription = null
                                )
                            },
                            title = { Text("Update Available", fontWeight = FontWeight.Bold) },
                            text = {
                                if (launchUpdateDownloading) {
                                    androidx.compose.foundation.layout.Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        CircularProgressIndicator(progress = { launchUpdateProgress })
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Downloading… ${(launchUpdateProgress * 100).toInt()}%",
                                            fontSize = 13.sp
                                        )
                                    }
                                } else {
                                    androidx.compose.foundation.layout.Column {
                                        Text(
                                            "${release.tagName}${if (release.isPreRelease) " (pre-release)" else ""}",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "Installed: v$appVersion",
                                            fontSize = 12.sp,
                                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!release.body.isNullOrBlank()) {
                                            Spacer(Modifier.height(12.dp))
                                            HorizontalDivider()
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                "What's new",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            androidx.compose.foundation.layout.Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 180.dp)
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                Text(
                                                    stripMarkdown(release.body),
                                                    fontSize = 12.sp,
                                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                                    lineHeight = 18.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                if (!launchUpdateDownloading && release.apkUrl != null) {
                                    Button(onClick = {
                                        scope.launch {
                                            launchUpdateDownloading = true
                                            launchUpdateProgress = 0f
                                            try {
                                                val file = UpdateRepository.downloadApk(
                                                    this@MainActivity,
                                                    release.apkUrl
                                                ) { progress ->
                                                    launchUpdateProgress = progress
                                                }
                                                UpdateRepository.installApk(this@MainActivity, file)
                                            } catch (_: Exception) {}
                                            launchUpdateDownloading = false
                                            launchUpdateRelease = null
                                        }
                                    }) {
                                        Text("Download & Install")
                                    }
                                }
                            },
                            dismissButton = {
                                if (!launchUpdateDownloading) {
                                    androidx.compose.foundation.layout.Row {
                                        if (release.apkUrl != null) {
                                            OutlinedButton(onClick = {
                                                runCatching {
                                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
                                                }
                                                launchUpdateRelease = null
                                            }) {
                                                Text("View on GitHub")
                                            }
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        TextButton(onClick = { launchUpdateRelease = null }) {
                                            Text("Not Now")
                                        }
                                    }
                                }
                            }
                        )
                    }

                    val screenContent: @Composable () -> Unit = {
                    when (currentTab) {
                        MainTab.INJECT -> {
                            if (uiState.selectedApp == null) {
                                AppListScreen(
                                    currentTab = currentTab,
                                    onTabSelected = { currentTab = it },
                                    apps = uiState.apps,
                                    onAppSelected = { vm.selectApp(it) },
                                    onAccessGranted = { app, uri -> vm.grantAccess(app, uri) },
                                    onRevokeAccess = { vm.revokeAccess(it) },
                                    onRefresh = { vm.refreshAppList() },
                                    initialUriHintFor = { vm.initialUriHintFor(it) },
                                    onListBackups = { vm.listAllBackups() },
                                    onDeleteBackupByName = { vm.deleteBackupByName(it) },
                                    appVersion = appVersion,
                                    accentColor = accentColor,
                                    onAccentColorChanged = { accentColor = it },
                                    isDarkMode = isDarkMode,
                                    onDarkModeChanged = { isDarkMode = it },
                                    isAmoled = isAmoled,
                                    onAmoledChanged = { isAmoled = it },
                                    isDynamicColor = isDynamicColor,
                                    onDynamicColorChanged = { isDynamicColor = it },
                                    onAddCustomApp = { name, pkg -> vm.addCustomApp(name, pkg) },
                                    onRemoveCustomApp = { vm.removeCustomApp(it) },
                                    hasAccessForPackage = { vm.hasAccessForPackage(it) },
                                    showMyGamesTab = showMyGamesTab,
                                    onShowMyGamesTabChanged = { showMyGamesTab = it }
                                )
                            } else {
                                ComponentListScreen(
                                    currentTab = currentTab,
                                    onTabSelected = { currentTab = it },
                                    app = uiState.selectedApp!!,
                                    components = uiState.components,
                                    isLoading = uiState.isLoadingComponents,
                                    totalComponentCount = uiState.totalComponentCount,
                                    loadedComponentCount = uiState.loadedComponentCount,
                                    opState = uiState.opState,
                                    onBack = { vm.clearSelectedApp() },
                                    onRefresh = { vm.refresh() },
                                    onBackupComponent = { vm.backupComponent(it) },
                                    onReplaceWcp = { comp, uri -> vm.replaceWithWcp(comp, uri) },
                                    onRestoreComponent = { vm.restoreComponent(it) },
                                    onDeleteBackup = { vm.deleteBackup(it) },
                                    onClearOpState = { vm.clearOpState() },
                                    onListBackups = { vm.listAllBackups() },
                                    onDeleteBackupByName = { vm.deleteBackupByName(it) },
                                    appVersion = appVersion,
                                    accentColor = accentColor,
                                    onAccentColorChanged = { accentColor = it },
                                    isDarkMode = isDarkMode,
                                    onDarkModeChanged = { isDarkMode = it },
                                    isAmoled = isAmoled,
                                    onAmoledChanged = { isAmoled = it },
                                    isDynamicColor = isDynamicColor,
                                    onDynamicColorChanged = { isDynamicColor = it },
                                    showMyGamesTab = showMyGamesTab,
                                    onShowMyGamesTabChanged = { showMyGamesTab = it }
                                )
                            }
                        }
                        MainTab.DOWNLOAD -> {
                            var showBackupManager by remember { mutableStateOf(false) }
                            var showSettings by remember { mutableStateOf(false) }

                            DownloadScreen(
                                currentTab = currentTab,
                                onTabSelected = { currentTab = it },
                                repo = repo,
                                onShowBackupManager = { showBackupManager = true },
                                onShowSettings = { showSettings = true }
                            )

                            if (showBackupManager) {
                                BackupManagerSheet(
                                    onDismiss = { showBackupManager = false },
                                    onListBackups = { vm.listAllBackups() },
                                    onDeleteBackup = { vm.deleteBackupByName(it) }
                                )
                            }

                            if (showSettings) {
                                SettingsSheet(
                                    appVersion = appVersion,
                                    accentColor = accentColor,
                                    onAccentColorChanged = { accentColor = it },
                                    onDismiss = { showSettings = false },
                                    onOpenBackupManager = { showSettings = false; showBackupManager = true },
                                    isDarkMode = isDarkMode,
                                    onDarkModeChanged = { isDarkMode = it },
                                    isAmoled = isAmoled,
                                    onAmoledChanged = { isAmoled = it },
                                    isDynamicColor = isDynamicColor,
                                    onDynamicColorChanged = { isDynamicColor = it },
                                    showMyGamesTab = showMyGamesTab,
                                    onShowMyGamesTabChanged = { showMyGamesTab = it }
                                )
                            }
                        }
                        MainTab.MANAGERS -> {
                            var showSettings by remember { mutableStateOf(false) }

                            DownloadManagerScreen(
                                currentTab = currentTab,
                                onTabSelected = { currentTab = it },
                                repo = repo,
                                onListBackups = { vm.listAllBackups() },
                                onDeleteBackup = { vm.deleteBackupByName(it) },
                                onShowSettings = { showSettings = true },
                                apps = uiState.apps,
                                onGetComponentsForApp = { app -> vm.getComponentsForApp(app) },
                                onInjectInto = { comp, uri -> vm.replaceWithWcp(comp, uri) },
                                opState = uiState.opState,
                                onClearOpState = { vm.clearOpState() }
                            )

                            if (showSettings) {
                                SettingsSheet(
                                    appVersion = appVersion,
                                    accentColor = accentColor,
                                    onAccentColorChanged = { accentColor = it },
                                    onDismiss = { showSettings = false },
                                    onOpenBackupManager = { showSettings = false },
                                    isDarkMode = isDarkMode,
                                    onDarkModeChanged = { isDarkMode = it },
                                    isAmoled = isAmoled,
                                    onAmoledChanged = { isAmoled = it },
                                    isDynamicColor = isDynamicColor,
                                    onDynamicColorChanged = { isDynamicColor = it },
                                    showMyGamesTab = showMyGamesTab,
                                    onShowMyGamesTabChanged = { showMyGamesTab = it }
                                )
                            }
                        }
                        MainTab.GAMES -> {
                            var isoSnackbarMessage by remember { mutableStateOf<String?>(null) }

                            MyGamesScreen(
                                currentTab = currentTab,
                                onTabSelected = { currentTab = it },
                                apps = uiState.apps,
                                selectedApp = uiState.selectedGamesApp,
                                games = uiState.games,
                                isLoadingGames = uiState.isLoadingGames,
                                hasDataAccess = { vm.hasDataAccess(it) },
                                onSelectApp = { vm.selectGamesApp(it) },
                                onAccessGranted = { app, uri -> vm.grantAccess(app, uri) },
                                onRevokeAccess = { vm.revokeAccess(it) },
                                onBack = { vm.clearSelectedGamesApp() },
                                onRefresh = { vm.refreshGames() },
                                onLaunchGame = { pkg, gameId -> vm.launchGame(pkg, gameId) },
                                onCreateIsos = {
                                    vm.createIsoFiles { count ->
                                        isoSnackbarMessage = "$count ISO file${if (count != 1) "s" else ""} created in virtual_containers"
                                    }
                                },
                                onHideGame = { game -> vm.hideLocalGame(game.gameId) },
                                onDeleteGame = { game, onResult -> vm.deleteLocalGameFolder(game.gameId, onResult) },
                                initialDataUriHintFor = { vm.initialUriHintFor(it) }
                            )

                            isoSnackbarMessage?.let { msg ->
                                androidx.compose.runtime.LaunchedEffect(msg) {
                                    kotlinx.coroutines.delay(3000)
                                    isoSnackbarMessage = null
                                }
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Snackbar(
                                        modifier = Modifier.padding(16.dp),
                                        action = {
                                            TextButton(onClick = { isoSnackbarMessage = null }) {
                                                Text("OK")
                                            }
                                        }
                                    ) { Text(msg) }
                                }
                            }
                        }
                    }
                    } // end screenContent lambda

                    if (isLandscape) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            LandscapeNavRail(
                                currentTab = currentTab,
                                onTabSelected = { currentTab = it }
                            )
                            Box(modifier = Modifier.weight(1f)) { screenContent() }
                        }
                    } else {
                        screenContent()
                    }
                }
                } // CompositionLocalProvider
            }
        }
    }

    private fun stripMarkdown(text: String): String = text
        .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("__(.+?)__"), "$1")
        .replace(Regex("\\*(.+?)\\*"), "$1")
        .replace(Regex("`(.+?)`"), "$1")
        .replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "• ")
        .replace(Regex("^>\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private fun requestStoragePermissionsIfNeeded() {
        // READ_EXTERNAL_STORAGE only applies up to Android 12 (API 32)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            val needed = listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) requestPermissions.launch(needed.toTypedArray())
        }
    }
}
