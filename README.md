# Kai-custom

Kai-custom is a FOSS Android-first fork of [Kai](https://github.com/SimonSchubert/Kai): a personal AI assistant with persistent memory, tool use, Linux sandbox automation, local device integrations, and optional remote model providers.

This fork keeps the app fully open-source, removes proprietary telemetry dependencies, and focuses on Android as the primary supported target.

## Highlights

- Android assistant with chat, voice input, text-to-speech, tool use, scheduling, and heartbeat checks.
- Multi-provider AI support with fallback ordering, local LiteRT models, OpenAI-compatible endpoints, and a free-provider path.
- Two-tier memory with user-editable memories, protected behavior learnings, personas, import/export, and optional Alt-Memory backend.
- Alpine Linux sandbox via proot for shell commands, Python/Node scripts, file work, package installs, and AI-driven coding tasks.
- Device tools for notifications, contacts, SMS, calendar, location, battery, Wi-Fi/network info, clipboard, installed apps, logs, media, Bluetooth, Shizuku ADB, and root commands where enabled.
- SSH tooling, MCP server support, scheduled tasks, heartbeat automation, dynamic UI generation, and debug API for development.

## Platform Support

| Target | Status |
| --- | --- |
| Android | Primary target. Release APKs are built for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`. |
| Desktop JVM | Builds are best-effort on Windows, Linux, and macOS. |
| WasmJs | Source target exists but is not actively tested. |
| iOS | Source target exists but is not actively tested. |

## Core Features

### Chat And AI Providers

- Chat UI with persistent history and tool-calling support.
- Provider ordering and fallback across hosted services and compatible APIs.
- LiteRT on-device inference support for downloaded local models.
- Local-service fallback avoids selecting local inference when no local model is installed.

### Memory And Personas

- User memories store facts, preferences, and extracted conversation details.
- Protected behavior memories store learned operating patterns and are hidden from normal deletion UI.
- Memory can be disabled globally, which removes memory tools, prompt memory dumps, and extraction.
- Persona support includes named persona profiles, editable persona details, and active persona switching.
- Soul text is split into user-edited text and auto-generated behavior notes.
- Memory export/import is supported from Settings.

### Alt-Memory Backend

- Optional Alt-Memory MCP backend can be installed into the sandbox.
- Settings redirects users to the Sandbox tab when Alt-Memory is requested before installation.
- The Sandbox tab includes an `Alt-Memory` install action next to sandbox controls.
- Installation runs as the app user inside proot, with `--break-system-packages`, `--no-cache-dir`, longer timeouts, and retry handling for large Python wheels.
- Starting/stopping Alt-Memory registers or removes the built-in MCP server and migrates local memories when ready.

### Linux Sandbox

- Alpine Linux rootfs via proot.
- Basic package installer for `bash`, `curl`, `wget`, `git`, `jq`, `python3`, `pip`, `nodejs`, `openssh-client`, `lftp`, and `rsync`.
- Terminal tab with persistent session handling and a separate temporary scratch session when chat sessions exist.
- Files tab and package tooling for sandbox inspection and management.
- Shell commands can run through persistent sessions or one-shot command execution.
- Optional storage mount exposes device storage as `/sdcard`.
- Optional root mode can wrap proot commands with `su -c` when root is enabled.

### Device Tools

Kai-custom includes Android tools that are individually gated by settings and runtime permissions where applicable:

- GPS location
- Contacts read/write
- Device, battery, network, Wi-Fi, and phone-state info
- Clipboard read
- Installed apps list
- Calendar events
- SMS check/read/search/reply/send draft flow
- Notification check/read/search
- Media listing
- Bluetooth scan/list
- Device logs
- Shizuku-backed ADB commands
- Root commands through `su` when enabled

### Automation

- Heartbeat checks can run on a configurable interval during active hours.
- Scheduled tasks support one-shot, cron, and heartbeat-triggered execution.
- Email and SMS polling can surface pending items for the assistant.
- Dynamic UI generation can create interactive screens such as forms, dashboards, quizzes, and lists.

### SSH And MCP

- SSH profile configuration and command execution tools.
- Streamable HTTP MCP server support with built-in and custom endpoints.
- Built-in Alt-Memory MCP integration when installed and enabled.

### Debug API

Debug builds can expose a localhost-only HTTP API on `127.0.0.1:18500` for development through ADB port forwarding.

Documented endpoints include:

- `GET /health`
- `GET /prompt`
- `GET /history`
- `GET /state`
- `GET /tools`
- `GET /memories`
- `GET /settings`
- `POST /chat`
- `POST /settings/{key}`
- `POST /sandbox/setup`
- `POST /sandbox/install-packages`
- `POST /sandbox/exec`
- `POST /reset`

See [docs/debug-api.md](docs/debug-api.md) for authentication, examples, and the raw sandbox command endpoint.

## Build Requirements

- JDK 21+
- Android SDK with API 36 available
- Gradle wrapper from this repository
- ADB for device install/testing

## Common Commands

Compile shared Android code:

```powershell
.\gradlew.bat :composeApp:compileAndroidMain
```

Build debug APK:

```powershell
.\gradlew.bat :androidApp:assembleFossDebug
```

Install debug APK on a connected arm64 Android device:

```powershell
adb install -r androidApp\build\outputs\apk\foss\debug\androidApp-foss-arm64-v8a-debug.apk
```

Build release APKs:

```powershell
.\gradlew.bat :androidApp:assembleFossRelease
```

Release APK output paths:

```text
androidApp\build\outputs\apk\foss\release\androidApp-foss-arm64-v8a-release.apk
androidApp\build\outputs\apk\foss\release\androidApp-foss-armeabi-v7a-release.apk
androidApp\build\outputs\apk\foss\release\androidApp-foss-x86-release.apk
androidApp\build\outputs\apk\foss\release\androidApp-foss-x86_64-release.apk
```

## Versioning

The app version is stored in:

- `VERSION`
- `gradle/libs.versions.toml`

Version format is `vMAJOR.MINOR.PATCH`. Android `versionCode` is bumped for every app change.

## Release Flow

Releases are tag-driven. Pushing a `v*` tag can trigger GitHub Actions builds. Manual releases can also attach locally built APKs.

Typical manual release steps:

```powershell
.\gradlew.bat :composeApp:compileAndroidMain
.\gradlew.bat :androidApp:assembleFossRelease
git tag vX.Y.Z
git push origin main vX.Y.Z
```

## Repository Notes

- Package name: `com.kai.custom`
- App name: Kai-custom
- Build flavor: FOSS
- Primary branch: `main`
- License: Apache License 2.0

## Difference From Upstream

Kai-custom is a public FOSS fork with a custom version line and additional Android-focused capabilities.

Notable additions include:

- Two-tier memory and persona management
- Alt-Memory MCP backend integration
- Linux sandbox terminal/package/files UI
- Shizuku/root tools
- SSH tooling
- SMS, notification, contact, calendar, device, and media tools
- Debug API
- Dynamic UI generation
- Heartbeat and task automation changes

Removed or de-emphasized items include proprietary analytics/crash reporting dependencies and upstream-specific publishing workflows that do not apply to this fork.

## License

Apache License 2.0. See [LICENSE.txt](LICENSE.txt).

Based on [Kai](https://github.com/SimonSchubert/Kai) by Simon Schubert.
