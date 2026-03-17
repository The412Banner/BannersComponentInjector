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
import com.banner.inject.data.GameOverrideRepository
import com.banner.inject.data.GameRepository
import com.banner.inject.data.ImportedGame
import com.banner.inject.data.ImportedGameRepository
import com.banner.inject.data.SteamRepository
import com.banner.inject.model.ComponentEntry
import com.banner.inject.model.GameEntry
import com.banner.inject.model.GameOverride
import com.banner.inject.model.GameType
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
    val importedGames: List<GameEntry> = emptyList(),
    val steamGames: List<GameEntry> = emptyList(),
    val isLoadingSteam: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val prefs = application.getSharedPreferences("bci_prefs", Context.MODE_PRIVATE)
    private val repo = ComponentRepository(application)
    private val backupManager = BackupManager(application)
    private val gameRepo = GameRepository(application)
    private val importedGameRepo = ImportedGameRepository(application)
    private val gameOverrideRepo = GameOverrideRepository(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        SteamRepository.init(context)
        refreshAppList(autoSelectForSteam = true)
        loadImportedGames()
    }

    fun refreshAppList(autoSelectForSteam: Boolean = false) {
        val pm = context.packageManager
        val apps = (KNOWN_GAMEHUB_APPS + loadCustomApps()).map { known ->
            val installedPkgs = known.packageNames.filter { pkg ->
                try { pm.getPackageInfo(pkg, 0); isLikelyGameHub(pm, pkg, known.isCustom) }
                catch (_: Exception) { false }
            }
            val installedPkg = installedPkgs.firstOrNull()
            val accessPkg = known.packageNames.firstOrNull { pkg -> prefs.contains(dataUriKey(pkg)) }
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
        // Auto-select the first app with data access so Steam games load without manual selection
        if (autoSelectForSteam && _uiState.value.selectedGamesApp == null) {
            apps.firstOrNull { app -> app.known.packageNames.any { prefs.contains(dataUriKey(it)) } }
                ?.let { selectGamesApp(it) }
        }
    }

    /** Returns true if the data/ root grant exists for this package. */
    fun hasAccessForPackage(packageName: String): Boolean = prefs.contains(dataUriKey(packageName))

    fun addCustomApp(name: String, packageName: String) {
        val current = loadCustomApps().toMutableList()
        current.add(KnownApp(name.trim(), listOf(packageName.trim()), isCustom = true))
        saveCustomApps(current)
        refreshAppList()
    }

    fun removeCustomApp(app: GameHubApp) {
        val uri = app.known.packageNames.mapNotNull { storedDataUri(it) }.firstOrNull()
        if (uri != null) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }
        val edit = prefs.edit()
        app.known.packageNames.forEach { pkg -> edit.remove(dataUriKey(pkg)) }
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

    /**
     * Called with the URI returned from ACTION_OPEN_DOCUMENT_TREE for a given app.
     * The URI points to the app's data/ root — all sub-paths are derived from it.
     */
    fun grantAccess(app: GameHubApp, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.edit().putString(dataUriKey(app.activePackage), uri.toString()).apply()
        refreshAppList()
        selectApp(app.copy(hasAccess = true))
    }

    fun selectApp(app: GameHubApp) {
        val uri = storedDataUri(app.activePackage)
            ?: app.known.packageNames.mapNotNull { storedDataUri(it) }.firstOrNull()
            ?: return
        _uiState.update { it.copy(selectedApp = app, components = emptyList(), isLoadingComponents = true) }
        loadComponents(uri)
    }

    fun clearSelectedApp() {
        _uiState.update { it.copy(selectedApp = null, components = emptyList()) }
    }

    fun revokeAccess(app: GameHubApp) {
        val uri = app.known.packageNames.mapNotNull { storedDataUri(it) }.firstOrNull()
        if (uri != null) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }
        val edit = prefs.edit()
        app.known.packageNames.forEach { pkg -> edit.remove(dataUriKey(pkg)) }
        edit.apply()
        refreshAppList()
        if (_uiState.value.selectedApp?.known == app.known) clearSelectedApp()
        if (_uiState.value.selectedGamesApp?.known == app.known) clearSelectedGamesApp()
    }

    fun refresh() {
        val app = _uiState.value.selectedApp ?: return
        val uri = app.known.packageNames.mapNotNull { storedDataUri(it) }.firstOrNull() ?: return
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

    private fun loadComponents(dataUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingComponents = true, components = emptyList(), totalComponentCount = 0, loadedComponentCount = 0) }
            try {
                val dataRoot = withContext(Dispatchers.IO) { repo.getRootDocument(dataUri) }
                if (dataRoot == null || !dataRoot.canRead()) {
                    _uiState.update {
                        it.copy(isLoadingComponents = false, opState = OpState.Error("Cannot read folder. Re-grant access."))
                    }
                    return@launch
                }
                val componentsDir = withContext(Dispatchers.IO) { repo.navigateToComponents(dataRoot) }
                if (componentsDir == null || !componentsDir.canRead()) {
                    _uiState.update {
                        it.copy(isLoadingComponents = false, opState = OpState.Error("Components folder not found. Launch GameHub once to create it."))
                    }
                    return@launch
                }
                val dirs = withContext(Dispatchers.IO) { repo.scanComponentDirs(componentsDir) }
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

    /** Build the SAF hint URI pointing at the app's data/ root on external storage. */
    fun initialUriHintFor(packageName: String): Uri =
        Uri.parse(
            "content://com.android.externalstorage.documents/document/" +
                Uri.encode("primary:Android/data/$packageName")
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
        val dataUri = app.known.packageNames.mapNotNull { storedDataUri(it) }.firstOrNull()
            ?: return emptyList()
        return try {
            val dataRoot = withContext(Dispatchers.IO) { repo.getRootDocument(dataUri) }
                ?: return emptyList()
            if (!dataRoot.canRead()) return emptyList()
            val componentsDir = withContext(Dispatchers.IO) { repo.navigateToComponents(dataRoot) }
                ?: return emptyList()
            if (!componentsDir.canRead()) return emptyList()
            val dirs = withContext(Dispatchers.IO) { repo.scanComponentDirs(componentsDir) }
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

    /** True when the data root grant exists (used for Steam game scanning). */
    fun hasDataAccess(packageName: String): Boolean = prefs.contains(dataUriKey(packageName))

    /** Loads imported games from prefs into UiState — called at init and after add/remove. */
    private fun loadImportedGames() {
        val imported = importedGameRepo.getAll()
            .map { GameEntry(it.localId, GameType.LOCAL) }
        _uiState.update { it.copy(importedGames = imported) }
    }

    /**
     * Adds an imported game: saves to prefs, saves the name as a GameOverride,
     * writes the .iso file to Downloads/front end/, and refreshes the list.
     */
    fun addImportedGame(name: String, localId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                importedGameRepo.add(ImportedGame(name, localId))
                gameOverrideRepo.save(GameOverride(gameId = localId, customName = name))
                gameRepo.writeIsoToFrontEnd(name, localId)
            }
            loadImportedGames()
        }
    }

    /**
     * Removes an imported game: deletes from prefs, deletes the .iso from Downloads/front end/,
     * and refreshes the list.
     */
    fun removeImportedGame(localId: String) {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) {
                val gameName = importedGameRepo.getAll().firstOrNull { it.localId == localId }?.name
                importedGameRepo.remove(localId)
                gameName
            }
            if (name != null) {
                withContext(Dispatchers.IO) { gameRepo.deleteIsoFromFrontEnd(name) }
            }
            loadImportedGames()
        }
    }

    fun selectGamesApp(app: GameHubApp) {
        val uri = storedDataUri(app.activePackage)
            ?: app.known.packageNames.mapNotNull { storedDataUri(it) }.firstOrNull()
            ?: return
        _uiState.update { it.copy(selectedGamesApp = app, steamGames = emptyList(), isLoadingSteam = true) }
        loadSteamGames(uri)
    }

    fun clearSelectedGamesApp() {
        _uiState.update { it.copy(selectedGamesApp = null, steamGames = emptyList(), isLoadingSteam = false) }
    }

    fun refreshGames() {
        val app = _uiState.value.selectedGamesApp ?: return
        val uri = storedDataUri(app.activePackage)
            ?: app.known.packageNames.mapNotNull { storedDataUri(it) }.firstOrNull()
            ?: return
        _uiState.update { it.copy(steamGames = emptyList(), isLoadingSteam = true) }
        loadSteamGames(uri)
    }

    private fun loadSteamGames(dataUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSteam = true, steamGames = emptyList()) }
            try {
                val dataRoot = withContext(Dispatchers.IO) { gameRepo.getRootDocument(dataUri) }
                if (dataRoot == null || !dataRoot.canRead()) {
                    _uiState.update { it.copy(isLoadingSteam = false) }
                    return@launch
                }
                val steamRoot = withContext(Dispatchers.IO) { gameRepo.navigateToShadercache(dataRoot) }
                val steamGames = if (steamRoot != null && steamRoot.canRead()) {
                    withContext(Dispatchers.IO) { gameRepo.scanSteamGames(steamRoot) }
                } else emptyList()
                _uiState.update { it.copy(steamGames = steamGames, isLoadingSteam = false) }
                // Write ISO for each Steam game (matches imported-game behaviour).
                // Filename = resolved display name; content = Steam App ID.
                withContext(Dispatchers.IO) {
                    steamGames.forEach { game ->
                        val info = SteamRepository.fetch(game.gameId)
                        val name = gameOverrideRepo.get(game.gameId)?.customName
                            ?: info?.name
                            ?: game.gameId
                        gameRepo.writeIsoToFrontEnd(name, game.gameId)
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoadingSteam = false, opState = OpState.Error(e.message ?: "Failed to scan Steam games"))
                }
            }
        }
    }

    fun launchGame(packageName: String, gameId: String) {
        gameRepo.launchGame(packageName, gameId).onFailure { e ->
            _uiState.update { it.copy(opState = OpState.Error("Launch failed: ${e.message}")) }
        }
    }

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

    /** Single pref key covering the data/ root grant — used for both components and games. */
    private fun dataUriKey(packageName: String) = "data_uri_$packageName"

    private fun storedDataUri(packageName: String): Uri? =
        prefs.getString(dataUriKey(packageName), null)?.let { Uri.parse(it) }
}
