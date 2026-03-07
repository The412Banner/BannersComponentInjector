## Banners Component Injector v1.1.0

### **New in v1.1.0**
*   **External Backups:** Backups now land in `Downloads/BannersComponentInjector/<componentName>/` instead of app-private internal storage.
    *   Users can browse and manage backups in any file manager or the system Downloads app.
    *   Uses `MediaStore.Downloads` API (Android 10+) — no extra permissions needed!
    *   Full subdirectory structure preserved (e.g., `system32/`, `syswow64/`).
*   **Backup Manager:** A new centralized Backup Manager is now accessible from three locations: the AppList top bar, the Settings sheet, and the ComponentList top bar.
    *   Lists all component backups found in the Downloads folder.
    *   Shows file count and total size for each backup.
    *   Allows per-component deletion with a confirmation dialog.
*   **Settings Sheet:** Added a new Settings cog to the AppListScreen top bar.
    *   Shows the app version.
    *   Includes an "Open Downloads Folder" shortcut.

---

### **Full Feature List (Since v1.0.0 Stable)**
*   **Wide Compatibility:** Covers 8 GameHub package names across 6 app entries (Lite, PuBG, AnTuTu, Ludashi, Genshin, and Original).
*   **Access Management:** Uses Android's Storage Access Framework (SAF) to manage folder permissions *without root*.
    *   Includes a path guide dialog to help users navigate to the exact `Android/data/` path.
    *   Persistent URI permissions are stored per package group and can be revoked.
    *   Android 11+ package visibility fully supported via `<queries>`.
*   **Component List:** Recursive file count and size with loading/refreshing indicators. Smart per-component refresh after operations instead of full list rescans.
*   **Component Operations:** Complete component lifecycle management including Backup, Re-backup, Replace, Restore, and Delete backup, all with confirmation dialogs and no-backup warnings.
*   **WCP Support:** Auto-detects and extracts WCP formats: `zstd`, `xz`, `gzip`, `bzip2`, `lz4`, and plain `tar`.
    *   Automatically detects flat vs. structured layouts. FEXCore WCPs are extracted flat, while others preserve `system32`/`syswow64` structure.
*   **ZIP Support:** Fully supports Turnip/adrenotools GPU driver zips (magic bytes auto-detection, reads `meta.json`, flat `.so` extraction).
*   **User Interface:** A sleek Dark Material 3 theme with an orange accent, progress overlays during operations, and Snackbar feedback.