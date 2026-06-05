@file:OptIn(ExperimentalComposeUiApi::class)

package com.kai.custom

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.kai.custom.data.AppSettings
import com.kai.custom.data.EmailStore
import com.kai.custom.data.EncryptedFileSettings
import com.kai.custom.data.MemoryStore
import com.kai.custom.data.TaskStore
import com.kai.custom.mcp.McpServerManager
import com.kai.custom.root.AdminManager
import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import com.kai.custom.sendHeartbeatNotification
import com.kai.custom.tools.AdminTool
import com.kai.custom.tools.ApplyPatchTool
import com.kai.custom.tools.CommonTools
import com.kai.custom.tools.EditFileTool
import com.kai.custom.tools.EmailTools
import com.kai.custom.tools.GlobTool
import com.kai.custom.tools.GrepTool
import com.kai.custom.tools.HeartbeatTools
import com.kai.custom.tools.InternetSearchTool
import com.kai.custom.tools.OpenFileTool
import com.kai.custom.tools.PhoneTools
import com.kai.custom.tools.ProcessManagerTool
import com.kai.custom.tools.ReadFileTool
import com.kai.custom.tools.SchedulingTools
import com.kai.custom.tools.CreateCalendarEventToolDesktop
import com.kai.custom.tools.ListInstalledAppsToolDesktop
import com.kai.custom.tools.ListMediaToolDesktop
import com.kai.custom.tools.NotificationReaderDesktop
import com.kai.custom.tools.OpenCodeToolDesktop
import com.kai.custom.tools.ReadCalendarToolDesktop
import com.kai.custom.tools.ReadContactsToolDesktop
import com.kai.custom.tools.ReadLogsToolDesktop
import com.kai.custom.tools.ScanBluetoothToolDesktop
import com.kai.custom.tools.SetAlarmToolDesktop
import com.kai.custom.tools.ShellCommandTool
import com.kai.custom.tools.SpeakTextToolDesktop
import com.kai.custom.tools.telegramToolDefinitions
import com.kai.custom.tools.SshCommandTool
import com.kai.custom.tools.WifiInfoToolDesktop
import com.kai.custom.tools.WriteContactToolDesktop
import com.kai.custom.tools.SshConfigureHostTool
import com.kai.custom.tools.SshConnectTool
import com.kai.custom.tools.SshDisconnectTool
import com.kai.custom.tools.TodoWriteTool
import com.kai.custom.tools.WebFetchTool
import com.kai.custom.tools.WriteFileTool
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_send_notification_description
import kai.composeapp.generated.resources.tool_send_notification_name
import kotlinx.coroutines.Dispatchers
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.net.URI
import kotlin.coroutines.CoroutineContext

actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(CIO) {
    config(this)
}

actual fun getBackgroundDispatcher(): CoroutineContext = Dispatchers.IO

actual fun onDragAndDropEventDropped(event: DragAndDropEvent): PlatformFile? {
    if (event.dragData() is DragData.FilesList) {
        val dragData = event.dragData() as DragData.FilesList
        val filePath = dragData.readFiles().firstOrNull()
        if (filePath != null) {
            try {
                val fileUri = URI(filePath)
                val file = File(fileUri)

                if (file.exists()) {
                    return PlatformFile(file)
                }
            } catch (_: Exception) {
            }
        }
        return null
    } else {
        return null
    }
}

actual val BackIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack

actual val currentPlatform: Platform = run {
    val osName = System.getProperty("os.name", "").lowercase()
    when {
        "mac" in osName || "darwin" in osName -> Platform.Desktop.Mac
        "win" in osName -> Platform.Desktop.Windows
        else -> Platform.Desktop.Linux
    }
}

actual val defaultUiScale: Float = run {
    val base = if (currentPlatform is Platform.Desktop.Linux) 1.1f else 1.0f
    if (currentPlatform !is Platform.Desktop.Linux) return@run base
    // On Wayland/X11 Java's HiDPI auto-detection often fails; fall back to GDK env vars
    // so HiDPI users get a reasonable default before they touch the slider.
    val gdkScale = System.getenv("GDK_SCALE")?.toFloatOrNull()
    val gdkDpiScale = System.getenv("GDK_DPI_SCALE")?.toFloatOrNull()
    val envFactor = (gdkScale ?: 1f) * (gdkDpiScale ?: 1f)
    base * envFactor.coerceIn(0.5f, 4f)
}

actual val isEmailSupported: Boolean = true

actual val isSmsSupported: Boolean = false

actual val isTelegramSupported: Boolean = true

actual val isWhatsAppSupported: Boolean = true

actual val isNotificationsSupported: Boolean = false

actual val isSplinterlandsSupported: Boolean = true

actual val isShizukuSupported: Boolean = false

actual val isRootSupported: Boolean = true

actual fun isRootAvailable(): Boolean = com.kai.custom.root.AdminManager.isAdmin()

actual fun isShizukuPermissionGranted(): Boolean = false

actual fun requestShizukuPermission(onGranted: (() -> Unit)?) {}

actual fun getToolPermissionMap(): Map<String, List<String>> = emptyMap()

actual suspend fun compressImageBytes(bytes: ByteArray, mimeType: String): ByteArray {
    if (!mimeType.startsWith("image/")) return bytes
    return try {
        val inputStream = java.io.ByteArrayInputStream(bytes)
        val image = javax.imageio.ImageIO.read(inputStream) ?: return bytes
        val maxDim = 1024
        val scaled = if (image.width > maxDim || image.height > maxDim) {
            val scale = maxDim.toDouble() / maxOf(image.width, image.height)
            val newWidth = (image.width * scale).toInt()
            val newHeight = (image.height * scale).toInt()
            val resized = java.awt.image.BufferedImage(newWidth, newHeight, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val g2d = resized.createGraphics()
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2d.drawImage(image, 0, 0, newWidth, newHeight, null)
            g2d.dispose()
            resized
        } else {
            // Still need to convert to RGB for JPEG encoding (original might have alpha)
            val rgb = java.awt.image.BufferedImage(image.width, image.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val g2d = rgb.createGraphics()
            g2d.drawImage(image, 0, 0, null)
            g2d.dispose()
            rgb
        }
        val outputStream = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(scaled, "jpg", outputStream)
        outputStream.toByteArray()
    } catch (_: Exception) {
        bytes
    }
}

actual fun getAppFilesDirectory(): String {
    val userHome = System.getProperty("user.home")
    val kaiDir = File("$userHome/.kai")
    if (!kaiDir.exists()) {
        kaiDir.mkdirs()
    }
    return kaiDir.absolutePath
}

actual fun createSecureSettings(): Settings = EncryptedFileSettings()

actual fun createLegacySettings(): Settings? = null // Same storage location, no migration needed

actual fun getPlatformToolDefinitions(): List<ToolInfo> = buildList {
    addAll(CommonTools.commonToolDefinitions)
    add(ShellCommandTool.toolInfo)
    add(ProcessManagerTool.toolInfo)
    add(AdminTool.toolInfo)
    add(ReadFileTool.toolInfo)
    add(WriteFileTool.toolInfo)
    add(EditFileTool.toolInfo)
    add(GlobTool.toolInfo)
    add(GrepTool.toolInfo)
    add(ApplyPatchTool.toolInfo)
    add(TodoWriteTool.toolInfo)
    add(WebFetchTool.toolInfo)
    add(InternetSearchTool.toolInfo)
    add(OpenFileTool.toolInfo)
    add(SshCommandTool.toolInfo)
    add(SshConfigureHostTool.toolInfo)
    add(SshConnectTool.toolInfo)
    add(SshDisconnectTool.toolInfo)
    add(PhoneTools.deviceInfoToolInfo)
    add(PhoneTools.clipboardToolInfo)
    add(PhoneTools.networkInfoToolInfo)
    add(PhoneTools.batteryInfoToolInfo)
    add(PhoneTools.gpsLocationToolInfo)
    add(PhoneTools.wifiInfoToolInfo)
    add(PhoneTools.installedAppsToolInfo)
    add(PhoneTools.readCalendarToolInfo)
    add(PhoneTools.writeContactToolInfo)
    add(PhoneTools.scanBluetoothToolInfo)
    add(PhoneTools.listMediaToolInfo)
    add(PhoneTools.readLogsToolInfo)
    add(ListInstalledAppsToolDesktop.toolInfo)
    add(ListMediaToolDesktop.toolInfo)
    add(ReadLogsToolDesktop.toolInfo)
    add(ReadContactsToolDesktop.toolInfo)
    add(WriteContactToolDesktop.toolInfo)
    add(ReadCalendarToolDesktop.toolInfo)
    add(CreateCalendarEventToolDesktop.toolInfo)
    add(NotificationReaderDesktop.toolInfo)
    add(
        ToolInfo(
            id = "send_notification",
            name = "Send Notification",
            description = "Send a push notification to the device",
            nameRes = Res.string.tool_send_notification_name,
            descriptionRes = Res.string.tool_send_notification_description,
        ),
    )
    add(ScanBluetoothToolDesktop.toolInfo)
    add(WifiInfoToolDesktop.toolInfo)
    addAll(telegramToolDefinitions)
    add(PhoneTools.getPhoneStateToolInfo)
}

private val jsonIgnoreUnknown = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

actual fun getAvailableTools(): List<Tool> {
    val appSettings: AppSettings by inject(AppSettings::class.java)
    val memoryStore: MemoryStore by inject(MemoryStore::class.java)
    val mcpServerManager: McpServerManager by inject(McpServerManager::class.java)
    val taskStore: TaskStore by inject(TaskStore::class.java)
    val emailStore: EmailStore by inject(EmailStore::class.java)
    return buildList {
        addAll(CommonTools.getCommonTools(appSettings))
        if (appSettings.isMemoryEnabled()) {
            if (!mcpServerManager.isConnected("alt_memory")) {
                addAll(CommonTools.getMemoryTools(memoryStore))
            }
            addAll(listOf(HeartbeatTools.getPromoteLearningTool(memoryStore, appSettings)))
        }
        if (appSettings.isSchedulingEnabled()) {
            addAll(SchedulingTools.getSchedulingTools(taskStore))
        }
        if (appSettings.isToolEnabled(ShellCommandTool.schema.name, defaultEnabled = true)) {
            add(ShellCommandTool)
            add(ProcessManagerTool)
        }
        if (appSettings.isEmailEnabled()) {
            addAll(EmailTools.getEmailTools(emailStore))
        }
        if (appSettings.isToolEnabled(PhoneTools.deviceInfoToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "get_device_info",
                        description = "Get detailed device information including OS, architecture, and hardware specs",
                        parameters = emptyMap(),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any {
                        val osName = System.getProperty("os.name", "unknown")
                        val osVersion = System.getProperty("os.version", "unknown")
                        val osArch = System.getProperty("os.arch", "unknown")
                        val cpuCores = Runtime.getRuntime().availableProcessors()
                        val maxMemory = Runtime.getRuntime().maxMemory()
                        val totalMemory = Runtime.getRuntime().totalMemory()
                        val freeMemory = Runtime.getRuntime().freeMemory()
                        return mapOf(
                            "success" to true,
                            "os" to mapOf("name" to osName, "version" to osVersion, "architecture" to osArch),
                            "hardware" to mapOf("cpu_cores" to cpuCores),
                            "memory" to mapOf(
                                "max_memory_mb" to maxMemory / (1024 * 1024),
                                "total_memory_mb" to totalMemory / (1024 * 1024),
                                "free_memory_mb" to freeMemory / (1024 * 1024),
                                "physical_total_mb" to try {
                                    val os = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
                                    if (os is com.sun.management.OperatingSystemMXBean) os.getTotalMemorySize() / (1024 * 1024) else null
                                } catch (_: Exception) { null },
                                "physical_free_mb" to try {
                                    val os = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
                                    if (os is com.sun.management.OperatingSystemMXBean) os.getFreeMemorySize() / (1024 * 1024) else null
                                } catch (_: Exception) { null },
                                "total_disk_mb" to try {
                                    val root = java.io.File.listRoots().firstOrNull()
                                    root?.totalSpace?.div(1024 * 1024)
                                } catch (_: Exception) { null },
                                "free_disk_mb" to try {
                                    val root = java.io.File.listRoots().firstOrNull()
                                    root?.freeSpace?.div(1024 * 1024)
                                } catch (_: Exception) { null },
                            ),
                        )
                    }
                },
            )
        }
        if (appSettings.isToolEnabled(PhoneTools.clipboardToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "read_clipboard",
                        description = "Read the current content of the system clipboard",
                        parameters = emptyMap(),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any = try {
                        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                        val contents = clipboard.getContents(null)
                        val text = contents?.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor)?.toString()
                        mapOf("success" to true, "content" to (text ?: ""), "has_content" to (text != null))
                    } catch (e: Exception) {
                        mapOf("success" to false, "error" to "Failed to read clipboard: ${e.message}")
                    }
                },
            )
        }
        if (appSettings.isToolEnabled(PhoneTools.networkInfoToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "get_network_info",
                        description = "Get current network connectivity details (interfaces, IP addresses)",
                        parameters = emptyMap(),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any = try {
                        var ipAddress = ""
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
                        mapOf("success" to true, "ip_address" to ipAddress)
                    } catch (e: Exception) {
                        mapOf("success" to false, "error" to "Failed to get network info: ${e.message}")
                    }
                },
            )
        }
        if (appSettings.isToolEnabled("send_notification")) {
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
        if (appSettings.isToolEnabled(PhoneTools.batteryInfoToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "get_battery_info",
                        description = "Get battery level and charging status (Desktop)",
                        parameters = emptyMap(),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any = try {
                        val os = System.getProperty("os.name").lowercase()
                        val result = when {
                            os.contains("win") -> {
                                val proc = Runtime.getRuntime().exec(arrayOf("wmic", "path", "Win32_Battery", "Get", "EstimatedChargeRemaining,BatteryStatus"))
                                val output = proc.inputStream.bufferedReader().readText().trim()
                                val lines = output.lines().filter { it.isNotBlank() }
                                if (lines.size >= 2) {
                                    val parts = lines[1].trim().split("\\s+".toRegex())
                                    val pct = parts.getOrNull(0)?.toIntOrNull() ?: -1
                                    val status = parts.getOrNull(1)?.toIntOrNull() ?: -1
                                    val charging = status == 2 || status == 6 || status == 7
                                    mapOf("success" to true, "level_percent" to pct, "is_charging" to charging, "health" to "unknown")
                                } else {
                                    mapOf("success" to false, "error" to "No battery found")
                                }
                            }

                            os.contains("mac") -> {
                                val proc = Runtime.getRuntime().exec(arrayOf("pmset", "-g", "batt"))
                                val output = proc.inputStream.bufferedReader().readText().trim()
                                val match = Regex("(\\d+)%").find(output)
                                val pct = match?.groupValues?.get(1)?.toIntOrNull() ?: -1
                                val charging = output.contains("charging") || output.contains("AC Power")
                                mapOf("success" to true, "level_percent" to pct, "is_charging" to charging, "health" to "unknown")
                            }

                            os.contains("nux") || os.contains("nix") -> {
                                val capFile = java.io.File("/sys/class/power_supply/BAT0/capacity")
                                val statusFile = java.io.File("/sys/class/power_supply/BAT0/status")
                                if (capFile.exists()) {
                                    val pct = capFile.readText().trim().toIntOrNull() ?: -1
                                    val charging = if (statusFile.exists()) statusFile.readText().trim() == "Charging" else false
                                    mapOf("success" to true, "level_percent" to pct, "is_charging" to charging, "health" to "unknown")
                                } else {
                                    mapOf("success" to false, "error" to "No battery found")
                                }
                            }

                            else -> mapOf("success" to false, "error" to "Unsupported OS: $os")
                        }
                        result
                    } catch (e: Exception) {
                        mapOf("success" to false, "error" to "Failed to get battery info: ${e.message}")
                    }
                },
            )
        }
        if (appSettings.isToolEnabled(PhoneTools.gpsLocationToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "get_gps_location",
                        description = "Get approximate location via GeoIP (ipinfo.io)",
                        parameters = emptyMap(),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any = try {
                        val url = java.net.URI("https://ipinfo.io/json").toURL()
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 10_000
                        conn.readTimeout = 10_000
                        val body = conn.inputStream.bufferedReader().readText()
                        val data = jsonIgnoreUnknown
                            .decodeFromString<Map<String, String>>(body)
                        val locParts = data["loc"]?.split(",")?.map { it.trim() } ?: emptyList()
                        val lat = locParts.getOrNull(0)?.toDoubleOrNull()
                        val lon = locParts.getOrNull(1)?.toDoubleOrNull()
                        mapOf(
                            "success" to true,
                            "latitude" to lat,
                            "longitude" to lon,
                            "city" to (data["city"] ?: ""),
                            "region" to (data["region"] ?: ""),
                            "country" to (data["country"] ?: ""),
                            "ip" to (data["ip"] ?: ""),
                            "source" to "GeoIP (ipinfo.io)",
                            "accuracy" to "city-level (~25km)",
                        )
                    } catch (e: Exception) {
                        mapOf("success" to false, "error" to "Failed to get location: ${e.message}")
                    }
                },
            )
        }
        if (appSettings.isToolEnabled(PhoneTools.wifiInfoToolInfo.id)) {
            add(WifiInfoToolDesktop)
        }
        if (appSettings.isToolEnabled(PhoneTools.installedAppsToolInfo.id)) {
            add(ListInstalledAppsToolDesktop)
        }
        if (appSettings.isToolEnabled(PhoneTools.listMediaToolInfo.id)) {
            add(ListMediaToolDesktop)
        }
        if (appSettings.isToolEnabled(PhoneTools.readLogsToolInfo.id)) {
            add(ReadLogsToolDesktop)
        }
        if (appSettings.isToolEnabled(PhoneTools.readContactsToolInfo.id)) {
            add(ReadContactsToolDesktop)
        }
        if (appSettings.isToolEnabled(PhoneTools.writeContactToolInfo.id)) {
            add(WriteContactToolDesktop)
        }
        if (appSettings.isToolEnabled(PhoneTools.readCalendarToolInfo.id)) {
            add(ReadCalendarToolDesktop)
        }
        if (appSettings.isToolEnabled(PhoneTools.scanBluetoothToolInfo.id)) {
            add(ScanBluetoothToolDesktop)
        }
        if (appSettings.isToolEnabled(OpenCodeToolDesktop.toolInfo.id)) {
            add(OpenCodeToolDesktop)
        }
        if (appSettings.isToolEnabled(SpeakTextToolDesktop.toolInfo.id)) {
            add(SpeakTextToolDesktop)
        }
        if (appSettings.isToolEnabled(NotificationReaderDesktop.toolInfo.id)) {
            add(NotificationReaderDesktop)
        }
        if (appSettings.isToolEnabled(PhoneTools.getPhoneStateToolInfo.id)) {
            add(
                object : Tool {
                    override val schema = ToolSchema(
                        name = "get_phone_state",
                        description = "Get network connectivity status (desktop — no cellular radio)",
                        parameters = emptyMap(),
                    )
                    override suspend fun execute(args: Map<String, Any>): Any = try {
                        var hasWifi = false; var hasEthernet = false
                        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                        while (interfaces.hasMoreElements()) {
                            val intf = interfaces.nextElement()
                            if (intf.isUp && !intf.isLoopback) {
                                val name = intf.name.lowercase()
                                if ("wlan" in name || "wi-fi" in name || "wifi" in name) hasWifi = true
                                if ("eth" in name || "ethernet" in name) hasEthernet = true
                            }
                        }
                        mapOf(
                            "success" to true,
                            "has_cellular" to false,
                            "has_wifi" to hasWifi,
                            "has_ethernet" to hasEthernet,
                            "connection_type" to when {
                                hasWifi -> "wifi"
                                hasEthernet -> "ethernet"
                                else -> "unknown"
                            },
                        )
                    } catch (e: Exception) {
                        mapOf("success" to false, "error" to "Failed to get network state: ${e.message}")
                    }
                },
            )
        }
        if (appSettings.isToolEnabled(SetAlarmToolDesktop.toolInfo.id)) {
            add(SetAlarmToolDesktop)
        }
        if (appSettings.isToolEnabled(CreateCalendarEventToolDesktop.toolInfo.id)) {
            add(CreateCalendarEventToolDesktop)
        }
        if (appSettings.isToolEnabled(AdminTool.schema.name)) {
            add(AdminTool)
        }
        if (appSettings.isSandboxEnabled()) {
            add(ReadFileTool)
            add(WriteFileTool)
            add(EditFileTool)
            add(GlobTool)
            add(GrepTool)
            add(ApplyPatchTool)
            add(TodoWriteTool)
            add(WebFetchTool)
            add(InternetSearchTool)
            add(OpenFileTool)
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
        if (isWhatsAppSupported) {
            val whatsAppStore: com.kai.custom.data.WhatsAppStore by inject(com.kai.custom.data.WhatsAppStore::class.java)
            val whatsAppLifecycleManager: com.kai.custom.whatsapp.WhatsAppLifecycleManager by inject(com.kai.custom.whatsapp.WhatsAppLifecycleManager::class.java)
            if (whatsAppStore.isWhatsAppEnabled()) {
                addAll(com.kai.custom.tools.getWhatsAppAdminTools(
                    appSettings = appSettings,
                    restartBridge = { whatsAppLifecycleManager.restart() },
                    updateBridgeConfig = { whatsAppLifecycleManager.updateBridgeConfig() },
                ))
            }
        }
        if (isTelegramSupported) {
            val telegramStore: com.kai.custom.data.TelegramStore by inject(com.kai.custom.data.TelegramStore::class.java)
            val telegramPoller: com.kai.custom.telegram.TelegramPoller by inject(com.kai.custom.telegram.TelegramPoller::class.java)
            if (telegramStore.isTelegramEnabled() && telegramStore.getBotToken().isNotBlank()) {
                addAll(com.kai.custom.tools.getTelegramTools(telegramStore, telegramPoller))
            }
        }
        addAll(mcpServerManager.getEnabledMcpTools())
    }
}

actual fun openUrl(url: String): Boolean = try {
    java.awt.Desktop.getDesktop().browse(URI(url))
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
    // No system back gesture on desktop
}

actual suspend fun saveFileToDevice(bytes: ByteArray, baseName: String, extension: String) {
    val file = FileKit.openFileSaver(suggestedName = baseName, defaultExtension = extension)
    file?.write(bytes)
}

actual suspend fun saveFileToDevice(path: String, baseName: String, extension: String) {
    val bytes = java.io.File(path).readBytes()
    saveFileToDevice(bytes, baseName, extension)
}

/**
 * Posts a native OS notification. Each platform has its own surface:
 *   - macOS: `osascript` invokes the user-facing Notification Center.
 *   - Linux: `notify-send` (libnotify) is the freedesktop standard and ships in most distros.
 *   - Windows: AWT [java.awt.SystemTray] briefly registers a tray icon to display a balloon
 *     toast, then removes it so we don't leave a persistent tray entry.
 * All paths swallow failures — if the OS hook is missing the in-app heartbeat banner still fires.
 */
actual fun sendHeartbeatNotification(title: String, body: String) {
    try {
        when (currentPlatform as Platform.Desktop) {
            Platform.Desktop.Mac -> {
                // AppleScript string literals: backslash and double-quote need escaping.
                val safeTitle = title.replace("\\", "\\\\").replace("\"", "\\\"")
                val safeBody = body.replace("\\", "\\\\").replace("\"", "\\\"")
                ProcessBuilder("osascript", "-e", "display notification \"$safeBody\" with title \"$safeTitle\"")
                    .start()
            }

            Platform.Desktop.Windows -> {
                if (!java.awt.SystemTray.isSupported()) return
                val tray = java.awt.SystemTray.getSystemTray()
                // 1×1 transparent placeholder — Windows auto-supplies a fallback icon for the toast.
                val image = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val trayIcon = java.awt.TrayIcon(image, "Kai")
                trayIcon.isImageAutoSize = true
                tray.add(trayIcon)
                trayIcon.displayMessage(title, body, java.awt.TrayIcon.MessageType.INFO)
                java.util.Timer(true).schedule(
                    object : java.util.TimerTask() {
                        override fun run() = tray.remove(trayIcon)
                    },
                    5_000,
                )
            }

            Platform.Desktop.Linux -> {
                // `--` terminator prevents a title or body starting with `-` from being parsed as a flag.
                ProcessBuilder("notify-send", "--", title, body).start()
            }
        }
    } catch (_: Exception) {
        // notify-send missing, AWT headless, sandboxed osascript, etc. — fall back silently.
    }
}

actual fun openTtsSettings() = Unit

actual fun openBatteryOptimizationSettings() = Unit
actual fun isBatteryOptimizationDisabled(): Boolean = true
actual fun defaultOpenAICompatibleBaseUrl(): String = "http://localhost:11434/v1"
