package com.banner.inject

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banner.inject.data.RemoteSourceRepository
import com.banner.inject.model.MainTab
import com.banner.inject.ui.screens.AppListScreen
import com.banner.inject.ui.screens.BackupManagerSheet
import com.banner.inject.ui.screens.ComponentListScreen
import com.banner.inject.ui.screens.DownloadManagerScreen
import com.banner.inject.ui.screens.DownloadScreen
import com.banner.inject.ui.screens.SettingsSheet
import com.banner.inject.ui.theme.BannersComponentInjectorTheme
import com.banner.inject.ui.theme.ThemePrefs
import com.banner.inject.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted or denied — SAF still works regardless */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStoragePermissionsIfNeeded()
        enableEdgeToEdge()
        var accentColor by mutableStateOf(ThemePrefs.load(this))
        setContent {
            BannersComponentInjectorTheme(accentColor = accentColor) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val prefs = remember { getSharedPreferences("bci_settings", Context.MODE_PRIVATE) }
                    var currentTab by remember {
                        val savedTab = prefs.getString("default_start_tab", "INJECT") ?: "INJECT"
                        val initialTab = if (savedTab == "DOWNLOAD") MainTab.DOWNLOAD else MainTab.INJECT
                        mutableStateOf(initialTab)
                    }
                    val vm: MainViewModel = viewModel()
                    val uiState by vm.uiState.collectAsState()
                    val repo = remember { RemoteSourceRepository(this@MainActivity) }

                    val appVersion = remember {
                        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
                    }

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
                                    onAccentColorChanged = { accentColor = it }
                                )
                            } else {
                                ComponentListScreen(
                                    currentTab = currentTab,
                                    onTabSelected = { currentTab = it },
                                    app = uiState.selectedApp!!,
                                    components = uiState.components,
                                    isLoading = uiState.isLoadingComponents,
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
                                    onAccentColorChanged = { accentColor = it }
                                )
                            }
                        }
                        MainTab.DOWNLOAD, MainTab.MANAGERS -> {
                            var showBackupManager by remember { mutableStateOf(false) }
                            var showSettings by remember { mutableStateOf(false) }

                            if (currentTab == MainTab.DOWNLOAD) {
                                DownloadScreen(
                                    currentTab = currentTab,
                                    onTabSelected = { currentTab = it },
                                    repo = repo,
                                    onShowBackupManager = { showBackupManager = true },
                                    onShowSettings = { showSettings = true }
                                )
                            } else {
                                DownloadManagerScreen(
                                    currentTab = currentTab,
                                    onTabSelected = { currentTab = it },
                                    repo = repo,
                                    onShowBackupManager = { showBackupManager = true },
                                    onShowSettings = { showSettings = true }
                                )
                            }

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
                                    onOpenBackupManager = { showSettings = false; showBackupManager = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

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
