# Persona System Prompt Plan

## Overview
Three persona types with independent system prompt behavior, heartbeat behavior, and memory realms.

| Persona | Type | Prompt Style | Heartbeat Style | Memory |
|---|---|---|---|---|
| Kai | Built-in | Upstream (full behavioral sections) | Upstream | Current memory system (two-tier, alt-memory) |
| alt | Built-in | Current (trimmed) | Current | Current |
| Custom | User-defined | Template = Kai, user edits soul | User chooses (default Kai) | Current |

---

### PersonaPromptStyle Enum
```kotlin
enum class PersonaPromptStyle { KAI, ALT, CUSTOM }
enum class PersonaHeartbeatStyle { KAI, ALT }
```

### PersonaConfig Data Class
```kotlin
data class PersonaConfig(
    val id: String,          // "kai", "alt", or UUID for custom
    val name: String,        // Display name
    val description: String, // Shown in selector
    val style: PersonaPromptStyle,
    val heartbeatStyle: PersonaHeartbeatStyle,
    val isBuiltIn: Boolean,  // true for kai/alt, false for custom
)
```

### Storage
- `PersonaManager` stores list in `persona_descriptors` JSON key
- Active persona ID in `current_persona`
- Soul stored per-persona as `soul_user_{id}` / `soul_auto_{id}`
- Backward compat: existing `soul_user`/`soul_auto` migrates to `alt` persona

---

## Implementation Steps

### 1. PersonaManager + PersonaConfig
- `Persona.kt` → `PersonaConfig` data class, `PersonaPromptStyle` enum, `PersonaHeartbeatStyle` enum
- `PersonaManager.kt` → CRUD for persona list, active persona management, soul key routing
- `AppSettings.kt` → per-persona soul keys, persona list storage, migration from legacy

### 2. ChatSystemPromptBuilder Dual-Mode
- Accept `PersonaPromptStyle` parameter
- `KAI` = restore upstream sections: Tool Use, When to Act, Structured Learning, Automation, memory instructions, email sending policy, active skill, full KAI_UI catalog
- `ALT` = current custom prompt (unchanged)
- Memory budget: `KAI` = Int.MAX_VALUE (remote) / 2000 (local), `ALT` = 1024

### 3. HeartbeatPromptBuilder Dual-Mode
- Accept `PersonaHeartbeatStyle` parameter
- `KAI` = upstream heartbeat prompt structure
- `ALT` = current heartbeat prompt (Learned Patterns + Promotion Candidates)

### 4. Settings UI
- Persona selector dropdown (Kai / alt / Custom)
- Show persona description in selector
- For Custom: name text field, soul editor, heartbeat style toggle
- For Kai/alt: read-only description, soul still editable per-persona

### 5. Heartbeat Routing
- `HeartbeatManager.buildHeartbeatPrompt()` uses `personaHeartbeatStyle`
- `HeartbeatMemoryExtractor` behavior unchanged (persona-agnostic)

### 6. Alt-Memory Persona Scoping
- On persona switch: `AltMemoryClient.setPersona(personaId)` → calls alt-memory `set_persona`
- Memory operations scoped to `persona_{id}` realm
- SqliteMemoryStore: no change (no persona isolation in local DB)

---

## Key Files Changed
- `Persona.kt` — replace with PersonaConfig + enums
- `PersonaManager.kt` — full CRUD, JSON serialization
- `AppSettings.kt` — per-persona keys, migration
- `DataRepository.kt` — persona CRUD, style getters
- `RemoteDataRepository.kt` — persona switch hook, prompt routing
- `ChatSystemPromptBuilder.kt` — dual-mode sections
- `HeartbeatPromptBuilder.kt` — dual-mode heartbeat
- `AltMemoryClient.kt` — setPersona()
- `MemoryStore.kt` — setPersona() interface
- `MemoryStoreProvider.kt` — delegate setPersona()
- `AgentSettings.kt` — persona dropdown, custom editor
- `SettingsViewModel.kt` — persona switch handlers
- `HeartbeatManager.kt` — route heartbeat prompt by style
- `TaskScheduler.kt` — route heartbeat by style

## Non-Goals
- No new compilation targets
- No Android-only code changes
- No sandbox/SSH/wakeword changes
- No breaking changes to ALT behavior
