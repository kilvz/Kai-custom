# Agent Handoff — Kai-custom (FOSS)

## Repo
- **URL**: `https://github.com/kilvz/Kai-custom.git`
- **Workspace**: `F:\Kai\`
- **Remote**: `origin` → `github.com/kilvz/Kai-custom`
- **Upstream**: `upstream` → `github.com/SimonSchubert/Kai`

## Project
Public FOSS fork of Kai. Kotlin Multiplatform (Compose Multiplatform).
**Android-only builds** (iOS/Desktop/WasmJS targets exist in source but are not tested on this Windows machine).

## What This Repo Excludes
- **No Play Store files**: no `androidApp/src/playStore/`, no `com.kilv.ai` package override, no permission stripping overlay

## Build Commands
```powershell
# FOSS debug APK (the only build)
.\gradlew.bat :androidApp:assembleFossDebug --no-configuration-cache

# Deploy to device
adb install -r androidApp\build\outputs\apk\foss\debug\androidApp-foss-debug.apk
```

## APK Output
`androidApp\build\outputs\apk\foss\debug\androidApp-foss-debug.apk`

## Key Files
| File | Purpose |
|------|---------|
| `composeApp/src/commonMain/kotlin/com/kai/custom/App.kt` | App entry point |
| `composeApp/src/commonMain/kotlin/com/kai/custom/ui/chat/ChatUiState.kt` | `History.Role.SYSTEM` message role |
| `composeApp/src/commonMain/kotlin/com/kai/custom/ui/chat/ChatScreen.kt` | Chat UI rendering (skips SYSTEM role) |
| `composeApp/src/commonMain/kotlin/com/kai/custom/ui/chat/ChatViewModel.kt` | Voice input, retry use `addSystemMessage()` |
| `composeApp/src/commonMain/kotlin/com/kai/custom/data/DataRepository.kt` | Interface with `addSystemMessage()` |
| `composeApp/src/commonMain/kotlin/com/kai/custom/data/RemoteDataRepository.kt` | Implements `addSystemMessage()`, SYSTEM persist/load, `editAndBranch()` |
| `composeApp/src/commonMain/kotlin/com/kai/custom/ui/settings/GeneralSettings.kt` | Wake word (Experimental chip) |
| `composeApp/src/commonMain/kotlin/com/kai/custom/SandboxController.kt` | `writeBinaryFile()` interface |
| `composeApp/src/androidMain/kotlin/com/kai/custom/SandboxController.android.kt` | Android `writeBinaryFile` impl |
| `composeApp/src/commonMain/kotlin/com/kai/custom/tools/CommonTools.kt` | All memory tool definitions including `search_memories` |
| `composeApp/src/commonMain/kotlin/com/kai/custom/data/AppSettings.kt` | Memory instructions, tool gating |
| `composeApp/src/commonMain/kotlin/com/kai/custom/data/ChatSystemPromptBuilder.kt` | Builds system prompt with memory sections |
| `composeApp/src/commonMain/kotlin/com/kai/custom/tools/HeartbeatTools.kt` | `getPromoteLearningTool()` replaces old heartbeat |
| `composeApp/src/commonMain/kotlin/com/kai/custom/tools/PhoneTools.kt` | 8 phone tool definitions |
| `composeApp/src/androidMain/kotlin/com/kai/custom/Platform.android.kt` | Tool implementations + perms + promote_learning gating |
| `androidApp/src/foss/AndroidManifest.xml` | SMS + notification listener (FOSS-only features) |
| `PRIVACY.md` | Privacy policy |
| `gradle/libs.versions.toml` | Version: `appVersion`, `android-versionCode` |
| `androidApp/src/main/AndroidManifest.xml` | All Android permissions (30+) |

## Version
Current: `1.10.0` (versionCode `115`) — update in `gradle/libs.versions.toml`

## Architecture
- **UI**: Compose Multiplatform (Material 3)
- **DI**: Koin
- **HTTP**: Ktor client
- **State**: ViewModels + StateFlow
- **Inference**: LiteRT (on-device) + cloud providers (OpenAI, Anthropic, Gemini, etc.)
- **Sandbox**: PRoot-based Linux sandbox (arm64, x86_64)
- **Memory**: Local palace store (SQLite-based vector/keyword storage)
- **Tools**: 30+ tools (shell, ssh, email, sms, web search, calendar, phone, etc.)

## Rules
- **Never** add Play Store code (no `androidApp/src/playStore/`, no `isPlayStore` gating in build.gradle.kts)
- **Always** build with `--no-configuration-cache` to avoid cache corruption
- iOS/Desktop/WasmJS builds cannot be tested on this Windows machine
