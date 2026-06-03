package com.kai.custom

import android.Manifest
import android.app.usage.NetworkStatsManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.telephony.TelephonyManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.kai.custom.data.AppSettings
import com.kai.custom.data.EmailStore
import com.kai.custom.data.MemoryStore
import com.kai.custom.data.NotificationStore
import com.kai.custom.data.SmsDraftStore
import com.kai.custom.data.SmsStore
import com.kai.custom.data.TaskStore
import com.kai.custom.data.TelegramStore
import com.kai.custom.mcp.McpServerManager
import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import com.kai.custom.notifications.NotificationReader
import com.kai.custom.notifications.declaresNotificationListener
import com.kai.custom.root.RootManager
import com.kai.custom.shizuku.ShizukuManager
import com.kai.custom.sms.SmsReader
import com.kai.custom.sms.SmsSender
import com.kai.custom.sms.declaresReadSms
import com.kai.custom.tools.AdbTool
import com.kai.custom.tools.CalendarPermissionController
import com.kai.custom.tools.CalendarRepository
import com.kai.custom.tools.CalendarResult
import com.kai.custom.tools.CommonTools
import com.kai.custom.tools.EmailTools
import com.kai.custom.tools.HeartbeatTools
import com.kai.custom.tools.NotificationHelper
import com.kai.custom.tools.NotificationPermissionController
import com.kai.custom.tools.NotificationResult
import com.kai.custom.tools.NotificationTools
import com.kai.custom.tools.OpenCodeTool
import com.kai.custom.tools.OpenFileTool
import com.kai.custom.tools.ReadFileTool
import com.kai.custom.tools.WriteFileTool
import com.kai.custom.tools.EditFileTool
import com.kai.custom.tools.GlobTool
import com.kai.custom.tools.GrepTool
import com.kai.custom.tools.ApplyPatchTool
import com.kai.custom.tools.TodoWriteTool
import com.kai.custom.tools.WebFetchTool
import com.kai.custom.tools.InternetSearchTool
import com.kai.custom.tools.PhoneTools
import com.kai.custom.tools.ProcessManagerTool
import com.kai.custom.tools.RootTool
import com.kai.custom.tools.SchedulingTools
import com.kai.custom.tools.ShellCommandTool
import com.kai.custom.tools.SmsTools
import com.kai.custom.tools.SpeakTextTool
import com.kai.custom.tools.SshCommandTool
import com.kai.custom.tools.SshConfigureHostTool
import com.kai.custom.tools.SshConnectTool
import com.kai.custom.tools.SshDisconnectTool
import com.kai.custom.tools.WebSearchTool
import com.russhwolf.settings.BuildConfig
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import dev.spght.encryptedprefs.EncryptedSharedPreferences
import dev.spght.encryptedprefs.MasterKey
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.android.Android
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_create_calendar_event_description
import kai.composeapp.generated.resources.tool_create_calendar_event_name
import kai.composeapp.generated.resources.tool_get_battery_info_description
import kai.composeapp.generated.resources.tool_get_battery_info_name
import kai.composeapp.generated.resources.tool_get_device_info_description
import kai.composeapp.generated.resources.tool_get_device_info_name
import kai.composeapp.generated.resources.tool_get_gps_location_description
import kai.composeapp.generated.resources.tool_get_gps_location_name
import kai.composeapp.generated.resources.tool_get_network_info_description
import kai.composeapp.generated.resources.tool_get_network_info_name
import kai.composeapp.generated.resources.tool_get_wifi_info_description
import kai.composeapp.generated.resources.tool_get_wifi_info_name
import kai.composeapp.generated.resources.tool_list_installed_apps_description
import kai.composeapp.generated.resources.tool_list_installed_apps_name
import kai.composeapp.generated.resources.tool_open_file_description
import kai.composeapp.generated.resources.tool_open_file_name
import kai.composeapp.generated.resources.tool_read_clipboard_description
import kai.composeapp.generated.resources.tool_read_clipboard_name
import kai.composeapp.generated.resources.tool_read_contacts_description
import kai.composeapp.generated.resources.tool_read_contacts_name
import kai.composeapp.generated.resources.tool_send_notification_description
import kai.composeapp.generated.resources.tool_send_notification_name
import kai.composeapp.generated.resources.tool_set_alarm_description
import kai.composeapp.generated.resources.tool_set_alarm_name
import kotlinx.coroutines.Dispatchers
import org.koin.java.KoinJavaComponent.inject
import kotlin.coroutines.CoroutineContext

actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Android) {
    config(this)
}

actual fun getBackgroundDispatcher(): CoroutineContext = Dispatchers.IO

actual fun onDragAndDropEventDropped(event: DragAndDropEvent): PlatformFile? = null

actual val BackIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack

actual val currentPlatform: Platform = Platform.Mobile.Android

actual val defaultUiScale: Float = 1.0f

actual val isEmailSupported: Boolean = true

// Evaluated lazily because we need the Koin-injected Context. Whether READ_SMS
// is declared in the merged manifest is a build-time property (foss flavor adds
// it), so caching the first result is safe for the process lifetime. The try/catch
// guards unit-test environments that may call `getPlatformToolDefinitions()` before
// Koin has been started.
actual val isSmsSupported: Boolean by lazy {
    try {
        val context: Context by inject(Context::class.java)
        context.declaresReadSms()
    } catch (_: Throwable) {
        false
    }
}

// Same lazy pattern as `isSmsSupported`: probe the merged manifest for the listener
// service. Foss flavor declares it.
actual val isNotificationsSupported: Boolean by lazy {
    try {
        val context: Context by inject(Context::class.java)
        context.declaresNotificationListener()
    } catch (_: Throwable) {
        false
    }
}

actual val isTelegramSupported: Boolean = true

actual val isWhatsAppSupported: Boolean = true

actual val isSplinterlandsSupported: Boolean = true

actual val isShizukuSupported: Boolean = true

actual val isRootSupported: Boolean = true

actual fun isRootAvailable(): Boolean = com.kai.custom.root.RootManager.isAvailable

actual fun isShizukuPermissionGranted(): Boolean = ShizukuManager.hasPermission

actual fun requestShizukuPermission(onGranted: (() -> Unit)?) {
    ShizukuManager.requestPermission(
        onResult = if (onGranted != null) {
            { granted -> if (granted) onGranted() }
        } else {
            null
        },
    )
}

actual fun getToolPermissionMap(): Map<String, List<String>> = mapOf(
    "get_gps_location" to listOf(Manifest.permission.ACCESS_FINE_LOCATION),
    "read_contacts" to listOf(Manifest.permission.READ_CONTACTS),
    "get_wifi_info" to listOf(Manifest.permission.ACCESS_FINE_LOCATION),
    "read_calendar_events" to listOf(Manifest.permission.READ_CALENDAR),
    "write_contact" to listOf(Manifest.permission.WRITE_CONTACTS),
    "get_phone_state" to listOf(Manifest.permission.READ_PHONE_STATE),
    "send_notification" to listOf(Manifest.permission.POST_NOTIFICATIONS),
    "create_calendar_event" to listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
)

actual suspend fun compressImageBytes(bytes: ByteArray, mimeType: String): ByteArray {
    if (!mimeType.startsWith("image/")) return bytes
    return try {
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val maxDim = 1024
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            bitmap.scale(newWidth, newHeight)
        } else {
            bitmap
        }
        val outputStream = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        outputStream.toByteArray()
    } catch (_: Exception) {
        bytes
    }
}

actual fun getAppFilesDirectory(): String {
    val context: Context by inject(Context::class.java)
    return context.filesDir.absolutePath
}

// Uses dev.spght:encryptedprefs-ktx — a maintained community fork of the deprecated
// androidx.security:security-crypto. We keep application-level encryption because
// secure settings store API keys, email passwords, and conversation encryption keys.
actual fun createSecureSettings(): Settings {
    val context: Context by inject(Context::class.java)
    return try {
        SharedPreferencesSettings(createEncryptedPrefs(context))
    } catch (_: Exception) {
        // AEADBadTagException occurs when Android Auto Backup restores the encrypted
        // prefs file but the Keystore key is hardware-bound and doesn't transfer.
        // Delete the corrupted file and recreate fresh encrypted prefs.
        context.deleteSharedPreferences("kai_secure_prefs")
        SharedPreferencesSettings(createEncryptedPrefs(context))
    }
}

private fun createEncryptedPrefs(context: Context): android.content.SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        context,
        "kai_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}

actual fun createLegacySettings(): Settings? = try {
    Settings()
} catch (_: Exception) {
    null
}

actual fun createSshConnectionManager(): SshConnectionManager = AndroidSshConnectionManager()

actual fun getPlatformToolDefinitions(): List<ToolInfo> = buildList {
    val context: Context by inject(Context::class.java)

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    addAll(CommonTools.commonToolDefinitions)
    add(
        ToolInfo(
            id = "send_notification",
            name = "Send Notification",
            description = "Send a push notification to the device",
            nameRes = Res.string.tool_send_notification_name,
            descriptionRes = Res.string.tool_send_notification_description,
            isEnabled = NotificationPermissionController().hasPermission(),
        ),
    )
    add(
        ToolInfo(
            id = "create_calendar_event",
            name = "Create Calendar Event",
            description = "Create a calendar event on the user's device",
            nameRes = Res.string.tool_create_calendar_event_name,
            descriptionRes = Res.string.tool_create_calendar_event_description,
            isEnabled = CalendarPermissionController().hasPermission(),
        ),
    )
    add(
        ToolInfo(
            id = "set_alarm",
            name = "Set Alarm",
            description = "Set an alarm or countdown timer on the device",
            nameRes = Res.string.tool_set_alarm_name,
            descriptionRes = Res.string.tool_set_alarm_description,
        ),
    )
    add(
        ToolInfo(
            id = "open_file",
            name = "Open File",
            description = "Open sandbox files in your default Android app",
            nameRes = Res.string.tool_open_file_name,
            descriptionRes = Res.string.tool_open_file_description,
        ),
    )
    add(ReadFileTool.toolInfo)
    add(WriteFileTool.toolInfo)
    add(EditFileTool.toolInfo)
    add(GlobTool.toolInfo)
    add(GrepTool.toolInfo)
    add(ApplyPatchTool.toolInfo)
    add(TodoWriteTool.toolInfo)
    add(WebFetchTool.toolInfo)
    add(InternetSearchTool.toolInfo)
    add(SshCommandTool.toolInfo)
    add(SshConfigureHostTool.toolInfo)
    add(SshConnectTool.toolInfo)
    add(SshDisconnectTool.toolInfo)
    // Telegram tools
    addAll(com.kai.custom.tools.telegramToolDefinitions)
    // Phone tools — full device access
    add(PhoneTools.gpsLocationToolInfo.copy(isEnabled = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)))
    add(PhoneTools.readContactsToolInfo.copy(isEnabled = hasPermission(Manifest.permission.READ_CONTACTS)))
    add(PhoneTools.deviceInfoToolInfo)
    add(PhoneTools.batteryInfoToolInfo)
    add(PhoneTools.networkInfoToolInfo)
    add(
        PhoneTools.wifiInfoToolInfo.copy(
            isEnabled = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION),
        ),
    )
    add(PhoneTools.clipboardToolInfo)
    add(PhoneTools.installedAppsToolInfo)
    add(PhoneTools.readCalendarToolInfo.copy(isEnabled = hasPermission(Manifest.permission.READ_CALENDAR)))
    add(PhoneTools.writeContactToolInfo.copy(isEnabled = hasPermission(Manifest.permission.WRITE_CONTACTS)))
    add(PhoneTools.getPhoneStateToolInfo.copy(isEnabled = hasPermission(Manifest.permission.READ_PHONE_STATE)))
    add(PhoneTools.scanBluetoothToolInfo)
    add(PhoneTools.listMediaToolInfo)
    add(PhoneTools.readLogsToolInfo)
    // SMS tools are intentionally absent here: availability is driven by the Agent-tab
    // master toggles (isSmsEnabled / isSmsSendEnabled) plus the FOSS-only `isSmsSupported`
    // check in `getAvailableTools()`. Listing per-tool toggles in the Tools tab was dead
    // UI — `getAvailableTools()` never consulted them.
}

actual fun getAvailableTools(): List<Tool> {
    val context: Context by inject(Context::class.java)
    val appSettings: AppSettings by inject(AppSettings::class.java)
    val memoryStore: MemoryStore by inject(MemoryStore::class.java)
    val sandboxController: SandboxController by inject(SandboxController::class.java)
    val mcpServerManager: McpServerManager by inject(McpServerManager::class.java)
    val taskStore: TaskStore by inject(TaskStore::class.java)
    val calendarPermissionController: CalendarPermissionController by inject(CalendarPermissionController::class.java)
    val calendarRepository = CalendarRepository(context, calendarPermissionController)
    val emailStore: EmailStore by inject(EmailStore::class.java)

    val allTools = buildList {
        if (appSettings.isMemoryEnabled()) {
            addAll(CommonTools.getMemoryTools(memoryStore))
            addAll(CommonTools.getKgTools(memoryStore))
            addAll(CommonTools.getDiaryTools(memoryStore))
            addAll(listOf(HeartbeatTools.getPromoteLearningTool(memoryStore, appSettings)))
        }
        if (appSettings.isSchedulingEnabled()) {
            addAll(SchedulingTools.getSchedulingTools(taskStore))
        }
        if (appSettings.isToolEnabled(CommonTools.localTimeTool.schema.name)) {
            add(CommonTools.localTimeTool)
        }

        if (appSettings.isToolEnabled(CommonTools.ipLocationTool.schema.name)) {
            add(CommonTools.ipLocationTool)
        }

        if (appSettings.isToolEnabled(WebSearchTool.schema.name)) {
            add(WebSearchTool)
        }

        if (appSettings.isToolEnabled("send_notification")) {
            val notificationPermissionController: NotificationPermissionController by inject(NotificationPermissionController::class.java)
            val notificationHelper = NotificationHelper(context, notificationPermissionController)

            add(
                object : Tool {
                    override val schema = ToolSchema(
                        "send_notification",
                        "Send a push notification to the device",
                        mapOf(
                            "title" to ParameterSchema("string", "Notification title", false),
                            "message" to ParameterSchema("string", "Notification content/body", true),
                        ),
                    )

                    override suspend fun execute(args: Map<String, Any>): Any {
                        val title = args["title"] as? String ?: "K.Ai"
                        val message = args["message"] as? String
                            ?: return mapOf("success" to false, "error" to "Message is required")

                        return when (val result = notificationHelper.sendNotification(title, message)) {
                            is NotificationResult.Success -> mapOf(
                                "success" to true,
                                "notification_id" to result.notificationId,
                                "message" to "Notification sent successfully",
                            )

                            is NotificationResult.Error -> mapOf(
                                "success" to false,
                                "error" to result.message,
                            )
                        }
                    }
                },
            )
        }

        if (appSettings.isToolEnabled("create_calendar_event")) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        "create_calendar_event",
                        "Create a calendar event on the user's device",
                        mapOf(
                            "title" to ParameterSchema("string", "Event title", true),
                            "start_time" to ParameterSchema("string", "Start time as ISO 8601, e.g. '2024-03-15T14:30:00+02:00'. Naive (no offset) is treated as user's local time.", true),
                            "end_time" to ParameterSchema("string", "End time, same format as start_time. Defaults to 1 hour after start.", false),
                            "description" to ParameterSchema("string", "Event notes or description", false),
                            "location" to ParameterSchema("string", "Event location", false),
                            "all_day" to ParameterSchema("boolean", "Whether this is an all-day event", false),
                            "reminder_minutes" to ParameterSchema("integer", "Minutes before event to send reminder (default: 15)", false),
                        ),
                    )

                    override suspend fun execute(args: Map<String, Any>): Any {
                        val title = args["title"] as? String
                            ?: return mapOf("success" to false, "error" to "Title is required")
                        val startTime = args["start_time"] as? String
                            ?: return mapOf("success" to false, "error" to "Start time is required")
                        val endTime = args["end_time"] as? String
                        val description = args["description"] as? String
                        val location = args["location"] as? String
                        val allDay = (args["all_day"] as? Boolean) ?: false
                        val reminderMinutes = (args["reminder_minutes"] as? Number)?.toInt() ?: 15

                        return when (
                            val result = calendarRepository.createEvent(
                                title = title,
                                startTimeIso = startTime,
                                endTimeIso = endTime,
                                description = description,
                                location = location,
                                allDay = allDay,
                                reminderMinutes = reminderMinutes,
                            )
                        ) {
                            is CalendarResult.Success -> mapOf(
                                "success" to true,
                                "event_id" to result.eventId,
                                "title" to result.title,
                                "scheduled_for" to result.startTime,
                                "message" to "Event '${result.title}' created successfully for ${result.startTime}",
                            )

                            is CalendarResult.Error -> mapOf(
                                "success" to false,
                                "error" to result.message,
                            )
                        }
                    }
                },
            )
        }

        if (appSettings.isToolEnabled("set_alarm")) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        "set_alarm",
                        "Set an alarm or countdown timer on the device. For alarms provide hour and minutes. For countdown timers provide duration_seconds.",
                        mapOf(
                            "hour" to ParameterSchema("integer", "Hour of the alarm in 24-hour format (0-23)", false),
                            "minutes" to ParameterSchema("integer", "Minutes of the alarm (0-59)", false),
                            "label" to ParameterSchema("string", "Label for the alarm or timer", false),
                            "duration_seconds" to ParameterSchema("integer", "Duration in seconds for a countdown timer", false),
                        ),
                    )

                    override suspend fun execute(args: Map<String, Any>): Any {
                        val hour = (args["hour"] as? Number)?.toInt()
                        val minutes = (args["minutes"] as? Number)?.toInt()
                        val label = args["label"] as? String
                        val durationSeconds = (args["duration_seconds"] as? Number)?.toInt()

                        val intent = if (durationSeconds != null) {
                            Intent(AlarmClock.ACTION_SET_TIMER).apply {
                                putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
                                if (label != null) putExtra(AlarmClock.EXTRA_MESSAGE, label)
                                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            }
                        } else if (hour != null && minutes != null) {
                            Intent(AlarmClock.ACTION_SET_ALARM).apply {
                                putExtra(AlarmClock.EXTRA_HOUR, hour)
                                putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                                if (label != null) putExtra(AlarmClock.EXTRA_MESSAGE, label)
                                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            }
                        } else {
                            return mapOf(
                                "success" to false,
                                "error" to "Provide either hour+minutes for an alarm or duration_seconds for a timer",
                            )
                        }

                        return try {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            if (durationSeconds != null) {
                                mapOf(
                                    "success" to true,
                                    "type" to "timer",
                                    "duration_seconds" to durationSeconds,
                                    "message" to "Timer set for $durationSeconds seconds",
                                )
                            } else {
                                mapOf(
                                    "success" to true,
                                    "type" to "alarm",
                                    "hour" to hour!!,
                                    "minutes" to minutes!!,
                                    "message" to "Alarm set for %02d:%02d".format(hour, minutes),
                                )
                            }
                        } catch (e: Exception) {
                            mapOf(
                                "success" to false,
                                "error" to (e.message ?: "Failed to set alarm"),
                            )
                        }
                    }
                },
            )
        }

        if (appSettings.isToolEnabled(CommonTools.openUrlTool.schema.name)) {
            add(CommonTools.openUrlTool)
        }

        if (appSettings.isToolEnabled(OpenFileTool.schema.name)) {
            add(OpenFileTool)
        }

        if (appSettings.isSandboxEnabled()) {
            add(ShellCommandTool)
            add(ProcessManagerTool)
            add(SpeakTextTool)
            add(OpenCodeTool)
            add(ReadFileTool)
            add(WriteFileTool)
            add(EditFileTool)
            add(GlobTool)
            add(GrepTool)
            add(ApplyPatchTool)
            add(TodoWriteTool)
            add(WebFetchTool)
            add(InternetSearchTool)
        }

        if (appSettings.isSshEnabled()) {
            if (appSettings.isToolEnabled(SshCommandTool.schema.name)) {
                add(SshCommandTool)
            }
            if (appSettings.isToolEnabled(SshConfigureHostTool.schema.name)) {
                add(SshConfigureHostTool)
            }
            if (appSettings.isToolEnabled(SshConnectTool.schema.name)) {
                add(SshConnectTool)
            }
            if (appSettings.isToolEnabled(SshDisconnectTool.schema.name)) {
                add(SshDisconnectTool)
            }
        }

        if (appSettings.isToolEnabled(AdbTool.schema.name)) {
            add(AdbTool)
        }

        if (appSettings.isRootEnabled() && RootManager.isAvailable) {
            add(RootTool)
        }

        if (appSettings.isEmailEnabled()) {
            addAll(EmailTools.getEmailTools(emailStore))
        }

        // SMS read tools: triple-gated. `isSmsSupported` is only true on FOSS builds
        // (READ_SMS declared in merged manifest). `isSmsEnabled()` is the user toggle.
        // `hasPermission()` catches runtime revocation.
        val smsReaderForTools: SmsReader? = if (isSmsSupported) {
            val smsReader: SmsReader by inject(SmsReader::class.java)
            smsReader
        } else {
            null
        }
        if (smsReaderForTools != null && appSettings.isSmsEnabled() && smsReaderForTools.hasPermission()) {
            val smsStore: SmsStore by inject(SmsStore::class.java)
            addAll(SmsTools.getSmsReadTools(smsStore, smsReaderForTools))
        }

        // SMS send tools: independently gated on the Send toggle + SEND_SMS permission.
        // These only *stage* drafts — actual sending is user-triggered via the review banner.
        if (smsReaderForTools != null && appSettings.isSmsSendEnabled()) {
            val smsSender: SmsSender by inject(SmsSender::class.java)
            if (smsSender.hasPermission()) {
                val smsDraftStore: SmsDraftStore by inject(SmsDraftStore::class.java)
                addAll(SmsTools.getSmsSendTools(smsDraftStore, smsReaderForTools, smsSender))
            }
        }

        // Notification tools: triple-gated. `isNotificationsSupported` is FOSS-only
        // (listener service declared in merged manifest). `isNotificationsEnabled()`
        // is the user toggle. `hasAccess()` catches system-level revocation.
        if (isNotificationsSupported && appSettings.isNotificationsEnabled()) {
            val notificationReader: NotificationReader by inject(NotificationReader::class.java)
            if (notificationReader.hasAccess()) {
                val notificationStore: NotificationStore by inject(NotificationStore::class.java)
                addAll(NotificationTools.getNotificationTools(notificationStore, notificationReader))
            }
        }

        // Telegram tools
        if (isTelegramSupported) {
            val telegramStore: TelegramStore by inject(TelegramStore::class.java)
            val telegramPoller: com.kai.custom.telegram.TelegramPoller by inject(com.kai.custom.telegram.TelegramPoller::class.java)
            if (telegramStore.isTelegramEnabled() && telegramStore.getBotToken().isNotBlank()) {
                addAll(com.kai.custom.tools.getTelegramTools(telegramStore, telegramPoller))
            }
        }

        // WhatsApp tools
        if (isWhatsAppSupported) {
            val whatsAppStore: com.kai.custom.data.WhatsAppStore by inject(com.kai.custom.data.WhatsAppStore::class.java)
            val whatsAppPoller: com.kai.custom.whatsapp.WhatsAppPoller by inject(com.kai.custom.whatsapp.WhatsAppPoller::class.java)
            if (whatsAppStore.isWhatsAppEnabled() && whatsAppStore.isWhatsAppInstalled() && whatsAppStore.isWhatsAppAuthenticated()) {
                addAll(com.kai.custom.tools.getWhatsAppTools(whatsAppStore, whatsAppPoller))
            }
        }

        // ===================== PHONE TOOLS =====================
        // GPS Location
        if (appSettings.isToolEnabled(PhoneTools.gpsLocationToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "get_gps_location",
                        description = "Get precise GPS location coordinates (latitude, longitude, accuracy)",
                        parameters = emptyMap(),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            return mapOf("success" to false, "error" to "Location permission not granted. Grant it in Settings > Apps > Kai > Permissions.")
                        }
                        return try {
                            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager

                            @Suppress("DEPRECATION")
                            val provider = locationManager.getBestProvider(android.location.Criteria().apply { accuracy = android.location.Criteria.ACCURACY_FINE }, true)
                            if (provider == null) {
                                return mapOf("success" to false, "error" to "No location provider available. Enable GPS or network location.")
                            }
                            val location = locationManager.getLastKnownLocation(provider)
                            if (location != null) {
                                mapOf(
                                    "success" to true,
                                    "latitude" to location.latitude,
                                    "longitude" to location.longitude,
                                    "accuracy" to location.accuracy,
                                    "altitude" to location.altitude,
                                    "provider" to provider,
                                )
                            } else {
                                // Request a single location update
                                val latch = java.util.concurrent.CountDownLatch(1)
                                var result: Map<String, Any> = mapOf("success" to false, "error" to "Could not get location")

                                @Suppress("DEPRECATION")
                                val locationListener = object : android.location.LocationListener {
                                    override fun onLocationChanged(loc: android.location.Location) {
                                        result = mapOf(
                                            "success" to true,
                                            "latitude" to loc.latitude,
                                            "longitude" to loc.longitude,
                                            "accuracy" to loc.accuracy,
                                            "altitude" to loc.altitude,
                                            "provider" to provider,
                                        )
                                        latch.countDown()
                                    }

                                    @Deprecated("Deprecated in Java")
                                    override fun onStatusChanged(p0: String?, p1: Int, p2: android.os.Bundle?) {}
                                    override fun onProviderEnabled(p0: String) {}
                                    override fun onProviderDisabled(p0: String) {}
                                }
                                locationManager.requestLocationUpdates(provider, 0L, 0f, locationListener)
                                latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
                                locationManager.removeUpdates(locationListener)
                                latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
                                result
                            }
                        } catch (e: Exception) {
                            mapOf("success" to false, "error" to "Failed to get location: ${e.message}")
                        }
                    }
                },
            )
        }

        // Read Contacts
        if (appSettings.isToolEnabled(PhoneTools.readContactsToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "read_contacts",
                        description = "Search and read contacts from the device phonebook. Call with a search query to find specific contacts, or empty to list all.",
                        parameters = mapOf(
                            "query" to ParameterSchema("string", "Optional search query to filter contacts by name", false),
                            "limit" to ParameterSchema("integer", "Maximum number of contacts to return (default 50)", false),
                        ),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                            return mapOf("success" to false, "error" to "Contacts permission not granted. Grant it in Settings > Apps > Kai > Permissions.")
                        }
                        return try {
                            val query = args["query"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                            val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 200) ?: 50
                            val uri = ContactsContract.Contacts.CONTENT_URI
                            val projection = arrayOf(
                                ContactsContract.Contacts._ID,
                                ContactsContract.Contacts.DISPLAY_NAME,
                                ContactsContract.Contacts.HAS_PHONE_NUMBER,
                            )
                            val selection = query?.let {
                                "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?"
                            }
                            val selectionArgs = query?.let { arrayOf("%$it%") }
                            val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, "${ContactsContract.Contacts.DISPLAY_NAME} ASC LIMIT $limit")
                            val contacts = mutableListOf<Map<String, Any>>()
                            cursor?.use { c ->
                                while (c.moveToNext()) {
                                    val id = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                                    val name = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
                                    val hasPhone = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)).toIntOrNull() ?: 0
                                    var phoneNumber: String? = null
                                    if (hasPhone > 0) {
                                        val phoneCursor = context.contentResolver.query(
                                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                            arrayOf(id),
                                            null,
                                        )
                                        phoneCursor?.use { pc ->
                                            if (pc.moveToFirst()) {
                                                phoneNumber = pc.getString(pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                                            }
                                        }
                                    }
                                    contacts.add(mapOf("id" to id, "name" to name, "phone" to (phoneNumber ?: "")))
                                }
                            }
                            mapOf("success" to true, "contacts" to contacts, "count" to contacts.size)
                        } catch (e: Exception) {
                            mapOf("success" to false, "error" to "Failed to read contacts: ${e.message}")
                        }
                    }
                },
            )
        }

        // Device Info
        if (appSettings.isToolEnabled(PhoneTools.deviceInfoToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "get_device_info",
                        description = "Get detailed device information including model, Android version, and hardware specs",
                        parameters = emptyMap(),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any {
                        val ramInfo = try {
                            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                            val mi = android.app.ActivityManager.MemoryInfo()
                            activityManager.getMemoryInfo(mi)
                            mapOf("total_ram_gb" to "%.2f".format(mi.totalMem.toDouble() / 1_073_741_824), "available_ram_gb" to "%.2f".format(mi.availMem.toDouble() / 1_073_741_824))
                        } catch (_: Exception) {
                            emptyMap<String, String>()
                        }
                        val storageInfo = try {
                            val stat = android.os.StatFs(Environment.getDataDirectory().path)
                            val total = stat.totalBytes
                            val free = stat.availableBytes
                            mapOf("total_storage_gb" to "%.2f".format(total.toDouble() / 1_073_741_824), "free_storage_gb" to "%.2f".format(free.toDouble() / 1_073_741_824))
                        } catch (_: Exception) {
                            emptyMap<String, String>()
                        }
                        return mapOf(
                            "success" to true,
                            "device" to mapOf(
                                "manufacturer" to Build.MANUFACTURER,
                                "model" to Build.MODEL,
                                "brand" to Build.BRAND,
                                "product" to Build.PRODUCT,
                                "hardware" to Build.HARDWARE,
                            ),
                            "os" to mapOf(
                                "android_version" to Build.VERSION.RELEASE,
                                "sdk_level" to Build.VERSION.SDK_INT,
                                "security_patch" to (Build.VERSION.SECURITY_PATCH ?: "unknown"),
                            ),
                            "memory" to ramInfo,
                            "storage" to storageInfo,
                            "display" to mapOf(
                                "density" to context.resources.displayMetrics.densityDpi,
                                "width_px" to context.resources.displayMetrics.widthPixels,
                                "height_px" to context.resources.displayMetrics.heightPixels,
                            ),
                        )
                    }
                },
            )
        }

        // Battery Info
        if (appSettings.isToolEnabled(PhoneTools.batteryInfoToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "get_battery_info",
                        description = "Get battery level, charging status, and temperature",
                        parameters = emptyMap(),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any {
                        return try {
                            val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                            if (batteryIntent == null) {
                                return mapOf("success" to false, "error" to "Could not read battery state")
                            }
                            val level = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                            val scale = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                            val levelPercent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
                            val status = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
                            val temperatureCelsius = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
                            val voltageMv = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0)
                            val health = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, -1)
                            val healthStr = when (health) {
                                android.os.BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                                android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                                android.os.BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                                android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                                android.os.BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
                                else -> "unknown"
                            }
                            mapOf(
                                "success" to true,
                                "level_percent" to levelPercent,
                                "is_charging" to isCharging,
                                "temperature_celsius" to temperatureCelsius,
                                "voltage_mv" to voltageMv,
                                "health" to healthStr,
                            )
                        } catch (e: Exception) {
                            mapOf("success" to false, "error" to "Failed to get battery info: ${e.message}")
                        }
                    }
                },
            )
        }

        // Network Info
        if (appSettings.isToolEnabled(PhoneTools.networkInfoToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "get_network_info",
                        description = "Get current network connectivity details (WiFi/cellular status, IP, signal strength)",
                        parameters = emptyMap(),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any = try {
                        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                        val network = connectivityManager.activeNetwork
                        val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }
                        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                        val isEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
                        val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                        val isMetered = connectivityManager.isActiveNetworkMetered
                        // Try to get IP
                        var ipAddress = ""
                        try {
                            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                            while (interfaces.hasMoreElements()) {
                                val intf = interfaces.nextElement()
                                if (intf.isUp && !intf.isLoopback) {
                                    val addrs = intf.inetAddresses
                                    while (addrs.hasMoreElements()) {
                                        val addr = addrs.nextElement()
                                        if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                                            ipAddress = addr.hostAddress ?: ""
                                            break
                                        }
                                    }
                                    if (ipAddress.isNotEmpty()) break
                                }
                            }
                        } catch (_: Exception) {}
                        mapOf(
                            "success" to true,
                            "is_connected" to (network != null),
                            "has_internet" to hasInternet,
                            "transport" to when {
                                isWifi -> "wifi"
                                isCellular -> "cellular"
                                isEthernet -> "ethernet"
                                else -> "other"
                            },
                            "is_vpn" to isVpn,
                            "is_metered" to isMetered,
                            "ip_address" to ipAddress,
                        )
                    } catch (e: Exception) {
                        mapOf("success" to false, "error" to "Failed to get network info: ${e.message}")
                    }
                },
            )
        }

        // WiFi Info
        if (appSettings.isToolEnabled(PhoneTools.wifiInfoToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "get_wifi_info",
                        description = "Get detailed WiFi connection information including SSID, signal strength, and frequency",
                        parameters = emptyMap(),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return mapOf("success" to false, "error" to "Location permission is required to read WiFi details. Grant location permission in Settings.")
                        }
                        return try {
                            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

                            @Suppress("DEPRECATION")
                            val wifiInfo = wifiManager.connectionInfo
                            if (wifiInfo == null || wifiInfo.networkId == -1) {
                                return mapOf("success" to true, "connected" to false, "message" to "Not connected to WiFi")
                            }
                            val ssid = wifiInfo.ssid?.removeSurrounding("\"") ?: ""
                            val bssid = wifiInfo.bssid ?: ""
                            val rssi = wifiInfo.rssi
                            val frequency = wifiInfo.frequency
                            val linkSpeed = wifiInfo.linkSpeed

                            @Suppress("DEPRECATION")
                            val signalBars = WifiManager.calculateSignalLevel(rssi, 5)
                            mapOf(
                                "success" to true,
                                "connected" to true,
                                "ssid" to ssid,
                                "bssid" to bssid,
                                "signal_strength_dbm" to rssi,
                                "signal_bars" to signalBars,
                                "frequency_mhz" to frequency,
                                "link_speed_mbps" to linkSpeed,
                            )
                        } catch (e: Exception) {
                            mapOf("success" to false, "error" to "Failed to get WiFi info: ${e.message}")
                        }
                    }
                },
            )
        }

        // Clipboard
        if (appSettings.isToolEnabled(PhoneTools.clipboardToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "read_clipboard",
                        description = "Read the current content of the system clipboard",
                        parameters = emptyMap(),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any {
                        return try {
                            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = clipboardManager.primaryClip
                            if (clip == null || clip.itemCount == 0) {
                                return mapOf("success" to true, "content" to "", "has_content" to false)
                            }
                            val item = clip.getItemAt(0)
                            val text = item.text?.toString()
                            val uri = item.uri?.toString()
                            mapOf(
                                "success" to true,
                                "content" to (text ?: uri ?: ""),
                                "has_content" to true,
                                "mime_type" to clip.description?.takeIf { it.mimeTypeCount > 0 }?.getMimeType(0),
                            )
                        } catch (e: Exception) {
                            mapOf("success" to false, "error" to "Failed to read clipboard: ${e.message}")
                        }
                    }
                },
            )
        }

        // Installed Apps
        if (appSettings.isToolEnabled(PhoneTools.installedAppsToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "list_installed_apps",
                        description = "List all installed applications on the device. Optionally filter by package name or keyword.",
                        parameters = mapOf(
                            "query" to ParameterSchema("string", "Optional search query to filter apps by name or package", false),
                            "limit" to ParameterSchema("integer", "Maximum number of apps to return (default 100)", false),
                        ),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any {
                        return try {
                            val query = args["query"]?.toString()?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                            val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 500) ?: 100
                            val pm = context.packageManager
                            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                            val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
                            } else {
                                @Suppress("DEPRECATION")
                                pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
                            }
                            val packages = pm.getInstalledPackages(0)
                            val appMap = packages.mapNotNull { pkg ->
                                try {
                                    val info = pkg.applicationInfo ?: return@mapNotNull null
                                    val appName = pm.getApplicationLabel(info).toString()
                                    val isSystem = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                                    val hasLauncher = activities.any { it.activityInfo.packageName == pkg.packageName }
                                    Triple(pkg.packageName, appName, mapOf("is_system" to isSystem, "has_launcher" to hasLauncher, "version_name" to (pkg.versionName ?: "")))
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            val filtered = if (query != null) {
                                appMap.filter { (pkg, name, _) -> pkg.contains(query) || name.lowercase().contains(query) }
                            } else {
                                appMap
                            }
                            val sorted = filtered.sortedBy { (_, name, _) -> name.lowercase() }.take(limit)
                            mapOf(
                                "success" to true,
                                "apps" to sorted.map { (pkg, name, meta) ->
                                    mapOf("package_name" to pkg, "name" to name) + meta
                                },
                                "total_installed" to appMap.size,
                                "count" to sorted.size,
                            )
                        } catch (e: Exception) {
                            mapOf("success" to false, "error" to "Failed to list apps: ${e.message}")
                        }
                    }
                },
            )
        }

        // ── Read Calendar Events ──
        if (appSettings.isToolEnabled(PhoneTools.readCalendarToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "read_calendar_events",
                        description = "Read calendar events from the device calendar within an optional date range",
                        parameters = mapOf(
                            "start_date" to ParameterSchema("string", "Start date (ISO 8601, e.g. '2026-01-01'). Default: 7 days ago", false),
                            "end_date" to ParameterSchema("string", "End date (ISO 8601). Default: 30 days from now", false),
                            "max_events" to ParameterSchema("integer", "Maximum events to return (default 50)", false),
                        ),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                            return mapOf("success" to false, "error" to "Calendar permission not granted")
                        }
                        return try {
                            val startStr = args["start_date"]?.toString()
                            val endStr = args["end_date"]?.toString()
                            val maxEvents = (args["max_events"] as? Number)?.toInt()?.coerceIn(1, 200) ?: 50
                            val startMillis = startStr?.let { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(it)?.time }
                                ?: (System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)
                            val endMillis = endStr?.let { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(it)?.time }
                                ?: (System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
                            val uri = CalendarContract.Events.CONTENT_URI
                            val projection = arrayOf(
                                CalendarContract.Events._ID,
                                CalendarContract.Events.TITLE,
                                CalendarContract.Events.DESCRIPTION,
                                CalendarContract.Events.DTSTART,
                                CalendarContract.Events.DTEND,
                                CalendarContract.Events.EVENT_LOCATION,
                                CalendarContract.Events.ALL_DAY,
                            )
                            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
                            val cursor = context.contentResolver.query(uri, projection, selection, arrayOf(startMillis.toString(), endMillis.toString()), "${CalendarContract.Events.DTSTART} ASC")
                            val events = mutableListOf<Map<String, Any>>()
                            cursor?.use { c ->
                                var count = 0
                                while (c.moveToNext() && count < maxEvents) {
                                    val title = c.getString(c.getColumnIndexOrThrow(CalendarContract.Events.TITLE)) ?: "(no title)"
                                    val desc = c.getString(c.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)) ?: ""
                                    val loc = c.getString(c.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)) ?: ""
                                    val dtStart = c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                                    val dtEnd = c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events.DTEND))
                                    val allDay = c.getInt(c.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)) == 1
                                    events.add(mapOf("title" to title, "description" to desc, "location" to loc, "start_time" to dtStart, "end_time" to dtEnd, "all_day" to allDay))
                                    count++
                                }
                            }
                            mapOf("success" to true, "events" to events, "count" to events.size)
                        } catch (e: Exception) {
                            mapOf("success" to false, "error" to "Failed to read calendar: ${e.message}")
                        }
                    }
                },
            )
        }

        // ── Write Contact ──
        if (appSettings.isToolEnabled(PhoneTools.writeContactToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "write_contact",
                        description = "Create a new contact in the device phonebook",
                        parameters = mapOf(
                            "name" to ParameterSchema("string", "Contact display name", true),
                            "phone" to ParameterSchema("string", "Phone number", false),
                            "email" to ParameterSchema("string", "Email address", false),
                        ),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                            return mapOf("success" to false, "error" to "Write contacts permission not granted")
                        }
                        return try {
                            val name = args["name"]?.toString() ?: return mapOf("success" to false, "error" to "Name is required")
                            val phone = args["phone"]?.toString()
                            val email = args["email"]?.toString()
                            val ops = java.util.ArrayList<android.content.ContentProviderOperation>()
                            val rawContactId = ops.size
                            ops.add(android.content.ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI).withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null).withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build())
                            ops.add(
                                android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build(),
                            )
                            if (phone != null) {
                                ops.add(
                                    android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build(),
                                )
                            }
                            if (email != null) {
                                ops.add(
                                    android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                                        .withValue(ContactsContract.CommonDataKinds.Email.DATA, email)
                                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_HOME).build(),
                                )
                            }
                            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                            mapOf("success" to true, "message" to "Contact '$name' created")
                        } catch (e: Exception) {
                            mapOf("success" to false, "error" to "Failed to create contact: ${e.message}")
                        }
                    }
                },
            )
        }

        // ── Get Phone State ──
        if (appSettings.isToolEnabled(PhoneTools.getPhoneStateToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "get_phone_state",
                        description = "Get cellular network info: operator, signal strength, network type",
                        parameters = emptyMap(),
                    )
                    @Suppress("DEPRECATION")
                    override suspend fun execute(args: Map<String, Any>): Any {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                            return mapOf("success" to false, "error" to "Phone state permission not granted")
                        }
                        return try {
                            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                            mapOf(
                                "success" to true,
                                "network_operator" to (tm.networkOperatorName ?: ""),
                                "network_country_iso" to (tm.networkCountryIso ?: ""),
                                "phone_type" to when (tm.phoneType) {
                                    TelephonyManager.PHONE_TYPE_GSM -> "gsm"
                                    TelephonyManager.PHONE_TYPE_CDMA -> "cdma"
                                    TelephonyManager.PHONE_TYPE_SIP -> "sip"
                                    else -> "unknown"
                                },
                                "sim_operator" to (tm.simOperatorName ?: ""),
                                "sim_country_iso" to (tm.simCountryIso ?: ""),
                                "data_state" to when (tm.dataState) {
                                    TelephonyManager.DATA_CONNECTED -> "connected"
                                    TelephonyManager.DATA_CONNECTING -> "connecting"
                                    TelephonyManager.DATA_DISCONNECTED -> "disconnected"
                                    TelephonyManager.DATA_SUSPENDED -> "suspended"
                                    else -> "unknown"
                                },
                                "roaming" to tm.isNetworkRoaming,
                            )
                        } catch (e: Exception) {
                            mapOf("success" to false, "error" to "Failed to get phone state: ${e.message}")
                        }
                    }
                },
            )
        }

        // ── Scan Bluetooth Devices ──
        if (appSettings.isToolEnabled(PhoneTools.scanBluetoothToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "scan_bluetooth_devices",
                        description = "List paired Bluetooth devices and scan for nearby devices",
                        parameters = mapOf(
                            "scan" to ParameterSchema("boolean", "Whether to perform a scan for new devices (default false)", false),
                        ),
                    )
                    @Suppress("DEPRECATION")
                    override suspend fun execute(args: Map<String, Any>): Any {
                        return try {
                            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
                            val btAdapter = bluetoothManager.adapter
                            if (btAdapter == null) {
                                return mapOf("success" to false, "error" to "Bluetooth not supported on this device")
                            }
                            val paired = btAdapter.bondedDevices?.map { device ->
                                mapOf("name" to (device.name ?: ""), "address" to device.address, "type" to device.type)
                            } ?: emptyList()
                            mapOf("success" to true, "paired_devices" to paired, "is_enabled" to btAdapter.isEnabled)
                        } catch (e: Exception) {
                            mapOf("success" to false, "error" to "Failed to scan Bluetooth: ${e.message}")
                        }
                    }
                },
            )
        }

        // ── List Media Files ──
        if (appSettings.isToolEnabled(PhoneTools.listMediaToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "list_media",
                        description = "List images, videos, and audio files on the device",
                        parameters = mapOf(
                            "type" to ParameterSchema("string", "Media type: 'image', 'video', 'audio', or 'all' (default 'all')", false),
                            "limit" to ParameterSchema("integer", "Max items per type (default 20)", false),
                        ),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any {
                        return try {
                            val mediaType = args["type"]?.toString()?.lowercase() ?: "all"
                            val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20
                            val results = mutableMapOf<String, Any>()
                            fun queryMedia(uri: android.net.Uri, sortField: String): List<Map<String, Any>> {
                                val list = mutableListOf<Map<String, Any>>()
                                val cursor = context.contentResolver.query(uri, arrayOf("_id", "_display_name", "_size", "date_added"), null, null, "$sortField DESC LIMIT $limit")
                                cursor?.use { c ->
                                    while (c.moveToNext()) {
                                        list.add(mapOf("name" to (c.getString(1) ?: ""), "size" to c.getLong(2), "date_added" to c.getLong(3)))
                                    }
                                }
                                return list
                            }
                            if (mediaType == "all" || mediaType == "image") results["images"] = queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "date_added")
                            if (mediaType == "all" || mediaType == "video") results["videos"] = queryMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "date_added")
                            if (mediaType == "all" || mediaType == "audio") results["audio"] = queryMedia(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "date_added")
                            results["success"] = true
                            results
                        } catch (e: Exception) {
                            mapOf("success" to false, "error" to "Failed to list media: ${e.message}")
                        }
                    }
                },
            )
        }

        // ── Read Device Logs ──
        if (appSettings.isToolEnabled(PhoneTools.readLogsToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "read_device_logs",
                        description = "Read recent system logs (logcat)",
                        parameters = mapOf(
                            "lines" to ParameterSchema("integer", "Number of recent log lines to return (default 100)", false),
                            "filter" to ParameterSchema("string", "Filter logs by tag or keyword", false),
                        ),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any = try {
                        val lines = (args["lines"] as? Number)?.toInt()?.coerceIn(10, 1000) ?: 100
                        val filter = args["filter"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                        val cmd = mutableListOf("logcat", "-d", "-t", lines.toString())
                        filter?.let {
                            cmd.add("-s")
                            cmd.add(it)
                        }
                        val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
                        val output = process.inputStream.bufferedReader().readText()
                        process.waitFor()
                        mapOf("success" to true, "logs" to output, "line_count" to output.lines().size)
                    } catch (e: Exception) {
                        mapOf("success" to false, "error" to "Failed to read logs: ${e.message}")
                    }
                },
            )
        }

        addAll(mcpServerManager.getEnabledMcpTools())
    }

    // Deduplicate by name — MCP servers may expose tools with the same
    // names as built-in tools (e.g. memory_store, search_memories).
    return allTools.distinctBy { it.schema.name }
}

actual fun openUrl(url: String): Boolean = try {
    val context: Context by inject(Context::class.java)
    val parsedUri = url.toUri()
    val intent = if (parsedUri.scheme == "file") {
        val file = java.io.File(parsedUri.path!!)
        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val mimeType = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension) ?: "*/*"
        Intent(Intent.ACTION_VIEW, contentUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(contentUri, mimeType)
        }
    } else {
        Intent(Intent.ACTION_VIEW, parsedUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    context.startActivity(intent)
    true
} catch (_: Exception) {
    false
}

actual fun decodeToImageBitmap(bytes: ByteArray): ImageBitmap? = try {
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
} catch (_: Exception) {
    null
}

@androidx.compose.runtime.Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

actual suspend fun saveFileToDevice(bytes: ByteArray, baseName: String, extension: String) {
    val file = FileKit.openFileSaver(suggestedName = baseName, defaultExtension = extension)
    file?.write(bytes)
}

actual fun openTtsSettings() {
    val context: Context by inject(Context::class.java)
    try {
        val intent = Intent("com.android.settings.TTS_SETTINGS")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent("android.settings.TTS_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            // TTS settings activity not available
        }
    }
}

actual fun openBatteryOptimizationSettings() {
    val context: Context by inject(Context::class.java)
    try {
        val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        // Battery optimization settings not available
    }
}

actual fun isBatteryOptimizationDisabled(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val context: Context by inject(Context::class.java)
    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Detects whether the app is running inside an Android emulator by checking known
 * emulator fingerprints, models, and manufacturer strings.
 */
private fun isEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT
    val model = Build.MODEL
    val manufacturer = Build.MANUFACTURER
    val product = Build.PRODUCT
    val brand = Build.BRAND
    val device = Build.DEVICE

    return fingerprint.startsWith("generic") ||
        fingerprint.startsWith("unknown") ||
        model.contains("google_sdk") ||
        model.contains("Emulator") ||
        model.contains("Android SDK built for x86") ||
        manufacturer.contains("Genymotion") ||
        (brand.startsWith("generic") && device.startsWith("generic")) ||
        "google_sdk" == product
}

actual fun defaultOpenAICompatibleBaseUrl(): String = if (isEmulator()) "http://10.0.2.2:11434/v1" else "http://localhost:11434/v1"
