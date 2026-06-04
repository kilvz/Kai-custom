# Debug API

HTTP server on the Android device (`127.0.0.1:18500`) for inspecting and controlling the Kai agent from a dev machine via ADB.

## Activation

1. Start the daemon (Debug API requires daemon to bind the port)
2. Settings → General → Advanced → enable "Debug API Server"
3. Read the auth token from logcat:
   ```powershell
   adb logcat -s DebugServer:D
   ```
4. Forward the port:
   ```powershell
   adb forward tcp:18500 tcp:18500
   ```

## Authentication

All endpoints except `/health` require `Authorization: Bearer <token>` header. Token is a random 32-char hex regenerated on every app launch. Unauthenticated requests return `401`:
```json
{"error": "Invalid or missing token"}
```

## Endpoints (~80 total)

### Health & State

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/health` | No | Server alive + current auth token |
| `GET` | `/state` | Yes | App state dump (history/memory/tool counts, daemon/memory/scheduling/heartbeat/sandbox toggles) |
| `POST` | `/reset` | Yes | Clear conversation history |

### System Prompt & Chat

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/prompt` | Yes | Full system prompt the AI sees (plain text) |
| `GET` | `/history?n=10` | Yes | Last N chat exchanges (plain text) |
| `POST` | `/chat` | Yes | Send message to AI with full tool-calling |
| `POST` | `/chat/silent` | Yes | Send message silently (no history, no UI) → returns plain text response |
| `POST` | `/regenerate` | Yes | Trigger AI response regeneration |

### Settings

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/settings` | Yes | All settings as flat JSON (60+ keys) |
| `POST` | `/settings/{key}` | Yes | Update a single setting |
| `POST` | `/interactive` | Yes | Toggle interactive mode on/off |
| `POST` | `/soul/user` | Yes | Set user-edited soul text (raw body) |
| `POST` | `/soul/auto` | Yes | Set auto-generated behavior soul text (raw body) |

### Export / Import

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/export/preview` | Yes | Preview export sections with item counts |
| `POST` | `/export` | Yes | Export all data as JSON |
| `POST` | `/import` | Yes | Import settings from JSON |

### Personas

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/personas` | Yes | List all personas |
| `POST` | `/persona/save` | Yes | Save/create a persona |
| `POST` | `/persona/switch/{id}` | Yes | Switch active persona |
| `DELETE` | `/persona/{id}` | Yes | Delete a persona |

### Conversations

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/conversations` | Yes | List saved conversations |
| `POST` | `/conversation/load/{id}` | Yes | Load a saved conversation |
| `POST` | `/conversation/new` | Yes | Start a new conversation |
| `DELETE` | `/conversation/delete/{id}` | Yes | Delete a conversation |

### Services

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/services` | Yes | List all available services |
| `POST` | `/service/add/{serviceId}` | Yes | Add a configured service instance |
| `POST` | `/service/remove` | Yes | Remove a configured service |
| `POST` | `/service/api-key` | Yes | Update API key for a service |
| `POST` | `/service/base-url` | Yes | Update base URL for a service |
| `POST` | `/service/model` | Yes | Select a model for a service |

### Tools

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/tools` | Yes | All registered tool definitions (name + description) |
| `GET` | `/tools/definitions` | Yes | Tool definitions with id, name, desc, enabled |
| `GET` | `/tools/enabled` | Yes | Tool enabled/disabled map |
| `POST` | `/tools/enabled/{toolId}` | Yes | Enable/disable a tool |
| `POST` | `/tools/{name}` | Yes | Execute a tool by name (raw body = JSON args) |
| `POST` | `/tools/call` | Yes | Execute a tool by name with JSON body `{"tool":"...","arguments":{}}` |
| `GET` | `/tools/list` | Yes | List all tools with full schemas (name, description, timeout, input schema) |

### Memory

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/memories` | Yes | List all memories |
| `GET` | `/memory/{key}` | Yes | Get a single memory by key |
| `POST` | `/memory` | Yes | Store a new memory |
| `DELETE` | `/memory/{key}` | Yes | Delete/forget a memory |
| `POST` | `/memory/search` | Yes | Search memories by query |

### Alt-Memory (Vector DB)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/alt-memory` | Yes | Alt-memory status (enabled, installed, connected, counts) |
| `POST` | `/alt-memory/install` | Yes | Install alt-memory package in sandbox (calls `installAltMemoryPackage()`) |

### MCP Servers

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/mcp/servers` | Yes | List MCP server configs |
| `POST` | `/mcp/add` | Yes | Add an MCP server |
| `DELETE` | `/mcp/{serverId}` | Yes | Remove an MCP server |
| `POST` | `/mcp/connect/{serverId}` | Yes | Connect to an MCP server (fetches tools) |

### Skills

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/skills` | Yes | List installed skills |
| `POST` | `/skill/install` | Yes | Install a skill from GitHub |
| `POST` | `/skill/uninstall/{id}` | Yes | Uninstall a skill |
| `POST` | `/skill/activate` | Yes | Activate/deactivate a skill |

### Heartbeat

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/heartbeat` | Yes | Get heartbeat config + log |
| `POST` | `/heartbeat` | Yes | Update heartbeat configuration |

### Email

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/email` | Yes | List email accounts |
| `POST` | `/email/add` | Yes | Add an email account |
| `DELETE` | `/email/{accountId}` | Yes | Remove an email account |
| `POST` | `/email/poll/{accountId}` | Yes | Poll a specific email account |

### SMS

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/sms/drafts` | Yes | List SMS drafts |
| `POST` | `/sms/send/{draftId}` | Yes | Send an SMS draft |
| `POST` | `/sms/discard/{draftId}` | Yes | Discard an SMS draft |

### Sandbox (Linux)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/sandbox/status` | Yes | Sandbox status (installed, ready, working, progress, error, distro settings, lastWhatsAppError) |
| `POST` | `/sandbox/setup` | Yes | Start sandbox rootfs setup (download + extract) |
| `POST` | `/sandbox/install-packages` | Yes | Install sandbox packages (bash, curl, git, python3, nodejs, etc.) |
| `POST` | `/sandbox/exec` | Yes | Run raw command in sandbox (`?root=`, `?timeout=` params, `?format=json` for structured response with success/stdout/stderr/exit_code/error) |
| `POST` | `/sandbox/reset` | Yes | Delete rootfs and reset sandbox to NotInstalled state |

### WhatsApp (Baileys Bridge)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/whatsapp` | Yes | WhatsApp integration status |
| `POST` | `/whatsapp/install` | Yes | Install WhatsApp bridge (Node.js + baileys + npm) |
| `POST` | `/whatsapp/refresh-qr` | Yes | Refresh QR code for WhatsApp auth |
| `POST` | `/whatsapp/restart` | Yes | Restart WhatsApp bridge service |
| `GET` | `/whatsapp/settings` | Yes | Get Baileys socket settings |

### Other Integrations

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/local/models` | Yes | List local inference models |
| `POST` | `/local/download` | Yes | Start downloading a local model |
| `POST` | `/local/cancel` | Yes | Cancel local model download |
| `DELETE` | `/local/{modelId}` | Yes | Delete a downloaded local model |
| `GET` | `/wake-word` | Yes | Get wake-word settings |
| `POST` | `/wake-word` | Yes | Toggle wake-word on/off |
| `GET` | `/telegram` | Yes | Telegram integration status |
| `GET` | `/splinterlands` | Yes | Splinterlands integration status |

### Usage Scenarios

#### Inspect the AI's system prompt
```powershell
$token = (curl.exe -s http://localhost:18500/health | python -c "import sys,json; print(json.load(sys.stdin)['token'])")
curl.exe -s http://localhost:18500/prompt -H "Authorization: Bearer $token"
```

#### Test the AI with tool calling
```powershell
$body = '{"message":"Search the web for latest AI news and store the result as a memory"}'
$f = [System.IO.Path]::GetTempFileName(); [System.IO.File]::WriteAllText($f, $body)
curl.exe -s -X POST http://localhost:18500/chat -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@$f"
Remove-Item $f -EA 0
```

#### Full sandbox setup flow
```powershell
# 1. Setup rootfs (download + extract ~2min)
curl.exe -s -X POST http://localhost:18500/sandbox/setup -H "Authorization: Bearer $token"

# 2. Wait for sandbox to be ready...
# 3. Install packages (curl, git, python3, nodejs, etc.)
curl.exe -s -X POST http://localhost:18500/sandbox/install-packages -H "Authorization: Bearer $token"

# 4. Verify with a command
curl.exe -s -X POST "http://localhost:18500/sandbox/exec?timeout=10" -H "Authorization: Bearer $token" -d "uname -a"
```

#### WhatsApp bridge install flow
```powershell
# After sandbox is ready + packages installed:
# 1. Install WhatsApp bridge (Node.js v22 + baileys + MCP SDK)
curl.exe -s -X POST http://localhost:18500/whatsapp/install?timeout=300 -H "Authorization: Bearer $token"

# 2. Check status
curl.exe -s http://localhost:18500/whatsapp -H "Authorization: Bearer $token"

# 3. Refresh QR code for scanning
curl.exe -s -X POST http://localhost:18500/whatsapp/refresh-qr -H "Authorization: Bearer $token"
```

#### Enable/disable features remotely
```powershell
$body = '{"value":"true"}'
$f = [System.IO.Path]::GetTempFileName(); [System.IO.File]::WriteAllText($f, $body)
curl.exe -s -X POST http://localhost:18500/settings/root_enabled -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@$f"
Remove-Item $f -EA 0
```

#### Execute a tool directly (debug tool calls without AI)
```powershell
$body = '{"tool":"list_directory","arguments":{"path":"/root","recursive":false}}'
$f = [System.IO.Path]::GetTempFileName(); [System.IO.File]::WriteAllText($f, $body)
curl.exe -s -X POST http://localhost:18500/tools/call -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@$f"
Remove-Item $f -EA 0
```

#### Run sandbox command with structured JSON response (includes exit code)
```powershell
$f = [System.IO.Path]::GetTempFileName(); [System.IO.File]::WriteAllText($f, "ls -la /root")
curl.exe -s -X POST "http://localhost:18500/sandbox/exec?timeout=15&format=json" -H "Authorization: Bearer $token" -d "@$f"
Remove-Item $f -EA 0
```

#### Alt-memory install (requires sandbox with python3)
```powershell
curl.exe -s -X POST http://localhost:18500/alt-memory/install -H "Authorization: Bearer $token"
```

#### Dump full state
```powershell
curl.exe -s http://localhost:18500/state -H "Authorization: Bearer $token" | python -m json.tool
```

### Architecture

```
┌──────────────────────────────────────────────┐
│  Dev Machine (PC)                            │
│  curl / opencode / custom script             │
│       │                                       │
│       │ HTTP :18500                           │
└───────┬──────────────────────────────────────┘
        │
        │ adb forward tcp:18500 tcp:18500
        │
┌───────┬──────────────────────────────────────┐
│  Android Device                              │
│  ┌──────────────────────────────────────┐    │
│  │ Ktor Server (CIO, 127.0.0.1:18500)   │    │
│  │  77 endpoints across all features     │    │
│  │  auth: Bearer token (32-char hex)     │    │
│  └──────────────────────────────────────┘    │
│                                              │
│  ┌──────────────────────────────────────┐    │
│  │ DaemonService (starts/stops server)   │    │
│  └──────────────────────────────────────┘    │
└──────────────────────────────────────────────┘
```

### Configuration

- **Port:** `18500` (hardcoded, localhost-only)
- **Host:** `127.0.0.1` (only accessible via ADB or local apps)
- **Lifecycle:** bound to daemon — starts when daemon starts, stops when daemon stops
- **Build gate:** only runs in debug builds (`BuildConfig.DEBUG`)
- **Auth token:** random 32-char hex, regenerated on every app launch, printed to logcat
