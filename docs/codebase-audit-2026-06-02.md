# Codebase Audit Report â€” Kai-custom

**Date:** 2026-06-02  
**Last updated:** 2026-06-02 (v3.6.4)  
**Version at audit:** v3.6.3 (versionCode 150)  
**Current version:** v3.6.4 (versionCode 151) â€” items C2, H1-H3, H4-H6, M1-M2 fixed in v3.6.4  
**Scope:** All Kotlin source in `composeApp/src/`, `androidApp/src/`, Koin DI, tool system, memory system, prompt pipeline, sandbox, heartbeat, wake word.

---

## Fix Status (v3.6.4)

Items resolved in v3.6.4 (commit `47985f64`):

- **C2** â€” `SshConfigureHostTool` schema fixed: all 6 parameters declared
- **H1** â€” `ChatSystemPromptBuilder.kt` deleted (202 lines); types moved to `TaskDomain.kt`; test uses `UnifiedPromptBuilder`
- **H2** â€” `VectorIndex.kt` deleted (51 lines, orphaned)
- **H3** â€” `SyncEngine.kt` deleted (118 lines, orphaned)
- **H4** â€” `SandboxController.searchMemories()` removed from interface (no callers)
- **H5** â€” `DataRepository.requestOpenHeartbeat()` â€” confirmed wired (MainActivityâ†’ChatViewModel), kept
- **H6** â€” `DataRepository.sendTelegramMessage()` removed from interface + both impls
- **M1** â€” `Long.toHumanReadableDate()` extension removed
- **M2** â€” `MarkdownDocument.toPlainText()` + 3 helpers removed

## CRITICAL

### C1. `DimensionStore` unresolvable on non-Android platforms (runtime crash) â€” **unresolved**

**Where:** `SqliteMemoryStore` constructor requires `DimensionStore`, but `SqliteDimensionStore` (the only impl) and `dimensionModule` (its Koin registration) live only in `androidMain`. On iOS/Desktop/WasmJs, only `appModule` is loaded (not `dimensionModule`).

**Impact:** `get<MemoryStore>()` â†’ `get<MemoryStoreProvider>()` â†’ `SqliteMemoryStore` â†’ `get<DimensionStore>()` **throws** on first memory access. Affects `IosKoinHelper.memoryStore`, `WebKoinHelper.memoryStore`, `RemoteDataRepository`, `HeartbeatManager`, `TaskScheduler` â€” all on non-Android targets.

**Files:**  
- `composeApp/src/androidMain/kotlin/com/kai/custom/data/dimension/DimensionModule.kt` (registers `DimensionStore` â€” Android only)  
- `composeApp/src/commonMain/kotlin/com/kai/custom/AppModule.kt:79-87` (expects `DimensionStore` â€” always)  
- `androidApp/src/main/kotlin/com/kai/custom/KaiApplication.kt:23` (loads `dimensionModule` â€” Android only)  
- `composeApp/src/commonMain/kotlin/com/kai/custom/data/dimension/DimensionStore.kt` (interface in common)  
- `composeApp/src/androidMain/kotlin/com/kai/custom/data/dimension/SqliteDimensionStore.kt` (impl in android only)

**Fix:** Provide a non-Android `DimensionStore` impl (e.g. in-memory) or make `SqliteMemoryStore` registration conditional.

---

### C2. `SshConfigureHostTool` schema/implementation param mismatch

**Where:** Tool schema declares only `alias` (required string), but `execute()` reads 6 params: `alias`, `hostname`, `user`, `port`, `identity_file`, `known_host_line`. `hostname` is required â€” LLM will fail because schema doesn't tell it to send `hostname`.

**Impact:** LLM-callable `ssh_configure_host` tool always errors when called by AI (LLM only knows about `alias` from the schema).

**File:** `composeApp/src/androidMain/kotlin/com/kai/custom/tools/SshConfigureHostTool.kt:22-31` (schema) vs `:42-106` (execute)

**Fix:** Update schema to declare all parameters, or make hostname auto-derivable from alias via profiles.

---

## HIGH

### H1. `ChatSystemPromptBuilder.kt` â€” 202 lines of production dead code

**Where:** `buildChatSystemPrompt()` (line 32) plus 7 private helper functions and 3 supporting types/data classes â€” **zero callers in production**. Only called from `ChatSystemPromptBuilderTest.kt`. All actual production calls go directly to `UnifiedPromptBuilder.build()`.

**Dead functions:**
- `appendMemoryCategorySection()` (line 82)
- `appendEmailAccountsSection()` (line 109)
- `appendScheduledTasksSection()` (line 134)
- `appendHeartbeatAdditionsSection()` (line 153)
- `appendContextSection()` (line 167)
- `appendDynamicUiSection()` (line 176)
- `appendInteractiveUiSection()` (line 188)
- Supporting: `ChatPromptRuntimeContext` (line 12), `ChatPromptUiMode` (line 21), `EmailAccountSummary` (line 23), `MEMORY_BUDGET_CHARS` (line 30)

**File:** `F:\Kai\composeApp\src\commonMain\kotlin\com\kai\custom\data\ChatSystemPromptBuilder.kt`

**Recommendation:** Remove entire file and update test to use `UnifiedPromptBuilder` directly.

---

### H2. `VectorIndex.kt` â€” orphaned file (51 lines)

**Where:** `VectorIndex` class implements in-memory embedding search (`upsert`, `remove`, `rebuild`, `search` with cosine similarity). Zero imports or references from any other file. Not wired into production code.

**File:** `composeApp/src/commonMain/kotlin/com/kai/custom/data/dimension/VectorIndex.kt`

**Recommendation:** Safe to delete.

---

### H3. `SyncEngine.kt` â€” orphaned file (118 lines)

**Where:** Full bidirectional sync engine across local and remote (MCP) dimension stores with `SyncSource` interface, push/pull logic, and conflict counting. Zero imports or references from any other file. Not registered in Koin. Not called from any repository.

**Contains (all dead):** `SyncEngine` class, `SyncSource` interface, `DimensionSyncSource`, `McpSyncSource` implementations.

**File:** `composeApp/src/commonMain/kotlin/com/kai/custom/data/dimension/SyncEngine.kt`

**Recommendation:** Safe to delete.

---

### H4. `SandboxController.searchMemories()` â€” never called

**Where:** Defined at `SandboxController.kt:105` with default returning null. Not overridden by `AndroidSandboxController`. Zero callers in production code. Comment says "Semantic search is now handled by alt-memory MCP tools."

**File:** `composeApp/src/commonMain/kotlin/com/kai/custom/SandboxController.kt:105`

**Recommendation:** Remove from interface (breaking change) or deprecate.

---

### H5. `DataRepository.requestOpenHeartbeat()` â€” no callers

**Where:** Declared in interface at `DataRepository.kt:273`, implemented in both `RemoteDataRepository` and `FakeDataRepository`. Never called by any ViewModel, composable, or service. Only referenced in a comment in `HeartbeatNotifier.android.kt:19`.

**File:** `composeApp/src/commonMain/kotlin/com/kai/custom/data/DataRepository.kt:273`

---

### H6. `DataRepository.sendTelegramMessage()` â€” no callers

**Where:** Declared in `DataRepository.kt:314`, implemented in both `RemoteDataRepository.kt:2248` and `FakeDataRepository.kt:755`. Never called from any production code.

**File:** `composeApp/src/commonMain/kotlin/com/kai/custom/data/DataRepository.kt:314`

---

## MEDIUM

### M1. `Long.toHumanReadableDate()` â€” unused extension (1 line + ~9 of format def)

**Where:** `ExtensionFunctions.kt:17`. Zero callers outside its own declaration. All sibling extensions (`toIsoDate`, `formatContextWindow`, `formatReleaseDate`, `formatFileSize`, `smartTruncate`) are used.

**File:** `composeApp/src/commonMain/kotlin/com/kai/custom/ExtensionFunctions.kt:17`

---

### M2. `MarkdownDocument.toPlainText()` â€” unused extension + 3 private helpers (~38 lines)

**Where:** `MarkdownTextRenderer.kt:18`. Zero callers. Its documented purpose ("clipboard copy fallback") was never implemented. Private helpers `blockToPlain`, `itemToPlain`, `tableToPlain` are only called from `toPlainText`.

**File:** `composeApp/src/commonMain/kotlin/com/kai/custom/ui/markdown/MarkdownTextRenderer.kt:18`

---

### M3. `AnsiParser.kt` â€” test-only production code (223 lines)

**Where:** Full ANSI escape code parser that converts terminal-colored text to Compose `AnnotatedString`. Only referenced from `AnsiParserTest.kt`. Zero production callers.

**File:** `composeApp/src/commonMain/kotlin/com/kai/custom/ui/settings/AnsiParser.kt`

**Note:** Could be kept for future terminal output display, but currently dead in production.

---

### M4. Shell quoting code duplication

**Where:** Identical `shellQuote()` function in `SpeakTextTool.kt` and `OpenCodeTool.kt`. `ShellCommandTool` has nearly identical `shellSingleQuote()` helper.

**Files:**
- `composeApp/src/androidMain/kotlin/com/kai/custom/tools/SpeakTextTool.kt`
- `composeApp/src/androidMain/kotlin/com/kai/custom/tools/OpenCodeTool.kt`
- `composeApp/src/androidMain/kotlin/com/kai/custom/tools/ShellCommandTool.kt`

**Recommendation:** Extract to shared utility.

---

### M5. `AnsiParser.kt` â€” dead if not needed for future terminal display

As above.

---

## LOW

### L1. `FakeDataRepository.installSkillFromGitHub/RegistryEntry` throws `UnsupportedOperationException`

**Where:** Lines 718-719. Any test exercising skill installation via `FakeDataRepository` will fail.

**Files:** `composeApp/src/commonTest/kotlin/com/kai/custom/testutil/FakeDataRepository.kt:718-719`

---

### L2. AGENTS.md claims about `FakeDataRepository` are outdated

**Where:** AGENTS.md lines 45-47 claim `queryKgFacts`, `countDimensionEntities`, wake word methods, `getPreferredLanguage`/`setPreferredLanguage` are missing from `FakeDataRepository`. **All are now implemented.** The `installSkillFrom*` methods still throw though â€” that's a new gap not in AGENTS.md.

**File:** `F:\Kai\AGENTS.md`

---

### L3. `CuratedToolRegistry` not wired into production

**Where:** `CuratedToolRegistry.kt` â€” thin wrapper that always registers memory/KG/diary tools. Only used from tests. Production `getAvailableTools()` calls `CommonTools.getMemoryTools()` etc. directly.

**File:** `composeApp/src/commonMain/kotlin/com/kai/custom/tools/CuratedToolRegistry.kt`

---

### L4. MCP tool name deduplication only on Android

**Where:** Android `getAvailableTools()` (line 1281 of Platform.android.kt) calls `allTools.distinctBy { it.schema.name }`, allowing MCP tools to override built-ins. Desktop does NOT have this dedup â€” if an MCP server provides a tool with the same name as a built-in, both appear.

**Files:**
- `composeApp/src/androidMain/kotlin/com/kai/custom/Platform.android.kt:1281` (has dedup)
- `composeApp/src/desktopMain/kotlin/com/kai/custom/Platform.jvm.kt` (no dedup)

---

### L5. `HeartbeatPromptBuilderTest` doesn't test `## Learned Patterns` section

**Where:** Test's `build()` wrapper has no `learnedPatterns` parameter (defaults to empty). The `## Learned Patterns` section is never exercised in tests.

**File:** `composeApp/src/commonTest/kotlin/com/kai/custom/data/HeartbeatPromptBuilderTest.kt`

---

### L6. `search_memories` ToolInfo has no `nameRes`/`descriptionRes` resources

**Where:** Unlike the other 4 memory tool info entries, `searchMemoriesToolInfo` (CommonTools.kt:209-213) does not reference string resources. Display in settings may show raw string key.

**File:** `composeApp/src/commonMain/kotlin/com/kai/custom/tools/CommonTools.kt:209-213`

---

### L7. iOS EventKit unimplemented (two TODOs)

**Where:** `CalendarPermissionController.ios.kt:12,17` â€” `EKEventStore.authorizationStatus` and `requestAccess` calls are TODOs. iOS target not actively tested per AGENTS.md.

**File:** `composeApp/src/iosMain/kotlin/com/kai/custom/tools/CalendarPermissionController.ios.kt`

---

### L8. `chatHistory` is `MutableStateFlow` in impls but `StateFlow` in interface

**Where:** Valid Kotlin (covariant override), but breaks referential transparency if a consumer accidentally casts.

**Files:**
- `composeApp/src/commonMain/kotlin/com/kai/custom/data/DataRepository.kt` (interface)
- `composeApp/src/commonMain/kotlin/com/kai/custom/data/RemoteDataRepository.kt` (impl)
- `composeApp/src/commonTest/kotlin/com/kai/custom/testutil/FakeDataRepository.kt` (impl)

---

## OVERALL HEALTH

| Category | Pass | Fail |
|----------|------|------|
| Koin DI â€” all bindings registered | 43/43 | 0 |
| Koin DI â€” all bindings consumed | 43/43 | 0 |
| DataRepository interface methods implemented | 204/204 (both impls) | 0 |
| FakeDataRepository throws | 2 methods (`installSkill*`) | â€” |
| Each `expect` has platform `actual` | All verified | 0 |
| ToolExecutor â€” all tools registered | All verified | 0 |
| ToolExecutor â€” caching stale | N/A (no cache) | â€” |
| MemoryStore interface methods overridden | 22/22 (3 impls) | 0 |
| Protected memory guards everywhere | 3 layers (store, VM, UI) | 0 |
| Memory toggle gates all paths | 6 points verified | 0 |
| Wake word lifecycle | 3 management points | 0 |
| Heartbeat full chain intact | yes | 0 |
| Dead code (lines) | ~440 (definite) | â€” |
| Orphaned files | 3 (`VectorIndex.kt`, `SyncEngine.kt`, `ChatSystemPromptBuilder.kt`) | â€” |

### Dead Code Summary

| File | Lines | Status |
|------|-------|--------|
| `ChatSystemPromptBuilder.kt` | 202 | **REMOVED in v3.6.4** |
| `SyncEngine.kt` | 118 | **REMOVED in v3.6.4** |
| `VectorIndex.kt` | 51 | **REMOVED in v3.6.4** |
| `AnsiParser.kt` | 223 | Production dead (test only) â€” kept |
| `toHumanReadableDate()` | ~10 | **REMOVED in v3.6.4** |
| `toPlainText()` + helpers | ~38 | **REMOVED in v3.6.4** |
| **Total removed** | **~419** | |
| **Remaining** | **~223** (AnsiParser) | |

### Actions Needed (by priority) â€” Updated for v3.6.4

1. **C1**: Provide non-Android `DimensionStore` impl for iOS/Desktop/WasmJs (avoids deferred crash)
2. **L2**: Update AGENTS.md to reflect current `FakeDataRepository` state (installSkill* methods still throw)
3. **L3**: Wire `CuratedToolRegistry` into production or remove it
4. **L4**: Add MCP tool name dedup to Desktop (like Android)
5. **L5**: Add `learnedPatterns` param to `HeartbeatPromptBuilderTest`
6. **L6**: Add `nameRes`/`descriptionRes` to `search_memories` ToolInfo
