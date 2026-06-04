package com.kai.custom.whatsapp

import com.kai.custom.SandboxController
import com.kai.custom.SandboxSessions
import com.kai.custom.data.AppSettings
import com.kai.custom.data.SharedJson
import com.kai.custom.data.WhatsAppStore
import com.kai.custom.mcp.McpServerManager
import com.kai.custom.whatsapp.BRIDGE_JS_BASE64
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
        private const val RETRY_INTERVAL_MS = 10_000L
    }

    private var started = false
    private var connected = false
    private var retryJob: Job? = null

    suspend fun ensureInstalled(): Boolean {
        if (appSettings.isWhatsAppInstalled()) return true
        val ok = installIfNeeded()
        if (ok) appSettings.setWhatsAppInstalled(true)
        return ok
    }

    suspend fun setupAndStart() {
        if (started) return
        writeBridgeJs()
        if (appSettings.isWhatsAppInstalled()) {
            val check = sandboxController.executeCommand(
                command = "cd /root/whatsapp-bridge && node -e 'require(\"@whiskeysockets/baileys\"); console.log(1)' 2>/dev/null",
                sessionId = SandboxSessions.SYSTEM,
                useRoot = false,
            )
            if (check.trim() != "1") {
                appSettings.setWhatsAppInstalled(false)
                return
            }
        } else {
            return
        }
        started = true

        mcpServerManager.registerBuiltInServer(
            id = SERVER_ID,
            name = "WhatsApp",
            url = "http://127.0.0.1:8317/mcp",
        )

        CoroutineScope(Dispatchers.Default).launch {
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
        connected = true
        return true
    }

    fun isConnected(): Boolean = connected

    suspend fun restart() {
        stop()
        delay(2000)
        appSettings.setWhatsAppInstalled(false)
        ensureInstalled()
        setupAndStart()
    }

    suspend fun stop() {
        started = false
        connected = false
        retryJob?.cancel()
        retryJob = null
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
            val auth = root["connected"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            whatsAppStore.setWhatsAppAuthenticated(auth)
        } catch (_: Exception) {
        }
    }

    suspend fun refreshQrCode() {
        try {
            val client = mcpServerManager.getClient(SERVER_ID) ?: return
            val resultStr = client.callTool("get_qr_code", buildJsonObject { })
            val root = SharedJson.parseToJsonElement(resultStr).jsonObject
            val authenticated = root["authenticated"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val qr = root["qr"]?.jsonPrimitive?.content ?: ""
            if (authenticated) {
                whatsAppStore.setWhatsAppAuthenticated(true)
                whatsAppStore.setWhatsAppQrCode("")
            } else if (qr.isNotBlank()) {
                whatsAppStore.setWhatsAppQrCode(qr)
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun writeBridgeJs() {
        val bridgeDir = "/root/whatsapp-bridge"

        sandboxController.executeCommand(
            command = "mkdir -p $bridgeDir",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
            timeoutSeconds = 5,
        )

        sandboxController.executeCommand(
            command = "cat > $bridgeDir/bridge.js.b64 << 'ENDB64'\n${BRIDGE_JS_BASE64}\nENDB64",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
            timeoutSeconds = 30,
        )

        sandboxController.executeCommand(
            command = "base64 -d $bridgeDir/bridge.js.b64 > $bridgeDir/bridge.js && rm $bridgeDir/bridge.js.b64",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
            timeoutSeconds = 30,
        )
    }

    private suspend fun installIfNeeded(): Boolean {
        val bridgeDir = "/root/whatsapp-bridge"

        writeBridgeJs()

        val npmOk = sandboxController.executeCommand(
            command = "cd $bridgeDir && node -e 'require(\"@whiskeysockets/baileys\"); console.log(1)' 2>/dev/null",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
        )
        if (npmOk.trim() == "1") return true

        val nodeInstallCmd = buildString {
            append("NODE_VER=v22.14.0; ")
            append("ARCH=\$(uname -m); ")
            append("case \"\$ARCH\" in aarch64) NA=arm64 ;; x86_64) NA=x64 ;; armv7l) NA=armv7l ;; *) echo \"unsupported arch \$ARCH\"; exit 1 ;; esac; ")
            append("if ! command -v node >/dev/null 2>&1 || [ \"\$(node --version 2>/dev/null | cut -d. -f1 | tr -d v)\" -lt 20 ]; then ")
            append("  mkdir -p /usr/local/node22 && ")
            append("  curl -sL \"https://nodejs.org/dist/\${NODE_VER}/node-\${NODE_VER}-linux-\${NA}.tar.xz\" | tar -xJ -C /usr/local/node22 --strip-components=1 2>&1 && ")
            append("  ln -sf /usr/local/node22/bin/node /usr/local/bin/node && ")
            append("  ln -sf /usr/local/node22/bin/npm /usr/local/bin/npm && ")
            append("  ln -sf /usr/local/node22/bin/npx /usr/local/bin/npx; ")
            append("fi; ")
            append("echo NODE_OK")
        }
        sandboxController.executeCommand(
            command = nodeInstallCmd,
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
            timeoutSeconds = 120,
        )

        sandboxController.executeCommand(
            command = "cd $bridgeDir && npm init -y 2>/dev/null && npm install --no-bin-links @whiskeysockets/baileys @modelcontextprotocol/sdk qrcode pino 2>&1",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
            timeoutSeconds = 180,
        )

        val verify = sandboxController.executeCommand(
            command = "cd $bridgeDir && node -e 'require(\"@whiskeysockets/baileys\"); console.log(1)' 2>/dev/null",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = false,
        )
        return verify.trim() == "1"
    }

    private suspend fun writeBridgeConfig() {
        val configJson = buildJsonObject {
            put("browser", buildJsonArray {
                add(JsonPrimitive(appSettings.getBaileysBrowserName()))
                add(JsonPrimitive("Chrome"))
                add(JsonPrimitive(appSettings.getBaileysBrowserVersion()))
            })
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
            command = "setsid nohup node /root/whatsapp-bridge/bridge.js > /tmp/whatsapp-bridge.log 2>&1 &",
            sessionId = SandboxSessions.SYSTEM,
            useRoot = true,
            timeoutSeconds = 5,
        )
    }
}
