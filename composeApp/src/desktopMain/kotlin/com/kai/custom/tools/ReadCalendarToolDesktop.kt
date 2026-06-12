package com.kai.custom.tools

import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolSchema
import java.io.File
import java.time.LocalDate

object ReadCalendarToolDesktop : Tool {
    override val schema = ToolSchema(
        name = "read_calendar_events",
        description = "Read calendar events from the system",
        parameters = mapOf(
            "days" to ParameterSchema(type = "integer", description = "Number of days to look ahead (default: 7)", required = false),
            "max_results" to ParameterSchema(type = "integer", description = "Maximum events to return (default: 50)", required = false),
        ),
    )

    val toolInfo = PhoneTools.readCalendarToolInfo

    override suspend fun execute(args: Map<String, Any>): Any {
        val days = ((args["days"] as? Number)?.toInt() ?: 7).coerceIn(1, 90)
        val maxResults = ((args["max_results"] as? Number)?.toInt() ?: 50).coerceIn(1, 200)
        val os = System.getProperty("os.name").lowercase()

        return try {
            when {
                os.contains("windows") -> readWindowsCalendar(days, maxResults)
                os.contains("linux") || os.contains("mac") -> readIcsCalendar(days, maxResults)
                else -> mapOf("success" to false, "error" to "Unsupported OS")
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to read calendar: ${e.message}")
        }
    }

    private fun readWindowsCalendar(days: Int, max: Int): Map<String, Any> {
        val cmd = "powershell.exe -NoProfile -Command \"" +
            "\$start = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ss'); " +
            "\$end = (Get-Date).AddDays($days).ToString('yyyy-MM-ddTHH:mm:ss'); " +
            "\$session = New-Object -ComObject 'Microsoft.Office.Interop.Outlook.Application'; " +
            "\$namespace = \$session.GetNamespace('MAPI'); " +
            "\$calendar = \$namespace.GetDefaultFolder(9); " +
            "\$appointments = \$calendar.Items; " +
            "\$appointments.Sort('[Start]'); " +
            "\$appointments.IncludeRecurrences = \$true; " +
            "\$filter = \"[Start] >= '\$start' AND [End] <= '\$end'\"; " +
            "\$filtered = \$appointments.Restrict(\$filter); " +
            "foreach(\$item in \$filtered) { \"EVENT: \$(\$item.Subject) | START: \$(\$item.Start) | END: \$(\$item.End) | LOCATION: \$(\$item.Location)\" }" +
            "\""
        val proc = ProcessBuilder("cmd.exe", "/c", cmd).redirectErrorStream(true).start()
        proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
        val output = proc.inputStream.reader().readText()
        val events = output.lines().filter { it.startsWith("EVENT:") }.map { line ->
            val parts = line.removePrefix("EVENT: ").split(" | ")
            mapOf(
                "title" to (parts.getOrNull(0)?.removePrefix("EVENT: ") ?: parts.getOrNull(0) ?: ""),
                "start" to (parts.getOrNull(1)?.removePrefix("START: ") ?: ""),
                "end" to (parts.getOrNull(2)?.removePrefix("END: ") ?: ""),
                "location" to (parts.getOrNull(3)?.removePrefix("LOCATION: ") ?: ""),
            )
        }.take(max)
        return mapOf("success" to true, "count" to events.size, "events" to events)
    }

    private fun readIcsCalendar(days: Int, max: Int): Map<String, Any> {
        val events = mutableListOf<Map<String, String>>()
        val home = System.getProperty("user.home")
        val icsDirs = listOf(
            File(home, ".calendar"),
            File(home, ".local/share/calendar"),
            File(home, "Calendar"),
        )
        val now = LocalDate.now()
        val end = now.plusDays(days.toLong())

        for (dir in icsDirs) {
            if (!dir.isDirectory) continue
            dir.walkTopDown().maxDepth(2).forEach { file ->
                if (events.size >= max) return@forEach
                if (file.extension.lowercase() != "ics") return@forEach
                try {
                    val content = file.readText()
                    val vevents = content.split("BEGIN:VEVENT")
                    for (vevent in vevents.drop(1)) {
                        if (events.size >= max) break
                        val summary = Regex("SUMMARY[:;](.+)").find(vevent)
                        val dtstart = Regex("DTSTART[;:=]?(.+?)(?:\r?\n)").find(vevent)
                        val dtend = Regex("DTEND[;:=]?(.+?)(?:\r?\n)").find(vevent)
                        val location = Regex("LOCATION[:;](.+)").find(vevent)
                        events.add(
                            mapOf(
                                "title" to (summary?.groupValues?.getOrNull(1)?.trim() ?: "Untitled"),
                                "start" to (dtstart?.groupValues?.getOrNull(1)?.trim()?.replace("T", " ") ?: ""),
                                "end" to (dtend?.groupValues?.getOrNull(1)?.trim()?.replace("T", " ") ?: ""),
                                "location" to (location?.groupValues?.getOrNull(1)?.trim() ?: ""),
                            ),
                        )
                    }
                } catch (_: Exception) {}
            }
        }
        return mapOf("success" to true, "count" to events.size, "events" to events)
    }
}
