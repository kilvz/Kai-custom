# Desktop Version Plan — Kai-custom

## Goal

Port all Android features to desktop (Windows/Mac/Linux) without touching any Android code. Replace the Linux proot sandbox with Docker, and root/admin access with platform privilege elevation (UAC/sudo/pkexec).

---

## Architecture

```
┌──────────────────────────────────────────────┐
│                 Compose UI                    │
│          (commonMain — shared UI)             │
├──────────────────────────────────────────────┤
│              DataRepository                   │
│         ToolExecutor / ChatViewModel          │
├─────────────┬────────────────┬───────────────┤
│ androidMain │  desktopMain   │   commonMain  │
│ (unchanged) │  (new code)    │ (shared logic)│
│             │                │               │
│ proot sand  │ Docker sandbox │  SandboxCont. │
│ Shizuku     │ AdminManager   │  (interface)  │
│ DebugServer │ DebugServer    │  DebugApiCont.│
│ WakeWordSvc │ WakeWord (TBD) │  WakeWordCont.│
│ DaemonSvc   │ SystemTray     │  DaemonCont.  │
└─────────────┴────────────────┴───────────────┘
```

### Key principle
- `androidMain/` is **never modified**
- Desktop overrides via `desktopMain/` (`actual` implementations)
- Pure-JVM code shared via `jvmShared/` (already wired into both `androidMain` and `desktopMain`)

---

## Phase 0 — Foundation

### Files to create/modify

| File | Action | Purpose |
|------|--------|---------|
| `desktopMain/root/AdminManager.kt` | **Create** | Detect admin, UAC elevate, sudo/pkexec fallback |
| `desktopMain/sandbox/DockerManager.kt` | **Create** | Docker detection, auto-install, image lifecycle |
| `desktopMain/sandbox/DockerSandboxController.kt` | **Create** | Full SandboxController impl via Docker CLI |
| `desktopMain/sandbox/DockerContainerSession.kt` | **Create** | Persistent shell session inside container |
| `desktopMain/SandboxController.jvm.kt` | **Rewrite** | Replace NoOp → DockerSandboxController |
| `desktopMain/debug/DebugServerDesktop.kt` | **Create** | Port of DebugServer.kt (uses Ktor CIO on JVM) |
| `desktopMain/debug/DebugApiControllerDesktop.kt` | **Create** | Wrapper that starts/stops DebugServerDesktop |
| `desktopMain/DebugApiController.jvm.kt` | **Rewrite** | Replace NoOp → DebugApiControllerDesktop |
| `desktopMain/Platform.jvm.kt` | **Modify** | Enable root/admin, update tools, wire Docker |
| `desktopMain/main.kt` | **Modify** | Add admin-elevation-on-startup flag |
| `composeApp/build.gradle.kts` | **Modify** | Add ktor-server + docker deps to desktopMain |
| `commonMain/AppModule.kt` | **Modify** | Conditionally provide Docker vs NoOp based on platform |

### Dependencies to add (`build.gradle.kts` desktopMain)

```kotlin
desktopMain.dependencies {
    // already present: compose.desktop.currentOs, coroutines.swing, ktor-client-cio,
    //   bouncycastle, slf4j.nop, litert.lm.jvm, jsch
    // new:
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
}
```

No new third-party Docker library — we use ProcessBuilder to invoke `docker` CLI directly. This avoids library compatibility issues and works identically across all desktops that have Docker installed.

---

## Phase 1 — AdminManager (Windows UAC / sudo / pkexec)

### `AdminManager.kt`

```kotlin
class AdminManager {
    fun isAdmin(): Boolean
    fun requestElevation(command: String, args: List<String>): Result<String>
    fun relaunchAsAdmin()
}
```

**Behavior by platform:**

| OS | Detection | Elevation |
|----|-----------|-----------|
| Windows | `Shell32.IsUserAnAdmin()` via JNA or `net session` check | `ShellExecute("runas")` with temp script |
| Linux | `id -u` == 0 | `pkexec` or `sudo` via ProcessBuilder |
| macOS | `id -u` == 0 | `osascript -e 'do shell script ... with administrator privileges'` |

**How `relaunchAsAdmin()` works:**
1. Detect current JAR/exe path via `ProgramLocation` (or `java.class.path` / `java.home`)
2. On Windows: create a temp batch script, call `ShellExecute("runas")` pointing to `java.exe -jar app.jar --elevated`
3. On Linux: `pkexec java -jar app.jar --elevated` (pkexec shows GUI dialog)
4. On macOS: `osascript` with `do shell script` + admin password prompt
5. Flag `--elevated` tells the app it has admin rights (skip re-elevation)

**Koin integration:**
```kotlin
// AppModule.kt
single<AdminManager> { AdminManager() }
```

**Platform actuals updated in `Platform.jvm.kt`:**
```kotlin
actual val isRootSupported: Boolean = true   // was false
actual fun isRootAvailable(): Boolean = AdminManager().isAdmin()  // was false
```

**Tool integration:**
- Add `AdminTool` (replaces Android `RootTool`) — AI calls `run_admin_command`, AdminManager handles elevation
- Available in `getPlatformToolDefinitions()` on desktop

---

## Phase 2 — Docker Sandbox

### `DockerManager.kt`

```kotlin
class DockerManager {
    fun isDockerAvailable(): Boolean    // "docker info" exit code 0
    suspend fun installDocker(): Boolean // Windows: download Docker Desktop installer
    suspend fun pullImage(image: String): Boolean
    suspend fun createContainer(image: String, name: String, bindMounts: Map<String,String>): String // returns container ID
    suspend fun execCommand(containerId: String, command: String): String
    suspend fun execBash(containerId: String, command: String): String
    suspend fun copyIn(containerId: String, hostPath: String, containerPath: String)
    suspend fun copyOut(containerId: String, containerPath: String, hostPath: String)
    suspend fun exportContainer(containerId: String): ByteArray  // tar.gz
    suspend fun importContainer(data: ByteArray): String  // returns new container ID
    suspend fun removeContainer(containerId: String)
    suspend fun listContainers(): List<String>
    fun getContainerLogs(containerId: String): String
}
```

All operations use `ProcessBuilder("docker", ...)` — no external dependencies.

**Docker image:** `alpine:latest` (~3 MB) for lightweight; `ubuntu:22.04` optional in settings.

**Auto-install (Windows):**
1. Detect `docker` in PATH — if found, done
2. Detect Docker Desktop installed via registry: `HKLM\SOFTWARE\Docker\Docker Desktop`
3. If not installed: show dialog with download link + "Install Docker Desktop" button
4. Download from `https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe`
5. Run silent install: `Docker Desktop Installer.exe install --quiet`
6. Monitor install progress, show status in UI

### `DockerSandboxController.kt`

Implements the full `SandboxController` interface:

| Method | Docker implementation |
|--------|----------------------|
| `setup()` | `docker pull alpine:latest` → `docker run -d --name kai-sandbox alpine tail -f /dev/null` |
| `executeCommand()` | `docker exec kai-sandbox sh -c "<cmd>"` |
| `executeCommandStreaming()` | `docker exec -i kai-sandbox sh` with stdin pipe |
| `listDirectory()` | `docker exec kai-sandbox ls -la <path>` |
| `readTextFile()` | `docker exec kai-sandbox cat <path>` |
| `writeTextFile()` | `docker exec kai-sandbox sh -c "echo '...' > <path>"` |
| `writeBinaryFile()` | `docker cp <tempfile> kai-sandbox:<path>` |
| `deleteEntry()` | `docker exec kai-sandbox rm -rf <path>` |
| `renameEntry()` | `docker exec kai-sandbox mv <path> <newName>` |
| `backupSandbox()` | `docker export kai-sandbox \| gzip > <outputPath>` |
| `importSandbox()` | `docker import - kai-sandbox-import < <data>` → new container |
| `startAltMemory()` | `docker exec -d kai-sandbox alt-memory-server --port 8316` |
| `startWhatsApp()` | `docker exec -d kai-sandbox node /bridge/bridge.js` |
| `closeSession()` | Kill specific exec session |
| `transcriptFor()` | Returns exec history for session ID |

**Status mapping:**
- `installed` = container exists (`docker inspect kai-sandbox`)
- `ready` = container is running (`docker ps`)
- `working` = any long-running exec/gradle is in progress
- `packagesInstalled` = `which node python3 curl` in container
- `diskUsageMB` = `docker exec kai-sandbox du -sh /` parsed

### `DockerContainerSession.kt`

Persistent shell session within a container:

```kotlin
class DockerContainerSession(
    private val containerId: String,
    private val sessionId: String,
) : CommandHandle {
    private val process: Process  // "docker exec -i" process
    private val stdin: OutputStream
    private val transcript: SnapshotStateList<TerminalLine>
    
    override fun cancel() { process.destroy() }
    override fun writeInput(line: String) { stdin.write("$line\n".toByteArray()) }
    override suspend fun awaitExit(): Int = process.waitFor()
}
```

---

## Phase 3 — Debug API Server

### `DebugServerDesktop.kt`

Port of `androidMain/debug/DebugServer.kt` (1023 lines). The server code already uses Ktor CIO which compiles on JVM — minimal changes needed:

**Changes from Android version:**
1. Remove `WhatsAppLifecycleManager` import → replace with Docker-based WhatsApp manager (or stub)
2. Remove Android-specific endpoints: wakeword service state, battery optimization, shizuku
3. Add desktop-specific endpoints:
   - `GET /admin/status` — admin elevation state
   - `GET /docker/status` — Docker availability + container status
   - `POST /admin/elevate` — request UAC/sudo elevation
4. No import changes needed for `ContentResolver`, `MediaStore`, Android context — these are only in the Android handler code

**Constructor:**
```kotlin
class DebugServerDesktop(
    private val dataRepository: DataRepository,
    private val memoryStore: MemoryStore,
    private val appSettings: AppSettings,
    private val toolExecutor: ToolExecutor,
    private val mcpServerManager: McpServerManager,
    private val sandboxController: SandboxController,
    // No WhatsAppLifecycleManager — Docker-based via sandboxController.installWhatsAppBridge()
)
```

**Status:** ✅ Ported — ~720 LOC with all ~80 endpoints. Android-only endpoints (SMS, local inference, wake word) return stubs with sensible defaults.

### `DebugApiControllerDesktop.kt`

```kotlin
class DebugApiControllerDesktop(
    private val dataRepository: DataRepository,
    private val memoryStore: MemoryStore,
    private val appSettings: AppSettings,
    private val toolExecutor: ToolExecutor,
    private val mcpServerManager: McpServerManager,
    private val sandboxController: SandboxController,
) : DebugApiController {
    private var server: EmbeddedServer<*, *>? = null
    override var isRunning: Boolean = false
    override var isTransitioning: Boolean = false
    
    override fun start() {
        server = embeddedServer(CIO, port = 18500, host = "127.0.0.1") {
            // routing from DebugServerDesktop
        }.start(wait = false)
        isRunning = true
    }
    override fun stop() {
        server?.stop(1000, 2000)
        isRunning = false
    }
}
```

### Koin wiring (update to `AppModule.kt`)

```kotlin
// Desktop provides DebugApiControllerDesktop instead of NoOpDebugApiController
// This happens via expect/actual:
//   commonMain: expect fun createDebugApiController(): DebugApiController
//   desktopMain: actual fun createDebugApiController() = DebugApiControllerDesktop(...)
//   androidMain: actual fun createDebugApiController() = AndroidDebugApiController(...)
```

The `createDebugApiController()` expect/actual factory is how desktop will wire in all the injected dependencies. The `DebugApiControllerDesktop` needs the same deps as `AndroidDebugApiController`.

**Current desktop actual:**
```kotlin
// DebugApiController.jvm.kt (current — no-op)
actual fun createDebugApiController(): DebugApiController = NoOpDebugApiController()
```

**New desktop actual:**
```kotlin
// DebugApiController.jvm.kt (new)
actual fun createDebugApiController(): DebugApiController {
    val dataRepo: DataRepository by inject(DataRepository::class.java)
    val memoryStore: MemoryStore by inject(MemoryStore::class.java)
    val appSettings: AppSettings by inject(AppSettings::class.java)
    val toolExecutor: ToolExecutor by inject(ToolExecutor::class.java)
    val mcpServerManager: McpServerManager by inject(McpServerManager::class.java)
    val sandboxController: SandboxController by inject(SandboxController::class.java)
    return DebugApiControllerDesktop(dataRepo, memoryStore, appSettings, toolExecutor, mcpServerManager, sandboxController)
}
```

---

## Phase 4 — Move Tools to `jvmShared`

### Files to move from `androidMain` → `jvmShared`

| File | Reason | ETA |
|------|--------|-----|
| `tools/FileTools.kt` | Pure JVM (file I/O, glob, grep, patch) | Easy |
| `tools/WebTools.kt` | Pure Ktor HTTP | Easy |
| `tools/SshCommandTool.kt` | Uses JSch (already desktop dep) | Easy |
| `tools/SshConnectTool.kt` | Uses SshConnectionManager (desktop has impl) | Easy |
| `tools/SshConfigureHostTool.kt` | Uses SshConnectionManager | Easy |

### Files to create new desktop implementations for

| File | Purpose |
|------|---------|
| `desktopMain/tools/OpenFileToolDesktop.kt` | Open file via `java.awt.Desktop.getDesktop().open()` |
| `desktopMain/tools/SpeakTextToolDesktop.kt` | TTS via `nl.marc-apps.tts` |

### Files that remain no-op (correct for desktop)

| File | Reason |
|------|--------|
| `CalendarPermissionController.jvm.kt` | OS-level calendar access is OS-specific, no viable cross-platform API |
| `SmsPermissionController.jvm.kt` | Desktop has no SMS |
| `SmsSender.jvm.kt` / `SmsReader.jvm.kt` | Desktop has no SMS |
| `NotificationReader.jvm.kt` | OS notification reading is platform-specific |
| `MicrophonePermissionController.jvm.kt` | Audio capture needs platform-specific handling |

---

## Phase 5 — WhatsApp Bridge (Docker-hosted)

### Docker-based WhatsApp lifecycle

```kotlin
// DockerWhatsAppManager.kt
class DockerWhatsAppManager(private val dockerManager: DockerManager) {
    private val containerId = "kai-sandbox"
    
    suspend fun installBridgeJs(): Boolean {
        // Copy bridge.js to container via docker cp
        val bridgeJs = BridgeJsSource.getBridgeJs()
        val tempFile = createTempFile("bridge.js")
        tempFile.writeBytes(bridgeJs.decodeBase64())
        dockerManager.copyIn(containerId, tempFile.absolutePath, "/root/whatsapp-bridge/bridge.js")
        return true
    }
    
    suspend fun startBridge(): Boolean {
        return dockerManager.execBash(containerId, "node /root/whatsapp-bridge/bridge.js &")
    }
}
```

### `WhatsAppLifecycleManager.jvm.kt`
```kotlin
actual class WhatsAppLifecycleManager { ... } // or inject via Koin
```

---

## Phase 6 — Alt-Memory (Dedicated Docker Container)

Alt-memory runs as its own Docker container (not inside the sandbox container), built from `kilv/alt-memory:full` on Docker Hub:

```kotlin
// AltMemoryDockerManager.kt
class AltMemoryDockerManager(private val dockerManager: DockerManager) {
    suspend fun pullImage()       // docker pull kilv/alt-memory:full
    suspend fun startContainer()  // docker run -d --name kai-alt-memory -p 8316:8316 ...
    suspend fun stopContainer()   // docker stop kai-alt-memory
    suspend fun checkHealth()     // GET http://localhost:8316/health
}
```

Container command: `alt-memory mcp --host 0.0.0.0 --port 8316 --transport sse`

**Benefits over inline approach:**
- `--restart unless-stopped` keeps it alive across crashes
- Named volume `alt-memory-data:/root/.alt-memory` survives restarts
- No `&` in docker exec (broken — process dies when exec session ends)
- Clean separation from sandbox container
- Pre-built image on Docker Hub (no local build needed)

**Port mapping:** host `8316` → container `8316`

---

## Phase 7 — Wake Word (Desktop)

Keep no-op initially. Future implementation options:
- Vosk lightweight model (offline, ~50MB)
- Whisper.cpp (offline, ~80MB)
- Platform speech recognizer APIs

---

## Phase 8 — Build & Package

### Gradle tasks

```bash
# Run directly (dev)
./gradlew :composeApp:run -PdesktopDebug

# Windows installer
./gradlew :composeApp:packageMsi

# macOS DMG
./gradlew :composeApp:packageDmg

# Linux
./gradlew :composeApp:packageDeb
./gradlew :composeApp:packageRpm
./gradlew :composeApp:packageAppImage
```

### CI additions (`.github/workflows/release.yml`)

The existing release workflow already has `dmg`/`msi`/`deb`/`rpm`/`appimage` jobs with `continue-on-error: true`. Desktop code changes will flow through automatically.

### Required packaging extras

- **Windows MSI**: Include Docker Desktop prerequisite check in the installer
- **Linux DEB/RPM**: Add `docker.io` or `docker-ce` as a suggested dependency
- **macOS DMG**: Homebrew `docker` as prerequisite

---

## Migration Timeline (Actual June 2026)

| Phase | Content | Status | Files | Est. LOC |
|-------|---------|--------|-------|----------|
| 0 | Foundation (build config, AdminManager, Docker, DebugServer) | ✅ Done | 11 | 950 |
| 1 | Move tools to jvmShared (FileTools, WebTools, SSH tools) | ✅ Done | 6 | 850 |
| 2 | AdminTool + OpenFileTool (desktop actuals) | ✅ Done | 2 | 145 |
| 3 | WhatsApp Docker bridge (port mappings + npm install) | ✅ Done | 1 | 7 |
| 4 | Build: createDistributable + packageMsi | ✅ Done | - | - |
| 5 | Debug API server port (full parity) | ✅ Done | 3 | 720 |
| 6 | Alt-Memory Docker (full parity) | ✅ Done | 2 | 160 |
| 7 | Wake Word (stub) | ✅ Done | 1 | 14 |

**Completed: ~3566 LOC across ~39 files**

---

## Files NOT to touch

- `androidMain/` — all files (never modified)
- `commonMain/` — only `AppModule.kt` for conditional Koin wiring
- `iosMain/`, `wasmJsMain/` — unrelated targets
- `commonMain/Platform.kt` — expect declarations stay unchanged

---

## Risk Assessment

| Risk | Mitigation |
|------|-----------|
| Docker not available on user machine | Auto-install flow + clear UI guidance |
| UAC prompt fatigue | Only elevate when admin is actually needed, cache elevated token for session |
| DebugServerDesktop diverges from Android version | Keep common route handlers in `commonMain`, platform-specific parts in `desktopMain` |
| Docker exec not identical to proot behavior | Map all SandboxController methods carefully, test each |
| Port conflicts (18500 already in use) | Auto-detect and use next available port |
