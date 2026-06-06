package com.kai.custom

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.kai.custom.data.AppSettings
import com.kai.custom.data.MemoryStore
import com.kai.custom.data.TaskStore
import com.kai.custom.mcp.McpServerManager
import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import com.kai.custom.tools.CommonTools
import com.kai.custom.tools.HeartbeatTools
import com.kai.custom.tools.PhoneTools
import com.kai.custom.tools.SchedulingTools
import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.download
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.js.Js
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_send_notification_description
import kai.composeapp.generated.resources.tool_send_notification_name
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Js) {
    config(this)
}

actual fun getBackgroundDispatcher(): CoroutineContext = EmptyCoroutineContext

actual fun onDragAndDropEventDropped(event: DragAndDropEvent): PlatformFile? = null

actual val BackIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack

actual val currentPlatform: Platform = Platform.Web

actual val defaultUiScale: Float = 1.0f

actual val isEmailSupported: Boolean = false

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

actual suspend fun compressImageBytes(bytes: ByteArray, mimeType: String): ByteArray = bytes

actual fun getAppFilesDirectory(): String {
    // Web uses localStorage, return empty string as no file path is needed
    return ""
}

actual fun createSecureSettings(): Settings {
    // Web has no secure storage - using localStorage
    return StorageSettings()
}

actual fun createLegacySettings(): Settings? = null // Same storage location, no migration needed

actual fun getPlatformToolDefinitions(): List<ToolInfo> = buildList {
    addAll(CommonTools.commonToolDefinitions)
    add(PhoneTools.deviceInfoToolInfo)
    add(PhoneTools.clipboardToolInfo)
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
    add(PhoneTools.batteryInfoToolInfo)
    add(PhoneTools.networkInfoToolInfo)
}

private object WebKoinHelper : KoinComponent {
    val appSettings: AppSettings by inject()
    val memoryStore: MemoryStore by inject()
    val taskStore: TaskStore by inject()
    val mcpServerManager: McpServerManager by inject()
}

actual fun getAvailableTools(): List<Tool> = buildList {
    addAll(CommonTools.getCommonTools(WebKoinHelper.appSettings))
    if (WebKoinHelper.appSettings.isMemoryEnabled()) {
        if (!WebKoinHelper.mcpServerManager.isConnected("alt_memory")) {
            addAll(CommonTools.getMemoryTools(WebKoinHelper.memoryStore))
        }
    }
    if (WebKoinHelper.appSettings.isSchedulingEnabled()) {
        addAll(SchedulingTools.getSchedulingTools(WebKoinHelper.taskStore))
        addAll(HeartbeatTools.getHeartbeatTools(WebKoinHelper.memoryStore, WebKoinHelper.appSettings))
    }
    if (WebKoinHelper.appSettings.isToolEnabled("get_device_info")) {
        add(
            object : Tool {
                override val schema = ToolSchema(
                    name = "get_device_info",
                    description = "Get basic device info from browser environment",
                    parameters = emptyMap(),
                )
                override suspend fun execute(args: Map<String, Any>): Any = mapOf("success" to true, "platform" to "WasmJS", "os" to mapOf("name" to "Web (Wasm)"))
            },
        )
    }
    if (WebKoinHelper.appSettings.isToolEnabled("read_clipboard")) {
        add(object : Tool {
            override val schema = ToolSchema(
                name = "read_clipboard",
                description = "Read clipboard content (not available on WasmJS)",
                parameters = emptyMap(),
            )
            override suspend fun execute(args: Map<String, Any>): Any = mapOf("success" to false, "error" to "Clipboard API not available on WasmJS")
        })
    }
    if (WebKoinHelper.appSettings.isToolEnabled("send_notification")) {
        add(object : Tool {
            override val schema = ToolSchema(
                "send_notification",
                "Send notification (not available on WasmJS)",
                mapOf("title" to ParameterSchema("string", "Title", false), "message" to ParameterSchema("string", "Message", true)),
            )
            override suspend fun execute(args: Map<String, Any>): Any = mapOf("success" to false, "error" to "Notification API not available on WasmJS")
        })
    }
    if (WebKoinHelper.appSettings.isToolEnabled(PhoneTools.gpsLocationToolInfo.id)) {
        add(object : Tool {
            override val schema = ToolSchema(
                name = "get_gps_location",
                description = "GPS location (not available on WasmJS)",
                parameters = emptyMap(),
            )
            override suspend fun execute(args: Map<String, Any>): Any = mapOf("success" to false, "error" to "GPS not available on WasmJS")
        })
    }
    if (WebKoinHelper.appSettings.isToolEnabled(PhoneTools.batteryInfoToolInfo.id)) {
        add(object : Tool {
            override val schema = ToolSchema(
                name = "get_battery_info",
                description = "Battery info (not available on WasmJS)",
                parameters = emptyMap(),
            )
            override suspend fun execute(args: Map<String, Any>): Any = mapOf("success" to false, "error" to "Battery info not available on WasmJS")
        })
    }
    if (WebKoinHelper.appSettings.isToolEnabled(PhoneTools.networkInfoToolInfo.id)) {
        add(object : Tool {
            override val schema = ToolSchema(
                name = "get_network_info",
                description = "Network info (not available on WasmJS)",
                parameters = emptyMap(),
            )
            override suspend fun execute(args: Map<String, Any>): Any = mapOf("success" to false, "error" to "Network info not available on WasmJS")
        })
    }
    addAll(WebKoinHelper.mcpServerManager.getEnabledMcpTools())
}

actual fun openUrl(url: String): Boolean = try {
    kotlinx.browser.window.open(url, "_blank")
    true
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
    // No system back gesture on web
}

actual suspend fun saveFileToDevice(bytes: ByteArray, baseName: String, extension: String) {
    FileKit.download(bytes = bytes, fileName = "$baseName.$extension")
}

actual suspend fun saveFileToDevice(path: String, baseName: String, extension: String) {
    throw NotImplementedError("saveFileToDevice(path) not available on WasmJs")
}

// Web notifications API isn't wired up; stub.
actual fun sendHeartbeatNotification(title: String, body: String) = Unit

actual fun openTtsSettings() = Unit

actual fun openBatteryOptimizationSettings() = Unit
actual fun openMockLocationSettings() = Unit
actual fun isMockLocationConfigured(): Boolean = true
actual fun isBatteryOptimizationDisabled(): Boolean = true
actual fun defaultOpenAICompatibleBaseUrl(): String = "http://localhost:11434/v1"
actual fun listCalendarAccounts(): List<CalendarAccount> = emptyList()
