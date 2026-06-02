# Merge Plan â€” Unified Prompt Architecture

> **Goal**: Replace KAI / ALT / CUSTOM three-style split with a single architecture:
> **Persona (chooser) + Technical (auto) + Custom Soul (user-specific only)**
>
> Based on analysis in `docs/prompt-pipeline.md`, `docs/opencode-prompt-pipeline.md`.

## Core Architecture: Three Independent Layers

```
 SYSTEM PROMPT
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚  LAYER 1: PERSONA (from chooser â€” who the AI IS)         â”‚
â”‚  â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€  â”‚
â”‚  â€¢ Name + defaultSoul  â† character definition             â”‚
â”‚  â€¢ Language instruction  â† from LanguageStyle trait       â”‚
â”‚  â€¢ Honesty rule                                          â”‚
â”‚  â†’ Selected from built-in catalog, updated when user      â”‚
â”‚    switches persona. NOT user-editable as free text.      â”‚
â”œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¤
â”‚  LAYER 2: TECHNICAL (auto-generated â€” how the AI ACTS)   â”‚
â”‚  â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€  â”‚
â”‚  â€¢ Tool use instructions                                 â”‚
â”‚  â€¢ Memory system rules                                    â”‚
â”‚  â€¢ Automation & scheduling                                â”‚
â”‚  â€¢ Email policy                                           â”‚
â”‚  â€¢ Integration status (accounts, tasks)                   â”‚
â”‚  â€¢ Context (time, platform, model)                        â”‚
â”‚  â€¢ Dynamic UI catalog                                     â”‚
â”‚  â†’ Generated from capabilities. NOT user-editable.        â”‚
â”œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¤
â”‚  LAYER 3: CUSTOM SOUL (user + auto â€” what the AI KNOWS)  â”‚
â”‚  â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€  â”‚
â”‚  â€¢ soul_user  â† user-written preferences/behavior notes   â”‚
â”‚  â€¢ soul_auto  â† auto-promoted learnings (heartbeat)      â”‚
â”‚  â†’ ONLY user-specific content. NOT character definition.  â”‚
â”‚  â†’ Strips anything covered by persona or technical layer. â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
```

## Why This Architecture

| Old approach | Problem | New approach |
|-------------|---------|-------------|
| `PromptStyle.KAI/ALT/CUSTOM` | 3 duplicated code paths | Single unified builder |
| `soul_text` was everything (character + user prefs) | Couldn't tell what was persona vs learned | Split into persona (chooser) + custom soul (user prefs) |
| User had to write entire soul text | High barrier, no guidance | Pick persona from catalog, write only preferences |
| ALT defaultSoul had tool rules baked in | Duplicated with `## Tool Use` section | Tool rules â†’ Technical layer only |
| `memoryInstructions` always null | Dead parameter | Removed |
| KAI/ALT heartbeat styles | Duplicated logic | Single heartbeat builder |

## Layer 1: Persona (Chooser)

### Data Model

```kotlin
enum class BehaviorStyle { ASSISTANT, OPERATOR, CUSTOM }
enum class LanguageStyle { FORMAL, CASUAL, TECHNICAL, CREATIVE, MINIMAL }
enum class CharacterType { HELPER, EXPERT, COMPANION, CRITIC, CREATOR }

data class PersonaConfig(
    val id: String,
    val name: String,
    val description: String,
    val behaviorStyle: BehaviorStyle,
    val languageStyle: LanguageStyle,
    val characterType: CharacterType,
    val skills: List<String>,        // e.g. ["coding", "writing", "analysis"]
    val isBuiltIn: Boolean,
    val defaultSoul: String,          // the character soul text
    val renderMode: RenderMode,       // UPSTREAM_COMPAT or FORK_ENHANCED
)
```

### Catalog Categories

| Category | Count | Theme | Example Personas |
|----------|-------|-------|-----------------|
| **Assistant** | ~15 | Helpful, supportive, upstream-like | Kai (default), Sage, Butler, Professor, Coach, Mentor, Guide, Librarian, Secretary, Concierge, Steward, Aide, Deputy, Clerk, Curator |
| **Operator** | ~15 | Pragmatic, tool-oriented, opencode-like | Alt (default), Hacker, Analyst, Automator, Debugger, Architect, Engineer, Operator, Pilot, Scout, Agent, Technician, Crafter, Tinkerer, Builder |
| **Custom** | ~15 | Specialized, personality-driven | Storyteller, Companion, Critic, Poet, Muse, Advisor, Negotiator, Mediator, Explorer, Chef, Scientist, Detective, Reporter, Diplomat, Philosopher |

Each persona provides:
- A unique `defaultSoul` â€” character definition (tone, boundaries, personality)
- `languageStyle` â€” how it communicates
- `characterType` â€” archetype
- `skills` â€” areas of specialization

### SoulEditor UI

```
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚  Persona                                 â”‚
â”‚  â”Œâ”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”                  â”‚
â”‚  â”‚Assisâ”‚Operatâ”‚Customâ”‚  â† tabs          â”‚
â”‚  â”œâ”€â”€â”€â”€â”€â”´â”€â”€â”€â”€â”€â”€â”´â”€â”€â”€â”€â”€â”€â”¤                  â”‚
â”‚  â”‚ â”Œâ”€â”€â”€â”€â” â”Œâ”€â”€â”€â”€â”    â”‚  â† persona grid   â”‚
â”‚  â”‚ â”‚Kai â”‚ â”‚Sageâ”‚    â”‚    (cards)         â”‚
â”‚  â”‚ â”‚Helperâ”‚ â”‚Expertâ”‚  â”‚                  â”‚
â”‚  â”‚ â””â”€â”€â”€â”€â”˜ â””â”€â”€â”€â”€â”˜    â”‚                  â”‚
â”‚  â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜                   â”‚
â”‚                                         â”‚
â”‚  Character: Helper   Style: Casual    â”‚  â† read-only traits
â”‚  Skills: Coding, Writing               â”‚
â”‚                                         â”‚
â”‚  â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€  â”‚
â”‚                                         â”‚
â”‚  Custom Soul (what I know about you)    â”‚
â”‚  â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”   â”‚
â”‚  â”‚ User writes preferences/behaviorâ”‚   â”‚
â”‚  â”‚ notes here. NOT character def.  â”‚   â”‚
â”‚  â”‚                                  â”‚   â”‚
â”‚  â”‚ e.g. "User prefers concise      â”‚   â”‚
â”‚  â”‚ responses under 3 paragraphs"   â”‚   â”‚
â”‚  â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜   â”‚
â”‚                                         â”‚
â”‚  Behavior Notes (auto-generated)        â”‚
â”‚  â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”   â”‚
â”‚  â”‚ Promoted learnings from         â”‚   â”‚
â”‚  â”‚ heartbeat/auto learning         â”‚   â”‚
â”‚  â”‚ (read-only)                     â”‚   â”‚
â”‚  â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜   â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
```

### Key UI Rules

1. **Persona tabs**: Assistant | Operator | Custom â€” shows persona cards grid
2. **Persona card**: Name, description, character type icon, language style badge, skills chips
3. **Persona selected**: Shows read-only traits preview, applies immediately
4. **Custom Soul field**: Free text for user-specific preferences/behavior notes ONLY
5. **Behavior Notes**: Read-only from `soul_auto`, shows promoted learnings
6. **Built-in personas**: Cannot be deleted or edited (name/description fixed)
7. **Custom personas**: User can create from scratch, name it, pick traits, write soul

## Layer 2: Technical (Auto-Generated)

Not user-editable. Generated from current capabilities:

```
[Shown if tools available]
## Tool Use
Use tools to verify work and resolve ambiguity...

[Shown if memory enabled + remote]
## Memory System
Use memory_store to record user preferences...

[Shown if scheduling enabled + remote]
## Automation
Every form of automated execution goes through schedule_task...

[Shown if email enabled + accounts exist]
## Email Policy
Before calling compose_email, present the full draft...

[Shown if accounts/tasks/additions exist]
## Email Accounts
- user@example.com: 3 unread

## Scheduled Tasks
- Morning check (id: t1)...

## Heartbeat Additions
- Standing instructions...

[Always shown]
## Context
- Local time: ... (Europe/Berlin)
- Platform: Android
- Model: gpt-4o

[Shown if Dynamic UI enabled]
## Dynamic UI
[kai-ui component catalog]
```

## Layer 3: Custom Soul (User + Auto)

The only user-editable part of the system prompt. Contains ONLY user-specific content.

### Construction

```kotlin
fun getCustomSoul(personaId: String): String {
    val user = appSettings.getSoulUser(personaId)  // user-written
    val auto = appSettings.getSoulAuto(personaId)   // auto-promoted learnings
    val parts = mutableListOf<String>()
    if (user.isNotBlank()) parts.add(user)
    if (auto.isNotBlank()) parts.add("## Behavior Notes\n$auto")
    return parts.joinToString("\n\n")
}
```

### What Goes Here

| Content | Source | Editable? |
|---------|--------|-----------|
| User preferences for AI behavior | soul_user | Yes (free text) |
| Likes/dislikes | soul_user | Yes |
| Promoted learnings | soul_auto | Read-only |
| Behavioral observations | soul_auto + HeartbeatMemoryExtractor | Read-only |

### What Does NOT Go Here (covered elsewhere)

| Content | Belongs In |
|---------|-----------|
| AI character definition ("You are a helpful assistant...") | Persona.defaultSoul |
| Tool use instructions | Technical layer |
| Memory system rules | Technical layer |
| Automation rules | Technical layer |
| Email sending policy | Technical layer |
| Honesty rule | Persona layer |
| Language instruction | Persona layer |
| Context metadata | Technical layer |
| Dynamic UI catalog | Technical layer |

## UnifiedPromptBuilder â€” Implementation

```kotlin
class UnifiedPromptBuilder(private val defaultSoul: String = "") {

    fun build(context: PromptContext): String {
        val persona = buildPersonaLayer(context)
        val technical = buildTechnicalLayer(context)
        val customSoul = buildCustomSoulLayer(context)
        return listOfNotNull(persona, technical, customSoul).joinToString("\n\n")
    }

    private fun buildPersonaLayer(context: PromptContext): String {
        // soul already contains: "You are {name}.\n\n{defaultSoul}"
        // from PersonaConfig.defaultSoul
        val parts = mutableListOf(context.soul)
        if (context.renderMode != RenderMode.UPSTREAM_COMPAT) {
            parts.add("## Language\nAdapt to the user's language...")
        }
        parts.add(DEFAULT_HONESTY_RULE)
        return parts.joinToString("\n\n")
    }

    private fun buildTechnicalLayer(context: PromptContext): String {
        // Gather all enabled technical sections
        val sections = mutableListOf<String>()
        if (context.hasTools) sections.add(buildToolUse(context))
        if (context.memoryEnabled) sections.add(buildMemoryRules(context))
        if (context.schedulingEnabled) sections.add(buildAutomation(context))
        if (context.emailAccounts.isNotEmpty()) sections.add(buildEmailPolicy(context))
        if (hasIntegrations(context)) sections.add(buildIntegrationStatus(context))
        sections.add(buildContextSection(context))
        if (context.uiMode != NONE) sections.add(buildDynamicUi(context))
        return sections.joinToString("\n\n")
    }

    private fun buildCustomSoulLayer(context: PromptContext): String? {
        // Only user-specific content, NEVER character def or technical rules
        val user = context.soulUserText
        val auto = context.soulAutoText
        val parts = mutableListOf<String>()
        if (user.isNotBlank()) parts.add(user)
        if (auto.isNotBlank()) parts.add("## Behavior Notes\n$auto")
        return parts.joinToString("\n\n").ifEmpty { null }
    }
}
```

## SoulEditor â€” Redesigned

### Data Flow

```
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”    â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”    â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚ Persona     â”‚â”€â”€â”€â†’â”‚ AppSettings      â”‚â”€â”€â”€â†’â”‚ Prompt      â”‚
â”‚ Chooser     â”‚    â”‚ persona_id       â”‚    â”‚ Builder     â”‚
â”‚ (UI)        â”‚    â”‚ soul_user_{id}   â”‚    â”‚             â”‚
â”‚             â”‚    â”‚ soul_auto_{id}   â”‚    â”‚             â”‚
â”‚ Pick Kai â†’  â”‚    â”‚ â†’ persona=kai    â”‚    â”‚ â†’ Persona   â”‚
â”‚ soul_user   â”‚    â”‚ â†’ soul_user=...  â”‚    â”‚   + Custom  â”‚
â”‚ "Prefers    â”‚    â”‚ â†’ soul_auto=...  â”‚    â”‚   + Tech    â”‚
â”‚  short reps"â”‚    â”‚                  â”‚    â”‚             â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜    â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜    â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
```

### UI Components

1. **PersonaTabRow** â€” Assistant | Operator | Custom
2. **PersonaGrid** â€” cards showing name, character type badge, language style badge, skills chips
3. **PersonaPreview** â€” read-only traits of selected persona
4. **CustomSoulEditor** â€” text field for user-written preferences (NOT character def)
5. **BehaviorNotesDisplay** â€” read-only display of auto-promoted learnings

## Migration Path

### Phase A â€” Data Model (Day 1) âœ…
- Add `BehaviorStyle`, `LanguageStyle`, `CharacterType` enums to `Persona.kt`
- Add trait fields to `PersonaConfig` (with backward-compat defaults)
- Create `PersonaCatalog.kt` with 46 default personas
- PersonaManager stays the same, just `builtIns` comes from catalog

### Phase B â€” Prompt Builder (Day 2) âœ…
- Rewrite `UnifiedPromptBuilder.kt` with 3-layer split
- `buildPersonaLayer()` â€” uses persona defaultSoul + honesty + language
- `buildTechnicalLayer()` â€” auto-generated from capabilities
- `buildCustomSoulLayer()` â€” soul_user + soul_auto only
- `buildChatSystemPrompt()` becomes thin delegation wrapper
- `HeartbeatPromptBuilder` â€” removed heartbeatStyle param, always includes Learned Patterns

### Phase C â€” SoulEditor UI (Day 3) âœ…
- Rewrite `SoulEditor` composable with persona chooser tabs
- Add persona card grid with trait badges
- Custom soul text field (user-specific only)
- Behavior notes read-only section
- Create Persona dialog with trait pickers

### Phase D â€” Cleanup (Day 4) âœ…
- `PersonaPromptStyle` enum â€” kept for backward compat in `buildChatSystemPrompt()` (test-only code path)
- `PersonaHeartbeatStyle` enum â€” removed âœ…
- `PersonaConfig.style` / `PersonaConfig.heartbeatStyle` deprecated fields â€” removed âœ…
- `DEFAULT_*` constants â€” moved to UnifiedPromptBuilder.kt âœ…
- Dead `memoryInstructions` parameter â€” removed from `buildChatSystemPrompt()` and test âœ…
- `getPersonaPromptStyle()` / `getPersonaHeartbeatStyle()` â€” removed from DataRepository interface + both implementations âœ…
- `AltMemoryClient` persona construction â€” no longer sets removed fields âœ…

### Phase E â€” Build & Release â¬œ
- Compile check, lint, build APKs
- Commit, tag, push, create release

## Upstream Compatibility

When `renderMode = UPSTREAM_COMPAT` (Kai persona):
- Persona layer matches SimonSchubert/Kai exactly
- Technical layer is minimized (no Language section, no Memory Search Guidance)
- Custom soul layer is empty (upstream doesn't have soul split)
- Total output is byte-identical to upstream

## Token Budget

| Layer | Before (KAI) | Before (ALT) | After (Unified) |
|-------|-------------|-------------|----------------|
| Persona | 200-600 | 400-800 | 200-800 |
| Technical | 1500-4000 | 1000-3000 | 1000-3000 |
| Custom Soul | â€” | â€” | 50-500 |
| **Total** | **1700-4600** | **1400-3800** | **1250-4300** |

Savings come from:
- No duplicate tool descriptions in text (tools in array only)
- No duplicated sections across styles
- Custom soul is leaner than old full soul text
