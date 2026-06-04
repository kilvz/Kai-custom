package com.kai.custom

import com.kai.custom.sandbox.DockerManager
import com.kai.custom.sandbox.DockerSandboxController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

actual fun createSandboxController(): SandboxController {
    val dockerManager = DockerManager()
    val info = kotlinx.coroutines.runBlocking { dockerManager.getInfo() }
    return if (info.available) {
        DockerSandboxController(dockerManager)
    } else {
        NoOpSandboxController(dockerManager)
    }
}

class NoOpSandboxController(
    private val dockerManager: DockerManager,
) : SandboxController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _status = MutableStateFlow(SandboxStatus(error = true, statusText = "Docker Desktop not found — click Install to set it up"))
    override val status: StateFlow<SandboxStatus> = _status
    override val sessions: StateFlow<List<String>> =
        MutableStateFlow(emptyList())
    override fun setup() {
        _status.value = _status.value.copy(working = true, statusText = "Installing Docker Desktop...")
        scope.launch {
            val installed = dockerManager.installDockerDesktop()
            if (installed) {
                val info = dockerManager.getInfo()
                _status.value = _status.value.copy(working = false, error = false, statusText = "Docker installed. Restart the app to use the sandbox.")
            } else {
                _status.value = _status.value.copy(working = false, error = true, statusText = "Docker installation failed. Install Docker Desktop manually from https://docker.com")
            }
        }
    }
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

    override suspend fun installDocker(): Boolean = dockerManager.installDockerDesktop()
}
