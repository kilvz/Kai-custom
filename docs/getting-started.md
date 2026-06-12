# Getting Started â€” Kai-custom

## Installation

Kai-custom is distributed as APK files via GitHub Releases. No Play Store, F-Droid, or other app store distribution.

### APK Download

1. Go to [GitHub Releases](https://github.com/kilvz/Kai-custom/releases)
2. Download the APK matching your device architecture: `arm64-v8a`, `armeabi-v7a`, `x86`, or `x86_64`
3. On your Android device, enable **Install from unknown sources** in Settings
4. Open the downloaded APK file and follow the installation prompts

### ADB Install (Developer)

Connect your device via USB with USB debugging enabled:

```powershell
adb install -r androidApp\build\outputs\apk\foss\release\androidApp-foss-arm64-v8a-release.apk
```

### Building from Source

See the [README](../README.md) for build requirements and commands.

## First Steps

1. Launch Kai-custom â€” you'll see the chat screen with an animated welcome
2. Start chatting immediately using the **Free** tier (no API key needed)
3. For better models, open **Settings** and add a service (e.g. OpenAI, Gemini, DeepSeek)
4. Enter your API key â€” Kai validates the connection and loads available models automatically

## Adding a Service

1. Open Settings
2. Tap **Add Service** and pick a provider
3. Paste your API key
4. Select a model from the dropdown
5. Drag services to reorder â€” the first one is your primary, the rest are fallbacks

See [Multi-Service](features/multi-service.md) for the full details on providers and fallback behavior.
