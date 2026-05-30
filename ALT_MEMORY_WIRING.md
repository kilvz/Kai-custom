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

## Steps

### Step 1: `McpServerManager` — built-in server support
- Add `registerBuiltInServer(id, name, url)` — in-memory only, not persisted to AppSettings
- Merge built-in servers into `getServers()`
- `getEnabledMcpTools()` includes built-in servers
- `removeServer()` / `setServerEnabled()` refuse built-in IDs
- `connectEnabledServers()` auto-connects built-in servers

### Step 2: `AltMemoryLifecycleManager` — new file (commonMain)
- Interface: manages alt-memory lifecycle in sandbox
- `pip install alt-memory` (idempotent)
- Start `alt-memory mcp --transport sse --port 8316` as background process
- Health check: poll until ready (timeout 30s)
- Register + connect built-in MCP server on success
- Log warning on failure (no alt-memory, fallback to SQLite)

### Step 3: `SandboxController.android.kt` — replace kai-mempalace
- Remove `ensureSearchDeps()` — no more kai-mempalace clone
- Remove `SEARCH_WRAPPER` embedded Python script
- Wire `AltMemoryLifecycleManager` into sandbox state listener
- `searchMemories()` → return null (falls through to FTS5 local search)

### Step 4: Suppress 5 memory tools when alt-memory connected
In all 4 platform `getAvailableTools()`:
- When `mcpServerManager.isConnected("alt_memory")` → skip `CommonTools.getMemoryTools()`
- `HeartbeatTools.getPromoteLearningTool()` stays regardless
- When alt-memory not connected → existing behavior (5 memory tools available)

### Step 5: `AppSettings.kt` — update system prompt
- Remove kai-mempalace CLI references from `DEFAULT_MEMORY_INSTRUCTIONS`
- Add alt-memory tool descriptions
- Same for `DEFAULT_LOCAL_MEMORY_INSTRUCTIONS`

### Step 6: Delete old palace backup files
- `ExportFormat.kt`, `PalaceBackup.kt`, `AndroidPalaceBackup.kt`
- Remove `PalaceBackupManager` from DI in `PalaceModule.kt`
- Remove `exportPalace()`/`importPalace()` from `MemoryStore`

### Step 7: Build + deploy
- `.\gradlew.bat :androidApp:assembleFossRelease`
- Deploy to device: `adb install -r ...`

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

## Not Changed
- `MemoryStore` internals (SQLite sync reads stay)
- `PalaceStore` interface / `SqlitePalaceStore`
- `ChatSystemPromptBuilder`
- `ToolExecutor`
- `McpClient` / `McpTool`
- `MemoryEntry` / `MemoryCategory`
- `Drawer` / `PalaceConfig`
