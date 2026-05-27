# Kai 9001

> **Android-only** fork of [Kai](https://github.com/SimonSchubert/Kai) — an open-source AI assistant with persistent memory.  
> v1.5.0 · FOSS-only · No proprietary SDKs.

<div align="center">
<img src="site/img/logo_animation.gif" height="80">
<br>
<br>
</div>

An AI assistant that remembers you, runs on-device or via 25+ API providers, and can control phone features through text or voice. Fully open-source, zero telemetry, no Google Play Services required.

## Features

### Persistent Memory (KaiMemPalace)

A local semantic memory graph that stores facts, preferences, and learnings across conversations:

- **Automatic recall** — the AI queries memory before each response, pulling relevant context from past sessions
- **Promotion** — memories with 5+ hits are promoted into the system prompt permanently
- **search_memories tool** — the AI can semantic-search its own memory store on demand
- **Local variant** — trimmed memory instructions for on-device models without external API access
- **Heartbeat-driven learning** — periodic self-checks review and promote memories autonomously

### 25+ LLM Providers

Multi-service with automatic failover across 27 backend types:

Anthropic · OpenAI · Gemini · DeepSeek · Mistral · xAI · OpenRouter · Groq · NVIDIA · Cerebras · Ollama Cloud · Together AI · Hugging Face · Venice AI · Moonshot AI · Z.AI · MiniMax · AIHubMix · Deep Infra · Fireworks AI · OpenCode · PublicAI · OpenAI-Compatible · LiteRT On-Device · Free tier (no API key)

### On-Device Inference

Run AI models locally using LiteRT with no internet connection:

| Model | Size | GPU Memory | Context |
|-------|------|-----------|---------|
| Qwen3 0.6B | 614 MB | 300 MB | 4K/32K |
| Gemma 4 E2B IT | 2.6 GB | 676 MB | 4K/32K |
| Gemma 4 E4B IT | 3.7 GB | 710 MB | 4K/32K |

Models download from HuggingFace on first use.

### Phone Tools (Android Only)

Eight Android-specific tools, each individually toggleable in Settings:

| Tool | Permissions Required |
|------|---------------------|
| GPS location | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` |
| Contacts | `READ_CONTACTS` |
| Device info | None |
| Battery status | `BATTERY_STATS` |
| Network info | `ACCESS_NETWORK_STATE` |
| WiFi details | `ACCESS_FINE_LOCATION`, `ACCESS_WIFI_STATE` |
| Clipboard | None |
| Installed apps | `QUERY_ALL_PACKAGES` |

Tools request permissions at runtime; the user is directed to Android Settings to grant them.

### Linux Sandbox

A built-in Linux environment that the AI uses to execute shell commands, run scripts, and operate tools:

- **Alpine Linux** via [proot](https://proot-me.github.io/), no root required, ~3 MB download
- **Optional packages** — one-tap install bash, curl, wget, git, jq, python3, pip, Node.js
- **Binary file write** — non-image, non-PDF attachments (Excel, Word, etc.) are written to `/root/uploads/` so the AI can read them via shell commands
- **Interactive terminal** — manual command input alongside the AI
- **Secure** — sandboxed inside the app, no host system access

### MCP Server Support

Connect to external tool servers via the [Model Context Protocol](https://modelcontextprotocol.io/) (Streamable HTTP). Curated free servers include:

| Server | Description |
|--------|-------------|
| Fetch | Web content to markdown |
| DeepWiki | AI-powered GitHub docs |
| Sequential Thinking | Structured problem-solving |
| Context7 | Library/framework docs |
| Globalping | Network diagnostics |
| CoinGecko | Crypto prices |
| Manifold Markets | Prediction markets |
| Find-A-Domain | Domain availability (1,444+ TLDs) |

MCP servers auto-reconnect on app startup. Add custom endpoints in Settings > Tools.

### Heartbeat

Autonomous periodic self-checks that run every 30 minutes (configurable, 8am–10pm window):

- Reviews memories, pending tasks, emails, and SMS
- If everything is fine, stays silent
- If something needs attention, surfaces an in-app message or push notification
- Runs in a foreground service, survives app backgrounding

### Dynamic UI

The AI can generate interactive screens — quizzes, dashboards, recipes, brainstorms, forms — using a custom `kai-ui` markup language with buttons, inputs, lists, and real-time state. Tap buttons instead of scrolling through chat.

### Task Scheduler

Cron and one-shot scheduled tasks:

- Scheduled prompts executed by the AI at defined times
- Exponential backoff on failure (up to 1 hour)
- Cron expressions for recurring tasks
- Execution log with last 10 results

### Voice Input

Speech-to-text via Android `SpeechRecognizer` with manual mic button. Supports 27 languages for recognition. When input is spoken, the AI can reply using `speak_text` tool with edge-tts voices. Preferred language setting in General Settings injects the target language and voice into the AI's system prompt.

### Text to Speech

edge-tts voices mapped per preferred language (27 languages). The AI uses `speak_text` tool to read responses aloud.

### Settings & Storage

- **Conversations** stored as JSON in app-local preferences (plaintext, not encrypted)
- **Settings export/import** — full backup/restore as JSON (excludes: daemon state, app opens, encryption key)
- **Customizable soul** — editable system prompt defines the AI's personality, rules, and behavior

### Splinterlands Auto-Battle (Android)

Automated Wild Ranked battles with LLM-powered team strategy. Configure priority-ordered LLM services, add Hive account, and Kai finds matches, picks teams, and submits on-chain. Falls back to a greedy picker if all services fail.

---

### Experimental: Wake Word Detection ("Hey Kai")

> ⚠️ **Experimental.** May trigger on ambient noise or fail to detect in noisy environments. Accuracy depends on device hardware and ambient conditions.

Hands-free activation with dual-mode detection:

- **GENERAL mode** — on-device TFLite model detects "hey kai" without enrollment
- **PERSONAL mode** — cosine similarity on your enrolled voice template for higher accuracy; 3-step enrollment crops to the loudest 1 second of each utterance
- **Adaptive energy baseline** — running decaying average adjusts to ambient noise (rain, fan); only processes audio when energy exceeds 2x baseline
- **Anti-flap** — service rejects restart within 2s of `onDestroy` to break false-trigger loops
- **Trigger debounce** — 3s cooldown between successive detections
- **Mic handover** — wake word pauses before SpeechRecognizer starts, restarts after the AI finishes responding

---

## Quick Start

```bash
# Build (FOSS only)
.\gradlew.bat :androidApp:assembleFossDebug --no-configuration-cache

# Deploy
adb install -r androidApp\build\outputs\apk\foss\debug\androidApp-foss-debug.apk
```

## Build Dependencies

- Android SDK 34+
- JDK 17+
- No Google Play Services, no Firebase, no proprietary SDKs

## Installation

Download the APK from [GitHub Releases](https://github.com/kilvz/Kai-custom/releases).

## What's Different from Upstream

### Fork-Added Features

| Feature | Details |
|---------|---------|
| **Wake word "Hey Kai"** | Full subsystem: TFLite on-device model (GENERAL) + cosine similarity enrollment (PERSONAL), 3-step enrollment, adaptive energy baseline, anti-flap, trigger debounce, mic handover. Foreground service with `FOREGROUND_SERVICE_MICROPHONE`. Toggleable in Settings. |
| **8 Phone tools** | GPS location, contacts, device info, battery stats, network info, wifi details, clipboard, installed apps. Each individually toggleable, permission-gated per tool. |
| **`search_memories` tool** | AI can semantic-search its own memory store on demand via sandbox (kai-mempalace), falls back to FTS5. Upstream only has store/forget/learn/reinforce. |
| **`writeBinaryFile`** | Binary attachments (Excel, Word, etc.) written to sandbox at `/root/uploads/` so AI can read them via shell. Upstream only wrote text files. |
| **`speak_text` tool** | TTS via edge-tts in Linux sandbox, 27 language voices. Gated behind sandbox availability. |
| **Shizuku integration** | `run_adb` tool executes shell commands with ADB-level privileges via Shizuku. `ShizukuManager` + `ShizukuProvider` declared in manifest. |
| **`run_opencode` tool** | Delegate complex coding tasks to opencode's autonomous agent in sandbox. |
| **30+ Android permissions** | Location, contacts, phone state, camera, audio record, body sensors, activity recognition, bluetooth, external storage, WiFi state, query all packages, system alert window, install packages, read logs, biometric, vibrate. |
| **GitHub Issue button** | Links to `https://github.com/kilvz/Kai-custom/issues/new?template=integration_request.yml` in Settings > Integrations. |
| **Sponsor button** | Links to original author's GitHub Sponsors in FreeSettings card. Upstream shows full sponsor list with avatars. |
| **Notification title** | "Kai 9001" (upstream: "Kai 9000") |

### Fork-Modified Features

| Feature | Upstream | Kai 9001 |
|---------|----------|----------|
| **Build flavor** | FOSS + Play Store (Firebase, Crashlytics, Analytics) | **FOSS-only** — zero proprietary SDKs |
| **Platform targets** | Android + iOS + Desktop + Web + WasmJS | **Android-only** — other targets stubbed |
| **Memory system** | Basic recall/store, `getHeartbeatTools()` gated by scheduling, `DEFAULT_STRUCTURED_LEARNING_SECTION` | `search_memories` tool, `promote_learning` gated by memory (not scheduling), `DEFAULT_MEMORY_INSTRUCTIONS` + `DEFAULT_LOCAL_MEMORY_INSTRUCTIONS` with variant-aware selection, fixed double MemoryList rendering |
| **Terminal (SelectionContainer)** | Wraps entire `LazyColumn` — causes OOM/ANR on large output | **Per-item** `SelectionContainer` wrapping each `Text` — fixes OOM/ANR |
| **Tool gating** | `getHeartbeatTools()` gated by `isSchedulingEnabled()` | `promote_learning` gated by `isMemoryEnabled()`; phone tools, ADB, speak_text, opencode added with their own gates |
| **Conversation storage** | `loadConversations()` called from UI path only | Also called in `TaskScheduler.start()` for daemon-only path, preventing wipe from empty-list persist |
| **Think-tag stripping** | Regex `<think>.*?</think>` only | Also handles lone `</think>` without `<think>` (Qwen3-Thinking edge case) |
| **Package / identity** | `com.inspiredandroid.kai`, `Kai 9000` | `com.kai.custom`, `Kai 9001` |
| **App icon** | Purple overlapping circles | **Red** overlapping circles |
| **Version scheme** | Tracks upstream (2.x.x) | Custom 1.x.x — major = breaking, minor = features, patch = bugs |
| **GitHub issues** | Disabled | Enabled with integration request template |

### Features Present in Both (No Meaningful Changes)

- **LLM providers**: Identical — 27 providers: Free, Gemini, Anthropic, OpenAI, DeepSeek, Mistral, XAI, OpenRouter, Groq, NVIDIA, Cerebras, Ollama Cloud, LongCat, Together, HuggingFace, Venice, Moonshot, Z.AI, MiniMax, AIHubMix, Deep Infra, Fireworks AI, OpenCode, PublicAI, OpenAI-Compatible, LiteRT
- **Common tools**: Local time, IP location, URL open, web search, fetch URL, memory store/forget/learn/reinforce — structurally identical
- **SMS / Email / Calendar / Notification tools**: Identical logic
- **MCP support**: Full Streamable HTTP MCP server manager with curated free servers (Fetch, DeepWiki, Sequential Thinking, Context7, Globalping, CoinGecko, Manifold Markets, Find-A-Domain)
- **LiteRT on-device inference**: Same Qwen3 0.6B + Gemma 4 (E2B, E4B) model support
- **Heartbeat**: Same 30-minute autonomous self-check (8am–10pm), push notification on issues
- **Dynamic UI (kai-ui)**: Same interactive screen generation with buttons, inputs, forms
- **Task scheduler**: Same cron + one-shot scheduled tasks with exponential backoff
- **Sandbox base**: Same Alpine Linux via proot, shell execution, file read/write/delete
- **Settings UI**: Same tabs layout (General, Services, Agent, Integrations, Tools, Terminal, Sandbox, About), theme picker, daemon toggle
- **Encrypted prefs**: Both use `dev.spght:encryptedprefs-ktx` for app settings storage
- **Koin DI**: Same dependency injection framework

## License

Apache License 2.0. See [LICENSE.txt](LICENSE.txt).

Based on [Kai](https://github.com/SimonSchubert/Kai) by Simon Schubert — this is a modified fork.

## Credits

- Original Kai by [SimonSchubert](https://github.com/SimonSchubert/Kai)
- Mistral: https://mistral.ai/
