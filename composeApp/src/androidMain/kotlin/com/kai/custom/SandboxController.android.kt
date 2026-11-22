package com.kai.custom

import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.kai.custom.data.AppSettings
import com.kai.custom.data.DataRepository
import com.kai.custom.data.MemoryStoreProvider
import com.kai.custom.linux.LinuxDistro
import com.kai.custom.mcp.AltMemoryLifecycleManager
import com.kai.custom.mcp.McpServerManager
import com.kai.custom.sandbox.LinuxSandboxManager
import com.kai.custom.sandbox.ProotExecutor
import com.kai.custom.sandbox.SandboxState
import com.kai.custom.sandbox.SessionShell
import com.kai.custom.sandbox.decodeUtf8Strict
import com.kai.custom.sandbox.importFileInto
import com.kai.custom.sandbox.openFileWithIntent
import com.kai.custom.sandbox.toFileEntry
import com.kai.custom.shizuku.ShizukuManager
import com.kai.custom.whatsapp.WhatsAppLifecycleManager
import io.github.vinceglb.filekit.PlatformFile
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.terminal_sandbox_not_ready
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.encoding.Base64

actual fun createSandboxController(): SandboxController = AndroidSandboxController()

class AndroidSandboxController : SandboxController {

    private val sandboxManager: LinuxSandboxManager by inject(LinuxSandboxManager::class.java)
    private val context: Context by inject(Context::class.java)
    private val mcpServerManager: McpServerManager by inject(McpServerManager::class.java)
    private val memoryStore: MemoryStoreProvider by inject(MemoryStoreProvider::class.java)
    private val appSettings: AppSettings by inject(AppSettings::class.java)
    private val dataRepository: DataRepository by inject(DataRepository::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val altMemoryLifecycle by lazy { AltMemoryLifecycleManager(this, mcpServerManager, appSettings, memoryStore, dataRepository) }
    private val whatsAppLifecycle by lazy { WhatsAppLifecycleManager(this, mcpServerManager, appSettings, com.kai.custom.data.WhatsAppStore(appSettings)) }

    private var cachedDiskUsageMB = 0L
    private var previousState: SandboxState? = null
    private val _status = MutableStateFlow(SandboxStatus())
    override val status: StateFlow<SandboxStatus> = _status
    override val sessions: StateFlow<List<String>> = sandboxManager.sessions

    init {
        // Synchronously seed the status from the manager's current state so the
        // first observer doesn't briefly see "not installed" before the launched
        // collector below catches up. Skip the disk-usage walk in this fast path —
        // it iterates the rootfs and could block the calling thread (often main,
        // since Koin singletons are created lazily on first injection from
        // Composables). The launched collect immediately re-emits the same state
        // and fills in disk usage on Dispatchers.IO.
        _status.value = quickStatus(sandboxManager.state.value)
        // Leave previousState null so the launched collect's first mapState(Ready)
        // computes disk usage on IO.

        scope.launch {
            sandboxManager.state.collect { state ->
                try {
                    val oldReady = previousState is SandboxState.Ready
                    _status.value = mapState(state)
                    if (!oldReady && state is SandboxState.Ready) {
                        scope.launch {
                            altMemoryLifecycle.verifyInstalled()
                            if (appSettings.isAltMemoryEnabled()) {
                                altMemoryLifecycle.setupAndStart()
                            }
                            if (appSettings.isWhatsAppEnabled() && appSettings.isWhatsAppInstalled()) {
                                whatsAppLifecycle.setupAndStart()
                            }
                            // If verifyInstalled reset the flag, re-emit so the UI picks it up.
                            _status.value = mapState(state)
                        }
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("SandboxController", "mapState failed for $state", e)
                    _status.value = SandboxStatus(
                        distro = sandboxManager.distro,
                        error = true,
                        label = SandboxStatusLabel.Failure.Status(e.message ?: e::class.simpleName.orEmpty()),
                    )
                }
                previousState = state
                // Only a settled sandbox can have gained or lost migratable
                // files, and this walks another whole home — so it never runs
                // during an install's progress updates.
                if (!_status.value.working) refreshMigration()
            }
        }
        refreshMigration()
    }

    /**
     * Re-answers "is there anything to copy over?" on IO and patches it into the
     * status. Kept out of the status mapping itself: that mapping runs on the
     * calling thread for the first paint and for a distribution switch, and this
     * walk is unbounded.
     */
    private fun refreshMigration() {
        scope.launch {
            val distro = sandboxManager.distro
            val migration = runCatching { sandboxManager.surveyMigration() }.getOrNull()
            // A switch that landed while the walk ran has already published its
            // own status, and this answer describes the install we have left —
            // so it must not be published *or* cached for the next status build.
            if (sandboxManager.distro != distro) return@launch
            knownMigration = migration
            _status.update { it.copy(migration = migration) }
        }
    }

    private fun mapState(state: SandboxState): SandboxStatus = when (state) {
        is SandboxState.NotInstalled -> status(label = SandboxStatusLabel.NotInstalled)

        is SandboxState.Downloading -> status(
            working = true,
            progress = state.progress,
            label = SandboxStatusLabel.Downloading,
        )

        is SandboxState.Extracting -> status(working = true, label = SandboxStatusLabel.Extracting)

        is SandboxState.Installing -> status(
            installed = java.io.File(sandboxManager.rootfsPath).isDirectory,
            working = true,
            label = state.label,
            diskUsageMB = cachedDiskUsageMB,
        )

        is SandboxState.Ready -> {
            if (previousState !is SandboxState.Ready) {
                cachedDiskUsageMB = sandboxManager.getDiskUsageMB()
            }
            readyStatus(diskUsageMB = cachedDiskUsageMB)
        }

        is SandboxState.Error -> {
            val rootfsExists = java.io.File(sandboxManager.rootfsPath).isDirectory
            val msg = state.label.detail
            SandboxStatus(
                distro = sandboxManager.distro,
                installed = rootfsExists,
                ready = rootfsExists,
                error = true,
                label = state.label,
                diskUsageMB = cachedDiskUsageMB,
                // Force packagesInstalled=false so the Install Packages button
                // reappears and the user can retry after a failed install.
                packagesInstalled = false,
                needsReset = msg.contains("dpkg", ignoreCase = true) || msg.contains("sub-process", ignoreCase = true),
            )
        }
    }

    /**
     * The same mapping without the disk-usage walk, for the paths that have to
     * answer on the calling thread: process start, and a distribution switch —
     * where the buttons must stop describing the outgoing rootfs immediately.
     */
    private fun quickStatus(state: SandboxState): SandboxStatus = if (state is SandboxState.Ready) readyStatus() else mapState(state)

    private fun readyStatus(diskUsageMB: Long = 0) = status(
        installed = true,
        ready = true,
        label = SandboxStatusLabel.Ready,
        diskUsageMB = diskUsageMB,
        packagesInstalled = sandboxManager.arePackagesInstalled(),
    )

    /**
     * Last read of which distributions exist on disk. Only a settled state can
     * have changed that, and the picker consuming it is hidden while an install
     * runs — so a download's per-chunk progress updates do not each re-read both
     * install markers.
     */
    private var knownInstalledDistros: Set<LinuxDistro> = emptySet()

    private fun installedDistros(working: Boolean): Set<LinuxDistro> {
        if (!working) knownInstalledDistros = sandboxManager.installedDistros()
        return knownInstalledDistros
    }

    /** Last answer from [refreshMigration], so rebuilding a status never walks. */
    private var knownMigration: SandboxMigration? = null

    /** Every status carries the same view of which distributions exist on disk. */
    private fun status(
        installed: Boolean = false,
        ready: Boolean = false,
        working: Boolean = false,
        progress: Float? = null,
        label: SandboxStatusLabel? = null,
        diskUsageMB: Long = 0,
        packagesInstalled: Boolean = false,
        error: Boolean = false,
    ) = SandboxStatus(
        installed = installed,
        ready = ready,
        working = working,
        progress = progress,
        label = label,
        diskUsageMB = diskUsageMB,
        packagesInstalled = packagesInstalled,
        error = error,
        distro = sandboxManager.distro,
        installedDistros = installedDistros(working),
        migration = knownMigration,
    )

    override fun setup() {
        sandboxManager.setup()
    }

    override fun cancel() {
        sandboxManager.cancel()
    }

    override fun reset() {
        sandboxManager.reset()
    }

    override fun restart() {
        sandboxManager.restart()
    }

    override fun selectDistro(distro: LinuxDistro) {
        sandboxManager.selectDistro(distro)
        // The manager's state can land on the same value it already held (both
        // distributions uninstalled, say), and a StateFlow would not re-emit —
        // so the switch is published here rather than waited for.
        cachedDiskUsageMB = 0
        previousState = null
        // The outgoing install's answer describes the wrong direction now.
        knownMigration = null
        _status.value = quickStatus(sandboxManager.state.value)
        scope.launch {
            val state = sandboxManager.state.value
            _status.value = mapState(state)
            previousState = state
        }
        refreshMigration()
    }

    override fun migrateHome() {
        sandboxManager.migrateHome()
    }

    override fun installPackages() {
        sandboxManager.installPackages()
    }

    override fun closeSession(sessionId: String) {
        sandboxManager.closeShell(sessionId)
    }

    override fun transcriptFor(sessionId: String): SnapshotStateList<com.kai.custom.TerminalLine> = sandboxManager.transcriptFor(sessionId)

    override fun clearTranscript(sessionId: String) {
        sandboxManager.clearTranscript(sessionId)
    }

    override suspend fun executeCommand(command: String, sessionId: String, useRoot: Boolean, timeoutSeconds: Long): String = withContext(Dispatchers.IO) {
        val state = sandboxManager.state.value
        val rootfsExists = java.io.File(sandboxManager.rootfsPath).isDirectory
        if (state !is SandboxState.Ready && !(state is SandboxState.Error && rootfsExists)) return@withContext SANDBOX_NOT_READY

        if (!useRoot) {
            val executor = sandboxManager.createProotExecutor(rootEnabled = false)
            val result = executor.execute(command, timeoutSeconds = timeoutSeconds)
            val stdout = result["stdout"] as? String ?: ""
            val stderr = result["stderr"] as? String ?: ""
            val exitCode = result["exit_code"] as? Int
            val error = result["error"] as? String
            return@withContext buildString {
                if (stdout.isNotEmpty()) append(stdout)
                if (stderr.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append(stderr)
                }
                if (error != null) {
                    if (isNotEmpty()) append("\n")
                    append(error)
                }
                if (exitCode != null && exitCode != 0 && isEmpty()) {
                    append("Exit code: $exitCode")
                }
            }
        }

        val result = sandboxManager.shellFor(sessionId).run(command, timeoutSeconds = timeoutSeconds)

        val stdout = result["stdout"] as? String ?: ""
        val stderr = result["stderr"] as? String ?: ""
        val exitCode = result["exit_code"] as? Int
        val error = result["error"] as? String

        buildString {
            if (stdout.isNotEmpty()) append(stdout)
            if (stderr.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(stderr)
            }
            if (error != null) {
                if (isNotEmpty()) append("\n")
                append(error)
            }
            if (exitCode != null && exitCode != 0 && isEmpty()) {
                append("Exit code: $exitCode")
            }
        }
    }

    override suspend fun executeCommandStructured(
        command: String,
        sessionId: String,
        useRoot: Boolean,
        timeoutSeconds: Long,
    ): ExecResult = withContext(Dispatchers.IO) {
        val state = sandboxManager.state.value
        val rootfsExists = java.io.File(sandboxManager.rootfsPath).isDirectory
        if (state !is SandboxState.Ready && !(state is SandboxState.Error && rootfsExists)) {
            return@withContext ExecResult(error = "Sandbox not ready")
        }
        val executor = sandboxManager.createProotExecutor()
        val result = executor.execute(command, timeoutSeconds = timeoutSeconds)
        ExecResult(
            success = result["success"] as? Boolean == true,
            stdout = result["stdout"] as? String ?: "",
            stderr = result["stderr"] as? String ?: "",
            exitCode = result["exit_code"] as? Int,
            error = result["error"] as? String,
        )
    }

    override suspend fun executeCommandStreaming(
        command: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        sessionId: String,
    ): CommandHandle {
        val state = sandboxManager.state.value
        val rootfsExists = java.io.File(sandboxManager.rootfsPath).isDirectory
        if (state !is SandboxState.Ready && !(state is SandboxState.Error && rootfsExists)) {
            // Streaming output is read by a person in the terminal, unlike
            // [executeCommand]'s return value, which is an agent tool result.
            onStderr(getString(Res.string.terminal_sandbox_not_ready))
            return NoOpCommandHandle
        }
        val shell = sandboxManager.shellFor(sessionId)
        val deferred = CompletableDeferred<Map<String, Any>>()
        val cancelled = AtomicBoolean(false)
        // No implicit timeout in the streaming path — UI cancel + process exit
        // are the real "done" signals. The persistent shell still recovers
        // from a wedged shell via reset() on the next call.
        val streamingTimeoutSeconds = 24L * 60 * 60
        scope.launch {
            runCatching {
                shell.run(
                    command = command,
                    timeoutSeconds = streamingTimeoutSeconds,
                    onStdout = onStdout,
                    onStderr = onStderr,
                )
            }.onSuccess { deferred.complete(it) }
                .onFailure { deferred.complete(mapOf("exit_code" to -1)) }
        }
        return PersistentCommandHandle(shell, deferred, cancelled)
    }

    override suspend fun listDirectory(path: String): List<SandboxFileEntry> = withContext(Dispatchers.IO) {
        val dir = sandboxManager.fileMap().resolve(path)
            ?: return@withContext emptyList()

        val normalized = if (path.endsWith("/")) path.dropLast(1) else path
        val isRoot = normalized.isEmpty() || normalized == "/"
        // On a legacy install /root is bound in from external storage, so the
        // rootfs listing shows an empty mount point — swap it for the real thing.
        val homeIsSeparate = isRoot && !sandboxManager.homeOnRootfs

        if (!isRoot && isOnHostStorage(dir)) {
            return@withContext listHostDirectoryViaSandbox(normalized, dir.absolutePath)
        }

        if (!dir.isDirectory) return@withContext emptyList()

        val children = dir.listFiles().orEmpty()
            .filterNot { homeIsSeparate && it.name == "root" }
            .map { it.toFileEntry(parent = if (isRoot) "" else normalized) }
            .toMutableList()

        if (homeIsSeparate) {
            val home = File(sandboxManager.homePath)
            if (home.isDirectory) {
                children.add(
                    SandboxFileEntry(
                        name = "root",
                        path = "/root",
                        isDirectory = true,
                        sizeBytes = 0,
                        lastModifiedMs = home.lastModified(),
                    ),
                )
            }
            val sdcardHost = when {
                File("/storage/emulated/0").exists() -> "/storage/emulated/0"
                File("/storage/self/primary").exists() -> "/storage/self/primary"
                else -> null
            }
            if (sdcardHost != null) {
                val sdcard = File(sdcardHost)
                children.add(
                    SandboxFileEntry(
                        name = "sdcard",
                        path = "/sdcard",
                        isDirectory = true,
                        sizeBytes = 0,
                        lastModifiedMs = sdcard.lastModified(),
                    ),
                )
            }
        }
        children.sortedWith(
            compareByDescending<SandboxFileEntry> { it.isDirectory }
                .thenBy { it.name.lowercase() },
        )
    }

    // ── host storage helpers (sandbox shell bypasses scoped storage) ──

    companion object {
        private val HOST_STORAGE_PREFIXES = arrayOf("/storage/emulated/", "/storage/self/", "/data/media/")
    }

    private fun isOnHostStorage(file: File): Boolean = HOST_STORAGE_PREFIXES.any { file.absolutePath.startsWith(it) }

    private fun shellEscaped(path: String): String = path.replace("'", "'\\''")

    // Shell mirror of readFileAsText: same TextFileResult distinctions, but through
    // the sandbox shell because the app process cannot read root-owned rootfs files
    // directly via java.io.File.
    private suspend fun readHostTextFileViaSandbox(filePath: String, maxBytes: Int, force: Boolean): TextFileResult {
        val safe = shellEscaped(filePath)
        // Size first: stat reports -1 for a missing path or a non-regular file.
        val sizeResult = executeCommandStructured(
            "f='$safe'; if [ -f \"\$f\" ]; then stat -c%s \"\$f\" 2>/dev/null || echo -1; else echo -1; fi",
            useRoot = true,
            timeoutSeconds = 30,
        )
        val size = sizeResult.stdout.trim().toLongOrNull() ?: -1
        if (size < 0) return TextFileResult.Unreadable
        if (size > maxBytes) return TextFileResult.TooLarge(size)
        val result = executeCommandStructured("base64 < '$safe' 2>/dev/null", useRoot = true, timeoutSeconds = 30)
        if (result.exitCode != 0) return TextFileResult.Unreadable
        val bytes = try {
            Base64.decode(result.stdout.replace("\n", "").replace("\r", ""))
        } catch (_: Exception) {
            return TextFileResult.Unreadable
        }
        val strict = if (bytes.any { it == 0.toByte() }) null else decodeUtf8Strict(bytes)
        if (strict != null) return TextFileResult.Text(strict, editable = true)
        if (!force) return TextFileResult.Binary
        // Forced: decode lossily so the user can at least look at it. Not editable.
        return TextFileResult.Text(bytes.toString(Charsets.UTF_8), editable = false)
    }

    private suspend fun writeHostTextFileViaSandbox(filePath: String, content: String): Boolean {
        val safe = shellEscaped(filePath)
        val b64 = Base64.encode(content.encodeToByteArray())
        val cmd = "mkdir -p \"\$(dirname '$safe')\" && echo '$b64' | base64 -d > '$safe'"
        val result = executeCommandStructured(cmd, useRoot = true, timeoutSeconds = 30)
        return result.exitCode == 0
    }

    private suspend fun writeHostBinaryViaSandbox(filePath: String, data: ByteArray): Boolean {
        val safe = shellEscaped(filePath)
        val b64 = Base64.encode(data)
        val cmd = "mkdir -p \"\$(dirname '$safe')\" && echo '$b64' | base64 -d > '$safe'"
        val result = executeCommandStructured(cmd, useRoot = true, timeoutSeconds = 30)
        return result.exitCode == 0
    }

    private suspend fun deleteHostEntryViaSandbox(filePath: String, recursive: Boolean): Boolean {
        val safe = shellEscaped(filePath)
        val rm = if (recursive) "rm -rf" else "rm -f"
        val cmd = "$rm '$safe'"
        val result = executeCommandStructured(cmd, useRoot = true, timeoutSeconds = 30)
        return result.exitCode == 0
    }

    private suspend fun listHostDirectoryViaSandbox(
        sandboxPath: String,
        hostPath: String,
    ): List<SandboxFileEntry> {
        val safe = shellEscaped(hostPath)
        val cmd = buildString {
            append("d='$safe'\n")
            append("cd \"\$d\" 2>/dev/null || { echo '____KAI_LS_FAIL____'; exit 1; }\n")
            append("for e in * .*; do\n")
            append("  [ \"\$e\" = \".\" ] && continue\n")
            append("  [ \"\$e\" = \"..\" ] && continue\n")
            append("  [ -e \"\$e\" ] || continue\n")
            append("  if [ -d \"\$e\" ]; then\n")
            append("    printf 'D\\0%s\\0%s\\0' \"\$e\" \"\$(stat -c '%Y' \"\$e\" 2>/dev/null || echo 0)\"\n")
            append("  else\n")
            append("    printf 'F\\0%s\\0%s\\0%s\\0' \"\$e\" \"\$(stat -c '%s' \"\$e\" 2>/dev/null || echo 0)\" \"\$(stat -c '%Y' \"\$e\" 2>/dev/null || echo 0)\"\n")
            append("  fi\n")
            append("done\n")
        }
        val result = executeCommandStructured(cmd, useRoot = true, timeoutSeconds = 30)
        if (result.exitCode != 0 || result.stdout.startsWith("____KAI_LS_FAIL____")) return emptyList()

        val parts = result.stdout.split('\u0000')
        val entries = mutableListOf<SandboxFileEntry>()
        val normalized = if (sandboxPath.endsWith("/")) sandboxPath.dropLast(1) else sandboxPath

        var i = 0
        while (i < parts.size) {
            when (parts[i]) {
                "D" -> {
                    val name = parts.getOrNull(i + 1) ?: break
                    val mtime = parts.getOrNull(i + 2)?.toLongOrNull() ?: 0L
                    entries.add(
                        SandboxFileEntry(
                            name = name,
                            path = "$normalized/$name",
                            isDirectory = true,
                            sizeBytes = 0,
                            lastModifiedMs = mtime * 1000L,
                        ),
                    )
                    i += 3
                }

                "F" -> {
                    val name = parts.getOrNull(i + 1) ?: break
                    val size = parts.getOrNull(i + 2)?.toLongOrNull() ?: 0L
                    val mtime = parts.getOrNull(i + 3)?.toLongOrNull() ?: 0L
                    entries.add(
                        SandboxFileEntry(
                            name = name,
                            path = "$normalized/$name",
                            isDirectory = false,
                            sizeBytes = size,
                            lastModifiedMs = mtime * 1000L,
                        ),
                    )
                    i += 4
                }

                else -> break
            }
        }
        return entries
    }

    override suspend fun readTextFile(path: String, maxBytes: Int, force: Boolean): TextFileResult = withContext(Dispatchers.IO) {
        val file = sandboxManager.fileMap().resolve(path)
            ?: return@withContext TextFileResult.Unreadable
        // Rootfs inside proot is owned by root:1078 — the app process
        // can't access it directly via Java File. Always use sandbox shell.
        // Use the SANDBOX path (not host path) — proot translates it correctly
        // via bind-mounts (e.g. /root → homePath, /sdcard → /storage/emulated/0).
        readHostTextFileViaSandbox(path, maxBytes, force)
    }

    override suspend fun writeTextFile(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val file = sandboxManager.fileMap().resolve(path)
            ?: return@withContext false
        // Use sandbox path — proot translates via bind-mounts.
        writeHostTextFileViaSandbox(path, content)
    }

    override suspend fun writeBinaryFile(path: String, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val file = sandboxManager.fileMap().resolve(path)
            ?: return@withContext false
        // Use sandbox path — proot translates via bind-mounts.
        writeHostBinaryViaSandbox(path, data)
    }

    override suspend fun openFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        val file = sandboxManager.fileMap().resolve(path)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid path: $path"))
        if (!file.isFile) return@withContext Result.failure(IllegalArgumentException("Not a file: $path"))
        val result = openFileWithIntent(context, file)
        if (result.success) Result.success(Unit) else Result.failure(IllegalStateException(result.error ?: "Open failed"))
    }

    override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = withContext(Dispatchers.IO) {
        val file = sandboxManager.fileMap().resolve(path)
            ?: return@withContext false
        if (isOnHostStorage(file)) {
            return@withContext deleteHostEntryViaSandbox(file.absolutePath, recursive)
        }
        if (!file.exists()) return@withContext false
        // Refuse to delete the bind roots themselves.
        if (sandboxManager.fileMap().isRoot(file)) return@withContext false
        when {
            file.isDirectory && !recursive -> {
                val empty = file.list()?.isEmpty() != false
                if (empty) file.delete() else false
            }

            file.isDirectory -> file.deleteRecursively()

            else -> file.delete()
        }
    }

    override suspend fun importFile(directoryPath: String, source: PlatformFile): Result<String> = withContext(Dispatchers.IO) {
        val dir = sandboxManager.fileMap().resolve(directoryPath)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid path: $directoryPath"))
        try {
            val created = importFileInto(dir, source)
            val parent = directoryPath.trimEnd('/')
            Result.success(if (parent.isEmpty()) "/${created.name}" else "$parent/${created.name}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renameEntry(path: String, newName: String): Result<String> = withContext(Dispatchers.IO) {
        if (newName.isBlank() || newName.contains('/') || newName.contains('\\') ||
            newName == "." || newName == ".."
        ) {
            return@withContext Result.failure(IllegalArgumentException("Invalid name"))
        }
        val src = sandboxManager.fileMap().resolve(path)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid path"))
        if (!src.exists()) return@withContext Result.failure(IllegalArgumentException("Not found"))
        if (sandboxManager.fileMap().isRoot(src)) {
            return@withContext Result.failure(IllegalArgumentException("Cannot rename sandbox root"))
        }
        val parentSandbox = path.substringBeforeLast('/', "")
        val newSandboxPath = if (parentSandbox.isEmpty()) "/$newName" else "$parentSandbox/$newName"
        val dest = sandboxManager.fileMap().resolve(newSandboxPath)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid destination"))
        if (dest.exists()) return@withContext Result.failure(IllegalStateException("collision"))
        if (src.renameTo(dest)) {
            Result.success(newSandboxPath)
        } else {
            Result.failure(IllegalStateException("rename failed"))
        }
    }

    // ── memory semantic search ─────────────────────────────────────────
    // Semantic search is now handled by alt-memory MCP tools when connected.
    // Falls through to FTS5 local search (interface default returns null).

    override suspend fun startAltMemory() {
        android.util.Log.i("SandboxController", "startAltMemory called, sandboxReady=${_status.value.ready}")
        altMemoryLifecycle.setupAndStart()
    }

    override suspend fun installAltMemoryPackage(): Boolean {
        android.util.Log.i("SandboxController", "installAltMemoryPackage called")
        return altMemoryPipeline(force = false)
    }

    override suspend fun updateAltMemoryPackage(): Boolean {
        android.util.Log.i("SandboxController", "updateAltMemoryPackage called")
        return altMemoryPipeline(force = true)
    }

    override suspend fun getAltMemoryVersions(): Pair<String?, String?> {
        val current = sandboxManager.state.value
        if (current !is SandboxState.Ready) return Pair(null, null)
        return withContext(Dispatchers.IO) {
            val executor = sandboxManager.createProotExecutor()
            val currentVersion = try {
                val r = executor.execute(
                    "python3 -c 'import alt_memory; print(getattr(alt_memory, \"__version__\", \"0.0.0\"))' 2>/dev/null",
                    timeoutSeconds = 15,
                )
                (r["stdout"] as? String)?.trim()?.ifEmpty { null }
            } catch (_: Exception) {
                null
            }

            val latestVersion = try {
                val r = executor.execute(
                    "curl -s https://pypi.org/pypi/alt-memory/json | python3 -c \"import sys,json; print(json.load(sys.stdin)['info']['version'])\" 2>/dev/null",
                    timeoutSeconds = 30,
                )
                (r["stdout"] as? String)?.trim()?.ifEmpty { null }
            } catch (_: Exception) {
                null
            }

            Pair(currentVersion, latestVersion)
        }
    }

    private suspend fun altMemoryPipeline(force: Boolean): Boolean {
        val current = sandboxManager.state.value
        if (current !is SandboxState.Ready) {
            android.util.Log.w("SandboxController", "altMemoryPipeline: sandbox not ready, state=$current")
            return false
        }

        fun updateStatus(
            working: Boolean = false,
            error: Boolean = false,
            progress: Float? = null,
            label: SandboxStatusLabel? = null,
        ) {
            _status.value = _status.value.copy(
                working = working,
                error = error,
                progress = progress,
                label = label ?: _status.value.label,
            )
        }

        return withContext(Dispatchers.IO) {
            val executor = sandboxManager.createProotExecutor()

            if (!force) {
                updateStatus(working = true, label = SandboxStatusLabel.Configuring)
                val check = executor.execute("python3 -c 'import alt_memory; print(1)' 2>/dev/null", timeoutSeconds = 30)
                if (check["success"] as? Boolean == true && (check["stdout"] as? String)?.trim() == "1") {
                    appSettings.setAltMemoryInstalled(true)
                    updateStatus()
                    android.util.Log.i("SandboxController", "Alt-Memory already installed")
                    return@withContext true
                }
            }

            // Phase 1 — download-only: safe, never touches site-packages
            val actionLabel = if (force) "Updating" else "Installing"
            updateStatus(working = true, label = SandboxStatusLabel.Downloading)
            executor.execute("mkdir -p /tmp/pip-download", timeoutSeconds = 5)
            val download = executor.execute(
                "pip download --no-cache-dir --retries 10 --timeout 60 --dest /tmp/pip-download alt-memory 2>&1",
                timeoutSeconds = 600,
            )
            if (download["success"] as? Boolean != true) {
                val stderr = download["stderr"] as? String ?: ""
                val stdout = download["stdout"] as? String ?: ""
                val error = download["error"] as? String ?: ""
                val msg = stderr.ifEmpty { error }.ifEmpty { stdout }.take(200)
                android.util.Log.e("SandboxController", "pip download failed: $msg")
                updateStatus(error = true, label = SandboxStatusLabel.Failure.Status(detail = "Alt-Memory download failed: $msg"))
                return@withContext false
            }

            // Phase 2 — install from local cache: fast, no network
            updateStatus(working = true, label = SandboxStatusLabel.Installing)
            val install = executor.execute(
                "pip install --no-cache-dir --break-system-packages --no-index --find-links /tmp/pip-download alt-memory 2>&1",
                timeoutSeconds = 300,
            )
            executor.execute("rm -rf /tmp/pip-download", timeoutSeconds = 5)
            if (install["success"] as? Boolean != true) {
                val stderr = install["stderr"] as? String ?: ""
                val stdout = install["stdout"] as? String ?: ""
                val error = install["error"] as? String ?: ""
                val msg = stderr.ifEmpty { error }.ifEmpty { stdout }.take(200)
                android.util.Log.e("SandboxController", "pip install failed: $msg")
                updateStatus(error = true, label = SandboxStatusLabel.Failure.Status(detail = "Alt-Memory $actionLabel failed: $msg"))
                return@withContext false
            }

            updateStatus(working = true, label = SandboxStatusLabel.Configuring)

            val verify = executor.execute("python3 -c 'import alt_memory; print(1)' 2>/dev/null", timeoutSeconds = 30)
            val ok = verify["success"] as? Boolean == true && (verify["stdout"] as? String)?.trim() == "1"
            if (!ok) {
                updateStatus(error = true, label = SandboxStatusLabel.Failure.Status(detail = "Alt-Memory $actionLabel: import check failed"))
                return@withContext false
            }

            appSettings.setAltMemoryInstalled(true)
            updateStatus()
            android.util.Log.i("SandboxController", "Alt-Memory ${actionLabel.lowercase()}d successfully")
            true
        }
    }

    override suspend fun stopAltMemory() {
        android.util.Log.i("SandboxController", "stopAltMemory called")
        altMemoryLifecycle.stop()
    }

    override suspend fun startWhatsApp() {
        android.util.Log.i("SandboxController", "startWhatsApp called, sandboxReady=${_status.value.ready}")
        whatsAppLifecycle.setupAndStart()
    }

    override suspend fun installWhatsAppBridge(): Boolean {
        android.util.Log.i("SandboxController", "installWhatsAppBridge called")
        return whatsAppPipeline(label = "Installing")
    }

    override suspend fun updateWhatsAppBridge(): Boolean {
        android.util.Log.i("SandboxController", "updateWhatsAppBridge called")
        return whatsAppPipeline(label = "Updating")
    }

    private var lastWhatsAppLog: String? = null

    private suspend fun whatsAppPipeline(label: String): Boolean {
        val current = sandboxManager.state.value
        if (current !is SandboxState.Ready) {
            android.util.Log.w("SandboxController", "whatsAppPipeline: sandbox not ready, state=$current")
            return false
        }

        fun updateStatus(
            working: Boolean = false,
            error: Boolean = false,
            label: SandboxStatusLabel? = null,
            lastWhatsAppError: String? = null,
        ) {
            _status.value = _status.value.copy(
                working = working,
                error = error,
                label = label ?: _status.value.label,
                lastWhatsAppError = lastWhatsAppError ?: _status.value.lastWhatsAppError,
            )
        }

        fun logStep(step: String, detail: String = "") {
            val msg = "[WHATSAPP] $step | $detail"
            android.util.Log.i("SandboxController", msg)
            lastWhatsAppLog = msg
        }

        fun logFail(step: String, result: Map<String, Any?>) {
            val stderr = (result["stderr"] as? String)?.take(1000) ?: ""
            val stdout = (result["stdout"] as? String)?.take(1000) ?: ""
            val error = (result["error"] as? String)?.take(1000) ?: ""
            val msg = "[WHATSAPP_FAIL] $step | stderr=$stderr | stdout=$stdout | error=$error"
            android.util.Log.e("SandboxController", msg)
            lastWhatsAppLog = msg
            updateStatus(lastWhatsAppError = msg)
        }

        return withContext(Dispatchers.IO) {
            val executor = sandboxManager.createProotExecutor()

            logStep("START", "$label WhatsApp bridge")

            updateStatus(working = true, label = SandboxStatusLabel.Installing)

            logStep("MKDIR", "creating /root/whatsapp-bridge")
            val mkdir = executor.execute("mkdir -p /root/whatsapp-bridge", timeoutSeconds = 10)
            if (mkdir["success"] as? Boolean != true) {
                logFail("mkdir", mkdir)
                updateStatus(error = true, label = SandboxStatusLabel.Failure.Status(detail = "WhatsApp $label: mkdir failed"))
                return@withContext false
            }

            logStep("DOWNLOAD", "downloading bridge.js from repo")
            val bridgeUrl = "https://raw.githubusercontent.com/kilvz/Kai-custom/main/sandbox/whatsapp-bridge/bridge.js"
            val bridgeDl = executor.execute(
                "curl -sL '$bridgeUrl' -o /root/whatsapp-bridge/bridge.js && echo DOWNLOAD_OK",
                timeoutSeconds = 30,
            )
            if (bridgeDl["success"] as? Boolean != true || (bridgeDl["stdout"] as? String)?.trim() != "DOWNLOAD_OK") {
                logFail("download", bridgeDl)
                updateStatus(error = true, label = SandboxStatusLabel.Failure.Status(detail = "WhatsApp $label: download bridge.js failed"))
                return@withContext false
            }

            logStep("NODE_CHECK", "checking node version")
            val nodeVer = executor.execute("node --version 2>&1", timeoutSeconds = 10)
            logStep("NODE_VERSION", (nodeVer["stdout"] as? String)?.trim() ?: (nodeVer["stderr"] as? String)?.trim() ?: "unknown")

            val nodeInstallCmd = buildString {
                append("NODE_VER=v22.14.0; ")
                append("ARCH=\$(uname -m); ")
                append("case \"\$ARCH\" in aarch64) NA=arm64 ;; x86_64) NA=x64 ;; armv7l) NA=armv7l ;; *) echo \"unsupported arch \$ARCH\"; exit 1 ;; esac; ")
                append("if ! command -v node >/dev/null 2>&1 || [ \"\$(node --version 2>/dev/null | cut -d. -f1 | tr -d v)\" -lt 20 ]; then ")
                append("  mkdir -p /usr/local/node22 && ")
                append("  curl -sL \"https://nodejs.org/dist/\${NODE_VER}/node-\${NODE_VER}-linux-\${NA}.tar.xz\" | tar -xJ -C /usr/local/node22 --strip-components=1 2>&1 && ")
                append("  ln -sf /usr/local/node22/bin/node /usr/local/bin/node && ")
                append("  ln -sf /usr/local/node22/bin/npm /usr/local/bin/npm && ")
                append("  ln -sf /usr/local/node22/bin/npx /usr/local/bin/npx; ")
                append("fi; ")
                append("echo NODE_OK")
            }
            val nodeDl = executor.execute(
                nodeInstallCmd,
                timeoutSeconds = 120,
            )
            if (nodeDl["success"] as? Boolean != true) {
                logFail("node_install", nodeDl)
                updateStatus(error = true, label = SandboxStatusLabel.Failure.Status(detail = "WhatsApp $label: node install failed"))
                return@withContext false
            }
            logStep("NODE_DONE", "node ready")

            updateStatus(working = true, label = SandboxStatusLabel.Installing)
            logStep("NPM_INSTALL", "starting npm install (timeout 300s)")

            val install = executor.execute(
                "cd /root/whatsapp-bridge && npm init -y 2>/dev/null && npm install --no-bin-links @whiskeysockets/baileys@^7.0.0-rc13 express qrcode pino 2>&1",
                timeoutSeconds = 300,
            )

            if (install["success"] as? Boolean != true) {
                logFail("npm_install", install)
                val stderr = install["stderr"] as? String ?: ""
                val stdout = install["stdout"] as? String ?: ""
                val error = install["error"] as? String ?: ""
                val msg = stderr.ifEmpty { error }.ifEmpty { stdout }.take(200)
                updateStatus(error = true, label = SandboxStatusLabel.Failure.Status(detail = "WhatsApp $label failed: $msg"))
                return@withContext false
            }
            logStep("NPM_DONE", "npm install succeeded")

            updateStatus(working = true, label = SandboxStatusLabel.Configuring)
            logStep("VERIFY", "checking require('@whiskeysockets/baileys')")

            val verify = executor.execute(
                "cd /root/whatsapp-bridge && node -e 'require(\"@whiskeysockets/baileys\"); console.log(1)' 2>&1",
                timeoutSeconds = 30,
            )
            val ok = verify["success"] as? Boolean == true && (verify["stdout"] as? String)?.trim() == "1"
            if (!ok) {
                logFail("verify", verify)
                updateStatus(error = true, label = SandboxStatusLabel.Failure.Status(detail = "WhatsApp $label: require check failed"))
                return@withContext false
            }
            logStep("VERIFY_OK", "require check passed")

            appSettings.setWhatsAppInstalled(true)
            updateStatus()
            logStep("DONE", "WhatsApp ${label.lowercase()}d successfully")
            lastWhatsAppLog = null
            true
        }
    }

    override suspend fun stopWhatsApp() {
        android.util.Log.i("SandboxController", "stopWhatsApp called")
        whatsAppLifecycle.stop()
    }

    override suspend fun backupSandbox(outputPath: String?): Result<SandboxController.BackupResult> = withContext(Dispatchers.IO) {
        val rootfs = File(sandboxManager.rootfsPath)
        if (!rootfs.isDirectory) {
            return@withContext Result.failure<SandboxController.BackupResult>(IllegalStateException("Sandbox rootfs not found at ${rootfs.path}"))
        }
        if (android.os.Build.VERSION.SDK_INT in 23..28) {
            val permissionGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!permissionGranted) {
                return@withContext Result.failure<SandboxController.BackupResult>(
                    SecurityException(
                        "Storage permission is required to save backups. Grant it in Settings → Apps → Kai → Permissions → Storage.",
                    ),
                )
            }
        }
        val timestamp = System.currentTimeMillis()
        val fileName = "sandbox-rootfs-$timestamp.tar.gz"
        val tmpDir = File(context.cacheDir, "sandbox-backup-tmp")
        tmpDir.mkdirs()
        val tmpFile = File(tmpDir, fileName)
        try {
            val rootfsParent = rootfs.parentFile?.absolutePath ?: rootfs.absolutePath
            val process = ProcessBuilder(
                "tar",
                "-czf",
                tmpFile.absolutePath,
                "-C",
                rootfsParent,
                rootfs.name,
            ).redirectErrorStream(true).start()
            val completed = process.waitFor(30, java.util.concurrent.TimeUnit.MINUTES)
            if (!completed) {
                process.destroyForcibly()
                return@withContext Result.failure<SandboxController.BackupResult>(IOException("tar timed out after 30 minutes"))
            }
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                val err = process.inputStream.bufferedReader().readText()
                return@withContext Result.failure<SandboxController.BackupResult>(IOException("tar failed (exit=$exitCode): $err"))
            }

            val destPath: String

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/gzip")
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/Kai")
                }
                val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri == null) {
                    val fallback = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Kai/$fileName")
                    fallback.parentFile?.mkdirs()
                    tmpFile.copyTo(fallback, overwrite = true)
                    destPath = fallback.absolutePath
                } else {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        tmpFile.inputStream().use { it.copyTo(out) }
                    }
                    destPath = "/storage/emulated/0/Download/Kai/$fileName"
                }
            } else {
                val dest = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Kai/$fileName")
                dest.parentFile?.mkdirs()
                tmpFile.copyTo(dest, overwrite = true)
                destPath = dest.absolutePath
            }

            tmpFile.delete()
            android.util.Log.i("SandboxController", "Sandbox backed up to $destPath")

            val toastText = "Sandbox backup saved to Downloads/Kai/"
            try {
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                handler.post { android.widget.Toast.makeText(context, toastText, android.widget.Toast.LENGTH_LONG).show() }
            } catch (_: Exception) {}

            val finalPath = if (outputPath != null) {
                java.io.File(destPath).copyTo(java.io.File(outputPath), overwrite = true)
                outputPath
            } else {
                destPath
            }
            Result.success(SandboxController.BackupResult(path = finalPath))
        } catch (e: Exception) {
            android.util.Log.e("SandboxController", "Backup failed", e)
            Result.failure<SandboxController.BackupResult>(e)
        }
    }

    override suspend fun importSandbox(data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        val tmpDir = File(sandboxManager.rootfsPath).parentFile ?: return@withContext Result.failure(IllegalStateException("Cannot resolve rootfs parent"))
        tmpDir.mkdirs()
        val tmpFile = File(tmpDir, "import_${System.currentTimeMillis()}.tar.gz")
        try {
            tmpFile.writeBytes(data)
            val rootfs = File(sandboxManager.rootfsPath)
            val rootfsParent = rootfs.parentFile ?: return@withContext Result.failure(IllegalStateException("Cannot resolve rootfs parent"))
            if (rootfs.exists()) {
                android.util.Log.i("SandboxController", "Removing existing rootfs at ${rootfs.path}")
                rootfs.deleteRecursively()
            }
            rootfsParent.mkdirs()
            val process = ProcessBuilder(
                "tar",
                "-xzf",
                tmpFile.absolutePath,
                "-C",
                rootfsParent.absolutePath,
            ).redirectErrorStream(true).start()
            val completed = process.waitFor(30, java.util.concurrent.TimeUnit.MINUTES)
            if (!completed) {
                process.destroyForcibly()
                return@withContext Result.failure(IOException("tar extract timed out after 30 minutes"))
            }
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                val err = process.inputStream.bufferedReader().readText()
                return@withContext Result.failure(IOException("tar extract failed (exit=$exitCode): $err"))
            }
            if (!rootfs.isDirectory) {
                return@withContext Result.failure(IOException("Extracted rootfs not found at ${rootfs.path}"))
            }
            android.util.Log.i("SandboxController", "Sandbox restored from ${tmpFile.name}")
            sandboxManager.onRootfsRestored()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("SandboxController", "Import failed", e)
            Result.failure(e)
        } finally {
            tmpFile.delete()
        }
    }
}

private fun File.toEntry(parent: String): SandboxFileEntry = SandboxFileEntry(
    name = name,
    path = if (parent.isEmpty()) "/$name" else "$parent/$name",
    isDirectory = isDirectory,
    sizeBytes = if (isFile) length() else 0,
    lastModifiedMs = lastModified(),
)

/** Returned to the agent as a tool result, so it stays English like the rest of the tool surface. */
private const val SANDBOX_NOT_READY = "Sandbox is not ready"

private class PersistentCommandHandle(
    private val shell: SessionShell,
    private val result: CompletableDeferred<Map<String, Any>>,
    private val cancelled: AtomicBoolean,
) : CommandHandle {
    override fun cancel() {
        cancelled.set(true)
        shell.cancelForeground()
    }
    override fun isCancelled(): Boolean = cancelled.get()
    override suspend fun writeInput(line: String) {
        withContext(Dispatchers.IO) { shell.writeInput(line) }
    }
    override suspend fun awaitExit(): Int = (result.await()["exit_code"] as? Int) ?: -1
}
