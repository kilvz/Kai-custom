package com.kai.custom.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
data class HealthResponse(
    val status: String,
    val token: String,
)

@Serializable
data class ChatRequest(
    val message: String,
)

@Serializable
data class ChatResponse(
    val response: String,
    val toolCalls: List<ToolCallInfo> = emptyList(),
)

@Serializable
data class ToolCallInfo(
    val name: String,
    val arguments: String,
    val result: String? = null,
)

@Serializable
data class StateResponse(
    val historyCount: Int,
    val memoryCount: Int,
    val toolCount: Int,
    val isDaemonEnabled: Boolean,
    val isFloatingBallEnabled: Boolean,
    val isMemoryEnabled: Boolean,
    val isSchedulingEnabled: Boolean,
    val isHeartbeatEnabled: Boolean,
    val currentServiceId: String,
    val sandboxInstalled: Boolean,
    val sandboxReady: Boolean,
)

@Serializable
data class SettingUpdateRequest(
    val value: String,
)

@Serializable
data class ErrorResponse(
    val error: String,
)

@Serializable
data class MemoryRequest(
    val key: String,
    val content: String,
    val category: String? = null,
)

@Serializable
data class SearchRequest(
    val query: String,
    val limit: Int? = null,
)

@Serializable
data class ToolCallRequest(
    val tool: String = "",
    val arguments: JsonElement = JsonObject(emptyMap()),
)

@Serializable
data class ToolCallResponse(
    val success: Boolean,
    val name: String,
    val result: String = "",
    val error: String? = null,
)

@Serializable
data class AltMemoryStatusResponse(
    val enabled: Boolean,
    val installed: Boolean,
    val connected: Boolean,
    val localMemoryCount: Int,
    val behaviorMemoryCount: Int,
    val migrationComplete: Boolean,
)

// --- Additional models for comprehensive DebugServer ---

@Serializable
data class FullSettingsResponse(
    val soulUser: String = "",
    val soulAuto: String = "",
    val personaName: String = "",
    val activePersonaId: String = "",
    val currentServiceId: String = "",
    val freeFallbackEnabled: Boolean = true,
    val freeMode: String = "FAST",
    val freeServicePrimary: Boolean = false,
    val memoryEnabled: Boolean = true,
    val altMemoryEnabled: Boolean = false,
    val altMemoryInstalled: Boolean = false,
    val schedulingEnabled: Boolean = true,
    val dynamicUiEnabled: Boolean = true,
    val themeMode: String = "System",
    val interactiveMode: Boolean = false,
    val daemonEnabled: Boolean = false,
    val wakeWordEnabled: Boolean = false,
    val wakeWordPhrase: String = "hey kai",
    val wakeWordMode: String = "GENERAL",
    val wakeWordTemplate: String = "",
    val sandboxEnabled: Boolean = true,
    val sandboxStorageMount: Boolean = false,
    val sandboxDistro: String = "alpine",
    val sandboxRootEnabled: Boolean = false,
    val heartbeatEnabled: Boolean = true,
    val heartbeatIntervalMinutes: Int = 30,
    val heartbeatActiveHoursStart: Int = 8,
    val heartbeatActiveHoursEnd: Int = 22,
    val heartbeatPrompt: String = "",
    val emailEnabled: Boolean = true,
    val emailPollIntervalMinutes: Int = 15,
    val smsEnabled: Boolean = false,
    val smsSendEnabled: Boolean = false,
    val smsPollIntervalMinutes: Int = 15,
    val notificationsEnabled: Boolean = false,
    val shizukuEnabled: Boolean = false,
    val rootEnabled: Boolean = false,
    val debugApiEnabled: Boolean = false,
    val debugEndpointEnabled: Boolean = false,
    val telegramEnabled: Boolean = false,
    val telegramBotToken: String = "",
    val sshEnabled: Boolean = true,
    val preferredLanguage: String = "",
    val uiScale: Float = 1.0f,
    val splinterlandsEnabled: Boolean = false,
    val activeSkillId: String? = null,
    val altMemoryMigrationComplete: Boolean = false,
)

@Serializable
data class PersonaListEntry(
    val id: String,
    val name: String,
    val description: String,
    val behaviorStyle: String,
    val languageStyle: String,
    val characterType: String,
    val isBuiltIn: Boolean,
    val isActive: Boolean,
)

@Serializable
data class ConversationSummary(
    val id: String,
    val title: String,
    val type: String,
    val messageCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class McpServerEntry(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean,
    val connected: Boolean,
)

@Serializable
data class ServiceInstanceEntry(
    val instanceId: String,
    val serviceId: String,
)

@Serializable
data class HeartbeatConfigResponse(
    val enabled: Boolean,
    val intervalMinutes: Int,
    val activeHoursStart: Int,
    val activeHoursEnd: Int,
    val lastHeartbeatEpochMs: Long,
    val heartbeatInstanceId: String? = null,
    val prompt: String = "",
    val log: List<HeartbeatLogEntry> = emptyList(),
)

@Serializable
data class SmsDraftSummary(
    val id: String,
    val address: String,
    val body: String,
    val status: String,
    val createdAtEpochMs: Long,
)

@Serializable
data class SkillSummary(
    val id: String,
    val name: String,
    val version: String,
    val isActive: Boolean,
)

@Serializable
data class LocalModelSummary(
    val id: String,
    val isDownloaded: Boolean,
    val contextTokens: Int = 0,
)

@Serializable
data class EmailAccountDebugView(
    val id: String,
    val email: String,
    val displayName: String,
    val unreadCount: Int = 0,
)

@Serializable
data class TelegramStatusResponse(
    val enabled: Boolean,
    val botTokenPresent: Boolean,
    val authorizedChatIds: List<Long> = emptyList(),
    val pendingCount: Int = 0,
    val syncState: String? = null,
)

@Serializable
data class SplinterlandsStatusResponse(
    val enabled: Boolean,
    val accountPresent: Boolean,
)

@Serializable
data class WhatsAppStatusResponse(
    val enabled: Boolean,
    val readOnly: Boolean,
    val installed: Boolean,
    val authenticated: Boolean,
    val qrCode: String,
    val pendingCount: Int,
)

@Serializable
data class WakeWordSettings(
    val enabled: Boolean,
    val phrase: String,
    val mode: String,
    val template: String,
)

@Serializable
data class HeartbeatUpdateRequest(
    val enabled: Boolean? = null,
    val intervalMinutes: Int? = null,
    val activeHoursStart: Int? = null,
    val activeHoursEnd: Int? = null,
)

@Serializable
data class EmailAccountRequest(
    val id: String,
    val email: String,
    val displayName: String = "",
    val imapHost: String,
    val imapPort: Int = 993,
    val smtpHost: String,
    val smtpPort: Int = 587,
    val username: String = "",
    val password: String = "",
    val useStartTls: Boolean = true,
)

@Serializable
data class McpServerAddRequest(
    val name: String,
    val url: String,
)

@Serializable
data class InstallSkillRequest(
    val owner: String,
    val repo: String,
    val ref: String = "main",
    val path: String = "",
)

@Serializable
data class ApiKeyUpdateRequest(
    val instanceId: String,
    val apiKey: String,
)

@Serializable
data class BaseUrlUpdateRequest(
    val instanceId: String,
    val baseUrl: String,
)

@Serializable
data class ModelSelectRequest(
    val instanceId: String,
    val serviceId: String,
    val modelId: String,
)

@Serializable
data class ServiceRemoveRequest(
    val instanceId: String,
)

@Serializable
data class ImportRequest(
    val json: String,
    val sections: List<String> = emptyList(),
    val replace: Boolean = false,
)
