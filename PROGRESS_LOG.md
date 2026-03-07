# Banners Component Injector — Progress Log

---

### [release] — v1.0.0 — First stable release (2026-03-07)
**Commit:** `bf2c8cd`  |  **Tag:** v1.0.0

#### Full feature set at stable
- 6 app entries covering 8 GameHub package names (AnTuTu + Ludashi each cover 2 variants)
- Installed/access status per entry; Android 11+ package visibility via `<queries>`
- SAF folder access grant with guide dialog showing exact path
- Persistent URI permissions stored per package group; revoke access
- Component list with recursive file count + size; loading/refreshing indicators
- Per-component refresh after operations (not full list rescan)
- Backup / Re-backup / Replace / Restore / Delete backup with confirmation dialogs
- No-backup warning before replace
- WCP format: zstd, xz, gzip, bzip2, lz4, plain tar — auto-detected
- ZIP format (Turnip/adrenotools): meta.json + flat .so extraction
- WCP FEXCore → flat extraction; all others → preserve system32/syswow64 structure
- Progress overlay during operations; Snackbar feedback
- Dark Material 3 theme, orange accent

---


### [ux] — v1.0.0-pre — Path guide dialog before SAF folder picker (2026-03-07)
**Commit:** `dfde930`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- Android 11+ often ignores `EXTRA_INITIAL_URI` hints for `Android/data` paths, leaving users to navigate from storage root manually
- Added a guide dialog before the picker opens showing:
  - Instruction to navigate and tap "Use this folder"
  - Exact path: `Android/data/<packageName>/files/usr/home/components`
  - "Open Folder Picker" button
- The SAF picker still requires the user to confirm the folder (OS requirement, can't bypass)

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/AppListScreen.kt`

---

### [feat] — v1.0.0-pre — Add 3 new GameHub package variants (2026-03-07)
**Commit:** `e016818`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- Added GameHub (Original) — `com.xiaoji.egggame`
- Added GameHub (Ludashi) — `com.ludashi.benchmark`
- Added GameHub (AnTuTu) — `com.antutu.benchmark.full`
- Added matching `<queries>` entries to AndroidManifest.xml for Android 11+ visibility
- App list now shows 8 variants total

#### Files touched
- `app/src/main/java/com/banner/inject/model/Models.kt`
- `app/src/main/AndroidManifest.xml`

---

### [perf] — v1.0.0-pre — Refresh only affected component after operations (2026-03-07)
**Commit:** `79a6f76`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- Post-operation refresh (backup, replace, restore, delete backup) previously rescanned the entire directory
- Added `scanSingleComponent()` to `ComponentRepository` and `refreshComponent()` to `MainViewModel`
- All four post-op `refresh()` calls replaced with `refreshComponent(component)` — only the modified entry is re-scanned and swapped in-place
- Toolbar refresh button still does a full rescan

#### Files touched
- `app/src/main/java/com/banner/inject/data/ComponentRepository.kt`
- `app/src/main/java/com/banner/inject/viewmodel/MainViewModel.kt`

---

### [feat] — v1.0.0-pre — ZIP format support for Turnip / adrenotools GPU driver zips (2026-03-07)
**Commit:** `5b318af`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- Turnip drivers ship as plain ZIP (PK magic) with `meta.json` + flat `.so` — not a WCP tar archive
- Auto-detect format by magic bytes: `PK` → ZIP path, else → TAR path
- ZIP: reads `meta.json` (vendor→type, name→versionName), extracts all non-meta files flat to component root
- TAR: all existing behaviour unchanged (zstd/xz/gzip/bzip2/lz4, FEXCore flat, ./ prefix strip)
- No new dependencies — `java.util.zip` is built into the JVM
- Refactored WcpExtractor into clearly separated ZIP and TAR private methods

#### Files touched
- `app/src/main/java/com/banner/inject/data/WcpExtractor.kt`

---

### [fix] — v1.0.0-pre — Strip ./ prefix from tar entries so FEXCore extracts flat (2026-03-07)
**Commit:** `806d4b4`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- WCPs packed with `./` prefix on entries (e.g. `./profile.json`, `./system32/foo.dll`) caused `readProfile()` to never match `"profile.json"`, returning type `"Unknown"` instead of `"FEXCore"`
- `isFlat` was therefore always `false`, so FEXCore files landed in `system32/` instead of flat at the component root
- Fixed by calling `.removePrefix("./")` on entry names before matching and before building the destination path

#### Files touched
- `app/src/main/java/com/banner/inject/data/WcpExtractor.kt`

---

### [fix] — v1.0.0-pre — Add XZ decompression support for FEXCore nightlies (2026-03-07)
**Commit:** `01efb76`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- FEXCore nightly WCPs use XZ compression (`FD 37 7A 58 5A 00` magic bytes)
- `commons-compress` detects XZ format but requires `org.tukaani:xz` on the classpath to decompress — without it the catch block silently fell back to treating raw XZ as plain tar, causing a parse error
- Added `org.tukaani:xz:1.9` dependency — no logic changes needed
- Supported formats now: zstd, xz, gzip, bzip2, lz4, plain tar

#### Files touched
- `app/build.gradle.kts`

---

### [fix] — v1.0.0-pre — Black screen / ANR on load and refresh (2026-03-07)
**Commit:** `32d6c91`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- `scanComponents` and `getRootDocument` do blocking SAF disk IO but were called on `Dispatchers.Main` (default for `viewModelScope.launch`) — main thread hung on load (black screen) and triggered ANR dialog on refresh
- Wrapped both calls in `withContext(Dispatchers.IO)` so main thread stays free
- Replaced bare `CircularProgressIndicator` with a card showing "Loading components..." (first load) or "Refreshing components..." (subsequent refreshes)

#### Files touched
- `app/src/main/java/com/banner/inject/viewmodel/MainViewModel.kt`
- `app/src/main/java/com/banner/inject/ui/screens/HomeScreen.kt`

---

### [fix] — v1.0.0-pre — Use profile.json type for WCP extraction mode (2026-03-07)
**Commit:** `825f229`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- VKD3D components were being extracted incorrectly — folder-subdir detection was unreliable (VKD3D folder may be flat even though its WCP needs system32/syswow64 structure preserved)
- Now reads WCP profile.json type field before extraction to determine mode:
  - `FEXCore` → `flattenToRoot=true` (files land directly at component root, strip WCP subdirs)
  - All other types (DXVK, VKD3D, Box64, Turnip, etc.) → preserve system32/syswow64 structure
- Added `readProfile()` method to `WcpExtractor` — opens only profile.json, no full extraction

#### Files touched
- `app/src/main/java/com/banner/inject/data/WcpExtractor.kt`
- `app/src/main/java/com/banner/inject/data/ComponentRepository.kt`

---

### [fix] — v1.0.0-pre — Fix package visibility on Android 11+ (2026-03-07)
**Commit:** `8e875f7`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- App was showing all GameHub variants as "not installed" even when installed
- Root cause: Android 11+ package visibility — `getPackageInfo()` silently returns NameNotFoundException for undeclared packages
- Fixed by adding `<queries>` block to AndroidManifest.xml listing all 5 package names

#### Files touched
- `app/src/main/AndroidManifest.xml`

---

### [fix] — v1.0.0-pre — Auto-detect WCP compression format (2026-03-07)
**Commit:** `c95a3b5`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- FEXCore WCPs caused "unknown frame descriptor" error — they use a different compression format than DXVK (which is zstd)
- Replaced hardcoded ZstdInputStream with CompressorStreamFactory auto-detection
- Now handles zstd, gzip, bzip2, xz, lz4, and more automatically
- Falls back to plain uncompressed tar if no known compression detected

#### Files touched
- `app/src/main/java/com/banner/inject/data/WcpExtractor.kt`

---

### [fix] — v1.0.0-pre — Auto-detect flat vs structured layout for WCP extraction (2026-03-07)
**Commit:** `9f4a51a`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- FEXCore (and similar) components are flat — files sit directly at the component root, no subdirs
- WCP files pack those files inside system32/ — without this fix they'd land in componentFolder/system32/ instead of componentFolder/
- Before wiping, check if the existing component folder has any subdirectories
  - Flat (no subdirs): flattenToRoot=true — strip WCP directory prefixes, dump all files at component root
  - Structured (has subdirs, e.g. DXVK): flattenToRoot=false — preserve system32/, syswow64/ structure
- Detection is automatic, no user configuration needed

#### Files touched
- `app/src/main/java/com/banner/inject/data/WcpExtractor.kt`
- `app/src/main/java/com/banner/inject/data/ComponentRepository.kt`

---

### [fix] — v1.0.0-pre — Recursive file scan for components with subdirectories (2026-03-07)
**Commit:** `69d4e0c`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- Components with subdirs (system32/, syswow64/ etc.) were showing 0 files / 0 bytes
- Added `collectFilesRecursively()` to walk the full directory tree
- `FileInfo` gains `relativePath` field (e.g. "system32/d3d11.dll")
- Detail sheet now shows relative paths so users can see subdirectory structure

#### Files touched
- `app/src/main/java/com/banner/inject/model/Models.kt`
- `app/src/main/java/com/banner/inject/data/ComponentRepository.kt`
- `app/src/main/java/com/banner/inject/ui/screens/ComponentDetailSheet.kt`

---

### [refactor] — v1.0.0-pre — WCP-only replace, removed files/folder options (2026-03-07)
**Commit:** `a0eab19`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- Removed Import Files and Import Folder replace options
- ComponentDetailSheet now has a single "Replace with WCP File" button — no dialog needed
- Removed replaceWithFiles, replaceWithFolder from ViewModel and Repository
- Removed copyDocumentFolderContents, getFileName helpers from Repository
- Net: -256 lines

#### Files touched
- `app/src/main/java/com/banner/inject/ui/screens/ComponentDetailSheet.kt`
- `app/src/main/java/com/banner/inject/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/banner/inject/MainActivity.kt`
- `app/src/main/java/com/banner/inject/viewmodel/MainViewModel.kt`
- `app/src/main/java/com/banner/inject/data/ComponentRepository.kt`

---

### [feat] — v1.0.0-pre — WCP file import support (2026-03-07)
**Commit:** `8420503`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- WCP format confirmed: Zstandard-compressed tar archive with `profile.json` + `system32/`/`syswow64/` DLL subfolders
- `WcpExtractor.kt`: decompresses zstd stream → tar stream → extracts files to DocumentFile preserving directory structure; parses profile.json for type/versionName/description
- `ComponentRepository.replaceWithWcp`: backup → wipe → WCP extract
- `MainViewModel.replaceWithWcp`: success message includes component type + version name from profile.json
- `ComponentDetailSheet`: import dialog redesigned as 3 styled option buttons — WCP File, Folder, Files
- Dependencies added: `commons-compress:1.26.2`, `zstd-jni:1.5.6-3@aar`

#### Files touched
- `app/build.gradle.kts`
- `app/src/main/java/com/banner/inject/data/WcpExtractor.kt` (new)
- `app/src/main/java/com/banner/inject/data/ComponentRepository.kt`
- `app/src/main/java/com/banner/inject/viewmodel/MainViewModel.kt`
- `app/src/main/java/com/banner/inject/ui/screens/ComponentDetailSheet.kt`
- `app/src/main/java/com/banner/inject/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/banner/inject/MainActivity.kt`

---

### [feat] — v1.0.0-pre — Add storage permissions (2026-03-07)
**Commit:** `824ddc8`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- Added `READ_EXTERNAL_STORAGE` (maxSdkVersion 32) to manifest
- Added `WRITE_EXTERNAL_STORAGE` (maxSdkVersion 29) to manifest
- Added `MANAGE_DOCUMENTS` for SAF URI persistence
- Runtime permission request on launch for Android 12 and below

#### Files touched
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/banner/inject/MainActivity.kt`

---

### [ci] — v1.0.0-pre — Release notes + raw APK attachment (2026-03-07)
**Commit:** `5e9d9cc`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- Removed upload-artifact step (was creating zip downloads in Actions tab)
- Release now attaches the APK file directly — no zip wrapper
- Release notes auto-generated from git log since previous tag (doc commits filtered out)
- fetch-depth: 0 added so full tag history is available

#### Files touched
- `.github/workflows/release.yml`

---

### [fix] — v1.0.0-pre — Add contents:write permission to workflow (2026-03-07)
**Commit:** `d13dd63`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- APK build was succeeding but release creation was failing with 403
- Added `permissions: contents: write` to the build job in release.yml

#### Files touched
- `.github/workflows/release.yml`

---

### [fix] — v1.0.0-pre — Add missing launcher icons (2026-03-07)
**Commit:** `1cc5c91`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- Added adaptive launcher icons (mipmap-anydpi-v26) — were missing, causing AAPT failure
- Dark (#1A1A1A) background + orange (#FF6D00) foreground vector
- ic_launcher.xml + ic_launcher_round.xml both use adaptive-icon format

#### Files touched
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `app/src/main/res/drawable/ic_launcher_background.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`

---

### [fix] — v1.0.0-pre — Fix invalid theme resource (2026-03-07)
**Commit:** `17d9468`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- Replaced `android:Theme.Material.NoTitleBar` (doesn't exist in AAPT) with `Theme.AppCompat.DayNight.NoActionBar`
- Added `androidx.appcompat:appcompat:1.6.1` dependency

#### Files touched
- `app/src/main/res/values/themes.xml`
- `app/build.gradle.kts`

---

### [fix] — v1.0.0-pre — Add gradle.properties + bump minSdk to Android 10 (2026-03-07)
**Commit:** `41f9742`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- Added missing `gradle.properties` with `android.useAndroidX=true` (CI was failing: checkDebugAarMetadata task)
- Bumped `minSdk` from 28 (Android 9) to 29 (Android 10)

#### Files touched
- `gradle.properties`
- `app/build.gradle.kts`

---

### [fix] — v1.0.0-pre — Add gradlew + debug APK build (2026-03-07)
**Commit:** `c078e62`  |  **Tag:** v1.0.0-pre

#### What changed
- Added missing `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` (CI was failing without them)
- Switched CI build from `assembleRelease` to `assembleDebug` — produces installable signed APK without manual signing step
- Pre-release logic updated: any tag containing `-` is pre-release; clean `vX.Y.Z` tag = stable
- Deleted broken `v1.0.0` tag/release; retagged as `v1.0.0-pre`

#### Files touched
- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `.github/workflows/release.yml`

---

### [release] — v1.0.0 — Initial release (2026-03-07)
**Commit:** `febaa1f`  |  **Tag:** v1.0.0

#### What changed
- Full Android app built from scratch for GameHub emulation component management
- SAF-based file access — no root required
- App variant list screen: shows all known GameHub package names with installed/access status
- Per-package SAF URI grant stored in SharedPreferences; pre-hinted to each app's components path
- Component folder list screen: scans subfolders in the granted components directory
- Component detail bottom sheet:
  - Current file list with sizes
  - Explicit "Backup Current Contents" button (saves to app internal storage)
  - "Replace Component" — Import Files (multi-select) or Import Folder
  - No-backup warning dialog if user tries to replace without a prior backup
  - "Restore Original Backup" button (only shown when backup exists)
  - "Delete Backup" button with confirmation
- Progress overlay with live per-file status during copy operations
- Snackbar feedback on success/error
- Dark Material 3 theme with orange accent
- CI workflow: builds unsigned release APK on every v* tag push; pre-release if tag contains -pre/-alpha/-beta

#### GameHub package names (v1.0.0)
- `gamehub.lite` — GameHub Lite
- `com.tencent.ig` — GameHub (Tencent)
- `com.ludashi.aibench` — GameHub (Ludashi)
- `com.antutu.ABenchMark` — GameHub (AnTuTu)
- `com.mihoyo.genshinimpact` — GameHub (Genshin Impact)

#### Files touched
- `app/src/main/java/com/banner/inject/MainActivity.kt`
- `app/src/main/java/com/banner/inject/model/Models.kt`
- `app/src/main/java/com/banner/inject/data/BackupManager.kt`
- `app/src/main/java/com/banner/inject/data/ComponentRepository.kt`
- `app/src/main/java/com/banner/inject/viewmodel/MainViewModel.kt`
- `app/src/main/java/com/banner/inject/ui/theme/Theme.kt`
- `app/src/main/java/com/banner/inject/ui/screens/AppListScreen.kt`
- `app/src/main/java/com/banner/inject/ui/screens/HomeScreen.kt` (ComponentListScreen)
- `app/src/main/java/com/banner/inject/ui/screens/ComponentDetailSheet.kt`
- `app/src/main/java/com/banner/inject/ui/screens/SetupScreen.kt` (stub only)
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle/wrapper/gradle-wrapper.properties`
- `.github/workflows/release.yml`

### [feat] — v1.0.0-pre — Condense app list to 6 entries with multi-package support (2026-03-07)
**Commit:** `bbe1f0e`  |  **Tag:** v1.0.0-pre (retagged)

#### What changed
- `KnownApp` now holds `packageNames: List<String>` instead of single `packageName`
- `GameHubApp` gains `activePackage` (installed pkg → first with access → first in list)
- AnTuTu and Ludashi merged into single entries covering 2 packages each
- On grant: URI stored under ALL package names in the group
- On revoke: ALL package names removed; select/refresh search across all names

#### Files touched
- `app/src/main/java/com/banner/inject/model/Models.kt`
- `app/src/main/java/com/banner/inject/viewmodel/MainViewModel.kt`
- `app/src/main/java/com/banner/inject/ui/screens/AppListScreen.kt`

---
