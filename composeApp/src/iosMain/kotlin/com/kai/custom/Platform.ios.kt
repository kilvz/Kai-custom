package com.kai.custom

import androidx.compose.material.icons.Icons
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.kai.custom.data.AppSettings
import com.kai.custom.data.EmailStore
import com.kai.custom.data.MemoryStore
import com.kai.custom.data.TaskStore
import com.kai.custom.mcp.McpServerManager
import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import com.kai.custom.sendHeartbeatNotification
import com.kai.custom.tools.CommonTools
import com.kai.custom.tools.EmailTools
import com.kai.custom.tools.HeartbeatTools
import com.kai.custom.tools.PhoneTools
import com.kai.custom.tools.SchedulingTools
import com.kai.custom.ui.icons.ArrowBackIos
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_send_notification_description
import kai.composeapp.generated.resources.tool_send_notification_name
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import kotlin.coroutines.CoroutineContext

actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Darwin) {
    config(this)
}

actual fun getBackgroundDispatcher(): CoroutineContext = Dispatchers.IO

actual fun onDragAndDropEventDropped(event: DragAndDropEvent): PlatformFile? = null

actual val BackIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBackIos

actual val currentPlatform: Platform = Platform.Mobile.Ios

actual val defaultUiScale: Float = 1.0f

actual val isEmailSupported: Boolean = true

actual val isSmsSupported: Boolean = false

actual val isTelegramSupported: Boolean = false

actual val isWhatsAppSupported: Boolean = false

actual val isNotificationsSupported: Boolean = false

actual val isSplinterlandsSupported: Boolean = false

actual val isShizukuSupported: Boolean = false

actual val isRootSupported: Boolean = false

actual fun isRootAvailable(): Boolean = false

actual fun isShizukuPermissionGranted(): Boolean = false

actual fun requestShizukuPermission(onGranted: (() -> Unit)?) {}

actual fun getToolPermissionMap(): Map<String, List<String>> = emptyMap()

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
actual suspend fun compressImageBytes(bytes: ByteArray, mimeType: String): ByteArray {
    if (!mimeType.startsWith("image/")) return bytes
    return try {
        val nsData = bytes.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
        }
        val image = platform.UIKit.UIImage(data = nsData)
        val maxDim = 1024.0
        val imgWidth = image.size.useContents { width }
        val imgHeight = image.size.useContents { height }
        val scaled = if (imgWidth > maxDim || imgHeight > maxDim) {
            val scale = maxDim / maxOf(imgWidth, imgHeight)
            val newWidth = imgWidth * scale
            val newHeight = imgHeight * scale
            val newSize = kotlinx.cinterop.cValue<platform.CoreGraphics.CGSize> {
                width = newWidth
                height = newHeight
            }
            platform.UIKit.UIGraphicsBeginImageContextWithOptions(newSize, false, 1.0)
            image.drawInRect(
                kotlinx.cinterop.cValue<platform.CoreGraphics.CGRect> {
                    origin.x = 0.0
                    origin.y = 0.0
                    size.width = newWidth
                    size.height = newHeight
                },
            )
            val resized = platform.UIKit.UIGraphicsGetImageFromCurrentImageContext()
            platform.UIKit.UIGraphicsEndImageContext()
            resized ?: image
        } else {
            image
        }
        val jpegData = platform.UIKit.UIImageJPEGRepresentation(scaled, 0.8) ?: return bytes
        jpegData.toByteArray()
    } catch (_: Exception) {
        bytes
    }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned ->
        platform.posix.memcpy(pinned.addressOf(0), bytes, length)
    }
    return result
}

actual fun getAppFilesDirectory(): String {
    val paths = platform.Foundation.NSSearchPathForDirectoriesInDomains(
        platform.Foundation.NSDocumentDirectory,
        platform.Foundation.NSUserDomainMask,
        true,
    )
    return paths.first() as String
}

@OptIn(ExperimentalSettingsImplementation::class)
actual fun createSecureSettings(): Settings = KeychainSettings(service = "com.kai.custom")

actual fun createLegacySettings(): Settings? = NSUserDefaultsSettings(platform.Foundation.NSUserDefaults.standardUserDefaults)

actual fun getPlatformToolDefinitions(): List<ToolInfo> = buildList {
    addAll(CommonTools.commonToolDefinitions)
    add(PhoneTools.deviceInfoToolInfo)
    add(PhoneTools.clipboardToolInfo)
    add(PhoneTools.batteryInfoToolInfo)
    add(
        ToolInfo(
            id = "send_notification",
            name = "Send Notification",
            description = "Send a push notification to the device",
            nameRes = Res.string.tool_send_notification_name,
            descriptionRes = Res.string.tool_send_notification_description,
        ),
    )
    add(PhoneTools.gpsLocationToolInfo)
    add(PhoneTools.networkInfoToolInfo)
}

private object IosKoinHelper : KoinComponent {
    val appSettings: AppSettings by inject()
    val memoryStore: MemoryStore by inject()
    val taskStore: TaskStore by inject()
    val emailStore: EmailStore by inject()
    val mcpServerManager: McpServerManager by inject()
}

actual fun getAvailableTools(): List<Tool> = buildList {
    addAll(CommonTools.getCommonTools(IosKoinHelper.appSettings))
    if (IosKoinHelper.appSettings.isMemoryEnabled()) {
        if (!IosKoinHelper.mcpServerManager.isConnected("alt_memory")) {
            addAll(CommonTools.getMemoryTools(IosKoinHelper.memoryStore))
        }
    }
    if (IosKoinHelper.appSettings.isSchedulingEnabled()) {
        addAll(SchedulingTools.getSchedulingTools(IosKoinHelper.taskStore))
        addAll(HeartbeatTools.getHeartbeatTools(IosKoinHelper.memoryStore, IosKoinHelper.appSettings))
    }
    if (IosKoinHelper.appSettings.isEmailEnabled()) {
        addAll(EmailTools.getEmailTools(IosKoinHelper.emailStore))
    }
    if (IosKoinHelper.appSettings.isToolEnabled(PhoneTools.deviceInfoToolInfo.id)) {
        add(
            object : Tool {
                override val schema = ToolSchema(
                    name = "get_device_info",
                    description = "Get detailed device information including model, iOS version, and hardware specs",
                    parameters = emptyMap(),
                )
                override suspend fun execute(args: Map<String, Any>): Any {
                    val device = platform.UIKit.UIDevice.currentDevice
                    val mem = platform.Foundation.NSProcessInfo.processInfo
                    return mapOf(
                        "success" to true,
                        "device" to mapOf("model" to device.model, "name" to device.name, "system_name" to device.systemName),
                        "os" to mapOf(
                            "version" to device.systemVersion,
                            "name" to "iOS",
                        ),
                        "hardware" to mapOf(
                            "physical_memory_mb" to (mem.physicalMemory / 1_048_576u).toInt(),
                            "processor_count" to mem.processorCount,
                            "active_processor_count" to mem.activeProcessorCount,
                        ),
                    )
                }
            },
        )
    }
    if (IosKoinHelper.appSettings.isToolEnabled(PhoneTools.clipboardToolInfo.id)) {
        add(
            object : Tool {
                override val schema = ToolSchema(
                    name = "read_clipboard",
                    description = "Read the current content of the system clipboard",
                    parameters = emptyMap(),
                )
                override suspend fun execute(args: Map<String, Any>): Any {
                    try {
                        val pasteboard = platform.UIKit.UIPasteboard.generalPasteboard
                        val text = pasteboard.string
                        return mapOf("success" to true, "content" to (text ?: ""), "has_content" to (text != null))
                    } catch (e: Exception) {
                        return mapOf("success" to false, "error" to "Failed to read clipboard: ${e.message}")
                    }
                }
            },
        )
    }
    if (IosKoinHelper.appSettings.isToolEnabled(PhoneTools.batteryInfoToolInfo.id)) {
        add(
            object : Tool {
                override val schema = ToolSchema(
                    name = "get_battery_info",
                    description = "Get battery level, charging status, and temperature",
                    parameters = emptyMap(),
                )
                override suspend fun execute(args: Map<String, Any>): Any {
                    try {
                        val device = platform.UIKit.UIDevice.currentDevice
                        device.batteryMonitoringEnabled = true
                        val level = device.batteryLevel
                        val state = device.batteryState
                        val levelPercent = if (level >= 0) (level * 100).toInt() else -1
                        val isCharging = state == platform.UIKit.UIDeviceBatteryState.UIDeviceBatteryStateCharging ||
                            state == platform.UIKit.UIDeviceBatteryState.UIDeviceBatteryStateFull
                        return mapOf(
                            "success" to true,
                            "level_percent" to levelPercent,
                            "is_charging" to isCharging,
                            "health" to "unknown",
                        )
                    } catch (e: Exception) {
                        return mapOf("success" to false, "error" to "Failed to get battery info: ${e.message}")
                    }
                }
            },
        )
    }
    if (IosKoinHelper.appSettings.isToolEnabled("send_notification")) {
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
                    val title = args["title"] as? String ?: "Kai"
                    val message = args["message"] as? String
                        ?: return mapOf("success" to false, "error" to "Message is required")
                    sendHeartbeatNotification(title, message)
                    return mapOf("success" to true, "message" to "Notification sent successfully")
                }
            },
        )
    }
    if (IosKoinHelper.appSettings.isToolEnabled(PhoneTools.gpsLocationToolInfo.id)) {
        add(
            object : Tool {
                override val schema = ToolSchema(
                    name = "get_gps_location",
                    description = "Get current GPS location coordinates",
                    parameters = emptyMap(),
                )
                override suspend fun execute(args: Map<String, Any>): Any = mapOf("success" to false, "error" to "GPS location requires CoreLocation struct interop not available in this Kotlin/Native version")
            },
        )
    }
    if (IosKoinHelper.appSettings.isToolEnabled(PhoneTools.networkInfoToolInfo.id)) {
        add(
            object : Tool {
                override val schema = ToolSchema(
                    name = "get_network_info",
                    description = "Get basic network connectivity status",
                    parameters = emptyMap(),
                )
                override suspend fun execute(args: Map<String, Any>): Any = mapOf("success" to true, "platform" to "iOS")
            },
        )
    }
    addAll(IosKoinHelper.mcpServerManager.getEnabledMcpTools())
}

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun openUrl(url: String): Boolean = try {
    val nsUrl = platform.Foundation.NSURL.URLWithString(url)
    if (nsUrl != null) {
        platform.UIKit.UIApplication.sharedApplication.openURL(nsUrl)
    } else {
        false
    }
} catch (_: Exception) {
    false
}

actual fun decodeToImageBitmap(bytes: ByteArray): ImageBitmap? = try {
    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
} catch (_: Exception) {
    null
}

@androidx.compose.runtime.Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS swipe-back is handled by the navigation controller
}

actual suspend fun saveFileToDevice(bytes: ByteArray, baseName: String, extension: String) {
    val file = FileKit.openFileSaver(suggestedName = baseName, defaultExtension = extension)
    file?.write(bytes)
}

actual suspend fun saveFileToDevice(path: String, baseName: String, extension: String) {
    val file = FileKit.openFileSaver(suggestedName = baseName, defaultExtension = extension)
    file?.write(java.io.File(path).readBytes())
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun sendHeartbeatNotification(title: String, body: String) {
    // The authorization completion runs asynchronously on a system queue, so it's outside the
    // outer try/catch's scope and needs its own guard. Heartbeat delivery must never throw.
    try {
        val center = platform.UserNotifications.UNUserNotificationCenter.currentNotificationCenter()
        val options = platform.UserNotifications.UNAuthorizationOptionAlert or
            platform.UserNotifications.UNAuthorizationOptionSound or
            platform.UserNotifications.UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { granted, _ ->
            if (!granted) return@requestAuthorizationWithOptions
            try {
                val content = platform.UserNotifications.UNMutableNotificationContent().apply {
                    setTitle(title)
                    setBody(body)
                    setSound(platform.UserNotifications.UNNotificationSound.defaultSound())
                }
                // iOS rejects nil triggers for non-scheduled notifications and 0 for time-interval
                // triggers, so use a tiny delay to fire effectively immediately.
                val trigger = platform.UserNotifications.UNTimeIntervalNotificationTrigger
                    .triggerWithTimeInterval(timeInterval = 0.1, repeats = false)
                val request = platform.UserNotifications.UNNotificationRequest.requestWithIdentifier(
                    identifier = platform.Foundation.NSUUID().UUIDString,
                    content = content,
                    trigger = trigger,
                )
                center.addNotificationRequest(request, null)
            } catch (_: Throwable) {
            }
        }
    } catch (_: Throwable) {
    }
}

actual fun openTtsSettings() = Unit

actual fun openBatteryOptimizationSettings() = Unit
actual fun openMockLocationSettings() = Unit
actual fun isMockLocationConfigured(): Boolean = true
actual fun isBatteryOptimizationDisabled(): Boolean = true
actual fun defaultOpenAICompatibleBaseUrl(): String = "http://localhost:11434/v1"
actual fun listCalendarAccounts(): List<CalendarAccount> = emptyList()
