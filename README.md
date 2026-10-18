# R2 Drive — Google Drive-Style Native Android App

A lightweight, high-performance Native Android application engineered to deliver the full Google Drive mobile experience for your Cloudflare R2 Cloud Vault.

---

## ⚡ Key Highlights

1. **Google Drive Mobile Interface**:
   - **Zero Desktop Zoom-Out**: Renders at true 1:1 mobile scale (`width=device-width, user-scalable=no, viewport-fit=cover`). No pinch-zooming required.
   - **Top Search Pill**: Search Drive with instant filter chips (`All`, `Folders`, `Images`, `Videos`, `Audio`, `Docs`, `Zips`).
   - **Google Drive Floating Action Button (FAB `+`)**: Seamless BottomSheet to create folders, upload files, or upload entire directories.
   - **Google Drive File Actions Sheet**: Tap `⋮` on any file or folder to slide up the native action sheet (Download, Share Link, Rename, Cut, Copy, Delete).
   - **Bottom Navigation Bar**: Clean 3-tab navigation (`Files`, `Updates`, `Vault`).
2. **In-App Live Update System (Check for Updates)**:
   - When you tap **Check for Updates**, the update dialog appears with version notes.
   - Tapping **Download & Install** downloads the APK **directly inside the app** with a real-time progress bar (0% - 100%, MB / MB).
   - Once completed, it automatically launches Android's native package installer to update seamlessly in-place!
3. **Ultra-Fast Instant Launch**:
   - Assets are bundled locally and served via Google's `WebViewAssetLoader` from memory bus speeds.
   - Full hardware acceleration enabled (`hardwareAccelerated="true"`).
4. **Permanent Keystore Signature**:
   - Signed with a persistent 2048-bit RSA key valid until **March 5, 2054**.
   - Supports **V1, V2, V3, and V4** APK signing schemes.
   - All future updates install over previous versions without signature conflicts or uninstalling.
5. **Simplified 2-Key Auth, Dynamic Bucket Discovery & Photo Profiles**:
   - **Only 2 Fields Required**: Just enter your **Access Key ID** and **Secret Access Key** (with Admin Read & Write access).
   - **Automatic Bucket Discovery**: Scans your Cloudflare R2 account via `s3.listBuckets()` and allows choosing any bucket as your Drive.
   - **Personal Photo Upload**: Upload your real profile picture directly from your device, rendered cleanly in the top Google Drive search bar.
   - **Bucket & Profile Management**: Switch buckets or edit your photo/name anytime from the Vault sheet.
   - **Zero Flicker Cold Boot**: Synchronous local session check ensures 0ms delay on startup.
   - **Persistent Session**: Never prompts for credentials again once connected.

---

## 🔐 Keystore Specifications
- **Keystore Path**: `app/keystore/release.jks`
- **Key Alias**: `r2drive`
- **Validity Period**: 2026-10-18 to 2054-03-05
- **Signature Schemes**: V1 + V2 + V3 + V4

---

## 🚀 Automated CI/CD via GitHub Actions
Every commit pushed to the repository automatically triggers the GitHub Actions workflow to:
1. Set up Zulu/Temurin JDK 17 and Android build tools.
2. Compile and sign the release APK using the persistent keystore.
3. Publish a new GitHub Release with direct APK download assets.
