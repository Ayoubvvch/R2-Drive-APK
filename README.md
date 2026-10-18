# R2 Drive — Ultra-Fast Native Android Vault

A high-performance, raw Native Android application engineered to run **R2 Drive - Premium Cloud Vault** with instant startup speeds, hardware-accelerated animations, and native system integration.

---

## ⚡ Key Highlights

1. **Ultra-Fast Cold Start (0ms Latency)**:
   - The web app bundle is pre-packaged locally in internal assets (`assets/index.html`).
   - Served via Google's official `WebViewAssetLoader` over the secure `https://appassets.androidplatform.net/` origin, eliminating network overhead and CORS issues entirely.
2. **Permanent Signing Keystore (Lifetime Upgrades)**:
   - Signed with a dedicated 2048-bit RSA keystore (`app/keystore/release.jks`) valid until **March 5, 2054** (>27 years).
   - Fully enabled with **APK Signature Scheme v1, v2, v3, and v4**.
   - Guarantees seamless in-place updates without ever encountering signature mismatches or needing uninstallation.
3. **Encrypted Zero-Knowledge Vault Access**:
   - Owner R2 credentials are encrypted using **AES-256-GCM** with PBKDF2 (100,000 iterations, SHA-256).
   - No plaintext credentials exist in the source code or binary.
   - On first launch:
     - Prompts: **"Are you the Owner?"**
     - If **Yes**: Decrypts the vault using the master password and securely remembers the unlocked state on the device.
     - If **No**: Allows any public user to configure their own Cloudflare R2 bucket credentials securely.
4. **Built-in Update System (Check for Updates)**:
   - Includes a dedicated "Check for Updates" button integrated directly into the top toolbar and sidebar navigation.
   - Automatically queries the GitHub Releases API to notify users of newer versions with changelogs and one-tap APK installation.
5. **Native Android Features**:
   - Native file chooser (`onShowFileChooser`) supporting single and multi-file uploads directly from device storage.
   - Background downloads via Android `DownloadManager` with notification tray progress and direct saves to the `Downloads/` directory.
   - Fullscreen video playback with Plyr HTML5 video player.
   - Modern dark theme (`#050508`) with edge-to-edge status and navigation bar styling to eliminate cold-launch white flashes.

---

## 🔐 Keystore Specifications
- **Keystore Path**: `app/keystore/release.jks`
- **Key Alias**: `r2drive`
- **Validity Period**: 2026-10-18 to 2054-03-05
- **Signature Schemes**: V1 + V2 + V3 + V4

---

## 🚀 Automated CI/CD via GitHub Actions
Every commit or tag pushed to the repository automatically triggers the GitHub Actions workflow to:
1. Set up Zulu/Temurin JDK 17 and Android build tools.
2. Compile and sign the release APK using the persistent keystore.
3. Publish a new GitHub Release with direct APK download assets.
