package com.kai.custom.debug

import com.kai.custom.SandboxController
import com.kai.custom.data.AltMemoryStatusResponse
import com.kai.custom.data.AppSettings
import com.kai.custom.data.AskWithToolsResult
import com.kai.custom.data.ChatRequest
import com.kai.custom.data.ChatResponse
import com.kai.custom.data.DataRepository
import com.kai.custom.data.ErrorResponse
import com.kai.custom.data.HealthResponse
import com.kai.custom.data.MemoryCategory
import com.kai.custom.data.MemoryRequest
import com.kai.custom.data.MemoryStore
import com.kai.custom.data.SearchRequest
import com.kai.custom.data.SettingUpdateRequest
import com.kai.custom.data.StateResponse
import com.kai.custom.data.ToolCallResponse
import com.kai.custom.data.ToolExecutor
import com.kai.custom.getAvailableTools
import com.kai.custom.isDebugBuild
import com.kai.custom.mcp.McpServerManager
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID

class DebugServer(
    private val dataRepository: DataRepository,
    private val memoryStore: MemoryStore,
    private val appSettings: AppSettings,
    private val toolExecutor: ToolExecutor,
    private val mcpServerManager: McpServerManager,
    private val sandboxController: SandboxController,
) {
    private var running = false
    private var serverJob: EmbeddedServer<*, *>? = null
    private var token: String = ""

    private val json = Json { prettyPrint = true }

    fun start() {
        if (running) return
        if (!isDebugBuild) {
            android.util.Log.w("DebugServer", "Not starting \u2014 not a debug build")
            return
        }

        token = UUID.randomUUID().toString().replace("-", "").take(32)
        running = true

        val s = embeddedServer(CIO, port = 18500, host = "127.0.0.1") {
            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = true
                        ignoreUnknownKeys = true
                    },
                )
            }
            routing {
                get("/health") {
                    call.respondText(json.encodeToString(HealthResponse(status = "ok", token = token)), ContentType.Application.Json)
                }

                get("/prompt") {
                    val err = auth(call) ?: return@get
                    val prompt = withContext(Dispatchers.Default) {
                        dataRepository.getActiveSystemPrompt()
                    }
                    call.respondText(prompt ?: "(no prompt)", ContentType.Text.Plain)
                }

                get("/history") {
                    val err = auth(call) ?: return@get
                    val n = call.request.queryParameters["n"]?.toIntOrNull() ?: 10
                    call.respondText(dataRepository.getRecentExchanges(n), ContentType.Text.Plain)
                }

                get("/state") {
                    val err = auth(call) ?: return@get
                    call.respondText(
                        json.encodeToString(
                            StateResponse(
                                historyCount = dataRepository.chatHistory.value.size,
                                memoryCount = memoryStore.getAllMemories().size,
                                toolCount = getAvailableTools().size,
                                isDaemonEnabled = dataRepository.isDaemonEnabled(),
                                isMemoryEnabled = dataRepository.isMemoryEnabled(),
                                isSchedulingEnabled = dataRepository.isSchedulingEnabled(),
                                isHeartbeatEnabled = dataRepository.getHeartbeatConfig().enabled,
                                currentServiceId = "",
                            ),
                        ),
                        ContentType.Application.Json,
                    )
                }

                get("/tools") {
                    val err = auth(call) ?: return@get
                    val tools = getAvailableTools().map { tool ->
                        buildJsonObject {
                            put("name", JsonPrimitive(tool.schema.name))
                            put("description", JsonPrimitive(tool.schema.description))
                            put("timeout_seconds", JsonPrimitive(tool.timeout.inWholeSeconds))
                            put("parameters", buildJsonObject {
                                tool.schema.parameters.forEach { (key, p) ->
                                    put(key, buildJsonObject {
                                        put("type", JsonPrimitive(p.type))
                                        put("description", JsonPrimitive(p.description))
                                        put("required", JsonPrimitive(p.required))
                                    })
                                }
                            })
                        }
                    }
                    call.respondText(json.encodeToString(tools), ContentType.Application.Json)
                }

                get("/tools/definitions") {
                    val err = auth(call) ?: return@get
                    val all = dataRepository.getToolDefinitions().map { info ->
                        mapOf(
                            "id" to info.id,
                            "name" to info.name,
                            "description" to info.description,
                            "enabled" to info.isEnabled.toString(),
                        )
                    }
                    call.respondText(json.encodeToString(all), ContentType.Application.Json)
                }

                post("/tools/{name}") {
                    val err = auth(call) ?: return@post
                    val name = call.parameters["name"] ?: run {
                        call.respondText(json.encodeToString(ToolCallResponse(success = false, name = "", error = "Missing tool name")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                        return@post
                    }
                    val body = try { call.receiveText() } catch (_: Exception) { "{}" }
                    val result = try {
                        toolExecutor.executeTool(name, body.ifBlank { "{}" })
                    } catch (e: Exception) {
                        call.respondText(json.encodeToString(ToolCallResponse(success = false, name = name, error = e.message ?: "Unknown error")), ContentType.Application.Json)
                        return@post
                    }
                    call.respondText(json.encodeToString(ToolCallResponse(success = true, name = name, result = result)), ContentType.Application.Json)
                }

                get("/memories") {
                    val err = auth(call) ?: return@get
                    val memories = memoryStore.getAllMemories().map { mapOf("key" to it.key, "content" to it.content, "category" to it.category.name, "protected" to it.protected.toString()) }
                    call.respondText(json.encodeToString(memories), ContentType.Application.Json)
                }

                get("/memory/{key}") {
                    val err = auth(call) ?: return@get
                    val key = call.parameters["key"] ?: run {
                        call.respondText(json.encodeToString(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                        return@get
                    }
                    val entry = memoryStore.getAllMemories().find { it.key == key }
                    if (entry == null) {
                        call.respondText(json.encodeToString(ErrorResponse("Not found")), ContentType.Application.Json, HttpStatusCode.NotFound)
                        return@get
                    }
                    call.respondText(json.encodeToString(mapOf("key" to entry.key, "content" to entry.content, "category" to entry.category.name, "protected" to entry.protected.toString())), ContentType.Application.Json)
                }

                post("/memory") {
                    val err = auth(call) ?: return@post
                    val raw = try { call.receiveText() } catch (_: Exception) { "" }
                    val body = try { json.decodeFromString<MemoryRequest>(raw) } catch (_: Exception) {
                        call.respondText(json.encodeToString(ErrorResponse("Invalid JSON body")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post
                    }
                    if (body.key.isBlank()) {
                        call.respondText(json.encodeToString(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post
                    }
                    val category = try { MemoryCategory.valueOf(body.category?.uppercase() ?: "GENERAL") } catch (_: Exception) { MemoryCategory.GENERAL }
                    val entry = memoryStore.store(body.key, body.content, category)
                    call.respondText(json.encodeToString(mapOf("success" to "true", "key" to entry.key, "content" to entry.content, "category" to entry.category.name)), ContentType.Application.Json)
                }

                delete("/memory/{key}") {
                    val err = auth(call) ?: return@delete
                    val key = call.parameters["key"] ?: run {
                        call.respondText(json.encodeToString(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@delete
                    }
                    val deleted = memoryStore.forget(key)
                    if (!deleted) {
                        call.respondText(json.encodeToString(ErrorResponse("Not found or protected")), ContentType.Application.Json, HttpStatusCode.NotFound); return@delete
                    }
                    call.respondText(json.encodeToString(mapOf("success" to "true", "key" to key)), ContentType.Application.Json)
                }

                post("/memory/search") {
                    val err = auth(call) ?: return@post
                    val raw = try { call.receiveText() } catch (_: Exception) { "" }
                    val body = try { json.decodeFromString<SearchRequest>(raw) } catch (_: Exception) {
                        call.respondText(json.encodeToString(ErrorResponse("Invalid JSON body. Expected: {\"query\":\"...\"}")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post
                    }
                    val results = memoryStore.searchMemories(body.query, body.limit ?: 10)
                    call.respondText(json.encodeToString(results.map { mapOf("key" to it.key, "content" to it.content, "category" to it.category.name, "hit_count" to it.hitCount.toString()) }), ContentType.Application.Json)
                }

                get("/alt-memory") {
                    val err = auth(call) ?: return@get
                    val allMemories = memoryStore.getAllMemories()
                    val status = AltMemoryStatusResponse(
                        enabled = appSettings.isAltMemoryEnabled(),
                        installed = appSettings.isAltMemoryInstalled(),
                        connected = mcpServerManager.isConnected("alt_memory"),
                        localMemoryCount = allMemories.size,
                        behaviorMemoryCount = allMemories.count { it.protected },
                        migrationComplete = appSettings.isAltMemoryMigrationComplete(),
                    )
                    call.respondText(json.encodeToString(status), ContentType.Application.Json)
                }

                get("/settings") {
                    val err = auth(call) ?: return@get
                    call.respondText(
                        json.encodeToString(
                            mapOf(
                                "soul_text" to dataRepository.getSoulUser(),
                                "persona_name" to dataRepository.getPersonaName(),
                                "active_persona_id" to dataRepository.getActivePersona().id,
                                "free_service_primary" to dataRepository.isFreeServicePrimary().toString(),
                                "memory_enabled" to dataRepository.isMemoryEnabled().toString(),
                                "scheduling_enabled" to dataRepository.isSchedulingEnabled().toString(),
                                "daemon_enabled" to dataRepository.isDaemonEnabled().toString(),
                                "heartbeat_enabled" to dataRepository.getHeartbeatConfig().enabled.toString(),
                                "sandbox_enabled" to dataRepository.isSandboxEnabled().toString(),
                                "sandbox_storage_mount_enabled" to dataRepository.isSandboxStorageMountEnabled().toString(),
                                "sandbox_root_enabled" to dataRepository.isSandboxRootEnabled().toString(),
                                "root_enabled" to dataRepository.isRootEnabled().toString(),
                                "shizuku_enabled" to dataRepository.isShizukuEnabled().toString(),
                                "notifications_enabled" to dataRepository.isNotificationsEnabled().toString(),
                                "dynamic_ui_enabled" to dataRepository.isDynamicUiEnabled().toString(),
                                "email_enabled" to dataRepository.isEmailEnabled().toString(),
                                "sms_enabled" to dataRepository.isSmsEnabled().toString(),
                                "telegram_enabled" to dataRepository.isTelegramEnabled().toString(),
                                "wake_word_enabled" to dataRepository.isWakeWordEnabled().toString(),
                                "preferred_language" to dataRepository.getPreferredLanguage(),
                                "debug_api_enabled" to dataRepository.isDebugApiEnabled().toString(),
                                "debug_endpoint_enabled" to dataRepository.isDebugEndpointEnabled().toString(),
                                "alt_memory_enabled" to dataRepository.isAltMemoryEnabled().toString(),
                                "alt_memory_installed" to appSettings.isAltMemoryInstalled().toString(),
                                "sandbox_distro" to appSettings.getSandboxDistro(),
                            ),
                        ),
                        ContentType.Application.Json,
                    )
                }

                post("/chat") {
                    val err = auth(call) ?: return@post
                    val raw = try { call.receiveText() } catch (_: Exception) { "" }
                    val chatRequest = try {
                        json.decodeFromString<ChatRequest>(raw)
                    } catch (_: Exception) {
                        call.respondText(json.encodeToString(ErrorResponse("Invalid JSON body")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                        return@post
                    }
                    val result = withContext(Dispatchers.Default) {
                        try {
                            dataRepository.askWithToolsVerbose(chatRequest.message)
                        } catch (e: Exception) {
                            AskWithToolsResult("Error: ${e.message}")
                        }
                    }
                    call.respondText(json.encodeToString(ChatResponse(response = result.response, toolCalls = result.toolCalls)), ContentType.Application.Json)
                }

                post("/sandbox/setup") {
                    val err = auth(call) ?: return@post
                    sandboxController.setup()
                    call.respondText("Sandbox setup started", ContentType.Text.Plain)
                }

                post("/sandbox/install-packages") {
                    val err = auth(call) ?: return@post
                    sandboxController.installPackages()
                    call.respondText("Sandbox package install started", ContentType.Text.Plain)
                }

                post("/sandbox/exec") {
                    val err = auth(call) ?: return@post
                    val command = call.receiveText().trim()
                    if (command.isBlank()) {
                        call.respondText(json.encodeToString(ErrorResponse("Missing command body")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                        return@post
                    }
                    val useRoot = call.request.queryParameters["root"]?.toBooleanStrictOrNull() ?: false
                    val timeout = call.request.queryParameters["timeout"]?.toLongOrNull() ?: 60L
                    val output = sandboxController.executeCommand(command, useRoot = useRoot, timeoutSeconds = timeout)
                    call.respondText(output, ContentType.Text.Plain)
                }

                post("/reset") {
                    val err = auth(call) ?: return@post
                    dataRepository.clearHistory()
                    call.respondText("Conversation reset", ContentType.Text.Plain)
                }

                post("/settings/{key}") {
                    val err = auth(call) ?: return@post
                    val key = call.parameters["key"] ?: run {
                        call.respondText(json.encodeToString(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                        return@post
                    }
                    val rawBody = try { call.receiveText() } catch (_: Exception) { "" }
                    val updateRequest = try {
                        json.decodeFromString<SettingUpdateRequest>(rawBody)
                    } catch (_: Exception) {
                        call.respondText(json.encodeToString(ErrorResponse("Invalid JSON body")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                        return@post
                    }
                    try {
                        val v = updateRequest.value
                        when (key) {
                            "soul_text" -> dataRepository.setSoulText(v)

                            "persona_name" -> dataRepository.setPersonaName(v)

                            "active_persona_id" -> dataRepository.switchPersona(v)

                            "preferred_language" -> dataRepository.setPreferredLanguage(v)

                            "free_service_primary" -> dataRepository.setFreeServicePrimary(v.toBoolean())

                            "memory_enabled" -> dataRepository.setMemoryEnabled(v.toBoolean())

                            "scheduling_enabled" -> dataRepository.setSchedulingEnabled(v.toBoolean())

                            "daemon_enabled" -> dataRepository.setDaemonEnabled(v.toBoolean())

                            "heartbeat_enabled" -> dataRepository.setHeartbeatEnabled(v.toBoolean())

                            "sandbox_enabled" -> dataRepository.setSandboxEnabled(v.toBoolean())

                            "sandbox_storage_mount_enabled" -> dataRepository.setSandboxStorageMountEnabled(v.toBoolean())

                            "sandbox_root_enabled" -> dataRepository.setSandboxRootEnabled(v.toBoolean())

                            "root_enabled" -> dataRepository.setRootEnabled(v.toBoolean())

                            "shizuku_enabled" -> dataRepository.setShizukuEnabled(v.toBoolean())

                            "notifications_enabled" -> dataRepository.setNotificationsEnabled(v.toBoolean())

                            "dynamic_ui_enabled" -> dataRepository.setDynamicUiEnabled(v.toBoolean())

                            "email_enabled" -> dataRepository.setEmailEnabled(v.toBoolean())

                            "sms_enabled" -> dataRepository.setSmsEnabled(v.toBoolean())

                            "telegram_enabled" -> dataRepository.setTelegramEnabled(v.toBoolean())

                            "wake_word_enabled" -> dataRepository.setWakeWordEnabled(v.toBoolean())

                            "debug_api_enabled" -> dataRepository.setDebugApiEnabled(v.toBoolean())

                            "debug_endpoint_enabled" -> dataRepository.setDebugEndpointEnabled(v.toBoolean())

                            "alt_memory_enabled" -> dataRepository.setAltMemoryEnabled(v.toBoolean())

                            "sandbox_distro" -> appSettings.setSandboxDistro(v)

                            else -> {
                                call.respondText(json.encodeToString(ErrorResponse("Unknown setting: $key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                                return@post
                            }
                        }
                        call.respondText("Updated $key", ContentType.Text.Plain)
                    } catch (_: Exception) {
                        call.respondText(json.encodeToString(ErrorResponse("Failed to update $key")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                    }
                }
            }
        }
        s.start(wait = false)
        serverJob = s
        android.util.Log.d("DebugServer", "Started on 127.0.0.1:18500, token=$token")
    }

    fun stop() {
        running = false
        serverJob?.stop(1000, 2000)
        serverJob = null
        android.util.Log.d("DebugServer", "Stopped")
    }

    val isRunning: Boolean get() = running

    private suspend fun auth(call: io.ktor.server.application.ApplicationCall): String? {
        val auth = call.request.headers["Authorization"]
        if (auth != "Bearer $token") {
            call.respondText(json.encodeToString(ErrorResponse("Invalid or missing token")), ContentType.Application.Json, HttpStatusCode.Unauthorized)
            return null
        }
        return token
    }
}
