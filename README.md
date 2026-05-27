# Kai 9001

> **Android-only** fork of [Kai](https://github.com/SimonSchubert/Kai) — an open-source AI assistant with persistent memory.  
> v1.4.0 · FOSS-only · No proprietary SDKs.

<div align="center">
<img src="site/img/logo_animation.gif" height="80">
<br>
<br>
</div>

An AI assistant that remembers you, runs on-device or via 24+ LLM providers, and controls phone features through voice or text. **Fully open-source, zero telemetry, no Google Play Services required.**

## Features

### Wake Word Detection ("Hey Kai")

Hands-free activation with dual-mode detection:

- **GENERAL mode** — on-device TFLite model (TinyConv) detects "hey kai" without enrollment, ~5 MB model bundled in the APK
- **PERSONAL mode** — cosine similarity on your enrolled voice template for higher accuracy; 3-step enrollment crops to the loudest 1 second of each utterance
- **Adaptive energy baseline** — running decaying average adjusts to ambient noise (rain, fan, road noise); only processes audio when energy exceeds 2x baseline
- **Anti-flap** — service rejects restart within 2s of `onDestroy` to break false-trigger loops
- **Trigger debounce** — 3s cooldown between successive detections
- **Mic handover** — wake word pauses before SpeechRecognizer starts, restarts automatically after the AI finishes responding

### Voice Input & Response

- **Speech-to-text** via Android `SpeechRecognizer` — manual mic button or wake word triggered
- **Auto-voice-response** — when input is spoken, the AI replies using `speak_text` tool with edge-tts voices
- **Preferred language** — 27 language options (English, Arabic, Chinese, French, German, Hindi, Japanese, Korean, Spanish, and more), each mapped to its edge-tts voice; injected into the AI's system prompt so it responds in the correct language

### Persistent Memory (KaiMemPalace)

A local semantic memory graph that stores facts, preferences, and learnings across conversations:

- **Automatic recall** — the AI queries memory before each response, pulling relevant context from past sessions
- **Promotion** — memories with 5+ hits are promoted into the system prompt permanently
- **search_memories tool** — the AI can semantic-search its own memory store on demand
- **Local variant** — trimmed memory instructions for on-device models without external API access
- **Heartbeat-driven learning** — periodic self-checks review and promote memories autonomously
- **Encrypted** — all memory data stored locally with encryption

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
- **Binary file write** — non-image attachments (Excel, Word, PDF) are written to `/root/uploads/` so the AI can read them via shell commands
- **Interactive terminal** — manual command input alongside the AI
- **Secure** — sandboxed inside the app, no host system access

### 24+ LLM Providers

Multi-service with automatic failover:

Anthropic · OpenAI · Gemini · DeepSeek · Mistral · xAI · OpenRouter · Groq · NVIDIA · Cerebras · Ollama Cloud · Together AI · Hugging Face · Venice AI · Moonshot AI · Z.AI · MiniMax · AIHubMix · Deep Infra · Fireworks AI · OpenCode · OpenAI-Compatible · LiteRT On-Device · Free tier (no API key)

### On-Device Inference

Run AI models locally using LiteRT with no internet connection:

- **Qwen3 0.6B** — 614 MB model, 300 MB GPU memory, 4K default context
- **Gemma 4 E2B IT** — 2.6 GB model, 676 MB GPU memory, 4K default context
- **Gemma 4 E4B IT** — 3.7 GB model, 710 MB GPU memory, 4K default context
- Models download from HuggingFace on first use

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

### Settings & Storage

- **Encrypted conversations** — local XOR encryption with per-install key
- **Settings export/import** — full backup/restore as JSON (excludes secrets: daemon state, app opens, encryption key)
- **Customizable soul** — editable system prompt defines the AI's personality, rules, and behavior
- **Text-to-speech** — edge-tts voices per preferred language

### Splinterlands Auto-Battle (Android)

Automated Wild Ranked battles with LLM-powered team strategy. Configure priority-ordered LLM services, add Hive account, and Kai finds matches, picks teams, and submits on-chain. Falls back to a greedy picker if all services fail.

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

| Area | Upstream Kai | Kai 9001 |
|------|-------------|----------|
| Build | Proprietary SDKs (Firebase, Crashlytics, Analytics) | **FOSS-only** |
| Wake word | None | **"Hey Kai"** with GENERAL (TFLite) + PERSONAL (cosine similarity) modes |
| Voice | Manual mic button | Wake word + auto-voice-response + 27-language TTS |
| Phone integration | None | **8 phone tools** (location, contacts, battery, wifi, etc.) |
| Memory tools | Basic recall/store | **search_memories tool** + heartbeat promotion + local variant |
| Platform | Android + iOS + Desktop + Web | **Android-only** |
| Sandbox | Read-only file access | **Binary write** — AI can read Excel, Word, PDF via shell |
| Terminal | OOM with large output | **Per-item SelectionContainer** fixes ANR |
| Version | Tracks upstream (2.x.x) | **Custom 1.x.x** |
| GitHub issues | Disabled | **Integration request template** enabled |

## License

Apache License 2.0. See [LICENSE.txt](LICENSE.txt).

Based on [Kai](https://github.com/SimonSchubert/Kai) by Simon Schubert — this is a modified fork.

## Credits

- Original Kai by [SimonSchubert](https://github.com/SimonSchubert/Kai)
- Mistral: https://mistral.ai/
