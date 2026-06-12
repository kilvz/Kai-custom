package com.kai.custom.mcp

import com.kai.custom.SandboxController
import com.kai.custom.SandboxSessions
import com.kai.custom.data.AppSettings
import com.kai.custom.data.DataRepository
import com.kai.custom.data.MemoryCategory
import com.kai.custom.data.MemoryStoreProvider
import com.kai.custom.data.PersonaManager
import com.kai.custom.data.dimension.DimensionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class AltMemoryLifecycleManager(
    private val sandboxController: SandboxController,
    private val mcpServerManager: McpServerManager,
    private val appSettings: AppSettings,
    private val memoryStore: MemoryStoreProvider,
    private val dataRepository: DataRepository,
) {
    companion object {
        private const val ALT_MEMORY_URL = "http://127.0.0.1:8316/mcp"
        private const val SERVER_ID = "alt_memory"
        private const val MCP_SESSION_ID = "__alt_memory__"

        /** How often to re-check whether the MCP server has come up. */
        private const val RETRY_INTERVAL_MS = 10_000L
    }

    private var started = false
    private var connected = false
    private var retryJob: Job? = null

    /**
     * Check if the alt-memory package is actually importable in the current
     * rootfs. If the app setting says installed but the package isn't there
     * (e.g. after switching distros), reset the flag so the install button
     * becomes available again.
     */
    suspend fun verifyInstalled() {
        if (!appSettings.isAltMemoryInstalled()) return
        val check = sandboxController.executeCommand(
            command = "python3 -c 'import alt_memory; print(1)' 2>/dev/null",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
        )
        if (check.trim() != "1") {
            appSettings.setAltMemoryInstalled(false)
        }
    }

    /**
     * Ensure alt-memory Python package is installed. Returns true when
     * installation succeeded (or was already installed). Idempotent.
     */
    suspend fun ensureInstalled(): Boolean {
        if (appSettings.isAltMemoryInstalled()) return true
        val ok = installIfNeeded()
        if (ok) appSettings.setAltMemoryInstalled(true)
        return ok
    }

    suspend fun setupAndStart() {
        if (started) return
        if (appSettings.isAltMemoryInstalled()) {
            val ok = sandboxController.executeCommand(
                command = "python3 -c 'import alt_memory; print(1)' 2>/dev/null",
                sessionId = SandboxSessions.SYSTEM,
                useRoot = false,
            )
            if (ok.trim() != "1") {
                appSettings.setAltMemoryInstalled(false)
                return
            }
        } else {
            return
        }
        started = true

        // Register the built-in server config once (idempotent).
        // connectAndDiscoverTools needs the config to be registered first.
        mcpServerManager.registerBuiltInServer(
            id = SERVER_ID,
            name = "Alt Memory",
            url = ALT_MEMORY_URL,
        )

        // Fire-and-forget: launch the MCP server process so the retry loop
        // below starts immediately. Proot blocks even with & — the short
        // timeout kills proot while the orphaned alt-memory continues.
        CoroutineScope(Dispatchers.Default).launch {
            startMcpServer()
        }

        // Try to connect immediately (server may already be running).
        if (tryConnect()) return

        // Background retry: every 10s, try to connect until success.
        retryJob = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                delay(RETRY_INTERVAL_MS)
                if (connected || !started) break
                if (tryConnect()) break
            }
        }
    }

    /** One-shot connection attempt via the standard MCP flow. */
    private suspend fun tryConnect(): Boolean {
        if (connected) return true
        // Ensure the server process is running before trying to connect
        startMcpServer()
        val result = mcpServerManager.connectAndDiscoverTools(SERVER_ID)
        if (result.isFailure) return false
        runMigration()
        connected = true
        return true
    }

    /** Whether the MCP server is fully connected and migrated. */
    fun isConnected(): Boolean = connected

    suspend fun stop() {
        started = false
        connected = false
        retryJob?.cancel()
        retryJob = null
        try {
            sandboxController.executeCommand(
                command = "pkill -f 'alt-memory.*mcp' 2>/dev/null || true",
                sessionId = SandboxSessions.SYSTEM,
                useRoot = false,
            )
            mcpServerManager.removeBuiltInServer(SERVER_ID)
        } catch (_: Exception) {
        }
    }

    private suspend fun runMigration() {
        val client = mcpServerManager.getClient(SERVER_ID) ?: return
        if (!appSettings.isAltMemoryMigrationComplete()) {
            val memories = memoryStore.getAllMemories()
            var migrated = 0
            var failed = 0
            for (entry in memories) {
                try {
                    val realm = when (entry.category) {
                        MemoryCategory.GENERAL,
                        MemoryCategory.LEARNING,
                        MemoryCategory.ERROR,
                        -> DimensionConfig.REALM_AGENT

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
                    migrated++
                } catch (_: Exception) {
                    failed++
                }
            }
            appSettings.setAltMemoryMigrationComplete(true)
        }
        // After migration, switch to alt-memory as the active backend
        memoryStore.useAltMemory(client, appSettings)

        // Sync built-in personas (kai, alt) to alt-memory
        for (builtIn in PersonaManager.builtIns) {
            memoryStore.syncPersonaToRemote(builtIn)
        }

        // Set current active persona on alt-memory
        val personaId = appSettings.getActivePersonaId()
        memoryStore.setPersona(personaId)

        // Fetch remote personas and merge with local list
        val remotePersonas = memoryStore.fetchRemotePersonas()
        val localPersonas = dataRepository.getAllPersonas()
        val localIds = localPersonas.map { it.id }.toSet()
        for (rp in remotePersonas) {
            if (rp.id !in localIds) {
                dataRepository.savePersona(rp)
            }
        }
    }

    private suspend fun installIfNeeded(): Boolean {
        val check = sandboxController.executeCommand(
            command = "python3 -c 'import alt_memory; print(1)' 2>/dev/null",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
        )
        if (check.trim() == "1") return true

        // Run pip install synchronously (no &) with a generous timeout.
        // Background processes don't survive a one-shot proot shell, so we
        // avoid the old pattern of "pip … &" + polling a log file.
        val install = sandboxController.executeCommand(
            command = "pip install alt-memory --break-system-packages 2>&1",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
            timeoutSeconds = 180,
        )
        // Verify
        val verify = sandboxController.executeCommand(
            command = "python3 -c 'import alt_memory; print(1)' 2>/dev/null",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
        )
        return verify.trim() == "1"
    }

    private suspend fun startMcpServer() {
        // Short timeout: proot blocks even with &, so killing proot leaves the
        // orphaned alt-memory process running (reparented to init).
        sandboxController.executeCommand(
            command = "setsid nohup alt-memory mcp --transport sse --port 8316 > /tmp/alt-memory.log 2>&1 &",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
            timeoutSeconds = 5,
        )
    }
}
