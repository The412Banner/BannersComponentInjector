# Banners Component Injector — Progress Log

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
