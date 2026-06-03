# K.Ai

An **open-source personal AI assistant** that runs on **Android, Windows, Mac, and Linux**.

Your AI doesn't just chat — it remembers, learns, takes action, and even automates tasks for you.

## Features

- **Persistent memory** — Remembers facts, preferences, and learns your behavior patterns over time. Memories surface automatically when they're relevant.
- **Two-tier learning** — Uses memories get reinforced, useful patterns get locked in. You're always in control of what's stored.
- **Personas** — Give your AI a name and personality. Switch between different personas for different roles: assistant, advisor, companion, or whatever you need.
- **Chat with any AI** — Works with 24+ providers including OpenAI, Anthropic, Gemini, DeepSeek, and local on-device models. Automatic fallback if one goes down.
- **Interactive UI** — The AI can generate full interactive screens: quizzes, dashboards, recipes, brainstorms. Tap buttons instead of scrolling through chat.
- **Linux sandbox** — On Android, the AI runs shell commands, Python scripts, and Node.js in a secure sandboxed Linux environment (proot). Supports both Alpine and Ubuntu.
- **WhatsApp bridge** — Connect WhatsApp to your AI. It reads messages, stores context in memory, and can reply when you're ready. Optional read-only mode.
- **Device tools** — GPS, contacts, SMS, notifications, calendar, clipboard, Bluetooth, Wi-Fi, battery info, phone state, and more — all gated by your permission.
- **Automated heartbeat** — Your AI checks in periodically (every 30 min during active hours). If nothing needs attention, it stays silent. If something does, it speaks up.
- **Scheduled tasks** — Set up one-shot or recurring tasks. Your AI runs them even when you're not chatting.
- **MCP servers** — Connect to external tool servers via the Model Context Protocol. Built-in support for web fetch, crypto prices, domain search, and more.
- **SSH access** — Configure profiles and let your AI run commands on remote servers through active SSH sessions.
- **Wake word** — "Hey K.Ai" voice activation on Android. Speak your requests without opening the app.
- **Skills system** — Install skill packages from GitHub for specialized capabilities. Your AI picks the right skill automatically.
- **Debug API** — 70+ HTTP endpoints for development and integration via ADB port forwarding.
- **Alt-memory** — Optional Python vector store with search, knowledge graph, and diary. Replaces local SQLite for more powerful memory.
- **Encrypted storage** — Conversations stored locally with encryption.
- **Settings export/import** — Back up and restore everything as a JSON file.
- **Text to speech** — Listen to AI responses.

## Linux Sandbox (Android)

On Android, K.Ai includes a built-in Linux environment that turns your assistant from a chat-only bot into one that can take real action:

- **Alpine or Ubuntu** — Choose your distro. Alpine is lightweight (~3 MB), Ubuntu is more compatible with common packages.
- **Full package manager** — One tap installs bash, python3, Node.js, curl, git, and more.
- **Interactive terminal** — A built-in terminal lets you run commands alongside the AI.
- **Secure** — Everything runs inside the app via proot. No root required.

Enable it in **Settings > Linux Sandbox**.

## WhatsApp Integration

Connect your WhatsApp account and let your AI stay in the loop:

- **Read conversations** — New messages are automatically stored in memory so your AI has full context.
- **Read-only mode** — By default, the AI reads but doesn't reply. You decide when to turn on reply capabilities.
- **QR code pairing** — Simple QR scan to connect, just like WhatsApp Web.
- **Sandbox-powered** — Runs inside the Linux sandbox using an isolated Node.js bridge.

## Alt-Memory Backend

For power users, K.Ai can replace its local SQLite memory with a full Python vector database running inside the sandbox:

- **Vector search** — Find memories by meaning, not just keywords.
- **Knowledge graph** — Track relationships between concepts, people, and events.
- **Diary** — Automatic timeline of interactions and learnings.
- **Seamless** — Toggle between SQLite and Alt-Memory in settings. All existing data migrates automatically.

## How It Works

```
User → Chat → AI → Memory ↔ Heartbeat
              ↓
           Tools (sandbox, WhatsApp, device, SSH, MCP...)
```

- **Chat naturally** — Send a message, your AI responds with full context from memory.
- **AI calls tools** — It can install packages, run scripts, check WhatsApp, read your calendar, search the web, or connect to MCP servers.
- **Memory learns** — Facts and preferences are stored automatically. Useful patterns get reinforced.
- **Heartbeat checks in** — Every 30 minutes, your AI reviews what's going on and surfaces anything important. Otherwise, it stays quiet.

## Platform Support

| Platform | Status |
|----------|--------|
| Android | **Primary target.** ARM64, ARMv7, x86, x86_64 |
| Windows | Best-effort (MSI) |
| macOS | Best-effort (DMG) |
| Linux | Best-effort (DEB, RPM, AppImage) |

## Build & Install (Android)

```bash
# Build
./gradlew :androidApp:assembleFossDebug

# Install on device
adb install -r androidApp/build/outputs/apk/foss/debug/androidApp-foss-arm64-v8a-debug.apk
```

Requires JDK 21+, Android SDK API 35+, and a device running Android 12+.

## License

Apache License 2.0. See [LICENSE.txt](LICENSE.txt).

Based on [Kai](https://github.com/SimonSchubert/Kai) by Simon Schubert.
