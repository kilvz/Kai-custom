# Root Support — Implementation Plan

## Overview
Add root-level shell execution to Kai-custom. Two features:
1. **`run_root` AI tool** — execute arbitrary commands as root via `su -c`
2. **Sandbox root toggle** — run proot itself under `su` for real kernel capabilities

Both are opt-in (default off), with warning labels when enabled.

## New Files

### `composeApp/src/androidMain/kotlin/com/kai/custom/root/RootManager.kt`
- Object `RootManager`
- `isAvailable(): Boolean` — checks for `su` binary via `which su`
- `suspend fun runCommand(command: String, timeoutSeconds: Long = 30): Map<String, Any>`
  - Spawns `su -c 'command'` via `ProcessBuilder`
  - Reads stdout/stderr concurrently, polls for exit with timeout
  - Returns same `Map<String, Any>` schema as `ShizukuManager` (`success`, `exit_code`, `stdout`, `stderr`, `timed_out`, `error`)

### `composeApp/src/androidMain/kotlin/com/kai/custom/tools/RootTool.kt`
- Object `RootTool : Tool`
- Tool name: `"run_root"` (matches `run_adb` naming)
- Parameters: `command: string (required)`, `timeout: integer (optional, default 30)`
- Checks `RootManager.isAvailable()`, calls `RootManager.runCommand()`

## Modified Files

1. **Platform.kt** — add `expect val isRootSupported: Boolean`
2. **Platform.android.kt** — add `actual val isRootSupported: Boolean = true`; register `RootTool` in `getAvailableTools()` behind `appSettings.isToolEnabled("run_root")`
3. **Platform.jvm.kt, Platform.ios.kt, Platform.wasmJs.kt** — stub `actual val isRootSupported: Boolean = false`
4. **AppSettings.kt** — add `isRootEnabled()`/`setRootEnabled()` (default `false`, key `root_enabled`); add `isSandboxRootEnabled()`/`setSandboxRootEnabled()` (default `false`, key `sandbox_root_enabled`)
5. **DataRepository.kt** — add both toggle pairs to interface
6. **RemoteDataRepository.kt** — delegate to `appSettings`
7. **FakeDataRepository.kt** — stub both pairs
8. **SettingsUiState.kt** — add `showRootSection`, `isRootEnabled`, `rootAvailable` fields
9. **SettingsActions.kt** — add `onToggleRoot` action
10. **SettingsViewModel.kt** — implement `onToggleRoot`, compute `showRootSection` from platform support
11. **ToolsSettings.kt** — add `RootSection` composable with warning Surface (red/amber) when enabled
12. **SandboxViewModel.kt** — add `isSandboxRootEnabled` to state + `onToggleSandboxRoot()`
13. **SandboxSettings.kt** — add sandbox root toggle row with warning label
14. **SettingsScreen.kt** — wire new state/actions through both tabs
15. **ProotExecutor.kt** — when sandbox root is enabled + root available, prefix proot command with `su -c`

## Settings UI

### Tools Tab
```
┌─ SettingsCard ──────────────────────────────────┐
│ ● Root Shell                          [toggle]  │
│ Run shell commands with root privileges          │
│                                                  │
│ [when enabled:]                                  │
│ ┌─ ⚠ Warning ──────────────────────────────┐    │
│ │ Root access gives full system control.    │    │
│ │ Misuse can damage your device or void     │    │
│ │ warranty.                                 │    │
│ └───────────────────────────────────────────┘    │
│ ✅ Root available                                │
└──────────────────────────────────────────────────┘
```

### Sandbox Tab (inside SandboxSettingsCard)
```
│ ☐ Run sandbox as root                 [toggle]  │
│ Real root instead of proot-faked root.           │
│ Bypasses sandbox isolation.                      │
│ [when enabled: warning Surface]                  │
```

## Dependencies
- None. `su` is part of any rooted Android system.

## Version
- v3.2.5 (versionCode 141), patch bump
