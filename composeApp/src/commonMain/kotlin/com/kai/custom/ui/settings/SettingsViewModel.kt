package com.kai.custom.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kai.custom.DaemonController
import com.kai.custom.DebugApiController
import com.kai.custom.Platform
import com.kai.custom.PttTriggerManager
import com.kai.custom.SandboxController
import com.kai.custom.currentPlatform
import com.kai.custom.data.BehaviorStyle
import com.kai.custom.data.CharacterType
import com.kai.custom.data.DataRepository
import com.kai.custom.data.ImportSection
import com.kai.custom.data.LanguageStyle
import com.kai.custom.data.PersonaConfig
import com.kai.custom.data.RenderMode
import com.kai.custom.data.Service
import com.kai.custom.data.TaskScheduler
import com.kai.custom.data.ThemeMode
import com.kai.custom.data.supportsAgenticFlows
import com.kai.custom.getBackgroundDispatcher
import com.kai.custom.getToolPermissionMap
import com.kai.custom.httpClient
import com.kai.custom.inference.LocalModel
import com.kai.custom.isEmailSupported
import com.kai.custom.isMockLocationConfigured
import com.kai.custom.isNotificationsSupported
import com.kai.custom.isRootAvailable
import com.kai.custom.isRootSupported
import com.kai.custom.isShizukuPermissionGranted
import com.kai.custom.isShizukuSupported
import com.kai.custom.isSmsSupported
import com.kai.custom.mcp.McpServerManager
import com.kai.custom.mcp.PopularMcpServer
import com.kai.custom.network.AnthropicInsufficientCreditsException
import com.kai.custom.network.AnthropicInvalidApiKeyException
import com.kai.custom.network.AnthropicOverloadedException
import com.kai.custom.network.AnthropicRateLimitExceededException
import com.kai.custom.network.GeminiInvalidApiKeyException
import com.kai.custom.network.GeminiRateLimitExceededException
import com.kai.custom.network.OpenAICompatibleConnectionException
import com.kai.custom.network.OpenAICompatibleInvalidApiKeyException
import com.kai.custom.network.OpenAICompatibleQuotaExhaustedException
import com.kai.custom.network.OpenAICompatibleRateLimitExceededException
import com.kai.custom.network.dtos.SponsorsResponseDto
import com.kai.custom.openMockLocationSettings
import com.kai.custom.openTtsSettings
import com.kai.custom.requestShizukuPermission
import com.kai.custom.skills.RegistrySkillEntry
import com.kai.custom.skills.SkillManifest
import com.kai.custom.skills.parseGitHubSkillUrl
import com.kai.custom.tools.NotificationPermissionController
import com.kai.custom.tools.ToolPermissionBridge
import com.kai.custom.wakeword.WakeWordController
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SettingsViewModel(
    private val dataRepository: DataRepository,
    private val daemonController: DaemonController,
    private val debugApiController: DebugApiController,
    private val notificationPermissionController: NotificationPermissionController,
    private val taskScheduler: TaskScheduler,
    private val wakeWordController: WakeWordController,
    private val sandboxController: SandboxController,
    private val mcpServerManager: McpServerManager,
    private val toolPermissionBridge: ToolPermissionBridge,
    private val backgroundDispatcher: CoroutineContext = getBackgroundDispatcher(),
) : ViewModel() {

    private var connectionCheckJobs: MutableMap<String, Job> = mutableMapOf()
    private var hasCheckedInitialConnection = false
    private var pendingDeleteJob: Job? = null

    private fun buildFullState(): SettingsUiState = SettingsUiState(
        configuredServices = buildConfiguredServiceEntries().toImmutableList(),
        availableServicesToAdd = computeAvailableServices().toImmutableList(),
        tools = dataRepository.getToolDefinitions().toImmutableList(),
        soulText = dataRepository.getSoulUser(),
        soulAuto = dataRepository.getSoulAuto(),
        personaName = dataRepository.getPersonaName(),
        personas = dataRepository.getAllPersonas().toImmutableList(),
        activePersonaId = dataRepository.getActivePersona().id,
        isDynamicUiEnabled = dataRepository.isDynamicUiEnabled(),
        themeMode = dataRepository.getThemeMode(),
        isMemoryEnabled = dataRepository.isMemoryEnabled(),
        isAltMemoryEnabled = dataRepository.isAltMemoryEnabled(),
        altMemoryInstalled = dataRepository.isAltMemoryInstalled(),
        altMemoryConnected = mcpServerManager.isConnected("alt_memory"),
        memories = dataRepository.getMemories().filter { !it.protected }.toImmutableList(),
        isSchedulingEnabled = dataRepository.isSchedulingEnabled(),
        scheduledTasks = dataRepository.getScheduledTasks().toImmutableList(),
        isDaemonEnabled = dataRepository.isDaemonEnabled(),
        showDaemonToggle = currentPlatform is Platform.Mobile.Android,
        isFloatingBallEnabled = dataRepository.isFloatingBallEnabled(),
        isHeartbeatEnabled = dataRepository.getHeartbeatConfig().enabled,
        heartbeatIntervalMinutes = dataRepository.getHeartbeatConfig().intervalMinutes,
        heartbeatActiveHoursStart = dataRepository.getHeartbeatConfig().activeHoursStart,
        heartbeatActiveHoursEnd = dataRepository.getHeartbeatConfig().activeHoursEnd,
        heartbeatPrompt = dataRepository.getHeartbeatPrompt(),
        heartbeatLog = dataRepository.getHeartbeatLog().toImmutableList(),
        heartbeatServiceEntries = dataRepository.getServiceEntries()
            .filter { supportsAgenticFlows(it.serviceId, it.modelId) }
            .toImmutableList(),
        heartbeatSelectedInstanceId = dataRepository.getHeartbeatInstanceId()?.takeIf { id ->
            dataRepository.getServiceEntries().any { it.instanceId == id }
        }.also { validId ->
            val savedId = dataRepository.getHeartbeatInstanceId()
            if (savedId != null && validId == null) dataRepository.setHeartbeatInstanceId(null)
        },
        isEmailEnabled = dataRepository.isEmailEnabled(),
        showEmailToggle = isEmailSupported,
        emailAccounts = dataRepository.getEmailAccounts().toImmutableList(),
        emailPollIntervalMinutes = dataRepository.getEmailPollIntervalMinutes(),
        emailPendingCount = dataRepository.getPendingEmailCount(),
        emailSyncStates = dataRepository.getEmailSyncStates().toImmutableMap(),
        showSmsSection = isSmsSupported,
        isSmsEnabled = dataRepository.isSmsEnabled(),
        smsPermissionGranted = dataRepository.hasSmsPermission(),
        smsPollIntervalMinutes = dataRepository.getSmsPollIntervalMinutes(),
        smsPendingCount = dataRepository.getPendingSmsCount(),
        smsSyncState = dataRepository.getSmsSyncState(),
        isSmsSendEnabled = dataRepository.isSmsSendEnabled(),
        smsSendPermissionGranted = dataRepository.hasSmsSendPermission(),
        showNotificationsSection = isNotificationsSupported,
        isNotificationsEnabled = dataRepository.isNotificationsEnabled(),
        notificationListenerAccessGranted = dataRepository.isNotificationListenerAccessGranted(),
        notificationListenerBound = dataRepository.getNotificationSyncState().listenerBound,
        notificationPendingCount = dataRepository.getPendingNotificationCount(),
        showShizukuSection = isShizukuSupported,
        isShizukuEnabled = dataRepository.isShizukuEnabled(),
        shizukuPermissionGranted = isShizukuPermissionGranted(),
        showRootSection = isRootSupported,
        isRootEnabled = dataRepository.isRootEnabled(),
        rootAvailable = isRootAvailable(),
        showDebugApiSection = currentPlatform is Platform.Mobile.Android,
        isDebugApiEnabled = dataRepository.isDebugApiEnabled(),
        debugApiRunning = debugApiController.isRunning,
        debugApiTransitioning = debugApiController.isTransitioning,
        isDebugEndpointEnabled = dataRepository.isDebugEndpointEnabled(),
        isFreeFallbackEnabled = dataRepository.isFreeFallbackEnabled(),
        isWakeWordEnabled = dataRepository.isWakeWordEnabled(),
        wakeWordPhrase = dataRepository.getWakeWordPhrase(),
        wakeWordMode = dataRepository.getWakeWordMode(),
        wakeWordEnrolled = dataRepository.getWakeWordTemplate().isNotBlank(),
        isEnrolling = false,
        preferredLanguage = dataRepository.getPreferredLanguage(),
        pttTriggerKeyCode = dataRepository.getPttTriggerKeyCode(),
        uiScale = dataRepository.getUiScale(),
        showUiScale = currentPlatform is Platform.Desktop,
        mcpServers = buildMcpServerEntries().toImmutableList(),
        localAvailableModels = dataRepository.getLocalAvailableModels().toImmutableList(),
        totalDeviceMemoryBytes = dataRepository.getTotalDeviceMemoryBytes(),
        localFreeSpaceBytes = dataRepository.getLocalFreeSpaceBytes(),
        localDownloadingModelId = dataRepository.getLocalDownloadingModelId()?.value,
        localDownloadProgress = dataRepository.getLocalDownloadProgress()?.value,
        modelContextTokens = buildModelContextTokensMap(),
        modelMaxTokens = buildModelMaxTokensMap(),
        modelTemperature = buildModelTemperatureMap(),
        localStyleInstruction = dataRepository.getLocalStyleInstruction(),
        localModelFullPrompt = dataRepository.isLocalModelFullPrompt(),
        modelTopK = buildModelTopKMap(),
        modelTopP = buildModelTopPMap(),
        modelGpuLayers = buildModelGpuLayersMap(),
        installedSkills = dataRepository.getInstalledSkills().toImmutableList(),
        activeSkill = dataRepository.getActiveSkill(),
        schemaResetMessage = dataRepository.getSchemaResetMessage(),
    )

    // Bound once so downstream Compose skipping works — a new SettingsActions
    // instance on every state emission would defeat it.
    val actions: SettingsActions = SettingsActions(
        onSelectTab = ::onSelectTab,
        onAddService = ::onAddService,
        onRemoveService = ::onRemoveService,
        onReorderServices = ::onReorderServices,
        onExpandService = ::onExpandService,
        onChangeApiKey = ::onChangeApiKey,
        onChangeBaseUrl = ::onChangeBaseUrl,
        onSelectModel = ::onSelectModel,
        onToggleTool = ::onToggleTool,
        onSaveSoul = ::onSaveSoul,
        onChangePersonaName = ::onChangePersonaName,
        onSwitchPersona = ::onSwitchPersona,
        onSavePersona = ::onSavePersona,
        onDeletePersona = ::onDeletePersona,
        onCreatePersona = ::onCreatePersona,
        onToggleDynamicUi = ::onToggleDynamicUi,
        onChangeThemeMode = ::onChangeThemeMode,
        onToggleMemory = ::onToggleMemory,
        onToggleAltMemory = ::onToggleAltMemory,
        onDeleteMemory = ::onDeleteMemory,
        onUpdateMemory = ::onUpdateMemory,
        onToggleScheduling = ::onToggleScheduling,
        onCancelTask = ::onCancelTask,
        onToggleDaemon = ::onToggleDaemon,
        onToggleFloatingBall = ::onToggleFloatingBall,
        onToggleHeartbeat = ::onToggleHeartbeat,
        onChangeHeartbeatInterval = ::onChangeHeartbeatInterval,
        onChangeHeartbeatActiveHours = ::onChangeHeartbeatActiveHours,
        onSaveHeartbeatPrompt = ::onSaveHeartbeatPrompt,
        onChangeHeartbeatService = ::onChangeHeartbeatService,
        onRefreshHeartbeat = ::onRefreshHeartbeat,
        onToggleEmail = ::onToggleEmail,
        onRemoveEmailAccount = ::onRemoveEmailAccount,
        onChangeEmailPollInterval = ::onChangeEmailPollInterval,
        onRefreshEmailAccount = ::onRefreshEmailAccount,
        onToggleSms = ::onToggleSms,
        onChangeSmsPollInterval = ::onChangeSmsPollInterval,
        onRefreshSms = ::onRefreshSms,
        onToggleSmsSend = ::onToggleSmsSend,
        onToggleNotifications = ::onToggleNotifications,
        onOpenNotificationListenerSettings = ::onOpenNotificationListenerSettings,
        onOpenTtsSettings = ::onOpenTtsSettings,
        onClearPendingNotifications = ::onClearPendingNotifications,
        onToggleShizuku = ::onToggleShizuku,
        onOpenShizukuPermission = ::onOpenShizukuPermission,
        onToggleRoot = ::onToggleRoot,
        onToggleDebugApi = ::onToggleDebugApi,
        onToggleDebugEndpoint = ::onToggleDebugEndpoint,
        onToggleFreeFallback = ::onToggleFreeFallback,
        onToggleWakeWord = ::onToggleWakeWord,
        onChangeWakeWordPhrase = ::onChangeWakeWordPhrase,
        onChangePreferredLanguage = ::onChangePreferredLanguage,
        onChangeWakeWordMode = ::onChangeWakeWordMode,
        onEnrollWakeWord = ::onEnrollWakeWord,
        onCapturePttTrigger = ::onCapturePttTrigger,
        onClearPttTrigger = ::onClearPttTrigger,
        onChangeUiScale = ::onChangeUiScale,
        onAddMcpServer = ::onAddMcpServer,
        onRemoveMcpServer = ::onRemoveMcpServer,
        onToggleMcpServer = ::onToggleMcpServer,
        onRefreshMcpServer = ::onRefreshMcpServer,
        onShowAddMcpServerDialog = ::onShowAddMcpServerDialog,
        onAddPopularMcpServer = ::onAddPopularMcpServer,
        onDownloadLocalModel = ::onDownloadLocalModel,
        onCancelLocalModelDownload = ::onCancelLocalModelDownload,
        onDeleteLocalModel = ::onDeleteLocalModel,
        onChangeModelContextTokens = ::onChangeModelContextTokens,
        onChangeModelMaxTokens = ::onChangeModelMaxTokens,
        onChangeModelTemperature = ::onChangeModelTemperature,
        onChangeLocalStyleInstruction = ::onChangeLocalStyleInstruction,
        onChangeLocalModelFullPrompt = ::onChangeLocalModelFullPrompt,
        onChangeModelTopK = ::onChangeModelTopK,
        onChangeModelTopP = ::onChangeModelTopP,
        onChangeModelGpuLayers = ::onChangeModelGpuLayers,
        onImportLocalModel = ::onImportLocalModel,
        onImportPlatformFileComplete = { model ->
            viewModelScope.launch(backgroundDispatcher) {
                dataRepository.addImportedModel(model)
                refreshServiceList()
                val liteRtInstance = _state.value.configuredServices.find { it.service == com.kai.custom.data.Service.LiteRT }
                if (liteRtInstance != null) {
                    checkConnection(liteRtInstance.instanceId, com.kai.custom.data.Service.LiteRT)
                }
            }
        },
        onImportComplete = ::onImportComplete,
        onExportSettings = ::onExportSettings,
        onPrepareExport = ::onPrepareExport,
        onImportSettings = ::onImportSettings,
        onUndoDelete = ::onUndoDelete,
        onExportDimension = { dataRepository.exportDimension() },
        onImportDimension = { data -> dataRepository.importDimension(data) },
        onInstallGitHub = ::onInstallSkillFromGitHub,
        onInstallBrowsed = ::onInstallSkillFromBrowsed,
        onUninstallSkill = ::onUninstallSkill,
        onBrowseMarketplaceSkills = ::onBrowseMarketplaceSkills,
    )

    private val _state = MutableStateFlow(buildFullState())

    val state = _state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _state.value,
    )

    init {
        // Observe download state from the engine singleton (survives activity recreation)
        val downloadingFlow = dataRepository.getLocalDownloadingModelId() ?: flowOf(null)
        val progressFlow = dataRepository.getLocalDownloadProgress() ?: flowOf(null)
        val errorFlow = dataRepository.getLocalDownloadError() ?: flowOf(null)
        viewModelScope.launch {
            combine(downloadingFlow, progressFlow, errorFlow) { modelId, progress, error ->
                Triple(modelId, progress, error)
            }.collect { (modelId, progress, error) ->
                val wasDownloading = _state.value.localDownloadingModelId != null
                _state.update {
                    it.copy(
                        localDownloadingModelId = modelId,
                        localDownloadProgress = progress,
                        localDownloadError = error,
                    )
                }
                if (modelId == null && wasDownloading) {
                    // Download finished or cancelled — refresh
                    _state.update { it.copy(localFreeSpaceBytes = dataRepository.getLocalFreeSpaceBytes()) }
                    refreshServiceList()
                    _state.value.configuredServices
                        .filter { it.service.isOnDevice }
                        .forEach { checkConnection(it.instanceId, it.service) }
                }
            }
        }
        viewModelScope.launch {
            sandboxController.status.collect { status ->
                val connected = mcpServerManager.isConnected("alt_memory")
                _state.update { it.copy(sandboxReady = status.ready, altMemoryConnected = connected, altMemoryBackend = if (connected) it.altMemoryBackend else null, altMemoryEmbedder = if (connected) it.altMemoryEmbedder else null) }
                if (connected) refreshAltMemoryInfo()
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                if (_state.value.isAltMemoryEnabled && _state.value.altMemoryConnected != mcpServerManager.isConnected("alt_memory")) {
                    val connected = mcpServerManager.isConnected("alt_memory")
                    _state.update { it.copy(altMemoryConnected = connected, altMemoryBackend = if (connected) it.altMemoryBackend else null, altMemoryEmbedder = if (connected) it.altMemoryEmbedder else null) }
                    if (connected) refreshAltMemoryInfo()
                }
            }
        }
    }

    fun onScreenVisible() {
        if (!hasCheckedInitialConnection) {
            hasCheckedInitialConnection = true
            checkAllConnections()
            connectEnabledMcpServers()
            fetchSponsors()
        }
        // Re-read notification listener state every time the screen becomes visible:
        // the user may have toggled access in system settings while we were backgrounded.
        if (isNotificationsSupported) {
            _state.update {
                it.copy(
                    notificationListenerAccessGranted = dataRepository.isNotificationListenerAccessGranted(),
                    notificationListenerBound = dataRepository.getNotificationSyncState().listenerBound,
                    notificationPendingCount = dataRepository.getPendingNotificationCount(),
                )
            }
        }
    }

    private fun fetchSponsors() {
        viewModelScope.launch(backgroundDispatcher) {
            try {
                val client = httpClient {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }
                val response = client.get("https://ghs.vercel.app/v3/sponsors/SimonSchubert")
                if (response.status.isSuccess()) {
                    val dto = response.body<SponsorsResponseDto>()
                    _state.update {
                        it.copy(
                            currentSponsors = dto.sponsors.current.toImmutableList(),
                            pastSponsors = dto.sponsors.past.toImmutableList(),
                        )
                    }
                }
            } catch (_: Exception) {
                // Silently ignore - sponsors are non-critical
            }
        }
    }

    private fun buildConfiguredServiceEntries(): List<ConfiguredServiceEntry> = dataRepository.getConfiguredServiceInstances().map { instance ->
        val service = Service.fromId(instance.serviceId)
        val models = dataRepository.getInstanceModels(instance.instanceId, service).value
        ConfiguredServiceEntry(
            instanceId = instance.instanceId,
            service = service,
            apiKey = dataRepository.getInstanceApiKey(instance.instanceId),
            baseUrl = dataRepository.getInstanceBaseUrl(instance.instanceId, service),
            selectedModel = models.firstOrNull { it.isSelected },
            models = models.toImmutableList(),
        )
    }

    private fun computeAvailableServices(): List<Service> {
        // Allow all non-Free services (multiple instances of same type are allowed)
        // Pin OpenAI-Compatible and LiteRT (Local Model) to the top, then the featured Atlas Cloud
        // provider, then sort the rest alphabetically
        // Hide on-device services on platforms that don't support them
        return Service.all
            .filter { it != Service.Free }
            .filter { !it.isOnDevice || dataRepository.isLocalInferenceAvailable() }
            .sortedWith(
                compareBy<Service> {
                    when {
                        it is Service.OpenAICompatible || it.isOnDevice -> 0
                        it is Service.AtlasCloud -> 1
                        else -> 2
                    }
                }.thenBy { it.displayName },
            )
    }

    private fun refreshServiceList() {
        _state.update { current ->
            val existingStatuses = current.configuredServices.associate { it.instanceId to it.connectionStatus }
            val newEntries = buildConfiguredServiceEntries().map { entry ->
                val preservedStatus = existingStatuses[entry.instanceId]
                if (preservedStatus != null) entry.copy(connectionStatus = preservedStatus) else entry
            }
            current.copy(
                configuredServices = newEntries.toImmutableList(),
                availableServicesToAdd = computeAvailableServices().toImmutableList(),
            )
        }
    }

    private fun onSelectTab(tab: SettingsTab) {
        _state.update { it.copy(currentTab = tab) }
        if (tab == SettingsTab.Agent) refreshMcpServers()
    }

    private fun onAddService(service: Service) {
        val instance = dataRepository.addConfiguredService(service.id)
        refreshServiceList()
        _state.update { it.copy(expandedServiceId = instance.instanceId) }
        checkConnection(instance.instanceId, service)
    }

    private fun onRemoveService(instanceId: String) {
        commitPendingDeletion()
        _state.update {
            it.copy(
                expandedServiceId = if (it.expandedServiceId == instanceId) null else it.expandedServiceId,
                pendingDeletion = PendingDeletion.Service(instanceId),
            )
        }
        pendingDeleteJob = viewModelScope.launch(backgroundDispatcher) {
            delay(4.seconds)
            executeDeletion(PendingDeletion.Service(instanceId))
        }
    }

    private fun onReorderServices(orderedIds: List<String>) {
        dataRepository.reorderConfiguredServices(orderedIds)
        refreshServiceList()
    }

    private fun onExpandService(instanceId: String?) {
        _state.update { it.copy(expandedServiceId = instanceId) }
        if (instanceId != null) {
            refreshInstanceModels(instanceId)
        }
    }

    private fun refreshInstanceModels(instanceId: String) {
        val entry = _state.value.configuredServices.find { it.instanceId == instanceId } ?: return
        val models = dataRepository.getInstanceModels(instanceId, entry.service).value
        _state.update { state ->
            state.copy(
                configuredServices = state.configuredServices.map { e ->
                    if (e.instanceId == instanceId) {
                        e.copy(
                            models = models.toImmutableList(),
                            selectedModel = models.firstOrNull { it.isSelected },
                        )
                    } else {
                        e
                    }
                }.toImmutableList(),
            )
        }
    }

    private fun onChangeApiKey(instanceId: String, apiKey: String) {
        val entry = _state.value.configuredServices.find { it.instanceId == instanceId } ?: return
        dataRepository.updateInstanceApiKey(instanceId, apiKey)
        dataRepository.clearInstanceModels(instanceId, entry.service)
        _state.update { state ->
            state.copy(
                configuredServices = state.configuredServices.map { e ->
                    if (e.instanceId == instanceId) {
                        e.copy(apiKey = apiKey, connectionStatus = ConnectionStatus.Unknown)
                    } else {
                        e
                    }
                }.toImmutableList(),
            )
        }
        checkConnectionDebounced(instanceId, entry.service)
    }

    private fun onChangeBaseUrl(instanceId: String, baseUrl: String) {
        val entry = _state.value.configuredServices.find { it.instanceId == instanceId } ?: return
        dataRepository.updateInstanceBaseUrl(instanceId, baseUrl)
        dataRepository.clearInstanceModels(instanceId, entry.service)
        _state.update { state ->
            state.copy(
                configuredServices = state.configuredServices.map { e ->
                    if (e.instanceId == instanceId) {
                        e.copy(baseUrl = baseUrl, connectionStatus = ConnectionStatus.Unknown)
                    } else {
                        e
                    }
                }.toImmutableList(),
            )
        }
        checkConnectionDebounced(instanceId, entry.service)
    }

    private fun onSelectModel(instanceId: String, modelId: String) {
        val entry = _state.value.configuredServices.find { it.instanceId == instanceId } ?: return
        dataRepository.updateInstanceSelectedModel(instanceId, entry.service, modelId)
        refreshInstanceModels(instanceId)
    }

    private fun onImportComplete() {
        viewModelScope.launch(backgroundDispatcher) {
            refreshServiceList()
        }
    }

    private fun onSaveSoul(text: String) {
        dataRepository.setSoulUser(text)
        _state.update { it.copy(soulText = text) }
    }

    private fun onChangePersonaName(name: String) {
        dataRepository.setPersonaName(name)
        _state.update { it.copy(personaName = name) }
    }

    private fun onSwitchPersona(personaId: String) {
        val config = dataRepository.getAllPersonas().find { it.id == personaId } ?: return
        _state.update {
            it.copy(
                activePersonaId = personaId,
                personaName = config.name,
                soulText = dataRepository.getSoulUser(),
                soulAuto = dataRepository.getSoulAuto(),
            )
        }
        viewModelScope.launch {
            dataRepository.switchPersona(personaId)
        }
    }

    private fun onSavePersona(config: PersonaConfig) {
        dataRepository.savePersona(config)
        _state.update {
            it.copy(
                personas = dataRepository.getAllPersonas().toImmutableList(),
                personaName = if (config.id == it.activePersonaId) config.name else it.personaName,
            )
        }
    }

    private fun onDeletePersona(id: String) {
        dataRepository.deletePersona(id)
        val active = dataRepository.getActivePersona()
        _state.update {
            it.copy(
                personas = dataRepository.getAllPersonas().toImmutableList(),
                activePersonaId = active.id,
                personaName = active.name,
                soulText = dataRepository.getSoulUser(),
                soulAuto = dataRepository.getSoulAuto(),
            )
        }
    }

    private fun onCreatePersona(name: String, behaviorStyle: BehaviorStyle, languageStyle: LanguageStyle, characterType: CharacterType) {
        val id = "custom_${name.lowercase().replace(Regex("[^a-z0-9]"), "_")}"
        val config = PersonaConfig(
            id = id,
            name = name,
            description = "Custom persona",
            behaviorStyle = behaviorStyle,
            languageStyle = languageStyle,
            characterType = characterType,
            renderMode = RenderMode.CHARACTER,
            isBuiltIn = false,
        )
        dataRepository.savePersona(config)
        _state.update {
            it.copy(
                personas = dataRepository.getAllPersonas().toImmutableList(),
                activePersonaId = id,
                personaName = name,
                soulText = dataRepository.getSoulUser(),
                soulAuto = dataRepository.getSoulAuto(),
            )
        }
        viewModelScope.launch {
            dataRepository.switchPersona(id)
        }
    }

    private fun onToggleDynamicUi(enabled: Boolean) {
        dataRepository.setDynamicUiEnabled(enabled)
        _state.update { it.copy(isDynamicUiEnabled = enabled) }
    }

    private fun onChangeThemeMode(mode: ThemeMode) {
        dataRepository.setThemeMode(mode)
        _state.update { it.copy(themeMode = mode) }
    }

    private fun onToggleMemory(enabled: Boolean) {
        dataRepository.setMemoryEnabled(enabled)
        _state.update { it.copy(isMemoryEnabled = enabled) }
    }

    private fun onToggleAltMemory(enabled: Boolean) {
        if (enabled && !dataRepository.isAltMemoryInstalled()) {
            _state.update { it.copy(currentTab = SettingsTab.Sandbox) }
            return
        }
        dataRepository.setAltMemoryEnabled(enabled)
        _state.update { it.copy(isAltMemoryEnabled = enabled, altMemoryConnected = false) }
        viewModelScope.launch {
            if (enabled) {
                sandboxController.startAltMemory()
                _state.update { it.copy(altMemoryConnected = mcpServerManager.isConnected("alt_memory")) }
                refreshAltMemoryInfo()
            } else {
                sandboxController.stopAltMemory()
                _state.update { it.copy(altMemoryConnected = false, altMemoryBackend = null, altMemoryEmbedder = null) }
            }
        }
    }

    private suspend fun refreshAltMemoryInfo() {
        if (!mcpServerManager.isConnected("alt_memory")) return
        try {
            val client = mcpServerManager.getClient("alt_memory") ?: return
            val backendRaw = client.callTool("get_backend", buildJsonObject { })
            val embedderRaw = client.callTool("get_default_embedder", buildJsonObject { })
            val backend = try {
                Json.parseToJsonElement(backendRaw).jsonObject["backend"]?.jsonPrimitive?.content
            } catch (_: Exception) {
                backendRaw
            }
            val embedder = try {
                Json.parseToJsonElement(embedderRaw).jsonObject["default_embedder"]?.jsonPrimitive?.content
            } catch (_: Exception) {
                embedderRaw
            }
            _state.update { it.copy(altMemoryBackend = backend, altMemoryEmbedder = embedder) }
        } catch (_: Exception) { }
    }

    private fun onDeleteMemory(key: String) {
        commitPendingDeletion()
        _state.update { it.copy(pendingDeletion = PendingDeletion.Memory(key)) }
        pendingDeleteJob = viewModelScope.launch(backgroundDispatcher) {
            delay(4.seconds)
            executeDeletion(PendingDeletion.Memory(key))
        }
    }

    private fun onUpdateMemory(key: String, content: String) {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.updateMemoryContent(key, content)
            _state.update { it.copy(memories = dataRepository.getMemories().filter { !it.protected }.toImmutableList()) }
        }
    }

    private fun onToggleScheduling(enabled: Boolean) {
        dataRepository.setSchedulingEnabled(enabled)
        _state.update { it.copy(isSchedulingEnabled = enabled) }
    }

    private fun onCancelTask(id: String) {
        commitPendingDeletion()
        _state.update { it.copy(pendingDeletion = PendingDeletion.Task(id)) }
        pendingDeleteJob = viewModelScope.launch(backgroundDispatcher) {
            delay(4.seconds)
            executeDeletion(PendingDeletion.Task(id))
        }
    }

    private fun onToggleDaemon(enabled: Boolean) {
        dataRepository.setDaemonEnabled(enabled)
        if (enabled) {
            viewModelScope.launch { notificationPermissionController.requestPermission() }
            daemonController.start()
        } else {
            daemonController.stop()
        }
        _state.update { it.copy(isDaemonEnabled = enabled) }
    }

    private fun onToggleFloatingBall(enabled: Boolean) {
        if (enabled) {
            val started = daemonController.startFloatingBall()
            if (!started) return
        } else {
            daemonController.stopFloatingBall()
        }
        dataRepository.setFloatingBallEnabled(enabled)
        _state.update { it.copy(isFloatingBallEnabled = enabled) }
    }

    private fun onToggleDebugApi(enabled: Boolean) {
        _state.update { it.copy(debugApiTransitioning = true) }
        dataRepository.setDebugApiEnabled(enabled)
        if (enabled) {
            debugApiController.start()
        } else {
            debugApiController.stop()
        }
        viewModelScope.launch {
            while (debugApiController.isTransitioning) {
                delay(100)
            }
            _state.update {
                it.copy(
                    isDebugApiEnabled = enabled,
                    debugApiRunning = debugApiController.isRunning,
                    debugApiTransitioning = false,
                )
            }
        }
    }

    private fun onToggleDebugEndpoint(enabled: Boolean) {
        dataRepository.setDebugEndpointEnabled(enabled)
        _state.update { it.copy(isDebugEndpointEnabled = enabled) }
    }

    private fun onToggleHeartbeat(enabled: Boolean) {
        dataRepository.setHeartbeatEnabled(enabled)
        _state.update { it.copy(isHeartbeatEnabled = enabled) }
    }

    private fun onChangeHeartbeatInterval(minutes: Int) {
        dataRepository.setHeartbeatIntervalMinutes(minutes)
        _state.update { it.copy(heartbeatIntervalMinutes = minutes) }
    }

    private fun onChangeHeartbeatActiveHours(start: Int, end: Int) {
        dataRepository.setHeartbeatActiveHours(start, end)
        _state.update { it.copy(heartbeatActiveHoursStart = start, heartbeatActiveHoursEnd = end) }
    }

    private fun onSaveHeartbeatPrompt(text: String) {
        dataRepository.setHeartbeatPrompt(text)
        _state.update { it.copy(heartbeatPrompt = text) }
    }

    private fun onChangeHeartbeatService(instanceId: String?) {
        dataRepository.setHeartbeatInstanceId(instanceId)
        _state.update { it.copy(heartbeatSelectedInstanceId = instanceId) }
    }

    private fun onRefreshHeartbeat() {
        if (_state.value.isRefreshingHeartbeat) return
        _state.update { it.copy(isRefreshingHeartbeat = true) }
        viewModelScope.launch(backgroundDispatcher) {
            taskScheduler.triggerHeartbeatNow()
            _state.update {
                it.copy(
                    isRefreshingHeartbeat = false,
                    heartbeatLog = dataRepository.getHeartbeatLog().toImmutableList(),
                )
            }
        }
    }

    private fun onToggleEmail(enabled: Boolean) {
        dataRepository.setEmailEnabled(enabled)
        _state.update { it.copy(isEmailEnabled = enabled) }
    }

    private fun onRemoveEmailAccount(id: String) {
        commitPendingDeletion()
        _state.update { it.copy(pendingDeletion = PendingDeletion.EmailAccount(id)) }
        pendingDeleteJob = viewModelScope.launch(backgroundDispatcher) {
            delay(4.seconds)
            executeDeletion(PendingDeletion.EmailAccount(id))
        }
    }

    private fun onChangeEmailPollInterval(minutes: Int) {
        dataRepository.setEmailPollIntervalMinutes(minutes)
        _state.update { it.copy(emailPollIntervalMinutes = minutes) }
    }

    private fun onRefreshEmailAccount(id: String) {
        if (id in _state.value.refreshingEmailAccountIds) return
        _state.update { it.copy(refreshingEmailAccountIds = (it.refreshingEmailAccountIds + id).toPersistentSet()) }
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.pollEmailAccount(id)
            _state.update {
                it.copy(
                    refreshingEmailAccountIds = (it.refreshingEmailAccountIds - id).toPersistentSet(),
                    emailSyncStates = dataRepository.getEmailSyncStates().toImmutableMap(),
                    emailPendingCount = dataRepository.getPendingEmailCount(),
                )
            }
        }
    }

    private fun onToggleSms(enabled: Boolean) {
        if (enabled && !dataRepository.hasSmsPermission()) {
            // Ask for the OS permission first; only flip the toggle on if it's granted.
            viewModelScope.launch(backgroundDispatcher) {
                val granted = dataRepository.requestSmsPermission()
                _state.update { it.copy(smsPermissionGranted = granted, isSmsEnabled = granted) }
                if (granted) {
                    dataRepository.setSmsEnabled(true)
                    // First poll seeds lastSeenId to the current inbox max, so the AI
                    // isn't drowned in historical messages on opt-in.
                    dataRepository.pollSms()
                    _state.update {
                        it.copy(
                            smsSyncState = dataRepository.getSmsSyncState(),
                            smsPendingCount = dataRepository.getPendingSmsCount(),
                        )
                    }
                }
            }
        } else {
            dataRepository.setSmsEnabled(enabled)
            _state.update { it.copy(isSmsEnabled = enabled) }
        }
    }

    private fun onChangeSmsPollInterval(minutes: Int) {
        dataRepository.setSmsPollIntervalMinutes(minutes)
        _state.update { it.copy(smsPollIntervalMinutes = minutes) }
    }

    private fun onRefreshSms() {
        if (_state.value.isRefreshingSms) return
        _state.update { it.copy(isRefreshingSms = true) }
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.pollSms()
            _state.update {
                it.copy(
                    isRefreshingSms = false,
                    smsSyncState = dataRepository.getSmsSyncState(),
                    smsPendingCount = dataRepository.getPendingSmsCount(),
                    smsPermissionGranted = dataRepository.hasSmsPermission(),
                )
            }
        }
    }

    private fun onToggleSmsSend(enabled: Boolean) {
        if (enabled && !dataRepository.hasSmsSendPermission()) {
            viewModelScope.launch(backgroundDispatcher) {
                val granted = dataRepository.requestSmsSendPermission()
                _state.update { it.copy(smsSendPermissionGranted = granted, isSmsSendEnabled = granted) }
                if (granted) dataRepository.setSmsSendEnabled(true)
            }
        } else {
            dataRepository.setSmsSendEnabled(enabled)
            _state.update { it.copy(isSmsSendEnabled = enabled) }
        }
    }

    private fun onToggleNotifications(enabled: Boolean) {
        // Listener access is granted via system Settings, not a runtime permission
        // dialog. Set the toggle, then if access is missing, deep-link the user out
        // so they can enable Kai there. The toggle reflects the user's *intent*; the
        // listener still drops everything until access is granted.
        dataRepository.setNotificationsEnabled(enabled)
        _state.update {
            it.copy(
                isNotificationsEnabled = enabled,
                notificationListenerAccessGranted = dataRepository.isNotificationListenerAccessGranted(),
            )
        }
        if (enabled && !dataRepository.isNotificationListenerAccessGranted()) {
            dataRepository.openNotificationListenerSettings()
        }
    }

    private fun onOpenNotificationListenerSettings() {
        dataRepository.openNotificationListenerSettings()
    }

    private fun onToggleShizuku(enabled: Boolean) {
        if (enabled) {
            if (!isShizukuPermissionGranted()) {
                requestShizukuPermission(onGranted = {
                    dataRepository.setShizukuEnabled(true)
                    _state.update {
                        it.copy(isShizukuEnabled = true, shizukuPermissionGranted = true)
                    }
                })
            } else {
                dataRepository.setShizukuEnabled(true)
                _state.update {
                    it.copy(isShizukuEnabled = true, shizukuPermissionGranted = true)
                }
            }
        } else {
            dataRepository.setShizukuEnabled(false)
            _state.update {
                it.copy(isShizukuEnabled = false, shizukuPermissionGranted = isShizukuPermissionGranted())
            }
        }
    }

    private fun onOpenShizukuPermission() {
        requestShizukuPermission(onGranted = {
            _state.update { it.copy(shizukuPermissionGranted = true) }
        })
    }

    private fun onToggleRoot(enabled: Boolean) {
        if (enabled && !isRootAvailable()) {
            _state.update { it.copy(rootErrorMessage = "Root not available") }
            return
        }
        dataRepository.setRootEnabled(enabled)
        _state.update {
            it.copy(
                isRootEnabled = enabled,
                rootAvailable = isRootAvailable(),
                rootErrorMessage = null,
            )
        }
    }

    private fun onOpenTtsSettings() {
        openTtsSettings()
    }

    private fun onClearPendingNotifications() {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.clearPendingNotifications()
            _state.update { it.copy(notificationPendingCount = 0) }
        }
    }

    private fun onToggleFreeFallback(enabled: Boolean) {
        dataRepository.setFreeFallbackEnabled(enabled)
        _state.update { it.copy(isFreeFallbackEnabled = enabled) }
    }

    private fun onToggleWakeWord(enabled: Boolean) {
        dataRepository.setWakeWordEnabled(enabled)
        _state.update { it.copy(isWakeWordEnabled = enabled) }
    }

    private fun onChangeWakeWordPhrase(phrase: String) {
        dataRepository.setWakeWordPhrase(phrase)
        _state.update { it.copy(wakeWordPhrase = phrase) }
    }

    private fun onChangePreferredLanguage(lang: String) {
        dataRepository.setPreferredLanguage(lang)
        _state.update { it.copy(preferredLanguage = lang) }
    }

    private fun onChangeWakeWordMode(mode: String) {
        dataRepository.setWakeWordMode(mode)
        _state.update { it.copy(wakeWordMode = mode) }
    }

    private fun onEnrollWakeWord() {
        val phrase = dataRepository.getWakeWordPhrase()
        _state.update { it.copy(isEnrolling = true, wakeWordEnrollmentMessage = "Getting ready...") }
        viewModelScope.launch(backgroundDispatcher) {
            val template = wakeWordController.enroll(phrase) { msg ->
                _state.update { it.copy(wakeWordEnrollmentMessage = msg) }
            }
            if (template != null) {
                dataRepository.setWakeWordTemplate(template)
                _state.update { it.copy(wakeWordEnrolled = true, isEnrolling = false, wakeWordEnrollmentMessage = "") }
            } else {
                _state.update { it.copy(isEnrolling = false, wakeWordEnrollmentMessage = "") }
            }
        }
    }

    private fun onCapturePttTrigger() {
        println("PTT_DEBUG: onCapturePttTrigger called, captureMode=${PttTriggerManager.captureMode.value}")
        PttTriggerManager.startCapture()
        viewModelScope.launch {
            PttTriggerManager.capturedKeyCode.collect { keyCode ->
                println("PTT_DEBUG: capturedKeyCode received=$keyCode")
                if (keyCode != 0) {
                    dataRepository.setPttTriggerKeyCode(keyCode)
                    _state.update { it.copy(pttTriggerKeyCode = keyCode) }
                    return@collect
                }
            }
        }
    }

    private fun onClearPttTrigger() {
        println("PTT_DEBUG: onClearPttTrigger called, current=${dataRepository.getPttTriggerKeyCode()}")
        dataRepository.setPttTriggerKeyCode(0)
        _state.update { it.copy(pttTriggerKeyCode = 0) }
    }

    private fun onDownloadLocalModel(model: LocalModel) {
        dataRepository.startLocalModelDownload(model)
    }

    private fun onCancelLocalModelDownload() {
        dataRepository.cancelLocalModelDownload()
    }

    private fun onChangeModelContextTokens(modelId: String, contextTokens: Int) {
        if (_state.value.modelContextTokens[modelId] == contextTokens) return
        dataRepository.setModelContextTokens(modelId, contextTokens)
        _state.update {
            it.copy(modelContextTokens = it.modelContextTokens.toMutableMap().apply { put(modelId, contextTokens) }.toImmutableMap())
        }
        // Release engine so the next message re-initializes with the new context size
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.releaseLocalEngine()
        }
    }

    private fun buildModelContextTokensMap() = dataRepository.getLocalAvailableModels().associate { model ->
        val stored = dataRepository.getModelContextTokens(model.id)
        model.id to if (stored > 0) stored else model.defaultContextTokens
    }.toImmutableMap()

    private fun buildModelMaxTokensMap() = dataRepository.getLocalAvailableModels().associate { model ->
        model.id to dataRepository.getModelMaxTokens(model.id)
    }.toImmutableMap()

    private fun onChangeModelMaxTokens(modelId: String, maxTokens: Int) {
        if (_state.value.modelMaxTokens[modelId] == maxTokens) return
        dataRepository.setModelMaxTokens(modelId, maxTokens)
        _state.update {
            it.copy(modelMaxTokens = it.modelMaxTokens.toMutableMap().apply { put(modelId, maxTokens) }.toImmutableMap())
        }
    }

    private fun buildModelTemperatureMap() = dataRepository.getLocalAvailableModels().associate { model ->
        model.id to dataRepository.getModelTemperature(model.id)
    }.toImmutableMap()

    private fun onChangeModelTemperature(modelId: String, temperature: Float) {
        if (_state.value.modelTemperature[modelId] == temperature) return
        dataRepository.setModelTemperature(modelId, temperature)
        _state.update {
            it.copy(modelTemperature = it.modelTemperature.toMutableMap().apply { put(modelId, temperature) }.toImmutableMap())
        }
    }

    private fun onChangeLocalStyleInstruction(text: String) {
        dataRepository.setLocalStyleInstruction(text)
        _state.update { it.copy(localStyleInstruction = text) }
    }

    private fun onChangeLocalModelFullPrompt(enabled: Boolean) {
        dataRepository.setLocalModelFullPrompt(enabled)
        _state.update { it.copy(localModelFullPrompt = enabled) }
    }

    private fun onChangeModelTopK(modelId: String, topK: Int) {
        if (_state.value.modelTopK[modelId] == topK) return
        dataRepository.setModelTopK(modelId, topK)
        _state.update {
            it.copy(modelTopK = it.modelTopK.toMutableMap().apply { put(modelId, topK) }.toImmutableMap())
        }
    }

    private fun onChangeModelTopP(modelId: String, topP: Float) {
        if (_state.value.modelTopP[modelId] == topP) return
        dataRepository.setModelTopP(modelId, topP)
        _state.update {
            it.copy(modelTopP = it.modelTopP.toMutableMap().apply { put(modelId, topP) }.toImmutableMap())
        }
    }

    private fun onChangeModelGpuLayers(modelId: String, gpuLayers: Int) {
        if (_state.value.modelGpuLayers[modelId] == gpuLayers) return
        dataRepository.setModelGpuLayers(modelId, gpuLayers)
        _state.update {
            it.copy(modelGpuLayers = it.modelGpuLayers.toMutableMap().apply { put(modelId, gpuLayers) }.toImmutableMap())
        }
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.releaseLocalEngine()
        }
    }

    @Suppress("DEPRECATION")
    private fun onImportLocalModel(bytes: ByteArray, fileName: String) {
        viewModelScope.launch(backgroundDispatcher) {
            try {
                dataRepository.importLocalModel(bytes, fileName)
                refreshServiceList()
            } catch (e: Exception) {
                println("Import model failed: ${e.message}")
            }
        }
    }

    private fun buildModelTopKMap() = dataRepository.getLocalAvailableModels().associate { model ->
        model.id to dataRepository.getModelTopK(model.id)
    }.toImmutableMap()

    private fun buildModelTopPMap() = dataRepository.getLocalAvailableModels().associate { model ->
        model.id to dataRepository.getModelTopP(model.id)
    }.toImmutableMap()

    private fun buildModelGpuLayersMap() = dataRepository.getLocalAvailableModels().associate { model ->
        model.id to dataRepository.getModelGpuLayers(model.id)
    }.toImmutableMap()

    private fun onDeleteLocalModel(modelId: String) {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.deleteLocalModel(modelId)
            _state.update { it.copy(localFreeSpaceBytes = dataRepository.getLocalFreeSpaceBytes()) }
            refreshServiceList()
            _state.value.configuredServices
                .filter { it.service.isOnDevice }
                .forEach { checkConnection(it.instanceId, it.service) }
        }
    }

    private fun onChangeUiScale(scale: Float) {
        dataRepository.setUiScale(scale)
        _state.update { it.copy(uiScale = scale) }
    }

    private fun onExportSettings(sections: Set<ImportSection>): String = dataRepository.exportSettingsToJson(sections)

    private fun onPrepareExport(): Map<ImportSection, String?> = dataRepository.getExportPreview()

    private fun onImportSettings(bytes: ByteArray, sections: Set<ImportSection>, replace: Boolean): ImportResult = try {
        val currentTab = _state.value.currentTab
        val errors = dataRepository.importSettingsFromJson(bytes.decodeToString(), sections, replace)
        _state.value = buildFullState().copy(currentTab = currentTab)
        checkAllConnections()
        connectEnabledMcpServers()
        if (errors == 0) ImportResult.Success else ImportResult.PartialSuccess(errors)
    } catch (_: Exception) {
        ImportResult.Failure
    }

    private fun onToggleTool(toolId: String, enabled: Boolean) {
        if (enabled) {
            val permissions = getToolPermissionMap()[toolId]
            if (permissions != null && permissions.isNotEmpty()) {
                viewModelScope.launch(backgroundDispatcher) {
                    val granted = toolPermissionBridge.requestPermission(*permissions.toTypedArray())
                    if (granted) {
                        if (toolId == "set_gps_location" && !isMockLocationConfigured()) {
                            openMockLocationSettings()
                            return@launch
                        }
                        dataRepository.setToolEnabled(toolId, true)
                        updateToolEnabledInState(toolId, true)
                    }
                }
                return
            }
            if (toolId == "set_gps_location" && !isMockLocationConfigured()) {
                openMockLocationSettings()
                return
            }
        }
        dataRepository.setToolEnabled(toolId, enabled)
        updateToolEnabledInState(toolId, enabled)
    }

    private fun updateToolEnabledInState(toolId: String, enabled: Boolean) {
        _state.update { state ->
            state.copy(
                tools = state.tools.map { tool ->
                    if (tool.id == toolId) tool.copy(isEnabled = enabled) else tool
                }.toImmutableList(),
                mcpServers = state.mcpServers.map { server ->
                    server.copy(
                        tools = server.tools.map { tool ->
                            if (tool.id == toolId) tool.copy(isEnabled = enabled) else tool
                        }.toImmutableList(),
                    )
                }.toImmutableList(),
            )
        }
    }

    // MCP server management
    private fun buildMcpServerEntries(): List<McpServerUiState> = dataRepository.getMcpServers().map { config ->
        McpServerUiState(
            id = config.id,
            name = config.name,
            url = config.url,
            isEnabled = config.isEnabled,
            connectionStatus = if (dataRepository.isMcpServerConnected(config.id)) {
                McpConnectionStatus.Connected
            } else {
                McpConnectionStatus.Unknown
            },
            tools = dataRepository.getMcpToolsForServer(config.id).toImmutableList(),
        )
    }

    private fun refreshMcpServers() {
        _state.update { current ->
            val existingStatuses = current.mcpServers.associate { it.id to it.connectionStatus }
            current.copy(
                mcpServers = buildMcpServerEntries().map { entry ->
                    val preservedStatus = existingStatuses[entry.id]
                    if (preservedStatus == McpConnectionStatus.Connecting || preservedStatus == McpConnectionStatus.Error) {
                        entry.copy(connectionStatus = preservedStatus)
                    } else {
                        entry
                    }
                }.toImmutableList(),
                altMemoryConnected = mcpServerManager.isConnected("alt_memory"),
                altMemoryInstalled = dataRepository.isAltMemoryInstalled(),
            )
        }
    }

    private fun onAddMcpServer(name: String, url: String, headers: Map<String, String>) {
        viewModelScope.launch(backgroundDispatcher) {
            val config = dataRepository.addMcpServer(name, url, headers)
            refreshMcpServers()
            connectMcpServerWithStatus(config.id)
        }
        _state.update { it.copy(showAddMcpServerDialog = false) }
    }

    private fun onRemoveMcpServer(serverId: String) {
        commitPendingDeletion()
        _state.update { it.copy(pendingDeletion = PendingDeletion.McpServer(serverId)) }
        pendingDeleteJob = viewModelScope.launch(backgroundDispatcher) {
            delay(4.seconds)
            executeDeletion(PendingDeletion.McpServer(serverId))
        }
    }

    private fun onToggleMcpServer(serverId: String, enabled: Boolean) {
        dataRepository.setMcpServerEnabled(serverId, enabled)
        refreshMcpServers()
        if (enabled) {
            viewModelScope.launch(backgroundDispatcher) {
                connectMcpServerWithStatus(serverId)
            }
        }
    }

    private fun onRefreshMcpServer(serverId: String) {
        viewModelScope.launch(backgroundDispatcher) {
            connectMcpServerWithStatus(serverId)
        }
    }

    private fun onShowAddMcpServerDialog(show: Boolean) {
        _state.update { it.copy(showAddMcpServerDialog = show) }
    }

    private fun onAddPopularMcpServer(server: PopularMcpServer) {
        onAddMcpServer(server.name, server.url, emptyMap())
    }

    private suspend fun connectMcpServerWithStatus(serverId: String) {
        updateMcpConnectionStatus(serverId, McpConnectionStatus.Connecting)
        val result = dataRepository.connectMcpServer(serverId)
        if (result.isSuccess) {
            updateMcpConnectionStatus(serverId, McpConnectionStatus.Connected)
            refreshMcpServers()
        } else {
            updateMcpConnectionStatus(serverId, McpConnectionStatus.Error)
        }
    }

    private fun updateMcpConnectionStatus(serverId: String, status: McpConnectionStatus) {
        _state.update { state ->
            state.copy(
                mcpServers = state.mcpServers.map { entry ->
                    if (entry.id == serverId) entry.copy(connectionStatus = status) else entry
                }.toImmutableList(),
            )
        }
    }

    private fun connectEnabledMcpServers() {
        val enabledServers = _state.value.mcpServers.filter { it.isEnabled && it.connectionStatus != McpConnectionStatus.Connected }
        for (server in enabledServers) {
            viewModelScope.launch(backgroundDispatcher) {
                connectMcpServerWithStatus(server.id)
            }
        }
    }

    private fun commitPendingDeletion() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        val deletion = _state.value.pendingDeletion ?: return
        _state.update { it.copy(pendingDeletion = null) }
        viewModelScope.launch(backgroundDispatcher) {
            executeDeletion(deletion)
        }
    }

    private suspend fun executeDeletion(deletion: PendingDeletion) {
        when (deletion) {
            is PendingDeletion.Memory -> {
                dataRepository.deleteMemory(deletion.key)
                _state.update { it.copy(memories = dataRepository.getMemories().filter { !it.protected }.toImmutableList()) }
            }

            is PendingDeletion.Task -> {
                dataRepository.cancelScheduledTask(deletion.id)
                _state.update { it.copy(scheduledTasks = dataRepository.getScheduledTasks().toImmutableList()) }
            }

            is PendingDeletion.EmailAccount -> {
                dataRepository.removeEmailAccount(deletion.id)
                _state.update {
                    it.copy(
                        emailAccounts = dataRepository.getEmailAccounts().toImmutableList(),
                        emailSyncStates = dataRepository.getEmailSyncStates().toImmutableMap(),
                        emailPendingCount = dataRepository.getPendingEmailCount(),
                    )
                }
            }

            is PendingDeletion.Service -> {
                val service = _state.value.configuredServices.find { it.instanceId == deletion.instanceId }?.service
                dataRepository.removeConfiguredService(deletion.instanceId)
                // If removing the last on-device service, delete all downloaded models
                if (service?.isOnDevice == true) {
                    val hasOtherOnDevice = dataRepository.getConfiguredServiceInstances().any {
                        Service.fromId(it.serviceId).isOnDevice
                    }
                    if (!hasOtherOnDevice) {
                        dataRepository.getLocalDownloadedModels().forEach {
                            dataRepository.deleteLocalModel(it.id)
                        }
                        _state.update { it.copy(localFreeSpaceBytes = dataRepository.getLocalFreeSpaceBytes()) }
                    }
                }
                refreshServiceList()
            }

            is PendingDeletion.McpServer -> {
                dataRepository.removeMcpServer(deletion.serverId)
                refreshMcpServers()
            }

            is PendingDeletion.Skill -> {
                dataRepository.uninstallSkill(deletion.id)
                _state.update {
                    it.copy(
                        installedSkills = dataRepository.getInstalledSkills().toImmutableList(),
                        activeSkill = dataRepository.getActiveSkill(),
                    )
                }
            }
        }
        // Guard against a stale async deletion clobbering a newer pending one from a rapid second Remove click.
        _state.update { state ->
            if (state.pendingDeletion == deletion) state.copy(pendingDeletion = null) else state
        }
    }

    private fun onUndoDelete() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        _state.update { it.copy(pendingDeletion = null) }
    }

    override fun onCleared() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        val deletion = _state.value.pendingDeletion ?: run {
            super.onCleared()
            return
        }
        _state.update { it.copy(pendingDeletion = null) }
        CoroutineScope(backgroundDispatcher).launch {
            executeDeletion(deletion)
        }
        super.onCleared()
    }

    // Skill management

    private fun onInstallSkillFromGitHub(githubUrl: String) {
        viewModelScope.launch {
            val source = parseGitHubSkillUrl(githubUrl)
            if (source == null) return@launch
            val result = dataRepository.installSkillFromGitHub(source.owner, source.repo, source.ref, source.path)
            result.onSuccess {
                _state.update {
                    it.copy(installedSkills = dataRepository.getInstalledSkills().toImmutableList())
                }
            }
        }
    }

    private fun onInstallSkillFromBrowsed(entry: RegistrySkillEntry) {
        viewModelScope.launch {
            val result = dataRepository.installSkillFromRegistryEntry(entry)
            result.onSuccess {
                _state.update {
                    it.copy(installedSkills = dataRepository.getInstalledSkills().toImmutableList())
                }
            }
        }
    }

    private fun onUninstallSkill(id: String) {
        _state.update { it.copy(pendingDeletion = PendingDeletion.Skill(id)) }
    }

    private fun onBrowseMarketplaceSkills() {
        _state.update { it.copy(isBrowsingMarketplace = true) }
        viewModelScope.launch {
            val result = dataRepository.browseMarketplaceSkills()
            _state.update {
                it.copy(
                    marketplaceSkills = result.getOrDefault(emptyList()).toImmutableList(),
                    isBrowsingMarketplace = false,
                )
            }
        }
    }

    private fun checkAllConnections() {
        for (entry in _state.value.configuredServices) {
            checkConnection(entry.instanceId, entry.service)
        }
    }

    private fun checkConnectionDebounced(instanceId: String, service: Service) {
        connectionCheckJobs[instanceId]?.cancel()
        connectionCheckJobs[instanceId] = viewModelScope.launch {
            delay(800.milliseconds)
            checkConnection(instanceId, service)
        }
    }

    private fun checkConnection(instanceId: String, service: Service) {
        if (service == Service.Free) {
            updateConnectionStatus(instanceId, ConnectionStatus.Connected)
            return
        }
        if (service.isOnDevice) {
            validateConnectionWithStatus(instanceId, service)
            return
        }
        if (service.requiresApiKey && dataRepository.getInstanceApiKey(instanceId).isBlank()) {
            updateConnectionStatus(instanceId, ConnectionStatus.Unknown)
            return
        }
        validateConnectionWithStatus(instanceId, service)
    }

    private fun updateConnectionStatus(instanceId: String, status: ConnectionStatus) {
        _state.update { state ->
            state.copy(
                configuredServices = state.configuredServices.map { entry ->
                    if (entry.instanceId == instanceId) {
                        entry.copy(connectionStatus = status)
                    } else {
                        entry
                    }
                }.toImmutableList(),
            )
        }
    }

    private fun validateConnectionWithStatus(instanceId: String, service: Service) {
        updateConnectionStatus(instanceId, ConnectionStatus.Checking)
        viewModelScope.launch(backgroundDispatcher) {
            try {
                dataRepository.validateConnection(service, instanceId)
                if (service.isOnDevice && dataRepository.getLocalDownloadedModels().isEmpty()) {
                    updateConnectionStatus(instanceId, ConnectionStatus.Unknown)
                } else {
                    updateConnectionStatus(instanceId, ConnectionStatus.Connected)
                }
                refreshInstanceModels(instanceId)
            } catch (e: Exception) {
                val status = when (e) {
                    is OpenAICompatibleInvalidApiKeyException, is GeminiInvalidApiKeyException, is AnthropicInvalidApiKeyException ->
                        ConnectionStatus.ErrorInvalidKey

                    is OpenAICompatibleQuotaExhaustedException, is AnthropicInsufficientCreditsException ->
                        ConnectionStatus.ErrorQuotaExhausted

                    is OpenAICompatibleRateLimitExceededException, is GeminiRateLimitExceededException, is AnthropicRateLimitExceededException ->
                        ConnectionStatus.ErrorRateLimited

                    is AnthropicOverloadedException ->
                        ConnectionStatus.Error

                    is OpenAICompatibleConnectionException ->
                        ConnectionStatus.ErrorConnectionFailed

                    else -> ConnectionStatus.Error
                }
                updateConnectionStatus(instanceId, status)
            }
        }
    }
}
