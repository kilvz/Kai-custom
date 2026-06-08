package com.kai.custom.whatsapp

import kotlinx.coroutines.*
import java.io.File
import java.io.FileWriter
import java.net.HttpURLConnection
import java.net.URI

class DesktopWhatsAppBridgeManager {
    private var bridgeProcess: Process? = null
    private var isRunning = false
    private var installDone = false

    companion object {
        private const val BRIDGE_PORT = 8317
        private const val BRIDGE_JS_URL = "https://raw.githubusercontent.com/kilvz/Kai-custom/main/sandbox/whatsapp-bridge/bridge.js"
        private const val HEALTH_URL = "http://127.0.0.1:8317/health"
    }

    val isConnected: Boolean get() = isRunning && checkHealth()

    fun getBridgeDir(): File {
        val home = System.getProperty("user.home")
        return File(home, ".kai/whatsapp-bridge").also { it.mkdirs() }
    }

    suspend fun ensureInstalled(): Boolean {
        if (installDone) return true

        return withContext(Dispatchers.IO) {
            try {
                val nodeCheck = ProcessBuilder(
                    if (System.getProperty("os.name").lowercase().contains("windows")) {
                        listOf("where", "node")
                    } else {
                        listOf("which", "node")
                    },
                ).redirectErrorStream(true).start()
                nodeCheck.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
                if (nodeCheck.exitValue() != 0) {
                    println("[DesktopWhatsAppBridge] Node.js not found. Install from https://nodejs.org")
                    return@withContext false
                }

                val bridgeDir = getBridgeDir()
                val bridgeJs = File(bridgeDir, "bridge.js")
                if (!bridgeJs.exists()) {
                    try {
                        val url = URI(BRIDGE_JS_URL).toURL()
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 15000
                        conn.inputStream.bufferedReader().readText().let { content ->
                            bridgeJs.writeText(content)
                        }
                    } catch (e: Exception) {
                        println("[DesktopWhatsAppBridge] Failed to download bridge.js: ${e.message}")
                        return@withContext false
                    }
                }

                val packageJson = File(bridgeDir, "package.json")
                if (!packageJson.exists()) {
                    packageJson.writeText("""{"name":"whatsapp-bridge","version":"1.0.0","private":true}""")
                }

                val nodeModules = File(bridgeDir, "node_modules")
                if (!nodeModules.isDirectory) {
                    println("[DesktopWhatsAppBridge] Installing npm dependencies...")
                    val npmCmd = if (System.getProperty("os.name").lowercase().contains("windows")) {
                        listOf("cmd.exe", "/c", "cd /d \"${bridgeDir.absolutePath}\" && npm install @whiskeysockets/baileys qrcode-terminal")
                    } else {
                        listOf("bash", "-c", "cd \"${bridgeDir.absolutePath}\" && npm install @whiskeysockets/baileys qrcode-terminal")
                    }
                    val npmProc = ProcessBuilder(npmCmd)
                        .redirectErrorStream(true)
                        .start()
                    val done = npmProc.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
                    if (!done || npmProc.exitValue() != 0) {
                        val err = npmProc.inputStream.reader().readText()
                        println("[DesktopWhatsAppBridge] npm install failed: $err")
                        return@withContext false
                    }
                }

                installDone = true
                true
            } catch (e: Exception) {
                println("[DesktopWhatsAppBridge] Install failed: ${e.message}")
                false
            }
        }
    }

    suspend fun start(): Boolean {
        if (isRunning) return true
        if (!ensureInstalled()) return false

        return withContext(Dispatchers.IO) {
            try {
                val bridgeDir = getBridgeDir()
                val cmd = if (System.getProperty("os.name").lowercase().contains("windows")) {
                    listOf(
                        "cmd.exe",
                        "/c",
                        "cd /d \"${bridgeDir.absolutePath}\" && node bridge.js",
                    )
                } else {
                    listOf("bash", "-c", "cd \"${bridgeDir.absolutePath}\" && node bridge.js")
                }

                bridgeProcess = ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start()

                Thread.sleep(3000)

                isRunning = checkHealth()
                if (!isRunning) {
                    println("[DesktopWhatsAppBridge] Bridge started but health check failed")
                }
                isRunning
            } catch (e: Exception) {
                println("[DesktopWhatsAppBridge] Start failed: ${e.message}")
                false
            }
        }
    }

    fun stop() {
        bridgeProcess?.destroyForcibly()
        bridgeProcess = null
        isRunning = false
    }

    fun requestPairingCode(phone: String = "") {
        if (!isRunning) return
        try {
            val url = URI("http://127.0.0.1:$BRIDGE_PORT/request-pairing-code").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.doOutput = true
            val body = """{"phone":"$phone"}"""
            conn.outputStream.write(body.toByteArray())
            val response = conn.inputStream.bufferedReader().readText()
            println("[DesktopWhatsAppBridge] Pairing response: $response")
        } catch (e: Exception) {
            println("[DesktopWhatsAppBridge] Pairing request failed: ${e.message}")
        }
    }

    fun getBridgeLog(): String = try {
        bridgeProcess?.inputStream?.bufferedReader()?.readText() ?: "No logs"
    } catch (_: Exception) {
        "No logs"
    }

    private fun checkHealth(): Boolean = try {
        val url = URI(HEALTH_URL).toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 2000
        conn.responseCode == 200
    } catch (_: Exception) {
        false
    }
}
