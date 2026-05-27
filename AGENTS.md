# Agent Handoff

## Project
**Kai 9001** — Android fork of Kai. Kotlin Multiplatform (Compose Multiplatform).
Android-only builds. Repo: `kilvz/Kai-custom`.

## Versioning Convention
- **Major** bump (X.0.0) — breaking changes, new architectural patterns, major feature additions
- **Minor** bump (1.X.0) — any new feature, tool, or non-trivial improvement
- Patch (1.0.X) — bug fixes only (deprecated; all changes should be at least minor)

## What We Built This Session (v1.4.0)

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

### Sandbox File Write for Binary Attachments (Excel, Word, etc.)
- Added `writeBinaryFile(path, data)` to `SandboxController` interface + Android implementation + NoOp stubs
- `RemoteDataRepository.ask()` now writes binary files (non-image, non-PDF, non-text) to sandbox at `/root/uploads/{filename}`
- Sandbox paths are appended to the question text as `\n[File saved to sandbox: /root/uploads/filename.xlsx]`
- The AI can read these files using `execute_shell_command` with e.g. `python3 -c "import openpyxl; ..." /root/uploads/file.xlsx`
- Files written only when sandbox is `Ready`; falls back to text stub silently if sandbox not available
- Fixes the root cause of the DeepSeek error when uploading Excel files — AI can now actually access the file content instead of only seeing `[Attached file: ...]`

### Memory System Overhaul
- Added `search_memories(query, limit)` tool — AI can now query its own memory store on demand
- Rewrote `DEFAULT_MEMORY_INSTRUCTIONS` as a dedicated `## Memory System` header covering all 6 memory tools
- Added `DEFAULT_LOCAL_MEMORY_INSTRUCTIONS` (trimmed for on-device models without memory_learn/promote_learning)
- Variant-aware default selection in `getActiveSystemPrompt()`
- Fixed `promote_learning` gating: now controlled by `isMemoryEnabled()` instead of `isSchedulingEnabled()`
- Fixed `AgentSettings.kt` double MemoryList rendering
- Added `setMemoryInstructions()` + `hasCustomMemoryInstructions()` to `AppSettings`

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
| `composeApp/src/commonMain/kotlin/com/kai/custom/SandboxController.kt` | `writeBinaryFile()` interface |
| `composeApp/src/androidMain/kotlin/com/kai/custom/SandboxController.android.kt` | Android `writeBinaryFile` impl (writes raw bytes to sandbox) |
| `composeApp/src/commonMain/kotlin/com/kai/custom/data/RemoteDataRepository.kt` | `ask()` writes binary attachments to sandbox |
| `gradle/libs.versions.toml` | Version: appVersion = "1.4.0" |
| `composeApp/src/commonMain/kotlin/com/kai/custom/tools/CommonTools.kt` | search_memories tool + all memory tool definitions |
| `composeApp/src/commonMain/kotlin/com/kai/custom/data/AppSettings.kt` | DEFAULT_MEMORY_INSTRUCTIONS, DEFAULT_LOCAL_MEMORY_INSTRUCTIONS, setMemoryInstructions() |
| `composeApp/src/commonMain/kotlin/com/kai/custom/data/ChatSystemPromptBuilder.kt` | System prompt builder (memory sections, no more DEFAULT_STRUCTURED_LEARNING_SECTION) |
| `composeApp/src/commonMain/kotlin/com/kai/custom/data/RemoteDataRepository.kt` | Variant-aware default selection, LOCAL_TOOL_ALLOWLIST |
| `composeApp/src/androidMain/kotlin/com/kai/custom/Platform.android.kt` | promote_learning gated by memoryEnabled |
| `composeApp/src/commonMain/kotlin/com/kai/custom/tools/HeartbeatTools.kt` | getPromoteLearningTool() replaces getHeartbeatTools() |
| `composeApp/src/commonMain/kotlin/com/kai/custom/ui/settings/AgentSettings.kt` | Fixed double MemoryList rendering |

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
