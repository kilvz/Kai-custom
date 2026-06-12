package com.kai.custom.sandbox

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.kai.custom.CommandHandle
import com.kai.custom.NoOpCommandHandle
import com.kai.custom.SandboxController
import com.kai.custom.SandboxFileEntry
import com.kai.custom.SandboxStatus
import com.kai.custom.TerminalLine
import com.kai.custom.data.AppSettings
import com.kai.custom.data.DataRepository
import com.kai.custom.data.MemoryCategory
import com.kai.custom.data.MemoryStoreProvider
import com.kai.custom.data.PersonaManager
import com.kai.custom.data.dimension.DimensionConfig
import com.kai.custom.mcp.McpServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DockerSandboxController(
    private val dockerManager: DockerManager = DockerManager(),
    private val altMemoryDockerManager: AltMemoryDockerManager = AltMemoryDockerManager(dockerManager),
) : SandboxController {
    private val mcpServerManager: McpServerManager by inject(McpServerManager::class.java)
    private val appSettings: AppSettings by inject(AppSettings::class.java)
    private val memoryStore: MemoryStoreProvider by inject(MemoryStoreProvider::class.java)
    private val dataRepository: DataRepository by inject(DataRepository::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _status = MutableStateFlow(SandboxStatus())
    override val status: StateFlow<SandboxStatus> = _status

    private val _sessions = MutableStateFlow<List<String>>(emptyList())
    override val sessions: StateFlow<List<String>> = _sessions

    private val sessionProcesses = ConcurrentHashMap<String, Process>()
    private val sessionTranscripts = ConcurrentHashMap<String, SnapshotStateList<TerminalLine>>()
    private val containerName = "kai-sandbox"
    private val imageName = "alpine:latest"

    override fun setup() {
        _status.value = _status.value.copy(working = true, statusText = "Setting up Docker sandbox...")
        scope.launch {
            try {
                val info = dockerManager.getInfo()
                if (!info.available) {
                    _status.value = _status.value.copy(
                        working = false,
                        error = true,
                        statusText = "Docker is not available. Please install Docker Desktop.",
                    )
                    return@launch
                }
                _status.value = _status.value.copy(statusText = "Pulling Docker image ($imageName)...")
                dockerManager.pullImage(imageName)
                _status.value = _status.value.copy(statusText = "Creating sandbox container...")
                val cid = dockerManager.createContainer(
                    imageName,
                    containerName,
                    portMappings = mapOf(8316 to 8316, 8317 to 8317),
                )
                if (cid != null) {
                    _status.value = _status.value.copy(
                        installed = true,
                        ready = true,
                        working = false,
                        statusText = "Sandbox ready ($imageName)",
                    )
                    refreshStatus()
                } else {
                    _status.value = _status.value.copy(
                        working = false,
                        error = true,
                        statusText = "Failed to create Docker container",
                    )
                }
            } catch (e: Exception) {
                _status.value = _status.value.copy(
                    working = false,
                    error = true,
                    statusText = "Setup failed: ${e.message}",
                )
            }
        }
    }

    override fun cancel() {
        _status.value = _status.value.copy(working = false)
    }

    override fun reset() {
        _status.value = _status.value.copy(working = true, statusText = "Removing sandbox container...")
        scope.launch {
            dockerManager.removeContainer(containerName, force = true)
            _status.value = SandboxStatus()
            sessionProcesses.clear()
            sessionTranscripts.clear()
        }
    }

    override fun installPackages() {
        _status.value = _status.value.copy(working = true, statusText = "Installing packages...")
        scope.launch {
            try {
                dockerManager.execCommand(
                    containerName,
                    "apk add --no-cache python3 nodejs npm curl git bash sudo",
                )
                _status.value = _status.value.copy(statusText = "Installing edge-tts...")
                runCatching {
                    dockerManager.execCommand(containerName, "pip install --no-cache-dir edge-tts 2>&1")
                }
                _status.value = _status.value.copy(
                    working = false,
                    packagesInstalled = true,
                    statusText = "Packages installed",
                )
            } catch (e: Exception) {
                _status.value = _status.value.copy(
                    working = false,
                    error = true,
                    statusText = "Install failed: ${e.message}",
                )
            }
        }
    }

    override suspend fun executeCommand(
        command: String,
        sessionId: String,
        useRoot: Boolean,
        timeoutSeconds: Long,
    ): String = withContext(Dispatchers.IO) {
        try {
            val cmd = if (useRoot) command else "sh -c '$command'"
            val output = dockerManager.execCommand(containerName, cmd)
            addTranscript(sessionId, TerminalLine.Command(command))
            addTranscript(sessionId, TerminalLine.Output(output))
            output
        } catch (e: Exception) {
            addTranscript(sessionId, TerminalLine.Error(e.message ?: "Unknown error"))
            "Error: ${e.message}"
        }
    }

    override suspend fun executeCommandStreaming(
        command: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        sessionId: String,
    ): CommandHandle = withContext(Dispatchers.IO) {
        try {
            val proc = Runtime.getRuntime().exec(
                arrayOf("docker", "exec", "-i", containerName, "sh", "-c", command),
            )
            sessionProcesses[sessionId] = proc
            addTranscript(sessionId, TerminalLine.Command(command))

            Thread {
                try {
                    proc.inputStream.bufferedReader().use { reader ->
                        reader.lines().forEach { line ->
                            onStdout(line)
                            kotlinx.coroutines.runBlocking {
                                addTranscript(sessionId, TerminalLine.Output(line))
                            }
                        }
                    }
                } catch (_: Exception) {}
            }.apply {
                isDaemon = true
                start()
            }

            Thread {
                try {
                    proc.errorStream.bufferedReader().use { reader ->
                        reader.lines().forEach { line ->
                            onStderr(line)
                            kotlinx.coroutines.runBlocking {
                                addTranscript(sessionId, TerminalLine.Error(line))
                            }
                        }
                    }
                } catch (_: Exception) {}
            }.apply {
                isDaemon = true
                start()
            }

            object : CommandHandle {
                override fun cancel() {
                    proc.destroyForcibly()
                    sessionProcesses.remove(sessionId)
                }
                override fun isCancelled(): Boolean = !proc.isAlive
                override suspend fun writeInput(line: String) {
                    proc.outputStream.write((line + "\n").toByteArray())
                    proc.outputStream.flush()
                }
                override suspend fun awaitExit(): Int = proc.waitFor().also { sessionProcesses.remove(sessionId) }
            }
        } catch (e: Exception) {
            addTranscript(sessionId, TerminalLine.Error(e.message ?: "Failed to execute"))
            NoOpCommandHandle
        }
    }

    override fun closeSession(sessionId: String) {
        sessionProcesses[sessionId]?.destroyForcibly()
        sessionProcesses.remove(sessionId)
        _sessions.value = _sessions.value - sessionId
    }

    override fun transcriptFor(sessionId: String): SnapshotStateList<TerminalLine> = sessionTranscripts.getOrPut(sessionId, ::mutableStateListOf)

    override fun clearTranscript(sessionId: String) {
        sessionTranscripts.remove(sessionId)
    }

    override suspend fun listDirectory(path: String): List<SandboxFileEntry> = withContext(Dispatchers.IO) {
        try {
            val output = dockerManager.execCommand(
                containerName,
                "ls -la '$path' 2>/dev/null | tail -n +2",
            )
            if (output.isBlank()) return@withContext emptyList()
            output.lines().mapNotNull { line ->
                try {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size < 9) return@mapNotNull null
                    val name = parts.drop(8).joinToString(" ")
                    if (name == "." || name == "..") return@mapNotNull null
                    SandboxFileEntry(
                        name = name,
                        path = "$path/$name",
                        isDirectory = parts[0].startsWith("d"),
                        sizeBytes = parts[4].toLongOrNull() ?: 0L,
                        lastModifiedMs = 0L,
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun readTextFile(path: String, maxBytes: Int): String? = withContext(Dispatchers.IO) {
        try {
            val output = dockerManager.execCommand(containerName, "head -c $maxBytes '$path' 2>/dev/null")
            output.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun writeTextFile(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val escaped = content
                .replace("\\", "\\\\")
                .replace("'", "'\\''")
            dockerManager.execCommand(containerName, "mkdir -p '${path.substringBeforeLast("/")}' && echo '$escaped' > '$path'")
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun writeBinaryFile(path: String, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val tempFile = File.createTempFile("kai_docker_upload", ".bin")
            tempFile.writeBytes(data)
            val result = dockerManager.copyIn(containerName, tempFile.absolutePath, path)
            tempFile.delete()
            result
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun openFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val tempFile = File.createTempFile("kai_sandbox_", "_${path.substringAfterLast("/")}")
            dockerManager.copyOut(containerName, path, tempFile.absolutePath)
            java.awt.Desktop.getDesktop().open(tempFile)
            tempFile.deleteOnExit()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val flag = if (recursive) "-rf" else "-f"
            val output = dockerManager.execCommand(containerName, "rm $flag '$path' 2>/dev/null; echo \$?")
            output.trim() == "0"
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun renameEntry(path: String, newName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val parent = path.substringBeforeLast("/")
            val dest = "$parent/$newName"
            val output = dockerManager.execCommand(containerName, "mv '$path' '$dest' 2>/dev/null; echo \$?")
            if (output.trim() == "0") {
                Result.success(dest)
            } else {
                Result.failure(Exception("Rename failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val ALT_MEMORY_URL = "http://127.0.0.1:8316/mcp"
        private const val ALT_MEMORY_ID = "alt_memory"
        private const val RETRY_INTERVAL_MS = 3_000L
        private const val MAX_RETRIES = 10
    }

    private val nativeAltMemory by lazy { NativeAltMemoryManager() }

    private val isNativeMode: Boolean get() = appSettings.getAltMemoryMode() == "native"

    override suspend fun startAltMemory() {
        if (isNativeMode) {
            if (nativeAltMemory.start()) initAltMemoryMcp()
        } else {
            if (altMemoryDockerManager.startContainer()) initAltMemoryMcp()
        }
    }

    override suspend fun stopAltMemory() {
        if (isNativeMode) {
            nativeAltMemory.stop()
        } else {
            altMemoryDockerManager.stopContainer()
        }
        mcpServerManager.removeBuiltInServer(ALT_MEMORY_ID)
    }

    override suspend fun installAltMemoryPackage(): Boolean {
        val ok = if (isNativeMode) {
            if (!nativeAltMemory.isAvailable() && !nativeAltMemory.install()) return false
            nativeAltMemory.start()
        } else {
            if (!altMemoryDockerManager.pullImage()) return false
            altMemoryDockerManager.startContainer()
        }
        if (!ok) return false
        return initAltMemoryMcp()
    }

    override suspend fun updateAltMemoryPackage(): Boolean {
        stopAltMemory()
        if (isNativeMode) {
            if (!nativeAltMemory.install()) return false
            return installAltMemoryPackage()
        }
        return altMemoryDockerManager.pullAndRestart() && initAltMemoryMcp()
    }

    private suspend fun initAltMemoryMcp(): Boolean {
        return withContext(Dispatchers.Default) {
            mcpServerManager.registerBuiltInServer(
                id = ALT_MEMORY_ID,
                name = "Alt Memory",
                url = ALT_MEMORY_URL,
            )
            for (attempt in 1..MAX_RETRIES) {
                delay(RETRY_INTERVAL_MS)
                val result = mcpServerManager.connectAndDiscoverTools(ALT_MEMORY_ID)
                if (result.isSuccess) {
                    appSettings.setAltMemoryInstalled(true)
                    runAltMemoryMigration()
                    return@withContext true
                }
            }
            mcpServerManager.removeBuiltInServer(ALT_MEMORY_ID)
            false
        }
    }

    private suspend fun runAltMemoryMigration() {
        if (appSettings.isAltMemoryMigrationComplete()) {
            memoryStore.useAltMemory(
                mcpServerManager.getClient(ALT_MEMORY_ID)!!,
                appSettings,
            )
            return
        }
        val client = mcpServerManager.getClient(ALT_MEMORY_ID) ?: return
        val memories = memoryStore.getAllMemories()
        for (entry in memories) {
            try {
                val realm = when (entry.category) {
                    MemoryCategory.GENERAL, MemoryCategory.LEARNING, MemoryCategory.ERROR -> DimensionConfig.REALM_AGENT
                    MemoryCategory.PREFERENCE -> DimensionConfig.REALM_USER
                }
                val domain = when (entry.category) {
                    MemoryCategory.GENERAL -> DimensionConfig.DOMAIN_MEMORIES
                    MemoryCategory.PREFERENCE -> DimensionConfig.DOMAIN_PREFERENCES
                    MemoryCategory.LEARNING -> DimensionConfig.DOMAIN_LEARNINGS
                    MemoryCategory.ERROR -> DimensionConfig.DOMAIN_ERRORS
                }
                client.callTool(
                    "add_entity",
                    buildJsonObject {
                        put("entity_id", JsonPrimitive(entry.key))
                        put("realm", JsonPrimitive(realm))
                        put("domain", JsonPrimitive(domain))
                        put("content", JsonPrimitive(entry.content))
                        put(
                            "metadata",
                            buildJsonObject {
                                put("memory_key", JsonPrimitive(entry.key))
                                put("category", JsonPrimitive(entry.category.name))
                                put("hit_count", JsonPrimitive(entry.hitCount.toString()))
                                put("type", JsonPrimitive("memory_entry"))
                                entry.source?.let { put("source", JsonPrimitive(it)) }
                                if (entry.protected) put("protected", JsonPrimitive("true"))
                            },
                        )
                    },
                )
            } catch (_: Exception) {}
        }
        appSettings.setAltMemoryMigrationComplete(true)
        memoryStore.useAltMemory(client, appSettings)
        for (builtIn in PersonaManager.builtIns) {
            memoryStore.syncPersonaToRemote(builtIn)
        }
        val personaId = appSettings.getActivePersonaId()
        memoryStore.setPersona(personaId)
        val remotePersonas = memoryStore.fetchRemotePersonas()
        val localPersonas = dataRepository.getAllPersonas()
        val localIds = localPersonas.map { it.id }.toSet()
        for (rp in remotePersonas) {
            if (rp.id !in localIds) {
                dataRepository.savePersona(rp)
            }
        }
    }

    override suspend fun startWhatsApp() {
        dockerManager.execCommand(
            containerName,
            "cd /root/whatsapp-bridge && node bridge.js &",
        )
    }

    override suspend fun stopWhatsApp() {
        dockerManager.execCommand(containerName, "pkill -f 'node bridge.js' 2>/dev/null || true")
    }

    override suspend fun installWhatsAppBridge(): Boolean = try {
        dockerManager.execCommand(
            containerName,
            "mkdir -p /root/whatsapp-bridge && which node || apk add --no-cache nodejs npm",
        )
        val bridgeUrl = "https://raw.githubusercontent.com/kilvz/Kai-custom/main/sandbox/whatsapp-bridge/bridge.js"
        dockerManager.execCommand(
            containerName,
            "curl -sL '$bridgeUrl' -o /root/whatsapp-bridge/bridge.js",
        )
        dockerManager.execCommand(
            containerName,
            "cd /root/whatsapp-bridge && npm init -y 2>/dev/null && npm install --no-bin-links @whiskeysockets/baileys @modelcontextprotocol/sdk qrcode pino 2>&1",
        )
        true
    } catch (_: Exception) {
        false
    }

    override suspend fun updateWhatsAppBridge(): Boolean = installWhatsAppBridge()

    override suspend fun backupSandbox(outputPath: String?): Result<SandboxController.BackupResult> = withContext(Dispatchers.IO) {
        try {
            val path = outputPath ?: "${System.getProperty("java.io.tmpdir")}/sandbox-rootfs-${System.currentTimeMillis()}.tar.gz"
            val ok = dockerManager.exportContainer(containerName, path)
            if (ok) {
                Result.success(SandboxController.BackupResult(path))
            } else {
                Result.failure(Exception("Docker export failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importSandbox(data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dockerManager.removeContainer(containerName, force = true)
            val tempFile = File.createTempFile("sandbox_import", ".tar.gz")
            tempFile.writeBytes(data)
            dockerManager.importContainer(tempFile.absolutePath, "kai-sandbox-import")
            val cid = dockerManager.createContainer("kai-sandbox-import", containerName)
            tempFile.delete()
            if (cid != null) {
                refreshStatus()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to create container from import"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun refreshStatus() {
        try {
            val running = dockerManager.isContainerRunning(containerName)
            val exists = dockerManager.containerExists(containerName)
            val diskUsage = if (exists) dockerManager.getContainerDiskUsage(containerName) else 0L
            val pkgsInstalled = if (exists) dockerManager.checkPackagesInstalled(containerName) else false
            _status.value = _status.value.copy(
                installed = exists,
                ready = running,
                diskUsageMB = diskUsage / (1024 * 1024),
                packagesInstalled = pkgsInstalled,
            )
        } catch (_: Exception) {}
    }

    private suspend fun addTranscript(sessionId: String, line: TerminalLine) {
        transcriptFor(sessionId).add(line)
        if (!_sessions.value.contains(sessionId)) {
            _sessions.value = _sessions.value + sessionId
        }
    }

    override suspend fun installDocker(): Boolean = true
}
