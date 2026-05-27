# Kai 9001 — kilvz/custom

> **Android-only fork** of [Kai](https://github.com/SimonSchubert/Kai) — an open-source AI assistant with persistent memory.  
> **v1.4.0** (based on Kai v2.6.3) · FOSS build only.

<img src="https://img.shields.io/badge/Platform-Android-34a853.svg?logo=android" alt="Android" />

<div align="center">
<br>
<img src="site/img/logo_animation.gif" height="80">
<br>
<br>
</div>

## What's Different from Upstream Kai

| Feature | Upstream Kai | Kai 9001 |
|---------|-------------|----------|
| Build flavor | Proprietary SDKs (Firebase, Play Services, Crashlytics, Analytics) | **FOSS-only** — zero proprietary SDKs |
| Wake word | None | **"Hey Kai" wake word** — dual mode: GENERAL (TFLite on-device model) + PERSONAL (cosine similarity on enrolled voice template), 3-step enrollment in Settings |
| Voice input | Manual mic button only | Wake word + manual mic **handover** — wake word pauses before STT, restarts after AI responds |
| Voice response | None | **Auto-voice-response** — when input is spoken, AI replies via speak_text tool with edge-tts |
| Language | Hardcoded UI translation | **Preferred language setting** — 27 languages with edge-tts voice mapping injected into AI system prompt |
| Phone tools | None | **8 Android phone tools**: GPS location, contacts, device info, battery stats, network info, wifi details, clipboard, installed apps — each individually toggleable |
| Sandbox file write | Read-only access to uploaded files | **Binary file write** — non-image attachments (Excel, Word, PDF) written to sandbox at `/root/uploads/` so AI can read them via shell |
| Memory tools | Basic recall/store | **search_memories tool** — AI can semantic-search its own memory store; **memory promotion via heartbeat**; **local memory variant** for on-device models |
| Terminal | SelectionContainer wrapping entire LazyColumn causes OOM/ANR | **Per-item SelectionContainer** — fixes OOM crash when selecting large terminal output |
| Platform support | Android + iOS + Desktop + Web | **Android-only** — other platform targets removed or stubbed |
| Version scheme | Tracks upstream (2.x.x) | **Custom 1.x.x** — major = breaking/architecture, minor = features, patch = bug fixes |
| Sponsor | None | **Sponsor button** in Settings linking to original author |
| GitHub issues | Disabled on upstream | **Enabled with integration request template** |

## Quick Start

```bash
# Build
.\gradlew.bat :androidApp:assembleFossDebug --no-configuration-cache

# Deploy
adb install -r androidApp\build\outputs\apk\foss\debug\androidApp-foss-debug.apk
```

## Features (Common with Upstream)

- **Persistent memory** — facts, preferences, and learnings stored across conversations
- **Customizable soul** — editable system prompt defines AI personality
- **24 LLM providers** — Anthropic, OpenAI, Gemini, DeepSeek, Mistral, xAI, OpenRouter, and more
- **On-device inference** — LiteRT local models (Qwen3, Gemma 4), no internet needed
- **Tool execution** — web search, notifications, shell commands, and more
- **MCP server support** — Streamable HTTP MCP endpoints for remote tools
- **Autonomous heartbeat** — periodic self-checks, notifies on issues
- **Linux Sandbox (Android)** — Alpine Linux via proot, run scripts and tools securely
- **Dynamic UI** — AI generates interactive screens (quizzes, dashboards, forms)
- **Encrypted storage** — local conversation encryption
- **Task scheduler** — cron and one-shot scheduled tasks
- **Splinterlands auto-battle** — automated Wild Ranked battles with LLM team strategy

## Installation

Download the APK from [GitHub Releases](https://github.com/kilvz/Kai-custom/releases).

## Build Dependencies

- Android SDK 34+
- JDK 17+
- No Google Play Services, no Firebase, no proprietary SDKs

## License

Apache License 2.0. See [LICENSE.txt](LICENSE.txt).

Based on [Kai](https://github.com/SimonSchubert/Kai) by Simon Schubert — this is a modified fork.

## Credits

- Original Kai by [SimonSchubert](https://github.com/SimonSchubert/Kai)
- Mistral: https://mistral.ai/
