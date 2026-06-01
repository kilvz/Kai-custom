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

    suspend fun setupAndStart() {
        if (started) return
        started = true

        try {
            installIfNeeded()
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

    private suspend fun installIfNeeded() {
        val check = sandboxController.executeCommand(
            command = "python3 -c 'import alt_memory; print(1)' 2>/dev/null",
            sessionId = SandboxSessions.SYSTEM,
        )
        if (check.trim() == "1") return

        sandboxController.executeCommand(
            command = "pip install alt-memory > /tmp/alt-install.log 2>&1 &",
            sessionId = SandboxSessions.SYSTEM,
        )
        repeat(24) {
            delay(5_000L)
            val status = sandboxController.executeCommand(
                command = "tail -1 /tmp/alt-install.log 2>/dev/null || echo ''",
                sessionId = SandboxSessions.SYSTEM,
            )
            val trimmed = status.trim()
            if (trimmed.contains("Successfully installed") || trimmed.contains("already satisfied")) return
        }
    }

    private suspend fun startMcpServer() {
        val checkCmd = sandboxController.executeCommand(
            command = "pgrep -f 'alt-memory.*mcp' || true",
            sessionId = SandboxSessions.SYSTEM,
        )
        if (checkCmd.trim().isNotEmpty()) return

        sandboxController.executeCommand(
            command = "nohup alt-memory mcp --transport sse --port 8316 > /tmp/alt-memory.log 2>&1 &",
            sessionId = SandboxSessions.SYSTEM,
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
