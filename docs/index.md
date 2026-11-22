# Kai-custom

An **open-source AI assistant with persistent memory** that runs on **Android, iOS, Windows, Mac, Linux, and Web** â€” with Android as the primary target.

[:material-download: Get Started](getting-started.md){ .md-button .md-button--primary }
[:material-github: GitHub](https://github.com/kilvz/Kai-custom){ .md-button }

## Overview

Kai-custom is a public FOSS fork of [Kai](https://github.com/SimonSchubert/Kai). It connects to 32 LLM providers (plus a keyless Free tier and on-device models) with automatic fallback, remembers important details across conversations, can act autonomously via scheduled heartbeats, and runs tools in a Linux sandbox. This fork adds wake word activation, SSH tooling, Shizuku/root tools, two-tier memory, persona management, a debug API, automatic memory extraction, and more.

Current version: **v4.0.0** â€” based on upstream v3.3.0.

## Key Features

- **Two-tier memory** â€” User facts + protected behavior learnings, with auto-extraction and reinforcement
- **Customizable soul** â€” Split into user-edited text and auto-generated behavior notes
- **Persona system** â€” Named persona profiles with editable descriptions
- **Wake word** â€” "Hey Kai" keyword spotting on Android
- **Multi-service fallback** â€” 32 providers with automatic failover, plus LiteRT on-device inference
- **Tool execution** â€” Web search, notifications, calendar, SMS, contacts, shell commands, SSH, ADB, and more
- **Linux sandbox** â€” Alpine Linux via proot for shell commands and scripts (Android)
- **SSH tooling** â€” Profile management, session lifecycle, terminal UI
- **Kai Build** â€” An Android coding environment with terminals and coding agents (Claude Code, Grok, OpenCode)
- **Autonomous heartbeat & scheduled tasks** â€” Periodic self-checks plus one-time or recurring tasks the AI schedules
- **MCP server support** â€” Connect to remote tool servers via the Model Context Protocol
- **Interactive UI** â€” The AI can answer with interactive screens (forms, buttons, multi-step flows) instead of plain text
- **Skills system** â€” Installable skill packages from GitHub or marketplace
- **Debug API** â€” 50+ HTTP endpoints for development
- **Encrypted storage** â€” Conversations stored locally with encryption
- **Settings export/import** â€” Back up and restore all settings as a JSON file
- **Text to speech** â€” Listen to AI responses
- **Image attachments** â€” Attach images to any conversation

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

**[Atlas Cloud](https://www.atlascloud.ai?utm_source=github&utm_medium=link&utm_campaign=Kai)** Ã‚Â· [Anthropic](https://console.anthropic.com) Ã‚Â· [OpenAI](https://openai.com) Ã‚Â· [Gemini](https://aistudio.google.com) Ã‚Â· [DeepSeek](https://www.deepseek.com) Ã‚Â· [Mistral](https://mistral.ai) Ã‚Â· [xAI](https://x.ai) Ã‚Â· [OpenRouter](https://openrouter.ai) Â· [Requesty](https://requesty.ai) Ã‚Â· [Groq](https://groq.com) Ã‚Â· [NVIDIA](https://developer.nvidia.com) Ã‚Â· [Cerebras](https://cerebras.ai) Ã‚Â· [Ollama Cloud](https://ollama.com) Ã‚Â· [LongCat](https://longcat.chat) Ã‚Â· [Together AI](https://together.ai) Ã‚Â· [Hugging Face](https://huggingface.co) Ã‚Â· [Venice AI](https://venice.ai) Ã‚Â· [Moonshot AI](https://moonshot.cn) Ã‚Â· [Z.AI](https://z.ai) Ã‚Â· [MiniMax](https://minimax.io) Ã‚Â· [AIHubMix](https://aihubmix.com) Ã‚Â· [Deep Infra](https://deepinfra.com) Ã‚Â· [Fireworks AI](https://fireworks.ai) Ã‚Â· [OpenCode](https://opencode.ai) Â· [Public AI](https://publicai.co) Â· SpAIder Â· [AI Horde](https://stablehorde.net/) Â· [1min.AI](https://1min.ai) Â· [Perplexity](https://www.perplexity.ai) Ã‚Â· OpenAI-Compatible API Ã‚Â· LiteRT On-Device (Android) Ã‚Â· Free tier (no API key needed)

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
- **[MCP Servers](features/mcp.md)** â€” Remote tool servers over the Model Context Protocol
- **[Dynamic UI](features/dynamic-ui.md)** â€” AI-generated interactive screens rendered inline in chat
- **[On-Device Inference](features/on-device-inference.md)** â€” Local LiteRT models, downloads, and integrity checks
- **[Reasoning](features/reasoning.md)** â€” How reasoning traces are sent back to providers and shown
- **[System Prompts](features/system-prompts.md)** â€” How each prompt variant is composed
- **[Skills](features/skills.md)** â€” Installable SKILL.md instruction bundles
- **[Linux Sandbox](features/sandbox.md)** â€” On-device Debian/Alpine shell for the AI and the user (Android)
- **[Kai Build](features/kai-build.md)** â€” Coding environment with terminals and coding agents (Android)
- **[Notifications](features/notifications.md)** â€” Reading other apps' notifications (Android, FOSS build)
- **[SMS](features/sms.md)** â€” Reading SMS and drafting replies for the user to send (Android, FOSS build)
- **[Appearance](features/appearance.md)** â€” Theme picker
- **[Encryption](features/encryption.md)** â€” Secure settings storage per platform
- **[Settings Export & Import](features/settings-export-import.md)** â€” JSON backup and restore
- **[Splinterlands](features/splinterlands.md)** â€” Auto-battle integration
- **[Debug API](debug-api.md)** â€” 50+ HTTP endpoints for development
- **[Codebase Audit](codebase-audit-2026-06-02.md)** â€” Full codebase analysis and known issues

## Links

- [GitHub Repository](https://github.com/kilvz/Kai-custom)
- [Issue Tracker](https://github.com/kilvz/Kai-custom/issues)
- [Releases](https://github.com/kilvz/Kai-custom/releases)
- [Upstream Kai](https://github.com/SimonSchubert/Kai)
