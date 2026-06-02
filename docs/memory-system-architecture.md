# Kai-custom Memory System Architecture

## Overview

The memory system is a **two-tier, dual-backend** persistent memory layer for AI chat. It stores user facts, behavioral patterns, knowledge graph triples, and diary entries â€” all of which are surfaced to the AI in system prompts or heartbeat prompts. The system supports seamless switching between a local SQLite backend and a remote Python `alt-memory` MCP server.

---

## 1. Core Data Model

### MemoryEntry (two-tier)

```
MemoryEntry(
    key: String,              // Unique descriptive identifier (e.g. "user_name")
    content: String,          // Stored value
    createdAt: Long,
    updatedAt: Long,
    category: MemoryCategory,  // GENERAL | LEARNING | ERROR | PREFERENCE
    hitCount: Int,             // Reinforcement count
    source: String?,           // Origin: "auto_learner", "heartbeat", "tool", etc.
    protected: Boolean,        // true = behavior learning (hidden from UI/chat prompt)
)
```

**Two tiers defined by `protected`:**
- `protected=false` â€” **User-facing memories**: facts, preferences, general info. Created by AI tools, AutoMemoryLearner. Visible in Settings, deletable.
- `protected=true` â€” **Behavior learnings**: patterns observed by heartbeat. Created by HeartbeatMemoryExtractor. Hidden from deletion UI, rejected by `forget()`.

### EntityData (persistence layer)

```
EntityData(
    id: String,
    realm: String,       // "agent" | "user" | "project" | "system"
    domain: String,      // "memories" | "preferences" | "learnings" | "errors" | "diary"
    content: String,
    metadata: Map<String, String>,  // memory_key, category, hit_count, source
    protected: Boolean,  // DB column (v4 migration)
    embedding: List<Float>?,  // Vector embedding for similarity search
)
```

### KGFact (knowledge graph)

```
KGFact(
    id: String,
    subject: String,
    predicate: String,
    object: String,
    validFrom: Long?,
    validTo: Long?,       // Set on invalidation (soft delete)
    sourceEntityId: String?,
    createdAt: Long,
)
```

### DiaryEntry

```
DiaryEntry(
    id: String,
    agentName: String,   // Always "kai"
    topic: String,
    content: String,
    createdAt: Long,
)
```

---

## 2. Interface Hierarchy

```
MemoryStore (interface)
â”œâ”€â”€ store() / reinforceMemory() / updateContent() / forget()
â”œâ”€â”€ storeProtected() / getUserMemories() / getBehaviorMemories()
â”œâ”€â”€ getAllMemories() / searchMemories()
â”œâ”€â”€ addFact() / queryFacts() / invalidateFact()        â† KG operations
â”œâ”€â”€ diaryWrite() / diaryRead()                          â† Diary operations
â”œâ”€â”€ exportDimension() / importDimension()               â† Backup
â”‚
â”œâ”€â”€ SqliteMemoryStore (local, wraps DimensionStore)      â† Default
â””â”€â”€ AltMemoryClient (remote, wraps MCP Client)           â† After migration
     â”‚
     â””â”€â”€ MemoryStoreProvider (delegates to either)        â† Injected as MemoryStore
```

**Wiring (Koin):**
```
single<DimensionStore> â†’ SqliteDimensionStore
single<SqliteMemoryStore> â†’ wraps DimensionStore
single<MemoryStoreProvider> â†’ wraps SqliteMemoryStore (can switch)
single<MemoryStore> â†’ MemoryStoreProvider  â† public API
```

---

## 3. Database Schema (SQLite)

**File:** `kai_dimension.db`, version 4

### Tables

**`entities`** â€” stores memories, diary entries, and all dimension data:
```sql
CREATE TABLE entities (
    id TEXT PRIMARY KEY,
    realm TEXT NOT NULL,        -- "agent" | "user" | "project" | "system"
    domain TEXT NOT NULL,       -- "memories" | "preferences" | "learnings" | "errors" | "diary"
    content TEXT NOT NULL,
    source_file TEXT,
    metadata TEXT DEFAULT '{}',  -- JSON: memory_key, category, hit_count, type, source
    content_hash TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    -- v3: embedding TEXT      (JSON float array for vector search)
    -- v4: protected INTEGER DEFAULT 0
)
```

**`kg_facts`** â€” knowledge graph triple store:
```sql
CREATE TABLE kg_facts (
    id TEXT PRIMARY KEY,
    subject TEXT NOT NULL,
    predicate TEXT NOT NULL,
    object TEXT NOT NULL,
    valid_from INTEGER,
    valid_to INTEGER,           -- NULL = active, set = invalidated
    source_entity_id TEXT,
    created_at INTEGER NOT NULL
)
```

**`realms` / `domains`** â€” metadata tables for organizing entity hierarchy.

### Default Hierarchy
```
agent/
â”œâ”€â”€ memories/     (GENERAL category)
â”œâ”€â”€ learnings/    (LEARNING category)
â”œâ”€â”€ errors/       (ERROR category)
â””â”€â”€ diary/

user/
â”œâ”€â”€ memories/     (GENERAL category)
â””â”€â”€ preferences/  (PREFERENCE category)

project/memories/
system/memories/
system/errors/
```

---

## 4. Two-Tier Memory System

### Tier 1: User-Facing Memories (`protected=false`)

| Creator | Source | Method |
|---------|--------|--------|
| AI tool call | `memory_store` | `store()` |
| AI tool call | `memory_learn` | `store()` |
| AutoMemoryLearner | Every 5 chat exchanges | `store(source="auto_learner")` |

**Where they appear:**
- Chat system prompt â†’ `## What I Know About You` section (1024-char budget)
- Settings â†’ MemoryManagementSheet (deletable, editable)
- Search results via `searchMemories(query)`

### Tier 2: Behavior Learnings (`protected=true`)

| Creator | Source | Method |
|---------|--------|--------|
| HeartbeatMemoryExtractor | Post-heartbeat | `storeProtected(source="heartbeat")` |

**Where they appear:**
- Heartbeat prompt â†’ `## Learned Patterns` section (as `learnedPatterns`)
- Condensed into `soul_auto` â†’ `## Behavior Notes` in combined soul text
- NOT in chat system prompt's `## What I Know About You`
- NOT deletable from UI (hidden by default, toggle to show, no delete button)
- `forget()` rejects them

### Promotion (Tier 2 â†’ Tier 1 exit)

The `promote_learning` tool (called by AI during heartbeat) removes a behavior memory and appends its content to `soul_auto`:
```
promote_learning(key, soul_addition)
  â†’ appSettings.setSoulAuto(current + soul_addition)
  â†’ memoryStore.forget(key)  // removes from protected memories
```

---

## 5. Memory Creation Flows

### 5a. AI Tool Call (user facts)

```
User: "My name is Alice and I prefer dark mode"
  â†’ AI calls memory_store(key="user_name", content="Alice")
  â†’ AI calls memory_learn(key="pref_theme", content="dark mode", category="PREFERENCE")
    â†’ CommonTools â†’ MemoryStore.store()
      â†’ SqliteMemoryStore.store()
        â†’ dimension.putEntity(realm="agent"|"user", domain=basedOnCategory)
          â†’ INSERT INTO entities (protected=0)
```

### 5b. AutoMemoryLearner (batch inline extraction)

```
After every 5th AI response (RemoteDataRepository.ask()):
  â†’ AutoMemoryLearner.onExchangeComplete()
    â†’ counter hits 5, resets
    â†’ getRecentExchanges(3)  â†’ last 3 user+assistant pairs
    â†’ dataRepository.askSilently(extraction prompt)
      â†’ AI returns JSON: [{key, content, category}, ...]
    â†’ For each item:
      â†’ if key doesn't exist â†’ memoryStore.store(key, content, category, "auto_learner")
```

**Extraction rules** (instructed in prompt):
- Extract: named entities, explicit preferences, shared facts, errors/resolutions
- Ignore: transient topics, general knowledge, already-known info

### 5c. HeartbeatMemoryExtractor (post-heartbeat extraction)

```
After each heartbeat AI response (TaskScheduler.runHeartbeat()):
  â†’ HeartbeatMemoryExtractor.extractFromHeartbeat(response)
    â†’ Skip if blank or "HEARTBEAT_OK"
    â†’ getRecentExchanges(3)
    â†’ dataRepository.askSilently(behavior pattern prompt)
      â†’ AI returns JSON: [{key, content}, ...]
    â†’ For each item:
      â†’ if key doesn't exist â†’ memoryStore.storeProtected(key, content, "heartbeat")
    â†’ If new items:
      â†’ condenseToSoulAuto()
        â†’ getBehaviorMemories()
        â†’ askSilently("Condense to 2-3 sentences")
        â†’ dataRepository.setSoulAuto(summary)
```

---

## 6. Prompt Integration

### 6a. Chat System Prompt (`## What I Know About You`)

**Gate:** Only included if `isMemoryEnabled()` returns true.

**Budget:** Total 1024 characters across all memory categories.

**Structure:**
```
## What I Know About You
{relevantMemories (from search, with hit counts)}

Your Memories:
{general entries, truncated to budget}
User Preferences:
{preference entries, truncated}
Learnings:
{learning entries, truncated}
Known Issues & Resolutions:
{error entries, truncated}
```

**Protected entries are SKIPPED** â€” behavior learnings never appear here.

### 6b. Soul Text (always present in system prompt)

```
You are {personaName}.

{soul_user}

## Behavior Notes
{soul_auto}
```

The `soul_auto` portion is the condensed behavior summary from HeartbeatMemoryExtractor.

### 6c. Heartbeat Prompt (`## Learned Patterns`)

Only included if `getBehaviorMemories()` is non-empty:
```
## Learned Patterns
- **{key}** (reinforced {hitCount}x): {content}
```

Also includes a Promotion Candidates section for memories with high hitCount.

---

## 7. Knowledge Graph

**Storage:** `kg_facts` table (separate from entities)

**Tools exposed to AI:**
| Tool | Signature | Effect |
|------|-----------|--------|
| `kg_add` | (subject, predicate, object) | INSERT triple |
| `kg_query` | (entity?, relation?, limit) | SELECT where subject or object matches |
| `kg_invalidate` | (subject, predicate, object) | SET valid_to = now (soft delete) |

**No temporal query support** in the current implementation â€” facts are matched by string equality only.

---

## 8. Diary System

**Storage:** `entities` table, realm=`agent`, domain=`diary`

**Metadata:** `{agent_name: "kai", topic: "...", type: "diary_entry"}`

**Tools exposed to AI:**
| Tool | Signature | Effect |
|------|-----------|--------|
| `diary_write` | (content, topic?) | INSERT entry |
| `diary_read` | (last_n: Int) | SELECT recent N by createdAt |

---

## 9. Memory Toggle

**Setting:** `isMemoryEnabled()` (default: ON), stored in SharedPreferences as `memory_enabled`.

**When OFF:**
1. Chat system prompt gets `memories=emptyList()`, `relevantMemories=emptyList()` â€” no `## What I Know About You`
2. `getAvailableTools()` excludes all memory/KG/diary tools + `promote_learning`
3. AutoMemoryLearner still runs but has no memories to extract into (store() goes to DB but nothing surfaces)
4. HeartbeatMemoryExtractor still writes protected memories (used for behavior notes)

---

## 10. Alt Memory Backend (Optional Remote)

**File:** `AltMemoryClient.kt` â€” delegates all MemoryStore operations to MCP server running inside Linux sandbox.

**Lifecycle manager:** `AltMemoryLifecycleManager.runMigration()`:
1. `pip install alt-memory` if needed
2. Start `alt-memory mcp --transport sse --port 8316` in sandbox
3. Copy all existing entities from `SqliteMemoryStore` â†’ alt-memory via MCP calls
4. Switch `MemoryStoreProvider` to use `AltMemoryClient`

**After migration:** All memory operations go through Python alt-memory server (vector search, persistent storage). A `switchToLocal()` method can revert.

---

## 11. Memory Management UI

### MemoryManagementSheet (Settings â†’ Memories)

Three tabs:
- **Stats** â€” total entity count from `countDimensionEntities()`
- **Memories** â€” lists all non-protected memories by default
  - "Show protected" toggle reveals protected entries
  - Protected entries: no delete button
  - Non-protected: delete with 4-second undo timeout, edit inline
- **KG Facts** â€” lists all triples from `queryKgFacts()`

### Memory back up

- Export: `exportDimension()` â†’ gzipped JSON â†’ `.kai-dimension` file
- Import: `.kai-dimension` file â†’ `importDimension(data)` â†’ restore

---

## 12. Soul Split Architecture

Three keys in SharedPreferences:

| Key | Set by | Shown in | Exported |
|-----|--------|----------|----------|
| `soul_user` | User (Settings â†’ Soul editor) | Chat system prompt | Yes |
| `soul_auto` | HeartbeatMemoryExtractor (auto) | Chat system prompt as `## Behavior Notes` | No |
| `current_persona` | User (Settings â†’ Persona name field) | Prepended to combined soul | Partially |

**Combined output** (`getSoulText()`):
```
You are {personaName}.

{user-edited soul}

## Behavior Notes
{auto-generated behavior summary}
```

**Backward compat:** Legacy `soul_text` key migrated to `soul_user` on first read.

---

## 13. Component Dependency Graph

```
SqliteDimensionStore (single, Android)
  â””â”€â”€ SqliteMemoryStore (single)
        â””â”€â”€ MemoryStoreProvider (single, injected as MemoryStore)
              â”œâ”€â”€ AutoMemoryLearner (in RemoteDataRepository)
              â”œâ”€â”€ HeartbeatManager (single)
              â”‚     â””â”€â”€ HeartbeatPromptBuilder.buildHeartbeatPrompt()
              â”œâ”€â”€ TaskScheduler â†’ HeartbeatMemoryExtractor (lazy)
              â”œâ”€â”€ CommonTools (AI-facing memory/KG/diary tools)
              â”œâ”€â”€ HeartbeatTools (promote_learning tool)
              â”œâ”€â”€ SettingsViewModel (UI management)
              â””â”€â”€ ChatSystemPromptBuilder (prompt construction)
```

---

## 14. Key Constants

| Constant | Value | Location |
|----------|-------|----------|
| `EXTRACTION_INTERVAL` | 5 exchanges | AutoMemoryLearner |
| `PAIR_COUNT` (AutoMemory) | 3 exchanges | AutoMemoryLearner |
| `PAIR_COUNT` (Heartbeat) | 3 exchanges | HeartbeatMemoryExtractor |
| `MEMORY_BUDGET_CHARS` | 1024 chars | ChatSystemPromptBuilder |
| `DB_VERSION` | 4 | SqliteDimensionStore |
| `DB_NAME` | `kai_dimension.db` | SqliteDimensionStore |
| `ANTI_FLAP_MS` | 2000ms | WakeWordService |
| `HEALTH_CHECK_RETRIES` | 12 | AltMemoryLifecycleManager |
| `HEALTH_CHECK_DELAY_MS` | 5000ms | AltMemoryLifecycleManager |

---

## 15. Data Flow Diagrams

### User Memory Lifecycle
```
User says "I hate pineapple on pizza"
  â†’ AI calls memory_store(key="food_pineapple", content="hates pineapple on pizza")
    â†’ CommonTools â†’ MemoryStore.store()
      â†’ SqliteMemoryStore â†’ EntityData(realm="agent", domain="memories", protected=0)
        â†’ INSERT INTO entities

Next chat:
  â†’ getActiveSystemPrompt()
    â†’ getUserMemories() â†’ filters !protected
    â†’ ChatSystemPromptBuilder buildChatSystemPrompt(memories, budget=1024)
      â†’ "## What I Know About You\n- hates pineapple on pizza"

Settings:
  â†’ User views â†’ delete â†’ MemoryStore.forget("food_pineapple")
    â†’ EntityData found by metadata key â†’ DELETE
```

### Behavior Memory Lifecycle
```
Heartbeat fires (every N minutes):
  â†’ TaskScheduler.runHeartbeat()
    â†’ heartbeatManager.buildHeartbeatPrompt()
      â†’ getBehaviorMemories() â†’ learnedPatterns section
    â†’ heartbeatMemoryExtractor.extractFromHeartbeat(response)
      â†’ askSilently() â†’ parse JSON
      â†’ storeProtected(key="pattern_brevity", content="prefers brief replies")
        â†’ EntityData(protected=1) â†’ INSERT INTO entities
      â†’ condenseToSoulAuto()
        â†’ setSoulAuto("User prefers concise responses...")

Next heartbeat:
  â†’ ## Learned Patterns: "prefers brief replies (reinforced 1x)"

AI promotes:
  â†’ promote_learning("pattern_brevity", "Values concise communication")
    â†’ soul_auto updated â†’ memory forgotten
```

### Memory Disabled Flow
```
User toggles memory OFF:
  â†’ Platform.getAvailableTools() â†’ memory/KG/diary tools removed
  â†’ getActiveSystemPrompt() â†’ memories=[], relevantMemories=[]
  â†’ No "## What I Know About You" section
  â†’ Memory still persists in DB, just not surfaced
```

---

## 16. Security & Isolation

- **Protected memories** cannot be deleted via `forget()` â€” enforced at store level
- **Protected memories** hidden from chat system prompt â€” never exposed to AI context (only in heartbeat prompt)
- **Protected memories** hidden from UI by default â€” user must toggle "Show protected" to see them
- **Tool gating** â€” all memory operations are behind `isMemoryEnabled()` toggle
- **Export** only includes `soul_user`, not `soul_auto` (behavior summaries stay local)
