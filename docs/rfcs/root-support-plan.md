# Root Support â€” Implementation Plan

## Overview
Add root-level shell execution to Kai-custom. Two features:
1. **`run_root` AI tool** â€” execute arbitrary commands as root via `su -c`
2. **Sandbox root toggle** â€” run proot itself under `su` for real kernel capabilities

Both are opt-in (default off), with warning labels when enabled.

## New Files

### `composeApp/src/androidMain/kotlin/com/kai/custom/root/RootManager.kt`
- Object `RootManager`
- `isAvailable(): Boolean` â€” checks for `su` binary via `which su`
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

1. **Platform.kt** â€” add `expect val isRootSupported: Boolean`
2. **Platform.android.kt** â€” add `actual val isRootSupported: Boolean = true`; register `RootTool` in `getAvailableTools()` behind `appSettings.isToolEnabled("run_root")`
3. **Platform.jvm.kt, Platform.ios.kt, Platform.wasmJs.kt** â€” stub `actual val isRootSupported: Boolean = false`
4. **AppSettings.kt** â€” add `isRootEnabled()`/`setRootEnabled()` (default `false`, key `root_enabled`); add `isSandboxRootEnabled()`/`setSandboxRootEnabled()` (default `false`, key `sandbox_root_enabled`)
5. **DataRepository.kt** â€” add both toggle pairs to interface
6. **RemoteDataRepository.kt** â€” delegate to `appSettings`
7. **FakeDataRepository.kt** â€” stub both pairs
8. **SettingsUiState.kt** â€” add `showRootSection`, `isRootEnabled`, `rootAvailable` fields
9. **SettingsActions.kt** â€” add `onToggleRoot` action
10. **SettingsViewModel.kt** â€” implement `onToggleRoot`, compute `showRootSection` from platform support
11. **ToolsSettings.kt** â€” add `RootSection` composable with warning Surface (red/amber) when enabled
12. **SandboxViewModel.kt** â€” add `isSandboxRootEnabled` to state + `onToggleSandboxRoot()`
13. **SandboxSettings.kt** â€” add sandbox root toggle row with warning label
14. **SettingsScreen.kt** â€” wire new state/actions through both tabs
15. **ProotExecutor.kt** â€” when sandbox root is enabled + root available, prefix proot command with `su -c`

## Settings UI

### Tools Tab
```
â”Œâ”€ SettingsCard â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚ â— Root Shell                          [toggle]  â”‚
â”‚ Run shell commands with root privileges          â”‚
â”‚                                                  â”‚
â”‚ [when enabled:]                                  â”‚
â”‚ â”Œâ”€ âš  Warning â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”    â”‚
â”‚ â”‚ Root access gives full system control.    â”‚    â”‚
â”‚ â”‚ Misuse can damage your device or void     â”‚    â”‚
â”‚ â”‚ warranty.                                 â”‚    â”‚
â”‚ â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜    â”‚
â”‚ âœ… Root available                                â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
```

### Sandbox Tab (inside SandboxSettingsCard)
```
â”‚ â˜ Run sandbox as root                 [toggle]  â”‚
â”‚ Real root instead of proot-faked root.           â”‚
â”‚ Bypasses sandbox isolation.                      â”‚
â”‚ [when enabled: warning Surface]                  â”‚
```

## Dependencies
- None. `su` is part of any rooted Android system.

## Version
- v3.2.5 (versionCode 141), patch bump
