package com.kai.custom.tools

import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolSchema
import java.io.File

object ListInstalledAppsToolDesktop : Tool {
    override val schema = ToolSchema(
        name = "list_installed_apps",
        description = "List all installed applications on the system",
        parameters = emptyMap(),
    )

    val toolInfo = PhoneTools.installedAppsToolInfo

    override suspend fun execute(args: Map<String, Any>): Any {
        val os = System.getProperty("os.name").lowercase()
        val apps = mutableListOf<Map<String, String>>()

        try {
            when {
                os.contains("windows") -> {
                    val proc = ProcessBuilder(
                        "powershell.exe",
                        "-NoProfile",
                        "-Command",
                        "Get-ItemProperty HKLM:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\* | " +
                            "Where-Object { \$_.DisplayName } | " +
                            "Select-Object DisplayName, DisplayVersion, Publisher, InstallDate | " +
                            "ConvertTo-Json",
                    ).redirectErrorStream(true).start()
                    proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
                    val output = proc.inputStream.reader().readText()
                    if (output.isNotBlank() && output != "null") {
                        parseWindowsApps(output, apps)
                    }
                }

                os.contains("linux") -> {
                    val proc = ProcessBuilder("dpkg-query", "-W", "-f", "'\${Package}|\${Version}|\${Installed-Size}|\${Status}'")
                        .redirectErrorStream(true).start()
                    proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
                    val output = proc.inputStream.reader().readText()
                    if (output.isNotBlank()) {
                        for (line in output.lines()) {
                            val parts = line.trim('\'').split('|')
                            if (parts.size >= 2 && parts[0].isNotBlank()) {
                                apps.add(
                                    mapOf(
                                        "name" to parts[0],
                                        "version" to (parts.getOrNull(1) ?: ""),
                                        "size_kb" to (parts.getOrNull(2) ?: "0"),
                                    ),
                                )
                            }
                        }
                    }
                }

                os.contains("mac") -> {
                    File("/Applications").listFiles()?.forEach { app ->
                        if (app.name.endsWith(".app")) {
                            apps.add(mapOf("name" to app.name.removeSuffix(".app")))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return mapOf("success" to false, "error" to "Failed to list apps: ${e.message}", "apps" to apps)
        }

        return mapOf("success" to true, "count" to apps.size, "apps" to apps)
    }

    private fun parseWindowsApps(json: String, apps: MutableList<Map<String, String>>) {
        try {
            val clean = json.trim('[', ']', '\r', '\n')
            if (clean.isBlank()) return
            val entries = if (json.trimStart().startsWith("[")) {
                parseJsonArray(json)
            } else {
                listOf(json)
            }
            for (entry in entries) {
                val name = extractJsonValue(entry, "DisplayName") ?: continue
                val version = extractJsonValue(entry, "DisplayVersion") ?: ""
                val publisher = extractJsonValue(entry, "Publisher") ?: ""
                apps.add(mapOf("name" to name, "version" to version, "publisher" to publisher))
            }
        } catch (_: Exception) {}
    }

    private fun parseJsonArray(json: String): List<String> {
        val results = mutableListOf<String>()
        var depth = 0
        var start = -1
        for (i in json.indices) {
            val c = json[i]
            if (c == '{') {
                if (depth == 0) start = i
                depth++
            } else if (c == '}') {
                depth--
                if (depth == 0 && start >= 0) {
                    results.add(json.substring(start, i + 1))
                    start = -1
                }
            }
        }
        return results
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return regex.find(json)?.groupValues?.getOrNull(1)
    }
}
