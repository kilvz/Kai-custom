package com.kai.custom.whatsapp

import com.kai.custom.SandboxController
import com.kai.custom.SandboxSessions
import com.kai.custom.data.AppSettings
import com.kai.custom.data.SharedJson
import com.kai.custom.data.WhatsAppStore
import com.kai.custom.mcp.McpServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WhatsAppLifecycleManager(
    private val sandboxController: SandboxController,
    private val mcpServerManager: McpServerManager,
    private val appSettings: AppSettings,
    private val whatsAppStore: WhatsAppStore,
) {
    companion object {
        private const val SERVER_ID = "whatsapp"
        private const val RETRY_INTERVAL_MS = 3_000L
        private const val BRIDGE_JS_URL = "https://raw.githubusercontent.com/kilvz/Kai-custom/main/sandbox/whatsapp-bridge/bridge.js"
    }

    @Volatile private var started = false

    @Volatile private var connected = false
    private var retryJob: Job? = null
    private var setupJob: Job? = null

    suspend fun ensureInstalled(): Boolean {
        if (appSettings.isWhatsAppInstalled()) return true
        val ok = installIfNeeded()
        if (ok) appSettings.setWhatsAppInstalled(true)
        return ok
    }

    suspend fun setupAndStart() {
        if (started) return
        writeBridgeJs(force = false)
        // Verify baileys is actually available on disk — don't trust the stored flag
        // (it can be stale after an aborted restart). If not found, try to install.
        val check = sandboxController.executeCommand(
            command = "cd /root/whatsapp-bridge && node -e 'require(\"@whiskeysockets/baileys\"); console.log(1)' 2>/dev/null",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
        )
        if (check.trim() != "1") {
            appSettings.setWhatsAppInstalled(false)
            return
        }
        appSettings.setWhatsAppInstalled(true)
        started = true

        // Apply Baileys v7 RC auth handshake fixes (idempotent)
        sandboxController.executeCommand(
            command = "cd /root/whatsapp-bridge && " +
                "sed -i 's/passive: !0/passive: !1/g; s/passive: true/passive: false/g; s/lidDbMigrated:[^,}]*[,]*//g' " +
                "node_modules/@whiskeysockets/baileys/lib/Utils/validate-connection.js 2>/dev/null; echo PATCH_OK",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
            timeoutSeconds = 15,
        )

        mcpServerManager.registerBuiltInServer(
            id = SERVER_ID,
            name = "WhatsApp",
            url = "http://127.0.0.1:8317/mcp",
        )

        setupJob = CoroutineScope(Dispatchers.Default).launch {
            writeBridgeConfig()
            startBridgeServer()
        }

        retryJob = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                delay(RETRY_INTERVAL_MS)
                if (connected || !started) break
                if (tryConnect()) break
            }
        }
    }

    private suspend fun tryConnect(): Boolean {
        if (connected) return true
        val result = mcpServerManager.connectAndDiscoverTools(SERVER_ID)
        if (result.isFailure) return false
        refreshAuthState()
        refreshQrCode()
        connected = true
        return true
    }

    fun isConnected(): Boolean = connected

    suspend fun restart() {
        stop()
        delay(2000)
        // Check if baileys is still available before resetting the flag
        val check = sandboxController.executeCommand(
            command = "cd /root/whatsapp-bridge && node -e 'require(\"@whiskeysockets/baileys\"); console.log(1)' 2>/dev/null",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
        )
        if (check.trim() != "1") {
            appSettings.setWhatsAppInstalled(false)
            ensureInstalled()
        }
        setupAndStart()
    }

    suspend fun updateBridgeConfig() {
        writeBridgeConfig()
        // Restart bridge so it picks up the new config
        restart()
    }

    suspend fun stop() {
        started = false
        connected = false
        retryJob?.cancel()
        retryJob = null
        setupJob?.cancel()
        setupJob = null
        try {
            sandboxController.executeCommand(
                command = "pkill -f 'whatsapp-bridge' 2>/dev/null || true",
                sessionId = SandboxSessions.SYSTEM,
                useRoot = false,
            )
            mcpServerManager.removeBuiltInServer(SERVER_ID)
        } catch (_: Exception) {
        }
    }

    suspend fun refreshAuthState() {
        try {
            val client = mcpServerManager.getClient(SERVER_ID) ?: return
            val resultStr = client.callTool("is_authenticated", buildJsonObject { })
            val root = SharedJson.parseToJsonElement(resultStr).jsonObject
            val authenticated = root["connected"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            appSettings.setWhatsAppAuthenticated(authenticated)
        } catch (_: Exception) {
        }
    }

    suspend fun requestPairingCode(phone: String): String? {
        try {
            val client = mcpServerManager.getClient(SERVER_ID) ?: return null
            val resultStr = client.callTool(
                "request_pairing_code",
                buildJsonObject {
                    put("phone", JsonPrimitive(phone))
                },
            )
            if (resultStr.isBlank()) return null
            val root = SharedJson.parseToJsonElement(resultStr).jsonObject
            // Check for error responses from the bridge
            val error = root["error"]?.jsonPrimitive?.content
            if (!error.isNullOrBlank()) return null
            val formatted = root["formatted"]?.jsonPrimitive?.content
                ?: root["code"]?.jsonPrimitive?.content
            return formatted?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            return null
        }
    }

    suspend fun refreshQrCode() {
        try {
            val client = mcpServerManager.getClient(SERVER_ID) ?: return
            val resultStr = client.callTool("get_qr_code", buildJsonObject { })
            val root = SharedJson.parseToJsonElement(resultStr).jsonObject
            val qr = root["qr"]?.jsonPrimitive?.content ?: ""
            if (qr.isNotBlank()) {
                appSettings.setWhatsAppQrCode(qr)
            }
            val authenticated = root["connected"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            if (authenticated) {
                appSettings.setWhatsAppAuthenticated(true)
                appSettings.setWhatsAppQrCode("")
            }
        } catch (_: Exception) {
        }
    }

    suspend fun forceRefreshQr() {
        try {
            stop()
            delay(2000)
            appSettings.setWhatsAppAuthenticated(false)
            appSettings.setWhatsAppQrCode("")
            setupAndStart()
            // Wait for QR to appear
            repeat(30) {
                delay(1000)
                tryConnect()
                refreshQrCode()
                if (appSettings.getWhatsAppQrCode().isNotBlank()) return
            }
        } catch (_: Exception) {
        }
    }

    suspend fun resetBridge() {
        stop()
        delay(1000)
        sandboxController.executeCommand(
            command = "rm -rf /root/whatsapp-bridge/auth_info 2>/dev/null; echo RESET_OK",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
        )
        appSettings.setWhatsAppAuthenticated(false)
        appSettings.setWhatsAppQrCode("")
        setupAndStart()
    }

    private suspend fun writeBridgeJs(force: Boolean) {
        if (!force) {
            val exists = sandboxController.executeCommand(
                command = "test -f /root/whatsapp-bridge/bridge.js && echo OK",
                sessionId = SandboxSessions.SYSTEM,
                useRoot = false,
            )
            if (exists.trim() == "OK") return
        }
        sandboxController.executeCommand(
            command = "mkdir -p /root/whatsapp-bridge && curl -sL '$BRIDGE_JS_URL' -o /root/whatsapp-bridge/bridge.js",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
            timeoutSeconds = 30,
        )
    }

    /**
     * Delegates to SandboxController.installWhatsAppBridge() which has the
     * canonical install pipeline (Node.js download, npm install, verification).
     */
    private suspend fun installIfNeeded(): Boolean = sandboxController.installWhatsAppBridge()

    private suspend fun writeBridgeConfig() {
        val configJson = buildJsonObject {
            put(
                "browser",
                buildJsonArray {
                    add(JsonPrimitive(appSettings.getBaileysBrowserName()))
                    add(JsonPrimitive("Desktop"))
                    add(JsonPrimitive(appSettings.getBaileysBrowserVersion()))
                },
            )
            put("markOnlineOnConnect", JsonPrimitive(appSettings.getBaileysMarkOnline()))
            put("syncFullHistory", JsonPrimitive(appSettings.getBaileysSyncHistory()))
            put("generateHighQualityLinkPreview", JsonPrimitive(appSettings.getBaileysLinkPreviews()))
            put("shouldSyncHistoryMsg", JsonPrimitive(appSettings.getBaileysSyncHistory()))
        }
        val escaped = configJson.toString().replace("'", "'\\''")
        sandboxController.executeCommand(
            command = "echo '$escaped' > /root/whatsapp-bridge/config.json",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
            timeoutSeconds = 5,
        )
    }

    private suspend fun startBridgeServer() {
        sandboxController.executeCommand(
            command = "fuser -k 8317/tcp 2>/dev/null; setsid nohup node /root/whatsapp-bridge/bridge.js > /tmp/whatsapp-bridge.log 2>&1 &",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
            timeoutSeconds = 10,
        )
    }
}
