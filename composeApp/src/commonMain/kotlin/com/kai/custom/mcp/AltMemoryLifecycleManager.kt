package com.kai.custom.mcp

import com.kai.custom.SandboxController
import com.kai.custom.SandboxSessions
import com.kai.custom.data.AppSettings
import com.kai.custom.data.MemoryStore
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class AltMemoryLifecycleManager(
    private val sandboxController: SandboxController,
    private val mcpServerManager: McpServerManager,
    private val appSettings: AppSettings,
    private val memoryStore: MemoryStore,
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
        if (appSettings.isAltMemoryMigrationComplete()) return
        val client = mcpServerManager.getClient(SERVER_ID) ?: return
        val memories = memoryStore.getAllMemories()
        if (memories.isEmpty()) {
            appSettings.setAltMemoryMigrationComplete(true)
            return
        }
        var migrated = 0
        var failed = 0
        for (entry in memories) {
            try {
                client.callTool("memory_store", buildJsonObject {
                    put("key", JsonPrimitive(entry.key))
                    put("content", JsonPrimitive(entry.content))
                    put("category", JsonPrimitive(entry.category.name))
                    put("hit_count", JsonPrimitive(entry.hitCount))
                })
                migrated++
            } catch (_: Exception) {
                failed++
            }
        }
        appSettings.setAltMemoryMigrationComplete(true)
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
