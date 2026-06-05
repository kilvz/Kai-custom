package com.kai.custom.tools

import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import java.io.File

object NotificationReaderDesktop : Tool {
    override val schema = ToolSchema(
        name = "check_notifications",
        description = "Read recent system notifications (platform-dependent; may be limited)",
        parameters = mapOf(
            "max_results" to ParameterSchema(type = "integer", description = "Maximum notifications to return (default: 20)", required = false),
        ),
    )

    val toolInfo = ToolInfo(
        id = "check_notifications",
        name = "Check Notifications",
        description = "Read recent system notifications",
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val maxResults = ((args["max_results"] as? Number)?.toInt() ?: 20).coerceIn(1, 100)
        val os = System.getProperty("os.name").lowercase()

        return try {
            when {
                os.contains("windows") -> readWindowsNotifications(maxResults)
                os.contains("linux") -> readLinuxNotifications(maxResults)
                else -> mapOf("success" to true, "notifications" to emptyList<Map<String, String>>(),
                    "message" to "Notification reading not fully supported on this platform")
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to read notifications: ${e.message}")
        }
    }

    private fun readWindowsNotifications(max: Int): Map<String, Any> {
        val notifDir = File(System.getenv("LOCALAPPDATA") + "\\Microsoft\\Windows\\Notifications")
        val notifications = mutableListOf<Map<String, String>>()
        if (notifDir.isDirectory) {
            val dbFile = File(notifDir, "wpndatabase.db")
            if (dbFile.exists()) {
                val cmd = "powershell.exe -NoProfile -Command \"" +
                    "[System.Reflection.Assembly]::LoadWithPartialName('System.Data.SQLite') | Out-Null; " +
                    "\$conn = New-Object System.Data.SQLite.SQLiteConnection('Data Source=${dbFile.absolutePath.replace("'", "''")}'); " +
                    "\$conn.Open(); " +
                    "\$cmd = \$conn.CreateCommand(); " +
                    "\$cmd.CommandText = 'SELECT AppId, Payload, PayloadType, Idx FROM Notification ORDER BY Idx DESC LIMIT $max'; " +
                    "\$reader = \$cmd.ExecuteReader(); " +
                    "while (\$reader.Read()) { " +
                    "  Write-Output (\"NOTIF: \" + \$reader[\"AppId\"] + \" | \" + \$reader[\"PayloadType\"] + \" | \" + \$reader[\"Idx\"]); " +
                    "}" +
                    "\""
                val proc = ProcessBuilder("cmd.exe", "/c", cmd).redirectErrorStream(true).start()
                proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
                val output = proc.inputStream.reader().readText()
                for (line in output.lines().filter { it.startsWith("NOTIF:") }) {
                    val parts = line.removePrefix("NOTIF: ").split(" | ")
                    notifications.add(mapOf(
                        "app" to (parts.getOrNull(0) ?: ""),
                        "type" to (parts.getOrNull(1) ?: ""),
                    ))
                }
            }
        }
        return mapOf("success" to true, "count" to notifications.size, "notifications" to notifications)
    }

    private fun readLinuxNotifications(max: Int): Map<String, Any> {
        val notifs = mutableListOf<Map<String, String>>()
        val cmd = "dbus-monitor --profile 'interface=org.freedesktop.Notifications' 2>/dev/null | head -$((max * 20)) || true"
        val proc = ProcessBuilder("bash", "-c", cmd).redirectErrorStream(true).start()
        proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        proc.destroyForcibly()
        val output = proc.inputStream.reader().readText()
        notifs.add(mapOf("raw" to output.take(2000)))
        return mapOf("success" to true, "count" to notifs.size, "notifications" to notifs)
    }
}
