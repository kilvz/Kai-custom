package com.kai.custom

import com.kai.custom.sandbox.DockerManager
import com.kai.custom.sandbox.DockerSandboxController

actual fun createSandboxController(): SandboxController {
    val dockerManager = DockerManager()
    val info = kotlinx.coroutines.runBlocking { dockerManager.getInfo() }
    return if (info.available) {
        DockerSandboxController(dockerManager)
    } else {
        NoOpSandboxController()
    }
}

class NoOpSandboxController : SandboxController {
    override val status: kotlinx.coroutines.flow.StateFlow<SandboxStatus> =
        kotlinx.coroutines.flow.MutableStateFlow(SandboxStatus())
    override val sessions: kotlinx.coroutines.flow.StateFlow<List<String>> =
        kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    override fun setup() {}
    override fun cancel() {}
    override fun reset() {}
    override fun installPackages() {}
    override suspend fun executeCommand(
        command: String,
        sessionId: String,
        useRoot: Boolean,
        timeoutSeconds: Long,
    ): String = ""
    override suspend fun executeCommandStreaming(
        command: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        sessionId: String,
    ): CommandHandle = NoOpCommandHandle

    override suspend fun listDirectory(path: String): List<SandboxFileEntry> = emptyList()
    override suspend fun readTextFile(path: String, maxBytes: Int): String? = null
    override suspend fun writeTextFile(path: String, content: String): Boolean = false
    override suspend fun writeBinaryFile(path: String, data: ByteArray): Boolean = false
    override suspend fun openFile(path: String): Result<Unit> = Result.failure(UnsupportedOperationException("No Docker available"))
    override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = false
    override suspend fun renameEntry(path: String, newName: String): Result<String> =
        Result.failure(UnsupportedOperationException("No Docker available"))
}
