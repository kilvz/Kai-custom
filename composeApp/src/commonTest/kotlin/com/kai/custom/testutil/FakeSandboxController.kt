package com.kai.custom.testutil

import com.kai.custom.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSandboxController(installed: Boolean = true) : SandboxController {
    override val status: StateFlow<SandboxStatus> = MutableStateFlow(
        SandboxStatus(
            ready = installed,
            sandboxInstalled = installed,
            working = false,
        ),
    )
    override val sessions: StateFlow<List<String>> = MutableStateFlow(emptyList())

    override fun setup() {}
    override fun cancel() {}
    override fun reset() {}
    override fun restart() {}
    override fun installPackages() {}

    override suspend fun executeCommand(command: String, sessionId: String): String = ""

    override suspend fun executeCommandStructured(
        command: String,
        sessionId: String,
        useRoot: Boolean,
        timeoutSeconds: Long,
    ): ExecResult = ExecResult(stdout = "")

    override suspend fun executeCommandStreaming(
        command: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        sessionId: String,
    ): CommandHandle = NoOpCommandHandle

    override suspend fun listDirectory(path: String): List<SandboxFileEntry> {
        if (path == "/") return listOf(SandboxFileEntry("test.txt", isDirectory = false, size = 10))
        return emptyList()
    }

    override suspend fun readTextFile(path: String, maxBytes: Int): String? = "file content"
    override suspend fun writeTextFile(path: String, content: String): Boolean = true
    override suspend fun writeBinaryFile(path: String, data: ByteArray): Boolean = true
    override suspend fun openFile(path: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = true
    override suspend fun renameEntry(path: String, newName: String): Result<String> = Result.success(newName)

    override fun closeSession(sessionId: String) {}
    override fun transcriptFor(sessionId: String): SnapshotStateList<TerminalLine> = SnapshotStateList()
    override fun clearTranscript(sessionId: String) {}
}
