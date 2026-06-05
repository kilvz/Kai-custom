package com.kai.custom.tools

import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolSchema
import java.io.File

object ReadContactsToolDesktop : Tool {
    override val schema = ToolSchema(
        name = "read_contacts",
        description = "Search and read contacts from the system",
        parameters = mapOf(
            "query" to ParameterSchema(type = "string", description = "Optional search query to filter contacts", required = false),
            "max_results" to ParameterSchema(type = "integer", description = "Maximum contacts to return (default: 50)", required = false),
        ),
    )

    val toolInfo = PhoneTools.readContactsToolInfo

    override suspend fun execute(args: Map<String, Any>): Any {
        val query = (args["query"] as? String)?.lowercase() ?: ""
        val maxResults = ((args["max_results"] as? Number)?.toInt() ?: 50).coerceIn(1, 200)
        val os = System.getProperty("os.name").lowercase()

        return try {
            when {
                os.contains("windows") -> readWindowsContacts(query, maxResults)
                os.contains("linux") -> readLinuxContacts(query, maxResults)
                os.contains("mac") -> readMacContacts(query, maxResults)
                else -> mapOf("success" to false, "error" to "Unsupported OS")
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to read contacts: ${e.message}")
        }
    }

    private fun readWindowsContacts(query: String, max: Int): Map<String, Any> {
        val contacts = mutableListOf<Map<String, String>>()
        val contactsDir = File(System.getProperty("user.home"), "Contacts")
        if (contactsDir.isDirectory) {
            contactsDir.listFiles()?.forEach { file ->
                if (contacts.size >= max) return@forEach
                if (file.extension.lowercase() == "contact") {
                    val name = file.nameWithoutExtension
                    if (query.isBlank() || name.lowercase().contains(query)) {
                        contacts.add(mapOf("name" to name, "source" to file.absolutePath))
                    }
                }
            }
        }
        return mapOf("success" to true, "count" to contacts.size, "contacts" to contacts)
    }

    private fun readLinuxContacts(query: String, max: Int): Map<String, Any> {
        val contacts = mutableListOf<Map<String, String>>()
        val home = System.getProperty("user.home")
        val vcfDirs = listOf(
            File(home, ".contacts"),
            File(home, ".local/share/contacts"),
            File(home, "Contacts"),
        )
        for (dir in vcfDirs) {
            if (!dir.isDirectory) continue
            dir.listFiles()?.forEach { file ->
                if (contacts.size >= max) return@forEach
                if (file.extension.lowercase() in setOf("vcf", "vcard")) {
                    val content = file.readText()
                    val fnMatch = Regex("FN[:;](.+)").find(content)
                    val name = fnMatch?.groupValues?.getOrNull(1)?.trim() ?: file.nameWithoutExtension
                    if (query.isBlank() || name.lowercase().contains(query)) {
                        val telMatch = Regex("TEL[:;](.+)").find(content)
                        val emailMatch = Regex("EMAIL[:;](.+)").find(content)
                        contacts.add(mapOf(
                            "name" to name,
                            "phone" to (telMatch?.groupValues?.getOrNull(1)?.trim() ?: ""),
                            "email" to (emailMatch?.groupValues?.getOrNull(1)?.trim() ?: ""),
                            "source" to file.absolutePath,
                        ))
                    }
                }
            }
        }
        return mapOf("success" to true, "count" to contacts.size, "contacts" to contacts)
    }

    private fun readMacContacts(query: String, max: Int): Map<String, Any> {
        val cmd = "osascript -e 'set output to \"\"' -e 'tell application \"Contacts\"' -e 'set peopleList to every person' -e " +
            "'repeat with p in peopleList' -e 'set personName to name of p' -e 'set output to output & personName & return' -e 'end repeat' -e 'end tell' -e 'return output'"
        val proc = ProcessBuilder("bash", "-c", cmd).redirectErrorStream(true).start()
        proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
        val lines = proc.inputStream.reader().readText().lines().filter { it.isNotBlank() }
        val contacts = lines.map { mapOf("name" to it) }
        val filtered = if (query.isBlank()) contacts else contacts.filter {
            (it["name"] ?: "").lowercase().contains(query)
        }.take(max)
        return mapOf("success" to true, "count" to filtered.size, "contacts" to filtered)
    }
}
