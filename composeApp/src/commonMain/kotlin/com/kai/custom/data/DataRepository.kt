package com.kai.custom.data

import com.kai.custom.data.ToolCallInfo
import com.kai.custom.data.dimension.KGFact
import com.kai.custom.inference.DownloadError
import com.kai.custom.inference.DownloadedModel
import com.kai.custom.inference.EngineState
import com.kai.custom.inference.LocalModel
import com.kai.custom.mcp.McpServerConfig
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.skills.RegistrySkillEntry
import com.kai.custom.skills.SkillManifest
import com.kai.custom.ui.chat.History
import com.kai.custom.ui.settings.SettingsModel
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.StateFlow

interface DataRepository {
    val chatHistory: StateFlow<List<History>>
    val currentConversationId: StateFlow<String?>
    val fallbackStatus: StateFlow<FallbackStatus?>

    // Configured services management
    fun getConfiguredServiceInstances(): List<ServiceInstance>
    fun addConfiguredService(serviceId: String): ServiceInstance
    fun removeConfiguredService(instanceId: String)
    fun reorderConfiguredServices(orderedInstanceIds: List<String>)
    fun getServiceEntries(): List<ServiceEntry>
    fun isFreeFallbackEnabled(): Boolean
    fun setFreeFallbackEnabled(enabled: Boolean)
    fun getFreeMode(): FreeMode
    fun setFreeMode(mode: FreeMode)
    fun isFreeServicePrimary(): Boolean
    fun setFreeServicePrimary(primary: Boolean)

    // Per-instance settings
    fun getInstanceApiKey(instanceId: String): String
    fun updateInstanceApiKey(instanceId: String, apiKey: String)
    fun getInstanceBaseUrl(instanceId: String, service: Service): String
    fun updateInstanceBaseUrl(instanceId: String, baseUrl: String)
    fun getInstanceModels(instanceId: String, service: Service): StateFlow<List<SettingsModel>>
    fun updateInstanceSelectedModel(instanceId: String, service: Service, modelId: String)
    fun clearInstanceModels(instanceId: String, service: Service)
    suspend fun validateConnection(service: Service, instanceId: String)

    suspend fun ask(question: String?, files: List<PlatformFile>, uiSubmission: UiSubmission? = null)

    /** Recent user+assistant exchange pairs formatted as "User: ...\nAssistant: ...". */
    fun getRecentExchanges(pairCount: Int = 3): String
    fun clearHistory()
    fun currentService(): Service
    fun isUsingSharedKey(): Boolean
    fun supportedFileExtensions(): List<String>

    // Conversation management
    val savedConversations: StateFlow<List<Conversation>>
    fun loadConversations()
    fun loadConversation(id: String)
    suspend fun deleteConversation(id: String)
    fun startNewChat()
    fun regenerate()
    fun popLastExchange()
    fun truncateFrom(messageId: String)
    suspend fun forkConversation(messageId: String)
    fun restoreCurrentConversation()

    // Tool management
    fun getToolDefinitions(): List<ToolInfo>
    fun setToolEnabled(toolId: String, enabled: Boolean)

    // MCP servers
    fun getMcpServers(): List<McpServerConfig>
    suspend fun addMcpServer(name: String, url: String, headers: Map<String, String>): McpServerConfig
    fun updateMcpServerHeaders(serverId: String, headers: Map<String, String>)
    fun removeMcpServer(serverId: String)
    fun setMcpServerEnabled(serverId: String, enabled: Boolean)
    suspend fun connectMcpServer(serverId: String): Result<List<ToolInfo>>
    fun getMcpToolsForServer(serverId: String): List<ToolInfo>
    fun isMcpServerConnected(serverId: String): Boolean
    suspend fun connectEnabledMcpServers()

    // Soul (system prompt)

    /** Combined soul: user-edited text + auto behavior summary (for system prompt). */
    fun getSoulText(): String

    /** User-edited portion only (shown in Settings). */
    fun getSoulUser(): String
    fun setSoulUser(text: String)

    /** Auto-generated behavior summary (updated by heartbeat). */
    fun getSoulAuto(): String
    fun setSoulAuto(text: String)

    /** Kept for backward compat — delegates to setSoulUser. */
    fun setSoulText(text: String)

    // Persona
    fun getPersonaName(): String
    fun setPersonaName(name: String)
    fun getAllPersonas(): List<PersonaConfig>
    fun getActivePersona(): PersonaConfig
    fun savePersona(config: PersonaConfig)
    fun deletePersona(id: String)
    suspend fun switchPersona(personaId: String)
    suspend fun getActiveSystemPrompt(variant: SystemPromptVariant = SystemPromptVariant.CHAT_REMOTE, searchQuery: String? = null): String?

    // Memory management
    fun isMemoryEnabled(): Boolean
    fun setMemoryEnabled(enabled: Boolean)
    fun isAltMemoryEnabled(): Boolean
    fun setAltMemoryEnabled(enabled: Boolean)
    fun isAltMemoryInstalled(): Boolean
    fun setAltMemoryInstalled(installed: Boolean)
    fun isWhatsAppInstalled(): Boolean
    fun setWhatsAppInstalled(installed: Boolean)
    fun isWhatsAppEnabled(): Boolean
    fun setWhatsAppEnabled(enabled: Boolean)
    fun isWhatsAppReadOnly(): Boolean
    fun setWhatsAppReadOnly(readOnly: Boolean)
    fun getWhatsAppReplyMode(): String
    fun setWhatsAppReplyMode(mode: String)
    fun getWhatsAppAllowedContacts(): String
    fun setWhatsAppAllowedContacts(contacts: String)
    fun isWhatsAppReadReceipt(): Boolean
    fun setWhatsAppReadReceipt(enabled: Boolean)
    fun isWhatsAppAuthenticated(): Boolean
    fun setWhatsAppAuthenticated(auth: Boolean)
    fun getWhatsAppQrCode(): String
    fun getBaileysBrowserName(): String
    fun setBaileysBrowserName(v: String)
    fun getBaileysBrowserVersion(): String
    fun setBaileysBrowserVersion(v: String)
    fun getBaileysMarkOnline(): Boolean
    fun setBaileysMarkOnline(v: Boolean)
    fun getBaileysSyncHistory(): Boolean
    fun setBaileysSyncHistory(v: Boolean)
    fun getBaileysLinkPreviews(): Boolean
    fun setBaileysLinkPreviews(v: Boolean)
    fun getBaileysConfigJson(): String
    fun getMemories(): List<MemoryEntry>
    fun getSchemaResetMessage(): String?
    suspend fun deleteMemory(key: String)
    suspend fun updateMemoryContent(key: String, content: String)

    // Knowledge graph
    fun queryKgFacts(entity: String? = null, relation: String? = null, limit: Int = 20): List<KGFact>

    // Dimension stats
    fun countDimensionEntities(): Long

    // Scheduling management
    fun isSchedulingEnabled(): Boolean
    fun setSchedulingEnabled(enabled: Boolean)
    fun getScheduledTasks(): List<ScheduledTask>
    suspend fun cancelScheduledTask(id: String)

    // Dynamic UI
    fun isDynamicUiEnabled(): Boolean
    fun setDynamicUiEnabled(enabled: Boolean)

    // Theme mode
    fun getThemeMode(): ThemeMode
    fun setThemeMode(mode: ThemeMode)

    // Interactive mode
    fun setInteractiveMode(enabled: Boolean)
    fun isInteractiveModeActive(): Boolean

    // Daemon mode
    fun isDaemonEnabled(): Boolean
    fun setDaemonEnabled(enabled: Boolean)

    // Floating ball overlay
    fun isFloatingBallEnabled(): Boolean
    fun setFloatingBallEnabled(enabled: Boolean)

    // Wake word detection
    fun isWakeWordEnabled(): Boolean
    fun setWakeWordEnabled(enabled: Boolean)
    fun getWakeWordPhrase(): String
    fun setWakeWordPhrase(phrase: String)
    fun getWakeWordMode(): String
    fun setWakeWordMode(mode: String)
    fun getWakeWordTemplate(): String
    fun setWakeWordTemplate(template: String)
    fun getPreferredLanguage(): String
    fun setPreferredLanguage(lang: String)

    fun getPttTriggerKeyCode(): Int
    fun setPttTriggerKeyCode(keyCode: Int)

    // Linux Sandbox
    fun isSandboxEnabled(): Boolean
    fun setSandboxEnabled(enabled: Boolean)
    fun isSandboxStorageMountEnabled(): Boolean
    fun setSandboxStorageMountEnabled(enabled: Boolean)
    fun getSandboxDistro(): String
    fun setSandboxDistro(distro: String)

    // Heartbeat
    fun getHeartbeatConfig(): HeartbeatConfig
    fun setHeartbeatEnabled(enabled: Boolean)
    fun setHeartbeatIntervalMinutes(minutes: Int)
    fun setHeartbeatActiveHours(start: Int, end: Int)
    fun getHeartbeatPrompt(): String
    fun setHeartbeatPrompt(text: String)
    fun getHeartbeatLog(): List<HeartbeatLogEntry>
    fun getHeartbeatInstanceId(): String?
    fun setHeartbeatInstanceId(instanceId: String?)

    // Email
    fun isEmailEnabled(): Boolean
    fun setEmailEnabled(enabled: Boolean)
    fun getEmailAccounts(): List<EmailAccount>
    suspend fun removeEmailAccount(id: String)
    fun getEmailPollIntervalMinutes(): Int
    fun setEmailPollIntervalMinutes(minutes: Int)
    fun getPendingEmailCount(): Int
    fun getEmailSyncStates(): Map<String, EmailSyncState>
    suspend fun pollEmailAccount(accountId: String)

    // SMS (FOSS-only on Android; other platforms return stub values).
    // Read and send are independent opt-ins with separate runtime permissions.
    fun isSmsEnabled(): Boolean
    fun setSmsEnabled(enabled: Boolean)
    fun getSmsPollIntervalMinutes(): Int
    fun setSmsPollIntervalMinutes(minutes: Int)
    fun getPendingSmsCount(): Int
    fun getSmsSyncState(): SmsSyncState
    fun hasSmsPermission(): Boolean
    suspend fun requestSmsPermission(): Boolean
    suspend fun pollSms()

    fun isSmsSendEnabled(): Boolean
    fun setSmsSendEnabled(enabled: Boolean)
    fun hasSmsSendPermission(): Boolean
    suspend fun requestSmsSendPermission(): Boolean
    val smsDrafts: StateFlow<List<SmsDraft>>
    suspend fun sendSmsDraft(draftId: String): Boolean
    suspend fun discardSmsDraft(draftId: String)

    // Shizuku / ADB commands (Android-only; no-op on other platforms).
    fun isShizukuEnabled(): Boolean
    fun setShizukuEnabled(enabled: Boolean)

    // Root shell (Android-only with su; no-op on other platforms).
    fun isRootEnabled(): Boolean
    fun setRootEnabled(enabled: Boolean)
    fun isSandboxRootEnabled(): Boolean
    fun setSandboxRootEnabled(enabled: Boolean)

    // Debug API server (Android-only debug builds only).
    fun isDebugApiEnabled(): Boolean
    fun setDebugApiEnabled(enabled: Boolean)
    fun isDebugEndpointEnabled(): Boolean
    fun setDebugEndpointEnabled(enabled: Boolean)

    // Notifications (FOSS-only on Android; other platforms return stub values).
    // Per-app filtering is delegated to the system Notification Access "Apps" picker.
    fun isNotificationsEnabled(): Boolean
    fun setNotificationsEnabled(enabled: Boolean)
    fun isNotificationListenerAccessGranted(): Boolean
    fun openNotificationListenerSettings()
    fun getPendingNotificationCount(): Int
    fun getNotificationSyncState(): NotificationSyncState
    suspend fun clearPendingNotifications()

    // UI Scale
    fun getUiScale(): Float
    fun setUiScale(scale: Float)

    // Export/Import
    fun exportSettingsToJson(sections: Set<ImportSection> = ImportSection.entries.toSet()): String
    fun getExportPreview(): Map<ImportSection, String?>
    fun importSettingsFromJson(json: String, sections: Set<ImportSection>, replace: Boolean): Int

    // Dimension export/import
    fun exportDimension(): ByteArray
    fun importDimension(data: ByteArray)

    // Conversation branching — edits a message, stores old conversation,
    // starts a new conversation with the edited message, and triggers AI response.
    // Returns true if sandbox was available and branching succeeded.
    suspend fun editAndBranch(messageId: String, newContent: String): Boolean

    // Background ask with tools (no chat history update, supports tool-calling loop)
    suspend fun askWithTools(prompt: String, instanceId: String? = null): String
    suspend fun askWithToolsVerbose(prompt: String, instanceId: String? = null): AskWithToolsResult

    // Silent ask (no tools, no chat history update)
    suspend fun askSilently(question: String, timeoutMs: Long = 0L): String
    suspend fun askSilentlyWithInstance(instanceId: String, prompt: String, timeoutMs: Long = 0L): String
    suspend fun addAssistantMessage(content: String)

    // Heartbeat notification
    val hasUnreadHeartbeat: StateFlow<Boolean>
    fun clearUnreadHeartbeat()

    /**
     * Pulse that fires when the user taps a heartbeat push notification while the app is
     * not already on the heartbeat conversation. `true` means "load the heartbeat
     * conversation now, then call [consumeOpenHeartbeatRequest]". Collected by
     * `ChatViewModel` in its init block.
     */
    val openHeartbeatRequested: StateFlow<Boolean>
    fun requestOpenHeartbeat()
    fun consumeOpenHeartbeatRequest()

    // On-device inference (LiteRT)
    fun isLocalInferenceAvailable(): Boolean
    fun getLocalEngineState(): StateFlow<EngineState>?
    fun getLocalDownloadedModels(): List<DownloadedModel>
    fun getLocalAvailableModels(): List<LocalModel>
    fun getLocalFreeSpaceBytes(): Long
    fun getTotalDeviceMemoryBytes(): Long
    fun getModelContextTokens(modelId: String): Int
    fun setModelContextTokens(modelId: String, contextTokens: Int)
    fun getModelMaxTokens(modelId: String): Int
    fun setModelMaxTokens(modelId: String, maxTokens: Int)
    fun getModelTemperature(modelId: String): Float
    fun setModelTemperature(modelId: String, temperature: Float)
    fun getLocalStyleInstruction(): String
    fun setLocalStyleInstruction(text: String)
    fun isLocalModelFullPrompt(): Boolean
    fun setLocalModelFullPrompt(enabled: Boolean)
    fun getModelTopK(modelId: String): Int
    fun setModelTopK(modelId: String, topK: Int)
    fun getModelTopP(modelId: String): Float
    fun setModelTopP(modelId: String, topP: Float)
    fun getModelGpuLayers(modelId: String): Int
    fun setModelGpuLayers(modelId: String, gpuLayers: Int)
    fun getImportedModels(): List<com.kai.custom.inference.ImportedModel>
    fun addImportedModel(model: com.kai.custom.inference.ImportedModel)
    fun removeImportedModel(modelId: String)

    /** @deprecated Unsafe ByteArray path — use SAF-based [importSafFile] or [linkGgufExternal] instead.
     *  This method loads the entire file into memory and will OOM on large GGUF models. */
    @Deprecated("Use SAF-based importSafFile or linkGgufExternal. ByteArray path is unsafe for large models.")
    suspend fun importLocalModel(bytes: ByteArray, fileName: String): String
    fun getDefaultCalendarId(): Long
    fun setDefaultCalendarId(calendarId: Long)
    suspend fun releaseLocalEngine()
    fun getLocalDownloadingModelId(): StateFlow<String?>?
    fun getLocalDownloadProgress(): StateFlow<Float?>?
    fun getLocalDownloadError(): StateFlow<DownloadError?>?
    fun startLocalModelDownload(model: LocalModel)
    fun cancelLocalModelDownload()
    suspend fun deleteLocalModel(modelId: String)

    // Skills
    fun getInstalledSkills(): List<SkillManifest>
    fun getActiveSkill(): SkillManifest?
    fun setActiveSkill(skill: SkillManifest?)
    suspend fun installSkillFromGitHub(owner: String, repo: String, ref: String, path: String): Result<SkillManifest>
    suspend fun installSkillFromClawHub(slug: String): Result<SkillManifest>
    suspend fun installSkillFromRegistryEntry(entry: RegistrySkillEntry): Result<SkillManifest>
    suspend fun uninstallSkill(id: String)
    suspend fun browseMarketplaceSkills(): Result<List<RegistrySkillEntry>>

    fun addSystemMessage(content: String)

    // Telegram Bot
    fun isTelegramEnabled(): Boolean
    fun setTelegramEnabled(enabled: Boolean)
    fun getTelegramBotToken(): String
    fun setTelegramBotToken(token: String)
    fun getTelegramAuthorizedChatIds(): Set<Long>
    fun setTelegramAuthorizedChatIds(ids: Set<Long>)
    fun getTelegramSyncState(): TelegramSyncState
    fun getPendingTelegramCount(): Int
    suspend fun pollTelegram()

    fun getSandboxWorkDir(): String
    fun setSandboxWorkDir(uri: String)
}

data class AskWithToolsResult(
    val response: String,
    val toolCalls: List<ToolCallInfo> = emptyList(),
)
