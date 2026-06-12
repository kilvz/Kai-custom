# Alt-Memory Wiring Plan

## Goal
Replace kai-mempalace (in-sandbox FAISS semantic search) with alt-memory running as an embedded MCP server inside the Linux sandbox, exposing 40+ memory management tools to the AI.

## Architecture
```
Sandbox (Alpine Linux proot)
  └─ alt-memory mcp --transport sse --port 8316
       └─ Exposes 40+ MCP tools: search, store, forget, kg_add, diary, etc.

App (Kotlin/Android)
  └─ McpServerManager
       └─ Built-in server: "alt_memory" → http://127.0.0.1:8316
            └─ McpClient → McpTool wrappers → getAvailableTools() → ToolExecutor
  └─ MemoryStore (SQLite — synchronous reads only, fallback)
       └─ getAllMemories(), searchMemories() → synchronous, from SQLite
       └─ store(), forget(), reinforceMemory() → SQLite + fire-and-forget to alt-memory MCP
  └─ AltMemoryLifecycleManager
       └─ pip install alt-memory (once)
       └─ Start MCP server as background process
       └─ Register built-in server when sandbox ready
```

## Status
✅ **All 10 steps complete** (v2.0.11)

## Steps

### ✅ Step 1: `McpServerManager` — built-in server support
- `McpServerManager.kt`: `registerBuiltInServer()`, built-in guards, `getAllServers()`

### ✅ Step 2: `AltMemoryLifecycleManager`
- `AltMemoryLifecycleManager.kt`: pip install, start MCP, health check, register + connect

### ✅ Step 3: `SandboxController.android.kt` — replace kai-mempalace
- Removed `ensureSearchDeps()`, `SEARCH_WRAPPER`, wired alt-memory trigger on sandbox Ready

### ✅ Step 4: Suppress 5 memory tools when alt-memory connected
- All 4 platform `getAvailableTools()`: guard `CommonTools.getMemoryTools()` behind `!isConnected("alt_memory")`

### ✅ Step 5: `AppSettings.kt` — update system prompt
- Removed kai-mempalace CLI references, added alt-memory mention

### ✅ Step 6: Delete old palace backup files
- Deleted `PalaceBackup.kt`, `AndroidPalaceBackup.kt`, removed `PalaceBackupManager` from DI

### ✅ Step 7: Rename palace→dimension throughout
- `data.palace` → `data.dimension`, `Wing`→`Realm`, `Room`→`Domain`, `Drawer`→`EntityData`

### ✅ Step 8: Sandbox distro selection (Alpine / Ubuntu)
- Added `KEY_SANDBOX_DISTRO` setting (default `alpine`)
- Parameterized `RootfsDownloader` URLs, mirrors, and `writeRepositories()` for Alpine vs Ubuntu
- Adapted `installPackages()` for `apk` (Alpine) vs `apt-get` (Ubuntu) with distro-specific package lists
- Added distro selector UI (`ExposedDropdownMenuBox`) in `SandboxSettings.kt`
- Updated `SandboxViewModel` state with `sandboxDistro` + `onDistroChanged`

### ✅ Step 9: SQLite→alt-memory migration path
- Added `KEY_ALT_MEMORY_MIGRATION_COMPLETE` flag to `AppSettings`
- `AltMemoryLifecycleManager.runMigration()`: after alt-memory connects, iterates `MemoryStore.getAllMemories()` and calls `memory_store` MCP tool for each
- Added `getClient(serverId)` to `McpServerManager` to retrieve the active `McpClient`
- Migration runs once; skips if flag set or no memories exist

### ✅ Step 10: Build + deploy
- `.\gradlew.bat :androidApp:assembleFossRelease` — BUILD SUCCESSFUL (4m 14s)
- `v2.0.11` released on GitHub: https://github.com/kilvz/Kai-custom/releases/tag/v2.0.11

## Files Changed
| File | Change |
|------|--------|
| `McpServerManager.kt` | Add built-in server support |
| `AltMemoryLifecycleManager.kt` | NEW — lifecycle management |
| `SandboxController.android.kt` | Remove kai-mempalace, wire alt-memory |
| `Platform.android.kt` | Suppress memory tools when alt-memory connected |
| `Platform.jvm.kt` | Same |
| `Platform.ios.kt` | Same |
| `Platform.wasmJs.kt` | Same |
| `AppSettings.kt` | Update system prompt |
| `ExportFormat.kt` | DELETE |
| `PalaceBackup.kt` | DELETE |
| `AndroidPalaceBackup.kt` | DELETE |
| `PalaceModule.kt` | Remove PalaceBackupManager |
| `MemoryStore.kt` | Remove exportPalace/importPalace |
| `DimensionStore.kt` | NEW — renamed from PalaceStore |
| `SqliteDimensionStore.kt` | NEW — renamed from SqlitePalaceStore |
| `DimensionConfig.kt` | NEW — renamed from PalaceConfig |
| `EntityData.kt` | NEW — renamed from Drawer |
| `DimensionModule.kt` | NEW — renamed from PalaceModule |
| `KaiApplication.kt` | palaceModule→dimensionModule import |
| `AppModule.kt` | Inject DimensionStore instead of PalaceStore |
| `DataRepository.kt` | Add getSandboxDistro/setSandboxDistro |
| `RemoteDataRepository.kt` | Implement distro methods |
| `FakeDataRepository.kt` | Add distro stubs |
| `RootfsDownloader.kt` | Ubuntu constants/URLs, distro parameter |
| `LinuxSandboxManager.kt` | Branch on distro for setup/installPackages |
| `SandboxViewModel.kt` | Add sandboxDistro state + onDistroChanged |
| `SandboxSettings.kt` | Add distro selector dropdown |
| `SettingsScreen.kt` | Thread onDistroChanged through |
| `McpServerManager.kt` | Add getClient() for migration |
| `AltMemoryLifecycleManager.kt` | Add runMigration(), appSettings+memoryStore params |
| `SandboxController.android.kt` | Inject appSettings+memoryStore for AltMemoryLifecycleManager |

## Not Changed
- `MemoryStore` internals (SQLite sync reads stay)
- `ChatSystemPromptBuilder`
- `ToolExecutor`
- `McpClient` / `McpTool`
- `MemoryEntry` / `MemoryCategory`
