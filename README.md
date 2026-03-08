# BannersComponentInjector

**An external component manager for GameHub (Lite) and its variants — no root required.**

BannersComponentInjector lets you browse, back up, replace, and restore the Windows-emulation components inside GameHub app variants (DXVK, VKD3D-Proton, Box64, FEXCore, Turnip GPU drivers, and more) directly from your Android device. Components can be installed from local files or fetched straight from online repositories — all without needing root access or a PC.

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
  - [Backing Up a Component](#backing-up-a-component)
  - [Replacing a Component — Local File](#replacing-a-component--local-file)
  - [Replacing a Component — Online Sources](#replacing-a-component--online-sources)
  - [Restoring a Component](#restoring-a-component)
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

- **No root required** — uses Android's Storage Access Framework (SAF) for secure folder access.
- **Multi-app support** — automatically detects all installed GameHub variants side by side.
- **Component list** — fast, native `ContentResolver`-based scanner shows every component folder with file count and size.
- **Backup** — backs up any component folder to `Downloads/BannersComponentInjector/<componentName>/` using the MediaStore API.
- **Replace from local file** — pick a `.wcp` or `.zip` file from your device and inject it directly into the selected component folder.
- **Replace from online sources** — a multi-step drilldown wizard lets you browse, select, and download components from multiple online repositories without leaving the app.
- **Restore** — restore any component to its original backed-up state with one tap.
- **Replacement notes** — the component list remembers what each folder was last replaced with; the note is cleared automatically when you restore.
- **Backup Manager** — centralised view of all saved backups with one-tap deletion.
- **In-app update checker** — checks GitHub for new releases, downloads the APK directly inside the app (progress bar + percentage), and hands it to the system installer — no browser needed.
- **Full theme customization** — 8 preset accent colours plus a full HSV colour wheel with brightness slider and hex input.
- **Backup warning prompt** — warns before replacing a component that hasn't been backed up yet. Includes a "Don't ask again" option and a toggle in Settings to re-enable.
- **Settings on every screen** — accessible from the App List and the Component List via the ⚙ icon.

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

1. Go to the [**Releases**](https://github.com/The412Banner/BannersComponentInjector/releases/latest) page and download the latest APK.
2. On your Android device, enable **Install from unknown sources** for your file manager or browser (Settings → Apps → Special app access → Install unknown apps).
3. Open the downloaded APK and tap **Install**.

> APKs from **v1.2.6 and later** are signed with a stable certificate, so they install as updates over each other without needing to uninstall first.

---

## How to Set Up

### First Launch

1. Open **BannersComponentInjector**.
2. The app shows a list of all detected GameHub variants on your device.
3. Tap the variant you want to manage.
4. A guide dialog appears explaining which folder to select. Read it, then tap **Open Folder Picker**.
5. In the Android folder picker:
   - Tap the **≡ hamburger menu** (top-left) and select your **GameHub app** from the sidebar.
   - Navigate to: `data` → `files` → `usr` → `home` → `components`
   - Tap **Use this folder** and then **Allow**.
6. The app now has access. Tap the app card again to see all component folders.

> You only need to grant folder access once per app variant. Access is remembered across restarts. To remove access, tap the **🔗 unlink icon** next to the app card.

---

## How to Use

### Backing Up a Component

1. Tap any component card in the list to open its detail sheet.
2. Tap **Backup Current Contents**.
3. The component folder is copied to `Downloads/BannersComponentInjector/<componentName>/`.
4. The component card shows a **Backup** badge once a backup exists.

> Always back up before replacing — if something goes wrong you can restore instantly.

---

### Replacing a Component — Local File

1. Tap the component card you want to replace.
2. Tap **Select Local File**.
3. If no backup exists, a warning appears asking you to confirm. Tap **Replace Anyway** to proceed (or check **Don't ask again** to skip future warnings for this session).
4. In the file picker, select your `.wcp` or `.zip` file.
5. The app extracts the file into the component folder. A snackbar confirms success.
6. The component card updates its **Replaced** note with the file name.

---

### Replacing a Component — Online Sources

1. Tap the component card you want to replace.
2. Tap **Select Online Source**.
3. **Step 1 — Choose a repository**: pick the online source to browse (e.g., StevenMXZ, Arihany WCPHub, K11MCH1 AdrenoToolsDrivers, etc.).
4. **Step 2 — Choose a component type**: select the type you're looking for (e.g., DXVK, VKD3D, Box64, Turnip). Only types available from the selected source are shown.
5. **Step 3 — Choose a file**: a filtered list of matching packages appears with version info and the source name. Tap any entry to download and install it automatically.
6. A progress indicator shows download status. The component folder is updated on completion.

> The online wizard strictly filters results — if you open a `turnip` folder, you only see Turnip/Adreno packages regardless of the repository.

---

### Restoring a Component

1. Tap the component card (must have a **Backup** badge).
2. Tap **Restore Original Backup**.
3. Confirm in the dialog. The component folder is restored from the backup copy in Downloads.
4. The **Replaced** note is cleared from the component card.

---

### Backup Manager

Access the Backup Manager from:
- The **☁ icon** in the App List top bar.
- The **☁ icon** in the Component List top bar.
- **Settings → Backup Manager** button.

The Backup Manager lists every saved backup across all components and apps. Tap the **🗑 delete icon** next to any entry to remove it permanently.

---

### In-App Updates

1. Open **Settings** (⚙ icon on any screen).
2. Scroll to the **Updates** section.
3. Toggle **Include pre-releases** if you want to be notified about pre-release builds.
4. Tap **Check for Updates**.
5. If an update is available, two options appear:
   - **Download & Install** — the APK is downloaded directly inside the app. A progress bar shows the download percentage. When the download completes, the system installer launches automatically.
   - **View on GitHub** — opens the GitHub release page in your browser.
6. You can tap **Cancel** at any time during the download to abort.

---

### Theme Customization

1. Open **Settings** → **Appearance**.
2. Choose one of the **8 preset swatches**: Orange (default), Blue, Purple, Green, Red, Teal, Pink, or Amber.
3. For a fully custom colour, tap **Custom**:
   - **Drag** anywhere on the colour wheel disc to pick hue and saturation.
   - Use the **Brightness** slider to control lightness.
   - Type a hex value in the **Hex color** field and tap **Apply Custom Color** if you prefer to enter a colour manually.
4. The theme updates live as you interact with the wheel. The chosen colour is saved automatically.

---

## Supported Component Formats

### WCP (Winlator Component Package)
A tar archive compressed with **Zstandard (zstd)** or **XZ**. Contains:
- `profile.json` — metadata (type, version, description, file mappings).
- `system32/` and `syswow64/` subdirectories with DLL files.

**Extraction behaviour:**
- `FEXCore` type → files extracted flat to the component root.
- All other types (DXVK, VKD3D, Box64, Wine, etc.) → `system32`/`syswow64` directory structure preserved.

### ZIP (Turnip / Adrenotools)
A plain ZIP archive containing a `meta.json` file and flat `.so` library files. Detected automatically by magic bytes (`PK`). Always extracted flat to the component root.

---

## Online Sources

| Repository | Format | Component Types |
|-----------|--------|----------------|
| StevenMXZ / Winlator-Contents | WCP JSON | DXVK, VKD3D, Box64, FEX, FEXCore |
| Arihany / WinlatorWCPHub | GitHub Releases (WCP) | DXVK, VKD3D, Box64, FEX, FEXCore, Wine |
| Arihany / WinlatorWCPHub | GitHub Releases (Turnip) | Turnip, Adreno |
| Xnick417x / Winlator-Bionic-Nightly-wcp | WCP JSON | DXVK, VKD3D, Box64, FEX, FEXCore |
| K11MCH1 / AdrenoToolsDrivers | GitHub Releases | Turnip, Adreno |
| StevenMXZ / Adreno-Tools-Drivers | GitHub Releases (ZIP) | Turnip, Adreno |
| whitebelyash / freedreno_turnip-CI | GitHub Releases | Turnip, Adreno |

---

## Settings

| Setting | Description |
|---------|-------------|
| **Appearance → Accent Color** | Choose from 8 presets or use the HSV colour wheel for a fully custom accent. |
| **Prompts → Backup warning** | Toggle the "No Backup Found" warning shown before replacing an unbacked component. |
| **Updates → Include pre-releases** | When enabled, the update checker also considers pre-release builds. |
| **Updates → Check for Updates** | Checks GitHub for a newer version and offers in-app download + install. |
| **Backup Manager** | Opens the centralised backup list. |
| **Open Downloads Folder** | Opens the system Downloads folder where backups are saved. |

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
- Jetpack Compose + Material 3 (BOM 2024.02.00)
- AndroidX Activity, Lifecycle ViewModel
- Apache Commons Compress — WCP extraction
- `com.github.luben:zstd-jni` — Zstandard decompression
- `org.tukaani:xz` — XZ decompression
- Material Icons Extended

---

*BannersComponentInjector is an independent third-party tool and is not affiliated with, endorsed by, or connected to the GameHub or Winlator projects.*
