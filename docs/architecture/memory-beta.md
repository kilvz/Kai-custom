# Memory System Architecture (beta branch v3.1.0)

## Overview

Three-layer learning system with two-tier persistence, local-first storage with optional alt-memory MCP delegation, per-persona soul split, and a budgeted prompt builder that gates both tool availability and memory dump at inference time.

---

## Layer 1 â€” AI Tools (on-demand)

The LLM can read/write memories on demand via MCP tools gated in `Platform.android.kt:293`:

```kotlin
if (appSettings.isMemoryEnabled()) {
    addAll(CommonTools.getMemoryTools(memoryStore, sandboxController))  // memory_store, memory_retrieve, memory_forget, memory_search, memory_reinforce, memory_learn
    addAll(CommonTools.getKgTools(memoryStore))                         // dimension_kg_add, dimension_kg_query, dimension_kg_invalidate
    addAll(CommonTools.getDiaryTools(memoryStore))                      // diary_write, diary_read
    addAll(listOf(HeartbeatTools.getPromoteLearningTool(memoryStore, appSettings)))
}
```

When `isMemoryEnabled()` returns `false`, all memory/KG/diary tools are invisible to the model.

---

## Layer 2 â€” AutoMemoryLearner (inline batch extraction)

File: `AutoMemoryLearner.kt`

**Trigger**: `onExchangeComplete()` called from `RemoteDataRepository.ask()` (line 881) after every AI response.

**Interval**: Every 5 exchange pairs (`EXTRACTION_INTERVAL = 5`).

**Process**:
1. Gets last 3 exchange pairs via `dataRepository.getRecentExchanges(3)` â€” returns `"User: ...\nAssistant: ..."` formatted text.
2. Calls `dataRepository.askSilently()` with a JSON-extraction prompt targeting: named entities, user preferences, facts, errors/resolutions.
3. Parses JSON array response â€” skips transient topics, general knowledge, already-known info.
4. Dedup: checks `memoryStore.getAllMemories()` keys before storing.
5. Stores via `memoryStore.store()` â€” **unprotected** (user-visible, deletable in UI).

**Fails silently** on any error (best-effort only).

---

## Layer 3 â€” HeartbeatMemoryExtractor (post-heartbeat behavioral extraction)

File: `HeartbeatMemoryExtractor.kt`

**Trigger**: Called from `TaskScheduler.runHeartbeat()` after an AI response is received.

**Guards**: Skips if response is blank or contains `"HEARTBEAT_OK"`.

**Process**:
1. Gets last 3 exchange pairs via `dataRepository.getRecentExchanges(3)`.
2. Builds extraction prompt targeting: repeated behavioral patterns, recurring themes, behavior adjustments.
3. Calls `dataRepository.askSilently()` with prompt.
4. Parses JSON array â€” same dedup pattern as AutoMemoryLearner.
5. Stores via `memoryStore.storeProtected()` â€” **protected** (hidden from deletion UI, `memory_forget` rejects).
6. Calls `condenseToSoulAuto()` â€” summarizes all behavior memories into a compact 2-3 sentence summary via `askSilently()`, writes result to `dataRepository.setSoulAuto()`.

---

## Two-Tier Memory (`protected` field)

`MemoryEntry` contains `protected: Boolean = false`.

| Tier | protected | Source | Visible in UI | Deletable | Used in prompt |
|------|-----------|--------|---------------|-----------|----------------|
| User facts | `false` | AI tools, AutoMemoryLearner | Yes (Memories tab) | Yes (`memory_forget`, UI delete) | Yes â€” `## What I Know About You` section |
| Behavior learnings | `true` | HeartbeatMemoryExtractor | Only when "Show protected" toggle ON | No | Heartbeat prompt `## Learned Patterns` section; condensed into `soul_auto` |

### Enforcement

- **`MemoryStore.forget()`**: returns `false` if entry is protected (`SqliteMemoryStore.kt:150`, `AltMemoryClient.kt:118`).
- **Prompt builder**: `buildChatSystemPrompt` â†’ `appendMemoryCategorySection` skips `entry.protected` (line 220).
- **MemoryManagementSheet**: default filter is `.filter { !it.protected }`; "Show protected" toggle (`showProtected` state at line 52) reveals protected entries but keeps delete button â€” the `onDeleteMemory` callback calls `dataRepository.forget()` which will fail silently for protected entries.

---

## MemoryStore Interface & Implementations

```
MemoryStore (interface)
â”œâ”€â”€ SqliteMemoryStore (local, default)
â”œâ”€â”€ AltMemoryClient (MCP-backed, optional)
â””â”€â”€ MemoryStoreProvider (delegating proxy)
```

### MemoryStoreProvider

File: `MemoryStoreProvider.kt`

Delegating proxy with runtime switch:

- Default: delegates to `SqliteMemoryStore`.
- Call `useAltMemory(client, appSettings)` to switch to `AltMemoryClient`.
- Call `useLocal()` to switch back.
- `isUsingAltMemory: Boolean` exposes current delegate type.

Wired via Koin in `AppModule.kt`:
```kotlin
single<SqliteMemoryStore> { SqliteMemoryStore(get<DimensionStore>()) }
single<MemoryStoreProvider> { MemoryStoreProvider(get<SqliteMemoryStore>()) }
single<MemoryStore> { get<MemoryStoreProvider>() }
```

### SqliteMemoryStore

File: `SqliteMemoryStore.kt`

Backed by `DimensionStore` (SQLite with vector embeddings + FTS5).

**Category â†’ Realm/Domain mapping**:

| MemoryCategory | Realm | Domain |
|---------------|-------|--------|
| `GENERAL` | `REALM_AGENT` | `DOMAIN_MEMORIES` |
| `PREFERENCE` | `REALM_USER` | `DOMAIN_PREFERENCES` |
| `LEARNING` | `REALM_AGENT` | `DOMAIN_LEARNINGS` |
| `ERROR` | `REALM_AGENT` | `DOMAIN_ERRORS` |

**Entity metadata**: `memory_key`, `category`, `hit_count`, `type`, `source` stored in `EntityData.metadata` map.

**Protected field**: read from `EntityData.protected` column (DB v4 migration added `protected INTEGER NOT NULL DEFAULT 0`).

**Key operations**:
- `store()` / `storeProtected()` â€” upsert by `memory_key` metadata field.
- `forget()` â€” deletes entity by `memory_key` metadata lookup; returns `false` if protected.
- `getUserMemories()` â€” `searchEntities("", MAX).filter { !it.protected }`.
- `getBehaviorMemories()` â€” `allEntities.filter { it.protected }`.

### AltMemoryClient

File: `AltMemoryClient.kt`

Thin MCP wrapper: each call dispatches to an external `alt-memory` MCP server via `client.callTool()`.

**Key differences from SqliteMemoryStore**:
- `storeProtected()` passes `"protected": "true"` in MCP args.
- `forget()` checks entry's `protected` field via `memory_retrieve` before deleting.
- `getUserMemories()` filters client-side: `getAllMemories().filter { !it.protected }`.
- `getBehaviorMemories()` filters client-side: `getAllMemories().filter { it.protected }`.

---

## Soul Split (`soul_user` + `soul_auto`)

`AppSettings`:

| Key | Written by | Purpose |
|-----|-----------|---------|
| `soul_user` | User via Settings â†’ Soul Editor | User-defined persona/instructions |
| `soul_auto` | HeartbeatMemoryExtractor.condenseToSoulAuto() | Auto-generated behavior summary |
| `soul_text` (legacy) | Migrated to `soul_user` on first read | Pre-split combined soul |

**`getSoulText()`** returns: `"You are {personaName}."` + `soul_user` + `\n## Behavior Notes\n` + `soul_auto`.

**Export/Import**: only exports `soul_user` (not auto-generated behavior notes).

**HeartbeatTools.promote_learning**: writes to `soul_auto` instead of the combined soul.

---

## Persona System

File: `Persona.kt`

| Concept | Storage |
|---------|---------|
| Persona list | `AppSettings` key `persona_list` â€” serialized JSON `List<PersonaConfig>` |
| Active persona | `AppSettings` key `active_persona_id` |
| Per-persona soul | `soul_user_{personaId}` / `soul_auto_{personaId}` keys |

**Two built-in personas**:
- `kai` â€” full upstream prompt (all behavioral sections, `PersonaPromptStyle.KAI`, `PersonaHeartbeatStyle.KAI`)
- `alt` â€” streamlined prompt (trimmed sections, `PersonaPromptStyle.ALT`, `PersonaHeartbeatStyle.ALT`)

**Per-persona soul storage**: `PersonaManager.getSoulUserKey(id)` / `getSoulAutoKey(id)` return `"soul_user_$personaId"` / `"soul_auto_$personaId"` â€” each persona gets its own AppSettings keys, so switching persona switches soul context without data loss.

---

## ChatSystemPromptBuilder

File: `ChatSystemPromptBuilder.kt`

Two prompt style paths controlled by `PersonaPromptStyle`:

### KAI style (upstream)
Full behavioral sections: Honesty â†’ Tool Use â†’ Acting â†’ Memory Instructions â†’ Structured Learning â†’ Memory dumps â†’ Automation â†’ Email â†’ Scheduled Tasks â†’ Heartbeat Additions â†’ Context â†’ kai-ui.
Memory dump per category (GENERAL, PREFERENCE, LEARNING, ERROR) â€” no cap on remote, 1024-char cap on local.

### ALT style (custom)
Trimmed: Soul â†’ Language (2 lines) â†’ Honesty â†’ `## What I Know About You` (single section with all categories combined under 1024-char cap) â†’ Email Accounts â†’ Scheduled Tasks â†’ Heartbeat Additions â†’ Context â†’ kai-ui.

Removed sections (relative to KAI): Tool Use, When to Act, Automation, Structured Learning, Memory System, email sending policy.

**Memory budget** for ALT style: 1024 chars total across all categories (vs unlimited per-section in KAI remote).

**Protected entries**: explicitly filtered out in `appendMemoryCategorySection` (line 220: `if (entry.protected) continue`).

---

## HeartbeatPromptBuilder â€” Learned Patterns

File: `HeartbeatPromptBuilder.kt`

`buildHeartbeatPrompt()` accepts `learnedPatterns: List<MemoryEntry>`. The `## Learned Patterns` section is only rendered for `ALT` heartbeat style (`PersonaHeartbeatStyle.ALT`), displaying protected behavior memories with reinforcement counts. This section appears after pending notifications and before promotion candidates.

---

## DataRepository â€” getRecentExchanges()

Method: `getRecentExchanges(pairCount: Int = 3): String`

Returns last N user+assistant exchange pairs as:
```
User: ...
Assistant: ...
```
Used by both `AutoMemoryLearner` and `HeartbeatMemoryExtractor` as context for extraction prompts.

---

## Memory Toggle

`AppSettings.isMemoryEnabled()` â€” defaults `true`.

When disabled:
- No memory/KG/diary tools in `getAvailableTools()` (Platform.android.kt:293)
- No memory dump in `buildChatSystemPrompt` (no `## What I Know About You` section)
- AutoMemoryLearner still instantiates but `onExchangeComplete()` is called regardless â€” however extraction calls `askSilently()` which has access to tools; since tools are gated, extraction prompts still fire but cannot store. This is a minor inconsistency.

---

## Wiring Summary (Koin)

```
AppModule.kt
â”œâ”€â”€ SqliteMemoryStore â† DimensionStore
â”œâ”€â”€ MemoryStoreProvider â† SqliteMemoryStore (default)
â”‚   â””â”€â”€ MemoryStore â† MemoryStoreProvider
â”œâ”€â”€ RemoteDataRepository
â”‚   â”œâ”€â”€ memoryStore â† MemoryStore (injected)
â”‚   â”œâ”€â”€ autoMemoryLearner â† created inline with memoryStore + this (RemoteDataRepository)
â”‚   â””â”€â”€ onExchangeComplete() called after each AI response
â”œâ”€â”€ TaskScheduler
â”‚   â”œâ”€â”€ memoryStore â† MemoryStore (injected)
â”‚   â””â”€â”€ HeartbeatMemoryExtractor â† created lazily inside TaskScheduler
â””â”€â”€ Platform.androidkt.getAvailableTools()
    â””â”€â”€ memory tools gated: if (appSettings.isMemoryEnabled())
```

## Data Flow Diagram

```
User Message â†’ RemoteDataRepository.ask()
  â”œâ”€â”€ ChatHistory: user message appended
  â”œâ”€â”€ Tools: AI calls memory_store/memory_retrieve/memory_forget (on-demand, Layer 1)
  â”œâ”€â”€ ChatHistory: AI response appended
  â””â”€â”€ autoMemoryLearner.onExchangeComplete()   â† every response
       â””â”€â”€ Counter reaches 5?
            â”œâ”€â”€ getRecentExchanges(3)
            â”œâ”€â”€ askSilently(extraction prompt)
            â”œâ”€â”€ parse JSON
            â””â”€â”€ memoryStore.store(key, content, category, source="auto_learner")

Heartbeat â†’ TaskScheduler.runHeartbeat()
  â”œâ”€â”€ buildHeartbeatPrompt(learnedPatterns=memoryStore.getBehaviorMemories())
  â”œâ”€â”€ AI response
  â””â”€â”€ HeartbeatMemoryExtractor.extractFromHeartbeat(response)
       â”œâ”€â”€ Guards: blank or "HEARTBEAT_OK"? â†’ skip
       â”œâ”€â”€ getRecentExchanges(3)
       â”œâ”€â”€ askSilently(extraction prompt)
       â”œâ”€â”€ parse JSON
       â”œâ”€â”€ memoryStore.storeProtected(key, content, category, source="heartbeat")
       â””â”€â”€ condenseToSoulAuto()
            â”œâ”€â”€ memoryStore.getBehaviorMemories()
            â”œâ”€â”€ askSilently(condense prompt)
            â””â”€â”€ dataRepository.setSoulAuto(summary)
```
