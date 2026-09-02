<p align="center">
  <img src="app/src/main/res/drawable/fast_recorder_logo_1788107942806.jpg" width="120" alt="FastRecorder Logo"/>
</p>

<h1 align="center">FastRecorder</h1>

<p align="center">
  <strong>Ultra-lightweight Android screen recorder with neobrutalism UI</strong><br>
  <em>Developed by <a href="https://t.me/dikaacode">@dikaacode</a></em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-5.0%2B-green" alt="Min SDK"/>
  <img src="https://img.shields.io/badge/Target-SDK%2034-blue" alt="Target SDK"/>
  <img src="https://img.shields.io/badge/Language-Kotlin-purple" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/License-proprietary-red" alt="License"/>
</p>

---

## ✨ Features

### 🎬 Screen Recording
- **MediaProjection** screen capture (same API as WhatsApp/Discord)
- **Internal system audio** capture via AudioPlaybackCapture (Android 10+)
- **Microphone** audio recording
- **Internal + Microphone** simultaneous recording
- **Configurable resolution**: 1080p, 720p, 480p, 360p
- **Configurable FPS**: 60, 30, 24, 15
- **Battery saver mode**: reduced bitrate & hidden overlay
- **High performance mode**: maximum frame stability

### 🎨 Neobrutalism UI
- Bold, high-contrast design with thick borders
- Dark mode support
- Floating overlay controls
- Pulsing recording indicator
- Responsive layout for all screen sizes

### 📁 Gallery
- Built-in video gallery
- Video playback, rename, delete, share
- Catbox cloud upload with batch support
- Automatic temp file cleanup (24h)

### ⚙️ Settings
- Storage directory selection (internal/SD card)
- Storage usage monitoring with progress bar
- Cache management
- Auto-upload to cloud

### 🔒 Security System (Defense-in-Depth)
- **11 security modules** with 9 signal domains
- **5-level trust state machine**: TRUSTED → UNTRUSTED
- **Cryptographic identity verification**: package, certificate, build
- **Binary integrity checks**: DEX, native libs, manifest
- **Runtime detection**: debugger, Frida, Xposed, root, emulator
- **Attribution protection**: 5-layer @dikaacode credit system
- **Graceful failure**: never crashes or damages device

---

## 📁 Project Structure

```
FastRecorderAndroid/
├── app/src/main/java/com/dikacode/
│   ├── MainActivity.kt                    # Main screen with record button
│   ├── SettingsActivity.kt                # App settings
│   ├── GalleryActivity.kt                 # Video gallery & cloud upload
│   ├── SecurityDiagnosticsActivity.kt     # Security status dashboard
│   ├── recorder/
│   │   ├── ScreenRecorder.kt              # MediaProjection recording engine
│   │   ├── SettingsManager.kt             # SharedPreferences settings
│   │   ├── StorageUtils.kt                # Storage info & directory management
│   │   ├── StorageThresholdNotifier.kt    # Storage limit alerts
│   │   ├── TempFileCleaner.kt             # Temp file cleanup
│   │   └── CatboxUploader.kt             # Catbox.moe cloud upload
│   ├── security/
│   │   ├── SecurityEngine.kt              # Main orchestrator
│   │   ├── RiskAssessment.kt              # Risk score calculation
│   │   ├── SecuritySignal.kt              # Signal definitions (20+ types)
│   │   ├── SecurityEvent.kt               # Audit trail schema
│   │   ├── SecurityPolicy.kt              # R0-R4 response matrix
│   │   ├── IdentityVerifier.kt            # Package/certificate verification
│   │   ├── IntegrityChecker.kt            # Binary integrity checks
│   │   ├── RuntimeDetector.kt             # Anti-debug/anti-hook
│   │   ├── AttributionGuard.kt            # @dikaacode credit protection
│   │   ├── CreditManager.kt               # Persistent credit verification
│   │   └── SecurityManager.kt             # Legacy compatibility wrapper
│   ├── service/
│   │   ├── RecordingForegroundService.kt  # Foreground recording service
│   │   ├── RecordingState.kt              # Recording state management
│   │   └── OverlayManager.kt             # Floating overlay controls
│   └── ui/
│       ├── components/NeoComponents.kt    # Neobrutalism UI components
│       └── theme/                         # Theme, colors, typography
├── app/src/test/java/com/dikacode/security/
│   ├── SecurityTestSuite.kt               # Master test orchestrator
│   ├── RiskAssessmentTest.kt              # Risk calculation tests
│   ├── SecurityPolicyTest.kt              # Policy response tests
│   └── SecurityEventTest.kt               # Event schema tests
└── .github/workflows/android-ci.yml       # CI/CD pipeline
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 36

### Build

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease
```

### Install

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔒 Security Architecture

### Trust State Machine

```
R0 (0-9)    TRUSTED     → Normal operation
R1 (10-29)  LOW_RISK    → Silent re-verification
R2 (30-49)  SUSPICIOUS  → Additional verification
R3 (50-74)  HIGH_RISK   → Restrict sensitive features
R4 (75-100) UNTRUSTED   → Quarantine mode
```

### Signal Domains (9)

| Domain | Examples | Max Weight |
|--------|----------|------------|
| **IDENTITY** | Package rename, label change | 40 |
| **SIGNATURE** | Certificate mismatch, APK re-signed | 40 |
| **BINARY_INTEGRITY** | DEX mismatch, native lib modified | 40 |
| **MANIFEST** | Manifest integrity failure | 40 |
| **RUNTIME** | Debugger, Frida, Xposed detected | 40 |
| **ENVIRONMENT** | Emulator, rooted device | 40 |
| **ATTRIBUTION** | Credit tampered/removed | 40 |
| **BACKEND** | Server says unofficial build | 40 |
| **BUILD** | Debug build, unofficial rebuild | 40 |

### Anti-Tamper Protection

| Layer | Protection |
|-------|------------|
| **Package** | `com.dika.fastrecorder` verified at startup |
| **Certificate** | SHA-256 fingerprint of signing key |
| **Binary** | DEX/native library integrity hashes |
| **Runtime** | Debugger, hooking, injection detection |
| **Attribution** | 5-layer @dikaacode credit system |

### @dikaacode Attribution (5 Layers)

1. **BuildConfig** — compile-time constants
2. **String Resources** — `credit_developer`, `credit_team`
3. **Class Names** — `SecurityManager`, `CreditManager`
4. **Checksums** — SHA-256 hashes of credit strings
5. **ProGuard Rules** — `-keep` rules prevent removal

---

## 🧪 Testing

```bash
# Run all security tests
./gradlew test --tests "com.dikacode.security.*"

# Run specific test class
./gradlew test --tests "com.dikacode.security.RiskAssessmentTest"
```

### Test Coverage

| Test Class | Tests | Coverage |
|------------|-------|----------|
| `SecurityTestSuite` | 25+ | Architecture validation, real-world scenarios |
| `RiskAssessmentTest` | 40+ | Risk scoring, domain caps, correlation |
| `SecurityPolicyTest` | 20+ | R0-R4 responses, feature restrictions |
| `SecurityEventTest` | 15+ | Event schema, signal details |

---

## 🔄 CI/CD

**GitHub Actions** workflow (`.github/workflows/android-ci.yml`):

| Trigger | Action |
|---------|--------|
| Push to `main` | Build debug + release APK |
| Pull request | Build debug + release APK |
| Tag `v*` | Build + auto-create GitHub Release |

### Release

```bash
git tag v1.0.0
git push origin v1.0.0
```

GitHub automatically:
1. Builds debug & release APK
2. Creates GitHub Release
3. Attaches APK files

### Signing

- **Debug**: auto-generated `debug.keystore`
- **Release**: set env vars `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`

---

## 📦 Dependencies

| Category | Library |
|----------|---------|
| **UI** | Jetpack Compose, Material3, ViewBinding |
| **Lifecycle** | ViewModel, Lifecycle Runtime, Navigation |
| **Coroutines** | Kotlin Coroutines (Android + Core) |
| **Networking** | OkHttp 4.10 |
| **Storage** | DataStore Preferences |
| **Testing** | JUnit, Robolectric, Roborazzi |

---

## 📄 License

This project is proprietary software developed by **@dikaacode**.

Unauthorized copying, modification, distribution, or use of this software is strictly prohibited.

---

## 🙏 Credits

Developed by **[@dikaacode](https://t.me/dikaacode)**

---

<p align="center">
  Made with ❤️ for the Android community
</p>
