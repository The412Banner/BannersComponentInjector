# BannersComponentInjector

**An external component manager for GameHub (Lite) and its variants — no root required.**

BannersComponentInjector lets you browse, back up, replace, and restore the Windows-emulation components inside GameHub app variants (DXVK, VKD3D-Proton, Box64, FEXCore, Wine, GPU Drivers, and more) directly from your Android device. Components can be installed from local files or fetched and downloaded straight from online repositories — all without needing root access or a PC.

---

## Video Tutorial — How to Set Up & Use

[![BannersComponentInjector — Setup & Usage Tutorial](https://img.youtube.com/vi/vVAkRjtW9Gk/maxresdefault.jpg)](https://youtu.be/vVAkRjtW9Gk?si=KY-0ujAoaq2zhdvK)

> **▶ Watch on YouTube:** https://youtu.be/vVAkRjtW9Gk?si=KY-0ujAoaq2zhdvK

---

## Table of Contents

- [Features](#features)
- [Supported Apps](#supported-apps)
- [Installation](#installation)
- [How to Set Up](#how-to-set-up)
- [How to Use](#how-to-use)
  - [Inject Components Tab](#inject-components-tab)
    - [Backing Up a Component](#backing-up-a-component)
    - [Replacing a Component — Local File](#replacing-a-component--local-file)
    - [Replacing a Component — Online Sources](#replacing-a-component--online-sources)
    - [Restoring a Component](#restoring-a-component)
  - [Download Components Tab](#download-components-tab)
    - [Cross-Repo Search](#cross-repo-search)
    - [Batch Downloads](#batch-downloads)
    - [Managing Repositories](#managing-repositories)
    - [Reordering Repositories](#reordering-repositories)
    - [Adding a Custom Repository](#adding-a-custom-repository)
    - [Editing a Repository](#editing-a-repository)
  - [My Downloads Tab](#my-downloads-tab)
  - [Backup Manager](#backup-manager)
  - [In-App Updates](#in-app-updates)
  - [Theme Customization](#theme-customization)
- [Supported Component Formats](#supported-component-formats)
- [Online Sources](#online-sources)
- [Settings](#settings)
- [Requirements](#requirements)
- [Building from Source](#building-from-source)

---

## Features

**Core**
- **No root required** — uses Android's Storage Access Framework (SAF) for secure folder access.
- **Multi-app support** — automatically detects all installed GameHub variants side by side.
- **Fast streaming scanner** — components appear progressively as folders are found, with a live "Loading X / Y" counter. Parallel scanning with semaphore limiting makes load times fast even on large component trees.
- **Three-tab layout** — Inject Components, Download Components, and My Downloads.

**Inject Components**
- **Backup** — back up any component folder to `Downloads/BannersComponentInjector/<componentName>/` (or a custom location).
- **Replace from local file** — pick a `.wcp` or `.zip` from your device and inject it into the component folder.
- **Replace from online source** — cross-repo search or drill-down browse; file detail sheet with Release Notes; "Download & Replace" injects directly without leaving the app.
- **Restore** — restore any component to its backed-up state with one tap.
- **Replacement notes** — the component list remembers what each folder was last replaced with; cleared automatically on restore.
- **Backup warning** — warns before replacing an unbacked component, with a "Don't ask again" option and a Settings toggle to re-enable.
- **Pull-to-refresh** — swipe down on the component list to re-scan the folder.

**Download Components**
- **8 built-in repositories** — StevenMXZ, Arihany WCPHub, Xnick417x, AdrenoToolsDrivers (K11MCH1), freedreno Turnip CI, MaxesTechReview (MTR), HUB Emulators (T3st31), and Nightlies by The412Banner.
- **Unified GPU Drivers category** — Turnip, Adreno, Qualcomm, and Mesa driver files all appear together under a single "GPU Drivers" category.
- **Release tag browsing** — any individual GitHub release can be enabled as its own browseable category; browse all assets (WCP, ZIP, APK, tar.gz, and more) from a single named release.
- **Always-visible cross-repo search** — search field above the repo list searches all repositories simultaneously; results show file name, source, and type.
- **File detail sheet** — tap any file to see its name, source/type chips, published date, file size, and scrollable Release Notes (from GitHub release body); "Download to Device" button confirms the save.
- **Upload dates and file sizes** — shown on every file card and in the detail sheet.
- **Sort control** — Newest First, Oldest First, Name A→Z, Name Z→A.
- **Already-downloaded indicator** — files you've previously saved are marked with a checkmark.
- **Batch multi-select downloads** — enter multi-select mode; pick any number of files; "Download X files" downloads them all in parallel, skipping any already downloaded.
- **Custom repositories** — add any compatible URL; format is auto-detected. Supports plain GitHub repo links, GitHub Releases URLs, raw JSON feed URLs, and WCP hub JSON feeds.
- **Multi-URL custom repositories** — combine multiple endpoints (e.g. WCP releases + GPU driver releases) into a single repository card.
- **Reorder repositories** — use **Move Up** / **Move Down** in each repo's hamburger menu to arrange the list in any order you prefer.
- **Edit Repository** — rename any repository, change its URL, choose which component types it shows, and enable individual release tags as browseable categories.
- **Hamburger menu per repo** — Open in Browser, Edit Repository, Move Up, Move Down, Remove Repository.
- **Refresh All** — pre-fetches all sources × all types in parallel and caches results in memory.

**My Downloads**
- Browse saved files by Repository → Type → File.
- **Pull-to-refresh** — swipe down to prune stale records (files deleted outside the app); snackbar shows how many were removed.
- **Verify Downloads** — ☁ icon in the top bar runs the same stale-record check on demand.
- **Backups folder** at the root gives quick access to all component backups.
- Delete individual records or clear all at once.

**General**
- **Backup Manager** — centralised view of all saved backups with per-backup deletion.
- **Custom storage locations** — independently set a custom folder for Downloads and for Backups via the SAF folder picker.
- **In-app update checker** — checks GitHub for new releases, downloads the APK with a progress bar, and hands it to the system installer.
- **Full theme customization** — 8 preset accent colours plus a custom HSV colour wheel with brightness slider and hex input.
- **Native back button** — steps back through navigation states throughout the app.
- **Settings on every screen** — accessible via the ⚙ icon from the App List, Component List, and Download screens.

---

## Supported Apps

BannersComponentInjector detects the following GameHub variants automatically:

| App | Package Name(s) |
|-----|----------------|
| GameHub (Lite) | `gamehub.lite`, `emuready.gamehub.lite` |
| GameHub Lite — PuBG Edition | `com.tencent.ig` |
| GameHub Lite — AnTuTu Edition | `com.antutu.ABenchMark`, `com.antutu.benchmark.full` |
| GameHub Lite — Ludashi Edition | `com.ludashi.aibench`, `com.ludashi.benchmark` |
| GameHub Lite — Genshin Edition | `com.mihoyo.genshinimpact` |
| GameHub Lite — Original | `com.xiaoji.egggame` |

> If a variant is installed but not in the list above, open an issue and it can be added.

---

## Installation

1. Go to the [**Releases**](https://github.com/The412Banner/BannersComponentInjector/releases/latest) page.
2. Download the APK for your device architecture:
   - **arm64-v8a** — most modern Android phones (64-bit ARM)
   - **armeabi-v7a** — older 32-bit ARM devices
   - **x86_64** — x86 emulators / tablets
3. On your Android device, enable **Install from unknown sources** for your file manager or browser (Settings → Apps → Special app access → Install unknown apps).
4. Open the downloaded APK and tap **Install**.

> APKs from **v1.2.6 and later** are signed with a stable certificate, so they install as updates over each other without needing to uninstall first.

---

## How to Set Up

### First Launch

1. Open **BannersComponentInjector**.
2. The app shows all detected GameHub variants on your device.
3. Tap the variant you want to manage.
4. A guide dialog appears explaining which folder to select. Tap **Open Folder Picker**.
5. In the Android folder picker:
   - Tap the **≡ hamburger menu** (top-left) and select your **GameHub app** from the sidebar.
   - Navigate to: `data` → `files` → `usr` → `home` → `components`
   - Tap **Use this folder** and then **Allow**.
6. The app now has access. Tap the app card again to open the component list.

> You only need to grant folder access once per app variant. Access is remembered across restarts. To remove access, tap the **🔗 unlink icon** next to the app card.

---

## How to Use

### Inject Components Tab

This tab is the main workspace for managing components already installed inside a GameHub variant.

#### Backing Up a Component

1. Tap any component card to open its detail sheet.
2. Tap **Backup Current Contents**.
3. The component folder is copied to `Downloads/BannersComponentInjector/<componentName>/` (or your custom Backups location).
4. The component card shows a **Backup** badge once a backup exists.

> Always back up before replacing — if something goes wrong you can restore instantly.

---

#### Replacing a Component — Local File

1. Tap the component card you want to replace.
2. Tap **Select Local File**.
3. If no backup exists, a warning appears. Tap **Replace Anyway** to proceed (or check **Don't ask again** to skip future warnings).
4. In the file picker, select your `.wcp` or `.zip` file.
5. The app extracts the file into the component folder. A snackbar confirms success.
6. The component card shows a **Replaced** note with the file name.

---

#### Replacing a Component — Online Sources

1. Tap the component card you want to replace.
2. Tap **Select Online Source**.
3. At the top of the sheet, a **search bar** lets you search across all repositories at once. Type to see matching files from any repo — tap a result to open its detail sheet.
4. Or browse manually:
   - **Step 1 — Choose a repository**: tap any repo card to drill in.
   - **Step 2 — Choose a component type**: select the category (DXVK, VKD3D, Box64, FEXCore, GPU Drivers, Wine, etc.) or any individual release tag you've enabled.
   - **Step 3 — Choose a file**: sorted list with published date. Tap any file to open its detail sheet.
5. The detail sheet shows the file name, source, type, date, size, and Release Notes (if available).
6. Tap **Download & Replace**. A progress indicator shows download status. The component folder is updated on completion.

> Repo cards have a **⋮ menu**: Open in Browser, Edit Repository, Move Up, Move Down, Remove Repository.

---

#### Restoring a Component

1. Tap the component card (must have a **Backup** badge).
2. Tap **Restore Original Backup**.
3. Confirm in the dialog. The component folder is restored from the backup copy.
4. The **Replaced** note is cleared from the component card.

---

### Download Components Tab

This tab lets you browse online repositories and save component files to your device for later use.

#### Cross-Repo Search

The **search bar** at the top of the tab searches all repositories at once as you type. Results show the file name, source, and component type. Tap a result to open its detail sheet.

---

#### Batch Downloads

Tap the **☑ checkbox icon** in the top bar to enter multi-select mode. Tap files to select them (checkboxes appear). Tap **Download X files** to save all selected files in parallel. Already-downloaded files are automatically skipped.

---

#### Browsing a Repository

1. **Select a repository** from the list.
2. **Select a component type** — types shown are those the repository actually provides. GPU driver files (Turnip, Adreno, Qualcomm, Mesa) appear together under **GPU Drivers**. Any individual release tags you've enabled for that repo appear as additional entries in the list.
3. **Browse the file list**:
   - Each item shows its name, upload date (where available), file size, and a checkmark if you've already downloaded it.
   - Files are sorted **newest first** by default.
   - Tap the **Sort** button (top-right) to switch: Newest First, Oldest First, Name A→Z, Name Z→A.
4. **Tap a file** to open its detail sheet. The detail sheet shows the file name, source/type chips, published date, file size, and scrollable Release Notes (if available).
5. Tap **Download to Device** to save it to `Downloads/BannersComponentInjector/<Repo>/<Type>/<filename>` (or your custom Downloads location).

---

#### Managing Repositories

Each repository card has a **⋮ menu** with options:

- **Open in Browser** — opens the repository's GitHub page in your browser.
- **Edit Repository** — opens the edit dialog (see below).
- **Move Up / Move Down** — reorder the repository in the list.
- **Remove Repository** — removes the repository from your list (built-ins can be restored with **Restore Default Repositories** at the bottom of the list).

Tap **⟳ Refresh** (top-right when no repo is selected) to pre-fetch all sources and cache the results in memory for instant browsing and search.

---

#### Reordering Repositories

Tap **⋮ → Move Up** or **⋮ → Move Down** on any repo card to shift it up or down in the list. **Move Up** is disabled at the top of the list; **Move Down** is disabled at the bottom. The order is saved automatically and persists across restarts.

---

#### Adding a Custom Repository

Tap **+** in the header to open the Add Repository dialog.

1. Enter a **Repository Name**.
2. Enter a URL in the **URL** field. Supported URL formats:
   - A plain GitHub repo link: `https://github.com/{owner}/{repo}` — the app reads the folder structure directly from the repo; each folder becomes a component category.
   - A GitHub Releases URL: `https://github.com/{owner}/{repo}/releases` — the app scans release assets for `.wcp` or `.zip` files.
   - A raw JSON feed URL ending in `.json` (WCP JSON format).
3. To combine multiple endpoints into one card (e.g. a WCP releases link plus a GPU driver releases link), tap **+ Add another URL** to add a second URL field. Repeat for more. Tap **−** to remove a field.
4. Tap **Add**. The app auto-detects the format of each URL. For multi-URL repos, it also queries each URL to discover which component types it provides and assigns them accordingly.

---

#### Editing a Repository

Tap **⋮ → Edit Repository** on any card.

- Change the **name** or **URL**.
- **Component Types** — the app detects which categories the repository provides. Existing configured types always appear first, followed by any newly discovered ones (labelled **new**). Check or uncheck categories to control which ones appear when you select this repository. Use **⟳** to re-detect after changing the URL.
- **Tap Select All / Deselect All** for quick selection of component types.
- **Additional Releases** — if the repository has individual GitHub releases beyond the standard component categories (e.g. nightly builds, Steam client builds, emulator releases), they are listed here by release name/tag. Each release can be enabled independently to appear as its own browseable category. Newly discovered releases are labelled **new**.
- Tap **Select All / Deselect All** in the Additional Releases section for quick selection.
- Tap **Save** to apply. Changes to built-in repositories are saved as a custom override.

---

### My Downloads Tab

Browse and manage all files previously downloaded via the Download Components tab.

- The list is grouped by **Repository → Type → File**.
- A **Backups** folder at the root gives quick access to all component backups.
- **Swipe down** (pull-to-refresh) to verify all download records — stale records for files deleted outside the app are pruned; a snackbar shows how many were removed.
- Tap the **☁ icon** in the top bar to run the same stale-record check on demand.
- Tap the **🗑** icon on any file to remove its download record.
- Tap **Clear All** in the top bar to remove all records at once.

> Removing a record only removes the tracking entry — it does not delete the file from your device storage.

---

### Backup Manager

Access the Backup Manager from:
- The **☁ icon** in the Component List top bar.
- The **☁ icon** in the Download Components top bar.
- **Settings → Backup Manager** button.
- The **Backups** folder in the **My Downloads** tab.

The Backup Manager lists every saved backup across all components and apps. Tap the **🗑 delete icon** next to any entry to remove it permanently.

---

### In-App Updates

1. Open **Settings** (⚙ icon on any screen).
2. Scroll to the **Updates** section.
3. Toggle **Include pre-releases** if you want to be notified about pre-release builds.
4. Tap **Check for Updates**.
5. If an update is available, two options appear:
   - **Download & Install** — the APK streams directly inside the app with a live progress bar. The system installer launches automatically on completion.
   - **View on GitHub** — opens the GitHub release page in your browser.
6. Tap **Cancel** at any time during a download to abort.

---

### Theme Customization

1. Open **Settings** → **Appearance**.
2. Choose one of the **8 preset swatches**: Orange (default), Blue, Purple, Green, Red, Teal, Pink, or Amber.
3. For a fully custom colour, tap **Custom**:
   - **Drag** on the colour wheel disc to pick hue and saturation.
   - Use the **Brightness** slider to control lightness.
   - Type a hex value in the **Hex color** field and tap **Apply** to enter a colour manually.
4. The theme updates live. The chosen colour is saved automatically across restarts.

---

## Supported Component Formats

### WCP (Winlator Component Package)

A tar archive compressed with **Zstandard (zstd)** or **XZ**. Contains:
- `profile.json` — metadata: type, version name, description, and file mappings.
- `system32/` and `syswow64/` subdirectories with DLL files.

**Extraction behaviour:**
- `FEXCore` type → files are extracted flat to the component root.
- All other types (DXVK, VKD3D, Box64, Wine, Proton, etc.) → `system32`/`syswow64` directory structure is preserved.

### ZIP (Turnip / Adrenotools)

A plain ZIP archive containing a `meta.json` file and flat `.so` library files. Detected automatically by magic bytes (`PK`). Always extracted flat to the component root.

### Release Tag Downloads

When browsing a repository's individual release tags, all assets from that release are shown regardless of file type — WCP, ZIP, APK, tar.gz, etc. Files are downloaded as-is to your Downloads location.

---

## Online Sources

| Repository | Component Types |
|-----------|----------------|
| StevenMXZ / Winlator-Contents + Adreno-Tools-Drivers | DXVK, VKD3D, Box64, FEXCore, Wine, Proton, GPU Drivers |
| Arihany / WinlatorWCPHub | DXVK, VKD3D, Box64, FEXCore, Wine, Proton, GPU Drivers |
| Xnick417x / Winlator-Bionic-Nightly-wcp | DXVK, VKD3D, Box64, FEXCore, Wine, Proton |
| K11MCH1 / AdrenoToolsDrivers | GPU Drivers |
| whitebelyash / freedreno_turnip-CI | GPU Drivers |
| maxjivi05 / Components (MTR) | Auto-detected from repo folders (DXVK, VKD3D, Box64, FEXCore, GPU Drivers, and more) |
| T3st31 / HUB Emulators | DXVK, VKD3D, Box64, FEXCore, Wine, WowBox64, GPU Drivers |
| The412Banner / Nightlies | Box64, FEXCore, VKD3D, DXVK, and individual nightly/stable release tags |

> Upload dates and Release Notes are shown for GitHub Releases sources. WCP JSON sources and the GitHub Contents format (MTR) do not expose these fields.
>
> StevenMXZ and Arihany each appear as a single card covering both their WCP types and GPU drivers. The app routes each type to the correct upstream endpoint automatically.
>
> GPU Drivers consolidates Turnip, Adreno, Qualcomm, and Mesa driver files from all sources into a single unified category.

---

## Settings

| Section | Setting | Description |
|---------|---------|-------------|
| **General** | Default Start Tab | Choose whether the app opens to Inject Components or Download Components. |
| **Appearance** | Accent Color | 8 preset swatches or a fully custom colour via the HSV colour wheel + hex input. |
| **Prompts** | Backup warning | Toggle the "No Backup Found" warning shown before replacing an unbacked component. |
| **Storage** | Downloads Location | Set a custom folder for downloaded component files (SAF folder picker). Defaults to `Downloads/BannersComponentInjector/`. |
| **Storage** | Backups Location | Set a custom folder for component backups (SAF folder picker). Defaults to `Downloads/BannersComponentInjector/`. |
| **Updates** | Include pre-releases | When enabled, the update checker also considers pre-release builds. |
| **Updates** | Check for Updates | Checks GitHub for a newer version and offers in-app download + install. |
| **Utilities** | Backup Manager | Opens the centralised backup list. |
| **Utilities** | Open Downloads Folder | Opens the system Downloads folder. |

---

## Requirements

- **Android 10 (API 29)** or later.
- One or more GameHub app variants installed on the device.
- No root required.
- Internet access required for online sources and the update checker.

---

## Building from Source

```bash
git clone https://github.com/The412Banner/BannersComponentInjector.git
cd BannersComponentInjector
./gradlew assembleDebug
```

The APK is output to `app/build/outputs/apk/debug/`.

**Dependencies** (all resolved via Gradle):
- Jetpack Compose + Material 3
- AndroidX Activity, Lifecycle ViewModel, DocumentFile (SAF)
- Apache Commons Compress — WCP/tar extraction
- `com.github.luben:zstd-jni` — Zstandard decompression
- `org.tukaani:xz` — XZ decompression
- Material Icons Extended

---

*BannersComponentInjector is an independent third-party tool and is not affiliated with, endorsed by, or connected to the GameHub or Winlator projects.*
