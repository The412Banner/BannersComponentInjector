# Banners Component Injector — Progress Log

> **Development Rule:** Always create a *new* pre-release tag (e.g., bump to v1.4.3-pre, v1.4.4-pre) when pushing a new commit and build. Never overwrite or re-tag the same pre-release version.

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
