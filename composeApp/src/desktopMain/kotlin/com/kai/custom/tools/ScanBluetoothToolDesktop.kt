package com.kai.custom.tools

import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolSchema

object ScanBluetoothToolDesktop : Tool {
    override val schema = ToolSchema(
        name = "scan_bluetooth_devices",
        description = "Scan for nearby Bluetooth devices and list paired devices",
        parameters = mapOf(
            "action" to ParameterSchema(type = "string", description = "'scan' to search for new devices, 'paired' to list paired devices (default: paired)", required = false),
        ),
    )

    val toolInfo = PhoneTools.scanBluetoothToolInfo

    override suspend fun execute(args: Map<String, Any>): Any {
        val action = (args["action"] as? String)?.lowercase() ?: "paired"
        val os = System.getProperty("os.name").lowercase()

        return try {
            when {
                os.contains("windows") -> listWindowsBluetooth(action)
                os.contains("linux") -> listLinuxBluetooth(action)
                os.contains("mac") -> listMacBluetooth(action)
                else -> mapOf("success" to false, "error" to "Unsupported OS")
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to scan Bluetooth: ${e.message}")
        }
    }

    private fun listWindowsBluetooth(action: String): Map<String, Any> {
        val cmd = "powershell.exe -NoProfile -Command \"" +
            "Get-WmiObject -Namespace Root\\WMI -Class MSBluetooth_Device | " +
            "Select-Object Name, MacAddress, Connected | " +
            "ConvertTo-Json" +
            "\""
        val proc = ProcessBuilder("cmd.exe", "/c", cmd).redirectErrorStream(true).start()
        proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
        val output = proc.inputStream.reader().readText()
        val devices = parseJsonArray(output).map { entry ->
            mapOf(
                "name" to (jsonExtract(entry, "Name") ?: "Unknown"),
                "address" to (jsonExtract(entry, "MacAddress") ?: ""),
                "connected" to (jsonExtract(entry, "Connected") ?: "false"),
            )
        }
        return mapOf("success" to true, "count" to devices.size, "devices" to devices)
    }

    private fun listLinuxBluetooth(action: String): Map<String, Any> = if (action == "scan") {
        val proc = ProcessBuilder("bash", "-c", "timeout 10 bluetoothctl -- scan on 2>&1 || true")
            .redirectErrorStream(true).start()
        proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
        val output = proc.inputStream.reader().readText()
        mapOf("success" to true, "devices" to parseLinuxBluetoothOutput(output))
    } else {
        val proc = ProcessBuilder("bash", "-c", "bluetoothctl devices 2>&1 || true")
            .redirectErrorStream(true).start()
        proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
        val output = proc.inputStream.reader().readText()
        val paired = output.lines().filter { it.isNotBlank() }.map { line ->
            val parts = line.split(" ", limit = 3)
            mapOf("name" to (parts.getOrNull(2) ?: parts.getOrNull(1) ?: ""), "address" to (parts.getOrNull(1) ?: ""))
        }
        mapOf("success" to true, "count" to paired.size, "devices" to paired)
    }

    private fun listMacBluetooth(action: String): Map<String, Any> {
        val cmd = "system_profiler SPBluetoothDataType 2>/dev/null || echo 'No Bluetooth info available'"
        val proc = ProcessBuilder("bash", "-c", cmd).redirectErrorStream(true).start()
        proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
        val output = proc.inputStream.reader().readText()
        return mapOf("success" to true, "raw_output" to output)
    }

    private fun parseLinuxBluetoothOutput(output: String): List<Map<String, String>> {
        val devices = mutableListOf<Map<String, String>>()
        for (line in output.lines()) {
            if (line.contains("Device")) {
                val parts = line.split(" ", limit = 4)
                devices.add(
                    mapOf(
                        "name" to (parts.getOrNull(3)?.trim('[', ']') ?: parts.getOrNull(1) ?: ""),
                        "address" to (parts.getOrNull(2) ?: ""),
                    ),
                )
            }
        }
        return devices.distinctBy { it["address"] }
    }

    private fun parseJsonArray(json: String): List<String> {
        val results = mutableListOf<String>()
        var depth = 0
        var start = -1
        for (i in json.indices) {
            when (json[i]) {
                '{' -> {
                    if (depth++ == 0) start = i
                }

                '}' -> {
                    if (--depth == 0 && start >= 0) {
                        results.add(json.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }
        return results
    }

    private fun jsonExtract(json: String, key: String): String? {
        val regex = "\"$key\"\\s*:\\s*\"?([^,\"]*)\"?".toRegex()
        return regex.find(json)?.groupValues?.getOrNull(1)
    }
}
