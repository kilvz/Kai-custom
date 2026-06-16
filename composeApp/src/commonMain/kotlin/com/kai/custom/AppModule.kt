package com.kai.custom

import com.kai.custom.SpeechToText
import com.kai.custom.data.AppSettings
import com.kai.custom.data.ConversationStorage
import com.kai.custom.data.DataRepository
import com.kai.custom.data.EmailStore
import com.kai.custom.data.HeartbeatManager
import com.kai.custom.data.MemoryStore
import com.kai.custom.data.MemoryStoreProvider
import com.kai.custom.data.NotificationStore
import com.kai.custom.data.RemoteDataRepository
import com.kai.custom.data.SmsDraftStore
import com.kai.custom.data.SmsStore
import com.kai.custom.data.SqliteMemoryStore
import com.kai.custom.data.TaskScheduler
import com.kai.custom.data.TaskStore
import com.kai.custom.data.TelegramStore
import com.kai.custom.data.ToolExecutor
import com.kai.custom.data.WhatsAppStore
import com.kai.custom.data.dimension.DimensionStore
import com.kai.custom.data.runMigrations
import com.kai.custom.email.EmailPoller
import com.kai.custom.inference.createLocalInferenceEngine
import com.kai.custom.mcp.McpServerManager
import com.kai.custom.network.Requests
import com.kai.custom.notifications.NotificationReader
import com.kai.custom.skills.SkillManager
import com.kai.custom.skills.SkillRegistry
import com.kai.custom.sms.SmsPoller
import com.kai.custom.sms.SmsReader
import com.kai.custom.sms.SmsSender
import com.kai.custom.splinterlands.SplinterlandsApi
import com.kai.custom.splinterlands.SplinterlandsBattleRunner
import com.kai.custom.splinterlands.SplinterlandsStore
import com.kai.custom.telegram.TelegramPoller
import com.kai.custom.tools.ActivityResultBridge
import com.kai.custom.tools.CalendarPermissionController
import com.kai.custom.tools.MicrophonePermissionController
import com.kai.custom.tools.NotificationListenerController
import com.kai.custom.AutoUpdateManager
import com.kai.custom.tools.NotificationPermissionController
import com.kai.custom.tools.SmsPermissionController
import com.kai.custom.tools.SmsSendPermissionController
import com.kai.custom.tools.ToolPermissionBridge
import com.kai.custom.ui.chat.ChatViewModel
import com.kai.custom.ui.sandbox.SandboxFileBrowserViewModel
import com.kai.custom.ui.sandbox.SandboxPackagesViewModel
import com.kai.custom.ui.sandbox.SandboxSessionViewModel
import com.kai.custom.ui.settings.SandboxViewModel
import com.kai.custom.ui.settings.SettingsViewModel
import com.kai.custom.ui.settings.SplinterlandsViewModel
import com.kai.custom.ui.settings.SshViewModel
import com.kai.custom.wakeword.WakeWordController
import com.kai.custom.wakeword.createWakeWordController
import com.kai.custom.whatsapp.WhatsAppLifecycleManager
import com.kai.custom.whatsapp.WhatsAppPoller
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<CalendarPermissionController> { CalendarPermissionController() }
    single<NotificationPermissionController> { NotificationPermissionController() }
    single<AutoUpdateManager> { AutoUpdateManager() }
    single<SmsPermissionController> { SmsPermissionController() }
    single<SmsSendPermissionController> { SmsSendPermissionController() }
    single<MicrophonePermissionController> { MicrophonePermissionController() }
    single<ToolPermissionBridge> { ToolPermissionBridge() }
    single<ActivityResultBridge> { ActivityResultBridge() }
    single<SmsReader> { SmsReader() }
    single<SmsSender> { SmsSender() }
    single<NotificationListenerController> { NotificationListenerController() }
    single<NotificationReader> { NotificationReader() }
    single<AppSettings> {
        AppSettings(createSecureSettings()).also {
            it.runMigrations(createLegacySettings())
        }
    }
    single<Requests> {
        Requests()
    }
    single<ConversationStorage> {
        ConversationStorage(get())
    }
    single<ToolExecutor> {
        ToolExecutor()
    }
    single<SqliteMemoryStore> {
        SqliteMemoryStore(get<DimensionStore>())
    }
    single<MemoryStoreProvider> {
        MemoryStoreProvider(get<SqliteMemoryStore>())
    }
    single<MemoryStore> {
        get<MemoryStoreProvider>()
    }
    single<TaskStore> {
        TaskStore(get())
    }
    single<EmailStore> {
        EmailStore(get())
    }
    single<EmailPoller> {
        EmailPoller(get<EmailStore>())
    }
    single<SmsStore> {
        SmsStore(get())
    }
    single<SmsPoller> {
        SmsPoller(get<SmsStore>(), get<SmsReader>())
    }
    single<SmsDraftStore> {
        SmsDraftStore(get())
    }
    single<NotificationStore> {
        NotificationStore(get())
    }
    single<TelegramStore> {
        TelegramStore(get())
    }
    single<TelegramPoller> {
        TelegramPoller(get<TelegramStore>(), lazy { get<DataRepository>() })
    }
    single<WhatsAppStore> {
        WhatsAppStore(get())
    }
    single<WhatsAppPoller> {
        WhatsAppPoller(get<WhatsAppStore>(), lazy { get<DataRepository>() }, get<McpServerManager>())
    }
    single<WhatsAppLifecycleManager> {
        WhatsAppLifecycleManager(get<SandboxController>(), get<McpServerManager>(), get(), get<WhatsAppStore>())
    }
    single<SplinterlandsStore> {
        SplinterlandsStore(get())
    }
    single<SplinterlandsApi> {
        SplinterlandsApi()
    }
    single<HeartbeatManager> {
        HeartbeatManager(get(), get(), get(), get())
    }
    single<SkillRegistry> {
        SkillRegistry()
    }
    single<SkillManager> {
        SkillManager(get(), get())
    }
    single<McpServerManager> {
        McpServerManager(get())
    }
    single<RemoteDataRepository> {
        RemoteDataRepository(
            requests = get(),
            appSettings = get(),
            conversationStorage = get(),
            toolExecutor = get(),
            memoryStore = get(),
            taskStore = get(),
            heartbeatManager = get(),
            emailStore = get(),
            emailPoller = get(),
            smsStore = get(),
            smsPoller = get(),
            smsReader = get(),
            smsPermissionController = get(),
            smsSendPermissionController = get(),
            smsSender = get(),
            smsDraftStore = get(),
            notificationStore = get(),
            notificationListenerController = get(),
            mcpServerManager = get(),
            sandboxController = get(),
            localInferenceEngine = createLocalInferenceEngine(),
            skillManager = get(),
            telegramStore = get(),
            telegramPoller = get(),
        )
    }
    single<DataRepository> { get<RemoteDataRepository>() }
    single<SplinterlandsBattleRunner> {
        SplinterlandsBattleRunner(get(), get(), get<DataRepository>(), get<DaemonController>())
    }
    single<TaskScheduler> {
        TaskScheduler(
            get<DataRepository>(),
            get(),
            get(),
            get(),
            get(),
            get<EmailPoller>(),
            get<SmsStore>(),
            get<SmsPoller>(),
            get<NotificationStore>(),
            get<MemoryStore>(),
            telegramStore = get(),
            telegramPoller = get(),
            whatsAppStore = get(),
            whatsAppPoller = get(),
        )
    }
    single<DaemonController> { createDaemonController() }
    single<DebugApiController> { createDebugApiController() }
    single<SandboxController> { createSandboxController() }
    single<SpeechToText> { createSpeechToText() }
    single<WakeWordController> { createWakeWordController() }
    single<SshConnectionManager> { createSshConnectionManager() }
    viewModel { SettingsViewModel(get<DataRepository>(), get<DaemonController>(), get<DebugApiController>(), get<NotificationPermissionController>(), get<TaskScheduler>(), get<WakeWordController>(), get<SandboxController>(), get<McpServerManager>(), get<ToolPermissionBridge>(), get<AutoUpdateManager>()) }
    viewModel { SandboxViewModel(get<DataRepository>(), get<SandboxController>()) }
    viewModel { SshViewModel(get<AppSettings>(), get<SshConnectionManager>()) }
    viewModel { SandboxFileBrowserViewModel(get<SandboxController>()) }
    viewModel { SandboxPackagesViewModel(get<SandboxController>(), get<DataRepository>()) }
    viewModel { SandboxSessionViewModel(get<SandboxController>(), get<DataRepository>()) }
    viewModel { SplinterlandsViewModel(get<DataRepository>(), get(), get(), get<SplinterlandsApi>()) }
    viewModel { ChatViewModel(get<DataRepository>(), get<TaskScheduler>(), get<SpeechToText>(), get<MicrophonePermissionController>(), get<WakeWordController>()) }
}
