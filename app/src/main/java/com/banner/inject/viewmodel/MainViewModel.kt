package com.banner.inject.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.banner.inject.data.BackupManager
import com.banner.inject.data.ComponentRepository
import com.banner.inject.model.ComponentEntry
import com.banner.inject.model.OpState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val componentsRootUri: Uri? = null,
    val components: List<ComponentEntry> = emptyList(),
    val isLoading: Boolean = false,
    val opState: OpState = OpState.Idle
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val prefs = application.getSharedPreferences("bci_prefs", Context.MODE_PRIVATE)
    private val repo = ComponentRepository(application)
    private val backupManager = BackupManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        val savedUri = prefs.getString("components_root_uri", null)?.let { Uri.parse(it) }
        if (savedUri != null) {
            _uiState.update { it.copy(componentsRootUri = savedUri) }
            loadComponents(savedUri)
        }
    }

    fun setComponentsRoot(uri: Uri) {
        // Take persistable permission so we can access across restarts
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.edit().putString("components_root_uri", uri.toString()).apply()
        _uiState.update { it.copy(componentsRootUri = uri) }
        loadComponents(uri)
    }

    fun clearRoot() {
        val uri = _uiState.value.componentsRootUri
        if (uri != null) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }
        prefs.edit().remove("components_root_uri").apply()
        _uiState.update { UiState() }
    }

    fun refresh() {
        val uri = _uiState.value.componentsRootUri ?: return
        loadComponents(uri)
    }

    private fun loadComponents(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val root = repo.getRootDocument(uri)
                if (root == null || !root.canRead()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            opState = OpState.Error("Cannot read components folder. Re-select it.")
                        )
                    }
                    return@launch
                }
                val components = repo.scanComponents(root, backupManager)
                _uiState.update { it.copy(isLoading = false, components = components) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, opState = OpState.Error(e.message ?: "Unknown error"))
                }
            }
        }
    }

    fun replaceWithFiles(component: ComponentEntry, sourceUris: List<Uri>) {
        viewModelScope.launch {
            _uiState.update { it.copy(opState = OpState.InProgress("Preparing...")) }
            val result = repo.replaceWithFiles(
                component = component.documentFile,
                sourceUris = sourceUris,
                backupManager = backupManager,
                onProgress = { msg -> _uiState.update { it.copy(opState = OpState.InProgress(msg)) } }
            )
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(opState = OpState.Done("${component.folderName} replaced successfully")) }
                    refresh()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(opState = OpState.Error(e.message ?: "Replace failed")) }
                    refresh()
                }
            )
        }
    }

    fun replaceWithFolder(component: ComponentEntry, sourceFolderUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(opState = OpState.InProgress("Preparing...")) }
            val result = repo.replaceWithFolder(
                component = component.documentFile,
                sourceFolderUri = sourceFolderUri,
                backupManager = backupManager,
                onProgress = { msg -> _uiState.update { it.copy(opState = OpState.InProgress(msg)) } }
            )
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(opState = OpState.Done("${component.folderName} replaced successfully")) }
                    refresh()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(opState = OpState.Error(e.message ?: "Replace failed")) }
                    refresh()
                }
            )
        }
    }

    fun restoreComponent(component: ComponentEntry) {
        viewModelScope.launch {
            _uiState.update { it.copy(opState = OpState.InProgress("Restoring ${component.folderName}...")) }
            val result = repo.restoreComponent(
                component = component.documentFile,
                backupManager = backupManager,
                onProgress = { msg -> _uiState.update { it.copy(opState = OpState.InProgress(msg)) } }
            )
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(opState = OpState.Done("${component.folderName} restored from backup")) }
                    refresh()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(opState = OpState.Error(e.message ?: "Restore failed")) }
                    refresh()
                }
            )
        }
    }

    fun deleteBackup(component: ComponentEntry) {
        backupManager.deleteBackup(component.folderName)
        refresh()
    }

    fun clearOpState() {
        _uiState.update { it.copy(opState = OpState.Idle) }
    }
}
