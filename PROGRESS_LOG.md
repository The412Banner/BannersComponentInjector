# Banners Component Injector — Progress Log

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
