# Memory System Architecture (beta branch v3.1.0)

## Overview

Three-layer learning system with two-tier persistence, local-first storage with optional alt-memory MCP delegation, per-persona soul split, and a budgeted prompt builder that gates both tool availability and memory dump at inference time.

---

## Layer 1 — AI Tools (on-demand)

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

## Layer 2 — AutoMemoryLearner (inline batch extraction)

File: `AutoMemoryLearner.kt`

**Trigger**: `onExchangeComplete()` called from `RemoteDataRepository.ask()` (line 881) after every AI response.

**Interval**: Every 5 exchange pairs (`EXTRACTION_INTERVAL = 5`).

**Process**:
1. Gets last 3 exchange pairs via `dataRepository.getRecentExchanges(3)` — returns `"User: ...\nAssistant: ..."` formatted text.
2. Calls `dataRepository.askSilently()` with a JSON-extraction prompt targeting: named entities, user preferences, facts, errors/resolutions.
3. Parses JSON array response — skips transient topics, general knowledge, already-known info.
4. Dedup: checks `memoryStore.getAllMemories()` keys before storing.
5. Stores via `memoryStore.store()` — **unprotected** (user-visible, deletable in UI).

**Fails silently** on any error (best-effort only).

---

## Layer 3 — HeartbeatMemoryExtractor (post-heartbeat behavioral extraction)

File: `HeartbeatMemoryExtractor.kt`

**Trigger**: Called from `TaskScheduler.runHeartbeat()` after an AI response is received.

**Guards**: Skips if response is blank or contains `"HEARTBEAT_OK"`.

**Process**:
1. Gets last 3 exchange pairs via `dataRepository.getRecentExchanges(3)`.
2. Builds extraction prompt targeting: repeated behavioral patterns, recurring themes, behavior adjustments.
3. Calls `dataRepository.askSilently()` with prompt.
4. Parses JSON array — same dedup pattern as AutoMemoryLearner.
5. Stores via `memoryStore.storeProtected()` — **protected** (hidden from deletion UI, `memory_forget` rejects).
6. Calls `condenseToSoulAuto()` — summarizes all behavior memories into a compact 2-3 sentence summary via `askSilently()`, writes result to `dataRepository.setSoulAuto()`.

---

## Two-Tier Memory (`protected` field)

`MemoryEntry` contains `protected: Boolean = false`.

| Tier | protected | Source | Visible in UI | Deletable | Used in prompt |
|------|-----------|--------|---------------|-----------|----------------|
| User facts | `false` | AI tools, AutoMemoryLearner | Yes (Memories tab) | Yes (`memory_forget`, UI delete) | Yes — `## What I Know About You` section |
| Behavior learnings | `true` | HeartbeatMemoryExtractor | Only when "Show protected" toggle ON | No | Heartbeat prompt `## Learned Patterns` section; condensed into `soul_auto` |

### Enforcement

- **`MemoryStore.forget()`**: returns `false` if entry is protected (`SqliteMemoryStore.kt:150`, `AltMemoryClient.kt:118`).
- **Prompt builder**: `buildChatSystemPrompt` → `appendMemoryCategorySection` skips `entry.protected` (line 220).
- **MemoryManagementSheet**: default filter is `.filter { !it.protected }`; "Show protected" toggle (`showProtected` state at line 52) reveals protected entries but keeps delete button — the `onDeleteMemory` callback calls `dataRepository.forget()` which will fail silently for protected entries.

---

## MemoryStore Interface & Implementations

```
MemoryStore (interface)
├── SqliteMemoryStore (local, default)
├── AltMemoryClient (MCP-backed, optional)
└── MemoryStoreProvider (delegating proxy)
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

**Category → Realm/Domain mapping**:

| MemoryCategory | Realm | Domain |
|---------------|-------|--------|
| `GENERAL` | `REALM_AGENT` | `DOMAIN_MEMORIES` |
| `PREFERENCE` | `REALM_USER` | `DOMAIN_PREFERENCES` |
| `LEARNING` | `REALM_AGENT` | `DOMAIN_LEARNINGS` |
| `ERROR` | `REALM_AGENT` | `DOMAIN_ERRORS` |

**Entity metadata**: `memory_key`, `category`, `hit_count`, `type`, `source` stored in `EntityData.metadata` map.

**Protected field**: read from `EntityData.protected` column (DB v4 migration added `protected INTEGER NOT NULL DEFAULT 0`).

**Key operations**:
- `store()` / `storeProtected()` — upsert by `memory_key` metadata field.
- `forget()` — deletes entity by `memory_key` metadata lookup; returns `false` if protected.
- `getUserMemories()` — `searchEntities("", MAX).filter { !it.protected }`.
- `getBehaviorMemories()` — `allEntities.filter { it.protected }`.

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
| `soul_user` | User via Settings → Soul Editor | User-defined persona/instructions |
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
| Persona list | `AppSettings` key `persona_list` — serialized JSON `List<PersonaConfig>` |
| Active persona | `AppSettings` key `active_persona_id` |
| Per-persona soul | `soul_user_{personaId}` / `soul_auto_{personaId}` keys |

**Two built-in personas**:
- `kai` — full upstream prompt (all behavioral sections, `PersonaPromptStyle.KAI`, `PersonaHeartbeatStyle.KAI`)
- `alt` — streamlined prompt (trimmed sections, `PersonaPromptStyle.ALT`, `PersonaHeartbeatStyle.ALT`)

**Per-persona soul storage**: `PersonaManager.getSoulUserKey(id)` / `getSoulAutoKey(id)` return `"soul_user_$personaId"` / `"soul_auto_$personaId"` — each persona gets its own AppSettings keys, so switching persona switches soul context without data loss.

---

## ChatSystemPromptBuilder

File: `ChatSystemPromptBuilder.kt`

Two prompt style paths controlled by `PersonaPromptStyle`:

### KAI style (upstream)
Full behavioral sections: Honesty → Tool Use → Acting → Memory Instructions → Structured Learning → Memory dumps → Automation → Email → Scheduled Tasks → Heartbeat Additions → Context → kai-ui.
Memory dump per category (GENERAL, PREFERENCE, LEARNING, ERROR) — no cap on remote, 1024-char cap on local.

### ALT style (custom)
Trimmed: Soul → Language (2 lines) → Honesty → `## What I Know About You` (single section with all categories combined under 1024-char cap) → Email Accounts → Scheduled Tasks → Heartbeat Additions → Context → kai-ui.

Removed sections (relative to KAI): Tool Use, When to Act, Automation, Structured Learning, Memory System, email sending policy.

**Memory budget** for ALT style: 1024 chars total across all categories (vs unlimited per-section in KAI remote).

**Protected entries**: explicitly filtered out in `appendMemoryCategorySection` (line 220: `if (entry.protected) continue`).

---

## HeartbeatPromptBuilder — Learned Patterns

File: `HeartbeatPromptBuilder.kt`

`buildHeartbeatPrompt()` accepts `learnedPatterns: List<MemoryEntry>`. The `## Learned Patterns` section is only rendered for `ALT` heartbeat style (`PersonaHeartbeatStyle.ALT`), displaying protected behavior memories with reinforcement counts. This section appears after pending notifications and before promotion candidates.

---

## DataRepository — getRecentExchanges()

Method: `getRecentExchanges(pairCount: Int = 3): String`

Returns last N user+assistant exchange pairs as:
```
User: ...
Assistant: ...
```
Used by both `AutoMemoryLearner` and `HeartbeatMemoryExtractor` as context for extraction prompts.

---

## Memory Toggle

`AppSettings.isMemoryEnabled()` — defaults `true`.

When disabled:
- No memory/KG/diary tools in `getAvailableTools()` (Platform.android.kt:293)
- No memory dump in `buildChatSystemPrompt` (no `## What I Know About You` section)
- AutoMemoryLearner still instantiates but `onExchangeComplete()` is called regardless — however extraction calls `askSilently()` which has access to tools; since tools are gated, extraction prompts still fire but cannot store. This is a minor inconsistency.

---

## Wiring Summary (Koin)

```
AppModule.kt
├── SqliteMemoryStore ← DimensionStore
├── MemoryStoreProvider ← SqliteMemoryStore (default)
│   └── MemoryStore ← MemoryStoreProvider
├── RemoteDataRepository
│   ├── memoryStore ← MemoryStore (injected)
│   ├── autoMemoryLearner ← created inline with memoryStore + this (RemoteDataRepository)
│   └── onExchangeComplete() called after each AI response
├── TaskScheduler
│   ├── memoryStore ← MemoryStore (injected)
│   └── HeartbeatMemoryExtractor ← created lazily inside TaskScheduler
└── Platform.androidkt.getAvailableTools()
    └── memory tools gated: if (appSettings.isMemoryEnabled())
```

## Data Flow Diagram

```
User Message → RemoteDataRepository.ask()
  ├── ChatHistory: user message appended
  ├── Tools: AI calls memory_store/memory_retrieve/memory_forget (on-demand, Layer 1)
  ├── ChatHistory: AI response appended
  └── autoMemoryLearner.onExchangeComplete()   ← every response
       └── Counter reaches 5?
            ├── getRecentExchanges(3)
            ├── askSilently(extraction prompt)
            ├── parse JSON
            └── memoryStore.store(key, content, category, source="auto_learner")

Heartbeat → TaskScheduler.runHeartbeat()
  ├── buildHeartbeatPrompt(learnedPatterns=memoryStore.getBehaviorMemories())
  ├── AI response
  └── HeartbeatMemoryExtractor.extractFromHeartbeat(response)
       ├── Guards: blank or "HEARTBEAT_OK"? → skip
       ├── getRecentExchanges(3)
       ├── askSilently(extraction prompt)
       ├── parse JSON
       ├── memoryStore.storeProtected(key, content, category, source="heartbeat")
       └── condenseToSoulAuto()
            ├── memoryStore.getBehaviorMemories()
            ├── askSilently(condense prompt)
            └── dataRepository.setSoulAuto(summary)
```
