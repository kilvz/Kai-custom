package com.kai.custom.debug

import com.kai.custom.SandboxController
import com.kai.custom.data.AppSettings
import com.kai.custom.data.ChatRequest
import com.kai.custom.data.ChatResponse
import com.kai.custom.data.DataRepository
import com.kai.custom.data.ErrorResponse
import com.kai.custom.data.HealthResponse
import com.kai.custom.data.MemoryStore
import com.kai.custom.data.SettingUpdateRequest
import com.kai.custom.data.StateResponse
import com.kai.custom.data.ToolExecutor
import com.kai.custom.getAvailableTools
import com.kai.custom.isDebugBuild
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class DebugServer(
    private val dataRepository: DataRepository,
    private val memoryStore: MemoryStore,
    private val appSettings: AppSettings,
    private val toolExecutor: ToolExecutor,
    private val sandboxController: SandboxController? = null,
) {
    private var running = false
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

        embeddedServer(CIO, port = 18500, host = "127.0.0.1") {
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
                    val tools = getAvailableTools().map { mapOf("name" to it.schema.name, "description" to it.schema.description) }
                    call.respondText(json.encodeToString(tools), ContentType.Application.Json)
                }

                get("/memories") {
                    val err = auth(call) ?: return@get
                    val memories = memoryStore.getAllMemories().map { mapOf("key" to it.key, "content" to it.content, "category" to it.category.name, "protected" to it.protected.toString()) }
                    call.respondText(json.encodeToString(memories), ContentType.Application.Json)
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
                                "alt_memory_enabled" to dataRepository.isAltMemoryEnabled().toString(),
                                "sandbox_distro" to appSettings.getSandboxDistro(),
                            ),
                        ),
                        ContentType.Application.Json,
                    )
                }

                post("/chat") {
                    val err = auth(call) ?: return@post
                    val chatRequest = try {
                        call.receive<ChatRequest>()
                    } catch (_: Exception) {
                        call.respondText(json.encodeToString(ErrorResponse("Invalid JSON body")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                        return@post
                    }
                    val response = withContext(Dispatchers.Default) {
                        try {
                            dataRepository.askWithTools(chatRequest.message)
                        } catch (_: Exception) {
                            "Error processing request"
                        }
                    }
                    call.respondText(json.encodeToString(ChatResponse(response = response)), ContentType.Application.Json)
                }

                post("/sandbox/setup") {
                    val err = auth(call) ?: return@post
                    if (sandboxController == null) {
                        call.respondText(json.encodeToString(ErrorResponse("SandboxController not available")), ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                        return@post
                    }
                    sandboxController.setup()
                    call.respondText("Sandbox setup started", ContentType.Text.Plain)
                }

                post("/sandbox/install-packages") {
                    val err = auth(call) ?: return@post
                    if (sandboxController == null) {
                        call.respondText(json.encodeToString(ErrorResponse("SandboxController not available")), ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                        return@post
                    }
                    sandboxController.installPackages()
                    call.respondText("Sandbox package install started", ContentType.Text.Plain)
                }

                post("/sandbox/exec") {
                    val err = auth(call) ?: return@post
                    if (sandboxController == null) {
                        call.respondText(json.encodeToString(ErrorResponse("SandboxController not available")), ContentType.Application.Json, HttpStatusCode.ServiceUnavailable)
                        return@post
                    }
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
                    val updateRequest = try {
                        call.receive<SettingUpdateRequest>()
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
        }.start(wait = false)
        android.util.Log.d("DebugServer", "Started on 127.0.0.1:18500, token=$token")
    }

    fun stop() {
        running = false
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
