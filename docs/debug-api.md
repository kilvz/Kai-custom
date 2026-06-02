# Debug API

HTTP server on the Android device (`127.0.0.1:18500`) for inspecting and controlling the Kai agent from a dev machine via ADB.

## Activation

1. Start the daemon (Debug API requires daemon to bind the port)
2. Settings â†’ General â†’ Advanced â†’ enable "Debug API Server"
3. Read the auth token from logcat:
   ```powershell
   adb logcat -s DebugServer:D
   ```
4. Forward the port:
   ```powershell
   adb forward tcp:18500 tcp:18500
   ```

## Authentication

All endpoints (except `/health`) require `Authorization: Bearer <token>` header. Token is a random 32-char hex regenerated on every app launch. Unauthenticated requests return `401`:
```json
{"error": "Invalid or missing token"}
```

## Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/health` | No | Server alive + current auth token |
| `GET` | `/prompt` | Yes | Full system prompt the AI sees |
| `GET` | `/history?n=10` | Yes | Last N chat exchanges (plain text) |
| `GET` | `/state` | Yes | Agent state: history/memory/tool counts, toggles |
| `GET` | `/tools` | Yes | All registered tool definitions (name + description) |
| `GET` | `/memories` | Yes | All stored memories |
| `GET` | `/settings` | Yes | All settings as flat JSON |
| `POST` | `/chat` | Yes | Send message to AI with **full tool-calling** |
| `POST` | `/sandbox/setup` | Yes | Start Linux sandbox setup |
| `POST` | `/sandbox/install-packages` | Yes | Start basic sandbox package install |
| `POST` | `/sandbox/exec` | Yes | Run a raw Linux sandbox command and return output |
| `POST` | `/settings/{key}` | Yes | Update a single setting |
| `POST` | `/reset` | Yes | Clear conversation history |

### `GET /health`

**Response:**
```json
{
    "status": "ok",
    "token": "1a7db4dc742343d2a499b03c01c0267f"
}
```

### `GET /prompt`

Returns the **exact system prompt** currently fed to the AI â€” useful for debugging prompt injections or checking what the AI knows.

**Response:** `text/plain`

### `GET /history?n=10`

Returns the last N user+assistant exchange pairs.

**Response:** `text/plain`
```
User: Hello
Assistant: Hi! How can I help you?
User: What time is it?
Assistant: ...
```

### `GET /state`

**Response:**
```json
{
    "historyCount": 5,
    "memoryCount": 12,
    "toolCount": 45,
    "isDaemonEnabled": true,
    "isMemoryEnabled": true,
    "isSchedulingEnabled": true,
    "isHeartbeatEnabled": true,
    "currentServiceId": "free"
}
```

### `GET /tools`

Returns all tools the AI can call. Currently ~45 tools (varies by platform).

**Response:**
```json
[
    {
        "name": "memory_store",
        "description": "Store or update a memory with a descriptive key..."
    },
    {
        "name": "web_search",
        "description": "Search the internet..."
    }
]
```

### `GET /memories`

**Response:**
```json
[
    {
        "key": "preferred_name",
        "content": "User prefers to be called Alex",
        "category": "PREFERENCE",
        "protected": "false"
    }
]
```

### `GET /settings`

All settings as key-value string pairs. Boolean values are `"true"` / `"false"`.

**Response:**
```json
{
    "soul_text": "You are Kai...",
    "persona_name": "Alt",
    "free_service_primary": "true",
    "memory_enabled": "true",
    "alt_memory_enabled": "true",
    "scheduling_enabled": "true",
    "daemon_enabled": "true",
    "heartbeat_enabled": "true",
    "sandbox_enabled": "false",
    "sandbox_root_enabled": "false",
    "root_enabled": "false",
    "shizuku_enabled": "false",
    "debug_api_enabled": "true",
    "preferred_language": "en",
    "notifications_enabled": "false",
    "dynamic_ui_enabled": "true",
    "email_enabled": "true",
    "sms_enabled": "false",
    "telegram_enabled": "false",
    "wake_word_enabled": "false"
}
```

### `POST /chat`

Sends a message to the AI with **full tool-calling support**. The AI can use all ~45 registered tools (memory, web search, sandbox, root, etc.) in a tool loop and will return the final text response.

**Request:**
```json
{
    "message": "Store a memory with key 'test' and content 'hello world'"
}
```

**Response:**
```json
{
    "response": "Memory stored successfully with key 'test'..."
}
```

This endpoint uses `askWithTools()` internally, which:
1. Gets the active service (Free, OpenAI, Gemini, etc.)
2. Passes all registered tool definitions to the AI
3. Runs a tool loop (AI calls tools â†’ results fed back â†’ AI responds)
4. Returns the final text response

### `POST /settings/{key}`

Update any writable setting.

**Request:**
```json
{
    "value": "false"
}
```

**URL example:** `POST /settings/memory_enabled`

**Supported keys:**
`soul_text`, `persona_name`, `preferred_language`, `free_service_primary`,
`memory_enabled`, `alt_memory_enabled`, `scheduling_enabled`, `daemon_enabled`,
`heartbeat_enabled`, `sandbox_enabled`, `sandbox_storage_mount_enabled`,
`sandbox_root_enabled`, `root_enabled`, `shizuku_enabled`,
`notifications_enabled`, `dynamic_ui_enabled`, `email_enabled`, `sms_enabled`,
`telegram_enabled`, `wake_word_enabled`, `debug_api_enabled`

**Response (success):** `text/plain`: `Updated memory_enabled`

**Response (error):**
```json
{"error": "Unknown setting: foo"}
```

### `POST /sandbox/setup`

Starts Linux sandbox setup. Returns immediately with `text/plain`: `Sandbox setup started`.

### `POST /sandbox/install-packages`

Starts installation of the standard sandbox package set. Returns immediately with `text/plain`: `Sandbox package install started`.

### `POST /sandbox/exec`

Runs a raw command inside the Linux sandbox and returns combined command output as `text/plain`.

**Request body:** plain text shell command.

**Query parameters:**
- `root=true|false` â€” whether to use the sandbox root path. Defaults to `false`.
- `timeout=<seconds>` â€” command timeout. Defaults to `60`.

**Examples:**
```powershell
$token = (curl.exe -s http://localhost:18500/health | python -c "import sys,json; print(json.load(sys.stdin)['token'])")
curl.exe -s -X POST "http://localhost:18500/sandbox/exec?timeout=60&root=false" -H "Authorization: Bearer $token" -H "Content-Type: text/plain" --data-binary "python3 -m pip --version"
curl.exe -s -X POST "http://localhost:18500/sandbox/exec?timeout=600&root=false" -H "Authorization: Bearer $token" -H "Content-Type: text/plain" --data-binary "python3 -m pip install --no-cache-dir --break-system-packages six"
```

Use this endpoint for diagnostics where `/chat` would be too indirect. It executes through `SandboxController.executeCommand()` and does not involve the model/tool loop.

### `POST /reset`

Clears the current conversation history. Returns `text/plain`: `Conversation reset`

## Usage Scenarios

### Inspect the AI's system prompt
```powershell
$token = (curl.exe -s http://localhost:18500/health | python -c "import sys,json; print(json.load(sys.stdin)['token'])")
curl.exe -s http://localhost:18500/prompt -H "Authorization: Bearer $token"
```

### Test the AI with tool calling
```powershell
$body = '{"message":"Search the web for latest AI news and store the result as a memory"}'
curl.exe -s -X POST http://localhost:18500/chat -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d $body
```

### Enable/disable features remotely
```powershell
curl.exe -s -X POST http://localhost:18500/settings/root_enabled -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d '{"value":"true"}'
curl.exe -s -X POST http://localhost:18500/settings/alt_memory_enabled -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d '{"value":"false"}'
```

### Dump full state
```powershell
curl.exe -s http://localhost:18500/state -H "Authorization: Bearer $token" | python -m json.tool
```

### Monitor changes
```powershell
# Watch memory count over time
while ($true) { curl.exe -s http://localhost:18500/state -H "Authorization: Bearer $token" | python -c "import sys,json; d=json.load(sys.stdin); print(f'Memories: {d[\"memoryCount\"]}'); Start-Sleep 2" }
```

## Architecture

```
â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚  Dev Machine (PC)                            â”‚
â”‚  curl / opencode / custom script              â”‚
â”‚       â”‚                                       â”‚
â”‚       â”‚ HTTP :18500                           â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”¼â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
        â”‚
        â”‚ adb forward tcp:18500 tcp:18500
        â”‚
â”Œâ”€â”€â”€â”€â”€â”€â”€â”¼â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
â”‚  Android Device                              â”‚
â”‚  â”Œâ”€â”€â”€â”€â”´â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”    â”‚
â”‚  â”‚ Ktor Server (CIO, 127.0.0.1:18500)   â”‚    â”‚
â”‚  â”‚  â”œâ”€ /health    (no auth)             â”‚    â”‚
â”‚  â”‚  â”œâ”€ /prompt                          â”‚    â”‚
â”‚  â”‚  â”œâ”€ /history                         â”‚    â”‚
â”‚  â”‚  â”œâ”€ /state                           â”‚    â”‚
â”‚  â”‚  â”œâ”€ /tools                           â”‚    â”‚
â”‚  â”‚  â”œâ”€ /memories                        â”‚    â”‚
â”‚  â”‚  â”œâ”€ /settings                        â”‚    â”‚
â”‚  â”‚  â”œâ”€ /chat      â†’ askWithTools()      â”‚    â”‚
â”‚  â”‚  â”œâ”€ /sandbox/exec â†’ sandbox command  â”‚    â”‚
â”‚  â”‚  â”œâ”€ /settings/{key}                  â”‚    â”‚
â”‚  â”‚  â””â”€ /reset                           â”‚    â”‚
â”‚  â”‚                                       â”‚    â”‚
â”‚  â”‚  injects: DataRepository              â”‚    â”‚
â”‚  â”‚           MemoryStoreProvider          â”‚    â”‚
â”‚  â”‚           AppSettings                  â”‚    â”‚
â”‚  â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜    â”‚
â”‚                                               â”‚
â”‚  â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”    â”‚
â”‚  â”‚ DaemonService (starts/stops server)   â”‚    â”‚
â”‚  â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜    â”‚
â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
```

## Configuration

- **Port:** `18500` (hardcoded, localhost-only)
- **Host:** `127.0.0.1` (only accessible via ADB or local apps)
- **Lifecycle:** bound to daemon â€” starts when daemon starts, stops when daemon stops
- **Build gate:** only runs in debug builds (`BuildConfig.DEBUG`)
- **Auth token:** random 32-char hex, regenerated on every app launch, printed to logcat
