package com.kai.custom.tools

import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema

object SetAlarmToolDesktop : Tool {
    override val schema = ToolSchema(
        name = "set_alarm",
        description = "Set an alarm or reminder on the system",
        parameters = mapOf(
            "time" to ParameterSchema(type = "string", description = "Time for the alarm in ISO format (e.g. 2026-06-05T14:30:00)", required = true),
            "label" to ParameterSchema(type = "string", description = "Label for the alarm", required = false),
        ),
    )

    val toolInfo = ToolInfo(
        id = "set_alarm",
        name = "Set Alarm",
        description = "Set an alarm or reminder on the system",
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val time = args["time"] as? String ?: return mapOf("success" to false, "error" to "time is required")
        val label = args["label"] as? String ?: "Alarm"
        val os = System.getProperty("os.name").lowercase()

        return try {
            when {
                os.contains("windows") -> {
                    val taskName = "KaiAlarm_${System.currentTimeMillis()}"
                    val cmd = "powershell.exe -NoProfile -Command \"" +
                        "\$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument \"\"-NoProfile -Command \"\"\"\"\"\"[System.Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms'); [System.Windows.Forms.MessageBox]::Show('$label','Alarm')" +
                        "\"\"\"\"\"\"; " +
                        "\$trigger = New-ScheduledTaskTrigger -Once -At \"$time\"; " +
                        "Register-ScheduledTask -TaskName \"$taskName\" -Action \$action -Trigger \$trigger -Force; " +
                        "Write-Output \"Alarm set for $time\"" +
                        "\""
                    val proc = ProcessBuilder("cmd.exe", "/c", cmd).redirectErrorStream(true).start()
                    proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
                    val output = proc.inputStream.reader().readText()
                    mapOf("success" to true, "message" to "Alarm set for $time", "output" to output)
                }
                os.contains("linux") -> {
                    val inSeconds = parseTimeToEpoch(time)
                    val cmd = "echo 'notify-send \"$label\"' | at ${if (inSeconds > 0) (inSeconds - System.currentTimeMillis() / 1000).toString() + " seconds from now" else "now + 1 minute"} 2>&1"
                    val proc = ProcessBuilder("bash", "-c", cmd).redirectErrorStream(true).start()
                    proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
                    mapOf("success" to true, "message" to "Alarm scheduled for $time")
                }
                os.contains("mac") -> {
                    val cmd = "echo 'display notification \"$label\" with title \"Kai Alarm\"' | at $time 2>&1"
                    val proc = ProcessBuilder("bash", "-c", cmd).redirectErrorStream(true).start()
                    proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
                    mapOf("success" to true, "message" to "Alarm scheduled for $time")
                }
                else -> mapOf("success" to false, "error" to "Unsupported OS: $os")
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to set alarm: ${e.message}")
        }
    }

    private fun parseTimeToEpoch(isoTime: String): Long {
        return try {
            java.time.LocalDateTime.parse(isoTime)
                .atZone(java.time.ZoneId.systemDefault())
                .toEpochSecond()
        } catch (_: Exception) {
            0L
        }
    }
}
