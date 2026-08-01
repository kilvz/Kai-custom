package com.kai.custom

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.flow.StateFlow

data class SandboxStatus(
    val installed: Boolean = false,
    val ready: Boolean = false,
    val working: Boolean = false,
    val progress: Float? = null,
    val statusText: String = "",
    val diskUsageMB: Long = 0,
    val packagesInstalled: Boolean = false,
    val error: Boolean = false,
    val needsReset: Boolean = false,
    val lastWhatsAppError: String? = null,
)

interface CommandHandle {
    fun cancel()
    fun isCancelled(): Boolean
    suspend fun writeInput(line: String)
    suspend fun awaitExit(): Int
}

internal object NoOpCommandHandle : CommandHandle {
    override fun cancel() {}
    override fun isCancelled(): Boolean = false
    override suspend fun writeInput(line: String) {}
    override suspend fun awaitExit(): Int = -1
}

data class SandboxFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
)

/**
 * Packages the sandbox's own infrastructure depends on. `bash` backs every
 * persistent shell session (`PersistentSandboxShell` execs it directly), so
 * removing it would break the sandbox for every chat and the Terminal tab.
 * These install first and the Packages UI must never offer to uninstall them.
 */
object SandboxRequiredPackages {
    val NAMES: Set<String> = setOf("bash")
}

/** Sentinel ids for shell sessions that aren't tied to a specific chat. */
object SandboxSessions {
    /** Default scratch session — used when no caller-specific id is available. */
    const val DEFAULT = "__default__"

    /** Background system maintenance (package manager UI, settings refreshes). */
    const val SYSTEM = "__system__"

    /** User-facing Terminal tab in Settings. */
    const val TERMINAL = "__terminal__"

    /** True for chat-bound session ids (anything that isn't a sentinel). Such sessions get their transcript persisted. */
    fun isPersistable(sessionId: String): Boolean = sessionId != TERMINAL && sessionId != SYSTEM && sessionId != DEFAULT
}

data class ExecResult(
    val success: Boolean = false,
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int? = null,
    val error: String? = null,
)

interface SandboxController : FileBrowserSource {
    val status: StateFlow<SandboxStatus>

    /** Active shell-session ids (in-memory only, not persisted). */
    val sessions: StateFlow<List<String>>
    fun setup()
    fun cancel()
    fun reset()
    /** Restart the sandbox — close shells and kill proot processes but keep rootfs intact. */
    fun restart()
    fun installPackages()
    suspend fun executeCommand(
        command: String,
        sessionId: String = SandboxSessions.DEFAULT,
        useRoot: Boolean = true,
        timeoutSeconds: Long = 30,
    ): String

    suspend fun executeCommandStructured(
        command: String,
        sessionId: String = SandboxSessions.DEFAULT,
        useRoot: Boolean = true,
        timeoutSeconds: Long = 30,
    ): ExecResult = ExecResult(error = "Not implemented")
    suspend fun executeCommandStreaming(
        command: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        sessionId: String = SandboxSessions.DEFAULT,
    ): CommandHandle

    /** Drop the shell for [sessionId] if any. Idempotent. */
    fun closeSession(sessionId: String) {}

    /**
     * Live transcript of commands and output for [sessionId]. The list is
     * populated regardless of which path drove the command (chat tool or
     * Terminal UI), so the user can see the agent's activity. Returns an
     * empty, non-mutated list on platforms without a sandbox.
     */
    fun transcriptFor(sessionId: String): SnapshotStateList<TerminalLine> = mutableStateListOf()

    /** Wipe the transcript for [sessionId]. Idempotent. */
    fun clearTranscript(sessionId: String) {}

    suspend fun writeBinaryFile(path: String, data: ByteArray): Boolean


    suspend fun startAltMemory() {}
    suspend fun stopAltMemory() {}
    suspend fun installAltMemoryPackage(): Boolean = false
    suspend fun updateAltMemoryPackage(): Boolean = false

    /** Returns (currentVersion, latestVersion) from pip/PyPI. Both null on failure. */
    suspend fun getAltMemoryVersions(): Pair<String?, String?> = Pair(null, null)

    suspend fun startWhatsApp() {}
    suspend fun stopWhatsApp() {}
    suspend fun installWhatsAppBridge(): Boolean = false
    suspend fun updateWhatsAppBridge(): Boolean = false

    data class BackupResult(val path: String)

    /** Create a gzipped tarball of the rootfs at [outputPath], returns the path on success. */
    suspend fun backupSandbox(outputPath: String? = null): Result<BackupResult> = Result.failure(NotImplementedError())

    /** Restore rootfs from a gzipped tarball [data]. Deletes current rootfs first. */
    suspend fun importSandbox(data: ByteArray): Result<Unit> = Result.failure(NotImplementedError())

    /** Install Docker Desktop on the host (desktop only). No-op on Android. */
    suspend fun installDocker(): Boolean = false
}

expect fun createSandboxController(): SandboxController
