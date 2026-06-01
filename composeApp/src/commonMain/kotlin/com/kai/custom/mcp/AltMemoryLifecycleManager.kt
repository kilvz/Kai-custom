package com.kai.custom.mcp

import com.kai.custom.SandboxController
import com.kai.custom.SandboxSessions
import com.kai.custom.data.AppSettings
import com.kai.custom.data.DataRepository
import com.kai.custom.data.MemoryCategory
import com.kai.custom.data.MemoryStoreProvider
import com.kai.custom.data.PersonaManager
import com.kai.custom.data.dimension.DimensionConfig
import kotlinx.coroutines.delay
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
        private const val ALT_MEMORY_URL = "http://127.0.0.1:8316"
        private const val SERVER_ID = "alt_memory"
        private const val HEALTH_CHECK_RETRIES = 12
        private const val HEALTH_CHECK_DELAY_MS = 5_000L
        private const val MCP_SESSION_ID = "__alt_memory__"
    }

    private var started = false

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
        if (!appSettings.isAltMemoryInstalled()) return
        started = true

        try {
            startMcpServer()
            if (waitForReady()) {
                mcpServerManager.registerBuiltInServer(
                    id = SERVER_ID,
                    name = "Alt Memory",
                    url = ALT_MEMORY_URL,
                )
                mcpServerManager.connectAndDiscoverTools(SERVER_ID)
                runMigration()
            }
        } catch (_: Exception) {
        }
    }

    suspend fun stop() {
        if (!started) return
        started = false
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
        val checkCmd = sandboxController.executeCommand(
            command = "pgrep -f 'alt-memory.*mcp' || true",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
        )
        if (checkCmd.trim().isNotEmpty()) return

        sandboxController.executeCommand(
            command = "nohup alt-memory mcp --transport sse --port 8316 > /tmp/alt-memory.log 2>&1 &",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
        )
    }

    private suspend fun waitForReady(): Boolean {
        repeat(HEALTH_CHECK_RETRIES) {
            delay(HEALTH_CHECK_DELAY_MS)
            try {
                val client = McpClient(ALT_MEMORY_URL, emptyMap())
                client.initialize()
                client.close()
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }
}
