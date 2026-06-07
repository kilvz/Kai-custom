# Kai

A **fork of Kai 9000** built for Android.
Windows, Mac, and Linux builds compile but are untested.

Same rock-solid core. More features. Still FOSS, still private.

---

## What Makes This Fork Special

| Feature | What It Does |
|---------|-------------|
| **Floating Chat Bubble** | Drag it around your screen. Tap to chat without leaving other apps. |
| **Screen Reader** | The AI sees what's on your screen. It can describe, tap, and scroll for you. |
| **Custom Characters** | Give your AI a name and personality. Pick from 150+ premade characters or create your own. |
| **Character Mode** | Pure roleplay — no "how can I help you" framing. The AI just *becomes* the character. |
| **Plugin System** | Extra tools you can turn on, including an AI persona creator. |
| **ClawHub Skills** | Browse and install community skills from clawhub.ai directly in-app. |
| **Telegram & WhatsApp** | Connect your messaging apps. The AI reads, remembers, and can reply. |
| **SSH Remote Access** | Your AI logs into your servers and runs commands. Save profiles for quick access. |
| **Wake Word** | Say "Hey Kai" hands-free. Works even when the app is in the background. |
| **Push to Talk** | Hold and speak. Faster than typing. |
| **Auto Memory Learning** | The AI learns your preferences and habits automatically — no manual setup. |
| **Docker Sandbox (Desktop)** | On PC/Mac (untested), the AI runs code in a container — same sandbox powers as Android. |
| **Alt-Memory Dimension** | Optional vector brain with search, knowledge graph, and a personal AI diary. |
| **Debug API** | 77+ HTTP endpoints. Every feature programmable from outside the app. |
| **On-Device AI** | Run models locally (Gemma 4, Qwen3, TinySwallow). No internet needed. |

---

## What Comes From Kai 9000

- 24+ AI providers with automatic fallback
- Persistent memory stored locally
- Linux sandbox (proot) on Android
- Automated heartbeat & scheduled tasks
- MCP server support
- Skills system from GitHub
- Encrypted chat storage
- Settings export/import

---

## Installation

### Direct Downloads

| Platform | Format | Download |
|----------|--------|----------|
| Android | APK | [GitHub Releases](https://github.com/kilvz/Kai-custom/releases) |

### Build from Source

```shell
git clone https://github.com/kilvz/Kai-custom.git
cd Kai-custom
./gradlew :androidApp:assembleFossDebug
```

---

## Debug API (port 18500)

```powershell
adb forward tcp:18500 tcp:18500
curl.exe -s http://127.0.0.1:18500/health
```

---

## License

Apache 2.0. Based on [Kai 9000](https://github.com/SimonSchubert/Kai) by Simon Schubert.
