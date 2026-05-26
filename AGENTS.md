# Agent Handoff

## Project
**Kai 9001** — Android fork of Kai. Kotlin Multiplatform (Compose Multiplatform).
Android-only builds. Repo: `kilvz/Kai-custom`.

## What We Built This Session (v1.0.1)

### Phone Tools (8 new tools)
- `PhoneTools.kt` in `composeApp/src/commonMain/kotlin/com/kai/custom/tools/` — ToolInfo definitions
- Implementations in `Platform.android.kt` (`composeApp/src/androidMain/`)
- Tools: GPS location, contacts, device info, battery, network, wifi, clipboard, installed apps
- Every tool is individually toggleable in Settings > Tools via `appSettings.isToolEnabled()`
- Gated behind `ContextCompat.checkSelfPermission` — asks user to grant permissions in Settings
- 30+ Android permissions added to `androidApp/src/main/AndroidManifest.xml`

### Terminal Crash Fix
- `TerminalSheet.kt` — moved `SelectionContainer` from wrapping the entire `LazyColumn` to wrapping each individual `Text` item
- Fixes OOM/ANR when selecting large terminal output

### Sponsor Button
- Single sponsor button inside `FreeSettings` card with heart icon + "Sponsor Kai's Original Author on GitHub"
- Removed duplicate sponsor label from `BottomInfo` (was appearing on every settings tab)

### GitHub Issue URL
- URL: `https://github.com/kilvz/Kai-custom/issues/new?template=integration_request.yml`
- Issues were disabled on the repo — enabled via `gh repo edit --enable-issues=true`

## Key Files
| File | Purpose |
|------|---------|
| `composeApp/src/commonMain/kotlin/com/kai/custom/tools/PhoneTools.kt` | Tool definitions (8 phone tools) |
| `composeApp/src/androidMain/kotlin/com/kai/custom/Platform.android.kt` | Tool implementations + permissions |
| `composeApp/src/commonMain/kotlin/com/kai/custom/ui/settings/TerminalSheet.kt` | Terminal UI (per-item SelectionContainer) |
| `composeApp/src/commonMain/kotlin/com/kai/custom/ui/settings/SettingsScreen.kt` | Settings tabs layout |
| `composeApp/src/commonMain/kotlin/com/kai/custom/ui/settings/IntegrationsSettings.kt` | "Open GitHub Issue" button |
| `composeApp/src/commonMain/kotlin/com/kai/custom/ui/settings/ServicesSettings.kt` | FreeSettings card with sponsor button |
| `androidApp/src/main/AndroidManifest.xml` | All Android permissions |
| `gradle/libs.versions.toml` | Version: appVersion = "1.0.1" |

## Build & Deploy
```powershell
.\gradlew.bat :androidApp:assembleFossDebug --no-configuration-cache
adb install -r androidApp\build\outputs\apk\foss\debug\androidApp-foss-debug.apk
```

## Notes
- FOSS build flavor only — no proprietary SDKs (no Firebase, no analytics, no crash reporting)
- Ktor HTTP client (not OkHttp/Retrofit)
- All network calls are user-initiated or user-configured (AI provider, email, MCP)
- No WebView, no WebSocket, no telemetry
