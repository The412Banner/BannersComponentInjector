# Banners Component Injector — Progress Log

> **Development Rule:** Always create a *new* pre-release tag (e.g., bump to v1.4.3-pre, v1.4.4-pre) when pushing a new commit and build. Never overwrite or re-tag the same pre-release version.

> **Stable Release Rule:** After every stable release, as the final step (after setting the GitHub release description and deleting old releases), rewrite the repository README.md to accurately reflect the current feature set — all built-in repos, all features, all settings, and any UI/flow changes made since the previous stable.

---

### [pre] — v2.0.1-pre — Add GameNative built-in source (2026-03-15)
**Commit:** (pending)  |  **Tag:** v2.0.1-pre  |  **Branch:** main

#### What changed
- Added GameNative as a built-in source (9th built-in repo)
- New `GAMENATIVE_MANIFEST` SourceFormat for `items.driver/dxvk/proton/fexcore/wowbox64` JSON
- `fetchGameNativeManifest()` — fetches items for a single componentType; maps `driver` key → `GPU Drivers`
- `cacheAllGameNativeManifest()` — fetches once, populates all category buckets
- `discoverTypes()` — GAMENATIVE_MANIFEST case reads manifest keys, normalises `driver` → `GPU Drivers`
- `autoDetectFormat()` — detects gamenative manifest URL before raw.githubusercontent.com fallback

#### Files touched
- `data/RemoteSourceRepository.kt`
- `ui/screens/AddRepoDialog.kt`

---

### [stable] — v2.0.0 — Export/import repo list (2026-03-15)
**Commit:** `21c3717` (code) + `9bc85c1` (README)  |  **Tag:** v2.0.0  |  **Branch:** main

#### What changed (since v1.9.7)
- Export repo list: FileDownload icon in Download Components source list header — writes `bci-repos.json` to `Downloads/BannersComponentInjector/` via MediaStore; includes custom sources, hidden defaults, source order
- Import repo list: FileUpload icon opens JSON file picker; preview dialog shows incoming custom repos, hidden built-ins, order info; Merge (default) vs Replace; applies on confirm

#### Files touched
- `data/RemoteSourceRepository.kt` — `RepoListImport`, `exportRepoListJson()`, `parseRepoListJson()`, `applyRepoListImport()`, `parseRemoteSourceObject()` helper
- `ui/screens/DownloadScreen.kt` — import launcher, state vars, export/import buttons, preview dialog, `exportRepoListToDownloads()` suspend fun
- `README.md` — Export/Import sections added

---

### [pre-release] — v1.9.8-pre — Export/import repo list (2026-03-15)
**Commit:** `21c3717`  |  **Tag:** v1.9.8-pre  |  **Branch:** main

#### What changed (since v1.9.7)
- Export repo list: FileDownload icon in Download Components source list header — writes `bci-repos.json` to `Downloads/BannersComponentInjector/` via MediaStore (overwrites existing); includes custom sources, hidden defaults, source order
- Import repo list: FileUpload icon opens JSON file picker; preview dialog shows incoming custom repos, hidden built-ins, order info; Merge (default, add new only) vs Replace (swap all custom sources); applies on confirm
- Refactored `getCustomSources()` to use shared `parseRemoteSourceObject()` helper reused by new `parseRepoListJson()`

#### Files touched
- `data/RemoteSourceRepository.kt` — `RepoListImport` data class, `exportRepoListJson()`, `parseRepoListJson()`, `applyRepoListImport()`, `parseRemoteSourceObject()` helper
- `ui/screens/DownloadScreen.kt` — import launcher, state vars, export/import buttons, preview dialog, `exportRepoListToDownloads()` suspend fun

---

### [stable] — v1.9.7 — Remove local game from list / delete folder (2026-03-13)
**Commit:** `0c1886e` (code) + `1af1fb3` (README)  |  **Tag:** v1.9.7  |  **Branch:** main

#### What changed (since v1.9.5)
- "Remove from list" — hides local game, folder untouched, persists in SharedPrefs
- "Remove and delete folder" — SAF delete of virtual_containers/<gameId>/ + ISO stub; confirmation dialog

#### Files touched
- `data/GameRepository.kt`, `viewmodel/MainViewModel.kt`, `ui/screens/MyGamesScreen.kt`, `MainActivity.kt`
- `README.md` — updated My Games section + Features bullets

---

### [pre-release] — v1.9.6-pre — Remove local game from list / delete folder (2026-03-13)
**Commit:** `b90fe2b`  |  **Tag:** v1.9.6-pre  |  **Branch:** main

#### What changed (since v1.9.5)
- "Remove from list" — hides a local game without touching the filesystem; hidden IDs stored in SharedPrefs, filtered on every scan (persists across restarts)
- "Remove and delete folder" — deletes `virtual_containers/<gameId>/` via SAF + removes `<gameId>.iso` stub; protected by confirmation dialog with spinner
- Both options appear only on local game cards, separated from Edit/Reset by a divider
- Solves the GameHub leftover virtual container problem (GH uninstalls game but leaves the folder)

#### Files touched
- `data/GameRepository.kt` — `deleteLocalGameFolder()`
- `viewmodel/MainViewModel.kt` — `hideLocalGame()`, `deleteLocalGameFolder()`, hidden IDs prefs, filter in `loadGames()`
- `ui/screens/MyGamesScreen.kt` — new menu items, confirmation dialog, new callbacks
- `MainActivity.kt` — `onHideGame` + `onDeleteGame` wired to ViewModel

---

### [stable] — v1.9.5 — My Games tab, new-component notifications (2026-03-13)
**Commit:** `09d81cd` (code) + `e2ace95` (README)  |  **Tag:** v1.9.5  |  **Branch:** main

#### What changed (since v1.9.0)
- My Games tab: launch GameHub games; browse local + Steam games; offline Steam metadata; edit cards; ISO creator
- Single `data/` grant — one SAF permission covers components, virtual containers, and shadercache
- Show My Games Tab toggle in Settings (hidden by default)
- Fix: disk I/O off main thread — cover art no longer stuck in loading state (v1.9.2)
- Fix: search thumbnails always fetch fresh from CDN; portrait → header fallback chain (v1.9.3)
- New-component dot badge on Download tab (fingerprint-based; clears on tab open) (v1.9.4)
- "NEW" chip on individual source/repo cards when that source has unseen items (v1.9.5)

#### Files touched
- `data/RemoteSourceRepository.kt`, `ui/screens/MainTabRow.kt`, `MainActivity.kt`
- `ui/screens/DownloadScreen.kt`, `ui/screens/MyGamesScreen.kt`
- `README.md` — rewritten for v1.9.5

---

### [pre-release] — v1.9.5-pre — "NEW" chip on source repo cards in Download tab (2026-03-13)
**Commit:** `8f392b7`  |  **Tag:** v1.9.5-pre  |  **Branch:** main

#### What changed (since v1.9.4-pre)
- "NEW" chip shown on individual source/repo cards in the Download Components screen when that specific source has new items (i.e. items not yet seen on a previous launch)
- Chip uses `primaryContainer` background and `onPrimaryContainer` text; 9sp bold "NEW" label
- `getNewSourceNames()` added to `RemoteSourceRepository` — derives set of source names with unacknowledged fingerprints; same prefs as the tab-level badge

#### Files touched
- `data/RemoteSourceRepository.kt` — `getNewSourceNames()`
- `ui/screens/DownloadScreen.kt` — `remember { repo.getNewSourceNames() }`, "NEW" chip in source card

---

### [pre-release] — v1.9.4-pre — New-component badge on Download tab (2026-03-14)
**Commit:** `a35fa37`  |  **Tag:** v1.9.4-pre  |  **Branch:** main

#### What changed (since v1.9.3-pre)
- On app launch, background `refreshAllCache` + fingerprint comparison detects new items in any source
- Primary-color dot badge appears on "Download Components" tab when sources have items newer than the last visit
- Badge clears + snapshot saved when user opens the Download tab
- First-run: baseline established silently — no phantom badge on fresh install

#### Files touched
- `data/RemoteSourceRepository.kt` — `currentFingerprints()`, `hasNewItems()`, `markAllAsSeen()`, prefs storage
- `ui/screens/MainTabRow.kt` — `LocalHasNewDownloads` CompositionLocal, dot badge in tab label
- `MainActivity.kt` — `hasNewDownloads` state, background LaunchedEffect, tab-switch LaunchedEffect, CompositionLocalProvider

---

### [pre-release] — v1.9.3-pre — Fix search thumbnails never loading (2026-03-14)
**Commit:** `2920f59`  |  **Tag:** v1.9.3-pre  |  **Branch:** main

#### What changed (since v1.9.2-pre)
- Fix: search result thumbnails in Edit Game sheet never loaded — old code used `error={}` (renders nothing), Coil cached 404 responses for apps without portrait cover art
- New `SearchResultThumbnail` composable: disk cache disabled, tries `library_600x900.jpg` → falls back to `header.jpg` → fallback icon

#### Files touched
- `ui/screens/MyGamesScreen.kt`

---

### [pre-release] — v1.9.2-pre — Fix partial image loading in Edit Game search (2026-03-14)
**Commit:** `d8593f9`  |  **Tag:** v1.9.2-pre  |  **Branch:** main

#### What changed (since v1.9.1-pre)
- Fix: cover thumbnails in Edit Game → Search Steam results were stuck in loading state since v1.9.1-pre added disk cache
- Root cause: synchronous SharedPreferences + JSON reads on main thread blocked Coil's image callbacks
- `SteamGameCard` now loads info entirely via `LaunchedEffect` + `fetch()` (off main thread); removed disk I/O from `remember{}`
- `LaunchedEffect(games)` now batches override reads on `Dispatchers.IO` before applying to state map

#### Files touched
- `ui/screens/MyGamesScreen.kt`

---

### [pre-release] — v1.9.1-pre — My Games tab, single grant, offline metadata (2026-03-13)
**Commit:** `96ab8e2`  |  **Tag:** v1.9.1-pre  |  **Branch:** main

#### What changed (since v1.9.0)
- My Games tab: local imports + Steam games, card metadata, hamburger edit, Steam search, ISO creator, offline metadata persistence
- Single SAF grant at `<package>/data` covers components + all game folders
- Show My Games Tab toggle in Settings (default off)
- All beta builds (b2–b10) merged to main and beta releases deleted

---

### [beta] — beta-v1.9.1-b8 — Single data/ folder grant covers all access (2026-03-13)
**Commit:** `c6c9277`  |  **Tag:** beta-v1.9.1-b8  |  **Branch:** beta

#### What changed
- **Single SAF grant** at `Android/data/<pkg>` replaces 3 separate grants
- `ComponentRepository.navigateToComponents(dataRoot)` — `files/usr/home/components/`
- `GameRepository.navigateToVirtualContainers(dataRoot)` — `files/usr/home/virtual_containers/`
- `GameRepository.navigateToShadercache(dataRoot)` — `files/Steam/steamapps/shadercache/`
- `MainViewModel`: single `data_uri_<pkg>` pref key; `hasDataAccess()`; `loadComponents()`/`loadGames()` both navigate from data root; `revokeAccess` also clears games app; `createIsoFiles` navigates to virtual_containers
- `MyGamesScreen`: one folder picker, `hasDataAccess`, `onAccessGranted`/`onRevokeAccess`, dialogs show `Android/data/<pkg>` path; `GamesAppCard` shows single "Access Granted" chip
- `AppListScreen`: grant guide updated — "navigate to Android/data/<pkg>"
- `MainActivity`: `onAccessGranted` routes to shared `vm.grantAccess()`
- ⚠️ Existing grants invalidated — users must re-grant at `data/` level

#### Files touched
- `data/ComponentRepository.kt`
- `data/GameRepository.kt`
- `viewmodel/MainViewModel.kt`
- `ui/screens/MyGamesScreen.kt`
- `ui/screens/AppListScreen.kt`
- `MainActivity.kt`

---

### [beta] — beta-v1.9.1-b7 — Show My Games Tab toggle in Settings (2026-03-13)
**Commit:** `37d2bcd`  |  **Tag:** beta-v1.9.1-b7  |  **Branch:** beta

#### What changed
- Added "Show My Games Tab" Switch in Settings → General section (default OFF)
- `LocalShowGamesTab` CompositionLocal in MainTabRow drives tab visibility
- `MainTabRow` filters GAMES tab from visible tabs when toggle is OFF
- `MainActivity`: declares `showMyGamesTab` mutableStateOf (reads `bci_settings` prefs),
  wraps content in `CompositionLocalProvider(LocalShowGamesTab provides showMyGamesTab)`,
  adds `LaunchedEffect` fallback (if tab hidden while on GAMES → switch to INJECT),
  wires toggle to all 5 SettingsSheet call sites
- `AppListScreen` + `ComponentListScreen`: accept and forward `showMyGamesTab` / `onShowMyGamesTabChanged`

#### Files touched
- `MainActivity.kt`
- `ui/screens/AppListScreen.kt`
- `ui/screens/HomeScreen.kt`
- `ui/screens/MainTabRow.kt` (CompositionLocal declared here)
- `ui/screens/SettingsSheet.kt`

---

### [stable] — v1.9.0 — Stable release (2026-03-11)
**Commit:** `86e3989` (code) / `2a64101` (docs)  |  **Tag:** v1.9.0

#### What changed (since v1.8.5)
- From My Downloads button in ComponentDetailSheet
- Inject into GameHub button in DownloadManagerScreen (two-step app→component picker, Done/Error snackbar)
- Light mode / Dark mode toggle in Settings → Appearance
- AMOLED black checkbox (disabled when Dynamic Color on)
- Dynamic Color (Material You) toggle — Android 12+; hides accent picker, disables AMOLED
- In-app changelog in update dialog (scrollable, max 180dp) and Settings update card (max 150dp)
- Update dialog size fix (fillMaxSize → fillMaxWidth)
- Markdown stripping in all changelog Text composables
- AMOLED disabled with subtitle when Dynamic Color is on

#### Files touched
- All BCI UI and data source files (multiple screens)
- README.md — full rewrite for v1.9.0

---

### [pre-release] — v1.8.9-pre — Polish fixes: inject feedback, AMOLED+DynColor, markdown strip (2026-03-11)
**Commit:** `86e3989`  |  **Tag:** v1.8.9-pre

#### What changed
- **Inject feedback:** DownloadManagerScreen shows Done/Error snackbar after inject via `opState` LaunchedEffect; `hadRecentInject` gate prevents false positives from unrelated state changes
- **AMOLED + Dynamic Color:** AMOLED checkbox is disabled when Dynamic Color is on (`amoledEnabled = isDarkMode && !isDynamicColor`); shows "Not available with Dynamic Color" subtitle
- **Markdown stripping:** changelog text in both the SettingsSheet update card and MainActivity launch-time AlertDialog now runs through `stripMarkdown()` — removes `#`, `**`, `*`, `` ` ``, converts `-`/`+` bullets to `•`, strips blockquotes

#### Files touched
- `ui/screens/DownloadManagerScreen.kt` — inject result snackbar via LaunchedEffect(opState)
- `ui/screens/SettingsSheet.kt` — apply `stripMarkdown()` to changelog Text
- `MainActivity.kt` — apply `stripMarkdown()` to changelog Text in AlertDialog (already done in previous commit, no further change)

---

### [pre-release] — v1.8.8-pre — Update dialog fix + changelog (2026-03-11)
**Commit:** `3d458a8`  |  **Tag:** v1.8.8-pre

#### What changed
- **Fix:** Update dialog during download was huge — `Modifier.fillMaxSize()` on the progress column changed to `fillMaxWidth()`; also fixed `Spacer.width` → `Spacer.height`
- **Changelog in update dialog:** scrollable "What's new" section (max 180dp) shown in the launch-time AlertDialog between version line and buttons, pulled from the GitHub release body
- **Changelog in Settings update card:** same section (max 150dp) shown in `UpdateState.Available` card in Settings → Updates
- `UpdateRepository.ReleaseInfo` now includes `body: String?` parsed from GitHub API

#### Files touched
- `data/UpdateRepository.kt` — added `body: String?` to `ReleaseInfo`
- `MainActivity.kt` — fixed dialog size, added changelog + imports
- `ui/screens/SettingsSheet.kt` — added changelog to Available card + imports

---

### [pre-release] — v1.8.7-pre — Light mode, AMOLED black, Dynamic Color (2026-03-11)
**Commit:** `0224bec`  |  **Tag:** v1.8.7-pre

#### What changed
- **Dark Mode toggle** in Appearance settings — switches between full light and dark schemes
- **AMOLED Black checkbox** (only active when dark mode is on) — true black (`#000000`) background + `#111111` surface variant; text brightened to `#EEEEEE`/`#CCCCCC` for contrast
- **Dynamic Color toggle** (Android 12+ only) — uses Material You wallpaper-derived scheme; grayed out with note on Android 11 and below; accent picker hidden while active
- **Light mode color scheme** — `lightColorScheme` built from accent, auto-darkens overly bright accents for white-surface readability
- **Tertiary color** added to both dark and light schemes (`#66BB6A` / `#388E3C`) — fixes downloaded checkmark rendering in all modes

#### Files touched
- `ui/theme/ThemePrefs.kt` — load/save for `dark_mode`, `amoled_mode`, `dynamic_color`
- `ui/theme/Theme.kt` — `buildDarkScheme(amoled)`, `buildLightScheme()`, updated `BannersComponentInjectorTheme` signature
- `ui/screens/SettingsSheet.kt` — Display Mode card + Material You card; 6 new params with defaults; accent picker conditional on dynamic color off
- `ui/screens/AppListScreen.kt` — 6 new theme params forwarded to SettingsSheet
- `ui/screens/HomeScreen.kt` — same
- `MainActivity.kt` — 3 new state vars wired to theme + all 4 SettingsSheet call sites

---

### [pre-release] — v1.8.6-pre — Inject from My Downloads (2026-03-11)
**Commit:** `426f1c7`  |  **Tag:** v1.8.6-pre

#### What changed
- **Feature 1 – "From My Downloads" in ComponentDetailSheet (Inject tab):**
  - New `FilledTonalButton` "From My Downloads" between Local File and Online Source
  - Opens `MyDownloadsPicker` bottom sheet listing all previously downloaded files (newest-first)
  - Shows source, type, size per item; resolves SAF or MediaStore URI and injects
  - Integrated into the no-backup warning flow (refactored pending source tracking to `ReplaceSource` enum)
- **Feature 2 – "Inject into GameHub" button in My Downloads tab:**
  - `SystemUpdateAlt` icon button on each file in the type→file list
  - Opens `InjectPickerSheet`: Step 1 = pick accessible GameHub app, Step 2 = load & pick component
  - Loading indicator while components are fetched; back button to return to app selection
  - Snackbar feedback on injection start
- **`MainViewModel.getComponentsForApp()`:** new suspend function loads components for any app without touching main UI state

#### Files touched
- `ui/screens/ComponentDetailSheet.kt` — ReplaceSource enum, From My Downloads button, MyDownloadsPicker composable, resolveDownloadUri helper
- `ui/screens/DownloadManagerScreen.kt` — new params (apps/onGetComponentsForApp/onInjectInto), Inject button per file, InjectPickerSheet composable, resolveManagerDownloadUri helper
- `viewmodel/MainViewModel.kt` — getComponentsForApp() suspend fun
- `MainActivity.kt` — wires apps/onGetComponentsForApp/onInjectInto to DownloadManagerScreen

---

### [stable] — v1.8.5 — Stable release (2026-03-10)
**Commit:** `8f9b765` (code) | `3e85a4d` (docs)  |  **Tag:** v1.8.5

#### What changed since v1.8.0
- Custom app entries (+ button in Inject tab)
- Multi-install dropdown picker for same-group variants
- GameHub label detection (excludes real apps sharing package names)
- Report Issue / Feedback button in Settings → Utilities
- Clickable URLs in release notes (DownloadScreen + RemoteSourceSheet)
- Check for updates on launch toggle (Settings → Updates)
- Bug fix: null safety for `DocumentFile.listFiles()` in ComponentRepository + BackupManager (9 call sites)

#### Files touched
- All code changes from v1.8.1-pre through v1.8.4-pre
- README.md rewritten to reflect all v1.8.5 features

---

### [pre-release] — v1.8.4-pre — Null safety fixes for DocumentFile.listFiles() (2026-03-10)
**Commit:** `28a21b2`  |  **Tag:** v1.8.4-pre

#### What changed
- `ComponentRepository`: added null safety (`?.` / `?: emptyList()`) on every `listFiles()` call — `scanComponentDirs`, `collectFilesRecursively`, `replaceWithWcp` (clear step), `restoreComponent` (clear step), `deleteRecursively` extension
- `BackupManager`: same null safety applied to `listAllBackups`, `copyDocumentToDownloads`, `copyDocumentToDocumentFile`, `collectDocumentFiles`, `countFilesInDoc`
- Prevents NPE crashes when SAF provider returns null on low-storage, permission edge cases, or provider failures

#### Files touched
- `app/src/main/java/com/banner/inject/data/ComponentRepository.kt`
- `app/src/main/java/com/banner/inject/data/BackupManager.kt`

---

### [pre-release] — v1.8.3-pre — Check for updates on launch (2026-03-10)
**Commit:** `1bc62b3`  |  **Tag:** v1.8.3-pre

#### What changed
- `SettingsSheet`: new "Check for updates on launch" `Switch` row in Updates section; pref key `update_check_on_launch`, default `false`; separated from Include pre-releases by `HorizontalDivider`
- `MainActivity`: `LaunchedEffect(Unit)` reads pref; if enabled, calls `UpdateRepository.fetchLatestRelease(includePreReleases)`; if update found, sets `launchUpdateRelease` state
- `AlertDialog` shown when `launchUpdateRelease != null`: shows tag name, installed version, Download & Install with `mutableFloatStateOf` progress %, View on GitHub, Not Now; non-dismissible while downloading

#### Files touched
- `app/src/main/java/com/banner/inject/MainActivity.kt`
- `app/src/main/java/com/banner/inject/ui/screens/SettingsSheet.kt`

---

### [pre-release] — v1.8.2-pre — Clickable URLs in release notes (2026-03-10)
**Commit:** `1899e0b`  |  **Tag:** v1.8.2-pre

#### What changed
- `LinkedText` composable added to `DownloadScreen.kt` (accessible across the package): detects URLs via `android.util.Patterns.WEB_URL`, builds `AnnotatedString` with `SpanStyle` (primary color + underline), opens via `ACTION_VIEW` on tap via `ClickableText`
- Release notes `Text()` replaced with `LinkedText()` in `DownloadScreen.kt` detail sheet
- Release notes `Text()` replaced with `LinkedText()` in `RemoteSourceSheet.kt` detail sheet

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`
- `app/src/main/java/com/banner/inject/ui/screens/RemoteSourceSheet.kt`

---

### [pre-release] — v1.8.1-pre — Report Issue / Feedback button in Settings (2026-03-10)
**Commit:** `939f9f5`  |  **Tag:** v1.8.1-pre

#### What changed
- Added "Report Issue / Feedback" `OutlinedButton` to Settings → Utilities section
- Opens `https://github.com/The412Banner/BannersComponentInjector/issues` via `ACTION_VIEW` intent
- Uses `Icons.Default.BugReport` icon

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/SettingsSheet.kt`

---

### [stable] — v1.8.0 — Stable release: custom app entries, multi-install picker, GameHub label detection (2026-03-10)
**Commit:** `f052d60` (code) | `b8413fb` (docs)  |  **Tag:** v1.8.0

#### What changed (new since v1.7.0)
- **Custom app entries** — + button in Inject tab top bar; `AddCustomAppDialog` (name + package name, duplicate check); trash icon with confirm dialog to remove; `KnownApp.isCustom`; `addCustomApp()` / `removeCustomApp()` / `loadCustomApps()` / `saveCustomApps()` in ViewModel; persisted as JSON in SharedPreferences
- **Multi-installed variant picker** — `GameHubApp.installedPackages`; `DropdownMenu` in `AppCard` when multiple packages from the same group are installed; per-package Access badge; each variant stores its own independent SAF URI; `hasAccessForPackage()` in ViewModel
- **GameHub label detection** — `isLikelyGameHub()` helper; borrowed package names (com.tencent.ig, com.mihoyo.genshinimpact, com.antutu.*, com.ludashi.*) verified against app display label; real apps excluded
- README.md fully rewritten

#### Files touched
- `app/src/main/java/com/banner/inject/model/Models.kt`
- `app/src/main/java/com/banner/inject/viewmodel/MainViewModel.kt`
- `app/src/main/java/com/banner/inject/ui/screens/AppListScreen.kt`
- `app/src/main/java/com/banner/inject/MainActivity.kt`
- `README.md`

---

### [pre-release] — v1.7.3-pre — Fix: real apps with borrowed package names excluded from GameHub list (2026-03-10)
**Commit:** `f052d60`  |  **Tag:** v1.7.3-pre

#### What changed
- `isLikelyGameHub(pm, packageName, isCustom)`: new helper in `MainViewModel`
  - Packages with "gamehub" in name → always trusted
  - Custom apps → always trusted
  - Borrowed package names (e.g. `com.tencent.ig`, `com.mihoyo.genshinimpact`, `com.antutu.*`, `com.ludashi.*`) → label checked for "GameHub" or "Game Hub"
  - Real apps (PUBG Mobile, Genshin Impact, AnTuTu, etc.) fail the label check and are excluded
- `refreshAppList()`: `installedPkgs` filter now calls `isLikelyGameHub()` after detecting install

#### Files touched
- `app/src/main/java/com/banner/inject/viewmodel/MainViewModel.kt`

---

### [pre-release] — v1.7.2-pre — Dropdown package picker for multi-installed variants (2026-03-10)
**Commit:** `5017718`  |  **Tag:** v1.7.2-pre

#### What changed
- `GameHubApp`: added `installedPackages: List<String>` — all packages from the group that are actually installed
- `refreshAppList()`: collects ALL installed packages per group (not just first); custom apps get their own package in `installedPackages`
- `grantAccess()`: stores URI only for `activePackage` (not all group members) — independent URIs per variant
- `selectApp()`: prioritizes `activePackage` URI, falls back to any in group
- `hasAccessForPackage(pkg): Boolean`: new ViewModel function for per-package access check
- `AppCard`: `onMultiPackageSelect` callback + `hasAccessForPackage` param; `DropdownMenu` with per-package Access badges when `installedPackages.size > 1`; `ArrowDropDown` icon; subtitle shows "N variants installed — tap to select"
- `AppListScreen`: passes `hasAccessForPackage` lambda; builds `effectiveApp` with correct `activePackage` and `hasAccess` on dropdown selection
- `MainActivity`: wires `hasAccessForPackage`

#### Files touched
- `app/src/main/java/com/banner/inject/model/Models.kt`
- `app/src/main/java/com/banner/inject/viewmodel/MainViewModel.kt`
- `app/src/main/java/com/banner/inject/ui/screens/AppListScreen.kt`
- `app/src/main/java/com/banner/inject/MainActivity.kt`

---

### [pre-release] — v1.7.1-pre — Custom app entries in the Inject tab app list (2026-03-10)
**Commit:** `e6f6d12`  |  **Tag:** v1.7.1-pre

#### What changed
- `KnownApp`: added `isCustom: Boolean = false` flag
- `MainViewModel`: `addCustomApp()`, `removeCustomApp()`, `loadCustomApps()`, `saveCustomApps()` — JSON persistence in `bci_prefs` under `"custom_gamehub_apps"` key
- `refreshAppList()`: merges `KNOWN_GAMEHUB_APPS + loadCustomApps()`; custom apps forced to `isInstalled = true` so card is always tappable (Android 11+ blocks package visibility for unknown packages)
- `AppListScreen`: `+` (AddCircleOutline) button in top bar; `AddCustomAppDialog` (name + package name fields, duplicate check); trash icon on custom app cards; delete confirm dialog; two new callbacks `onAddCustomApp` / `onRemoveCustomApp`
- `MainActivity`: wired new callbacks to ViewModel

#### Files touched
- `app/src/main/java/com/banner/inject/model/Models.kt`
- `app/src/main/java/com/banner/inject/viewmodel/MainViewModel.kt`
- `app/src/main/java/com/banner/inject/ui/screens/AppListScreen.kt`
- `app/src/main/java/com/banner/inject/MainActivity.kt`

---

### [stable] — v1.7.0 — Stable release: GPU Drivers, release tags, repo ordering, edit dialog improvements (2026-03-10)
**Commit:** `f277636` (code) | `afbe90d` (docs)  |  **Tag:** v1.7.0

#### What changed (new since v1.6.0)
- **GPU Drivers unified category** — Turnip, Adreno, Qualcomm, Mesa files combined into single "GPU Drivers" category across all sources; `GPU_DRIVER_TYPE` constant; `normalizeGpuTypes()` helper
- **FEX/FEXCore filename bridge** — files named `fex-*.wcp` visible under `fexcore` type and vice versa
- **Nightlies by The412Banner built-in source** — 8th built-in; GITHUB_RELEASES_WCP format
- **HUB Emulators (T3st31) built-in source** — 7th built-in; RANKING_EMULATORS_JSON format
- **Release tag browsing** — `RemoteSource.releaseTags` field; `discoverReleaseTags()`; `fetchGithubReleaseByTag()` returns all assets any file type; `typesToShow` appends enabled release tags
- **Edit dialog — full merged type list** — union of configured + discovered types; "new" badge on newly found; existing types never lost
- **Edit dialog — Additional Releases section** — separate section below divider; individual GitHub release names listed; each can be toggled independently; Select All / Deselect All
- **Repo reordering** — Move Up / Move Down in each repo's hamburger menu; order persisted in SharedPreferences `source_order`
- README.md fully rewritten to reflect all current features

#### Files touched
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`
- `app/src/main/java/com/banner/inject/ui/screens/EditRepoDialog.kt`
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`
- `app/src/main/java/com/banner/inject/ui/screens/RemoteSourceSheet.kt`
- `README.md`

---

### [pre-release] — v1.6.8-pre — Opt-in individual GitHub releases as browseable categories (2026-03-10)
**Commit:** `29473bf`  |  **Tag:** v1.6.8-pre

#### What changed
- `RemoteSource.releaseTags: List<String>` — new field for user-opted release name categories
- `getCustomSources` / `saveCustomSources`: serialize/deserialize `releaseTags`
- `discoverReleaseTags(source)`: fetches all release names from GITHUB_RELEASES_* URLs
- `fetchGithubReleaseByTag(url, tag, sourceName)`: returns ALL assets from matching release (any file type)
- `fetchFromSource`: routes to `fetchGithubReleaseByTag` when `componentType in source.releaseTags`
- `refreshAllCache`: also caches `releaseTags` entries
- `EditRepoDialog`: new "Additional Releases" section (below divider); parallel discovery on open; pre-checks saved tags; "new" label on newly discovered; Select All / Deselect All per section; save persists to `releaseTags`
- `DownloadScreen` + `RemoteSourceSheet`: `typesToShow` appends `source.releaseTags` after component types

#### Files touched
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`
- `app/src/main/java/com/banner/inject/ui/screens/EditRepoDialog.kt`
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`
- `app/src/main/java/com/banner/inject/ui/screens/RemoteSourceSheet.kt`

---

### [pre-release] — v1.6.7-pre — Edit dialog shows full merged type list (2026-03-10)
**Commit:** `2fe41bc`  |  **Tag:** v1.6.7-pre

#### What changed
- `typesToShow` = union of `source.supportedTypes` (first, pre-checked) + `discoveredTypes` (appended, unchecked by default)
- Currently-configured types never silently dropped when discovery partially fails
- Newly discovered types labelled "new" in primary color so user knows they're additional
- Save logic filters `typesToShow` (not just `discoveredTypes`) so nothing is lost
- "No types detected" message only shows when `typesToShow` is truly empty
- Select All / Deselect All operate on the full merged list

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/EditRepoDialog.kt`

---

### [pre-release] — v1.6.6-pre — Custom repo order (Move Up / Move Down) (2026-03-10)
**Commit:** `575231d`  |  **Tag:** v1.6.6-pre

#### What changed
- `saveSourceOrder(orderedNames)` / `getSourceOrder()` persist order in SharedPreferences (`source_order` key, newline-separated names)
- `getAllSources()` applies saved order; unknown names go at end
- `removeSource()` removes the source name from saved order
- `restoreDefaultSources()` also clears `source_order` so defaults revert to natural order
- DownloadScreen + RemoteSourceSheet: `items()` → `itemsIndexed()`; **Move Up** and **Move Down** added at top of each repo's hamburger menu (disabled at list boundaries); list reloads immediately after each move

#### Files touched
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`
- `app/src/main/java/com/banner/inject/ui/screens/RemoteSourceSheet.kt`

---

### [pre-release] — v1.6.5-pre — GPU Drivers unified category (2026-03-10)
**Commit:** `2ba1b2e`  |  **Tag:** v1.6.5-pre

#### What changed
- `GPU_DRIVER_TYPE = "GPU Drivers"` and `GPU_DRIVER_KEYWORDS = [turnip, adreno, qualcomm, mesa]` constants added to companion object
- All GPU driver source `supportedTypes` and `extraEndpoints.types` updated to use "GPU Drivers" instead of individual keywords
- `normalizeGpuTypes()` helper collapses any GPU keyword list into a single "GPU Drivers" entry
- `fetchFromSource`: routes "GPU Drivers" to the correct extra endpoint; passes blank filterKeyword to TURNIP format
- `fetchWcpJson`: matches any GPU keyword when componentType is GPU Drivers
- `fetchGithubReleasesWcp`: gpuMatch condition — matches any GPU keyword file for GPU Drivers type
- `fetchRankingEmulators`: skips manifestDrivers for GPU Drivers; accepts all Drivers-category assets matching any GPU keyword
- `refreshAllCache`: TURNIP primary + extra endpoint — GPU Drivers gets full unfiltered item list
- `cacheAllRankingEmulators`: GPU driver assets cached under single "GPU Drivers" bucket instead of per-keyword
- `discoverTypes`: "mesa" added to knownTypes; TURNIP returns `["GPU Drivers"]`; WCP/ZIP apply `normalizeGpuTypes()`; RANKING appends "GPU Drivers" if any GPU keyword asset found
- `componentTypes` in DownloadScreen and RemoteSourceSheet updated (removed turnip/adreno/qualcomm, added GPU_DRIVER_TYPE)

#### Files touched
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`
- `app/src/main/java/com/banner/inject/ui/screens/RemoteSourceSheet.kt`

---

### [pre-release] — v1.6.4-pre — Nightlies built-in source + fex/fexcore filename bridging (2026-03-10)
**Commit:** `0be72a5`  |  **Tag:** v1.6.4-pre

#### What changed
- Added "Nightlies by The412Banner" as 8th built-in source (`GITHUB_RELEASES_WCP`)
  - URL: `https://api.github.com/repos/The412Banner/Nightlies/releases`
  - Types: dxvk, vkd3d, fex, fexcore, box64
- `fetchGithubReleasesWcp`: fex/fexcore bridge — when componentType is "fexcore", also matches assets containing "fex" in the name (and vice versa). Fixes FEX-named WCP files not showing under fexcore component folders.
- `discoverTypes` (GITHUB_RELEASES_WCP): same bridge applied — scanning releases with "fex"-named assets now surfaces both "fex" and "fexcore" as available types.

#### Files touched
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`

---

### [pre-release] — v1.6.3-pre — HUB Emulators (T3st31) built-in source + RANKING_EMULATORS_JSON format (2026-03-09)
**Commit:** `f277636` (docs: `c439587`)  |  **Tag:** v1.6.3-pre

#### What changed
- New `SourceFormat.RANKING_EMULATORS_JSON` for sites serving a `rankings.json`
- `fetchRankingEmulators()`: WCP types from `manifestDrivers` section; GPU driver types from `results[Drivers]` assets filtered by keyword
- `cacheAllRankingEmulators()`: fetches JSON once, caches all types in one pass
- `discoverTypes()`: reads manifestDrivers keys + scans Drivers assets for turnip/adreno/qualcomm
- `DownloadScreen` + `RemoteSourceSheet`: trigger `discoverTypes` for RANKING_EMULATORS_JSON (same path as GITHUB_REPO_CONTENTS)
- `AddRepoDialog.autoDetectFormat`: detects `t3st31.github.io` or `rankings.json` URLs
- **HUB Emulators (T3st31)** added as 7th default built-in source

#### Files touched
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`
- `app/src/main/java/com/banner/inject/ui/screens/RemoteSourceSheet.kt`
- `app/src/main/java/com/banner/inject/ui/screens/AddRepoDialog.kt`

---

### [pre-release] — v1.6.2-pre — Fix Qualcomm/non-turnip assets hidden by filename filter (2026-03-09)
**Commit:** `43f4888`  |  **Tag:** v1.6.2-pre

#### What changed
- `fetchTurnipReleases` gains optional `filterKeyword` param; when set, filters assets by keyword; when empty, returns all assets
- `fetchFromSource` passes `componentType` as the keyword — direct browse now correctly shows only matching assets per type
- `refreshAllCache` (TURNIP format, primary + extraEndpoints): fetches all assets once unfiltered, then filters per type before caching
- Root cause: previously everything was filtered to "turnip" keyword regardless of selected type, so Qualcomm-named assets were silently dropped

#### Files touched
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`

---

### [pre-release] — v1.6.1-pre — Add Qualcomm component category (2026-03-09)
**Commit:** `a29802e`  |  **Tag:** v1.6.1-pre

#### What changed
- Added "qualcomm" to all GPU-driver repo `supportedTypes` (StevenMXZ, Arihany, K11MCH1, freedreno Turnip CI) and their extraEndpoints
- Added "qualcomm" to `knownTypes` and `GITHUB_RELEASES_TURNIP` default type return in `RemoteSourceRepository`
- Added "qualcomm" to `componentTypes` fallback list in `DownloadScreen` and `RemoteSourceSheet`

#### Files touched
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`
- `app/src/main/java/com/banner/inject/ui/screens/RemoteSourceSheet.kt`

---

### [stable] — v1.6.0 — Cross-repo search, batch downloads, detail sheets, pull-to-refresh, parity (2026-03-09)
**Commit:** `cd38edd`  |  **Tag:** v1.6.0

#### New since v1.5.5
- Parallel + streaming component scanner with live progress counter
- Pull-to-refresh on Inject tab component list
- Cross-repo search (always-visible) in Download Components tab
- File sizes shown on download items
- Release Notes (GitHub release body) in file detail sheets
- Tap-to-detail: file cards open detail sheet before downloading (both tabs)
- Batch multi-select downloads with parallel execution and skip-already-downloaded
- Verify Downloads / pull-to-refresh on My Downloads tab (prunes stale records)
- Parity: Inject → Online Sources now has hamburger repo menu + tap-to-detail + cross-repo search, matching Download Components tab

#### Files touched
- README.md (rewritten), PROGRESS_LOG.md
- `ui/screens/HomeScreen.kt`, `ui/screens/DownloadScreen.kt`, `ui/screens/DownloadManagerScreen.kt`, `ui/screens/RemoteSourceSheet.kt`
- `data/RemoteSourceRepository.kt`, `viewmodel/MainViewModel.kt`, `data/ComponentRepository.kt`

---

### [pre-release] — v1.5.13-pre — Parity: hamburger menu + tap-to-detail in RemoteSourceSheet (2026-03-09)
**Commit:** `6e4f85a`  |  **Tag:** v1.5.13-pre

#### What changed
- **RemoteSourceSheet repo cards**: replaced plain Delete button with `MoreVert` hamburger dropdown matching DownloadScreen — "Open in Browser", "Edit Repository", "Remove Repository"
- **RemoteSourceSheet file cards (step 3)**: tap now opens detail sheet instead of immediate download; shows name, source/type chips, published date+size, scrollable "Release Notes", "Download & Replace" button
- **RemoteSourceSheet search results**: same — tap opens detail sheet instead of immediate download
- Imports added: `Intent`, `LocalContext`, `rememberScrollState`, `verticalScroll`, `MoreVert`, `OpenInBrowser`, `Edit`, `Download`, `CheckCircle`
- Implements PARITY RULE: all features from Download Components tab now mirrored in Inject → Online Sources

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/RemoteSourceSheet.kt`

---

### [pre-release] — v1.5.12-pre2 — Pull-to-refresh on My Downloads tab (2026-03-09)
**Commit:** `e1345bf`  |  **Tag:** v1.5.12-pre2

#### What changed
- Swipe-down pull-to-refresh in My Downloads runs `pruneStaleDownloadRecords`, refreshes `downloads` list, shows snackbar if stale records removed (silent if all up to date)
- Uses `PullToRefreshContainer` + `rememberPullToRefreshState` from `material3.pulltorefresh`; content wrapped in `Box` with `nestedScroll`
- Fix: `LaunchedEffect` moved after `var downloads` declaration to avoid "Unresolved reference" compile error

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/DownloadManagerScreen.kt`

---

### [pre-release] — v1.5.11-pre — Description field, tap-to-detail, verify downloads in My Downloads (2026-03-09)
**Commit:** `a66f8e4`  |  **Tag:** v1.5.11-pre

#### What changed
- **`RemoteItem.description`**: new nullable field; populated from GitHub release `body` for GITHUB_RELEASES_WCP/ZIP/TURNIP; null for WCP JSON and GITHUB_REPO_CONTENTS (no description available in those formats)
- **Tap-to-detail in Download Components tab**: tapping a file opens a `ModalBottomSheet` detail sheet showing file name, source+type chips, published date+size, scrollable "Release Notes" section (if description non-null), then a "Download to Device" button; same for cross-repo search results
- **Verify Downloads moved** from Download Components header → My Downloads top bar; pruning now also refreshes `downloads` state live
- Removed `isVerifying` state from DownloadScreen; `SearchContent` refactored to drop all download logic (now takes `onShowDetail` callback only)

#### Files touched
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`
- `app/src/main/java/com/banner/inject/ui/screens/DownloadManagerScreen.kt`

---

### [pre-release] — v1.5.10-pre — Verify Downloads button (2026-03-09)
**Commit:** `19f1194`  |  **Tag:** v1.5.10-pre

#### What changed
- **Verify Downloads** (`CloudSync` icon button) in Download Components tab header, next to Refresh All
- `RemoteSourceRepository.pruneStaleDownloadRecords(context: Context): Int`: checks each download record's URI against the actual filesystem; removes stale records; returns count removed
- `content://` URIs verified via `ContentResolver.query()` (row count == 0 → stale); file/path URIs via `File.exists()`
- Snackbar: "Removed X stale download record(s)" or "All download records are up to date"
- Spinner shown while running; disabled while downloading/loading

#### Files touched
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`

---

### [pre-release] — v1.5.9-pre5 — Cross-repo search at step 1 of online sources (Inject tab) (2026-03-09)
**Commit:** `c9a56e8`  |  **Tag:** v1.5.9-pre5

#### What changed
- **Cross-repo search at step 1 of RemoteSourceSheet**: search field at the top of the "Select Online Repository" screen, before any repo is chosen
- `LaunchedEffect(searchQuery)` triggers `repo.refreshAllCache(sources, componentTypes)` if cache is cold
- When query is non-empty, shows `RemoteSourceRepository.searchCache(query)` results instead of repo list
- Result cards show file name + source name + component type; tapping downloads to temp and calls `onDownloadAndReplace`
- Back handler: clears `searchQuery` before navigating; if already empty, navigates back normally
- Removed file-level filter that was added at step 3 in pre4

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/RemoteSourceSheet.kt`

---

### [pre-release] — v1.5.9-pre4 — Search/filter bar in online sources file list (2026-03-09)
**Commit:** `fa44359`  |  **Tag:** v1.5.9-pre4

#### What changed
- **Search/filter bar in RemoteSourceSheet file list (step 3)**: filter field above the file list when browsing online sources to replace a component in the Inject tab
- Filters files by `displayName` as you type (case-insensitive, trims whitespace)
- `displayedItems = sortedItems.filter { ... }` — respects current sort order
- Back button clears `fileSearchQuery` before navigating up to type selection
- Removed incorrectly placed search bar from local component list (`HomeScreen`)

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/RemoteSourceSheet.kt`
- `app/src/main/java/com/banner/inject/ui/screens/HomeScreen.kt` (reverted pre3 search bar)

---

### [pre-release] — v1.5.9-pre3 — Search bar on Inject tab component list (2026-03-09)
**Commit:** `32326dc`  |  **Tag:** v1.5.9-pre3

#### What changed
- **Search/filter bar on Inject tab**: always-visible `OutlinedTextField` in the top bar (below tab row) in `ComponentListScreen`
- Filters the component list by folder name as you type (case-insensitive, trims whitespace)
- Count label updates to "X of Y components" when a filter is active
- No-match empty state with `SearchOff` icon and "No components match …" message
- Hint text ("Tap a component to backup or replace its contents") hidden while search query is active

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/HomeScreen.kt`

---

### [pre-release] — v1.5.9-pre2 — Always-visible search bar on Download tab (2026-03-09)
**Commit:** `055d75a`  |  **Tag:** v1.5.9-pre2

#### What changed
- Moved the cross-repo search field from a collapsible icon to an always-visible `OutlinedTextField` above the repo list in `DownloadScreen`
- Search field is visible at all times (not just in search mode)
- Pressing Back clears search query before closing

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`

---

### [pre-release] — v1.5.9-pre — Cross-repo search on Download tab (2026-03-09)
**Commit:** `5398adb`  |  **Tag:** v1.5.9-pre

#### What changed
- **Cross-repo search** in the Download Components tab: search field in top bar searches across all cached repositories
- `RemoteSourceRepository.searchCache(query)`: case-insensitive search across all cached items, returns grouped results by source+type
- Auto-caches all repos in background when search query is entered and cache is empty
- `SearchContent` composable: shows spinner while caching, groups results by repo/type, "no results" state
- `BackHandler` clears search query on back press when search is active

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`

---

### [pre-release] — v1.5.8-pre-2 — Fix explicit PullToRefresh imports (2026-03-09)
**Commit:** `4566160`  |  **Tag:** v1.5.8-pre-2

#### What changed
- Fixed build failure: `PullToRefreshContainer` and `rememberPullToRefreshState` are in `androidx.compose.material3.pulltorefresh` sub-package, not covered by `material3.*` wildcard — added explicit imports to all screens using pull-to-refresh

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/HomeScreen.kt`
- (other screens using PullToRefresh)

---

### [pre-release] — v1.5.8-pre — Pull-to-refresh, batch downloads, file sizes, download resume (2026-03-09)
**Commit:** `5983c12`  |  **Tag:** v1.5.8-pre

#### What changed
- **Pull-to-refresh** on Inject tab component list (`ComponentListScreen`) using Material3 `PullToRefreshContainer`
- **Batch downloads** on Download tab: multi-select mode with checkboxes; "Download X files" button downloads all selected in parallel
- **File sizes** on download items: size shown in subtitle (from GitHub API `size` field)
- **Download resume**: skips already-downloaded files in batch mode (checks `isDownloaded`)

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`

---

### [pre-release] — v1.5.7-pre — Streaming component scan with live progress counter (2026-03-09)
**Commit:** `f55d647`  |  **Tag:** v1.5.7-pre

#### What changed
- **Live loading counter**: component list shows "Loading X / Y components..." with a small spinner while scanning
- `ComponentListScreen` gains `totalComponentCount` + `loadedComponentCount` parameters
- Scan emits each component as it's found (streaming) so the list populates progressively

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/banner/inject/viewmodel/MainViewModel.kt`
- `app/src/main/java/com/banner/inject/data/ComponentRepository.kt`

---

### [pre-release] — v1.5.6-pre — Parallel component scanning with semaphore (2026-03-09)
**Commit:** `fbfcd9f`  |  **Tag:** v1.5.6-pre

#### What changed
- **Parallel scanning**: component folder scans now run concurrently with a semaphore to limit parallelism and avoid ANR
- Significantly faster load times on large component trees

#### Files touched
- `app/src/main/java/com/banner/inject/data/ComponentRepository.kt`

---

### [stable] — v1.5.5 — Repository Merging, Edit Repo, MTR & Multi-URL (2026-03-09)
**Commit:** `11ca506`  |  **Tag:** v1.5.5

#### New since v1.5.3
- Edit Repository (rename, URL, type selection with auto-detect)
- GITHUB_REPO_CONTENTS format (plain GitHub repo URL → folder structure)
- MTR (MaxesTechReview) uses GITHUB_REPO_CONTENTS; plain github.com URL stored
- Merged composite repos: StevenMXZ and Arihany each one card covering WCP + Turnip/Adreno
- ExtraEndpoint system replacing secondaryUrl/Format/Types — supports N extra URLs
- Multi-URL custom repos in AddRepoDialog: dynamic URL fields (add/remove), auto-detect + type discovery per URL

#### Files touched (since v1.5.3)
- `RemoteSourceRepository.kt`, `DownloadScreen.kt`, `RemoteSourceSheet.kt`, `AddRepoDialog.kt`, `EditRepoDialog.kt` (new)

---

### [pre-release] — v1.5.4-pre-5 — Multi-URL custom repos (2026-03-09)
**Commit:** `11ca506`  |  **Tag:** v1.5.4-pre-5

#### What changed
- `RemoteSource.secondaryUrl/Format/Types` replaced with `extraEndpoints: List<ExtraEndpoint>` supporting N extra URLs
- `ExtraEndpoint` data class: url + format + types
- Built-in StevenMXZ and Arihany migrated to `extraEndpoints`
- `fetchFromSource`: routes to first ExtraEndpoint matching the type
- `refreshAllCache`: iterates all ExtraEndpoints and fetches each with its format
- `discoverTypes`: composite short-circuits to `supportedTypes`
- `saveCustomSources`/`getCustomSources`: serialize/deserialize `extraEndpoints` in JSON
- `AddRepoDialog`: rewritten with dynamic URL fields; "Add another URL" button / "−" remove button; single URL = simple source; multiple URLs = format-detect + discoverTypes per URL, primary + extras built automatically; takes `repo` param for discoverTypes calls
- `DownloadScreen` + `RemoteSourceSheet`: pass `repo =` to AddRepoDialog

#### Files touched
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`
- `app/src/main/java/com/banner/inject/ui/screens/AddRepoDialog.kt`
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`
- `app/src/main/java/com/banner/inject/ui/screens/RemoteSourceSheet.kt`

---

### [pre-release] — v1.5.4-pre-4 — Merged composite repos (2026-03-09)
**Commit:** `4ea7547`  |  **Tag:** v1.5.4-pre-4

#### What changed
- `RemoteSource` gains `secondaryUrl`, `secondaryFormat`, `secondaryTypes` fields for composite sources
- StevenMXZ merged with Adreno-Tools-Drivers (StevenMXZ) → one entry: WCP JSON for WCP types + GITHUB_RELEASES_ZIP for turnip/adreno
- Arihany WCPHub merged with Arihany WCPHub (Turnip) → one entry: GITHUB_RELEASES_WCP for WCP types + GITHUB_RELEASES_TURNIP for turnip/adreno
- Default sources reduced from 8 to 6
- `fetchFromSource`: routes to `secondaryUrl`/`secondaryFormat` when `componentType in secondaryTypes`
- `refreshAllCache`: fetches primary and secondary groups separately with their correct formats
- `discoverTypes`: composite sources short-circuit to `supportedTypes` (no network scan)

#### Files touched
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`

---

### [pre-release] — v1.5.4-pre-3 — Plain GitHub URL for MTR (2026-03-09)
**Commit:** `79bb8f4`  |  **Tag:** v1.5.4-pre-3

#### What changed
- MTR default source URL changed to `https://github.com/maxjivi05/Components` (plain GitHub URL)
- Added `normalizeContentsUrl()` private helper in `RemoteSourceRepository`: converts `github.com/{owner}/{repo}` to API `/contents` URL before any network call — both plain and API URLs work for `GITHUB_REPO_CONTENTS` sources
- `fetchGithubRepoContents()` and `discoverTypes()` GITHUB_REPO_CONTENTS branch both use `normalizeContentsUrl()`

#### Files touched
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`

---

### [pre-release] — v1.5.4-pre-2 — GitHub Repo Contents format (MTR) (2026-03-09)
**Commit:** `e8e7de3`  |  **Tag:** v1.5.4-pre-2

#### What changed
- `RemoteSourceRepository.fetchGithubRepoContents()`: fetches `{url}/{folderName}` via GitHub Contents API; returns `.wcp`/`.zip` files as `RemoteItem` list using `download_url`; `publishedAt = null` (no dates from Contents API)
- MTR default source already set to `GITHUB_REPO_CONTENTS` format pointing at `api.github.com/repos/maxjivi05/Components/contents`
- `discoverTypes()` GITHUB_REPO_CONTENTS branch: lists non-hidden directories from repo root with original casing (for correct API paths)
- `refreshAllCache()` GITHUB_REPO_CONTENTS branch: discovers folders then fetches each
- `DownloadScreen` + `RemoteSourceSheet`: added `dynamicTypes`/`isLoadingTypes` state + `LaunchedEffect(selectedSource)` that auto-calls `discoverTypes()` for Contents-format repos; shows "Detecting types..." spinner; `typesToShow` uses dynamic folder names for that format
- `AddRepoDialog.sanitizeUrl()`: plain `github.com/{owner}/{repo}` → `api.github.com/repos/{owner}/{repo}/contents`
- `AddRepoDialog.autoDetectFormat()`: returns `GITHUB_REPO_CONTENTS` when URL ends with `/contents` (no extra network call needed)

#### Files touched
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`
- `app/src/main/java/com/banner/inject/ui/screens/RemoteSourceSheet.kt`
- `app/src/main/java/com/banner/inject/ui/screens/AddRepoDialog.kt`

---

### [pre-release] — v1.5.4-pre — Edit Repository (2026-03-09)
**Commit:** `b050609`  |  **Tag:** v1.5.4-pre

#### What changed
- **Edit Repository** option added to each repo card's hamburger menu in Download Components tab
- `EditRepoDialog` (new file): name + URL fields; auto-detects types from the live repo on open; checkboxes for each discovered type pre-checked per current config; ⟳ refresh button re-detects with updated URL; Select All / Deselect All shortcuts
- `RemoteSourceRepository.discoverTypes()`: fetches repo once, extracts unique types — WCP JSON uses `type` field, GitHub Releases scans asset names against known types list, Turnip always returns `["turnip","adreno"]`
- `RemoteSourceRepository.editSource()`: in-place update for custom sources; for defaults, marks original removed and saves edited version as custom

#### Files touched
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`
- `app/src/main/java/com/banner/inject/ui/screens/EditRepoDialog.kt` (new)

---

### [stable] — v1.5.3 — Wine/Proton, Upload Dates & Sort Control (2026-03-09)
**Commit:** `819ceba`  |  **Tag:** v1.5.3

#### What changed (new since v1.5.0)
- Wine & Proton component categories in Download screen and injection wizard
- Upload dates on file items (from GitHub published_at; WCP JSON sources have none)
- Auto-sort newest first by default; Sort button dropdown (Newest First, Oldest First, Name A→Z, Name Z→A)

#### Files touched
- All files modified across v1.5.1-pre → v1.5.2-pre-2 (see individual pre-release entries below)

---

### [pre-release] — v1.5.2-pre — Auto-sort newest first + user sort control (2026-03-09)
**Commit:** `32d9afb`  |  **Tag:** v1.5.2-pre-2

#### What changed
- Component file lists default to **newest first** (by `publishedAt` descending; items with no date fall to bottom)
- **Sort button** (`Icons.Default.Sort`) appears in the breadcrumb header when at the file list step in both `DownloadScreen` and `RemoteSourceSheet`
- Dropdown offers 4 options with active checkmark: Newest First, Oldest First, Name A→Z, Name Z→A
- `SortOrder` enum declared at package level in `DownloadScreen.kt`, shared with `RemoteSourceSheet`
- Sorting is `remember(currentItems, sortOrder)` — instant, no re-fetch
- Fix: added explicit `Sort` and `Check` icon imports to `RemoteSourceSheet.kt` (it uses per-icon imports, not wildcard)

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`
- `app/src/main/java/com/banner/inject/ui/screens/RemoteSourceSheet.kt`

---

### [pre-release] — v1.5.1-pre — Wine/Proton categories + upload dates (2026-03-09)
**Commit:** `7613e24`  |  **Tag:** v1.5.1-pre

#### What changed
- **Wine & Proton categories**: added "wine" and "proton" to the `componentTypes` list in `DownloadScreen` and `RemoteSourceSheet`. All WCP/GitHub-WCP sources (StevenMXZ, Arihany WCPHub, Xnick417x, MTR) now include these types in `supportedTypes`.
- **Upload dates on file items**: `RemoteItem` gains a nullable `publishedAt: String?` field ("YYYY-MM-DD"). Extracted from `published_at` in GitHub API responses for `fetchTurnipReleases`, `fetchGithubReleasesWcp`, and `fetchGithubReleasesZip`. WCP JSON sources have no date field — stays null. Shown in the subtitle of each file card: "Uploaded YYYY-MM-DD · Tap to download".

#### Files touched
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`
- `app/src/main/java/com/banner/inject/ui/screens/RemoteSourceSheet.kt`

---

### [stable] — v1.5.0 — Custom Storage, Backups in My Downloads, Download Manager & More (2026-03-09)
**Commit:** `31f1603`  |  **Tag:** v1.5.0

#### What changed (new since v1.4.0)
- Native device back button support
- Hamburger menu on repo cards (Open in Browser + Remove Repository)
- Refresh All button with in-memory API caching
- MaxesTechReview (MTR) built-in repo + Drivers component category
- Organized download folder structure: `Downloads/BannersComponentInjector/{Repo}/{Type}/`
- Already-downloaded indicator on file items
- My Downloads tab: browse by repo → type → file; delete records + Clear All
- Backups folder inside My Downloads tab
- Settings > Storage: custom Downloads Location + custom Backups Location (SAF)
- All other pre-release improvements (v1.4.1-pre through v1.4.7-pre)

#### Files touched
- All files modified across v1.4.1-pre → v1.4.7-pre (see individual pre-release entries)

---

### [pre-release] — v1.4.7-pre — Custom Storage Locations for Downloads & Backups (2026-03-09)
**Commit:** `b72f3b6`  |  **Tag:** v1.4.7-pre

#### What changed
- **Settings > Storage** (new section): two configurable save locations.
  - **Downloads Location**: default (MediaStore `Downloads/BannersComponentInjector/…`) or user-selected SAF folder via system file picker.
  - **Backups Location**: same choice for component backups.
- Each row shows the current path, "Select Folder" / "Change" button, and "Use Default" reset button when custom is active.
- SAF folder picker uses `ActivityResultContracts.OpenDocumentTree()`; takes persistable URI permission so access survives reboots.
- `saveToDownloads()` reads `custom_downloads_uri` pref and writes to SAF `DocumentFile` tree when set (repo/type subfolders), falling back to MediaStore default.
- `BackupManager`: all operations (`hasBackup`, `deleteBackup`, `backupFromDocumentFile`, `listAllBackupFiles`, `listAllBackups`) branch on custom vs. default; custom path uses `DocumentFile` tree traversal instead of MediaStore queries.

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/SettingsSheet.kt`
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`
- `app/src/main/java/com/banner/inject/data/BackupManager.kt`

---

### [pre-release] — v1.4.6-pre — Backups Integrated into My Downloads Tab (2026-03-09)
**Commit:** `37dfc74`  |  **Tag:** v1.4.6-pre

#### What changed
- **Backups moved into My Downloads tab**: top-level "Backups" folder card appears at root of My Downloads list; tapping navigates into a full backup browser showing file count + size per backup, with per-backup delete support.
- Removed standalone Backup `IconButton` from `DownloadManagerScreen` top bar.
- `BackHandler` updated to handle `showingBackups` state (Backups → root pop).
- Separate confirmation dialogs for deleting a backup vs a download record.
- `MainTab.MANAGERS` when branch in `MainActivity` now passes `onListBackups`/`onDeleteBackup` directly to `DownloadManagerScreen` — no `BackupManagerSheet` modal on that tab.
- INJECT tab and Download Components tab retain their existing `BackupManagerSheet` modals.

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/DownloadManagerScreen.kt`
- `app/src/main/java/com/banner/inject/MainActivity.kt`

---

### [pre-release] — v1.4.5-pre-2 — Hamburger Menu, Download Indicators, My Downloads Tab (2026-03-09)
**Commit:** `fda7879`  |  **Tag:** v1.4.5-pre-2

#### What changed
- **Hamburger menu** on repo cards: replaced inline browser + delete `IconButton`s with a single `MoreVert` that opens a `DropdownMenu` with "Open in Browser" and "Remove Repository".
- **Downloaded indicator**: file list shows `CheckCircle` icon + "Already downloaded" subtitle for previously saved items. Updates immediately after download; persists via SharedPreferences.
- **My Downloads tab** (`DownloadManagerScreen`): new third tab — repo → type → file drill-down. Each file card shows name, size, date, and a delete button. "Clear All" in top bar. Empty state when no downloads exist.
- **Download record tracking** added to `RemoteSourceRepository`: `DownloadedFile` data class, `recordDownload()`, `removeDownloadRecord()`, `getAllDownloads()`, `isDownloaded()` (O(1) companion `ConcurrentHashSet`). `sanitizeFolderName()` moved to companion. `saveToDownloads()` now returns `Pair<String?, Long>` (URI + file size).

#### Files touched
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`
- `app/src/main/java/com/banner/inject/ui/screens/DownloadManagerScreen.kt` (new)
- `app/src/main/java/com/banner/inject/model/Models.kt`
- `app/src/main/java/com/banner/inject/MainActivity.kt`

---

### [pre-release] — v1.4.4-pre — Organized Download Folder Structure (2026-03-09)
**Commit:** `18a3a46`  |  **Tag:** v1.4.4-pre

#### What changed
- Downloads now land in `Downloads/BannersComponentInjector/{RepoName}/{ComponentType}/{file}` instead of a flat `Downloads/` subfolder.
- `sanitizeFolderName()` helper strips filesystem-unsafe characters (`/ \ : * ? " < > |`) from repo and type names before using them as folder names.
- Both MediaStore path and legacy fallback use the same structure.

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`

---

### [pre-release] — v1.4.3-pre — MTR Repository and Drivers Category (2026-03-09)
**Commit:** `f54e027`  |  **Tag:** v1.4.3-pre

#### What changed
- Added **MaxesTechReview (MTR)** as a built-in default source (`WCP_JSON`) using `https://raw.githubusercontent.com/maxjivi05/Components/main/contents.json`. Supported types: dxvk, vkd3d, box64, fex, fexcore, drivers.
- Added **"drivers"** to the global `componentTypes` list in both `DownloadScreen` and `RemoteSourceSheet`, since MTR labels his GPU/turnip components as "drivers" rather than "turnip"/"adreno".

#### Files touched
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`
- `app/src/main/java/com/banner/inject/ui/screens/RemoteSourceSheet.kt`

---

### [pre-release] — v1.4.2-pre — Browser Button, Refresh, and API Caching (2026-03-09)
**Commit:** `7904d4b`  |  **Tag:** v1.4.2-pre

#### What changed
- **Open in Browser**: Each source card in the Download tab now has a browser icon button. Tapping it opens the repo's GitHub page (API/raw URLs are auto-converted to `github.com` links).
- **Refresh All Button**: A refresh icon in the source list header fetches all sources × all supported types in parallel and populates the cache. Shows a spinner while running; snackbars on completion. TURNIP/ZIP sources are fetched once and stored under all their supported types to avoid redundant API calls.
- **API Result Caching**: Results stored in a `ConcurrentHashMap` (companion object, lives for the app session). After the first browse or a manual refresh, re-entering any source/type returns instantly from cache with no network hit.

#### Files touched
- `app/src/main/java/com/banner/inject/data/RemoteSourceRepository.kt`
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`

---

### [pre-release] — v1.4.1-pre — Native Back Button Support (2026-03-09)
**Commit:** `504b7a2`  |  **Tag:** v1.4.1-pre

#### What changed
- **Device Back Button Interception**: Added Jetpack Compose `BackHandler` to both the full-screen "Download Components" tab (`DownloadScreen`) and the "Select Online Source" injection sheet (`RemoteSourceSheet`).
- **Intuitive Navigation**: Pressing the physical/swipe back button steps back through navigation state (file list → type selection → source selection) rather than immediately closing the app or sheet. Disabled during active downloads.

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/DownloadScreen.kt`
- `app/src/main/java/com/banner/inject/ui/screens/RemoteSourceSheet.kt`

---

### [release] — v1.4.0 — Global Navigation & Custom Repositories (2026-03-08)
**Tag:** v1.4.0

#### What changed
- Official stable release promoting all v1.3.1-pre through v1.3.6-pre features.
- **Global Tab Navigation**: Transformed the primary UX by introducing a `TabRow` at the top of the app toolbar present at all times. Users can easily switch between "Inject Components" (the existing flow) and the new "Download Components" tab.
- **Dedicated Download Screen**: Replicated the remote sources drill-down wizard as a permanent, full-screen UI. Users can browse online repositories, select component types, and directly download `.wcp` or `.zip` files to their device's Downloads folder for later use.
- **Custom Repositories**: Users can now add their own custom online repositories directly within the app.
- **Smart Auto-Detection**: Removed the confusing "Format" dropdown when adding a repo. The app now automatically fetches the URL in the background, inspects its JSON, and intelligently detects if it's a `WCP_JSON`, `GITHUB_RELEASES_WCP`, `GITHUB_RELEASES_TURNIP`, or standard `GITHUB_RELEASES_ZIP`. Automatically converts standard GitHub release links into proper API endpoints.
- **Universal Deletion**: Users can remove *any* repository from their list, including the built-in defaults (e.g., Arihany, StevenMXZ). Includes a safety confirmation dialog to prevent accidental deletions.
- **Restore Defaults**: Added a "Restore Default Repositories" button to easily recover the original built-in sources.
- **Default Start Tab Setting**: Added a "General" section to settings to choose whether the app opens to the "Inject Components" or "Download Components" tab by default upon launch.
- **Settings Reorganization**: Appearance options (Theme & Accent Color) have been moved into a dedicated sub-menu within Settings to reduce clutter.

---

### [release] — v1.3.0 — Full-Screen Settings, In-App Updates, Color Wheel & More (2026-03-08)
**Tag:** v1.3.0

#### What changed
- Official stable release promoting all v1.2.1-pre through v1.2.9-pre features.
- GameHub Lite `emuready.gamehub.lite` package support.
- Fixed two online sources wizard back-navigation crashes (job cancellation + LazyColumn NPE race).
- Added StevenMXZ Adreno-Tools-Drivers, whitebelyash freedreno_turnip-CI sources.
- Replaced broken Arihany JSON with two entries from `Arihany/WinlatorWCPHub` GitHub releases.
- Stable signing keystore — APKs update over previous v1.2.6+ installs.
- In-app update checker with Download & Install (streams APK, progress bar, system installer).
- Settings available on all screens as a full-screen scrollable page.
- Full theme customization: 8 preset swatches + HSV color wheel + hex input.
- "Don't ask again" on backup warning + Settings toggle to re-enable.

---

### [pre-release] — v1.2.9-pre — Full-screen Settings + in-app update downloader (2026-03-08)
**Tag:** v1.2.9-pre

#### What changed
- **Full-screen Settings**: replaced `ModalBottomSheet` with a full-screen `Scaffold` + `LazyColumn`. All sections (About, Appearance, Prompts, Updates, utilities) are scrollable and never cut off. Back arrow + device back button dismiss. `BackHandler` intercepts device back.
- **In-app update download**: when an update is available, user can now tap "Download & Install" to stream the APK directly into the app's cache dir, with a live `LinearProgressIndicator` + percentage. On completion, `FileProvider` + `ACTION_VIEW` hands it to the system installer — no browser needed.
- "View on GitHub" remains as an outlined button alongside Download & Install.
- Download is cancellable mid-stream; pressing Cancel returns to the "Update available" state (restores `UpdateState.Available`).
- `UpdateState.Downloading(progress, release)` added; `UpdateState.Available` now shows inline card with buttons instead of an AlertDialog.
- `UpdateRepository` gains `downloadApk()` (streams with `ensureActive()` for cancellation) and `installApk()` (FileProvider intent).
- `AndroidManifest.xml`: added `REQUEST_INSTALL_PACKAGES` permission + `FileProvider` provider declaration.
- `res/xml/file_paths.xml` (new): exposes `<cache-path>` for FileProvider.

#### Files touched
- `ui/screens/SettingsSheet.kt`
- `data/UpdateRepository.kt`
- `AndroidManifest.xml`
- `res/xml/file_paths.xml` (new)

---

### [pre-release] — v1.2.8-pre-3 — Don't-ask-again backup warning + HSV color wheel (2026-03-08)
**Tag:** v1.2.8-pre-3

#### What changed
- **"Don't ask again"** checkbox added to the No Backup Found dialog in `ComponentDetailSheet`. When checked + Replace Anyway, saves `skip_backup_warning=true` to `bci_settings` SharedPreferences; future replaces bypass the dialog.
- **Settings → Prompts section** (new): toggle *Warn before replacing without a backup* to re-enable or suppress the dialog. Inverts the `skip_backup_warning` pref.
- **HSV Color Wheel** replaces the plain hex input for custom accent color in Settings → Appearance:
  - 240 dp circular disc drawn with `SweepGradient` (hue) + `RadialGradient` (saturation) + black overlay (brightness).
  - Tap or drag the disc to pick hue + saturation; white/black thumb shows selection.
  - Brightness `Slider` below the disc controls the value channel; saves on drag end.
  - Hex field remains as secondary input with Apply button.
  - Live theme preview while dragging; `LaunchedEffect(color)` syncs external preset changes back to wheel state.
- **Fix notes:** Two compile errors caught by CI — `awaitFirstDown`/`awaitPointerEvent` have wrong import paths (solved by switching to `detectDragGestures`/`detectTapGestures`); Kotlin resolved `color = android.graphics.Color.argb(...)` inside `apply {}` as the outer composable `val color: Color` parameter instead of `Paint.color` — fixed with `darkPaint.color = ...` direct assignment.

#### Files touched
- `ui/screens/ComponentDetailSheet.kt`
- `ui/screens/SettingsSheet.kt`

---

### [pre-release] — v1.2.7-pre — Settings on all screens + theme customization (2026-03-08)
**Tag:** v1.2.7-pre

#### What changed
- Settings cog added to ComponentListScreen top bar (was only on AppListScreen before).
- `SettingsSheet` extracted to its own file (`SettingsSheet.kt`) shared by both screens.
- **Appearance section** added to Settings:
  - 8 preset accent color swatches (Orange default, Blue, Purple, Green, Red, Teal, Pink, Amber).
  - Custom swatch opens a hex input field (`#RRGGBB`) with live preview and validation.
  - Color persisted in SharedPreferences, restored on launch.
  - Full theme dynamically derived from accent color (container, secondary, onPrimary all auto-computed).
  - `onPrimary` switches black/white based on luminance for readable text on any accent.
- Fixed `parseHex` to use `Float` components instead of `Int` (compile bug caught pre-push).

#### Files touched
- `ui/theme/ThemePrefs.kt` (new)
- `ui/theme/Theme.kt`
- `ui/screens/SettingsSheet.kt` (new)
- `ui/screens/AppListScreen.kt`
- `ui/screens/HomeScreen.kt`
- `MainActivity.kt`

---

### [pre-release] — v1.2.6-pre — Stable signing key + in-app update checker (2026-03-08)
**Tag:** v1.2.6-pre

#### What changed
- **Stable keystore** (`app/keystore.jks`) committed to repo. All builds now signed with the same key — APKs are installable as updates over previous v1.2.6-pre+ builds without uninstalling first.
- **CI** now passes `-PversionName` from the git tag to Gradle so each APK embeds the correct version string.
- **In-app update checker** added to Settings sheet:
  - "Check for Updates" button — hits GitHub releases API, compares latest vs installed.
  - "Include pre-releases" toggle (off by default, persisted in SharedPreferences).
  - Inline "up to date" message or dialog showing installed vs available version with "Open GitHub Release" button.

#### Files touched
- `app/keystore.jks` (new)
- `app/build.gradle.kts`
- `.github/workflows/release.yml`
- `data/UpdateRepository.kt` (new)
- `ui/screens/AppListScreen.kt`

---

### [pre-release] — v1.2.5-pre — Replace broken Arihany JSON with WinlatorWCPHub releases (2026-03-08)
**Tag:** v1.2.5-pre

#### What changed
- Replaced the failing `arihany/wcp-json` JSON endpoint with two entries from `Arihany/WinlatorWCPHub` GitHub releases.
- **Arihany WCPHub** (`GITHUB_RELEASES_WCP`) → dxvk, vkd3d, box64, fex, fexcore. Covers DXVK (6 variants), VKD3D-Proton, FEXCore + nightly, BOX64-Bionic + nightly, WOWBOX64 + nightly, WINE.
- **Arihany WCPHub (Turnip)** (`GITHUB_RELEASES_TURNIP`) → turnip, adreno.

#### Files touched
- `data/RemoteSourceRepository.kt`

---

### [pre-release] — v1.2.4-pre — Add StevenMXZ Adreno-Tools-Drivers + whitebelyash freedreno_turnip-CI sources (2026-03-08)
**Tag:** v1.2.4-pre

#### What changed
- New `GITHUB_RELEASES_ZIP` source format: fetches all `.zip` assets from GitHub releases (no name filter).
- Added **Adreno Tools Drivers (StevenMXZ)** — `StevenMXZ/Adreno-Tools-Drivers` via `GITHUB_RELEASES_ZIP`.
- Added **freedreno Turnip CI (whitebelyash)** — `whitebelyash/freedreno_turnip-CI` via `GITHUB_RELEASES_TURNIP`.
- Both sources scoped to `turnip`/`adreno` component types.

#### Files touched
- `data/RemoteSourceRepository.kt`

---

### [pre-release] — v1.2.3-pre — Fix NPE crash (LazyColumn snapshot race) in online sources wizard (2026-03-08)
**Tag:** v1.2.3-pre

#### What changed
- Fixed NPE crash at RemoteSourceSheet.kt:219 (`items!!` in LazyColumn) caused by a Compose snapshot race condition.
- The `items != null` when-branch condition evaluated as true, but LazyColumn's lazy content lambda ran after a concurrent state write set `items = null`.
- Fix: replaced `items != null ->` with `else ->`, captured `items` into a local `val currentItems`, and use `return@Column` if it's null to exit cleanly.

#### Files touched
- `ui/screens/RemoteSourceSheet.kt`

---

### [pre-release] — v1.2.2-pre — Fix back-navigation crash in online sources wizard (2026-03-08)
**Tag:** v1.2.2-pre

#### What changed
- Fixed crash/stuck-spinner when pressing Back during or after component-type selection in the online sources wizard.
- Back button now cancels the active fetch job and resets `isLoading = false` immediately.
- `selectedSource` is captured as a local val before launching the coroutine to avoid stale MutableState reads.
- `items` is cleared to null before each new fetch begins.

#### Files touched
- `ui/screens/RemoteSourceSheet.kt`

---

### [pre-release] — v1.2.1-pre — GameHub Lite Package Support (2026-03-08)
**Tag:** v1.2.1-pre

#### What changed
- App Detection: Updated GameHub Lite detection to include the `emuready.gamehub.lite` package name alongside `gamehub.lite`, and updated its display name to "GameHub (Lite)".

---

### [release] — v1.2.0 — Stable Remote Repositories & Fast Scanner (2026-03-07)
**Tag:** v1.2.0

#### What changed
- Official stable release promoting all v1.1.1-pre through v1.1.9-pre features.
- Performance: Massive speedup to component list loading using highly optimized raw `ContentResolver` queries (`SafFastScanner`).
- Remote Sources: Built a complete multi-step drilldown wizard allowing users to fetch components directly from online repositories (StevenMXZ, Arihany, Xnick417x, AdrenoToolsDrivers) and generic GitHub Release APIs.
- UX: Added clear replacement notes in the UI to remember what a component was last replaced with.

---

### [pre-release] — v1.1.9-pre — Remote Source Drilldown Wizard (2026-03-07)
**Tag:** v1.1.9-pre

#### What changed
- UX Overhaul: Replaced the flat remote components list with a multi-step drilldown wizard.
  - Step 1: Users first select which online repository they want to browse (e.g., StevenMXZ, AdrenoToolsDrivers).
  - Step 2: Users select the component type they are looking for (e.g., DXVK, Box64, Turnip). This allows users to browse and install any component type, regardless of which component folder they are currently editing.
  - Step 3: Users are presented with the strictly filtered list of WCPs/ZIPs for that exact type from that specific repository.
- Navigation: Added a dynamic header with a back button and breadcrumbs so users can easily navigate back up the wizard steps.

---

### [pre-release] — v1.1.8-pre — Strict Component Filtering (2026-03-07)
**Tag:** v1.1.8-pre

#### What changed
- Smart Filtering: Remote sources now strictly filter their returned lists based on the active component folder. If you open `dxvk`, you will *only* see `dxvk` packages. If you open `box64`, you will *only* see `box64` packages, removing clutter and preventing accidental incorrect installations.

---

### [pre-release] — v1.1.7-pre — GitHub Releases WCP Support (2026-03-07)
**Tag:** v1.1.7-pre

#### What changed
- Remote Sources: Added support for a new format `GITHUB_RELEASES_WCP`. This allows users or developers to add standard GitHub repository release APIs to the `defaultSources` list. The app will automatically scan the release assets for `.wcp` files and filter them based on the active component type.

---

### [pre-release] — v1.1.6-pre — Additional Online Source (2026-03-07)
**Tag:** v1.1.6-pre

#### What changed
- Remote Sources: Added `Xnick417x` (Winlator-Bionic-Nightly-wcp) to the list of default remote repositories for broader component coverage.

---

### [pre-release] — v1.1.5-pre — Multi-Source Remote Repositories (2026-03-07)
**Tag:** v1.1.5-pre

#### What changed
- Multi-Source Integration: The app now supports fetching components from multiple remote sources concurrently. 
- Component Type Scoping: Sources can be configured to only trigger for specific component folders (e.g., Turnip sources only trigger for `turnip` or `adreno` folders).
- Source Attribution: The remote list UI now clearly indicates which repository a file is originating from (e.g., "From: StevenMXZ" or "From: Arihany").

---

### [pre-release] — v1.1.4-pre — Online Source Update (2026-03-07)
**Tag:** v1.1.4-pre

#### What changed
- Remote Sources: Updated the default WCP JSON repository from `arihany/wcp-json` to `StevenMXZ/Winlator-Contents` to access a different catalog of components.

---

### [pre-release] — v1.1.3-pre — Fast SAF Scanner (2026-03-07)
**Tag:** v1.1.3-pre

#### What changed
- Performance Optimization: Dramatically sped up the initial load time of the component list.
- Custom Scanner: Bypassed the slow Android `DocumentFile` recursive wrappers by introducing `SafFastScanner`, which performs highly optimized, raw `ContentResolver` queries to calculate file counts and sizes natively across directory trees.

---

### [pre-release] — v1.1.2-pre — Remote Online Sources (2026-03-07)
**Tag:** v1.1.2-pre

#### What changed
- Remote Replacement: Users can now replace components by downloading them directly from online sources instead of requiring a local WCP file.
- Remote Sources: Added integration with `arihany/wcp-json` for standard components and GitHub Releases for Turnip GPU drivers.
- UI Update: `ComponentDetailSheet` now features two options for replacement: "Select Local File" and "Select Online Source". 
- Downloader UI: A new bottom sheet (`RemoteSourceSheet`) displays available remote files with their versions and shows real-time progress during downloads.

---

### [pre-release] — v1.1.1-pre — Component Replacement Notes (2026-03-07)
**Tag:** v1.1.1-pre

#### What changed
- Component Info: The component list now displays a note indicating what a component was replaced with after a successful WCP import.
- State Persistence: Replacement notes are saved to `SharedPreferences` so they persist across app restarts and are cleared when a component is restored to its backup.

---

### [release] — v1.1.0 — Stable Backup Manager & External Backups (2026-03-07)
**Tag:** v1.1.0

#### What changed
- Official stable release promoting v1.0.1-pre through v1.0.3-pre features.
- External Backups: Backups now land in `Downloads/BannersComponentInjector/<componentName>/` instead of app-private internal storage, using `MediaStore.Downloads` API.
- Backup Manager: Centralized Backup Manager accessible from the AppList top bar, Settings sheet, and ComponentList top bar.
- Settings Sheet: Added a new Settings cog to the AppListScreen top bar to show the app version and provide an "Open Downloads Folder" shortcut.

---
