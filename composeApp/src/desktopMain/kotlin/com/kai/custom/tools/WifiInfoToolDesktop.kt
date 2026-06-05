package com.kai.custom.tools

import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolSchema

object WifiInfoToolDesktop : Tool {
    override val schema = ToolSchema(
        name = "get_wifi_info",
        description = "Get current WiFi connection information",
        parameters = mapOf(
            "action" to ParameterSchema(type = "string", description = "'status' for current connection, 'scan' for available networks (default: status)", required = false),
        ),
    )

    val toolInfo = PhoneTools.wifiInfoToolInfo

    override suspend fun execute(args: Map<String, Any>): Any {
        val action = (args["action"] as? String)?.lowercase() ?: "status"
        val os = System.getProperty("os.name").lowercase()

        return try {
            when {
                os.contains("windows") -> getWindowsWifi(action)
                os.contains("linux") -> getLinuxWifi(action)
                os.contains("mac") -> getMacWifi(action)
                else -> mapOf("success" to false, "error" to "Unsupported OS")
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to get WiFi info: ${e.message}")
        }
    }

    private fun getWindowsWifi(action: String): Map<String, Any> {
        if (action == "scan") {
            val proc = ProcessBuilder("cmd.exe", "/c", "netsh wlan show networks mode=Bssid").redirectErrorStream(true).start()
            proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
            val output = proc.inputStream.reader().readText()
            return mapOf("success" to true, "networks" to output)
        }
        val proc = ProcessBuilder("cmd.exe", "/c", "netsh wlan show interfaces").redirectErrorStream(true).start()
        proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
        val output = proc.inputStream.reader().readText()
        val lines = output.lines()
        val ssid = lines.find { it.trimStart().startsWith("SSID") }?.substringAfter(":")?.trim() ?: ""
        val signal = lines.find { it.trimStart().startsWith("Signal") }?.substringAfter(":")?.trim() ?: ""
        val freq = lines.find { it.trimStart().startsWith("Radio type") }?.substringAfter(":")?.trim() ?: ""
        val bssid = lines.find { it.trimStart().startsWith("BSSID") }?.substringAfter(":")?.trim() ?: ""
        return mapOf(
            "success" to true, "ssid" to ssid, "signal" to signal,
            "frequency" to freq, "bssid" to bssid, "raw" to output,
        )
    }

    private fun getLinuxWifi(action: String): Map<String, Any> {
        if (action == "scan") {
            val proc = ProcessBuilder("bash", "-c", "nmcli device wifi list 2>/dev/null || iwlist scan 2>/dev/null || echo 'WiFi scan not available'")
                .redirectErrorStream(true).start()
            proc.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)
            val output = proc.inputStream.reader().readText()
            return mapOf("success" to true, "networks" to output)
        }
        val proc = ProcessBuilder("bash", "-c", "nmcli -t -f active,ssid,signal,chan device wifi list 2>/dev/null | grep '^yes' || echo 'WiFi not available'")
            .redirectErrorStream(true).start()
        proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
        val output = proc.inputStream.reader().readText()
        return mapOf("success" to true, "info" to output)
    }

    private fun getMacWifi(action: String): Map<String, Any> {
        val cmd = if (action == "scan") {
            "/System/Library/PrivateFrameworks/Apple80211.framework/Versions/Current/Resources/airport -s 2>/dev/null || echo 'Scan not available'"
        } else {
            "/System/Library/PrivateFrameworks/Apple80211.framework/Versions/Current/Resources/airport -I 2>/dev/null || networksetup -getairportnetwork en0 2>/dev/null || echo 'WiFi info not available'"
        }
        val proc = ProcessBuilder("bash", "-c", cmd).redirectErrorStream(true).start()
        proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
        val output = proc.inputStream.reader().readText()
        return mapOf("success" to true, "info" to output)
    }
}
