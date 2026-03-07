package com.banner.inject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banner.inject.ui.screens.AppListScreen
import com.banner.inject.ui.screens.ComponentListScreen
import com.banner.inject.ui.theme.BannersComponentInjectorTheme
import com.banner.inject.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BannersComponentInjectorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val vm: MainViewModel = viewModel()
                    val uiState by vm.uiState.collectAsState()

                    if (uiState.selectedApp == null) {
                        AppListScreen(
                            apps = uiState.apps,
                            onAppSelected = { vm.selectApp(it) },
                            onAccessGranted = { app, uri -> vm.grantAccess(app, uri) },
                            onRevokeAccess = { vm.revokeAccess(it) },
                            onRefresh = { vm.refreshAppList() },
                            initialUriHintFor = { vm.initialUriHintFor(it) }
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
                            onReplaceFiles = { comp, uris -> vm.replaceWithFiles(comp, uris) },
                            onReplaceFolder = { comp, uri -> vm.replaceWithFolder(comp, uri) },
                            onRestoreComponent = { vm.restoreComponent(it) },
                            onDeleteBackup = { vm.deleteBackup(it) },
                            onClearOpState = { vm.clearOpState() }
                        )
                    }
                }
            }
        }
    }
}
