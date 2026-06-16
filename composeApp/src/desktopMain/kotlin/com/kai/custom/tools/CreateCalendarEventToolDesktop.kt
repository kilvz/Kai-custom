package com.kai.custom.tools

import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import java.io.File

object CreateCalendarEventToolDesktop : Tool {
    override val schema = ToolSchema(
        name = "create_calendar_event",
        description = "Create a calendar event on the system",
        parameters = mapOf(
            "title" to ParameterSchema(type = "string", description = "Event title", required = true),
            "start_time" to ParameterSchema(type = "string", description = "Start time in ISO format (e.g. 2026-06-05T14:00:00)", required = true),
            "end_time" to ParameterSchema(type = "string", description = "End time in ISO format", required = false),
            "description" to ParameterSchema(type = "string", description = "Event description", required = false),
            "location" to ParameterSchema(type = "string", description = "Event location", required = false),
        ),
    )

    val toolInfo = ToolInfo(
        id = "create_calendar_event",
        name = "Create Calendar Event",
        description = "Create a calendar event on the system",
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val title = args["title"] as? String ?: return mapOf("success" to false, "error" to "title is required")
        val startTime = args["start_time"] as? String ?: return mapOf("success" to false, "error" to "start_time is required")
        val endTime = args["end_time"] as? String ?: startTime
        val description = args["description"] as? String ?: ""
        val location = args["location"] as? String ?: ""
        val os = System.getProperty("os.name").lowercase()

        return try {
            when {
                os.contains("windows") -> {
                    val cmd = "powershell.exe -NoProfile -Command \"" +
                        "\$outlook = New-Object -ComObject 'Microsoft.Office.Interop.Outlook.Application'; " +
                        "\$appt = \$outlook.CreateItem(1); " +
                        "\$appt.Subject = '$title'; " +
                        "\$appt.Start = '$startTime'; " +
                        "\$appt.End = '$endTime'; " +
                        "\$appt.Location = '$location'; " +
                        "\$appt.Body = '${description.replace("'", "''")}'; " +
                        "\$appt.Save(); " +
                        "Write-Output 'Event created'" +
                        "\""
                    val proc = ProcessBuilder("cmd.exe", "/c", cmd)
                        .redirectErrorStream(true).start()
                    proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
                    val output = proc.inputStream.reader().readText()
                    mapOf("success" to true, "message" to "Calendar event created", "output" to output)
                }

                else -> {
                    val escapedTitle = title.replace(":", "").replace("/", "-")
                    val icsContent = buildIcsEvent(title, startTime, endTime, description, location)
                    val icsDir = File(System.getProperty("user.home"), "Calendar")
                    icsDir.mkdirs()
                    val icsFile = File(icsDir, "${escapedTitle}_${System.currentTimeMillis()}.ics")
                    icsFile.writeText(icsContent)
                    mapOf("success" to true, "message" to "Event exported to ${icsFile.absolutePath}. Import into your calendar app.")
                }
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to create event: ${e.message}")
        }
    }

    private fun buildIcsEvent(title: String, start: String, end: String, desc: String, loc: String): String = buildString {
        val now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
        val dtStart = start.replace("-", "").replace(":", "").substringBefore(".").take(15)
        val dtEnd = end.replace("-", "").replace(":", "").substringBefore(".").take(15)
        appendLine("BEGIN:VCALENDAR")
        appendLine("VERSION:2.0")
        appendLine("PRODID:-//Kai//Desktop//EN")
        appendLine("BEGIN:VEVENT")
        appendLine("UID:${System.currentTimeMillis()}@kai")
        appendLine("DTSTAMP:$now")
        appendLine("DTSTART:${dtStart}00")
        appendLine("DTEND:${dtEnd}00")
        appendLine("SUMMARY:$title")
        if (desc.isNotBlank()) appendLine("DESCRIPTION:$desc")
        if (loc.isNotBlank()) appendLine("LOCATION:$loc")
        appendLine("END:VEVENT")
        appendLine("END:VCALENDAR")
    }
}
