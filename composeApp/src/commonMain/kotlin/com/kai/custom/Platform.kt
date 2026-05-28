package com.kai.custom

import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import kotlin.coroutines.CoroutineContext

expect fun httpClient(config: HttpClientConfig<*>.() -> Unit = {}): HttpClient

expect fun createSecureSettings(): Settings

expect fun createLegacySettings(): Settings?

expect fun getBackgroundDispatcher(): CoroutineContext

expect fun onDragAndDropEventDropped(event: DragAndDropEvent): PlatformFile?

expect val BackIcon: ImageVector

sealed class Platform(val displayName: String) {
    sealed class Mobile(displayName: String) : Platform(displayName) {
        data object Android : Mobile("Android")
        data object Ios : Mobile("iOS")
    }

    sealed class Desktop(displayName: String) : Platform(displayName) {
        data object Mac : Desktop("macOS")
        data object Windows : Desktop("Windows")
        data object Linux : Desktop("Linux")
    }

    data object Web : Platform("Web")
}

expect val currentPlatform: Platform

expect val defaultUiScale: Float

expect fun getAppFilesDirectory(): String

expect fun getAvailableTools(): List<Tool>

/**
 * Returns all raw tool definitions available on this platform.
 * The returned tools have no isEnabled state set - that's handled by RemoteDataRepository.
 * Unlike getAvailableTools(), this returns all tools regardless of enabled state.
 */
expect fun getPlatformToolDefinitions(): List<ToolInfo>

expect val isEmailSupported: Boolean

/**
 * True only on Android builds where `READ_SMS` is declared in the merged manifest.
 * The `foss` flavor declares it; other platforms return false.
 */
expect val isSmsSupported: Boolean

/**
 * True only on Android builds where `KaiNotificationListenerService` is declared
 * in the merged manifest. The `foss` flavor declares it; other platforms return false.
 */
expect val isNotificationsSupported: Boolean

expect val isSplinterlandsSupported: Boolean

/**
 * True only on Android builds. Shizuku API is always compiled in; this flag
 * indicates the feature is available in the build. Runtime checks determine
 * whether Shizuku is actually installed and permission is granted.
 */
expect val isShizukuSupported: Boolean

expect fun isShizukuPermissionGranted(): Boolean

expect fun requestShizukuPermission(onGranted: (() -> Unit)? = null)

expect suspend fun compressImageBytes(bytes: ByteArray, mimeType: String): ByteArray

expect fun openUrl(url: String): Boolean

expect fun openTtsSettings()

expect fun openBatteryOptimizationSettings()

/**
 * Returns the default base URL for the OpenAI-Compatible service (local Ollama).
 * On Android emulators, returns the host-loopback address (10.0.2.2) instead of localhost.
 */
expect fun defaultOpenAICompatibleBaseUrl(): String

/**
 * Returns true if battery optimization is already disabled for this app.
 * On non-Android platforms, returns true (no battery optimization concerns).
 */
expect fun isBatteryOptimizationDisabled(): Boolean

@androidx.compose.runtime.Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

expect fun decodeToImageBitmap(bytes: ByteArray): ImageBitmap?

expect suspend fun saveFileToDevice(bytes: ByteArray, baseName: String, extension: String)

/**
 * Fires a background push notification for a heartbeat that produced a non-trivial
 * response. Android additionally wires a tap-to-open-heartbeat deep link via its
 * PendingIntent; iOS/desktop just surface the message in the OS notification center
 * without deep-linking back to the conversation. No-op on web.
 */
expect fun sendHeartbeatNotification(title: String, body: String)
