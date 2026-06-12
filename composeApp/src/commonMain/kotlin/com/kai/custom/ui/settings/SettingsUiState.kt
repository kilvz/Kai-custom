package com.kai.custom.ui.settings

import androidx.compose.runtime.Immutable
import com.kai.custom.data.EmailAccount
import com.kai.custom.data.EmailSyncState
import com.kai.custom.data.HeartbeatLogEntry
import com.kai.custom.data.MemoryEntry
import com.kai.custom.data.PersonaConfig
import com.kai.custom.data.ScheduledTask
import com.kai.custom.data.Service
import com.kai.custom.data.ServiceEntry
import com.kai.custom.data.SmsSyncState
import com.kai.custom.data.ThemeMode
import com.kai.custom.inference.DownloadError
import com.kai.custom.inference.LocalModel
import com.kai.custom.network.dtos.SponsorsResponseDto
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.skills.RegistrySkillEntry
import com.kai.custom.skills.SkillManifest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.jetbrains.compose.resources.StringResource

@Immutable
data class ConfiguredServiceEntry(
    val instanceId: String,
    val service: Service,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Unknown,
    val apiKey: String = "",
    val baseUrl: String = "",
    val selectedModel: SettingsModel? = null,
    val models: ImmutableList<SettingsModel> = persistentListOf(),
)

enum class ConnectionStatus {
    Unknown,
    Checking,
    Connected,
    ErrorInvalidKey,
    ErrorQuotaExhausted,
    ErrorRateLimited,
    ErrorConnectionFailed,
    Error,
}

enum class SettingsTab {
    General,
    Agent,
    Services,
    SSH,
    Tools,
    Sandbox,
    Integrations,
    Plugins,
}

@Immutable
data class SettingsUiState(
    val currentTab: SettingsTab = SettingsTab.Services,
    val configuredServices: ImmutableList<ConfiguredServiceEntry> = persistentListOf(),
    val expandedServiceId: String? = null,
    val availableServicesToAdd: ImmutableList<Service> = persistentListOf(),
    val tools: ImmutableList<ToolInfo> = persistentListOf(),
    val soulText: String = "",
    val soulAuto: String = "",
    val personaName: String = "Kai",
    val personas: ImmutableList<PersonaConfig> = persistentListOf(),
    val activePersonaId: String = "kai",
    val isDynamicUiEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.System,
    val isMemoryEnabled: Boolean = true,
    val isAltMemoryEnabled: Boolean = false,
    val altMemoryInstalled: Boolean = false,
    val altMemoryConnected: Boolean = false,
    val altMemoryBackend: String? = null,
    val altMemoryEmbedder: String? = null,
    val sandboxReady: Boolean = false,
    val memories: ImmutableList<MemoryEntry> = persistentListOf(),
    val isSchedulingEnabled: Boolean = true,
    val scheduledTasks: ImmutableList<ScheduledTask> = persistentListOf(),
    val isDaemonEnabled: Boolean = false,
    val showDaemonToggle: Boolean = false,
    val isFloatingBallEnabled: Boolean = false,
    val isHeartbeatEnabled: Boolean = true,
    val heartbeatIntervalMinutes: Int = 30,
    val heartbeatActiveHoursStart: Int = 8,
    val heartbeatActiveHoursEnd: Int = 22,
    val heartbeatPrompt: String = "",
    val heartbeatLog: ImmutableList<HeartbeatLogEntry> = persistentListOf(),
    val heartbeatServiceEntries: ImmutableList<ServiceEntry> = persistentListOf(),
    val heartbeatSelectedInstanceId: String? = null,
    val isRefreshingHeartbeat: Boolean = false,
    val isEmailEnabled: Boolean = true,
    val showEmailToggle: Boolean = false,
    val emailAccounts: ImmutableList<EmailAccount> = persistentListOf(),
    val emailPollIntervalMinutes: Int = 15,
    val emailPendingCount: Int = 0,
    val emailSyncStates: ImmutableMap<String, EmailSyncState> = persistentMapOf(),
    val refreshingEmailAccountIds: ImmutableSet<String> = persistentSetOf(),
    val showSmsSection: Boolean = false,
    val isSmsEnabled: Boolean = false,
    val smsPermissionGranted: Boolean = false,
    val smsPollIntervalMinutes: Int = 15,
    val smsPendingCount: Int = 0,
    val smsSyncState: SmsSyncState = SmsSyncState(),
    val isRefreshingSms: Boolean = false,
    val isSmsSendEnabled: Boolean = false,
    val smsSendPermissionGranted: Boolean = false,
    val showNotificationsSection: Boolean = false,
    val isNotificationsEnabled: Boolean = false,
    val notificationListenerAccessGranted: Boolean = false,
    val notificationListenerBound: Boolean = false,
    val notificationPendingCount: Int = 0,
    val showShizukuSection: Boolean = false,
    val isShizukuEnabled: Boolean = false,
    val shizukuPermissionGranted: Boolean = false,
    val showRootSection: Boolean = false,
    val isRootEnabled: Boolean = false,
    val rootAvailable: Boolean = false,
    val rootErrorMessage: String? = null,
    val showDebugApiSection: Boolean = false,
    val isDebugApiEnabled: Boolean = false,
    val debugApiRunning: Boolean = false,
    val debugApiTransitioning: Boolean = false,
    val isDebugEndpointEnabled: Boolean = false,
    val isFreeFallbackEnabled: Boolean = true,
    val isWakeWordEnabled: Boolean = false,
    val wakeWordPhrase: String = "hey kai",
    val wakeWordMode: String = "GENERAL",
    val wakeWordEnrolled: Boolean = false,
    val isEnrolling: Boolean = false,
    val wakeWordEnrollmentMessage: String = "",
    val preferredLanguage: String = "en",
    val pttTriggerKeyCode: Int = 0,
    val uiScale: Float = 1.0f,
    val showUiScale: Boolean = false,
    val mcpServers: ImmutableList<McpServerUiState> = persistentListOf(),
    val showAddMcpServerDialog: Boolean = false,
    val localAvailableModels: ImmutableList<LocalModel> = persistentListOf(),
    val totalDeviceMemoryBytes: Long = Long.MAX_VALUE,
    val localFreeSpaceBytes: Long = 0L,
    val localDownloadingModelId: String? = null,
    val localDownloadProgress: Float? = null,
    val localDownloadError: DownloadError? = null,
    val modelContextTokens: ImmutableMap<String, Int> = persistentMapOf(),
    val modelMaxTokens: ImmutableMap<String, Int> = persistentMapOf(),
    val modelTemperature: ImmutableMap<String, Float> = persistentMapOf(),
    val localStyleInstruction: String = "",
    val localModelFullPrompt: Boolean = false,
    val modelTopK: ImmutableMap<String, Int> = persistentMapOf(),
    val modelTopP: ImmutableMap<String, Float> = persistentMapOf(),
    val modelGpuLayers: ImmutableMap<String, Int> = persistentMapOf(),
    val currentSponsors: ImmutableList<SponsorsResponseDto.Sponsor> = persistentListOf(),
    val pastSponsors: ImmutableList<SponsorsResponseDto.Sponsor> = persistentListOf(),
    val pendingDeletion: PendingDeletion? = null,
    val installedSkills: ImmutableList<SkillManifest> = persistentListOf(),
    val activeSkill: SkillManifest? = null,
    val marketplaceSkills: ImmutableList<RegistrySkillEntry> = persistentListOf(),
    val isBrowsingMarketplace: Boolean = false,
    val schemaResetMessage: String? = null,
)

@Immutable
data class McpServerUiState(
    val id: String,
    val name: String,
    val url: String,
    val isEnabled: Boolean,
    val connectionStatus: McpConnectionStatus,
    val tools: ImmutableList<ToolInfo>,
    val apiKey: String = "",
)

enum class McpConnectionStatus {
    Unknown,
    Connecting,
    Connected,
    Error,
}

sealed interface PendingDeletion {
    data class Memory(val key: String) : PendingDeletion
    data class Task(val id: String) : PendingDeletion
    data class EmailAccount(val id: String) : PendingDeletion
    data class Service(val instanceId: String) : PendingDeletion
    data class McpServer(val serverId: String) : PendingDeletion
    data class Skill(val id: String) : PendingDeletion
}

sealed interface ImportResult {
    data object Success : ImportResult
    data class PartialSuccess(val errorCount: Int) : ImportResult
    data object Failure : ImportResult
}

@Immutable
data class SettingsModel(
    val id: String,
    val subtitle: String,
    val description: String? = null,
    val descriptionRes: StringResource? = null,
    val isSelected: Boolean = false,
    /** Human-readable name to display in place of [id], when the provider exposes one. */
    val displayName: String? = null,
    /** Max context window in tokens, from the API or the curated catalog. */
    val contextWindow: Long? = null,
    /** Release date as "YYYY-MM" or "YYYY-MM-DD", from the API or the curated catalog. */
    val releaseDate: String? = null,
    /** Parameter count, pre-formatted for display (e.g. "70B", "8B", "3.3B"). */
    val parameterCount: String? = null,
    /** LMArena Elo score, or null when unknown. */
    val arenaScore: Int? = null,
)
