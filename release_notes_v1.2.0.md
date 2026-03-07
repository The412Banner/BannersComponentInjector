## Banners Component Injector v1.2.0

### **New Additions Since v1.1.0**
*   **Fast Component Scanner:** Dramatically reduced the time it takes to scan and calculate the sizes of component folders. The app now bypasses the standard slow Android wrappers and uses highly optimized raw database queries to scan files instantly.
*   **Remote Online Sources:** You can now replace components by downloading them directly from the internet inside the app! No need to manually download WCP files first.
    *   **Drilldown Wizard:** A new multi-step wizard allows you to select a repository, pick a component type (DXVK, Box64, Turnip, etc.), and view all available packages.
    *   **Supported Repositories:** Out of the box, it supports StevenMXZ, Arihany, Xnick417x, and the official AdrenoToolsDrivers GitHub.
    *   **Custom Repositories:** Fully supports scraping standard `.wcp` files from any GitHub Releases page.
*   **Replacement Notes:** The component list will now visibly remember and display the name of the last package you successfully injected into a folder (e.g., "Replaced: FEXCore 2603-Nightly").

---

### **Full Feature List**
*   **Remote Component Injection:** Download and inject components directly from curated online repositories without leaving the app.
*   **Local Archive Support:** Auto-detects and extracts local WCP formats (`zstd`, `xz`, `gzip`, `bzip2`, `lz4`, plain `tar`) and Turnip `.zip` drivers.
*   **Component Memory:** Remembers and displays the exact version of the WCP or ZIP you last injected into any given folder.
*   **External Backups:** Safely backs up components to your public `Downloads/BannersComponentInjector/` folder before any modifications, preserving their directory structure.
*   **Centralized Backup Manager:** A dedicated UI to view sizes, file counts, and easily delete specific saved backups.
*   **Wide Compatibility:** Covers 8 GameHub package names across 6 app entries (Lite, PuBG, AnTuTu, Ludashi, Genshin, and Original).
*   **Access Management:** Uses Android's Storage Access Framework (SAF) to manage folder permissions *without root*.
    *   Persistent URI permissions are stored per package group and can be revoked.
    *   Includes a path guide dialog to help users navigate to the exact `Android/data/` path.
*   **Component Operations:** Complete component lifecycle management including Backup, Re-backup, Replace, Restore, and Delete backup, all with confirmation dialogs and no-backup warnings.
*   **Smart Layout Detection:** Automatically detects flat vs. structured archive layouts (e.g., FEXCore WCPs are extracted flat, while others preserve `system32`/`syswow64` subdirectories).
*   **User Interface:** A sleek Dark Material 3 theme with an orange accent, real-time progress overlays during extractions/downloads, and Snackbar feedback.