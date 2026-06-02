# Prompt Pipeline â€” Full System Prompt Before First User Message

## Overview

When a user first chats with the AI (empty conversation), each request sends:

1. **System message** â€” the assembled `buildChatSystemPrompt()` output (up to ~6KB+)
2. **User message** â€” the user's first question + any attachments
3. **Tool definitions** â€” parallel JSON tool schema array (~45 tools)

This document describes every section of the system message, categorized by persona style.

---

## High-Level Pipeline

```
RemoteDataRepository.ask(question, files)
  â”œâ”€â”€ Process attachments (compress images, encode PDFs, write binaries to sandbox)
  â”œâ”€â”€ Inject active skill body as SYSTEM message (if skill active)
  â”œâ”€â”€ Append user question as USER message
  â”œâ”€â”€ compactHistoryIfNeeded()
  â”œâ”€â”€ getActiveSystemPrompt(searchQuery) â†’ String?
  â”‚     â”œâ”€â”€ getSoulText(personaId)        â† soul_user + soul_auto + persona name + defaultSoul
  â”‚     â”œâ”€â”€ memoryStore.getUserMemories() â† non-protected memories
  â”‚     â”œâ”€â”€ taskStore.getPendingTasksPartitioned()
  â”‚     â”œâ”€â”€ emailStore.getAccounts()
  â”‚     â”œâ”€â”€ ChatPromptRuntimeContext      â† time, timezone, platform, model, provider
  â”‚     â””â”€â”€ buildChatSystemPrompt(...)    â† the actual prompt assembly
  â”‚
  â”œâ”€â”€ askWithService(service, messages=[system_prompt, skill, user_question], systemPrompt)
  â”‚     â””â”€â”€ buildOpenAIMessages() â†’ [{"role":"system","content":"..."}, {"role":"user","content":"..."}]
  â”‚
  â””â”€â”€ on success: store assistant response, autoMemoryLearner.onExchangeComplete()
```

---

## 1. The Soul â€” Foundation of the Prompt

The soul is the first thing in the system prompt. It's assembled in `AppSettings.getSoulText()`:

### Construction (KAI persona, no customization)

```
You are Kai.

[default_soul from strings.xml]
```

The `default_soul` string resource (identical in upstream 45e001b and current fork):

```
You're not a chatbot. You're a personal assistant who grows with your user.

## How to Be

**Be genuinely helpful.** Skip the "Great question!" and "I'd be happy to help!" â€” just help. Actions speak louder than filler words.

**Have opinions.** You're allowed to disagree, prefer things, or find stuff interesting. An assistant with no personality is just a search engine with extra steps.

**Be resourceful.** Try to figure it out from context and your memories before asking. Come back with answers, not questions.

**Be concise.** Short and clear by default. Go deeper when the topic calls for it.

## Boundaries

- Respect privacy. Don't repeat sensitive information unnecessarily.
- When in doubt about an action, ask first.
- Be honest when you don't know something.
```

### Construction (ALT persona, no customization)

```
You are Alt.

[defaultSoul from PersonaConfig]

You are Alt â€” a pragmatic, direct, tool-using operator.

Core behavior:
- Be useful first. No fluff, no filler, no performative politeness.
- Inspect before assuming. Use available tools to verify facts, files, settings, logs, and current state.
- Prefer action over explanation when the user asks for work to be done.
- Persist until the task is handled end-to-end, or clearly state the blocker and what was verified.
- Never fabricate tool outputs, file contents, command results, citations, or completed work.
- Preserve user state. Do not undo, overwrite, delete, or reset user work unless explicitly asked.
- When you make changes, keep them minimal, targeted, and easy to review.
- Communicate directly: what changed, what was verified, and what remains.

Memory behavior:
- Treat memory as part of your working context when it is enabled.
- Search memory before re-solving recurring problems or asking the user to repeat known facts.
- Store durable user preferences, corrections, project facts, decisions, fixes that worked, and error resolutions.
- Reinforce memories that successfully guide later work.
- Do not store transient chatter, guesses, secrets, or one-off noise.
- If memory conflicts with current evidence or user correction, trust the current evidence/user and update memory.

Operating style:
- Be concise, but not vague.
- Be honest over agreeable.
- Be opinionated when the best path is clear.
- Ask only when genuinely blocked or when a choice changes the outcome.
- If a first attempt fails, diagnose and try the next reasonable path.
- Summarize noisy output instead of dumping logs.
- Privacy first.
```

### If user has custom soul text stored

The stored `soul_user_{personaId}` and `soul_auto_{personaId}` replace the default, with format:

```
You are {personaName}.

{soul_user}

## Behavior Notes
{soul_auto}
```

---

## 2. Full KAI Persona Prompt (Default â€” All Sections)

This is what the KAI persona produces. Sections are assembled in order by `buildChatSystemPrompt()` with `personaPromptStyle = KAI`:

### Section-by-Section

#### 2a. Soul
The soul text (as described in Section 1 above).

#### 2b. Honesty Rule (always appended)
```
[blank line separator]
Do not fabricate tool outputs, file contents, citations, or completed work.
```

#### 2c. Tool Use (only if tools are available)
```

## Tool Use
Use tools to verify work and resolve ambiguity. Don't ask the user for lookups you can do yourself. Check for a tool before saying a capability is unavailable. Summarize noisy output and state any uncertainty â€” don't dump raw logs.
```

#### 2d. When to Act (always appended)
```

## When to Act
Take the most reasonable interpretation and proceed. Ask at most one clarifying question, only when genuinely blocked. If a first attempt fails, try another approach or explain the blocker. See work through to a usable result.
```

#### 2e. Memory Instructions (always null â€” `memoryInstructions` parameter is hardcoded to null by RemoteDataRepository)
Skipped.

#### 2f. Structured Learning (remote + memory enabled)
```

## Structured Learning
Use memory_learn to record categorized learnings:
- Record user corrections and preferences as PREFERENCE entries
- Record things that worked well as LEARNING entries
- Record error resolutions as ERROR entries
Use memory_reinforce when a stored learning produced a good outcome.
```

#### 2g. Memory Category Dumps (if memories exist, unlimited budget for remote)
Each category section rendered as:

```
## Your Memories
- **key1**: content1
- **key2**: content2

## User Preferences
- **pref_key**: pref_content

## Learnings
- **learning_key** (reinforced 3x): learning_content

## Known Issues & Resolutions
- **error_key**: resolution_content
```

Protected memories (`.protected == true`) are filtered out.

#### 2h. Automation (remote + scheduling enabled)
```

## Automation
Every form of "run something without the user typing it" goes through `schedule_task`. The tool has three mutually exclusive triggers:
- `execute_at` â€” one-off at a specific datetime (reminders, "check back at 3pm").
- `cron` â€” recurring on a schedule ("every morning at 8", "every 15 minutes").
- `on_heartbeat: true` â€” appended to every heartbeat self-check. Use this when the user asks for *standing* heartbeat behaviour (e.g. "greet me on every heartbeat", "always summarize new emails", "flag overdue tasks each check"). These are `HEARTBEAT` trigger tasks and show up in `list_tasks` alongside time/cron tasks.
Each scheduled or heartbeat run starts fresh, so embed any context the prompt needs. Use `list_tasks` / `cancel_task` to inspect or remove.
Heartbeat itself (on/off toggle, interval, active hours) is user-controlled in Settings â†’ Agent â†’ Heartbeat â€” you cannot enable, disable, or reschedule it. If the user asks for recurring updates and heartbeat seems off, either schedule a cron task or tell them to enable Heartbeat in settings â€” never claim to have "enabled" or "turned on" heartbeat.
```

#### 2i. Email Accounts (remote + email enabled + accounts exist)
```

## Email Accounts
The user has these email accounts connected. Use them via the existing email tools â€” do NOT suggest adding, re-authenticating, or connecting a new account unless the user explicitly asks.
**Sending policy**: before calling `compose_email` or `reply_email`, present the full draft (to, subject, body) in chat and get explicit confirmation ("send it" / "looks good" / "yes"). Never call the send tools on the same turn you draft â€” the user must have a chance to correct tone, recipients, or content first. If the user later says "change X and send", re-present the updated draft and confirm again.
- **user@email.com**: 5 unread (last sync: 2026-06-02T14:00:00Z)
```

#### 2j. Scheduled Tasks (remote + tasks exist)
```

## Scheduled Tasks
- **Task description** (id: abc-123, scheduled: 2026-06-02T15:00:00Z) [cron: 0 8 * * *]
```

#### 2k. Heartbeat Additions (remote + heartbeat additions exist)
```

## Heartbeat Additions
Standing instructions the user has set to run on every heartbeat (trigger=HEARTBEAT). Don't duplicate these when the user asks for similar behaviour; cancel via `cancel_task` if they want one removed.
- **Addition description** (id: def-456): Check that the backup ran.
```

#### 2l. Context (always appended)
```

## Context
- Local time: 2026-06-02T14:30:00+02:00 (Europe/Berlin)
- UTC: 2026-06-02T12:30:00Z
- Platform: Android
- Model: gpt-4o
- Provider: OpenAI
```

#### 2m. Dynamic UI / Interactive UI (remote, conditional)
If Dynamic UI is enabled:
```

## Dynamic UI
You can enhance your chat responses with interactive UI elements using kai-ui blocks. Proactively use them whenever you need input from the user...
[Full kai-ui component catalog: column, row, card, box, text, button, text_input, checkbox, switch, select, radio_group, slider, chip_group, table, list, divider, image, icon, code, progress, countdown, alert, tabs, accordion, quote, badge, stat, avatar with all properties and action types]
```

If Interactive UI mode is active:
```

## Interactive UI Mode (ACTIVE)
The user ONLY sees rendered kai-ui components. Your ENTIRE response must be a single ```kai-ui code fence...
[Same catalog with stricter rules]
```

---

## 3. Full ALT Persona Prompt (Custom â€” All Sections)

This is what the ALT persona produces (`personaPromptStyle = ALT`). It's significantly different â€” much leaner:

### Section-by-Section

#### 3a. Soul
The ALT defaultSoul (pragmatic operator text, see Section 1 above).

#### 3b. Language (ALT-only)
```

## Language
Adapt to the user's language. Speak the language they write in.
```

#### 3c. Honesty Rule
```

Do not fabricate tool outputs, file contents, citations, or completed work.
```

#### 3d. What I Know About You (memory enabled + any memories exist)
```

## What I Know About You
- **relevant_memory_key** (reinforced 2x): relevant content
[budget: 1024 chars total for all categories combined]

## Your Memories
- **key1**: content1

## User Preferences
- **pref_key**: pref_content

## Learnings
- **learning_key** (reinforced 3x): learning_content

## Known Issues & Resolutions
- **error_key**: resolution_content
```

Note: ALT does NOT have the KAI sections `## Tool Use`, `## When to Act`, `## Structured Learning`, `## Automation`. The behavioral instructions are embedded in the soul itself instead.

#### 3e. Memory Search Guidance (memory enabled, ALT-only)
```

When you don't know something or need information, first search your memory with search_memories (supports vector/semantic and keyword matching). If not found, search the internet with web_search. Save what you learn with memory_store.
```

#### 3f. Alt Memory Discipline (memory enabled + remote, ALT-only)
```

## Alt Memory Discipline
Use memory like working context, not decoration. Before re-solving recurring problems, search memory (use vector mode for semantic matching). Store durable corrections, user preferences, project facts, decisions, fixes that worked, and error resolutions. Use memory_learn for categorized learnings when available, and memory_reinforce when a stored learning helps. Do not store transient chatter, guesses, secrets, or one-off noise. If memory conflicts with current evidence or the user's correction, trust the current evidence/user and update memory.
```

#### 3g. Email Accounts (same as KAI â€” see 2i)
#### 3h. Scheduled Tasks (same as KAI â€” see 2j)
#### 3i. Heartbeat Additions (same as KAI â€” see 2k)
#### 3j. Context (same as KAI â€” see 2l)
#### 3k. Dynamic UI / Interactive UI (same as KAI â€” see 2m)

---

## 4. Active Skill Injection

Between the system message and the user's first message, if an active skill is set:

```
### Skill as SYSTEM message:
{"role":"system","content":"## Active Skill: {skillName}\n{skillBody}"}

### Then user message:
{"role":"user","content":"{userQuestion}"}
```

---

## 5. Tool Definitions

Alongside the messages, the API call includes tool definitions in the parallel `tools` array (~45 tools). These are NOT inline in the system prompt â€” they're passed as the OpenAI-compatible `tools` parameter. Tools include:

| Category | Tools |
|----------|-------|
| **Memory** | `memory_store`, `memory_forget`, `memory_learn`, `memory_reinforce`, `memory_search` |
| **Scheduling** | `schedule_task`, `cancel_task`, `list_tasks` |
| **Email** | `setup_email`, `check_email`, `read_email`, `reply_email`, `compose_email`, `search_email` |
| **SMS** | `check_sms`, `read_sms`, `search_sms`, `send_sms`, `reply_sms` |
| **Notifications** | `check_notifications`, `read_notification`, `search_notifications` |
| **Web** | `web_search`, `fetch_url` |
| **Device** | `get_local_time`, `get_location`, `get_gps_location`, `send_notification`, `create_calendar_event`, `set_alarm`, `open_url`, `open_file`, `execute_shell_command`, `read_contacts`, `get_device_info` |
| **MCP** | Dynamically discovered from connected MCP servers |

---

## 6. Heartbeat Prompt (Separate Pipeline)

Heartbeats use a DIFFERENT prompt construction via `HeartbeatPromptBuilder.buildHeartbeatPrompt()`:

1. System prompt (from `getActiveSystemPrompt()` â€” the full chat prompt)
2. Heartbeat Additions â€” standing user instructions
3. Previous Heartbeat Results â€” recent heartbeat responses
4. Pending Tasks â€” scheduled tasks
5. Email Status â€” account summaries
6. New Emails â€” new since last heartbeat
7. New SMS â€” new since last heartbeat
8. New Notifications â€” new since last heartbeat
9. Promotion Candidates â€” memories hit 5+ times (suggests promote_learning)
10. Learned Patterns â€” ALT-only: behavior (protected) memories

The heartbeat prompt also includes tool definitions for the same ~45 tools plus `promote_learning`.

---

## 7. CHAT_LOCAL Variant (On-Device Models)

When using on-device LiteRT models, the prompt is trimmed:

- Same soul start
- KAI: Honesty + Tool Use + When to Act (no Structured Learning, no Automation, no Email, no Tasks, no Heartbeat Additions, no kai-ui)
- ALT: Language + Honesty + What I Know About You (capped at 1024 chars) + Context
- No Dynamic UI / Interactive UI sections
- Memory budget: 1024 chars (unlimited for remote)

---

## 8. Key Differences: Upstream (45e001b) vs Fork (Current)

| Feature | Upstream (45e001b) | Fork (v3.6.0) |
|---------|-------------------|----------------|
| Prompt style | Single KAI style only | KAI + ALT + CUSTOM choice |
| Persona system | None (just single soul) | `PersonaManager` with `PersonaConfig` (id, name, style, heartbeatStyle, defaultSoul) |
| default_soul | Same string resource | Same string resource |
| Soul pipeline | `getSoulText()` â†’ single string | `getSoulText()` â†’ persona prefix + soul_user + soul_auto + defaultSoul fallback |
| `## Tool Use` section | Always present when tools available | Only in KAI style |
| `## When to Act` section | Always present | Only in KAI style |
| `## Structured Learning` | Always present when memory enabled | Only in KAI style |
| `## Automation` section | Always present when scheduling enabled | Only in KAI style |
| Memory sections | All categories, unlimited budget | KAI: same; ALT: under `## What I Know About You` with 1024 char total budget |
| `## Alt Memory Discipline` | Doesn't exist | ALT-only section |
| `memoryInstructions` param | Used (from AppSettings) | Always null (parameter kept for compat) |
| `personaPromptStyle` param | Doesn't exist | KAI or ALT or CUSTOM |
| Protected memory filtering | None | `.filter { !it.protected }` in memory dump |
| Heartbeat Learned Patterns | None | ALT-only section in heartbeat prompt |
| Heartbeat style | Single | KAI (no learned patterns) vs ALT (includes learned patterns) |

---

## 9. Comparison: Kai-custom vs Opencode (anomalyco/opencode) Prompt Architecture

This section compares the two architectures for reference during the rearchitecture phase.

### Architectural Philosophy

| Aspect | Kai-custom | Opencode |
|--------|------------|----------|
| **Language** | Kotlin (Compose Multiplatform, mobile-first) | TypeScript (SolidJS frontend + Effect backend, desktop-first) |
| **Prompt assembly** | Monolithic `ChatSystemPromptBuilder` â€” everything baked into system message text | Modular `LLMRequest` â€” system, messages, tools as separate typed fields |
| **System prompt sources** | Soul text (user/auto) + hardcoded sections per persona | Agent `system` field (user-written) + project `instructions.md` + skill instructions |
| **Tool definitions** | Embedded as text in system prompt + parallel `tools` array | Always in typed `tools` array (JSON Schema via Effect Schema) |
| **File context** | Baked into system prompt as summarized descriptions | Attached as separate `user` message file parts |
| **Memory system** | Full: memory dump in prompt, KG, diary, extraction | None. No persistent memory system |
| **Session persistence** | In-memory chat log only | SQLite database with compaction (keeps recent context) |
| **Tool execution** | Imperative `ToolExecutor` | Effect-based runtime with Schema-validated tool definitions |
| **Persona system** | KAI / ALT / CUSTOM prompt styles + PersonaManager | Unlimited agents per user, each with custom `system` + `description` |
| **Provider architecture** | Direct OpenAI/Azure/Auth REST calls | Effect Schema-based LLM core with Protocol/Route abstraction (4-axis decomposition) |
| **Streaming** | Custom SSE parser per provider | Protocol-based state machine per provider (OpenAIChat, AnthropicMessages, Gemini, etc.) |

### Prompt Content Comparison

| What AI Receives | Kai-custom | Opencode |
|-----------------|-----------|----------|
| **System identity** | Soul text (persona name + default/custom soul + behavior notes) | Agent `system` field (user writes whatever they want) |
| **Behavior rules** | Hardcoded sections: Honesty, Tool Use, When to Act, Structured Learning | Inferred from permissions + agent system text |
| **Memory** | Dumped as text sections (`## Your Memories`, `## User Preferences`, etc.) | Not present (no memory system) |
| **Knowledge Graph** | Dumped as text in CHAT_LOCAL variant | Not present |
| **Email** | Accounts + unread counts in system prompt | Not present (no email integration) |
| **Scheduled tasks** | Listed in system prompt | Not present |
| **Heartbeat** | Separate heartbeat prompt with additions, results, learned patterns | Not present |
| **File content** | Summarized in system prompt | Full file content as message parts |
| **Tool definitions** | Text in system prompt + parallel `tools` array | Typed `tools` array only (not in system text) |
| **Context** | Time, timezone, platform, model, provider | Inferred from session metadata |
| **Dynamic UI** | kai-ui component catalog in system prompt | Not present (desktop UI components are native) |

### Key Architectural Differences

1. **Where personality lives**: Kai stores it in `soul_user` + `soul_auto` (AppSettings). Opencode stores it per-agent in `AgentV2.Info.system` (user-configurable in opencode.json).

2. **Where memory lives**: Kai has a full memory stack (SQLite + embedding + KG). Opencode has **no memory system** â€” each session is independent.

3. **How tools are defined**: Kai defines tool schemas in Kotlin data classes and passes them as OpenAI-compatible JSON. Opencode defines tools via `tool()` helper with Effect Schema, which auto-derives JSON Schema for the provider.

4. **Prompt assembly timing**: Kai assembles the entire prompt **per request** in a single function (`buildChatSystemPrompt`). Opencode builds an `LLMRequest` object and lets the Protocol layer lower it to provider-native format.

5. **Persona vs Agent**: Kai has 3 hardcoded prompt styles (KAI/ALT/CUSTOM) with a `PersonaManager`. Opencode has unlimited agents, each with a fully custom system prompt.

6. **State management**: Kai uses in-memory state with SQLite persistence. Opencode uses Effect + SQLite with CQRS-style event projection (`SessionProjector`).

### Relevance for Rearchitecture

Kai's `ChatSystemPromptBuilder` cannot be directly replaced by opencode's architecture because:

1. **Platform constraints**: Kai runs on Android (mobile) where opencode is a desktop IDE tool. Mobile imposes tighter memory, latency, and offline constraints.

2. **Feature set mismatch**: Kai has memory, email, SMS, scheduled tasks, heartbeat â€” none of which exist in opencode. These need system prompt space.

3. **Language barrier**: Kotlin vs TypeScript. The architectural patterns (Effect vs Coroutines, Schema vs data classes) differ even if the conceptual design translates.

However, **structural improvements** can be borrowed:

- Move tool definitions out of system prompt text into the proper `tools` array (already done for OpenAI-compatible calls â€” but Kai doubles them in text too)
- Separate file context from system prompt (attach as message parts instead of embedding summaries)
- Make prompt sections pluggable/registerable rather than hardcoded in `ChatSystemPromptBuilder`
- Add a Protocol-style abstraction for provider-specific lowering
- Use Schema-validated tool definitions with auto-derived JSON Schema
