# Upstream-to-Fork Comprehensive Map

Reference document for navigating between **SimonSchubert/Kai** (upstream) and **kilvz/Kai-custom** (our fork).

## Overview

| Item | Upstream | Fork |
|------|----------|------|
| Repository | `github.com/SimonSchubert/Kai` | `github.com/kilvz/Kai-custom` |
| Fork point commit | `45e001b` | `2b7d400` (our initial commit) |
| Upstream version at fork | `v2.6.3` (code 107) | — |
| Our current version | — | `v2.1.0` (code 132) on `main`; `v3.1.2` (code 134) on `beta` |
| Package | `com.inspiredandroid.kai` | `com.kai.custom` |
| Git tag for fork point | `v2.6.3` | `2b7d400` |
| Upstream HEAD (latest) | ~`bc7d622` (v2.7.0) | — |

### Version Mapping

Upstream tags → Fork equivalents (both branches diverge after fork point).

| Upstream Tag | Upstream Version | Fork `main` | Fork `beta` |
|---|---|---|---|
| `v2.6.0` | 2.6.0 (code 105) | — | — |
| `v2.6.1` | 2.6.1 (code 106) | — | — |
| — | — | 2.0.0–2.1.0 (code 124–132) | 3.0.0–3.1.2 (code 128–134) |

**Baseline**: upstream `v2.6.3` / fork `v2.0.0` (initial fork; both at same upstream source, just package-renamed).

---

## Source Set Mapping

### commonMain

Every upstream `com.inspiredandroid.kai.*` file maps to `com.kai.custom.*` by package rename.
The following table shows only files that differ in behavior, plus fork-only additions.
Files not listed are straight renames with identical logic.

#### Shared files (package renamed, functionally equivalent)

| Upstream (com.inspiredandroid.kai) | Fork (com.kai.custom) | Notes |
|---|---|---|
| `Platform.kt` | `Platform.kt` | Renamed package |
| `SandboxController.kt` | `SandboxController.kt` | Renamed |
| `DaemonController.kt` | `DaemonController.kt` | Renamed |
| `ExtensionFunctions.kt` | `ExtensionFunctions.kt` | Renamed |
| `TerminalLine.kt` | `TerminalLine.kt` | Renamed |
| `BuildKonfig.kt` | `BuildKonfig.kt` | Fork-specific, see below |
| `App.kt` | `App.kt` | **Modified** — +SandboxAwareUriHandler |
| `AppModule.kt` | `AppModule.kt` | **Modified** — +SkillManager, AutoMemoryLearner, HeartbeatMemoryExtractor, DimensionStore |
| `data/AppSettings.kt` | `data/AppSettings.kt` | **Modified** — soul split (soul_user+soul_auto), persona system, memory toggle |
| `data/AppSettingsImportExport.kt` | `data/AppSettingsImportExport.kt` | **Modified** — exports only soul_user |
| `data/AppSettingsMigrations.kt` | `data/AppSettingsMigrations.kt` | Renamed |
| `data/AppSettingsService.kt` | `data/AppSettingsService.kt` | Renamed |
| `data/ChatSystemPromptBuilder.kt` | `data/ChatSystemPromptBuilder.kt` | **Modified** — trimmed (removed 6 sections), memory budget cap 1024 chars |
| `data/DataRepository.kt` | `data/DataRepository.kt` | **Modified** — +skill methods, +schemaResetMessage, +protected memory, +soul split, +persona, +getRecentExchanges |
| `data/MemoryStore.kt` | `data/MemoryStore.kt` | **Modified** — +protected field, +storeProtected/getUserMemories/getBehaviorMemories, +schemaResetMessage |
| `data/RemoteDataRepository.kt` | `data/RemoteDataRepository.kt` | **Modified** — +skill wiring, soul split, AutoMemoryLearner, persona, getRecentExchanges |
| `data/ToolExecutor.kt` | `data/ToolExecutor.kt` | **Modified** — +caching (cachedTools/cachedToolDefs), +invalidateCache |
| `data/TaskScheduler.kt` | `data/TaskScheduler.kt` | **Modified** — +HeartbeatMemoryExtractor, +memoryStore param |
| `data/HeartbeatManager.kt` | `data/HeartbeatManager.kt` | **Modified** — +learnedPatterns, builds with behavior memories |
| `data/HeartbeatPromptBuilder.kt` | `data/HeartbeatPromptBuilder.kt` | **Modified** — +Learned Patterns section |
| `data/ModelCatalog.kt` | `data/ModelCatalog.kt` | **Modified** — +hunyuan-hy3-preview, updated Elo scores 2026-05-31 |
| `data/ConversationStorage.kt` | `data/ConversationStorage.kt` | Renamed |
| `data/*` (all other data files) | `data/*` | Renamed package only |
| `email/*` | `email/*` | Renamed package only |
| `inference/*` | `inference/*` | Renamed package only |
| `mcp/*` | `mcp/*` | Renamed package only |
| `network/*` | `network/*` | Renamed package only |
| `notifications/*` | `notifications/*` | Renamed package only |
| `sms/*` | `sms/*` | Renamed package only |
| `splinterlands/*` | `splinterlands/*` | Renamed package only |
| `tools/*` | `tools/*` | Renamed package only |
| `ui/Theme.kt` | `ui/Theme.kt` | Renamed |
| `ui/chat/ChatActions.kt` | `ui/chat/ChatActions.kt` | **Modified** — +skill actions |
| `ui/chat/ChatUiState.kt` | `ui/chat/ChatUiState.kt` | **Modified** — +installedSkills, +schemaResetMessage |
| `ui/chat/ChatViewModel.kt` | `ui/chat/ChatViewModel.kt` | **Modified** — +skill invocation, +formatException extracted to Util.kt |
| `ui/chat/ChatScreen.kt` | `ui/chat/ChatScreen.kt` | **Modified** — +installedSkills prop to QuestionInput |
| `ui/chat/composables/QuestionInput.kt` | `ui/chat/composables/QuestionInput.kt` | **Modified** — +slash autocomplete, +detectSlashQuery |
| `ui/settings/SettingsUiState.kt` | `ui/settings/SettingsUiState.kt` | **Modified** — +6 skill fields, PendingDeletion.Skill, +schemaResetMessage |
| `ui/settings/SettingsViewModel.kt` | `ui/settings/SettingsViewModel.kt` | **Modified** — +skill management, +schemaResetMessage |
| `ui/settings/SettingsScreen.kt` | `ui/settings/SettingsScreen.kt` | **Modified** — +SkillsSection, +schemaResetMessage AlertDialog |
| `ui/settings/SettingsActions.kt` | `ui/settings/SettingsActions.kt` | **Modified** — +4 skill actions |
| `ui/settings/ToolsSettings.kt` | `ui/settings/ToolsSettings.kt` | **Modified** — +SkillsSection |
| `ui/settings/*` (all other settings) | `ui/settings/*` | Renamed package only |
| `ui/chat/composables/*` | `ui/chat/composables/*` | Renamed package only |
| `ui/components/*` | `ui/components/*` | Renamed package only |
| `ui/dynamicui/*` | `ui/dynamicui/*` | Renamed package only |
| `ui/icons/*` | `ui/icons/*` | Renamed package only |
| `ui/markdown/*` | `ui/markdown/*` | Renamed package only |
| `ui/sandbox/*` | `ui/sandbox/*` | Renamed package only |

#### Fork-only files in commonMain

| Fork file | Purpose | Added |
|---|---|---|
| `data/dimension/DimensionConfig.kt` | Dimension store configuration | Initial fork |
| `data/dimension/DimensionStore.kt` | Local dimension DB interface | Initial fork |
| `data/dimension/EntityData.kt` | Dimension entity model | Initial fork |
| `data/dimension/ExportFormat.kt` | Dimension export model | Initial fork |
| `data/MemoryStoreProvider.kt` | Delegates to SQLite or alt-memory backend | Initial fork |
| `data/AltMemoryClient.kt` | MCP-based memory client | Initial fork |
| `data/SqliteMemoryStore.kt` | SQLite-backed memory store (common) | Initial fork |
| `data/Language.kt` | Language model | Initial fork |
| `mcp/AltMemoryLifecycleManager.kt` | MCP lifecycle for alt-memory server | Initial fork |
| `tools/MicrophonePermissionController.kt` | Mic permission abstract | Initial fork |
| `tools/PhoneTools.kt` | Phone call tools | Initial fork |
| `ui/chat/EditMessageDialog.kt` | Edit sent messages | Initial fork |
| `ui/settings/GeneralSettings.kt` | General settings (not in upstream) | Initial fork |
| `ui/settings/SshSettings.kt` | SSH settings UI | Initial fork |
| `ui/settings/SshTerminalContent.kt` | SSH terminal UI component | Initial fork |
| `ui/settings/SshViewModel.kt` | SSH view model | Initial fork |
| `ui/chat/composables/TopBar.kt` | Chat top bar (separated from ChatScreen) | Initial fork |
| `wakeword/WakeWordController.kt` | Wake word controller interface | Initial fork |
| `wakeword/WakeWordMatcher.kt` | Wake word matching logic | Initial fork |
| `data/AltMemoryClient.kt` | Alt-memory MCP integration | Initial fork |
| `skills/SkillManifest.kt` | Skills system (ported from upstream post-fork) | Phase A |
| `skills/SkillFrontmatterParser.kt` | Skills system | Phase A |
| `skills/SkillMarketplaces.kt` | Skills system | Phase A |
| `skills/SkillRegistry.kt` | Skills system | Phase A |
| `skills/SkillManager.kt` | Skills system | Phase A |
| `ui/SandboxUriHandler.kt` | Sandbox URI handler | Phase A |
| `ui/chat/composables/SkillAutocomplete.kt` | Skills autocomplete UI | Phase A |
| `ui/settings/SkillsSection.kt` | Settings section for skills | Phase A |

---

### androidMain

#### Upstream-mapped files (package renamed)

| Upstream | Fork | Notes |
|---|---|---|
| `BuildKonfig.kt` | `BuildKonfig.kt` | Fork-specific values |
| `DaemonController.android.kt` | `DaemonController.android.kt` | Renamed |
| `DaemonService.kt` | `DaemonService.kt` | Renamed |
| `HeartbeatNotifier.android.kt` | `HeartbeatNotifier.android.kt` | Renamed |
| `Platform.android.kt` | `Platform.android.kt` | **Modified** — +memory toggle gating, +wake word support |
| `SandboxController.android.kt` | `SandboxController.android.kt` | Renamed |
| `data/ConversationStorage.android.kt` | `data/ConversationStorage.android.kt` | Renamed |
| `email/EmailConnection.android.kt` | `email/EmailConnection.android.kt` | Renamed |
| `inference/InferencePlatform.android.kt` | `inference/InferencePlatform.android.kt` | Renamed |
| `inference/LocalInferenceEngineProvider.android.kt` | `inference/LocalInferenceEngineProvider.android.kt` | Renamed |
| `inference/ModelDownloadService.kt` | `inference/ModelDownloadService.kt` | Renamed |
| `notifications/KaiNotificationListenerService.kt` | `notifications/KaiNotificationListenerService.kt` | Renamed |
| `notifications/NotificationReader.android.kt` | `notifications/NotificationReader.android.kt` | Renamed |
| `sandbox/LinuxSandboxManager.kt` | `sandbox/LinuxSandboxManager.kt` | Renamed |
| `sandbox/PersistentSandboxShell.kt` | `sandbox/PersistentSandboxShell.kt` | Renamed |
| `sandbox/ProotExecutor.kt` | `sandbox/ProotExecutor.kt` | Renamed |
| `sandbox/RootfsDownloader.kt` | `sandbox/RootfsDownloader.kt` | Renamed |
| `sandbox/SandboxFiles.kt` | `sandbox/SandboxFiles.kt` | Renamed |
| `sandbox/SandboxModule.kt` | `sandbox/SandboxModule.kt` | Renamed |
| `sandbox/SandboxState.kt` | `sandbox/SandboxState.kt` | Renamed |
| `sandbox/SessionShell.kt` | `sandbox/SessionShell.kt` | Renamed |
| `sms/SmsReader.android.kt` | `sms/SmsReader.android.kt` | Renamed |
| `sms/SmsSender.android.kt` | `sms/SmsSender.android.kt` | Renamed |
| `splinterlands/HiveCrypto.android.kt` | `splinterlands/HiveCrypto.android.kt` | Renamed |
| `tools/CalendarPermissionController.android.kt` | `tools/CalendarPermissionController.android.kt` | Renamed |
| `tools/CalendarRepository.kt` | `tools/CalendarRepository.kt` | Renamed |
| `tools/NotificationHelper.kt` | `tools/NotificationHelper.kt` | Renamed |
| `tools/NotificationListenerController.android.kt` | `tools/NotificationListenerController.android.kt` | Renamed |
| `tools/NotificationPermissionController.android.kt` | `tools/NotificationPermissionController.android.kt` | Renamed |
| `tools/OpenFileTool.kt` | `tools/OpenFileTool.kt` | Renamed |
| `tools/ProcessManager.kt` | `tools/ProcessManager.kt` | Renamed |
| `tools/ProcessManagerTool.kt` | `tools/ProcessManagerTool.kt` | Renamed |
| `tools/ShellCommandTool.kt` | `tools/ShellCommandTool.kt` | Renamed |
| `tools/SmsPermissionController.android.kt` | `tools/SmsPermissionController.android.kt` | Renamed |
| `tools/SmsSendPermissionController.android.kt` | `tools/SmsSendPermissionController.android.kt` | Renamed |
| `tools/SshConfigureHostTool.kt` | `tools/SshConfigureHostTool.kt` | Renamed |
| `ui/components/DesktopScrollbar.android.kt` | `ui/components/DesktopScrollbar.android.kt` | Renamed |

#### Fork-only files in androidMain

| Fork file | Purpose | Added |
|---|---|---|
| `data/dimension/DimensionModule.kt` | Android dimension DB module | Initial fork |
| `data/dimension/SqliteDimensionStore.kt` | Android SQLite dimension store impl | Initial fork (modified with onDowngrade) |
| `SpeechToText.android.kt` | Speech-to-text (Android) | Initial fork |
| `SshConnectionManager.android.kt` | SSH connection (Android) | Initial fork |
| `tools/AdbTool.kt` | ADB tool for connected devices | Initial fork |
| `tools/OpenCodeTool.kt` | OpenCode editor integration | Initial fork |
| `tools/SpeakTextTool.kt` | TTS speak tool | Initial fork |
| `tools/SshCommandTool.kt` | SSH command execution tool | Initial fork |
| `tools/SshConnectTool.kt` | SSH connect tool | Initial fork |
| `tools/SshDisconnectTool.kt` | SSH disconnect tool | Initial fork |
| `tools/MicrophonePermissionController.android.kt` | Mic permission (Android) | Initial fork |
| `shizuku/ShizukuManager.kt` | Shizuku privilege escalation | Initial fork |
| `shizuku/CommandService.kt` | Shizuku command service | Initial fork |
| `shizuku/ICommandService.kt` | Shizuku AIDL interface | Initial fork |
| `shizuku/CommandResultDto.kt` | Shizuku command result DTO | Initial fork |
| `wakeword/MfccProcessor.kt` | Wake word MFCC | Initial fork |
| `wakeword/WakeWordController.android.kt` | Wake word (Android impl) | Initial fork |
| `wakeword/WakeWordInterpreter.kt` | Wake word TFLite interpreter | Initial fork |
| `wakeword/WakeWordService.kt` | Wake word foreground service | Initial fork |

---

### iosMain, desktopMain, wasmJsMain

Same package-rename mapping applies. Fork added these platform files:

| Source Set | Fork-only files |
|---|---|
| **desktopMain** | `SpeechToText.jvm.kt`, `SshConnectionManager.jvm.kt`, `tools/MicrophonePermissionController.jvm.kt`, `wakeword/WakeWordController.jvm.kt`, `tools/ProcessManager.kt`, `tools/ProcessManagerTool.kt`, `tools/ShellCommandTool.kt` |
| **iosMain** | `SpeechToText.ios.kt`, `tools/MicrophonePermissionController.ios.kt`, `wakeword/WakeWordController.ios.kt` |
| **wasmJsMain** | `SpeechToText.wasmJs.kt`, `tools/MicrophonePermissionController.wasmJs.kt`, `wakeword/WakeWordController.wasmJs.kt` |

---

### commonTest

#### Upstream-mapped test files (package renamed)

All upstream test files from `com/inspiredandroid/kai/` map to `com/kai/custom/` with package rename. Differences:

| Upstream file | Fork file | Notes |
|---|---|---|
| `testutil/FakeDataRepository.kt` | `testutil/FakeDataRepository.kt` | **Modified** — +skill stubs, +protected memory stubs, +soul split stubs, +persona stubs, +schemaResetMessage |
| All other test files | All other test files | Renamed package only |

#### Fork-only test files

| Fork file | Purpose |
|---|---|
| `skills/GitHubSkillUrlTest.kt` | Skills system test |
| `skills/SkillFrontmatterParserTest.kt` | Skills system test |
| `skills/SkillManagerTest.kt` | Skills system test |
| `skills/SkillMarketplaceManifestTest.kt` | Skills system test |
| `testutil/FakeSandboxController.kt` | Skills system test helper |
| `ui/SandboxUriHandlerTest.kt` | Skills system test |

#### Upstream test files not ported

| Upstream file | Fork status |
|---|---|
| `ui/chat/ChatViewModelSkillTest.kt` | Skipped — architecture mismatch |
| `ui/chat/DetectSlashQueryTest.kt` | Skipped — function inlined |
| `ui/chat/ToGroqMessageDtoImageTest.kt` | Not in fork (not needed) |

---

### desktopTest & jvmShared

Package-rename mapping applies. All files are functionally equivalent.

---

### androidApp

| File | Upstream? | Fork notes |
|---|---|---|
| `KaiApplication.kt` | Package renamed | Renamed |
| `MainActivity.kt` | Package renamed | Renamed |
| `ReviewHelper.kt` (foss) | Package renamed | Renamed |
| `ReviewHelper.kt` (playStore) | Package renamed | Renamed |

---

## Non-Kotlin Files Map

### Compose Resources (composeResources/)

| Path | Upstream? | Fork notes |
|---|---|---|
| `values/strings.xml` | Yes | **Modified** — +22 skill strings, +snackbar_skill_removed |
| `values-*/strings.xml` (54 locale files) | Yes | **Modified** — +snackbar_skill_removed |
| `files/skills/create-skill/SKILL.md` | Yes (post-fork) | Ported in Phase A |

### Gradle Build Files

| File | Upstream? | Fork notes |
|---|---|---|
| `gradle/libs.versions.toml` | Diverged | Fork: `appVersion=2.1.0`, `android-versionCode=132` (main); `3.1.2`/`134` (beta) |
| `VERSION` | Fork-only | `v2.1.0` (main), `v3.1.2` (beta) |

### Resource Files — Fork-Only

| Path | Purpose |
|---|---|
| `composeResources/files/wakeword/hey_kai.tflite` | Wake word model |
| `composeResources/drawable/*` | Fork-specific icons |

---

## Architecture Divergences

| Aspect | Upstream | Fork |
|---|---|---|
| **Package** | `com.inspiredandroid.kai` | `com.kai.custom` |
| **Memory** | SQLite + simple memory | Two-tier (protected/unprotected), alt-memory MCP server, dimension store |
| **Soul** | Single `soul_text` | Split `soul_user` + `soul_auto`, persona system |
| **Wake word** | None | Full wake word (Hey Kai) with TFLite model |
| **SSH** | None | SSH connection manager, terminal, tools |
| **Shizuku** | None | Shizuku privilege escalation for adb/shell |
| **Sandbox** | Alpine only | Alpine + Ubuntu distro selection |
| **Tools — unique** | Basic set | +ADB, SSH, Shizuku, TTS, OpenCode integration, phone tools, process management |
| **ChatSystemPrompt** | Full sections | Trimmed (removed 6 sections), 1KB memory cap |
| **Active skill** | Via `ChatSystemPromptBuilder` | Via `History.SYSTEM` message in `ask()` |
| **Wiring** | Monolithic DI | Koin-based DI, DimensionStore via Koin |
| **ToolExecutor** | No caching | Cached tools/tool defs with `invalidateCache()` |
| **DB downgrade** | Crashes | `onDowngrade` resets schema, shows dialog |
| **Version scheme** | Follows upstream | Main: v2.x.x, Beta: v3.x.x (own versioning) |

---

## Files Upstream Added Post-Fork (Not Yet Ported)

These upstream commits after `45e001b` added files we haven't brought in:

| Commit | File | Status |
|---|---|---|
| `04c5407` | Skills system (9 files + tests) | **Ported** in Phase A |
| `79e1a30` | `docs/features/skills.md` | **Created** |
| `79e1a30` | `docs/features/system-prompts.md` update | Not ported |

All other post-fork upstream commits are merges, formatting, version bumps, or packaging.

---

## Navigation Quick Reference

To find the fork equivalent of an upstream file:

```
Upstream: composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/AppSettings.kt
Fork:     composeApp/src/commonMain/kotlin/com/kai/custom/data/AppSettings.kt
```

To find the upstream equivalent of a fork file:

```
Fork:     composeApp/src/commonMain/kotlin/com/kai/custom/data/SqliteMemoryStore.kt
Upstream: (no equivalent — fork-only)
```

## Diff Tool Command

```powershell
# Compare a file between fork point and current HEAD
git diff 45e001b..HEAD -- composeApp/src/commonMain/kotlin/com/kai/custom/data/DataRepository.kt

# List all files changed since fork point
git log 45e001b..HEAD --oneline --stat
```
