# Kai-custom

An **open-source AI assistant with persistent memory** that runs on **Android, iOS, Windows, Mac, Linux, and Web** â€” with Android as the primary target.

[:material-download: Get Started](getting-started.md){ .md-button .md-button--primary }
[:material-github: GitHub](https://github.com/kilvz/Kai-custom){ .md-button }

## Overview

Kai-custom is a public FOSS fork of [Kai](https://github.com/SimonSchubert/Kai). It connects to 24+ LLM providers with automatic fallback, remembers important details across conversations, can act autonomously via scheduled heartbeats, and runs tools in a Linux sandbox. This fork adds wake word activation, SSH tooling, Shizuku/root tools, two-tier memory, persona management, a debug API, automatic memory extraction, and more.

Current version: **v3.6.4** â€” based on upstream v2.7.0.

## Key Features

- **Two-tier memory** â€” User facts + protected behavior learnings, with auto-extraction and reinforcement
- **Customizable soul** â€” Split into user-edited text and auto-generated behavior notes
- **Persona system** â€” Named persona profiles with editable descriptions
- **Wake word** â€” "Hey Kai" keyword spotting on Android
- **Multi-service fallback** â€” 24+ providers with automatic failover, plus LiteRT on-device inference
- **Tool execution** â€” Web search, notifications, calendar, SMS, contacts, shell commands, SSH, ADB, and more
- **Linux sandbox** â€” Alpine Linux via proot for shell commands and scripts (Android)
- **SSH tooling** â€” Profile management, session lifecycle, terminal UI
- **Autonomous heartbeat** â€” Periodic self-checks that surface anything needing attention
- **Skills system** â€” Installable skill packages from GitHub or marketplace
- **Debug API** â€” 50+ HTTP endpoints for development
- **Encrypted storage** â€” Conversations stored locally with encryption
- **Text to speech** â€” Listen to AI responses

## How It Works

```
                    â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”
                    â”‚  User  â”‚
                    â””â”€â”€â”€â”¬â”€â”€â”€â”€â”˜
                        â”‚ message
                        â–¼
           â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
           â”‚          Chat           â”‚
           â”‚                         â”‚
           â”‚  prompt + memories      â”‚
           â”‚        â”‚                â”‚
           â”‚        â–¼                â”‚
           â”‚    â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”           â”‚
           â”‚    â”‚   AI   â”‚â—€â”€â”        â”‚
           â”‚    â””â”€â”€â”€â”¬â”€â”€â”€â”€â”˜  â”‚        â”‚
           â”‚        â”‚   tool calls   â”‚
           â”‚        â”‚   & results    â”‚
           â”‚        â–¼      â”‚        â”‚
           â”‚    â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â” â”‚        â”‚
           â”‚    â”‚ Tools  â”‚â”€â”˜        â”‚
           â”‚    â””â”€â”€â”€â”¬â”€â”€â”€â”€â”˜          â”‚
           â”‚        â”‚               â”‚
           â””â”€â”€â”€â”€â”€â”€â”€â”€â”¼â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
                    â”‚ store / recall
                    â–¼
           â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”   AutoMemoryLearner
           â”‚     Memory      â”‚â”€â”€ every 5 exchanges â”€â”€â–º unprotected memories
           â”‚                 â”‚
           â”‚  facts, prefs,  â”‚   HeartbeatMemoryExtractor
           â”‚  learnings      â”‚â”€â”€ post-heartbeat â”€â”€â–º protected memories
           â”‚                 â”‚
           â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
                    â–²
                    â”‚ reviews
                    â”‚
           â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
           â”‚    Heartbeat    â”‚
           â”‚                 â”‚
           â”‚  autonomous     â”‚
           â”‚  self-check     â”‚
           â”‚  every 30 min   â”‚
           â”‚  (8amâ€“10pm)     â”‚
           â”‚                 â”‚
           â”‚  all good?      â”‚
           â”‚  â†’ stays silent â”‚
           â”‚  needs action?  â”‚
           â”‚  â†’ notifies userâ”‚
           â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
```

- **Chat** â€” User sends a message. The AI responds, calling tools (memory, web search, shell, SSH, etc.) in a loop until it has a final answer.
- **Memory** â€” The AI stores and recalls facts, preferences, and learnings. Every 5 exchanges, AutoMemoryLearner extracts new facts automatically. After heartbeats, HeartbeatMemoryExtractor stores behavior patterns as protected memories.
- **Heartbeat** â€” A background self-check runs every 30 minutes. It reviews memories, pending tasks, and emails. If something needs attention, it notifies the user. Otherwise, it stays silent.

## Supported Services

**[Atlas Cloud](https://www.atlascloud.ai)** Â· [Anthropic](https://console.anthropic.com) Â· [OpenAI](https://openai.com) Â· [Gemini](https://aistudio.google.com) Â· [DeepSeek](https://www.deepseek.com) Â· [Mistral](https://mistral.ai) Â· [xAI](https://x.ai) Â· [OpenRouter](https://openrouter.ai) Â· [Groq](https://groq.com) Â· [NVIDIA](https://developer.nvidia.com) Â· [Cerebras](https://cerebras.ai) Â· [Ollama Cloud](https://ollama.com) Â· [LongCat](https://longcat.chat) Â· [Together AI](https://together.ai) Â· [Hugging Face](https://huggingface.co) Â· [Venice AI](https://venice.ai) Â· [Moonshot AI](https://moonshot.cn) Â· [Z.AI](https://z.ai) Â· [MiniMax](https://minimax.io) Â· [AIHubMix](https://aihubmix.com) Â· [Deep Infra](https://deepinfra.com) Â· [Fireworks AI](https://fireworks.ai) Â· [OpenCode](https://opencode.ai) Â· OpenAI-Compatible API Â· LiteRT On-Device (Android) Â· Free tier (no API key needed)

## Platforms

| Platform | Distribution |
|---|---|
| Android | GitHub Releases APK |
| iOS | Target exists, not actively tested |
| macOS | Best-effort via CI (continue-on-error) |
| Windows | Best-effort via CI (continue-on-error) |
| Linux | Best-effort via CI (DEB, RPM, AppImage, TAR â€” continue-on-error) |
| Web | WasmJs target exists, not actively tested |

## Documentation

- **[Getting Started](getting-started.md)** â€” Installation and first steps
- **[Chat & Conversations](features/chat.md)** â€” Message history, conversation persistence, image attachments, and speech output
- **[Multi-Service](features/multi-service.md)** â€” Provider configuration, fallback chain, and connection validation
- **[Memory System](docs/memory-system-architecture.md)** â€” Two-tier memory, soul split, personas, auto-learning
- **[Tools](features/tools.md)** â€” Available tools, execution flow, safety guards, and enablement
- **[Heartbeat](features/heartbeat.md)** â€” Autonomous self-checks, active hours, and configuration
- **[Tasks](features/tasks.md)** â€” Scheduled tasks, future execution, and task management
- **[Daemon](features/daemon.md)** â€” Background service for scheduled tasks and heartbeat execution
- **[Debug API](debug-api.md)** â€” 50+ HTTP endpoints for development
- **[Codebase Audit](codebase-audit-2026-06-02.md)** â€” Full codebase analysis and known issues

## Links

- [GitHub Repository](https://github.com/kilvz/Kai-custom)
- [Issue Tracker](https://github.com/kilvz/Kai-custom/issues)
- [Releases](https://github.com/kilvz/Kai-custom/releases)
- [Upstream Kai](https://github.com/SimonSchubert/Kai)
