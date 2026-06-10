# Debug API Server â€” Plan

## Goal
Allow opencode (PC) to inspect and interact with the Kai agent running on a phone via HTTP, for debugging purposes.

## Architecture
Ktor HTTP server on the Android device (`localhost:18500`), accessible from PC via `adb forward tcp:18500 tcp:18500`. Random auth token generated at startup.

## Auth
- Random 32-char hex token generated at app startup
- Required as `Authorization: Bearer <token>` on all endpoints
- Printed to logcat on startup

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Server alive + auth token |
| `GET` | `/prompt` | Current system prompt (what the AI sees) |
| `GET` | `/history?n=10` | Last N chat exchanges |
| `GET` | `/state` | Full dump: history + memory + tools + settings |
| `GET` | `/tools` | Registered tools with schemas |
| `GET` | `/memories` | All memories (user + behavior) |
| `GET` | `/settings` | All settings as flat JSON |
| `POST` | `/chat` | Send a message to the agent, get response |
| `POST` | `/settings/{key}` | Update a setting value |
| `POST` | `/reset` | Reset conversation |

## UI
- General tab â†’ "Advanced" section (collapsible, no "Experimental" badge)
- Toggle: "Debug API Server"
- Warning Surface (orange): "Opens an HTTP API on localhost:18500 accessible via ADB. Only enable while debugging."
- Requires daemon to be running (toggle disabled when daemon is off)
- Only visible on Android (gated by `isDebugBuild`)

## Files

### New
- `docs/rfcs/debug-api-plan.md` â€” this plan
- `composeApp/src/commonMain/.../data/DebugApiModels.kt` â€” DTOs
- `composeApp/src/androidMain/.../debug/DebugServer.kt` â€” Ktor server + routes

### Modified
- `AppSettings.kt` â€” add `debug_api_enabled` key
- `DataRepository.kt` â€” add `isDebugApiEnabled()`/`setDebugApiEnabled()`
- `RemoteDataRepository.kt` â€” delegate to AppSettings
- `FakeDataRepository.kt` â€” stub via private var
- `SettingsUiState.kt` â€” add `isDebugApiEnabled`, `showDebugApiSection`
- `SettingsActions.kt` â€” add `onToggleDebugApi`
- `GeneralSettings.kt` â€” add `AdvancedSection` composable
- `SettingsViewModel.kt` â€” wire state + action
- `SettingsScreen.kt` â€” pass new params to `GeneralContent`
- `AppModule.kt` â€” register DebugServer singleton
- `DaemonService.kt` â€” start/stop DebugServer with daemon

## Usage
```powershell
adb forward tcp:18500 tcp:18500
# Get auth token from /health
curl http://localhost:18500/health
# See the AI's system prompt
curl http://localhost:18500/prompt -H "Authorization: Bearer <token>"
# Send a message
curl -X POST http://localhost:18500/chat -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d '{"message":"hello"}'
# Full state dump
curl http://localhost:18500/state -H "Authorization: Bearer <token>"
```
