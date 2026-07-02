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
import android.speech.RecognitionListener
import android.hardware.display.DisplayManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.FileOutputStream
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import com.kai.custom.data.DataRepository
import com.kai.custom.data.EmailStore
import com.kai.custom.data.MemoryStore
import com.kai.custom.data.NotificationStore
import com.kai.custom.data.PersonaManager
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
import com.kai.custom.tools.ActivityResultBridge
import com.kai.custom.tools.ApplyPatchTool
import com.kai.custom.tools.CalendarPermissionController
import com.kai.custom.tools.CalendarRepository
import com.kai.custom.tools.CalendarResult
import com.kai.custom.tools.CommonTools
import com.kai.custom.tools.EditFileTool
import com.kai.custom.tools.EmailTools
import com.kai.custom.tools.GlobTool
import com.kai.custom.tools.GrepTool
import com.kai.custom.tools.HeartbeatTools
import com.kai.custom.tools.InternetSearchTool
import com.kai.custom.tools.NotificationHelper
import com.kai.custom.tools.NotificationPermissionController
import com.kai.custom.tools.NotificationResult
import com.kai.custom.tools.NotificationTools
import com.kai.custom.tools.OpenCodeTool
import com.kai.custom.tools.OpenFileTool
import com.kai.custom.tools.PhoneTools
import com.kai.custom.tools.ProcessManagerTool
import com.kai.custom.tools.ReadFileTool
import com.kai.custom.tools.RootTool
import com.kai.custom.tools.SchedulingTools
import com.kai.custom.tools.ShellCommandTool
import com.kai.custom.tools.SmsTools
import com.kai.custom.tools.SpeakTextTool
import com.kai.custom.tools.SshCommandTool
import com.kai.custom.tools.SshConfigureHostTool
import com.kai.custom.tools.SshConnectTool
import com.kai.custom.tools.SshDisconnectTool
import com.kai.custom.tools.TodoWriteTool
import com.kai.custom.tools.ToolPermissionBridge
import com.kai.custom.tools.WebFetchTool
import com.kai.custom.tools.WebSearchTool
import com.kai.custom.tools.SandboxFileTransferTool
import com.kai.custom.tools.WriteFileTool
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
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    "set_gps_location" to listOf(Manifest.permission.ACCESS_FINE_LOCATION),
    "take_picture" to listOf(Manifest.permission.CAMERA),
    "hear_surroundings" to listOf(Manifest.permission.RECORD_AUDIO),
    "read_contacts" to listOf(Manifest.permission.READ_CONTACTS),
    "get_wifi_info" to listOf(Manifest.permission.ACCESS_FINE_LOCATION),
    "read_calendar_events" to listOf(Manifest.permission.READ_CALENDAR),
    "write_contact" to listOf(Manifest.permission.WRITE_CONTACTS),
    "get_phone_state" to listOf(Manifest.permission.READ_PHONE_STATE),
    "send_notification" to listOf(Manifest.permission.POST_NOTIFICATIONS),
    "create_calendar_event" to listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
    "list_media" to listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO),
    "scan_bluetooth_devices" to listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
)

actual fun keyCodeToName(keyCode: Int): String = android.view.KeyEvent.keyCodeToString(keyCode)

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

    fun hasPermission(permission: String): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

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
    add(SandboxFileTransferTool.toolInfo)
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
    add(PhoneTools.setGpsLocationToolInfo.copy(isEnabled = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) && isMockLocationConfigured()))
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
    add(PhoneTools.takePictureToolInfo.copy(isEnabled = hasPermission(Manifest.permission.CAMERA)))
    add(PhoneTools.hearSurroundingsToolInfo.copy(isEnabled = hasPermission(Manifest.permission.RECORD_AUDIO)))
    add(PhoneTools.screenshotToolInfo)
    add(PhoneTools.listActivitiesToolInfo)
    add(PhoneTools.launchActivityToolInfo)
    add(PhoneTools.modifySettingsToolInfo)
    add(PhoneTools.readScreenTextToolInfo)
    add(PhoneTools.navigateScreenToolInfo)
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
    val calendarRepository = CalendarRepository(context, calendarPermissionController, appSettings.getDefaultCalendarId())
    val emailStore: EmailStore by inject(EmailStore::class.java)
    val toolPermissionBridge: ToolPermissionBridge by inject(ToolPermissionBridge::class.java)
    val activityResultBridge: ActivityResultBridge by inject(ActivityResultBridge::class.java)

    val dataRepository: DataRepository by inject(DataRepository::class.java)
    val personaManager = PersonaManager(appSettings)

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
        if (appSettings.isToolEnabled(CommonTools.savePersonaToolSchema.name)) {
            add(CommonTools.savePersonaTool(appSettings, personaManager, memoryStore))
            add(CommonTools.switchPersonaTool(personaManager, dataRepository))
            add(CommonTools.listPersonasTool(personaManager))
            add(CommonTools.deletePersonaTool(personaManager))
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
            add(SandboxFileTransferTool)
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
            val whatsAppLifecycleManager: com.kai.custom.whatsapp.WhatsAppLifecycleManager by inject(com.kai.custom.whatsapp.WhatsAppLifecycleManager::class.java)
            if (whatsAppStore.isWhatsAppEnabled()) {
                if (whatsAppStore.isWhatsAppInstalled()) {
                    if (whatsAppStore.isWhatsAppAuthenticated()) {
                        addAll(com.kai.custom.tools.getWhatsAppTools(whatsAppStore, whatsAppPoller))
                    }
                }
                addAll(
                    com.kai.custom.tools.getWhatsAppAdminTools(
                        appSettings = appSettings,
                        restartBridge = { whatsAppLifecycleManager.restart() },
                        updateBridgeConfig = { whatsAppLifecycleManager.updateBridgeConfig() },
                    ),
                )
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
                                locationManager.requestLocationUpdates(provider, 0L, 0f, locationListener, android.os.Looper.getMainLooper())
                                latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
                                locationManager.removeUpdates(locationListener)
                                result
                            }
                        } catch (e: Exception) {
                            mapOf("success" to false, "error" to "Failed to get location: ${e.message}")
                        }
                    }
                },
            )
        }

        // Set GPS Location (mock)
        if (appSettings.isToolEnabled(PhoneTools.setGpsLocationToolInfo.id) && isMockLocationConfigured()) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "set_gps_location",
                        description = "Set a persistent mock GPS location on the device. Can optionally simulate walking/driving to a destination.",
                        parameters = mapOf(
                            "latitude" to ParameterSchema("number", "Target or starting latitude coordinate (e.g. 48.8566)", true),
                            "longitude" to ParameterSchema("number", "Target or starting longitude coordinate (e.g. 2.3522)", true),
                            "destination_latitude" to ParameterSchema("number", "Destination latitude (optional)", false),
                            "destination_longitude" to ParameterSchema("number", "Destination longitude (optional)", false),
                            "speed_kmh" to ParameterSchema("number", "Movement speed in km/h (optional, default 5.0)", false),
                        ),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any {
                        val latitude = (args["latitude"] as? Number)?.toDouble()
                        val longitude = (args["longitude"] as? Number)?.toDouble()
                        val destLat = (args["destination_latitude"] as? Number)?.toDouble()
                        val destLng = (args["destination_longitude"] as? Number)?.toDouble()
                        val speedKmh = (args["speed_kmh"] as? Number)?.toDouble()

                        if (latitude == null || longitude == null) {
                            return mapOf("success" to false, "error" to "latitude and longitude are required")
                        }
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            return mapOf("success" to false, "error" to "Location permission not granted. Grant it in Settings > Apps > Kai > Permissions.")
                        }

                        val resultMessage = com.kai.custom.tools.MockLocationController.startMocking(
                            context = context,
                            startLat = latitude,
                            startLng = longitude,
                            destLat = destLat,
                            destLng = destLng,
                            speedKmh = speedKmh,
                        )

                        return if (resultMessage.contains("not set as the mock location provider")) {
                            mapOf("success" to false, "error" to resultMessage)
                        } else {
                            mapOf("success" to true, "message" to resultMessage)
                        }
                    }
                },
            )
        }

        // Stop GPS Mocking
        if (appSettings.isToolEnabled(PhoneTools.stopGpsMockingToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "stop_gps_mocking",
                        description = "Stops the persistent mock GPS location and restores real GPS hardware location.",
                        parameters = emptyMap(),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any {
                        val message = com.kai.custom.tools.MockLocationController.stopMocking(context)
                        return mapOf("success" to true, "message" to message)
                    }
                },
            )
        }

        // ── Take Picture ──
        if (appSettings.isToolEnabled(PhoneTools.takePictureToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "take_picture",
                        description = "Capture a photo using the device camera. Returns the image path for AI analysis. Specify camera: 'back' (default) or 'front'.",
                        parameters = mapOf(
                            "camera" to ParameterSchema("string", "Which camera to use: 'back' or 'front' (default 'back')", false),
                        ),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            val granted = toolPermissionBridge.requestPermission(Manifest.permission.CAMERA)
                            if (!granted) {
                                return mapOf("success" to false, "error" to "Camera permission not granted.")
                            }
                        }
                        val useFront = args["camera"]?.toString() == "front"
                        val aiDir = java.io.File(context.filesDir, "ai_captures")
                        aiDir.mkdirs()
                        val outputFile = java.io.File(aiDir, "capture_${System.currentTimeMillis()}.jpg")
                        val outputUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            outputFile,
                        )
                        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                            putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
                            if (useFront && Build.VERSION.SDK_INT >= 34) {
                                putExtra("android.intent.extra.CAMERA_FACING", 0)
                            }
                            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        return try {
                            context.startActivity(intent)
                            var elapsed = 0
                            val maxWait = 120_000L
                            while (elapsed < maxWait && !outputFile.exists()) {
                                kotlinx.coroutines.delay(500)
                                elapsed += 500
                            }
                            if (outputFile.exists()) {
                                mapOf(
                                    "success" to true,
                                    "path" to outputFile.absolutePath,
                                    "message" to "Photo saved to ${outputFile.absolutePath}",
                                )
                            } else {
                                mapOf("success" to false, "error" to "Camera was not used or photo was not saved.")
                            }
                        } catch (e: Exception) {
                            mapOf("success" to false, "error" to "Failed to take picture: ${e.message}")
                        }
                    }
                },
            )
        }

        // ── Hear Surroundings ──
        if (appSettings.isToolEnabled(PhoneTools.hearSurroundingsToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "hear_surroundings",
                        description = "Listen through the device microphone, transcribe speech to text, and return the transcription. The AI automatically determines the response language.",
                        parameters = mapOf(
                            "duration_seconds" to ParameterSchema("integer", "Maximum listening duration in seconds (default 10, max 60)", false),
                        ),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            val granted = toolPermissionBridge.requestPermission(Manifest.permission.RECORD_AUDIO)
                            if (!granted) {
                                return mapOf("success" to false, "error" to "Microphone permission not granted.")
                            }
                        }
                        val maxDuration = (args["duration_seconds"] as? Number)?.toInt()?.coerceIn(1, 60) ?: 10
                        val result = withContext(Dispatchers.Main) {
                            val deferred = kotlinx.coroutines.CompletableDeferred<Map<String, Any>>()
                            val recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
                            val listener = object : android.speech.RecognitionListener {
                                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                                override fun onBeginningOfSpeech() {}
                                override fun onRmsChanged(rmsdB: Float) {}
                                override fun onBufferReceived(buffer: ByteArray?) {}
                                override fun onEndOfSpeech() {}
                                override fun onError(error: Int) {
                                    val msg = when (error) {
                                        android.speech.SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                                        android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                                        android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
                                        else -> "Recognition error: $error"
                                    }
                                    deferred.complete(mapOf("success" to false, "error" to msg))
                                }
                                override fun onResults(results: android.os.Bundle?) {
                                    val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                                    val text = matches?.firstOrNull()?.takeIf { it.isNotBlank() }
                                    if (text != null) {
                                        deferred.complete(
                                            mapOf(
                                                "success" to true,
                                                "transcription" to text,
                                                "message" to "Heard: $text",
                                            ),
                                        )
                                    } else {
                                        deferred.complete(mapOf("success" to false, "error" to "No speech detected"))
                                    }
                                }
                                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                            }
                            recognizer.setRecognitionListener(listener)
                            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                                putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                                putExtra(android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                            }
                            recognizer.startListening(intent)
                            val timeoutMs = (maxDuration + 5) * 1000L
                            val speechResult = withTimeoutOrNull(timeoutMs) { deferred.await() }
                            recognizer.destroy()
                            speechResult
                        }
                        return result ?: mapOf("success" to false, "error" to "Listening timed out after ${maxDuration}s")
                    }
                },
            )
        }

        // ── Take Screenshot ──
        if (appSettings.isToolEnabled(PhoneTools.screenshotToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "screenshot",
                        description = "Capture the current device screen and save it for AI analysis.",
                        parameters = emptyMap(),
                    )
                    override val timeout: Duration = 120.seconds
                    override suspend fun execute(args: Map<String, Any>): Any {
                        val ts = System.currentTimeMillis()
                        val aiDir = java.io.File(context.filesDir, "ai_captures")
                        aiDir.mkdirs()
                        val outputFile = java.io.File(aiDir, "screenshot_${ts}.png")
                        val sandboxName = "screenshot_${ts}.png"
                        val errors = mutableListOf<String>()

                        // Try 1: AccessibilityService (no dialog, needs Screen Reader enabled)
                        val a11yResult = try {
                            val bitmap = ScreenReaderService.captureScreenshot()
                            if (bitmap != null) {
                                FileOutputStream(outputFile).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                                copyScreenshotToSandbox(sandboxController, outputFile, sandboxName)
                                mapOf("success" to true, "method" to "accessibility", "path" to outputFile.absolutePath)
                            } else { errors.add("AccessibilityService not connected"); null }
                        } catch (e: Exception) {
                            errors.add("Accessibility error: ${e.message}")
                            null
                        }
                        if (a11yResult != null) return a11yResult

                        // Try 2: Runtime.exec("screencap") directly (works on some devices)
                        val directResult = try {
                            val proc = Runtime.getRuntime().exec(arrayOf("screencap", "-p", outputFile.absolutePath))
                            proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
                            if (proc.exitValue() == 0 && outputFile.exists() && outputFile.length() > 0) {
                                copyScreenshotToSandbox(sandboxController, outputFile, sandboxName)
                                mapOf("success" to true, "method" to "direct", "path" to outputFile.absolutePath)
                            } else null
                        } catch (_: Exception) { null }
                        if (directResult != null) return directResult

                        // Try 3: Shizuku screencap (write to /data/local/tmp, then copy)
                        val shScrPath = "/data/local/tmp/${sandboxName}"
                        val shResult = try {
                            ShizukuManager.runCommand("screencap -p $shScrPath")
                        } catch (e: Exception) {
                            errors.add("Shizuku error: ${e.message}")
                            null
                        }
                        if (shResult is Map<*, *>) {
                            val exitCode = (shResult["exit_code"] as? Int) ?: -1
                            if (exitCode == 0) {
                                val tmpFile = java.io.File(shScrPath)
                                if (tmpFile.exists() && tmpFile.length() > 0) {
                                    tmpFile.copyTo(outputFile, overwrite = true)
                                    tmpFile.delete()
                                    copyScreenshotToSandbox(sandboxController, outputFile, sandboxName)
                                    return mapOf(
                                        "success" to true,
                                        "path" to outputFile.absolutePath,
                                        "message" to "Screenshot saved (via Shizuku)",
                                    )
                                }
                                errors.add("Shizuku: file missing or empty after capture")
                            } else {
                                val shStderr = (shResult["stderr"] as? String)?.take(200) ?: ""
                                errors.add("Shizuku: ${shResult["error"] ?: "exit=$exitCode"} stderr=$shStderr")
                            }
                        }

                        val hint = if (!ScreenReaderService.isConnected()) {
                            "Enable Kai Screen Reader in Accessibility settings (Settings → Accessibility → Kai Screen Reader), then try again."
                        } else ""
                        return mapOf(
                            "success" to false,
                            "error" to errors.joinToString("; ").ifEmpty { "All screenshot methods failed." },
                            "hint" to hint,
                        )
                    }
                },
            )
        }

        // ── List Activities ──
        if (appSettings.isToolEnabled(PhoneTools.listActivitiesToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "list_activities",
                        description = "List exported activities in a given app package. Returns activity class names with their intent filter actions and labels, so you know which activities can be launched.",
                        parameters = mapOf(
                            "package" to ParameterSchema("string", "Package name (e.g. com.android.settings)", true),
                        ),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any = try {
                        val pkg = args["package"]?.toString() ?: return mapOf("success" to false, "error" to "package is required")
                        val info = context.packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES) ?: return mapOf("success" to false, "error" to "Package '$pkg' not found")
                        val activities = info.activities?.map { activity ->
                            mapOf(
                                "name" to activity.name,
                                "label" to activity.loadLabel(context.packageManager).toString(),
                                "exported" to activity.exported,
                            )
                        } ?: emptyList()
                        mapOf("success" to true, "package" to pkg, "activities" to activities)
                    } catch (e: Exception) {
                        mapOf("success" to false, "error" to "Failed to list activities: ${e.message}")
                    }
                },
            )
        }

        // ── Launch Activity ──
        if (appSettings.isToolEnabled(PhoneTools.launchActivityToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "launch_activity",
                        description = "Launch an Android app or activity by package name. If no specific activity class is given, the app's default launcher activity is used.",
                        parameters = mapOf(
                            "package" to ParameterSchema("string", "Package name (e.g. com.android.settings)", true),
                            "activity" to ParameterSchema("string", "Optional full activity class name (e.g. com.android.settings.Settings). If omitted, launches the app's default launcher activity.", false),
                            "action" to ParameterSchema("string", "Optional Intent action (e.g. android.intent.action.VIEW)", false),
                            "data_uri" to ParameterSchema("string", "Optional data URI for the intent", false),
                        ),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any = try {
                        val pkg = args["package"]?.toString() ?: return mapOf("success" to false, "error" to "package is required")
                        val activity = args["activity"]?.toString()
                        val action = args["action"]?.toString()
                        val dataUri = args["data_uri"]?.toString()
                        val intent = if (activity != null) {
                            Intent(action ?: Intent.ACTION_MAIN).apply {
                                setClassName(pkg, activity)
                                dataUri?.let { data = android.net.Uri.parse(it) }
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        } else {
                            context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            } ?: return mapOf("success" to false, "error" to "No launch intent found for package '$pkg'")
                        }
                        context.startActivity(intent)
                        mapOf("success" to true, "message" to "Launched ${if (activity != null) "$pkg/$activity" else pkg}")
                    } catch (e: Exception) {
                        mapOf("success" to false, "error" to "Failed to launch activity: ${e.message}")
                    }
                },
            )
        }

        // ── Modify Settings ──
        if (appSettings.isToolEnabled(PhoneTools.modifySettingsToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "modify_settings",
                        description = "Modify Android system/global/secure settings. Grants WRITE_SETTINGS via Shizuku automatically when needed. Namespace: 'system' (default), 'global', or 'secure'.",
                        parameters = mapOf(
                            "key" to ParameterSchema("string", "Setting key (e.g. screen_brightness, wifi_on)", true),
                            "value" to ParameterSchema("string", "Value to set", true),
                            "namespace" to ParameterSchema("string", "Settings namespace: 'system', 'global', or 'secure' (default 'system')", false),
                        ),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any {
                        val key = args["key"]?.toString() ?: return mapOf("success" to false, "error" to "key is required")
                        val value = args["value"]?.toString() ?: return mapOf("success" to false, "error" to "value is required")
                        val namespace = args["namespace"]?.toString()?.lowercase() ?: "system"

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.System.canWrite(context)) {
                            val grantedByShizuku = if (ShizukuManager.isAvailable && ShizukuManager.hasPermission) {
                                val result = ShizukuManager.runCommand("appops set ${context.packageName} android:write_settings allow")
                                result["exitCode"] as? Int == 0
                            } else {
                                false
                            }
                            if (!grantedByShizuku) {
                                val intent = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                                return mapOf(
                                    "success" to false,
                                    "error" to "WRITE_SETTINGS permission not granted and Shizuku unavailable. Settings screen has been opened — please grant the permission and try again.",
                                )
                            }
                        }

                        return try {
                            val resolver = context.contentResolver
                            when (namespace) {
                                "global" -> android.provider.Settings.Global.putString(resolver, key, value)
                                "secure" -> android.provider.Settings.Secure.putString(resolver, key, value)
                                else -> android.provider.Settings.System.putString(resolver, key, value)
                            }
                            mapOf("success" to true, "key" to key, "value" to value, "namespace" to namespace)
                        } catch (e: Exception) {
                            mapOf("success" to false, "error" to "Failed to set $namespace/$key: ${e.message}")
                        }
                    }
                },
            )
        }

        // ── Read Screen Text ──
        if (appSettings.isToolEnabled(PhoneTools.readScreenTextToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "read_screen_text",
                        description = "Read all visible text on the current screen. For scrolling through long content (e.g. WhatsApp chats), use extract_scrollable_content instead — it automates the scroll+read loop in one call.",
                        parameters = emptyMap(),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any {
                        if (!com.kai.custom.ScreenReaderService.isConnected()) {
                            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                            return mapOf(
                                "success" to false,
                                "error" to "Accessibility Service is not enabled. Settings opened — please enable 'Kai Screen Reader' in Accessibility settings and try again.",
                            )
                        }
                        return try {
                            val text = com.kai.custom.ScreenReaderService.readScreenText()
                            if (text.isNullOrBlank()) {
                                // Fall back to Shizuku + uiautomator dump
                                val fallbackText = com.kai.custom.ScreenReaderService.readScreenTextWithFallback()
                                if (fallbackText.isNullOrBlank()) {
                                    mapOf("success" to true, "text" to "", "message" to "No text found on screen")
                                } else {
                                    mapOf("success" to true, "text" to fallbackText, "char_count" to fallbackText.length, "source" to "uiautomator")
                                }
                            } else {
                                mapOf("success" to true, "text" to text, "char_count" to text.length, "source" to "accessibility")
                            }
                        } catch (e: Exception) {
                            mapOf("success" to false, "error" to "Failed to read screen: ${e.message}")
                        }
                    }
                },
            )
        }

        // ── Extract Scrollable Content ──
        if (appSettings.isToolEnabled(PhoneTools.readScreenTextToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "extract_scrollable_content",
                        description = "Automatically scroll through the current screen and capture all visible text. Use this for reading entire conversations, long lists, or documents. Specify direction 'up' for WhatsApp chats (older messages at top). Returns text from each scroll position, separated by ---.",
                        parameters = mapOf(
                            "direction" to ParameterSchema("string", "Scroll direction: 'down' (default) or 'up'. Use 'up' for WhatsApp chats (scrolling to older messages).", false),
                            "max_scrolls" to ParameterSchema("integer", "Maximum scroll steps (default 20)", false),
                        ),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any {
                        if (!com.kai.custom.ScreenReaderService.isConnected()) {
                            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                            return mapOf("success" to false, "error" to "Accessibility Service not enabled. Settings opened.")
                        }
                        val direction = args["direction"]?.toString() ?: "down"
                        val maxScrolls = (args["max_scrolls"] as? Number)?.toInt()?.coerceIn(1, 50) ?: 20
                        return try {
                            val text = com.kai.custom.ScreenReaderService.extractScrollableContent(direction, maxScrolls)
                            if (text.isNullOrBlank()) {
                                mapOf("success" to true, "text" to "", "message" to "No scrollable content found")
                            } else {
                                mapOf(
                                    "success" to true,
                                    "text" to text,
                                    "char_count" to text.length,
                                    "scrolls" to (text.split("---").size).coerceAtLeast(1),
                                )
                            }
                        } catch (e: Exception) {
                            mapOf("success" to false, "error" to "Failed to extract scrollable content: ${e.message}")
                        }
                    }
                },
            )
        }

        // ── Navigate Screen ──
        if (appSettings.isToolEnabled(PhoneTools.navigateScreenToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "navigate_screen",
                        description = "Navigate on screen using AccessibilityService. Actions: click_text (tap text/label), click_coordinates (tap x,y), back, scroll_down, scroll_up, home, recents, notifications.",
                        parameters = mapOf(
                            "action" to ParameterSchema("string", "Action to perform: click_text, click_coordinates, back, scroll_down, scroll_up, home, recents, notifications", true),
                            "text" to ParameterSchema("string", "Text to tap (required for click_text action)", false),
                            "x" to ParameterSchema("number", "X coordinate (required for click_coordinates action)", false),
                            "y" to ParameterSchema("number", "Y coordinate (required for click_coordinates action)", false),
                        ),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any {
                        if (!com.kai.custom.ScreenReaderService.isConnected()) {
                            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                            return mapOf(
                                "success" to false,
                                "error" to "Accessibility Service is not enabled. Settings opened — please enable 'Kai Screen Reader' in Accessibility.",
                            )
                        }
                        val action = args["action"]?.toString() ?: return mapOf("success" to false, "error" to "action is required")
                        return try {
                            val result = when (action) {
                                "click_text" -> {
                                    val text = args["text"]?.toString()
                                    if (text.isNullOrBlank()) return mapOf("success" to false, "error" to "text is required for click_text")
                                    com.kai.custom.ScreenReaderService.clickOnText(text)
                                }

                                "click_coordinates" -> {
                                    val x = (args["x"] as? Number)?.toFloat()
                                    val y = (args["y"] as? Number)?.toFloat()
                                    if (x == null || y == null) return mapOf("success" to false, "error" to "x and y are required for click_coordinates")
                                    com.kai.custom.ScreenReaderService.clickOnCoordinates(x, y)
                                }

                                "back" -> com.kai.custom.ScreenReaderService.globalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)

                                "home" -> com.kai.custom.ScreenReaderService.globalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)

                                "recents" -> com.kai.custom.ScreenReaderService.globalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)

                                "notifications" -> com.kai.custom.ScreenReaderService.globalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)

                                "scroll_down" -> com.kai.custom.ScreenReaderService.scrollForward()

                                "scroll_up" -> com.kai.custom.ScreenReaderService.scrollBackward()

                                else -> return mapOf("success" to false, "error" to "Unknown action: $action")
                            }
                            mapOf("success" to result, "action" to action, "message" to if (result) "$action performed" else "$action failed")
                        } catch (e: Exception) {
                            mapOf("success" to false, "error" to "Failed to $action: ${e.message}")
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                val granted = toolPermissionBridge.requestPermission(Manifest.permission.BLUETOOTH_CONNECT)
                                if (!granted) {
                                    return mapOf("success" to false, "error" to "BLUETOOTH_CONNECT permission not granted. Grant it in Settings > Apps > Kai > Permissions.")
                                }
                            }
                        }
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
                        val permissionsToRequest = mutableListOf<String>()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val mediaType = args["type"]?.toString()?.lowercase() ?: "all"
                            if (mediaType == "all" || mediaType == "image") permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
                            if (mediaType == "all" || mediaType == "video") permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
                            if (mediaType == "all" || mediaType == "audio") permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
                        }
                        if (permissionsToRequest.isNotEmpty()) {
                            val allGranted = permissionsToRequest.all {
                                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                            }
                            if (!allGranted) {
                                val granted = toolPermissionBridge.requestPermission(*permissionsToRequest.toTypedArray())
                                if (!granted) {
                                    return mapOf("success" to false, "error" to "Media permissions not granted. Grant them in Settings > Apps > Kai > Permissions.")
                                }
                            }
                        }
                        return try {
                            val mediaType = args["type"]?.toString()?.lowercase() ?: "all"
                            val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20
                            val results = mutableMapOf<String, Any>()
                            fun queryMedia(uri: android.net.Uri, sortField: String): List<Map<String, Any>> {
                                val list = mutableListOf<Map<String, Any>>()
                                val projection = arrayOf("_id", "_display_name", "_size", "date_added")
                                val cursor = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    val bundle = android.os.Bundle().apply {
                                        putString(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, sortField)
                                        putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, 2)
                                        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
                                    }
                                    context.contentResolver.query(uri, projection, bundle, null)
                                } else {
                                    context.contentResolver.query(uri, projection, null, null, "$sortField DESC LIMIT $limit")
                                }
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

        add(generatePollinationImageTool(context))
        addAll(mcpServerManager.getEnabledMcpTools())
    }

    // Deduplicate by name — MCP servers may expose tools with the same
    // names as built-in tools (e.g. memory_store, search_memories).
    return allTools.distinctBy { it.schema.name }
}

private fun generatePollinationImageTool(context: Context): Tool = object : Tool {
    override val schema = ToolSchema(
        name = "generate_image",
        description = "Generate an image from a text prompt (free, via image.pollinations.ai). Returns the path to the downloaded image file.",
        parameters = mapOf(
            "prompt" to ParameterSchema("string", "Text description of the image to generate", true),
            "width" to ParameterSchema("integer", "Image width in pixels (default: 1024)", false),
            "height" to ParameterSchema("integer", "Image height in pixels (default: 1024)", false),
            "seed" to ParameterSchema("integer", "Random seed for reproducible results", false),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any = try {
        val prompt = (args["prompt"] as? String)?.trim()
            ?: return mapOf("success" to false, "error" to "Prompt is required")
        val width = args["width"] as? Int ?: 1024
        val height = args["height"] as? Int ?: 1024
        val seed = args["seed"] as? Int

        val encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8")
        val url = StringBuilder("https://image.pollinations.ai/prompt/$encodedPrompt")
        val params = mutableListOf("width=$width", "height=$height")
        if (seed != null) params.add("seed=$seed")
        url.append("?${params.joinToString("&")}")

        val client = httpClient {
            install(HttpTimeout) { requestTimeoutMillis = 120_000 }
        }
        try {
            client.prepareGet(url.toString()).execute { response ->
                if (!response.status.isSuccess()) {
                    return@execute mapOf("success" to false, "error" to "Pollinations API returned HTTP ${response.status.value}")
                }
                val channel = response.bodyAsChannel()
                val aiDir = java.io.File(context.filesDir, "ai_captures")
                aiDir.mkdirs()
                val outputFile = java.io.File(aiDir, "pollination_${System.currentTimeMillis()}.jpg")
                java.io.FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    while (!channel.isClosedForRead) {
                        val bytesRead = channel.readAvailable(buffer)
                        if (bytesRead <= 0) break
                        output.write(buffer, 0, bytesRead)
                    }
                }
                mapOf(
                    "success" to true,
                    "path" to outputFile.absolutePath,
                    "file_size" to outputFile.length(),
                    "message" to "Image saved to ${outputFile.absolutePath}",
                )
            }
        } finally {
            client.close()
        }
    } catch (e: Exception) {
        mapOf("success" to false, "error" to "Failed to generate image: ${e.message}")
    }
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
        val viewIntent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val defaultResolve = context.packageManager.resolveActivity(viewIntent, 0)
        if (defaultResolve != null && defaultResolve.activityInfo.packageName != context.packageName) {
            viewIntent.setPackage(defaultResolve.activityInfo.packageName)
        }
        viewIntent
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

actual suspend fun saveFileToDevice(path: String, baseName: String, extension: String) {
    val bytes = java.io.File(path).readBytes()
    saveFileToDevice(bytes, baseName, extension)
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
    val intents = buildList {
        // General battery optimization list (user finds Kai manually) — works on all OEMs
        add(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        // Direct dialog — remapped to autostart/background permissions on some OEMs (Xiaomi etc.)
        add(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${context.packageName}")))
    }
    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Exception) { }
    }
}

actual fun isBatteryOptimizationDisabled(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val context: Context by inject(Context::class.java)
    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

actual fun openMockLocationSettings() {
    val context: Context by inject(Context::class.java)
    try {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        // Developer settings not available
    }
}

actual fun isMockLocationConfigured(): Boolean {
    val context: Context by inject(Context::class.java)
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    val testProviders = locationManager.allProviders.filter {
        @Suppress("DEPRECATION")
        locationManager.getProvider(it)?.let { p -> p.name == android.location.LocationManager.GPS_PROVIDER } == true
    }
    // Check if any test provider already exists (from previous startMocking)
    try {
        @Suppress("DEPRECATION")
        if (locationManager.getProvider(android.location.LocationManager.GPS_PROVIDER) != null) return true
    } catch (_: Exception) {}
    // Try to add/remove atomically to check permission
    return try {
        locationManager.addTestProvider(
            android.location.LocationManager.GPS_PROVIDER,
            false, false, false, false, true, true, true,
            @Suppress("DEPRECATION") android.location.Criteria.POWER_HIGH,
            @Suppress("DEPRECATION") android.location.Criteria.ACCURACY_FINE,
        )
        locationManager.removeTestProvider(android.location.LocationManager.GPS_PROVIDER)
        true
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        true
    }
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

actual fun listCalendarAccounts(): List<CalendarAccount> {
    val context: Context by inject(Context::class.java)
    val projection = arrayOf(
        CalendarContract.Calendars._ID,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        CalendarContract.Calendars.ACCOUNT_NAME,
    )
    val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
    val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
    val accounts = mutableListOf<CalendarAccount>()
    context.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)) ?: ""
            val account = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)) ?: ""
            accounts.add(CalendarAccount(id, if (account.isNotBlank()) "$name ($account)" else name))
        }
    }
    return accounts
}

private suspend fun copyScreenshotToSandbox(
    sandboxController: SandboxController,
    file: java.io.File,
    sandboxName: String,
) {
    if (!file.exists()) return
    try {
        val pngBytes = file.readBytes()
        sandboxController.writeBinaryFile("/root/$sandboxName", pngBytes)
    } catch (_: Exception) { }
}

private suspend fun captureScreenViaMediaProjection(
    context: Context,
    bridge: ActivityResultBridge,
    outputFile: java.io.File,
): Map<String, Any> {
    val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    val captureIntent = mediaProjectionManager.createScreenCaptureIntent()

    val result = bridge.launchIntentForResult(captureIntent) ?: return mapOf(
        "success" to false,
        "error" to "Screen capture request timed out or was cancelled.",
    )
    if (!result.success) {
        return mapOf("success" to false, "error" to "Screen capture permission denied.")
    }

    val resultIntent = bridge.pendingResultIntent ?: return mapOf(
        "success" to false,
        "error" to "No result data from screen capture permission.",
    )

    val mediaProjection = mediaProjectionManager.getMediaProjection(result.resultCode, resultIntent)
        ?: return mapOf("success" to false, "error" to "Failed to create MediaProjection.")

    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val metrics = DisplayMetrics()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val windowMetrics = wm.currentWindowMetrics
        metrics.widthPixels = windowMetrics.bounds.width()
        metrics.heightPixels = windowMetrics.bounds.height()
    } else {
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
    }
    val width = metrics.widthPixels
    val height = metrics.heightPixels
    val density = metrics.densityDpi

    val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    val imageHandler = Handler(Looper.getMainLooper())
    val frameReady = kotlinx.coroutines.CompletableDeferred<Bitmap?>()

    imageReader.setOnImageAvailableListener({ reader ->
        val image = reader.acquireLatestImage()
        if (image != null) {
            val bitmap = imageToBitmap(image)
            image.close()
            frameReady.complete(bitmap)
        } else {
            frameReady.complete(null)
        }
    }, imageHandler)

    val virtualDisplay = mediaProjection.createVirtualDisplay(
        "KaiScreenshotCapture",
        width, height, density,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        imageReader.surface,
        null, null,
    )

    try {
        val bitmap = withTimeoutOrNull(8000L) { frameReady.await() }
            ?: return mapOf("success" to false, "error" to "Timed out waiting for screen capture frame.")

        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        outFile@ return mapOf(
            "success" to true,
            "path" to outputFile.absolutePath,
            "message" to "Screenshot saved to ${outputFile.name}",
        )
    } finally {
        virtualDisplay?.release()
        imageReader.close()
        mediaProjection.stop()
    }
}

private fun imageToBitmap(image: android.media.Image): Bitmap {
    val planes = image.planes
    val buffer = planes[0].buffer
    val pixelStride = planes[0].pixelStride
    val rowStride = planes[0].rowStride
    val rowPadding = rowStride - pixelStride * image.width

    val bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(buffer)
    return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
}
