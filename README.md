<p align="center">
  <img src="app/src/main/res/drawable/fast_recorder_logo_1788107942806.jpg" width="120" alt="FastRecorder Logo"/>
</p>

<h1 align="center">FAST RECORDER</h1>

<p align="center">
  <strong>Ultra-lightweight Android screen recorder — MediaProjection + internal audio, zero bloat</strong><br>
  <em>by <a href="https://t.me/dikaacode">@dikaacode</a> — FAST RECORDER</em>
</p>

<p align="center">
  <a href="https://github.com/dikaofc/FastRecorderAndroid/releases"><img src="https://img.shields.io/badge/Download-Latest_Release-2E7D32?style=for-the-badge" alt="Download"/></a><br>
  <img src="https://img.shields.io/badge/Android-5.0%2B-3DDC84?logo=android" alt="Min SDK"/>
  <img src="https://img.shields.io/badge/Target-SDK%2034-0066CC" alt="Target SDK"/>
  <img src="https://img.shields.io/badge/Compile-SDK%2036.1-673AB7" alt="Compile SDK"/>
  <img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/CI-Android_CI_%2B_Release-00C853" alt="CI"/>
</p>

---

## ⬇️ Download

**Latest stable:** https://github.com/dikaofc/FastRecorderAndroid/releases — 2 APK di root release: `app-release.apk` (install) + `app-debug.apk`

- **Branch `release`:** https://github.com/dikaofc/FastRecorderAndroid/tree/release — `app-release.apk` & `app-debug.apk` selalu di root (auto-deploy tiap push `main`)
- **In-app update:** `Settings → App Update → CHECK FOR UPDATE` — auto-check tiap 6 jam, notifikasi kalau ada versi baru, progress 0–100%, auto-install, checkbox `Delete APK after update`

> Tiap push ke `main` auto-build & auto-publish Release `v1.0.<run_number>` (stable) — no tag manual needed, tapi `git tag v*` juga tetap trigger.

---

## ✨ Features

### 🎬 Recording (PTS Fixed)
- **MediaProjection** capture (same API as WhatsApp/Discord screen share)
- **Internal audio** via `AudioPlaybackCapture` (Android 10+) — `NONE / MIC / INTERNAL / INTERNAL+MIC`
- **Resolutions:** 1080p / 720p / 480p / 360p • **FPS:** 60 / 30 / 24 / 15
- **Battery saver** & **High performance** toggle
- **Notification controls:** `Pause` ↔ `Resume` & `Stop` langsung dari shade tanpa buka app
- **Auto-install after download:** selesai download langsung launch installer (600ms), anti stuck `Downloaded` doang
- **PTS normalization** — fix bug durasi `25:43:17` (video PTS ~ uptime vs audio 0 → now `pts - firstVideoPts - pauseOffset`)

### 🎨 Neobrutalism UI
- Bold borders, high contrast, `bg_neo_*` drawables — no hardcode emoji, semua icon `VectorDrawable` (`ic_close`, `ic_check_neo`, `ic_warning`, `ic_folder`)
- **Dark mode** full (card/spinner/button)
- Responsive di semua ukuran layar — semua dialog `ScrollView` + `weight=1` + `clipChildren=false` (no button kepotong bawah, no animasi pulse kepotong kotak)
- Home `FAST RECORDER` header (ex `ZETA RECORDER` removed) + pulsing `REC` 120dp di container 180dp `clipChildren=false` → scale 1.5x = 180dp pas

### 📁 Gallery
- Play / rename / delete / share / Catbox upload (batch)
- Temp cleanup `updates/` >24h auto purge + manual `delete apk after update`

### ⚙️ Settings
- Resolution / FPS / Audio mode spinners, toggles (overlay / high perf / battery saver / autoUpload / darkMode)
- **Storage** picker `ACTION_OPEN_DOCUMENT_TREE` (internal / SD card) + progress bar `free/total` + threshold
- **Security diagnostics** entry + **App Update** section (`Current: v1.0.x (code)`, `CHECK FOR UPDATE`, last download path tap-to-copy)

### 🔒 Security (Defense-in-Depth)
- **11 modules, 9 domains, 5 states:** `TRUSTED → LOW_RISK → SUSPICIOUS → HIGH_RISK → UNTRUSTED`
- **Trust banner responsive:** `TRUSTED` hijau 18sp `singleLine ellipsize` pas di dalam `bg_neo_spinner` (fix hijau keluar box) — attribution `VERIFIED @dikaacode` row weight-1 singleLine `attributionRow` background fix
- **Diagnostics tab:** `ScrollView 0dp weight1 fillViewport`, `domain/signal/cluster` rows `11-12sp maxLines 3 breakStrategy simple` — no text kepotong atas/bawah/samping
- Semua warna / font scale aman, dark mode full

### 🔄 Updater (Live from GitHub, zero hardcode)
- `REPO = dikaofc/FastRecorderAndroid` + `API https://api.github.com/repos/.../releases?per_page=10` + `User-Agent FastRecorder-Updater/<ver>` + `X-GitHub-Api-Version`
- **Live check:** `PackageManager.versionName` vs `JSONArray tag_name` filter `!draft && !prerelease` + `isNewerVersion()` semantic
- **403 fallback:** kalau API `403 rate limit (60/jam)`, auto hit `https://github.com/.../releases/latest` (302 `Location` → `v1.0.x`) → synthetic `app-release.apk` direct URL, no API limit
- Error toast full 120 chars + msg Indo jelas, no truncated `40`

---

## 📁 Project Structure

```
FastRecorderAndroid/
├── app/src/main/java/com/dikacode/
│   ├── MainActivity.kt                 # Home FAST RECORDER + REC pulse 180dp + auto-check notif
│   ├── SettingsActivity.kt             # Settings + App Update (checkForUpdate, delete toggle)
│   ├── GalleryActivity.kt              # Gallery + Catbox
│   ├── SecurityDiagnosticsActivity.kt  # Diagnostics responsive (trust banner 18sp, attributionRow)
│   ├── recorder/ScreenRecorder.kt      # PTS offset normalization, HEVC/AVC, pause offset
│   ├── update/GitHubUpdater.kt         # Live API + 403 web fallback + FileProvider install
│   ├── update/UpdateDialog.kt          # Scroll dialog, auto-install, install via provider
│   ├── update/UpdateNotifier.kt        # HIGH channel PendingIntent to MainActivity
│   ├── security/*                      # SecurityEngine, RiskAssessment, IdentityVerifier, etc.
│   └── service/RecordingForegroundService.kt # Foreground + notification Pause/Resume/Stop
├── app/src/main/res/
│   ├── layout/activity_main.xml              # FAST RECORDER header, clipChildren false
│   ├── layout/dialog_update.xml              # ScrollView + ic_close + ic_folder
│   ├── layout/dialog_developer_info.xml      # ScrollView + ic_close
│   ├── layout/activity_security_diagnostics.xml # ScrollView weight1, attributionRow
│   ├── drawable/ic_close.xml / ic_folder.xml / ic_check_neo.xml / ic_warning.xml
│   └── xml/file_paths.xml                    # FileProvider external Download + cache
└── .github/workflows/android-ci.yml    # Build debug/release + Deploy root + Release v1.0.<run>
```

---

## 🚀 Build & Install

### Prerequisites
- Android Studio Hedgehog+ • JDK 17 • Android SDK 36.1

```bash
# Debug
./gradlew assembleDebug
# Release (optional keystore env)
./gradlew assembleRelease

# Tests (avoid AGP `test --tests` unknown option)
./gradlew testDebugUnitTest --tests "com.dikacode.security.*"

# Install
adb install app/build/outputs/apk/debug/app-debug.apk
# atau download app-release.apk dari Releases lalu Allow unknown sources
```

### Versioning & Signing (no signature conflict)
- `versionCode = GITHUB_RUN_NUMBER ?: 1` → `versionName = 1.0.<run_number>` — selalu naik, gak bentrok `PackageManager`
- Debug keystore stable: workflow `if [ -f debug.keystore ] reuse else keytool -genkeypair RSA 2048 validity 10000` → signature konsisten antar build, gak perlu uninstall tiap update (kecuali pernah install v1.0.32 random key → uninstall sekali)

---

## 🔒 Security Architecture

```
R0 (0-9)    TRUSTED     → Normal
R1 (10-29)  LOW_RISK    → Silent re-verify
R2 (30-49)  SUSPICIOUS  → Extra verification
R3 (50-74)  HIGH_RISK   → Restrict features
R4 (75-100) UNTRUSTED   → Quarantine
```

| Domain | Example | Max |
|--------|---------|-----|
| IDENTITY | package rename | 40 |
| SIGNATURE | cert mismatch | 40 |
| BINARY_INTEGRITY | DEX/native mismatch | 40 |
| MANIFEST | manifest tamper | 40 |
| RUNTIME | debugger/Frida/Xposed | 40 |
| ENVIRONMENT | emulator/root | 40 |
| ATTRIBUTION | credit strip | 40 |
| BACKEND | unofficial build | 40 |
| BUILD | debug rebuild | 40 |

- `@dikaacode` 5 layers: `BuildConfig.CREDIT_*` + `strings credit_*` + class names + SHA-256 + keep rules (`keep.xml` + `BuildConfig { *; }`)
- Keep: `proguard-rules.pro` `public static int credit_*;` + `keep class BuildConfig { *; }`

---

## 🔄 CI/CD

`.github/workflows/android-ci.yml`

| Trigger | Action |
|---------|--------|
| Push `main` | Build debug+release → Upload Artifacts (`FastRecorder-<sha>`) → Deploy 2 APK root ke branch `release` (force) → Create Release `v1.0.<run_number>` stable `prerelease:false` |
| Tag `v*` | Same |
| PR | Build only |

```bash
git push origin main  # auto-release v1.0.<run> muncul di https://github.com/dikaofc/FastRecorderAndroid/releases
```

Permissions `contents: write` only, `checkout@v5` + `setup-java@v5`, `submodules: false` to avoid warn.

---

## 🧪 Testing

```bash
./gradlew testDebugUnitTest --tests "com.dikacode.security.RiskAssessmentTest"
```

| Suite | Tests | Focus |
|-------|-------|-------|
| SecurityTestSuite | 25+ | arch + scenarios |
| RiskAssessmentTest | 40+ | scoring, caps, correlation |
| SecurityPolicyTest | 20+ | R0-R4 |
| SecurityEventTest | 15+ | schema |

---

## 📦 Dependencies

| Cat | Lib |
|-----|-----|
| UI | Jetpack Compose, Material3, ViewBinding, ConstraintLayout |
| Lifecycle | ViewModel, Navigation |
| Coroutines | Android + Core |
| Net | OkHttp 4.10 + `org.json` |
| Other | DataStore, FileProvider |

No hardcode version — `BuildConfig.VERSION_NAME` live from `GITHUB_RUN_NUMBER`.

---

## 📄 License

Proprietary — by **@dikaacode**. No copy/mod/dist without permission.

---

## 🙏 Credits

Developed by **[@dikaacode](https://t.me/dikaacode)** — FAST RECORDER

<p align="center">Made with ❤️ for Android community</p>
