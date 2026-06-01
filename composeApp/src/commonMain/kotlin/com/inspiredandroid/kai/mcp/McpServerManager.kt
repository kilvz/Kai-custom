package com.inspiredandroid.kai.mcp

import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

private val serverIdRegex = Regex("[^a-z0-9]")

class McpServerManager(private val appSettings: AppSettings) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val mutex = Mutex()
    private val clients = mutableMapOf<String, McpClient>()
    private val discoveredTools = mutableMapOf<String, List<McpToolMetadata>>()

    private var cachedServersJson: String? = null
    private var cachedServers: List<McpServerConfig> = emptyList()

    /** Servers managed internally (not persisted to settings, not user-removable). */
    private val builtInServers = mutableMapOf<String, McpServerConfig>()

    /** Register a built-in MCP server that is hidden from user settings. */
    fun registerBuiltInServer(id: String, name: String, url: String, headers: Map<String, String> = emptyMap()) {
        builtInServers[id] = McpServerConfig(id = id, name = name, url = url, headers = headers, isEnabled = true)
    }

    /** User-configured servers from AppSettings (excludes built-in). */
    fun getServers(): List<McpServerConfig> {
        val jsonStr = appSettings.getMcpServersJson()
        if (jsonStr.isBlank()) return emptyList()
        if (jsonStr == cachedServersJson) return cachedServers
        return try {
            json.decodeFromString<List<McpServerConfig>>(jsonStr).also {
                cachedServersJson = jsonStr
                cachedServers = it
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** All servers including built-in. */
    private fun getAllServers(): List<McpServerConfig> =
        getServers() + builtInServers.values.toList()

    private fun saveServers(servers: List<McpServerConfig>) {
        val jsonStr = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(McpServerConfig.serializer()), servers)
        appSettings.setMcpServersJson(jsonStr)
        cachedServersJson = jsonStr
        cachedServers = servers
    }

    fun addServer(name: String, url: String, headers: Map<String, String>): McpServerConfig {
        val servers = getServers().toMutableList()
        val id = generateServerId(name, servers)
        val config = McpServerConfig(id = id, name = name, url = url, headers = headers)
        servers.add(config)
        saveServers(servers)
        return config
    }

    fun removeServer(serverId: String) {
        if (builtInServers.containsKey(serverId)) return
        val servers = getServers().toMutableList()
        servers.removeAll { it.id == serverId }
        saveServers(servers)
        clients[serverId]?.close()
        clients.remove(serverId)
        discoveredTools.remove(serverId)
    }

    fun setServerEnabled(serverId: String, enabled: Boolean) {
        if (builtInServers.containsKey(serverId)) return
        val servers = getServers().toMutableList()
        val index = servers.indexOfFirst { it.id == serverId }
        if (index >= 0) {
            servers[index] = servers[index].copy(isEnabled = enabled)
            saveServers(servers)
        }
        if (!enabled) {
            clients[serverId]?.close()
            clients.remove(serverId)
            discoveredTools.remove(serverId)
        }
    }

    suspend fun connectAndDiscoverTools(serverId: String): Result<List<McpToolMetadata>> {
        val server = getAllServers().find { it.id == serverId }
            ?: return Result.failure(McpException("Server not found: $serverId"))

        // Close existing client if any
        mutex.withLock { clients[serverId] }?.close()

        val client = McpClient(server.url, server.headers)
        return try {
            client.initialize()
            val toolDefs = client.listTools()
            val metadata = toolDefs.map { def ->
                McpToolMetadata(
                    serverId = serverId,
                    name = def.name,
                    description = def.description ?: "",
                    inputSchema = def.inputSchema,
                )
            }
            mutex.withLock {
                clients[serverId] = client
                discoveredTools[serverId] = metadata
            }
            Result.success(metadata)
        } catch (e: Exception) {
            client.close()
            mutex.withLock {
                clients.remove(serverId)
                discoveredTools.remove(serverId)
            }
            Result.failure(e)
        }
    }

    fun getEnabledMcpTools(): List<Tool> {
        val enabledServers = getAllServers().filter { it.isEnabled }.map { it.id }.toSet()
        return buildList {
            for ((serverId, tools) in discoveredTools) {
                if (serverId !in enabledServers) continue
                val client = clients[serverId] ?: continue
                for (meta in tools) {
                    val toolId = McpTool.toolId(serverId, meta.name)
                    if (appSettings.isToolEnabled(toolId)) {
                        add(McpTool(client, meta))
                    }
                }
            }
        }
    }

    fun getToolsForServer(serverId: String): List<ToolInfo> {
        val tools = discoveredTools[serverId] ?: return emptyList()
        return tools.map { meta ->
            val toolId = McpTool.toolId(serverId, meta.name)
            ToolInfo(
                id = toolId,
                name = meta.name,
                description = meta.description,
                isEnabled = appSettings.isToolEnabled(toolId),
            )
        }
    }

    suspend fun connectEnabledServers() {
        val enabledServers = getAllServers().filter { it.isEnabled }
        coroutineScope {
            enabledServers
                .filter { !clients.containsKey(it.id) }
                .map { server ->
                    async {
                        try {
                            connectAndDiscoverTools(server.id)
                        } catch (_: Exception) {
                        }
                    }
                }
                .awaitAll()
        }
    }

    fun isConnected(serverId: String): Boolean = clients.containsKey(serverId)

    fun getClient(serverId: String): McpClient? = clients[serverId]

    private fun generateServerId(name: String, existing: List<McpServerConfig>): String {
        val base = name.lowercase().replace(serverIdRegex, "_").take(30)
        val existingIds = existing.map { it.id }.toSet()
        if (base !in existingIds) return base
        var counter = 2
        while ("${base}_$counter" in existingIds) counter++
        return "${base}_$counter"
    }
}
