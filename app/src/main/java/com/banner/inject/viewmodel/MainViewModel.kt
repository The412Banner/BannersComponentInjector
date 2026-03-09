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
import com.banner.inject.model.FileInfo
import com.banner.inject.model.GameHubApp
import com.banner.inject.model.KNOWN_GAMEHUB_APPS
import com.banner.inject.model.OpState
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val cachePrefs = application.getSharedPreferences("component_cache_prefs", Context.MODE_PRIVATE)
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
            val installedPkg = known.packageNames.firstOrNull { pkg ->
                try { pm.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }
            }
            val accessPkg = known.packageNames.firstOrNull { pkg -> prefs.contains(uriKey(pkg)) }
            GameHubApp(
                known = known,
                isInstalled = installedPkg != null,
                hasAccess = accessPkg != null,
                activePackage = installedPkg ?: accessPkg ?: known.packageNames.first()
            )
        }
        _uiState.update { it.copy(apps = apps) }
    }

    /** Called with the URI returned from ACTION_OPEN_DOCUMENT_TREE for a given app. */
    fun grantAccess(app: GameHubApp, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        // Store URI under all package names in the group so any lookup hits it
        val edit = prefs.edit()
        app.known.packageNames.forEach { pkg -> edit.putString(uriKey(pkg), uri.toString()) }
        edit.apply()
        refreshAppList()
        selectApp(app.copy(hasAccess = true))
    }

    fun selectApp(app: GameHubApp) {
        val uri = app.known.packageNames.mapNotNull { storedUri(it) }.firstOrNull() ?: return
        val cacheKey = app.known.packageNames.first()
        val cached = loadComponentCache(cacheKey)
        if (cached != null) {
            // Cache hit: show list instantly, no spinner
            _uiState.update { it.copy(selectedApp = app, components = cached, isLoadingComponents = false) }
        } else {
            // Cache miss: do a full scan with loading indicator
            _uiState.update { it.copy(selectedApp = app, components = emptyList(), isLoadingComponents = true) }
            loadComponents(uri)
        }
    }

    fun clearSelectedApp() {
        _uiState.update { it.copy(selectedApp = null, components = emptyList()) }
    }

    fun revokeAccess(app: GameHubApp) {
        val uri = app.known.packageNames.mapNotNull { storedUri(it) }.firstOrNull()
        if (uri != null) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }
        val edit = prefs.edit()
        app.known.packageNames.forEach { pkg -> edit.remove(uriKey(pkg)) }
        edit.apply()
        cachePrefs.edit().remove("cache_${app.known.packageNames.first()}").apply()
        refreshAppList()
        if (_uiState.value.selectedApp?.known == app.known) {
            clearSelectedApp()
        }
    }

    fun refresh() {
        val app = _uiState.value.selectedApp ?: return
        val uri = app.known.packageNames.mapNotNull { storedUri(it) }.firstOrNull() ?: return
        loadComponents(uri)
    }

    private fun refreshComponent(component: ComponentEntry) {
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                repo.scanSingleComponent(component.documentFile, backupManager)
            }
            _uiState.update { state ->
                state.copy(components = state.components.map {
                    if (it.folderName == updated.folderName) updated else it
                })
            }
        }
    }

    private fun loadComponents(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingComponents = true) }
            try {
                val root = withContext(Dispatchers.IO) { repo.getRootDocument(uri) }
                if (root == null || !root.canRead()) {
                    _uiState.update {
                        it.copy(
                            isLoadingComponents = false,
                            opState = OpState.Error("Cannot read folder. Re-grant access.")
                        )
                    }
                    return@launch
                }
                val components = withContext(Dispatchers.IO) { repo.scanComponents(root, backupManager) }
                _uiState.update { it.copy(isLoadingComponents = false, components = components) }
                val cacheKey = _uiState.value.selectedApp?.known?.packageNames?.first()
                if (cacheKey != null) saveComponentCache(cacheKey, components)
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
                refreshComponent(component)
            } catch (e: Exception) {
                _uiState.update { it.copy(opState = OpState.Error(e.message ?: "Backup failed")) }
            }
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
                    val note = "${profile.type} ${profile.versionName}"
                    context.getSharedPreferences("component_notes", Context.MODE_PRIVATE)
                        .edit().putString("replaced_with_${component.folderName}", note).apply()
                    _uiState.update {
                        it.copy(opState = OpState.Done(
                            "${component.folderName} replaced with $note"
                        ))
                    }
                    refreshComponent(component)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(opState = OpState.Error(e.message ?: "WCP import failed")) }
                    refreshComponent(component)
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
                    context.getSharedPreferences("component_notes", Context.MODE_PRIVATE)
                        .edit().remove("replaced_with_${component.folderName}").apply()
                    _uiState.update { it.copy(opState = OpState.Done("${component.folderName} restored")) }
                    refreshComponent(component)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(opState = OpState.Error(e.message ?: "Restore failed")) }
                    refreshComponent(component)
                }
            )
        }
    }

    fun deleteBackup(component: ComponentEntry) {
        backupManager.deleteBackup(component.folderName)
        refreshComponent(component)
    }

    fun clearOpState() {
        _uiState.update { it.copy(opState = OpState.Idle) }
    }

    fun listAllBackups(): List<BackupManager.BackupInfo> = backupManager.listAllBackups()

    fun deleteBackupByName(componentName: String) {
        backupManager.deleteBackup(componentName)
        val matching = _uiState.value.components.firstOrNull { it.folderName == componentName }
        if (matching != null) refreshComponent(matching)
    }

    // ── Component list cache ───────────────────────────────────────────────────

    private fun saveComponentCache(packageKey: String, components: List<ComponentEntry>) {
        val array = JSONArray()
        for (comp in components) {
            val obj = JSONObject()
            obj.put("folderName", comp.folderName)
            obj.put("uri", comp.documentFile.uri.toString())
            obj.put("fileCount", comp.fileCount)
            obj.put("totalSize", comp.totalSize)
            array.put(obj)
        }
        cachePrefs.edit().putString("cache_$packageKey", array.toString()).apply()
    }

    /**
     * Loads cached component metadata and reconstructs ComponentEntry objects.
     * hasBackup and replacedWith are always read live from their respective prefs
     * so they're never stale even if the cache was written at a different time.
     * Returns null if no cache exists or parsing fails.
     */
    private fun loadComponentCache(packageKey: String): List<ComponentEntry>? {
        val json = cachePrefs.getString("cache_$packageKey", null) ?: return null
        return try {
            val array = JSONArray(json)
            val componentNotes = context.getSharedPreferences("component_notes", Context.MODE_PRIVATE)
            val result = mutableListOf<ComponentEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val folderName = obj.getString("folderName")
                val uri = Uri.parse(obj.getString("uri"))
                val fileCount = obj.getInt("fileCount")
                val totalSize = obj.getLong("totalSize")
                val doc = DocumentFile.fromTreeUri(context, uri) ?: continue
                // Synthetic file list gives the right count; actual files are never
                // accessed from cached entries — operations use the DocumentFile directly
                val syntheticFiles = List(fileCount) { FileInfo("", "", 0L, "") }
                result.add(ComponentEntry(
                    folderName = folderName,
                    documentFile = doc,
                    files = syntheticFiles,
                    hasBackup = backupManager.hasBackup(folderName),
                    totalSize = totalSize,
                    replacedWith = componentNotes.getString("replaced_with_$folderName", null)
                ))
            }
            if (result.isEmpty()) null else result
        } catch (_: Exception) { null }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun uriKey(packageName: String) = "uri_$packageName"

    private fun storedUri(packageName: String): Uri? =
        prefs.getString(uriKey(packageName), null)?.let { Uri.parse(it) }
}
