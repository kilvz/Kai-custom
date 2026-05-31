# Architecture Improvement Plan — Kai-custom

## Scope
This plan identifies architectural debt across the codebase and proposes phased
improvements. Each phase is ordered by **impact ÷ effort** — highest-return,
lowest-risk items first.

---

## Phase 1 — Quick Wins (low effort, high impact)

### 1.1 ToolExecutor caching
**Problem:** `ToolExecutor.executeTool()` calls `getAvailableTools()` (which
injects Koin deps and builds all tool objects) on *every* LLM tool execution.
**Fix:** Cache the tool list and rebuild only when settings change.
**Effort:** ~30 min
**Files:** `ToolExecutor.kt`, `Platform.android.kt`

### 1.2 Builder DTOs → domain models
**Problem:** `HeartbeatPromptBuilder` defines parallel DTOs
(`HeartbeatPendingEmail`, `HeartbeatPendingSms`, etc.) that are 1:1 copies of
domain models (`EmailMessage`, `SmsMessage`, `NotificationRecord`).
**Fix:** Accept domain models directly; remove the DTOs.
**Effort:** ~1 hr
**Files:** `HeartbeatPromptBuilder.kt`, `HeartbeatManager.kt`, `TaskScheduler.kt`

### 1.3 Static utility → top-level function
**Problem:** `TaskScheduler.formatException()` is a pure static function
defined as an instance method on a stateful class.
**Fix:** Move to a top-level private function.
**Effort:** ~15 min
**Files:** `TaskScheduler.kt`

### 1.4 Unused import cleanup
**Problem:** Several files have dead imports (e.g., `jsonArray`, `jsonObject`,
`jsonPrimitive` in `AutoMemoryLearner`, `HeartbeatMemoryExtractor`).
**Fix:** Remove unused imports project-wide.
**Effort:** ~30 min
**Files:** Multiple

### 1.5 Inline plugin extraction
**Problem:** `VersionGeneratorPlugin` is defined inline in `build.gradle.kts`
(lines 228-269) — untestable, non-shareable, fragile `afterEvaluate`.
**Fix:** Extract to `buildSrc/src/main/kotlin/`. Keep the version-number
generation logic intact; move the iOS xcconfig update side-effect into a
separate task registered conditionally.
**Effort:** ~2 hr
**Files:** `composeApp/build.gradle.kts` → `buildSrc/src/main/kotlin/VersionGeneratorPlugin.kt`

### 1.6 `androidx-lifecycle-process` hardcoded version
**Problem:** `lifecycle-process` uses `2.10.0` as a literal string instead of
the version reference `libs.androidx-lifecycle`.
**Fix:** Change to `libs.androidx-lifecycle`.
**Effort:** ~5 min
**Files:** `gradle/libs.versions.toml` (line 57)

---

## Phase 2 — Decomposition (medium effort, high impact)

### 2.1 Split `AppSettings` companion keys into namespaced objects
**Problem:** 50+ `KEY_*` constants in a flat companion object. SSH credentials,
email passwords, and UI theme preferences share the same namespace.
No access control — any caller can read `KEY_SSH_PRIVATE_KEY`.
**Fix:** Group keys into nested objects:
```kotlin
companion object {
    object Ssh { const val HOST = "ssh_host"; const val PORT = "ssh_port" }
    object Email { const val ACCOUNTS = "email_accounts" }
    object Ui { const val THEME = "theme_mode"; const val SCALE = "ui_scale" }
}
```
**Effort:** ~4 hr
**Files:** `AppSettings.kt`

### 2.2 Split `SettingsViewModel` by domain
**Problem:** 1059-line god ViewModel handling services, heartbeat, email, SMS,
notifications, sandbox, local inference, MCP, wake word, theme, export/import.
**Fix:** Extract into focused sub-ViewModels coordinated by the parent
SettingsScreen. Each sub-screen gets its own ViewModel that exposes its own
`StateFlow`; the parent composes them.
**Candidates:**
- `ServicesSettingsViewModel`
- `HeartbeatSettingsViewModel`
- `IntegrationsSettingsViewModel` (email, SMS, notifications)
- `LocalInferenceSettingsViewModel`
- `McpSettingsViewModel`
- `ExportImportViewModel`
**Effort:** ~12 hr (largest single refactor)
**Files:** All 18 files in `ui/settings/`

### 2.3 Split `SettingsUiState` into domain-specific state classes
**Problem:** Single data class with 50+ fields. Every update rebuilds the
entire object via `.copy()`. New toggles bloat the class further.
**Fix:** Extract per-domain state classes (matching ViewModel split above);
compose them via `combine` in a top-level state holder, or let each sub-screen
observe its own domain state.
**Effort:** ~6 hr (paired with 2.2)
**Files:** `SettingsUiState.kt`, per-domain state files

### 2.4 Split `TaskScheduler` responsibilities
**Problem:** 11 constructor params (all nullable except `DataRepository`).
Manages heartbeat, email polling, SMS polling, notification processing.
**Fix:** Extract each pollable concern into a `Poller` interface:
```kotlin
interface Poller {
    suspend fun poll(): List<PollResult>
    suspend fun process(result: PollResult)
}
```
`TaskScheduler` becomes a loop that iterates over registered `Poller`
instances. EmailPoller, SmsPoller, NotificationPoller each implement the
interface independently.
**Effort:** ~8 hr
**Files:** `TaskScheduler.kt`, new `Poller.kt`, refactored pollers

### 2.5 Split `Requests.kt` into per-provider services
**Problem:** 636-line god class handling Gemini, Anthropic, and
OpenAI-compatible in one file. Triplicated PropertySchema DTOs and conversion
functions across three DTO packages.
**Fix:** One service class per provider (`GeminiService`, `AnthropicService`,
`OpenAICompatibleService`). Extract a shared `ToolSchemaConverter` for the
JSON-to-PropertySchema mapping.
**Effort:** ~6 hr
**Files:** `Requests.kt`, `**ChatRequestDto.kt` (3 files), new service files

### 2.6 Split `HeartbeatPromptBuilder` parameter list
**Problem:** `buildHeartbeatPrompt()` has 10 params (8 list params).
**Fix:** Introduce `HeartbeatPromptData` aggregate data class:
```kotlin
data class HeartbeatPromptData(
    val customOrDefaultPrompt: String,
    val heartbeatAdditions: List<ScheduledTask>,
    val recentResponses: List<String>,
    val pendingTasks: List<ScheduledTask>,
    val emailAccounts: List<EmailAccountSummary>,
    val pendingEmails: List<EmailMessage>,       // domain model directly
    val pendingSms: List<SmsMessage>,            // domain model directly
    val pendingNotifications: List<NotificationRecord>,
    val promotionCandidates: List<MemoryEntry>,
)
```
**Effort:** ~3 hr
**Files:** `HeartbeatPromptBuilder.kt`, `HeartbeatManager.kt`

---

## Phase 3 — Deep Architecture (high effort, high impact)

### 3.1 Split `DataRepository` into focused interfaces
**Problem:** 80+ method god interface. Forces every `FakeDataRepository` to
implement 80+ stubs. Every new feature touches this file.
**Fix:** Split into:
- `ChatService` — ask/askSilently/askWithTools, service management
- `ConversationRepository` — history, CRUD, branching
- `SettingsRepository` — service config, soul, theme, wake word, sandbox,
  daemon, interactive mode, Shizuku, language
- `MemoryRepository` — memory enable/disable, memory CRUD
- `SchedulingRepository` — scheduling, heartbeat config/execution
- `ToolRepository` — tool definitions, MCP server list
- `NotificationRepository` — email, SMS, push notifications
- `LocalInferenceRepository` — local model lifecycle

**Effort:** ~16 hr (biggest single refactor, breaks many imports)
**Files:** `DataRepository.kt`, `RemoteDataRepository.kt`,
`FakeDataRepository.kt`, all ViewModels, all tests

### 3.2 Unify tool metadata (ToolInfo + ToolSchema)
**Problem:** Every tool defines `name`/`description` twice — once in `ToolInfo`
(for UI), once in `ToolSchema` (for LLM). No compile-time check that they match.
**Fix:** Derive `ToolSchema` from `ToolInfo` (or make `Tool` require a
`ToolInfo` reference). Remove the duplicated strings.
```kotlin
abstract class BaseTool(open val info: ToolInfo) : Tool {
    final override val schema: ToolSchema
        get() = ToolSchema(info.id, info.description, buildParameters())
    protected abstract fun buildParameters(): Map<String, ParameterSchema>
}
```
**Effort:** ~6 hr
**Files:** `Tool.kt`, `ToolInfo.kt`, all 16 tool files under `tools/`,
`Platform.android.kt` tool definitions

### 3.3 Standardize MemoryStore interface (all suspend or all blocking)
**Problem:** Mixed `suspend`/blocking signatures. `AltMemoryClient` uses
`runBlocking` to bridge them (ANR risk on Android main thread).
**Fix:** Make all MemoryStore methods `suspend`. Remove `runBlocking` from
`AltMemoryClient`. Update all consumers.
**Effort:** ~4 hr
**Files:** `MemoryStore.kt`, `SqliteMemoryStore.kt`, `AltMemoryClient.kt`,
`MemoryStoreProvider.kt`, all callers

### 3.4 Consolidate exception hierarchies
**Problem:** Three parallel sealed hierarchies (`GeminiApiException`,
`AnthropicApiException`, `OpenAICompatibleApiException`) with identical
structure mapping to the same UI strings.
**Fix:** Single `ApiException` sealed class with a `provider` enum:
```kotlin
sealed class ApiException(provider: ApiProvider, override val message: String) : Exception(message) {
    class InvalidKey(provider: ApiProvider) : ApiException(provider, "Invalid API key")
    class RateLimited(provider: ApiProvider, retryAfter: Duration? = null) : ...
    class Overloaded(provider: ApiProvider) : ...
    class InsufficientCredits(provider: ApiProvider) : ...
    class Generic(provider: ApiProvider, cause: Throwable) : ...
}
```
**Effort:** ~4 hr
**Files:** `NetworkExceptions.kt`, `Requests.kt`, all error handlers

### 3.5 Move inline Platform tools to named objects
**Problem:** Tools defined as anonymous `object : Tool` in `Platform.android.kt`
(like `send_notification`, `create_calendar_event`) cannot be unit-tested.
**Fix:** Extract each to a named object in the `tools/` package.
**Effort:** ~3 hr
**Files:** `Platform.android.kt`, new tool files

---

## Phase 4 — Polish (low effort, low impact)

### 4.1 ChatUiState — extract DTO conversions
Move `toGroqMessageDto`, `toAnthropicContentBlocks`, `toGeminiMessageDto` from
`ChatUiState.kt` into a separate mapper file.
**Effort:** ~1 hr

### 4.2 SplinterlandsUiState — align with MVI pattern
Split into separate state + actions, matching Settings pattern.
**Effort:** ~30 min

### 4.3 NoOp `SettingsActions` — lazy instead of eager
Make the `NoOp` companion lazy, or derive defaults via Kotlin default params.
**Effort:** ~15 min

### 4.4 Platform target dead code cleanup
Remove or document the iOS/desktop/WasmJS build targets that aren't tested.
**Effort:** ~2 hr (risk of breaking contributor builds)

---

## Effort Summary

| Phase | Total Effort | Risk | Value |
|-------|-------------|------|-------|
| **1 — Quick Wins** | ~4 hr | Very low | Immediate DX improvement |
| **2 — Decomposition** | ~39 hr | Medium | Major maintainability gain |
| **3 — Deep Architecture** | ~33 hr | High | Unlocks future velocity |
| **4 — Polish** | ~3.5 hr | Very low | Cleanup |

Recommended order: Phase 1 → Phase 2 → Phase 3 → Phase 4, with
option to pause after any phase.

---

## Non-Recommendations (explicitly deferred)
- **Splitting `AppSettings` into separate classes** (e.g., `CredentialStore`,
  `UiPreferences`, `ServiceConfig`) — while architecturally cleaner, this
  would break the `DataRepository` delegation pattern and every consumer.
  Defer until after Phase 3 (DataRepository split) to minimize churn.
- **Removing `MemoryStoreProvider`** — the indirection is intentional for
  runtime MCP/SQLite switching. The pattern is fine despite being thin.
- **Adding a mocking library** (MockK, Mockito) — project convention uses
  hand-written fakes. Consistent with the codebase style; changing would
  require rewriting all tests.
