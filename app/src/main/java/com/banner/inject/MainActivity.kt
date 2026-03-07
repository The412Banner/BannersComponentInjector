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
import com.banner.inject.ui.screens.HomeScreen
import com.banner.inject.ui.screens.SetupScreen
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

                    if (uiState.componentsRootUri == null) {
                        SetupScreen(onRootSelected = { vm.setComponentsRoot(it) })
                    } else {
                        HomeScreen(
                            uiState = uiState,
                            onChangeRoot = { vm.clearRoot() },
                            onRefresh = { vm.refresh() },
                            onReplaceFiles = { comp, uris -> vm.replaceWithFiles(comp, uris) },
                            onReplaceFolder = { comp, uri -> vm.replaceWithFolder(comp, uri) },
                            onRestore = { vm.restoreComponent(it) },
                            onDeleteBackup = { vm.deleteBackup(it) },
                            onClearOpState = { vm.clearOpState() }
                        )
                    }
                }
            }
        }
    }
}
