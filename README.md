<div align="center">

<!-- HERO SVG — pulsing REC + FAST RECORDER neobrutalism -->
<svg width="100%" height="200" viewBox="0 0 820 200" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="FAST RECORDER hero">
  <defs>
    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#FFE600"/>
      <stop offset="100%" stop-color="#FFEB3B"/>
    </linearGradient>
    <filter id="shadow" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="6" dy="6" stdDeviation="0" flood-color="#0A0A0A" flood-opacity="1"/>
    </filter>
  </defs>
  <!-- card -->
  <rect x="10" y="10" width="800" height="180" rx="22" fill="#0A0A0A" filter="url(#shadow)"/>
  <rect x="10" y="10" width="800" height="180" rx="22" fill="none" stroke="#FFE600" stroke-width="6"/>
  <!-- pulse outer -->
  <circle cx="90" cy="100" r="52" fill="#FF3B30" opacity="0.18">
    <animate attributeName="r" values="52;64;52" dur="1.6s" repeatCount="indefinite"/>
    <animate attributeName="opacity" values="0.18;0.06;0.18" dur="1.6s" repeatCount="indefinite"/>
  </circle>
  <circle cx="90" cy="100" r="38" fill="#FF3B30" opacity="0.32">
    <animate attributeName="r" values="38;46;38" dur="1.6s" begin="0.2s" repeatCount="indefinite"/>
    <animate attributeName="opacity" values="0.32;0.12;0.32" dur="1.6s" begin="0.2s" repeatCount="indefinite"/>
  </circle>
  <!-- REC button -->
  <circle cx="90" cy="100" r="32" fill="#FF3B30" stroke="white" stroke-width="3"/>
  <circle cx="90" cy="100" r="9" fill="white">
    <animate attributeName="opacity" values="1;0.35;1" dur="1s" repeatCount="indefinite"/>
  </circle>
  <!-- text -->
  <text x="150" y="78" font-family="'JetBrains Mono','Fira Code',monospace" font-size="44" font-weight="900" fill="white" letter-spacing="2">FAST</text>
  <text x="150" y="122" font-family="'JetBrains Mono','Fira Code',monospace" font-size="44" font-weight="900" fill="url(#g)" letter-spacing="2">RECORDER</text>
  <text x="150" y="152" font-family="monospace" font-size="12.5" fill="#AAAAAA">MediaProjection • Internal Audio • 60 FPS • by @dikaacode</text>
  <!-- right badge -->
  <g transform="translate(640,36)">
    <rect width="138" height="28" rx="14" fill="#FFE600" stroke="#0A0A0A" stroke-width="2.5"/>
    <circle cx="14" cy="14" r="6" fill="#00C853"><animate attributeName="opacity" values="1;0.4;1" dur="1s" repeatCount="indefinite"/></circle>
    <text x="26" y="18.5" font-family="monospace" font-size="11" font-weight="800" fill="#0A0A0A">● LIVE • v1.0</text>
  </g>
  <g transform="translate(640,72)">
    <rect width="138" height="26" rx="13" fill="white" stroke="#0A0A0A" stroke-width="2.5"/>
    <text x="69" y="17.5" text-anchor="middle" font-family="monospace" font-size="10" font-weight="800" fill="#0A0A0A">NEOBRUTALISM ✦</text>
  </g>
</svg>

<!-- typing animation -->
<p>
  <a href="https://github.com/dikaofc/FastRecorderAndroid/releases"><img src="https://readme-typing-svg.demolab.com?font=JetBrains+Mono&weight=800&size=18&duration=2200&pause=900&color=0A0A0A&background=FFE600&center=true&vCenter=true&width=560&height=36&lines=FAST+RECORDER+%E2%80%A2+Screen+Capture+Reimagined;MediaProjection+%2B+Internal+Audio+%E2%80%A2+Zero+Bloat" alt="typing"/></a>
</p>

<a href="https://github.com/dikaofc/FastRecorderAndroid/releases"><img src="https://img.shields.io/badge/⬇_Download-Latest_Release-2E7D32?style=for-the-badge" alt="Download"/></a>
<img src="https://img.shields.io/badge/Android-5.0%2B-3DDC84?logo=android" alt="Min SDK"/>
<img src="https://img.shields.io/badge/Target-SDK%2034-0066CC" alt="Target SDK"/>
<img src="https://img.shields.io/badge/Compile-36.1-673AB7" alt="Compile"/>
<img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin" alt="Kotlin"/>
<img src="https://img.shields.io/badge/CI-Android_CI_%2B_Release-00C853" alt="CI"/>

</div>

<!-- EQUALIZER SVG — animated bars -->
<div align="center">

<svg width="100%" height="64" viewBox="0 0 640 64" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="equalizer">
  <rect x="8" y="8" width="624" height="48" rx="14" fill="#FFE600" stroke="#0A0A0A" stroke-width="3.5"/>
  <g transform="translate(28,12)">
    <rect x="0" y="10" width="10" height="20" rx="5" fill="#0A0A0A"><animate attributeName="height" values="12;28;14;22;12" dur="0.9s" repeatCount="indefinite"/><animate attributeName="y" values="14;6;13;9;14" dur="0.9s" repeatCount="indefinite"/></rect>
    <rect x="18" y="6" width="10" height="28" rx="5" fill="#FF3B30"><animate attributeName="height" values="28;14;26;10;28" dur="0.85s" repeatCount="indefinite"/><animate attributeName="y" values="6;13;7;15;6" dur="0.85s" repeatCount="indefinite"/></rect>
    <rect x="36" y="14" width="10" height="12" rx="5" fill="#0A0A0A"><animate attributeName="height" values="12;22;8;28;12" dur="0.95s" repeatCount="indefinite"/><animate attributeName="y" values="14;9;16;6;14" dur="0.95s" repeatCount="indefinite"/></rect>
    <rect x="54" y="8" width="10" height="24" rx="5" fill="#0A0A0A"><animate attributeName="height" values="24;10;30;16;24" dur="0.8s" repeatCount="indefinite"/><animate attributeName="y" values="8;15;5;11;8" dur="0.8s" repeatCount="indefinite"/></rect>
    <rect x="72" y="12" width="10" height="16" rx="5" fill="#FF3B30"><animate attributeName="height" values="16;30;12;24;16" dur="1s" repeatCount="indefinite"/><animate attributeName="y" values="12;5;14;8;12" dur="1s" repeatCount="indefinite"/></rect>
    <rect x="90" y="10" width="10" height="20" rx="5" fill="#0A0A0A"><animate attributeName="height" values="20;12;28;14;20" dur="0.88s" repeatCount="indefinite"/><animate attributeName="y" values="10;14;6;13;10" dur="0.88s" repeatCount="indefinite"/></rect>
    <text x="120" y="26" font-family="monospace" font-size="12" font-weight="800" fill="#0A0A0A">720p • 60 FPS • INTERNAL+MIC • PTS FIXED • AUTO-INSTALL</text>
  </g>
</svg>

</div>

---

## ⬇️ Download

<table>
<tr>
<td align="center" width="50%">

<!-- download card SVG -->
<svg width="280" height="92" viewBox="0 0 280 92" xmlns="http://www.w3.org/2000/svg" role="img">
  <rect x="4" y="4" width="272" height="84" rx="16" fill="#0A0A0A"/>
  <rect x="4" y="4" width="272" height="84" rx="16" fill="none" stroke="#0A0A0A" stroke-width="3"/>
  <rect width="280" height="92" rx="16" fill="#FFE600" stroke="#0A0A0A" stroke-width="3"/>
  <text x="140" y="32" text-anchor="middle" font-family="monospace" font-size="13" font-weight="900" fill="#0A0A0A">⬇  LATEST RELEASE</text>
  <text x="140" y="52" text-anchor="middle" font-family="monospace" font-size="11" fill="#333">app-release.apk + app-debug.apk</text>
  <text x="140" y="72" text-anchor="middle" font-family="monospace" font-size="9" fill="#666">2 APK di root • stable • auto v1.0.&lt;run&gt;</text>
</svg>

**[→ github.com/dikaofc/FastRecorderAndroid/releases](https://github.com/dikaofc/FastRecorderAndroid/releases)**

</td>
<td width="50%">

- **Branch `release`:** https://github.com/dikaofc/FastRecorderAndroid/tree/release — `app-release.apk` & `app-debug.apk` di root (auto-deploy tiap push `main`)
- **In-app:** `Settings → App Update → CHECK FOR UPDATE` — auto tiap 6 jam + notif + progress 0–100% + auto-install + `Delete APK after update`

> Push `main` → auto-build & auto-publish `v1.0.<run_number>` stable — no tag manual, tapi `git tag v*` tetap jalan.

</td>
</tr>
</table>

---

## ✨ Features

<!-- feature grid with animated icons -->
<div align="center">

<table>
<tr>
<td align="center" width="25%">

<svg width="84" height="84" viewBox="0 0 84 84" xmlns="http://www.w3.org/2000/svg">
  <rect width="84" height="84" rx="18" fill="#FFE600" stroke="#0A0A0A" stroke-width="3"/>
  <circle cx="42" cy="42" r="22" fill="#FF3B30" opacity="0.2"><animate attributeName="r" values="22;26;22" dur="1.4s" repeatCount="indefinite"/></circle>
  <circle cx="42" cy="42" r="16" fill="#FF3B30" stroke="white" stroke-width="2"/>
  <circle cx="42" cy="42" r="5" fill="white"><animate attributeName="opacity" values="1;0.3;1" dur="1s" repeatCount="indefinite"/></circle>
</svg>

**RECORDING**
PTS fixed `25:43:17`

</td>
<td align="center" width="25%">

<svg width="84" height="84" viewBox="0 0 84 84" xmlns="http://www.w3.org/2000/svg">
  <rect width="84" height="84" rx="18" fill="#0A0A0A" stroke="#0A0A0A" stroke-width="3"/>
  <rect x="22" y="24" width="40" height="28" rx="6" fill="none" stroke="white" stroke-width="3"/>
  <polygon points="62,42 48,32 48,52" fill="white"/>
  <rect x="28" y="54" width="28" height="4" rx="2" fill="#FFE600"/>
</svg>

**GALLERY**
Catbox batch

</td>
<td align="center" width="25%">

<svg width="84" height="84" viewBox="0 0 84 84" xmlns="http://www.w3.org/2000/svg">
  <rect width="84" height="84" rx="18" fill="white" stroke="#0A0A0A" stroke-width="3"/>
  <path d="M42 18 L66 30 L66 58 L42 70 L18 58 L18 30 Z" fill="none" stroke="#0A0A0A" stroke-width="3" stroke-linejoin="round"/>
  <path d="M42 34 L42 48 M42 52 L42 56" stroke="#00C853" stroke-width="3" stroke-linecap="round"/>
  <circle cx="42" cy="42" r="18" fill="none" stroke="#00C853" stroke-width="2" opacity="0.2"><animate attributeName="r" values="18;22;18" dur="1.6s" repeatCount="indefinite"/></circle>
</svg>

**SECURITY**
11 modules

</td>
<td align="center" width="25%">

<svg width="84" height="84" viewBox="0 0 84 84" xmlns="http://www.w3.org/2000/svg">
  <rect width="84" height="84" rx="18" fill="#00C853" stroke="#0A0A0A" stroke-width="3"/>
  <path d="M42 22 L62 32 L62 52 L42 64 L22 52 L22 32 Z" fill="white" stroke="#0A0A0A" stroke-width="2.5"/>
  <path d="M32 42 L40 50 L54 34" fill="none" stroke="#00C853" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
</svg>

**UPDATER**
Live GitHub

</td>
</tr>
</table>

</div>

### 🎬 Recording (PTS Fixed)
- **MediaProjection** capture (same API as WhatsApp/Discord)
- **Internal audio** via `AudioPlaybackCapture` (Android 10+) — `NONE / MIC / INTERNAL / INTERNAL+MIC`
- **Resolutions:** 1080p / 720p / 480p / 360p • **FPS:** 60 / 30 / 24 / 15
- **Battery saver** & **High performance** toggle
- **Notification controls:** `Pause` ↔ `Resume` & `Stop` dari shade tanpa buka app
- **Auto-install after download:** selesai download langsung launch installer (600ms), anti stuck `Downloaded` doang
- **PTS normalization** — `pts - firstVideoPts - pauseOffset` (fix `25:43:17`)

### 🎨 Neobrutalism UI
- Bold borders, high contrast, `bg_neo_*` — no emoji hardcode, semua `VectorDrawable` (`ic_close`, `ic_check_neo`, `ic_warning`, `ic_folder`)
- **Dark mode** full • Responsive `ScrollView weight=1` + `clipChildren=false` (no button kepotong, no pulse kepotong kotak)
- Home `FAST RECORDER` + pulsing `REC` 120dp / container 180dp → scale 1.5x = 180dp pas

### 📁 Gallery
- Play / rename / delete / share / Catbox upload (batch) • Temp `updates/` >24h auto purge

### ⚙️ Settings
- Resolution / FPS / Audio spinners, toggles (overlay / high perf / battery / autoUpload / darkMode)
- **Storage** picker `ACTION_OPEN_DOCUMENT_TREE` + progress `free/total` + threshold
- **Security diagnostics** + **App Update** (`Current: v1.0.x (code)`, `CHECK FOR UPDATE`, path tap-to-copy)

### 🔒 Security (Defense-in-Depth)
- **11 modules, 9 domains, 5 states:** `TRUSTED → LOW_RISK → SUSPICIOUS → HIGH_RISK → UNTRUSTED`
- **Trust banner** 18sp `singleLine ellipsize` di `bg_neo_spinner` (fix hijau keluar box) + `attributionRow` weight-1
- **Diagnostics** `ScrollView 0dp weight1 fillViewport`, rows `11-12sp maxLines3 breakStrategy simple` — no kepotong

### 🔄 Updater (Live from GitHub, zero hardcode)
- `REPO = dikaofc/FastRecorderAndroid` + `API .../releases?per_page=10` + `User-Agent .../<ver>` + `X-GitHub-Api-Version`
- **Live:** `PackageManager.versionName` vs `JSONArray tag_name` filter `!draft && !prerelease` + `isNewerVersion()`
- **403 fallback:** API `403 rate limit (60/jam)` → auto `https://github.com/.../releases/latest` (302 → `v1.0.x`) → synthetic `app-release.apk` direct URL

---

## 📁 Project Structure

```
FastRecorderAndroid/
├── app/src/main/java/com/dikacode/
│   ├── MainActivity.kt                 # Home FAST RECORDER + REC pulse 180dp + auto-check notif
│   ├── SettingsActivity.kt             # Settings + App Update (checkForUpdate, delete toggle)
│   ├── GalleryActivity.kt              # Gallery + Catbox
│   ├── SecurityDiagnosticsActivity.kt  # Diagnostics responsive (trust 18sp, attributionRow)
│   ├── recorder/ScreenRecorder.kt      # PTS offset normalization, HEVC/AVC, pause offset
│   ├── update/GitHubUpdater.kt         # Live API + 403 web fallback + FileProvider install
│   ├── update/UpdateDialog.kt          # Scroll dialog, auto-install, provider
│   ├── update/UpdateNotifier.kt        # HIGH channel PendingIntent
│   ├── security/*                      # SecurityEngine, RiskAssessment, IdentityVerifier, ...
│   └── service/RecordingForegroundService.kt # Foreground + Pause/Resume/Stop
├── app/src/main/res/
│   ├── layout/activity_main.xml              # FAST RECORDER, clipChildren false
│   ├── layout/dialog_update.xml              # ScrollView + ic_close + ic_folder
│   ├── layout/dialog_developer_info.xml      # ScrollView + ic_close
│   ├── layout/activity_security_diagnostics.xml # ScrollView weight1, attributionRow
│   ├── drawable/ic_close / ic_folder / ic_check_neo / ic_warning
│   └── xml/file_paths.xml
└── .github/workflows/android-ci.yml    # Build + Deploy root + Release v1.0.<run>
```

<!-- flow SVG -->
<div align="center">

<svg width="100%" height="72" viewBox="0 0 720 72" xmlns="http://www.w3.org/2000/svg">
  <rect x="8" y="8" width="704" height="56" rx="16" fill="white" stroke="#0A0A0A" stroke-width="3"/>
  <g font-family="monospace" font-size="11" font-weight="800" fill="#0A0A0A" text-anchor="middle">
    <rect x="18" y="20" width="110" height="30" rx="10" fill="#FFE600" stroke="#0A0A0A" stroke-width="2"/><text x="73" y="39">push main</text>
    <text x="140" y="39">→</text>
    <rect x="152" y="20" width="110" height="30" rx="10" fill="#0A0A0A" stroke="#0A0A0A" stroke-width="2"/><text x="207" y="39" fill="white">Build APK</text>
    <text x="274" y="39">→</text>
    <rect x="286" y="20" width="130" height="30" rx="10" fill="#00C853" stroke="#0A0A0A" stroke-width="2"/><text x="351" y="39" fill="white">Deploy release</text>
    <text x="428" y="39">→</text>
    <rect x="440" y="20" width="130" height="30" rx="10" fill="#FF3B30" stroke="#0A0A0A" stroke-width="2"/><text x="505" y="39" fill="white">Release v1.0.x</text>
    <text x="582" y="39">→</text>
    <rect x="594" y="20" width="110" height="30" rx="10" fill="#FFE600" stroke="#0A0A0A" stroke-width="2"/><text x="649" y="39">In-app update</text>
  </g>
</svg>

</div>

---

## 🚀 Build & Install

### Prerequisites
- Android Studio Hedgehog+ • JDK 17 • Android SDK 36.1

```bash
./gradlew assembleDebug          # debug
./gradlew assembleRelease        # release (optional keystore env)
./gradlew testDebugUnitTest --tests "com.dikacode.security.*"  # tests (avoid AGP test --tests)
adb install app/build/outputs/apk/debug/app-debug.apk
# atau download app-release.apk dari Releases → Allow unknown sources
```

### Versioning & Signing (no signature conflict)
- `versionCode = GITHUB_RUN_NUMBER ?: 1` → `versionName = 1.0.<run>` — selalu naik
- Debug keystore stable: `if [ -f debug.keystore ] reuse else keytool -genkeypair RSA 2048 validity 10000` → konsisten antar build

---

## 🔒 Security Architecture

<div align="center">

<svg width="100%" height="92" viewBox="0 0 640 92" xmlns="http://www.w3.org/2000/svg">
  <rect x="8" y="8" width="624" height="76" rx="16" fill="#0A0A0A" stroke="#0A0A0A" stroke-width="3"/>
  <g font-family="monospace" font-size="11" font-weight="800" text-anchor="middle">
    <rect x="18" y="22" width="108" height="48" rx="12" fill="#00C853"/><text x="72" y="42" fill="white">R0 TRUSTED</text><text x="72" y="58" fill="white" font-size="9">0-9</text>
    <text x="136" y="50" fill="white">→</text>
    <rect x="148" y="22" width="108" height="48" rx="12" fill="#FFEB3B" stroke="#0A0A0A" stroke-width="2"/><text x="202" y="42" fill="#0A0A0A">R1 LOW_RISK</text><text x="202" y="58" fill="#0A0A0A" font-size="9">10-29</text>
    <text x="266" y="50" fill="white">→</text>
    <rect x="278" y="22" width="108" height="48" rx="12" fill="#FF9800"/><text x="332" y="42" fill="white">R2 SUSPIC.</text><text x="332" y="58" fill="white" font-size="9">30-49</text>
    <text x="396" y="50" fill="white">→</text>
    <rect x="408" y="22" width="108" height="48" rx="12" fill="#FF5722"/><text x="462" y="42" fill="white">R3 HIGH</text><text x="462" y="58" fill="white" font-size="9">50-74</text>
    <text x="526" y="50" fill="white">→</text>
    <rect x="538" y="22" width="86" height="48" rx="12" fill="#B71C1C"/><text x="581" y="42" fill="white">R4</text><text x="581" y="58" fill="white" font-size="9">75-100</text>
  </g>
</svg>

</div>

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

`@dikaacode` 5 layers: `BuildConfig.CREDIT_*` + `strings credit_*` + class names + SHA-256 + keep (`keep.xml` + `BuildConfig { *; }`) — `proguard-rules.pro` `public static int credit_*;`

---

## 🔄 CI/CD

`.github/workflows/android-ci.yml`

| Trigger | Action |
|---------|--------|
| Push `main` | Build debug+release → Artifacts `FastRecorder-<sha>` → Deploy 2 APK root → Release `v1.0.<run>` stable |
| Tag `v*` | Same |
| PR | Build only |

```bash
git push origin main  # auto-release v1.0.<run> di https://github.com/dikaofc/FastRecorderAndroid/releases
```
`contents: write` only, `checkout@v5` + `setup-java@v5`, `submodules: false`.

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
| UI | Compose, Material3, ViewBinding, ConstraintLayout |
| Lifecycle | ViewModel, Navigation |
| Coroutines | Android + Core |
| Net | OkHttp 4.10 + `org.json` |
| Other | DataStore, FileProvider |

No hardcode — `BuildConfig.VERSION_NAME` live `GITHUB_RUN_NUMBER`.

---

## 📄 License

Proprietary — by **@dikaacode**. No copy/mod/dist without permission.

---

## 🙏 Credits

<div align="center">

<svg width="320" height="54" viewBox="0 0 320 54" xmlns="http://www.w3.org/2000/svg">
  <rect width="320" height="54" rx="16" fill="#0A0A0A" stroke="#0A0A0A" stroke-width="3"/>
  <rect x="3" y="3" width="314" height="48" rx="13" fill="none" stroke="#FFE600" stroke-width="2.5"/>
  <text x="160" y="23" text-anchor="middle" font-family="monospace" font-size="12" font-weight="800" fill="white">Developed by @dikaacode</text>
  <text x="160" y="38" text-anchor="middle" font-family="monospace" font-size="9" fill="#FFE600">t.me/dikaacode  •  FAST RECORDER</text>
</svg>

<p>Made with ❤️ for Android community — <b>FAST RECORDER</b></p>

</div>
