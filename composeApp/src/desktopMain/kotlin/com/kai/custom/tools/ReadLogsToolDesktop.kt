package com.kai.custom.tools

import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolSchema

object ReadLogsToolDesktop : Tool {
    override val schema = ToolSchema(
        name = "read_device_logs",
        description = "Read recent system logs",
        parameters = mapOf(
            "lines" to ParameterSchema(type = "integer", description = "Number of lines to return (default: 50)", required = false),
            "filter" to ParameterSchema(type = "string", description = "Filter logs by keyword", required = false),
        ),
    )

    val toolInfo = PhoneTools.readLogsToolInfo

    override suspend fun execute(args: Map<String, Any>): Any {
        val lines = ((args["lines"] as? Number)?.toInt() ?: 50).coerceIn(10, 500)
        val filter = args["filter"] as? String ?: ""
        val os = System.getProperty("os.name").lowercase()

        return try {
            when {
                os.contains("windows") -> readWindowsLogs(lines, filter)
                os.contains("linux") || os.contains("mac") -> readLinuxLogs(lines, filter)
                else -> mapOf("success" to false, "error" to "Unsupported OS: $os")
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to read logs: ${e.message}")
        }
    }

    private fun readWindowsLogs(lines: Int, filter: String): Map<String, Any> {
        val cmd = buildString {
            append("powershell.exe -NoProfile -Command \"")
            append("Get-WinEvent -LogName System,Application -MaxEvents $lines")
            if (filter.isNotBlank()) append(" | Where-Object { \$_.Message -like '*$filter*' }")
            append(" | Select-Object TimeCreated, LevelDisplayName, Message | Format-List | Out-String -Width 4096")
            append("\"")
        }
        val proc = ProcessBuilder("cmd.exe", "/c", cmd).redirectErrorStream(true).start()
        proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
        val output = proc.inputStream.reader().readText()
        return mapOf("success" to true, "logs" to output, "platform" to "windows")
    }

    private fun readLinuxLogs(lines: Int, filter: String): Map<String, Any> {
        val cmd = if (filter.isNotBlank()) {
            "journalctl -n $lines --no-pager | grep -i '${filter.replace("'", "'\\''")}'"
        } else {
            "journalctl -n $lines --no-pager"
        }
        val proc = ProcessBuilder("bash", "-c", cmd).redirectErrorStream(true).start()
        proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
        val output = proc.inputStream.reader().readText()
        return mapOf("success" to true, "logs" to output, "platform" to "linux")
    }
}
