package com.banner.inject

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banner.inject.ui.screens.AppListScreen
import com.banner.inject.ui.screens.ComponentListScreen
import com.banner.inject.ui.theme.BannersComponentInjectorTheme
import com.banner.inject.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted or denied — SAF still works regardless */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestStoragePermissionsIfNeeded()
        enableEdgeToEdge()
        setContent {
            BannersComponentInjectorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val vm: MainViewModel = viewModel()
                    val uiState by vm.uiState.collectAsState()

                    if (uiState.selectedApp == null) {
                        val appVersion = remember {
                            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
                        }
                        AppListScreen(
                            apps = uiState.apps,
                            onAppSelected = { vm.selectApp(it) },
                            onAccessGranted = { app, uri -> vm.grantAccess(app, uri) },
                            onRevokeAccess = { vm.revokeAccess(it) },
                            onRefresh = { vm.refreshAppList() },
                            initialUriHintFor = { vm.initialUriHintFor(it) },
                            onListBackups = { vm.listAllBackups() },
                            onDeleteBackupByName = { vm.deleteBackupByName(it) },
                            appVersion = appVersion
                        )
                    } else {
                        ComponentListScreen(
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
                            onClearOpState = { vm.clearOpState() }
                        )
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
