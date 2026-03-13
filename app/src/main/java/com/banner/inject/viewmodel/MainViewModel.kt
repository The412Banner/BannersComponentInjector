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
import com.banner.inject.data.GameRepository
import com.banner.inject.model.ComponentEntry
import com.banner.inject.model.GameEntry
import com.banner.inject.model.GameHubApp
import com.banner.inject.model.KNOWN_GAMEHUB_APPS
import com.banner.inject.model.KnownApp
import com.banner.inject.model.OpState
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val apps: List<GameHubApp> = emptyList(),
    val selectedApp: GameHubApp? = null,
    val components: List<ComponentEntry> = emptyList(),
    val isLoadingComponents: Boolean = false,
    val totalComponentCount: Int = 0,
    val loadedComponentCount: Int = 0,
    val opState: OpState = OpState.Idle,
    // My Games tab
    val selectedGamesApp: GameHubApp? = null,
    val games: List<GameEntry> = emptyList(),
    val isLoadingGames: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val prefs = application.getSharedPreferences("bci_prefs", Context.MODE_PRIVATE)
    private val repo = ComponentRepository(application)
    private val backupManager = BackupManager(application)
    private val gameRepo = GameRepository(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        refreshAppList()
    }

    fun refreshAppList() {
        val pm = context.packageManager
        val apps = (KNOWN_GAMEHUB_APPS + loadCustomApps()).map { known ->
            val installedPkgs = known.packageNames.filter { pkg ->
                try { pm.getPackageInfo(pkg, 0); isLikelyGameHub(pm, pkg, known.isCustom) }
                catch (_: Exception) { false }
            }
            val installedPkg = installedPkgs.firstOrNull()
            val accessPkg = known.packageNames.firstOrNull { pkg -> prefs.contains(uriKey(pkg)) }
            GameHubApp(
                known = known,
                // Custom apps are always treated as installed so the card is always tappable
                // (Android 11+ blocks package visibility for packages not in <queries>)
                isInstalled = installedPkg != null || known.isCustom,
                hasAccess = accessPkg != null,
                activePackage = installedPkg ?: accessPkg ?: known.packageNames.first(),
                installedPackages = if (known.isCustom) known.packageNames else installedPkgs
            )
        }
        _uiState.update { it.copy(apps = apps) }
    }

    /** Returns true if a specific package name has a stored SAF URI. */
    fun hasAccessForPackage(packageName: String): Boolean = prefs.contains(uriKey(packageName))

    fun addCustomApp(name: String, packageName: String) {
        val current = loadCustomApps().toMutableList()
        current.add(KnownApp(name.trim(), listOf(packageName.trim()), isCustom = true))
        saveCustomApps(current)
        refreshAppList()
    }

    fun removeCustomApp(app: GameHubApp) {
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
        val current = loadCustomApps().filter { it.packageNames != app.known.packageNames }
        saveCustomApps(current)
        if (_uiState.value.selectedApp?.known == app.known) clearSelectedApp()
        refreshAppList()
    }

    private fun loadCustomApps(): List<KnownApp> {
        val json = prefs.getString("custom_gamehub_apps", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                KnownApp(
                    displayName = obj.getString("name"),
                    packageNames = listOf(obj.getString("packageName")),
                    isCustom = true
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveCustomApps(apps: List<KnownApp>) {
        val arr = JSONArray()
        apps.forEach { app ->
            arr.put(JSONObject().apply {
                put("name", app.displayName)
                put("packageName", app.packageNames.first())
            })
        }
        prefs.edit().putString("custom_gamehub_apps", arr.toString()).apply()
    }

    /** Called with the URI returned from ACTION_OPEN_DOCUMENT_TREE for a given app. */
    fun grantAccess(app: GameHubApp, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        // Store URI only for activePackage so multi-installed packages each get their own URI
        prefs.edit().putString(uriKey(app.activePackage), uri.toString()).apply()
        refreshAppList()
        selectApp(app.copy(hasAccess = true))
    }

    fun selectApp(app: GameHubApp) {
        // Prioritize the URI for the specific activePackage, then fall back to any in the group
        val uri = storedUri(app.activePackage)
            ?: app.known.packageNames.mapNotNull { storedUri(it) }.firstOrNull()
            ?: return
        _uiState.update { it.copy(selectedApp = app, components = emptyList(), isLoadingComponents = true) }
        loadComponents(uri)
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
            _uiState.update { it.copy(isLoadingComponents = true, components = emptyList(), totalComponentCount = 0, loadedComponentCount = 0) }
            try {
                val root = withContext(Dispatchers.IO) { repo.getRootDocument(uri) }
                if (root == null || !root.canRead()) {
                    _uiState.update {
                        it.copy(isLoadingComponents = false, opState = OpState.Error("Cannot read folder. Re-grant access."))
                    }
                    return@launch
                }
                val dirs = withContext(Dispatchers.IO) { repo.scanComponentDirs(root) }
                _uiState.update { it.copy(totalComponentCount = dirs.size) }
                repo.scanComponents(dirs, backupManager)
                    .flowOn(Dispatchers.IO)
                    .collect { component ->
                        _uiState.update { state ->
                            val newLoaded = state.loadedComponentCount + 1
                            state.copy(
                                components = (state.components + component).sortedBy { it.folderName.lowercase() },
                                loadedComponentCount = newLoaded,
                                isLoadingComponents = newLoaded < state.totalComponentCount
                            )
                        }
                    }
                _uiState.update { it.copy(isLoadingComponents = false) }
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

    /** Loads components for [app] without touching the main UI state. Used by inject-from-downloads flow. */
    suspend fun getComponentsForApp(app: GameHubApp): List<ComponentEntry> {
        val uri = app.known.packageNames.mapNotNull { storedUri(it) }.firstOrNull()
            ?: return emptyList()
        return try {
            val root = withContext(Dispatchers.IO) { repo.getRootDocument(uri) }
                ?: return emptyList()
            if (!root.canRead()) return emptyList()
            val dirs = withContext(Dispatchers.IO) { repo.scanComponentDirs(root) }
            val list = mutableListOf<ComponentEntry>()
            repo.scanComponents(dirs, backupManager)
                .flowOn(Dispatchers.IO)
                .collect { list.add(it) }
            list.sortedBy { it.folderName.lowercase() }
        } catch (_: Exception) { emptyList() }
    }

    fun deleteBackupByName(componentName: String) {
        backupManager.deleteBackup(componentName)
        val matching = _uiState.value.components.firstOrNull { it.folderName == componentName }
        if (matching != null) refreshComponent(matching)
    }

    // ── My Games ───────────────────────────────────────────────────────────────

    fun hasGamesAccess(packageName: String): Boolean = prefs.contains(gamesUriKey(packageName))

    fun grantGamesAccess(app: GameHubApp, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.edit().putString(gamesUriKey(app.activePackage), uri.toString()).apply()
        selectGamesApp(app.copy(hasAccess = true))
    }

    fun revokeGamesAccess(app: GameHubApp) {
        val uri = app.known.packageNames.mapNotNull { storedGamesUri(it) }.firstOrNull()
        if (uri != null) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }
        val edit = prefs.edit()
        app.known.packageNames.forEach { pkg -> edit.remove(gamesUriKey(pkg)) }
        edit.apply()
        if (_uiState.value.selectedGamesApp?.known == app.known) clearSelectedGamesApp()
    }

    fun selectGamesApp(app: GameHubApp) {
        val uri = storedGamesUri(app.activePackage)
            ?: app.known.packageNames.mapNotNull { storedGamesUri(it) }.firstOrNull()
            ?: return
        _uiState.update { it.copy(selectedGamesApp = app, games = emptyList(), isLoadingGames = true) }
        loadGames(uri)
    }

    fun clearSelectedGamesApp() {
        _uiState.update { it.copy(selectedGamesApp = null, games = emptyList(), isLoadingGames = false) }
    }

    fun refreshGames() {
        val app = _uiState.value.selectedGamesApp ?: return
        val uri = storedGamesUri(app.activePackage)
            ?: app.known.packageNames.mapNotNull { storedGamesUri(it) }.firstOrNull()
            ?: return
        loadGames(uri)
    }

    private fun loadGames(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingGames = true, games = emptyList()) }
            try {
                val root = withContext(Dispatchers.IO) { gameRepo.getRootDocument(uri) }
                if (root == null || !root.canRead()) {
                    _uiState.update {
                        it.copy(isLoadingGames = false, opState = OpState.Error("Cannot read virtual_containers. Re-grant access."))
                    }
                    return@launch
                }
                val games = withContext(Dispatchers.IO) { gameRepo.scanGames(root) }
                _uiState.update { it.copy(games = games, isLoadingGames = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoadingGames = false, opState = OpState.Error(e.message ?: "Failed to scan games"))
                }
            }
        }
    }

    fun launchGame(packageName: String, gameId: String) {
        gameRepo.launchGame(packageName, gameId).onFailure { e ->
            _uiState.update { it.copy(opState = OpState.Error("Launch failed: ${e.message}")) }
        }
    }

    /** Creates .iso files in virtual_containers/ for all scanned games. Returns count written. */
    fun createIsoFiles(onResult: (Int) -> Unit) {
        val app = _uiState.value.selectedGamesApp ?: return
        val uri = storedGamesUri(app.activePackage)
            ?: app.known.packageNames.mapNotNull { storedGamesUri(it) }.firstOrNull()
            ?: return
        viewModelScope.launch {
            val root = withContext(Dispatchers.IO) { gameRepo.getRootDocument(uri) } ?: return@launch
            val games = _uiState.value.games
            val count = gameRepo.createIsoFiles(root, games)
            onResult(count)
        }
    }

    fun initialGamesUriHintFor(packageName: String): Uri =
        Uri.parse(
            "content://com.android.externalstorage.documents/document/" +
                Uri.encode("primary:Android/data/$packageName/files/usr/home/virtual_containers")
        )

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Returns true if the installed package is actually a GameHub variant.
     * Packages whose name already contains "gamehub" are always trusted.
     * For borrowed package names (e.g. com.tencent.ig, com.mihoyo.genshinimpact),
     * the app label is checked — real apps like PUBG Mobile or Genshin Impact
     * won't have "GameHub" in their label, so they are excluded.
     * Custom apps added by the user are always trusted.
     */
    private fun isLikelyGameHub(pm: PackageManager, packageName: String, isCustom: Boolean): Boolean {
        if (isCustom) return true
        if (packageName.contains("gamehub", ignoreCase = true)) return true
        return try {
            val label = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            label.contains("gamehub", ignoreCase = true) || label.contains("game hub", ignoreCase = true)
        } catch (_: Exception) { false }
    }

    private fun uriKey(packageName: String) = "uri_$packageName"

    private fun storedUri(packageName: String): Uri? =
        prefs.getString(uriKey(packageName), null)?.let { Uri.parse(it) }

    private fun gamesUriKey(packageName: String) = "games_uri_$packageName"

    private fun storedGamesUri(packageName: String): Uri? =
        prefs.getString(gamesUriKey(packageName), null)?.let { Uri.parse(it) }
}
