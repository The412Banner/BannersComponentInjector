# Banners Component Injector — Progress Log

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
