package com.kai.custom.debug

import com.kai.custom.SandboxController
import com.kai.custom.data.AltMemoryStatusResponse
import com.kai.custom.data.ApiKeyUpdateRequest
import com.kai.custom.data.AppSettings
import com.kai.custom.data.AskWithToolsResult
import com.kai.custom.data.BaseUrlUpdateRequest
import com.kai.custom.data.ChatRequest
import com.kai.custom.data.ChatResponse
import com.kai.custom.data.ConversationSummary
import com.kai.custom.data.DataRepository
import com.kai.custom.data.EmailAccountRequest
import com.kai.custom.data.ErrorResponse
import com.kai.custom.data.FullSettingsResponse
import com.kai.custom.data.HealthResponse
import com.kai.custom.data.HeartbeatConfigResponse
import com.kai.custom.data.HeartbeatUpdateRequest
import com.kai.custom.data.ImportRequest
import com.kai.custom.data.InstallSkillRequest
import com.kai.custom.data.LocalModelSummary
import com.kai.custom.data.McpServerAddRequest
import com.kai.custom.data.McpServerEntry
import com.kai.custom.data.MemoryCategory
import com.kai.custom.data.MemoryRequest
import com.kai.custom.data.MemoryStore
import com.kai.custom.data.ModelSelectRequest
import com.kai.custom.data.PersonaListEntry
import com.kai.custom.data.SearchRequest
import com.kai.custom.data.ServiceInstanceEntry
import com.kai.custom.data.ServiceRemoveRequest
import com.kai.custom.data.SettingUpdateRequest
import com.kai.custom.data.SkillSummary
import com.kai.custom.data.SmsDraftSummary
import com.kai.custom.data.SplinterlandsStatusResponse
import com.kai.custom.data.StateResponse
import com.kai.custom.data.TelegramStatusResponse
import com.kai.custom.data.ThemeMode
import com.kai.custom.data.ToolCallResponse
import com.kai.custom.data.ToolExecutor
import com.kai.custom.data.WakeWordSettings
import com.kai.custom.getAvailableTools
import com.kai.custom.whatsapp.WhatsAppLifecycleManager
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
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.response.header
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
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
    private val whatsAppLifecycleManager: WhatsAppLifecycleManager,
) {
    private var running = false
    private var serverJob: EmbeddedServer<*, *>? = null
    private var token: String = ""

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

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
                json(Json { prettyPrint = true; ignoreUnknownKeys = true })
            }
            routing {
                // ======================= HEALTH =======================
                get("/health") {
                    call.respondText(json.encodeToString(HealthResponse(status = "ok", token = token)), ContentType.Application.Json)
                }

                // ======================= PROMPT =======================
                get("/prompt") {
                    val err = auth(call) ?: return@get
                    call.respondText(withContext(Dispatchers.Default) { dataRepository.getActiveSystemPrompt() } ?: "(no prompt)", ContentType.Text.Plain)
                }

                // ======================= HISTORY =======================
                get("/history") {
                    val err = auth(call) ?: return@get
                    val n = call.request.queryParameters["n"]?.toIntOrNull() ?: 10
                    call.respondText(dataRepository.getRecentExchanges(n), ContentType.Text.Plain)
                }

                // ======================= STATE =======================
                get("/state") {
                    val err = auth(call) ?: return@get
                    call.respondText(json.encodeToString(StateResponse(
                        historyCount = dataRepository.chatHistory.value.size,
                        memoryCount = memoryStore.getAllMemories().size,
                        toolCount = getAvailableTools().size,
                        isDaemonEnabled = dataRepository.isDaemonEnabled(),
                        isMemoryEnabled = dataRepository.isMemoryEnabled(),
                        isSchedulingEnabled = dataRepository.isSchedulingEnabled(),
                        isHeartbeatEnabled = dataRepository.getHeartbeatConfig().enabled,
                        currentServiceId = dataRepository.currentService().id,
                        sandboxInstalled = sandboxController.status.value.installed,
                        sandboxReady = sandboxController.status.value.ready,
                    )), ContentType.Application.Json)
                }

                // ======================= FULL SETTINGS =======================
                get("/settings") {
                    val err = auth(call) ?: return@get
                    val cfg = dataRepository.getHeartbeatConfig()
                    val activePersona = dataRepository.getActivePersona()
                    val personaList = dataRepository.getAllPersonas().map {
                        buildJsonObject {
                            put("id", JsonPrimitive(it.id))
                            put("name", JsonPrimitive(it.name))
                            put("description", JsonPrimitive(it.description))
                            put("behaviorStyle", JsonPrimitive(it.behaviorStyle.name))
                            put("languageStyle", JsonPrimitive(it.languageStyle.name))
                            put("characterType", JsonPrimitive(it.characterType.name))
                            put("isBuiltIn", JsonPrimitive(it.isBuiltIn))
                            put("isActive", JsonPrimitive(it.id == activePersona.id))
                        }
                    }
                    val configuredServices = dataRepository.getConfiguredServiceInstances().map {
                        buildJsonObject {
                            put("instanceId", JsonPrimitive(it.instanceId))
                            put("serviceId", JsonPrimitive(it.serviceId))
                        }
                    }
                    call.respondText(json.encodeToString(buildJsonObject {
                        put("soul_user", JsonPrimitive(dataRepository.getSoulUser()))
                        put("soul_auto", JsonPrimitive(dataRepository.getSoulAuto()))
                        put("persona_name", JsonPrimitive(dataRepository.getPersonaName()))
                        put("active_persona_id", JsonPrimitive(activePersona.id))
                        put("persona_list", kotlinx.serialization.json.JsonArray(personaList))
                        put("current_service_id", JsonPrimitive(dataRepository.currentService().id))
                        put("configured_services", kotlinx.serialization.json.JsonArray(configuredServices))
                        put("free_fallback_enabled", JsonPrimitive(dataRepository.isFreeFallbackEnabled()))
                        put("free_mode", JsonPrimitive(dataRepository.getFreeMode().name))
                        put("free_service_primary", JsonPrimitive(dataRepository.isFreeServicePrimary()))
                        put("memory_enabled", JsonPrimitive(dataRepository.isMemoryEnabled()))
                        put("alt_memory_enabled", JsonPrimitive(dataRepository.isAltMemoryEnabled()))
                        put("alt_memory_installed", JsonPrimitive(appSettings.isAltMemoryInstalled()))
                        put("scheduling_enabled", JsonPrimitive(dataRepository.isSchedulingEnabled()))
                        put("dynamic_ui_enabled", JsonPrimitive(dataRepository.isDynamicUiEnabled()))
                        put("theme_mode", JsonPrimitive(dataRepository.getThemeMode().name))
                        put("interactive_mode", JsonPrimitive(dataRepository.isInteractiveModeActive()))
                        put("daemon_enabled", JsonPrimitive(dataRepository.isDaemonEnabled()))
                        put("wake_word_enabled", JsonPrimitive(dataRepository.isWakeWordEnabled()))
                        put("wake_word_phrase", JsonPrimitive(dataRepository.getWakeWordPhrase()))
                        put("wake_word_mode", JsonPrimitive(dataRepository.getWakeWordMode()))
                        put("wake_word_template", JsonPrimitive(dataRepository.getWakeWordTemplate()))
                        put("sandbox_enabled", JsonPrimitive(dataRepository.isSandboxEnabled()))
                        put("sandbox_storage_mount", JsonPrimitive(dataRepository.isSandboxStorageMountEnabled()))
                        put("sandbox_distro", JsonPrimitive(dataRepository.getSandboxDistro()))
                        put("sandbox_root_enabled", JsonPrimitive(dataRepository.isSandboxRootEnabled()))
                        put("heartbeat_enabled", JsonPrimitive(cfg.enabled))
                        put("heartbeat_interval_minutes", JsonPrimitive(cfg.intervalMinutes))
                        put("heartbeat_active_hours_start", JsonPrimitive(cfg.activeHoursStart))
                        put("heartbeat_active_hours_end", JsonPrimitive(cfg.activeHoursEnd))
                        put("heartbeat_prompt", JsonPrimitive(dataRepository.getHeartbeatPrompt()))
                        put("email_enabled", JsonPrimitive(dataRepository.isEmailEnabled()))
                        put("email_poll_interval_minutes", JsonPrimitive(dataRepository.getEmailPollIntervalMinutes()))
                        put("sms_enabled", JsonPrimitive(dataRepository.isSmsEnabled()))
                        put("sms_send_enabled", JsonPrimitive(dataRepository.isSmsSendEnabled()))
                        put("sms_poll_interval_minutes", JsonPrimitive(dataRepository.getSmsPollIntervalMinutes()))
                        put("notifications_enabled", JsonPrimitive(dataRepository.isNotificationsEnabled()))
                        put("shizuku_enabled", JsonPrimitive(dataRepository.isShizukuEnabled()))
                        put("root_enabled", JsonPrimitive(dataRepository.isRootEnabled()))
                        put("debug_api_enabled", JsonPrimitive(dataRepository.isDebugApiEnabled()))
                        put("debug_endpoint_enabled", JsonPrimitive(dataRepository.isDebugEndpointEnabled()))
                        put("telegram_enabled", JsonPrimitive(dataRepository.isTelegramEnabled()))
                        put("ssh_enabled", JsonPrimitive(appSettings.isSshEnabled()))
                        put("preferred_language", JsonPrimitive(dataRepository.getPreferredLanguage()))
                        put("ui_scale", JsonPrimitive(dataRepository.getUiScale()))
                        put("splinterlands_enabled", JsonPrimitive(appSettings.isSplinterlandsEnabled()))
                        put("active_skill_id", JsonPrimitive(dataRepository.getActiveSkill()?.id ?: ""))
                        put("alt_memory_migration_complete", JsonPrimitive(appSettings.isAltMemoryMigrationComplete()))
                        put("whatsapp_enabled", JsonPrimitive(appSettings.isWhatsAppEnabled()))
                        put("whatsapp_read_only", JsonPrimitive(appSettings.isWhatsAppReadOnly()))
                        put("whatsapp_reply_mode", JsonPrimitive(appSettings.getWhatsAppReplyMode()))
                        put("whatsapp_allowed_contacts", JsonPrimitive(appSettings.getWhatsAppAllowedContacts()))
                        put("whatsapp_read_receipt", JsonPrimitive(appSettings.isWhatsAppReadReceipt()))
                        put("whatsapp_installed", JsonPrimitive(appSettings.isWhatsAppInstalled()))
                        put("whatsapp_authenticated", JsonPrimitive(appSettings.isWhatsAppAuthenticated()))
                        put("baileys_browser_name", JsonPrimitive(appSettings.getBaileysBrowserName()))
                        put("baileys_browser_version", JsonPrimitive(appSettings.getBaileysBrowserVersion()))
                        put("baileys_mark_online", JsonPrimitive(appSettings.getBaileysMarkOnline()))
                        put("baileys_sync_history", JsonPrimitive(appSettings.getBaileysSyncHistory()))
                        put("baileys_link_previews", JsonPrimitive(appSettings.getBaileysLinkPreviews()))
                    }), ContentType.Application.Json)
                }

                // ======================= UPDATE SETTING =======================
                post("/settings/{key}") {
                    val err = auth(call) ?: return@post
                    val key = call.parameters["key"] ?: run { call.respondText(json.encodeToString(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    val rawBody = try { call.receiveText() } catch (_: Exception) { "" }
                    val updateRequest = try { json.decodeFromString<SettingUpdateRequest>(rawBody) } catch (_: Exception) {
                        call.respondText(json.encodeToString(ErrorResponse("Invalid JSON body")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post
                    }
                    try {
                        val v = updateRequest.value
                        when (key) {
                            "soul_text" -> dataRepository.setSoulText(v)
                            "persona_name" -> dataRepository.setPersonaName(v)
                            "active_persona_id" -> dataRepository.switchPersona(v)
                            "preferred_language" -> dataRepository.setPreferredLanguage(v)
                            "free_service_primary" -> dataRepository.setFreeServicePrimary(v.toBoolean())
                            "free_fallback_enabled" -> dataRepository.setFreeFallbackEnabled(v.toBoolean())
                            "free_mode" -> { val mode = com.kai.custom.data.FreeMode.entries.find { it.name == v.uppercase() }; if (mode != null) dataRepository.setFreeMode(mode) }
                            "memory_enabled" -> dataRepository.setMemoryEnabled(v.toBoolean())
                            "alt_memory_enabled" -> dataRepository.setAltMemoryEnabled(v.toBoolean())
                            "alt_memory_installed" -> appSettings.setAltMemoryInstalled(v.toBoolean())
                            "alt_memory_migration_complete" -> appSettings.setAltMemoryMigrationComplete(v.toBoolean())
                            "scheduling_enabled" -> dataRepository.setSchedulingEnabled(v.toBoolean())
                            "dynamic_ui_enabled" -> dataRepository.setDynamicUiEnabled(v.toBoolean())
                            "theme_mode" -> { val mode = ThemeMode.entries.find { it.name.equals(v, ignoreCase = true) }; if (mode != null) dataRepository.setThemeMode(mode) }
                            "interactive_mode" -> dataRepository.setInteractiveMode(v.toBoolean())
                            "daemon_enabled" -> dataRepository.setDaemonEnabled(v.toBoolean())
                            "wake_word_enabled" -> dataRepository.setWakeWordEnabled(v.toBoolean())
                            "wake_word_phrase" -> dataRepository.setWakeWordPhrase(v)
                            "wake_word_mode" -> dataRepository.setWakeWordMode(v)
                            "wake_word_template" -> dataRepository.setWakeWordTemplate(v)
                            "sandbox_enabled" -> dataRepository.setSandboxEnabled(v.toBoolean())
                            "sandbox_storage_mount" -> dataRepository.setSandboxStorageMountEnabled(v.toBoolean())
                            "sandbox_storage_mount_enabled" -> dataRepository.setSandboxStorageMountEnabled(v.toBoolean())
                            "sandbox_distro" -> dataRepository.setSandboxDistro(v)
                            "sandbox_root_enabled" -> dataRepository.setSandboxRootEnabled(v.toBoolean())
                            "heartbeat_enabled" -> dataRepository.setHeartbeatEnabled(v.toBoolean())
                            "root_enabled" -> dataRepository.setRootEnabled(v.toBoolean())
                            "shizuku_enabled" -> dataRepository.setShizukuEnabled(v.toBoolean())
                            "notifications_enabled" -> dataRepository.setNotificationsEnabled(v.toBoolean())
                            "email_enabled" -> dataRepository.setEmailEnabled(v.toBoolean())
                            "sms_enabled" -> dataRepository.setSmsEnabled(v.toBoolean())
                            "sms_send_enabled" -> dataRepository.setSmsSendEnabled(v.toBoolean())
                            "telegram_enabled" -> dataRepository.setTelegramEnabled(v.toBoolean())
                            "debug_api_enabled" -> dataRepository.setDebugApiEnabled(v.toBoolean())
                            "debug_endpoint_enabled" -> dataRepository.setDebugEndpointEnabled(v.toBoolean())
                            "ui_scale" -> dataRepository.setUiScale(v.toFloat())
                            "heartbeat_interval_minutes" -> dataRepository.setHeartbeatIntervalMinutes(v.toInt())
                            "heartbeat_active_hours_start" -> {
                                val hours = dataRepository.getHeartbeatConfig()
                                dataRepository.setHeartbeatActiveHours(v.toInt(), hours.activeHoursEnd)
                            }
                            "heartbeat_active_hours_end" -> {
                                val hours = dataRepository.getHeartbeatConfig()
                                dataRepository.setHeartbeatActiveHours(hours.activeHoursStart, v.toInt())
                            }
                            "heartbeat_prompt" -> dataRepository.setHeartbeatPrompt(v)
                            "email_poll_interval_minutes" -> dataRepository.setEmailPollIntervalMinutes(v.toInt())
                            "sms_poll_interval_minutes" -> dataRepository.setSmsPollIntervalMinutes(v.toInt())
                            "soul_user" -> dataRepository.setSoulUser(v)
                            "whatsapp_enabled" -> appSettings.setWhatsAppEnabled(v.toBoolean())
                            "whatsapp_read_only" -> appSettings.setWhatsAppReadOnly(v.toBoolean())
                            "whatsapp_reply_mode" -> appSettings.setWhatsAppReplyMode(v)
                            "whatsapp_allowed_contacts" -> appSettings.setWhatsAppAllowedContacts(v)
                            "whatsapp_read_receipt" -> appSettings.setWhatsAppReadReceipt(v.toBoolean())
                            "baileys_browser_name" -> appSettings.setBaileysBrowserName(v)
                            "baileys_browser_version" -> appSettings.setBaileysBrowserVersion(v)
                            "baileys_mark_online" -> appSettings.setBaileysMarkOnline(v.toBoolean())
                            "baileys_sync_history" -> appSettings.setBaileysSyncHistory(v.toBoolean())
                            "baileys_link_previews" -> appSettings.setBaileysLinkPreviews(v.toBoolean())
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

                // ======================= PERSONAS =======================
                get("/personas") {
                    val err = auth(call) ?: return@get
                    val activeId = dataRepository.getActivePersona().id
                    val personas = dataRepository.getAllPersonas().map {
                        PersonaListEntry(id = it.id, name = it.name, description = it.description,
                            behaviorStyle = it.behaviorStyle.name, languageStyle = it.languageStyle.name,
                            characterType = it.characterType.name, isBuiltIn = it.isBuiltIn, isActive = it.id == activeId)
                    }
                    call.respondText(json.encodeToString(personas), ContentType.Application.Json)
                }

                post("/persona/save") {
                    val err = auth(call) ?: return@post
                    try {
                        val config = json.decodeFromString<com.kai.custom.data.PersonaConfig>(call.receiveText())
                        dataRepository.savePersona(config)
                        call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)); put("id", JsonPrimitive(config.id)) }), ContentType.Application.Json)
                    } catch (e: Exception) {
                        call.respondText(json.encodeToString(ErrorResponse(e.message ?: "Invalid persona")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                    }
                }

                post("/persona/switch/{id}") {
                    val err = auth(call) ?: return@post
                    val id = call.parameters["id"] ?: run { call.respondText(json.encodeToString(ErrorResponse("Missing id")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    withContext(Dispatchers.Default) { dataRepository.switchPersona(id) }
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)); put("active_persona_id", JsonPrimitive(id)) }), ContentType.Application.Json)
                }

                delete("/persona/{id}") {
                    val err = auth(call) ?: return@delete
                    val id = call.parameters["id"] ?: run { call.respondText(json.encodeToString(ErrorResponse("Missing id")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@delete }
                    dataRepository.deletePersona(id)
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)); put("deleted", JsonPrimitive(id)) }), ContentType.Application.Json)
                }

                // ======================= CONVERSATIONS =======================
                get("/conversations") {
                    val err = auth(call) ?: return@get
                    val convos = dataRepository.savedConversations.value.map {
                        ConversationSummary(id = it.id, title = it.title, type = it.type, messageCount = it.messages.size, createdAt = it.createdAt, updatedAt = it.updatedAt)
                    }
                    call.respondText(json.encodeToString(convos), ContentType.Application.Json)
                }

                post("/conversation/load/{id}") {
                    val err = auth(call) ?: return@post
                    val id = call.parameters["id"] ?: run { call.respondText(json.encodeToString(ErrorResponse("Missing id")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    dataRepository.loadConversation(id)
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)); put("conversation_id", JsonPrimitive(id)) }), ContentType.Application.Json)
                }

                post("/conversation/new") {
                    val err = auth(call) ?: return@post
                    dataRepository.startNewChat()
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
                }

                post("/conversation/delete/{id}") {
                    val err = auth(call) ?: return@post
                    val id = call.parameters["id"] ?: run { call.respondText(json.encodeToString(ErrorResponse("Missing id")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    withContext(Dispatchers.Default) { dataRepository.deleteConversation(id) }
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)); put("deleted", JsonPrimitive(id)) }), ContentType.Application.Json)
                }

                // ======================= SERVICES =======================
                get("/services") {
                    val err = auth(call) ?: return@get
                    val all = com.kai.custom.data.Service.all.map { s ->
                        buildJsonObject {
                            put("id", JsonPrimitive(s.id))
                            put("display_name", JsonPrimitive(s.displayName))
                            put("requires_api_key", JsonPrimitive(s.requiresApiKey))
                            put("default_model", JsonPrimitive(s.defaultModel ?: ""))
                            put("is_on_device", JsonPrimitive(s.isOnDevice))
                        }
                    }
                    call.respondText(json.encodeToString(all), ContentType.Application.Json)
                }

                post("/service/add/{serviceId}") {
                    val err = auth(call) ?: return@post
                    val serviceId = call.parameters["serviceId"] ?: run { call.respondText(json.encodeToString(ErrorResponse("Missing serviceId")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    val instance = dataRepository.addConfiguredService(serviceId)
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)); put("instance_id", JsonPrimitive(instance.instanceId)); put("service_id", JsonPrimitive(serviceId)) }), ContentType.Application.Json)
                }

                post("/service/remove") {
                    val err = auth(call) ?: return@post
                    val body = try { json.decodeFromString<ServiceRemoveRequest>(call.receiveText()) } catch (_: Exception) { call.respondText(json.encodeToString(ErrorResponse("Invalid body")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    dataRepository.removeConfiguredService(body.instanceId)
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
                }

                post("/service/api-key") {
                    val err = auth(call) ?: return@post
                    val body = try { json.decodeFromString<ApiKeyUpdateRequest>(call.receiveText()) } catch (_: Exception) { call.respondText(json.encodeToString(ErrorResponse("Invalid body")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    dataRepository.updateInstanceApiKey(body.instanceId, body.apiKey)
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
                }

                post("/service/base-url") {
                    val err = auth(call) ?: return@post
                    val body = try { json.decodeFromString<BaseUrlUpdateRequest>(call.receiveText()) } catch (_: Exception) { call.respondText(json.encodeToString(ErrorResponse("Invalid body")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    dataRepository.updateInstanceBaseUrl(body.instanceId, body.baseUrl)
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
                }

                post("/service/model") {
                    val err = auth(call) ?: return@post
                    val body = try { json.decodeFromString<ModelSelectRequest>(call.receiveText()) } catch (_: Exception) { call.respondText(json.encodeToString(ErrorResponse("Invalid body")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    dataRepository.updateInstanceSelectedModel(body.instanceId, com.kai.custom.data.Service.fromId(body.serviceId), body.modelId)
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
                }

                // ======================= TOOLS =======================
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
                    call.respondText(json.encodeToString(dataRepository.getToolDefinitions().map { info ->
                        buildJsonObject { put("id", JsonPrimitive(info.id)); put("name", JsonPrimitive(info.name)); put("description", JsonPrimitive(info.description)); put("enabled", JsonPrimitive(info.isEnabled)) }
                    }), ContentType.Application.Json)
                }

                get("/tools/enabled") {
                    val err = auth(call) ?: return@get
                    val enabled = dataRepository.getToolDefinitions().associate { it.id to it.isEnabled }
                    call.respondText(json.encodeToString(enabled), ContentType.Application.Json)
                }

                post("/tools/enabled/{toolId}") {
                    val err = auth(call) ?: return@post
                    val toolId = call.parameters["toolId"] ?: run { call.respondText(json.encodeToString(ErrorResponse("Missing toolId")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    val body = try { json.decodeFromString<SettingUpdateRequest>(call.receiveText()) } catch (_: Exception) {
                        call.respondText(json.encodeToString(ErrorResponse("Invalid body")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post
                    }
                    dataRepository.setToolEnabled(toolId, body.value.toBoolean())
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)); put("tool_id", JsonPrimitive(toolId)); put("enabled", JsonPrimitive(body.value)) }), ContentType.Application.Json)
                }

                post("/tools/{name}") {
                    val err = auth(call) ?: return@post
                    val name = call.parameters["name"] ?: run {
                        call.respondText(json.encodeToString(ToolCallResponse(success = false, name = "", error = "Missing tool name")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post
                    }
                    val body = try { call.receiveText() } catch (_: Exception) { "{}" }
                    val result = try {
                        toolExecutor.executeTool(name, body.ifBlank { "{}" })
                    } catch (e: Exception) {
                        call.respondText(json.encodeToString(ToolCallResponse(success = false, name = name, error = e.message ?: "Unknown error")), ContentType.Application.Json); return@post
                    }
                    call.respondText(json.encodeToString(ToolCallResponse(success = true, name = name, result = result)), ContentType.Application.Json)
                }

                // ======================= MEMORY =======================
                get("/memories") {
                    val err = auth(call) ?: return@get
                    call.respondText(json.encodeToString(memoryStore.getAllMemories().map {
                        buildJsonObject { put("key", JsonPrimitive(it.key)); put("content", JsonPrimitive(it.content)); put("category", JsonPrimitive(it.category.name)); put("protected", JsonPrimitive(it.protected)) }
                    }), ContentType.Application.Json)
                }

                get("/memory/{key}") {
                    val err = auth(call) ?: return@get
                    val key = call.parameters["key"] ?: run { call.respondText(json.encodeToString(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@get }
                    val entry = memoryStore.getAllMemories().find { it.key == key }
                    if (entry == null) { call.respondText(json.encodeToString(ErrorResponse("Not found")), ContentType.Application.Json, HttpStatusCode.NotFound); return@get }
                    call.respondText(json.encodeToString(buildJsonObject { put("key", JsonPrimitive(entry.key)); put("content", JsonPrimitive(entry.content)); put("category", JsonPrimitive(entry.category.name)); put("protected", JsonPrimitive(entry.protected)) }), ContentType.Application.Json)
                }

                post("/memory") {
                    val err = auth(call) ?: return@post
                    val raw = try { call.receiveText() } catch (_: Exception) { "" }
                    val body = try { json.decodeFromString<MemoryRequest>(raw) } catch (_: Exception) { call.respondText(json.encodeToString(ErrorResponse("Invalid JSON")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    if (body.key.isBlank()) { call.respondText(json.encodeToString(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    val category = try { MemoryCategory.valueOf(body.category?.uppercase() ?: "GENERAL") } catch (_: Exception) { MemoryCategory.GENERAL }
                    val entry = withContext(Dispatchers.Default) { memoryStore.store(body.key, body.content, category) }
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)); put("key", JsonPrimitive(entry.key)); put("content", JsonPrimitive(entry.content)); put("category", JsonPrimitive(entry.category.name)) }), ContentType.Application.Json)
                }

                delete("/memory/{key}") {
                    val err = auth(call) ?: return@delete
                    val key = call.parameters["key"] ?: run { call.respondText(json.encodeToString(ErrorResponse("Missing key")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@delete }
                    val deleted = withContext(Dispatchers.Default) { memoryStore.forget(key) }
                    if (!deleted) { call.respondText(json.encodeToString(ErrorResponse("Not found or protected")), ContentType.Application.Json, HttpStatusCode.NotFound); return@delete }
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)); put("key", JsonPrimitive(key)) }), ContentType.Application.Json)
                }

                post("/memory/search") {
                    val err = auth(call) ?: return@post
                    val raw = try { call.receiveText() } catch (_: Exception) { "" }
                    val body = try { json.decodeFromString<SearchRequest>(raw) } catch (_: Exception) { call.respondText(json.encodeToString(ErrorResponse("Invalid body")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    val results = memoryStore.searchMemories(body.query, body.limit ?: 10)
                    call.respondText(json.encodeToString(results.map { buildJsonObject { put("key", JsonPrimitive(it.key)); put("content", JsonPrimitive(it.content)); put("category", JsonPrimitive(it.category.name)); put("hit_count", JsonPrimitive(it.hitCount)) } }), ContentType.Application.Json)
                }

                // ======================= ALT-MEMORY =======================
                get("/alt-memory") {
                    val err = auth(call) ?: return@get
                    val allMemories = memoryStore.getAllMemories()
                    call.respondText(json.encodeToString(AltMemoryStatusResponse(
                        enabled = appSettings.isAltMemoryEnabled(), installed = appSettings.isAltMemoryInstalled(),
                        connected = mcpServerManager.isConnected("alt_memory"),
                        localMemoryCount = allMemories.size, behaviorMemoryCount = allMemories.count { it.protected },
                        migrationComplete = appSettings.isAltMemoryMigrationComplete(),
                    )), ContentType.Application.Json)
                }

                // ======================= MCP SERVERS =======================
                get("/mcp/servers") {
                    val err = auth(call) ?: return@get
                    val servers = dataRepository.getMcpServers().map {
                        McpServerEntry(id = it.id, name = it.name, url = it.url, enabled = it.isEnabled, connected = dataRepository.isMcpServerConnected(it.id))
                    }
                    call.respondText(json.encodeToString(servers), ContentType.Application.Json)
                }

                post("/mcp/add") {
                    val err = auth(call) ?: return@post
                    val body = try { json.decodeFromString<McpServerAddRequest>(call.receiveText()) } catch (_: Exception) { call.respondText(json.encodeToString(ErrorResponse("Invalid body")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    val config = withContext(Dispatchers.Default) { dataRepository.addMcpServer(body.name, body.url, emptyMap()) }
                        call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)); put("id", JsonPrimitive(config.id)); put("name", JsonPrimitive(config.name)) }), ContentType.Application.Json)
                }

                delete("/mcp/{serverId}") {
                    val err = auth(call) ?: return@delete
                    val serverId = call.parameters["serverId"] ?: run { call.respondText(json.encodeToString(ErrorResponse("Missing serverId")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@delete }
                    dataRepository.removeMcpServer(serverId)
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
                }

                post("/mcp/connect/{serverId}") {
                    val err = auth(call) ?: return@post
                    val serverId = call.parameters["serverId"] ?: run { call.respondText(json.encodeToString(ErrorResponse("Missing serverId")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    val result = withContext(Dispatchers.Default) { dataRepository.connectMcpServer(serverId) }
                    if (result.isSuccess) {
                        val toolCount = result.getOrNull()?.size ?: 0
                        call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)); put("tools", JsonPrimitive(toolCount)) }), ContentType.Application.Json)
                    } else {
                        call.respondText(json.encodeToString(ErrorResponse(result.exceptionOrNull()?.message ?: "Connection failed")), ContentType.Application.Json, HttpStatusCode.InternalServerError)
                    }
                }

                // ======================= SKILLS =======================
                get("/skills") {
                    val err = auth(call) ?: return@get
                    val activeSkill = dataRepository.getActiveSkill()
                    val skills = dataRepository.getInstalledSkills().map {
                        SkillSummary(id = it.id, name = it.displayName, version = "", isActive = activeSkill?.id == it.id)
                    }
                    call.respondText(json.encodeToString(skills), ContentType.Application.Json)
                }

                post("/skill/install") {
                    val err = auth(call) ?: return@post
                    val body = try { json.decodeFromString<InstallSkillRequest>(call.receiveText()) } catch (_: Exception) { call.respondText(json.encodeToString(ErrorResponse("Invalid body")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    val result = withContext(Dispatchers.Default) { dataRepository.installSkillFromGitHub(body.owner, body.repo, body.ref, body.path) }
                    if (result.isSuccess) {
                        call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)); result.getOrNull()?.id?.let { put("id", JsonPrimitive(it)) } }), ContentType.Application.Json)
                    } else {
                        call.respondText(json.encodeToString(ErrorResponse(result.exceptionOrNull()?.message ?: "Install failed")), ContentType.Application.Json, HttpStatusCode.InternalServerError)
                    }
                }

                post("/skill/uninstall/{id}") {
                    val err = auth(call) ?: return@post
                    val id = call.parameters["id"] ?: run { call.respondText(json.encodeToString(ErrorResponse("Missing id")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    withContext(Dispatchers.Default) { dataRepository.uninstallSkill(id) }
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
                }

                post("/skill/activate") {
                    val err = auth(call) ?: return@post
                    val body = try { json.decodeFromString<SettingUpdateRequest>(call.receiveText()) } catch (_: Exception) { call.respondText(json.encodeToString(ErrorResponse("Invalid body")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    if (body.value.isBlank()) {
                        dataRepository.setActiveSkill(null)
                    } else {
                        val skill = dataRepository.getInstalledSkills().find { it.id == body.value }
                        dataRepository.setActiveSkill(skill)
                    }
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
                }

                // ======================= HEARTBEAT =======================
                get("/heartbeat") {
                    val err = auth(call) ?: return@get
                    val cfg = dataRepository.getHeartbeatConfig()
                    call.respondText(json.encodeToString(HeartbeatConfigResponse(
                        enabled = cfg.enabled, intervalMinutes = cfg.intervalMinutes,
                        activeHoursStart = cfg.activeHoursStart, activeHoursEnd = cfg.activeHoursEnd,
                        lastHeartbeatEpochMs = cfg.lastHeartbeatEpochMs, heartbeatInstanceId = cfg.heartbeatInstanceId,
                        prompt = dataRepository.getHeartbeatPrompt(), log = dataRepository.getHeartbeatLog(),
                    )), ContentType.Application.Json)
                }

                post("/heartbeat") {
                    val err = auth(call) ?: return@post
                    val body = try { json.decodeFromString<HeartbeatUpdateRequest>(call.receiveText()) } catch (_: Exception) { call.respondText(json.encodeToString(ErrorResponse("Invalid body")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    body.enabled?.let { dataRepository.setHeartbeatEnabled(it) }
                    body.intervalMinutes?.let { dataRepository.setHeartbeatIntervalMinutes(it) }
                    body.activeHoursStart?.let { s -> dataRepository.setHeartbeatActiveHours(s, body.activeHoursEnd ?: dataRepository.getHeartbeatConfig().activeHoursEnd) }
                    body.activeHoursEnd?.let { e -> dataRepository.setHeartbeatActiveHours(body.activeHoursStart ?: dataRepository.getHeartbeatConfig().activeHoursStart, e) }
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
                }

                // ======================= EMAIL =======================
                get("/email") {
                    val err = auth(call) ?: return@get
                    call.respondText(json.encodeToString(dataRepository.getEmailAccounts().map {
                        com.kai.custom.data.EmailAccountDebugView(id = it.id, email = it.email, displayName = it.displayName)
                    }), ContentType.Application.Json)
                }

                post("/email/add") {
                    val err = auth(call) ?: return@post
                    val body = try { json.decodeFromString<EmailAccountRequest>(call.receiveText()) } catch (_: Exception) { call.respondText(json.encodeToString(ErrorResponse("Invalid body")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    appSettings.setEmailPassword(body.id, body.password)
                    val accounts = dataRepository.getEmailAccounts().toMutableList()
                    accounts.add(com.kai.custom.data.EmailAccount(id = body.id, email = body.email, displayName = body.displayName, imapHost = body.imapHost, imapPort = body.imapPort, smtpHost = body.smtpHost, smtpPort = body.smtpPort, username = body.username, useStartTls = body.useStartTls))
                    appSettings.setEmailAccountsJson(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.kai.custom.data.EmailAccount.serializer()), accounts))
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
                }

                delete("/email/{accountId}") {
                    val err = auth(call) ?: return@delete
                    val accountId = call.parameters["accountId"] ?: run { call.respondText(json.encodeToString(ErrorResponse("Missing accountId")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@delete }
                    withContext(Dispatchers.Default) { dataRepository.removeEmailAccount(accountId) }
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
                }

                post("/email/poll/{accountId}") {
                    val err = auth(call) ?: return@post
                    val accountId = call.parameters["accountId"] ?: run { call.respondText(json.encodeToString(ErrorResponse("Missing accountId")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    withContext(Dispatchers.Default) { dataRepository.pollEmailAccount(accountId) }
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
                }

                // ======================= SMS =======================
                get("/sms/drafts") {
                    val err = auth(call) ?: return@get
                    val drafts = dataRepository.smsDrafts.value.map {
                        SmsDraftSummary(id = it.id, address = it.address, body = it.body, status = it.status.name, createdAtEpochMs = it.createdAtEpochMs)
                    }
                    call.respondText(json.encodeToString(drafts), ContentType.Application.Json)
                }

                post("/sms/send/{draftId}") {
                    val err = auth(call) ?: return@post
                    val draftId = call.parameters["draftId"] ?: run { call.respondText(json.encodeToString(ErrorResponse("Missing draftId")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    val result = withContext(Dispatchers.Default) { dataRepository.sendSmsDraft(draftId) }
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(result)) }), ContentType.Application.Json)
                }

                post("/sms/discard/{draftId}") {
                    val err = auth(call) ?: return@post
                    val draftId = call.parameters["draftId"] ?: run { call.respondText(json.encodeToString(ErrorResponse("Missing draftId")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    withContext(Dispatchers.Default) { dataRepository.discardSmsDraft(draftId) }
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
                }

                // ======================= LOCAL INFERENCE =======================
                get("/local/models") {
                    val err = auth(call) ?: return@get
                    call.respondText(json.encodeToString(dataRepository.getLocalAvailableModels().map {
                        LocalModelSummary(id = it.id, isDownloaded = dataRepository.getLocalDownloadedModels().any { d -> d.id == it.id }, contextTokens = dataRepository.getModelContextTokens(it.id))
                    }), ContentType.Application.Json)
                }

                post("/local/download") {
                    val err = auth(call) ?: return@post
                    val body = try { json.decodeFromString<SettingUpdateRequest>(call.receiveText()) } catch (_: Exception) { call.respondText(json.encodeToString(ErrorResponse("Invalid body")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    val model = dataRepository.getLocalAvailableModels().find { it.id == body.value }
                    if (model == null) { call.respondText(json.encodeToString(ErrorResponse("Model not found")), ContentType.Application.Json, HttpStatusCode.NotFound); return@post }
                    dataRepository.startLocalModelDownload(model)
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)); put("model_id", JsonPrimitive(body.value)) }), ContentType.Application.Json)
                }

                post("/local/cancel") {
                    val err = auth(call) ?: return@post
                    dataRepository.cancelLocalModelDownload()
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
                }

                delete("/local/{modelId}") {
                    val err = auth(call) ?: return@delete
                    val modelId = call.parameters["modelId"] ?: run { call.respondText(json.encodeToString(ErrorResponse("Missing modelId")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@delete }
                    withContext(Dispatchers.Default) { dataRepository.deleteLocalModel(modelId) }
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
                }

                // ======================= WAKE WORD =======================
                get("/wake-word") {
                    val err = auth(call) ?: return@get
                    call.respondText(json.encodeToString(WakeWordSettings(
                        enabled = dataRepository.isWakeWordEnabled(), phrase = dataRepository.getWakeWordPhrase(),
                        mode = dataRepository.getWakeWordMode(), template = dataRepository.getWakeWordTemplate(),
                    )), ContentType.Application.Json)
                }

                post("/wake-word") {
                    val err = auth(call) ?: return@post
                    val raw = try { call.receiveText() } catch (_: Exception) { "" }
                    val body = try { json.decodeFromString<SettingUpdateRequest>(raw) } catch (_: Exception) { null }
                    if (body != null) {
                        dataRepository.setWakeWordEnabled(body.value.toBoolean())
                    }
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
                }

                // ======================= TELEGRAM =======================
                get("/telegram") {
                    val err = auth(call) ?: return@get
                    call.respondText(json.encodeToString(TelegramStatusResponse(
                        enabled = dataRepository.isTelegramEnabled(), botTokenPresent = dataRepository.getTelegramBotToken().isNotBlank(),
                        authorizedChatIds = dataRepository.getTelegramAuthorizedChatIds().toList(),
                        pendingCount = dataRepository.getPendingTelegramCount(),
                    )), ContentType.Application.Json)
                }

                // ======================= WHATSAPP =======================
                get("/whatsapp") {
                    val err = auth(call) ?: return@get
                    call.respondText(json.encodeToString(com.kai.custom.data.WhatsAppStatusResponse(
                        enabled = appSettings.isWhatsAppEnabled(),
                        readOnly = appSettings.isWhatsAppReadOnly(),
                        installed = appSettings.isWhatsAppInstalled(),
                        authenticated = appSettings.isWhatsAppAuthenticated(),
                        qrCode = appSettings.getWhatsAppQrCode(),
                        pendingCount = com.kai.custom.data.WhatsAppStore(appSettings).getPending().size,
                    )), ContentType.Application.Json)
                }

                post("/whatsapp/install") {
                    val err = auth(call) ?: return@post
                    try {
                        val ok = withContext(Dispatchers.Default) {
                            whatsAppLifecycleManager.ensureInstalled()
                        }
                        if (ok) {
                            call.respondText(json.encodeToString(mapOf("success" to true)), ContentType.Application.Json)
                        } else {
                            call.respondText(json.encodeToString(ErrorResponse("Install failed")), ContentType.Application.Json, HttpStatusCode.InternalServerError)
                        }
                    } catch (e: Exception) {
                        call.respondText(json.encodeToString(ErrorResponse(e.message ?: "Install error")), ContentType.Application.Json, HttpStatusCode.InternalServerError)
                    }
                }

                post("/whatsapp/refresh-qr") {
                    val err = auth(call) ?: return@post
                    try {
                        withContext(Dispatchers.Default) {
                            whatsAppLifecycleManager.refreshQrCode()
                        }
                        call.respondText(json.encodeToString(mapOf("success" to true, "qrCode" to appSettings.getWhatsAppQrCode())), ContentType.Application.Json)
                    } catch (e: Exception) {
                        call.respondText(json.encodeToString(ErrorResponse(e.message ?: "Refresh error")), ContentType.Application.Json, HttpStatusCode.InternalServerError)
                    }
                }

                post("/whatsapp/restart") {
                    val err = auth(call) ?: return@post
                    try {
                        withContext(Dispatchers.Default) {
                            whatsAppLifecycleManager.restart()
                        }
                        call.respondText(json.encodeToString(mapOf("success" to true)), ContentType.Application.Json)
                    } catch (e: Exception) {
                        call.respondText(json.encodeToString(ErrorResponse(e.message ?: "Restart error")), ContentType.Application.Json, HttpStatusCode.InternalServerError)
                    }
                }

                get("/whatsapp/settings") {
                    val err = auth(call) ?: return@get
                    call.respondText(buildJsonObject {
                        put("browser_name", JsonPrimitive(appSettings.getBaileysBrowserName()))
                        put("browser_version", JsonPrimitive(appSettings.getBaileysBrowserVersion()))
                        put("mark_online_on_connect", JsonPrimitive(appSettings.getBaileysMarkOnline()))
                        put("sync_full_history", JsonPrimitive(appSettings.getBaileysSyncHistory()))
                        put("generate_high_quality_link_preview", JsonPrimitive(appSettings.getBaileysLinkPreviews()))
                    }.toString(), ContentType.Application.Json)
                }

                // ======================= SPLINTERLANDS =======================
                get("/splinterlands") {
                    val err = auth(call) ?: return@get
                    call.respondText(json.encodeToString(SplinterlandsStatusResponse(
                        enabled = appSettings.isSplinterlandsEnabled(), accountPresent = appSettings.getSplinterlandsAccountJson().isNotBlank(),
                    )), ContentType.Application.Json)
                }

                // ======================= CHAT =======================
                post("/chat") {
                    val err = auth(call) ?: return@post
                    val raw = try { call.receiveText() } catch (_: Exception) { "" }
                    val chatRequest = try { json.decodeFromString<ChatRequest>(raw) } catch (_: Exception) {
                        call.respondText(json.encodeToString(ErrorResponse("Invalid JSON body")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post
                    }
                    val result = withContext(Dispatchers.Default) {
                        try { dataRepository.askWithToolsVerbose(chatRequest.message) } catch (e: Exception) { AskWithToolsResult("Error: ${e.message}") }
                    }
                    call.respondText(json.encodeToString(ChatResponse(response = result.response, toolCalls = result.toolCalls)), ContentType.Application.Json)
                }

                post("/chat/silent") {
                    val err = auth(call) ?: return@post
                    val raw = try { call.receiveText() } catch (_: Exception) { "" }
                    val chatRequest = try { json.decodeFromString<ChatRequest>(raw) } catch (_: Exception) {
                        call.respondText(json.encodeToString(ErrorResponse("Invalid JSON body")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post
                    }
                    val result = withContext(Dispatchers.Default) {
                        try { dataRepository.askSilently(chatRequest.message) } catch (e: Exception) { "Error: ${e.message}" }
                    }
                    call.respondText(result, ContentType.Text.Plain)
                }

                // ======================= SANDBOX =======================
                get("/sandbox/status") {
                    val err = auth(call) ?: return@get
                    val s = sandboxController.status.value
                    call.respondText(json.encodeToString(buildJsonObject {
                        put("installed", JsonPrimitive(s.installed))
                        put("ready", JsonPrimitive(s.ready))
                        put("working", JsonPrimitive(s.working))
                        put("progress", s.progress?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("statusText", JsonPrimitive(s.statusText))
                        put("error", JsonPrimitive(s.error))
                        put("sandbox_enabled", JsonPrimitive(dataRepository.isSandboxEnabled()))
                        put("sandbox_distro", JsonPrimitive(dataRepository.getSandboxDistro()))
                        put("sandbox_storage_mount", JsonPrimitive(dataRepository.isSandboxStorageMountEnabled()))
                        put("sandbox_root_enabled", JsonPrimitive(dataRepository.isSandboxRootEnabled()))
                    }), ContentType.Application.Json)
                }

                post("/sandbox/setup") {
                    val err = auth(call) ?: return@post
                    withContext(Dispatchers.Default) { sandboxController.setup() }
                    call.respondText("Sandbox setup started", ContentType.Text.Plain)
                }

                post("/sandbox/install-packages") {
                    val err = auth(call) ?: return@post
                    withContext(Dispatchers.Default) { sandboxController.installPackages() }
                    call.respondText("Sandbox package install started", ContentType.Text.Plain)
                }

                post("/sandbox/reset") {
                    val err = auth(call) ?: return@post
                    withContext(Dispatchers.Default) { sandboxController.reset() }
                    call.respondText("Sandbox reset and rootfs deleted", ContentType.Text.Plain)
                }

                post("/sandbox/exec") {
                    val err = auth(call) ?: return@post
                    val command = call.receiveText().trim()
                    if (command.isBlank()) { call.respondText(json.encodeToString(ErrorResponse("Missing command body")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    val useRoot = call.request.queryParameters["root"]?.toBooleanStrictOrNull() ?: false
                    val timeout = call.request.queryParameters["timeout"]?.toLongOrNull() ?: 60L
                    val output = withContext(Dispatchers.Default) { sandboxController.executeCommand(command, useRoot = useRoot, timeoutSeconds = timeout) }
                    call.respondText(output, ContentType.Text.Plain)
                }

                post("/sandbox/backup") {
                    val err = auth(call) ?: return@post
                    val result = withContext(Dispatchers.Default) { sandboxController.backupSandbox() }
                    result.onSuccess { backup ->
                        call.respondText(json.encodeToString(buildJsonObject {
                            put("success", JsonPrimitive(true))
                            put("path", JsonPrimitive(backup.path))
                        }), ContentType.Application.Json)
                    }.onFailure { e ->
                        call.respondText(json.encodeToString(ErrorResponse("Backup failed: ${e.message}")), ContentType.Application.Json, HttpStatusCode.InternalServerError)
                    }
                }

                post("/sandbox/import") {
                    val err = auth(call) ?: return@post
                    val bytes = try { call.receive<ByteArray>() } catch (_: Exception) {
                        call.respondText(json.encodeToString(ErrorResponse("Missing or invalid body")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                        return@post
                    }
                    if (bytes.isEmpty()) {
                        call.respondText(json.encodeToString(ErrorResponse("Empty body")), ContentType.Application.Json, HttpStatusCode.BadRequest)
                        return@post
                    }
                    val result = withContext(Dispatchers.Default) { sandboxController.importSandbox(bytes) }
                    result.onSuccess {
                        call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
                    }.onFailure { e ->
                        call.respondText(json.encodeToString(ErrorResponse("Import failed: ${e.message}")), ContentType.Application.Json, HttpStatusCode.InternalServerError)
                    }
                }

                post("/alt-memory/install") {
                    val err = auth(call) ?: return@post
                    val ok = withContext(Dispatchers.Default) { sandboxController.installAltMemoryPackage() }
                    if (ok) {
                        call.respondText(json.encodeToString(mapOf("success" to true)), ContentType.Application.Json)
                    } else {
                        call.respondText(json.encodeToString(ErrorResponse("Install failed")), ContentType.Application.Json, HttpStatusCode.InternalServerError)
                    }
                }

                post("/alt-memory/restart") {
                    val err = auth(call) ?: return@post
                    try {
                        withContext(Dispatchers.Default) {
                            sandboxController.stopAltMemory()
                            sandboxController.startAltMemory()
                        }
                        call.respondText(json.encodeToString(mapOf("success" to true)), ContentType.Application.Json)
                    } catch (e: Exception) {
                        call.respondText(json.encodeToString(ErrorResponse(e.message ?: "Restart error")), ContentType.Application.Json, HttpStatusCode.InternalServerError)
                    }
                }

                // ======================= RESET =======================
                post("/reset") {
                    val err = auth(call) ?: return@post
                    dataRepository.clearHistory()
                    call.respondText("Conversation reset", ContentType.Text.Plain)
                }

                // ======================= EXPORT / IMPORT =======================
                get("/export/preview") {
                    val err = auth(call) ?: return@get
                    call.respondText(json.encodeToString(dataRepository.getExportPreview().map { (section, count) -> buildJsonObject { put("section", JsonPrimitive(section.name)); put("count", JsonPrimitive(count)) } }), ContentType.Application.Json)
                }

                post("/export") {
                    val err = auth(call) ?: return@post
                    val all = dataRepository.exportSettingsToJson()
                    call.respondText(all, ContentType.Text.Plain)
                }

                post("/import") {
                    val err = auth(call) ?: return@post
                    val body = try { json.decodeFromString<ImportRequest>(call.receiveText()) } catch (_: Exception) { call.respondText(json.encodeToString(ErrorResponse("Invalid body")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    val sections = if (body.sections.isEmpty()) com.kai.custom.data.ImportSection.entries.toSet() else body.sections.map { com.kai.custom.data.ImportSection.valueOf(it.uppercase()) }.toSet()
                    val count = dataRepository.importSettingsFromJson(body.json, sections, body.replace)
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)); put("imported", JsonPrimitive(count)) }), ContentType.Application.Json)
                }

                // ======================= INTERACTIVE MODE =======================
                post("/interactive") {
                    val err = auth(call) ?: return@post
                    val body = try { json.decodeFromString<SettingUpdateRequest>(call.receiveText()) } catch (_: Exception) { call.respondText(json.encodeToString(ErrorResponse("Invalid body")), ContentType.Application.Json, HttpStatusCode.BadRequest); return@post }
                    dataRepository.setInteractiveMode(body.value.toBoolean())
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)); put("interactive_mode", JsonPrimitive(body.value)) }), ContentType.Application.Json)
                }

                // ======================= REGENERATE =======================
                post("/regenerate") {
                    val err = auth(call) ?: return@post
                    dataRepository.regenerate()
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
                }

                // ======================= SOUL =======================
                post("/soul/user") {
                    val err = auth(call) ?: return@post
                    val text = call.receiveText()
                    dataRepository.setSoulUser(text)
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
                }

                post("/soul/auto") {
                    val err = auth(call) ?: return@post
                    val text = call.receiveText()
                    dataRepository.setSoulAuto(text)
                    call.respondText(json.encodeToString(buildJsonObject { put("success", JsonPrimitive(true)) }), ContentType.Application.Json)
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
