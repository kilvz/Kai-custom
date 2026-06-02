# Rearchitecture Plan â€” Kai-custom Prompt Pipeline (Archived)

> **This plan was the original analysis. The active implementation plan is now `docs/plan-merge.md`.**
> Based on analysis of Kai-custom pipeline (`docs/prompt-pipeline.md`) vs opencode pipeline (`docs/opencode-prompt-pipeline.md`)
> Target: Incremental refactoring, no rewrite. Every phase is safe to deploy independently.

## Guiding Principles

1. **No functional regression** â€” every phase must produce the same (or strictly better) prompt output
2. **Mobile-safe** â€” Android-first; no desktop-only patterns (open code's Effect/SQLite/SSE patterns don't apply)
3. **Backward compatible** â€” existing `ChatSystemPromptBuilder` API surface preserved until phase 5
4. **Pluggable over hardcoded** â€” reduce tight coupling in `ChatSystemPromptBuilder`
5. **Tool definitions stay out of system prompt** â€” already partially done for OpenAI-compatible calls

---

## Phase 0: Audit & Baselines (Week 1)

| Task | Detail | Verification |
|------|--------|-------------|
| 0.1 | Count system prompt token cost per section (memory dumps, tool text, rules, soul) | Instrument `buildChatSystemPrompt()` with token counter |
| 0.2 | Measure overhead: tool definitions in text vs tools array (duplication) | Compare `system` text size with/without tool definitions in text |
| 0.3 | Audit all callers of `buildChatSystemPrompt()` â€” who consumes it? | Grep for references; document all callers |
| 0.4 | Measure memory dump size distribution (avg/max chars per category) | Log `getUserMemories()` sizes in production |
| 0.5 | Identify which sections are always-present vs conditional | Table in `docs/prompt-pipeline.md` already has this |

**Output**: `docs/audit.md` with token budgets, caller map, memory size distribution.

---

## Phase 1: Extract `PromptSection` Interface (Week 2-3)

### Problem
`ChatSystemPromptBuilder` is a single monolithic function with nested conditionals. Adding a new section means editing the central function.

### Solution
Define a `PromptSection` interface that each section implements independently:

```kotlin
interface PromptSection {
    val id: String
    fun shouldInclude(context: PromptContext): Boolean
    fun build(context: PromptContext): String?
}
```

Where `PromptContext` bundles all the runtime data currently passed as loose params:

```kotlin
data class PromptContext(
    val soulText: String,
    val personaPromptStyle: PromptStyle,
    val isMemoryEnabled: Boolean,
    val isRemote: Boolean,
    val isSchedulingEnabled: Boolean,
    val isEmailEnabled: Boolean,
    val isHeartbeatEnabled: Boolean,
    val isDynamicUiEnabled: Boolean,
    val isInteractiveUiMode: Boolean,
    val userMemories: List<MemoryEntry>,
    val pendingTasks: List<PendingTask>,
    val emailAccounts: List<EmailAccount>,
    val heartbeatAdditions: List<HeartbeatAddition>,
    val runtimeContext: ChatPromptRuntimeContext,
    val knowledgeGraphFacts: List<KGFact>,
    val mode: PromptMode  // CHAT, HEARTBEAT, CHAT_LOCAL
)
```

### Migration

1. Extract each section of `buildChatSystemPrompt()` into a `PromptSection` implementation:
   - `SoulSection` â€” soul text assembly
   - `HonestySection` â€” honesty rule
   - `ToolUseSection` â€” tool use instructions (KAI only)
   - `ActingSection` â€” when to act (KAI only)
   - `StructuredLearningSection` â€” memory learn instructions (KAI only)
   - `MemoryDumpSection` â€” memory category dumps
   - `MemorySearchGuidanceSection` â€” ALT memory search guidance
   - `AltMemoryDisciplineSection` â€” ALT memory discipline
   - `AutomationSection` â€” automation rules (KAI only)
   - `EmailSection` â€” email accounts
   - `TasksSection` â€” scheduled tasks
   - `HeartbeatAdditionsSection` â€” heartbeat additions
   - `ContextSection` â€” time/platform info
   - `DynamicUISection` â€” kai-ui component catalog

2. Register sections in order in a `PromptSectionRegistry`:

```kotlin
class PromptSectionRegistry {
    private val sections = mutableListOf<PromptSection>()

    fun register(section: PromptSection, index: Int = -1) { ... }
    fun build(context: PromptContext): String {
        return sections
            .filter { it.shouldInclude(context) }
            .mapNotNull { it.build(context) }
            .joinToString("\n\n")
    }
}
```

3. `buildChatSystemPrompt()` becomes a thin wrapper around `PromptSectionRegistry.build(context)`.

### Why This Matters
- New sections (e.g., "## Active Skills", "## Recent Files") can be added without touching `ChatSystemPromptBuilder`
- Sections can be tested independently
- ALT vs KAI differences become a matter of which sections are registered, not conditional branches

### Verification
- Unit test: each `PromptSection` output matches the current hardcoded output for the same inputs
- Integration test: `buildChatSystemPrompt()` output is identical before and after refactoring

---

## Phase 2: Move Tool Definitions Out of System Prompt (Week 3-4)

### Problem
Kai embeds tool definitions in system prompt text (e.g., `## Tool Use` section describes tools in prose). For OpenAI-compatible calls, tools are ALSO passed in the parallel `tools` array â€” meaning the model sees tool descriptions twice.

### Solution

1. Remove tool descriptions from `ToolUseSection` prose. Replace with:

```
## Tool Use
Use tools to verify work and resolve ambiguity. Don't ask the user for lookups you can do yourself.
Check available tools in the tools array before saying a capability is unavailable.
```

2. Ensure the `tools` JSON array passed to `buildOpenAIMessages()` has comprehensive descriptions. Currently tool descriptions are in Kotlin data classes â€” audit and improve them:

```kotlin
// Before
ToolDefinition(
    name = "memory_search",
    description = "Search memories",
    parameters = ...
)

// After  
ToolDefinition(
    name = "memory_search",
    description = "Search stored memories using semantic or keyword matching. " +
        "Returns relevant memory entries ranked by similarity.",
    parameters = ...
)
```

3. For CHAT_LOCAL (on-device models without parallel tools), keep brief tool descriptions in system prompt â€” but as a dedicated `PromptSection` registered only for CHAT_LOCAL mode.

### Token savings
Removing tool descriptions from system prompt text saves ~500-1500 tokens depending on tool count.

### Verification
- Compare prompt token count before/after
- Verify model still invokes tools correctly (regression test with known queries)

---

## Phase 3: Separate File Context From System Prompt (Week 4-5)

### Problem
When files are attached, `RemoteDataRepository` summarizes them into the system prompt. Opencode attaches full file content as separate `user` message parts â€” which is cleaner and allows the model to reference file contents directly.

### Solution

1. Add a `PromptSection` for file summaries that only renders when the model cannot see attached files:

```kotlin
class FileSummarySection(
    private val attachedFiles: List<AttachedFile>
) : PromptSection {
    override val id = "file_summary"
    override fun shouldInclude(context: PromptContext) = 
        attachedFiles.isNotEmpty() && !context.supportsFileAttachments
    
    override fun build(context: PromptContext): String {
        return "## Attached Files\n" + attachedFiles.joinToString("\n") { file ->
            "- ${file.name}: ${file.summary ?: file.mimeType}"
        }
    }
}
```

2. For providers that support file attachments (OpenAI with `file` message parts), attach files as separate message parts instead of embedding summaries.

3. `buildOpenAIMessages()` can be extended with a `fileAttachments` parameter:

```kotlin
fun buildOpenAIMessages(
    systemPrompt: String,
    userQuestion: String,
    files: List<AttachedFile> = emptyList(),
    supportsFileAttachments: Boolean = false
): List<Message>
```

### Verification
- File content is still accessible to the model (test with "read this file and summarize it")
- No duplication of file content in both system prompt and messages

---

## Phase 4: Heartbeat Prompt Reuse Chat Prompt Sections (Week 5-6)

### Problem
`HeartbeatPromptBuilder.buildHeartbeatPrompt()` duplicates much of the chat prompt logic (soul, context, tool definitions) plus adds heartbeat-specific sections (additions, results, email status, etc.). Changes to chat prompt sections must be manually synced to heartbeat.

### Solution

1. Refactor `HeartbeatPromptBuilder` to reuse `PromptSectionRegistry`:

```kotlin
fun buildHeartbeatPrompt(context: HeartbeatContext): String {
    val chatContext = context.toPromptContext()  // common fields
    val sectionRegistry = PromptSectionRegistry().apply {
        // Reuse chat sections
        register(SoulSection())
        register(HonestySection())
        register(ContextSection())
        if (context.personaPromptStyle == ALT) {
            register(AltMemoryDisciplineSection())
        }
        // Heartbeat-specific sections
        register(HeartbeatAdditionsSection(context.additions))
        register(PreviousHeartbeatResultsSection(context.results))
        register(PendingTasksSection(context.tasks))
        register(EmailStatusSection(context.emailAccounts))
        register(NewEmailsSection(context.newEmails))
        register(NewSmsSection(context.newSms))
        register(NewNotificationsSection(context.newNotifications))
        register(PromotionCandidatesSection(context.promotionCandidates))
        if (context.personaPromptStyle == ALT) {
            register(LearnedPatternsSection(context.learnedPatterns))
        }
    }
    return sectionRegistry.build(chatContext)
}
```

2. Remove duplicate code from `HeartbeatPromptBuilder`.

### Verification
- Heartbeat prompt output is identical before and after
- Changes to `SoulSection` or `ContextSection` automatically propagate to heartbeat

---

## Phase 5: Protocol Abstraction for Provider-Specific Lowering (Week 6-8)

### Problem
Kai currently has provider-specific code scattered across `RemoteDataRepository.askWithService()`, `OpenAIMessages.kt`, and provider-specific transports (OpenAI, Azure, Ollama, Anthropic, Gemini). Each provider has different:
- Message format (system/user/assistant vs `role` enums)
- Tool format (function_call vs tool_use vs tools)
- Streaming format (SSE event names, delta structures)
- Error format (status codes, error types)

### Inspiration
Opencode's `Protocol` interface abstracts this cleanly:

```typescript
interface Protocol {
    body: { schema, from: (LLMRequest) => Body }
    stream: { event, initial, step, onHalt }
}
```

### Solution (Kotlin equivalent)

```kotlin
interface LlmProtocol<TBody, TEvent> {
    val bodySchema: JsonSchema<TBody>
    fun buildBody(request: LlmRequest): TBody
    fun parseEvent(raw: String): TEvent?
    fun reduce(state: StreamState, event: TEvent): StreamResult
    fun onHalt(state: StreamState): AssistantMessage?
}

data class LlmRequest(
    val system: List<SystemPart>,
    val messages: List<Message>,
    val tools: List<ToolDefinition>,
    val toolChoice: ToolChoice,
    val model: ModelConfig,
    val generation: GenerationOptions,
    val providerOptions: ProviderOptions,
    val metadata: Map<String, String>
)
```

1. Implement protocols for each provider:
   - `OpenAiChatProtocol` â€” OpenAI / Azure / DeepSeek / TogetherAI
   - `AnthropicProtocol` â€” Anthropic Messages API
   - `GeminiProtocol` â€” Gemini API
   - `OllamaProtocol` â€” Ollama (OpenAI-compatible variant)

2. Each protocol handles:
   - **`body.from()`**: Convert `LlmRequest` â†’ provider-native JSON body
   - **`stream.step()`**: Parse SSE events â†’ internal `StreamEvent` types
   - **`stream.initial()`**: Create initial state
   - **`stream.onHalt()`**: Handle stream termination (return final message)

3. A `Route` composes `Protocol + Endpoint + Auth`, registered by provider name.

### Benefits
- New providers added by implementing `LlmProtocol` â€” no `RemoteDataRepository` changes
- Streaming parsing is isolated per provider
- Tool format differences handled in one place
- Easier to add streaming middleware (token counting, logging, rate limiting)

### Verification
- Each provider produces identical messages and streaming behavior before/after
- Regression test: send same `LlmRequest` to each provider and compare outputs

---

## Phase 6: Schema-Validated Tool Definitions (Week 8-10)

### Problem
Tool definitions in Kai are Kotlin data classes with manual JSON Schema annotations (`@JsonProperty`, `@JsonInclude`, etc.). There's no compile-time guarantee that the JSON Schema matches the actual function parameters.

### Inspiration
Opencode uses Effect Schema to define tool schemas, which auto-derives JSON Schema:

```typescript
const myTool = tool({
    description: "Do something",
    parameters: Schema.Struct({
        name: Schema.String,
        count: Schema.Number
    })
})
```

### Solution

1. Define a `ToolSchema` DSL similar to opencode's `tool()`:

```kotlin
val memorySearchTool = tool("memory_search", "Search stored memories") {
    param("query", StringType, "Search query")
    param("mode", EnumType("vector", "keyword", "hybrid"), "Search mode", default = "hybrid")
    param("limit", IntType, "Max results", default = 10)
}
```

2. Auto-derive JSON Schema from the DSL:

```kotlin
fun ToolDefinition.toJsonSchema(): JsonObject {
    // Walk the DSL AST to produce:
    // { "type": "object", "properties": { ... }, "required": [...] }
}
```

3. Replace manual `@JsonProperty` annotations with DSL-based definitions.

4. Add compile-time validation: tool definition must match the actual function's Kotlin parameter list.

### Benefits
- JSON Schema is always consistent with the tool's parameter list
- Adding a parameter is a single change (DSL + function signature)
- Schema can be validated at compile time with a lint check

### Verification
- All existing tool schemas produce identical JSON output before/after
- No regressions in tool call/response flow

---

## Phase 7: Memory System as Optional Prompt Section (Week 10-11)

### Problem
Memory dumps (facts, preferences, learnings, errors) are currently unconditionally appended to the system prompt when memory is enabled. This can consume significant token budget.

### Solution

1. Memory dump becomes a `PromptSection` (from Phase 1):

```kotlin
class MemoryDumpSection(
    private val memories: List<MemoryEntry>,
    private val budget: Int = UNLIMITED
) : PromptSection {
    override val id = "memory_dump"
    override fun shouldInclude(context: PromptContext) = 
        context.isMemoryEnabled && memories.isNotEmpty()
    
    override fun build(context: PromptContext): String? {
        val filtered = memories.filter { !it.protected }
        val grouped = groupByCategory(filtered)
        val rendered = renderWithBudget(grouped, budget)
        return if (rendered.isBlank()) null else "## What I Know About You\n$rendered"
    }
}
```

2. Add budget-aware rendering that respects token limits:
   - CHAT_LOCAL: 1024 chars total
   - Remote ALT: 1024 chars under `## What I Know About You`
   - Remote KAI: unlimited but capped at `memoryDumpMaxTokens` setting

3. Add a `relevanceSort` option â€” only include memories relevant to the current conversation context (requires embedding similarity search). This is what upstream Kai does with `relevantPages` concept.

### Verification
- Same memories appear in prompt as before (when budget allows)
- No functional regression for memory-dependent queries

---

## Phase 8: Dynamic Prompt Section Ordering (Week 11-12)

### Problem
Section order is currently hardcoded in `buildChatSystemPrompt()`. ALT and KAI have different orders, but within a style the order never changes.

### Solution

1. Allow per-persona section ordering in `PersonaConfig`:

```kotlin
data class PersonaConfig(
    val id: String,
    val name: String,
    val promptStyle: PromptStyle,
    val heartbeatStyle: HeartbeatStyle,
    val defaultSoul: String,
    val sectionOrder: List<String> = defaultSectionOrder(promptStyle)
)
```

2. `PromptSectionRegistry` uses the persona's `sectionOrder`:

```kotlin
fun build(context: PromptContext): String {
    val order = context.personaConfig.sectionOrder
    return order.mapNotNull { id -> registry[id]?.takeIf { it.shouldInclude(context) }?.build(context) }
        .joinToString("\n\n")
}
```

3. Default orders match current KAI and ALT behavior.

### Verification
- Default orders produce identical output to current behavior
- Custom order works correctly for new personas

---

## Phase 9: Tool Definition Registry (Week 12-13)

### Problem
Tools are currently defined ad-hoc in `ToolDefinitions.kt`, registered in `ToolExecutor`, and described in the system prompt. Adding/removing tools requires touching multiple places.

### Solution

1. Create a `ToolRegistry` analogous to `PromptSectionRegistry`:

```kotlin
class ToolRegistry {
    private val tools = mutableMapOf<String, ToolDefinition>()
    fun register(tool: ToolDefinition) { ... }
    fun unregister(name: String) { ... }
    fun definitions(): List<ToolDefinition>
}
```

2. MCP-discovered tools are registered dynamically (already works, but formalize the API).

3. The `tools` array passed to the LLM comes from `ToolRegistry.definitions()`, filtered by context (e.g., no memory tools when memory disabled).

### Benefits
- Adding a new tool = `toolRegistry.register(myTool)` in one place
- Context-dependent tool filtering is centralized
- MCP tools are naturally first-class

### Verification
- All existing tools appear in the `tools` array as before
- MCP tools continue to work

---

## Phase 10: Documentation & Testing (Ongoing)

| Task | Detail |
|------|--------|
| 10.1 | Document each `PromptSection` with purpose, conditions, and token cost in `docs/prompt-sections.md` |
| 10.2 | Unit test every section independently |
| 10.3 | Integration test: full prompt output for every combination of conditions |
| 10.4 | Token budget regression test: alert if prompt exceeds threshold |
| 10.5 | Update `docs/prompt-pipeline.md` after each phase |

---

## Migration Strategy

Each phase is designed to be independently mergable and deployable:

```
Phase 0 â”€â”¬â”€â†’ Phase 1 â”€â”€â†’ Phase 2 â”€â”€â†’ Phase 3 â”€â”€â†’ Phase 4 â”€â”€â†’ Phase 5 â”€â”€â†’ Phase 6 ...
          â”‚                               â†“
          â””â”€â†’ [target: main branch]        (all phases merge to main)
```

- **Dependencies**: Phase 1 â†’ 4 (sections must be extractable before heartbeat can reuse them)  
  Phase 5 â†’ 6 (protocol abstraction enables schema validation)
- **Parallelizable**: Phase 7 (memory budget) can be done independently after Phase 1  
  Phase 8 (dynamic ordering) depends on Phase 1  
  Phase 9 (tool registry) can be done independently

---

## Risk Assessment

| Risk | Mitigation |
|------|-----------|
| **Prompt output changes** (model behavior regression) | Phase 1 strict equality test; Stage-by-stage gradual rollout |
| **Increased complexity** from abstraction layers | Keep interfaces minimal; avoid over-engineering (YAGNI) |
| **Performance overhead** from section iteration | Sections are O(n) on <20 items; negligible |
| **Tool call breakage** from removing tool text from prompt | Phase 2 tested with known tool-invoking queries |
| **Memory budget changes** break user experience | Phase 7 preserves existing budgets by default |
| **Android build size increase** from new abstractions | Minimal â€” interface definitions are zero-cost abstractions in Kotlin |

---

## Token Budget Projections

After all phases:

| Section | Current (est. tokens) | After (est. tokens) | Savings |
|---------|---------------------|---------------------|---------|
| Soul | 200-600 | 200-600 | â€” |
| Honesty | 15 | 15 | â€” |
| Tool Use (prose) | 200-500 | ~30 | ~170-470 |
| When to Act | 100 | 100 | â€” |
| Structured Learning | 120 | 120 | â€” |
| Memory dumps | 200-2000 | 200-2000 (budget+capped) | â€” |
| Automation | 300 | 300 | â€” |
| Email | 50-200 | 50-200 | â€” |
| Tasks | 50-200 | 50-200 | â€” |
| Heartbeat Additions | 50-300 | 50-300 | â€” |
| Context | 50 | 50 | â€” |
| Dynamic UI | 400-800 | 400-800 | â€” |
| **Tool definitions (duplicate text)** | **500-1500** | **0** | **500-1500** |
| **Total** | **~2200-6500** | **~1700-5000** | **~500-1500** |

Primary savings come from removing duplicate tool descriptions from system prompt text.
