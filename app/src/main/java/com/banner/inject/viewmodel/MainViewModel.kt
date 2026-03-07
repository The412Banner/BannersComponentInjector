package com.banner.inject.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.banner.inject.data.BackupManager
import com.banner.inject.data.ComponentRepository
import com.banner.inject.model.ComponentEntry
import com.banner.inject.model.GameHubApp
import com.banner.inject.model.KNOWN_GAMEHUB_APPS
import com.banner.inject.model.OpState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val apps: List<GameHubApp> = emptyList(),
    val selectedApp: GameHubApp? = null,
    val components: List<ComponentEntry> = emptyList(),
    val isLoadingComponents: Boolean = false,
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
        refreshAppList()
    }

    fun refreshAppList() {
        val pm = context.packageManager
        val apps = KNOWN_GAMEHUB_APPS.map { known ->
            val installed = try {
                pm.getPackageInfo(known.packageName, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
            val hasAccess = prefs.contains(uriKey(known.packageName))
            GameHubApp(known = known, isInstalled = installed, hasAccess = hasAccess)
        }
        _uiState.update { it.copy(apps = apps) }
    }

    /** Called with the URI returned from ACTION_OPEN_DOCUMENT_TREE for a given app. */
    fun grantAccess(app: GameHubApp, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.edit().putString(uriKey(app.known.packageName), uri.toString()).apply()
        refreshAppList()
        // Immediately open this app's component list
        selectApp(app.copy(hasAccess = true))
    }

    fun selectApp(app: GameHubApp) {
        val uri = storedUri(app.known.packageName) ?: return
        _uiState.update { it.copy(selectedApp = app, components = emptyList(), isLoadingComponents = true) }
        loadComponents(uri)
    }

    fun clearSelectedApp() {
        _uiState.update { it.copy(selectedApp = null, components = emptyList()) }
    }

    fun revokeAccess(app: GameHubApp) {
        val uri = storedUri(app.known.packageName)
        if (uri != null) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }
        prefs.edit().remove(uriKey(app.known.packageName)).apply()
        refreshAppList()
        if (_uiState.value.selectedApp?.known?.packageName == app.known.packageName) {
            clearSelectedApp()
        }
    }

    fun refresh() {
        val uri = _uiState.value.selectedApp?.known?.packageName
            ?.let { storedUri(it) } ?: return
        loadComponents(uri)
    }

    private fun loadComponents(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingComponents = true) }
            try {
                val root = repo.getRootDocument(uri)
                if (root == null || !root.canRead()) {
                    _uiState.update {
                        it.copy(
                            isLoadingComponents = false,
                            opState = OpState.Error("Cannot read folder. Re-grant access.")
                        )
                    }
                    return@launch
                }
                val components = repo.scanComponents(root, backupManager)
                _uiState.update { it.copy(isLoadingComponents = false, components = components) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoadingComponents = false, opState = OpState.Error(e.message ?: "Unknown error"))
                }
            }
        }
    }

    /** Build the SAF hint URI for the given package's components path on external storage. */
    fun initialUriHintFor(packageName: String): Uri =
        Uri.parse(
            "content://com.android.externalstorage.documents/document/" +
                Uri.encode("primary:Android/data/$packageName/files/usr/home/components")
        )

    // ── Component operations ───────────────────────────────────────────────────

    fun backupComponent(component: ComponentEntry) {
        viewModelScope.launch {
            _uiState.update { it.copy(opState = OpState.InProgress("Backing up ${component.folderName}...")) }
            try {
                backupManager.backupFromDocumentFile(component.documentFile, component.folderName)
                _uiState.update { it.copy(opState = OpState.Done("Backup saved for ${component.folderName}")) }
                refresh()
            } catch (e: Exception) {
                _uiState.update { it.copy(opState = OpState.Error(e.message ?: "Backup failed")) }
            }
        }
    }

    fun replaceWithFiles(component: ComponentEntry, sourceUris: List<Uri>) {
        viewModelScope.launch {
            _uiState.update { it.copy(opState = OpState.InProgress("Preparing...")) }
            repo.replaceWithFiles(
                component = component.documentFile,
                sourceUris = sourceUris,
                backupManager = backupManager,
                onProgress = { msg -> _uiState.update { it.copy(opState = OpState.InProgress(msg)) } }
            ).fold(
                onSuccess = {
                    _uiState.update { it.copy(opState = OpState.Done("${component.folderName} replaced")) }
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
            repo.replaceWithFolder(
                component = component.documentFile,
                sourceFolderUri = sourceFolderUri,
                backupManager = backupManager,
                onProgress = { msg -> _uiState.update { it.copy(opState = OpState.InProgress(msg)) } }
            ).fold(
                onSuccess = {
                    _uiState.update { it.copy(opState = OpState.Done("${component.folderName} replaced")) }
                    refresh()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(opState = OpState.Error(e.message ?: "Replace failed")) }
                    refresh()
                }
            )
        }
    }

    fun replaceWithWcp(component: ComponentEntry, wcpUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(opState = OpState.InProgress("Reading WCP file...")) }
            repo.replaceWithWcp(
                component = component.documentFile,
                wcpUri = wcpUri,
                backupManager = backupManager,
                onProgress = { msg -> _uiState.update { it.copy(opState = OpState.InProgress(msg)) } }
            ).fold(
                onSuccess = { profile ->
                    _uiState.update {
                        it.copy(opState = OpState.Done(
                            "${component.folderName} replaced with ${profile.type} ${profile.versionName}"
                        ))
                    }
                    refresh()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(opState = OpState.Error(e.message ?: "WCP import failed")) }
                    refresh()
                }
            )
        }
    }

    fun restoreComponent(component: ComponentEntry) {
        viewModelScope.launch {
            _uiState.update { it.copy(opState = OpState.InProgress("Restoring ${component.folderName}...")) }
            repo.restoreComponent(
                component = component.documentFile,
                backupManager = backupManager,
                onProgress = { msg -> _uiState.update { it.copy(opState = OpState.InProgress(msg)) } }
            ).fold(
                onSuccess = {
                    _uiState.update { it.copy(opState = OpState.Done("${component.folderName} restored")) }
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

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun uriKey(packageName: String) = "uri_$packageName"

    private fun storedUri(packageName: String): Uri? =
        prefs.getString(uriKey(packageName), null)?.let { Uri.parse(it) }
}
