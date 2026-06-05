package com.kai.custom.tools

import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolSchema
import java.io.File

object ListMediaToolDesktop : Tool {
    override val schema = ToolSchema(
        name = "list_media",
        description = "List media files (images, videos, audio) from common directories",
        parameters = mapOf(
            "type" to ParameterSchema(type = "string", description = "Media type: 'image', 'video', 'audio', or 'all' (default: all)", required = false),
            "max_results" to ParameterSchema(type = "integer", description = "Maximum number of files to return (default: 50)", required = false),
        ),
    )

    val toolInfo = PhoneTools.listMediaToolInfo

    override suspend fun execute(args: Map<String, Any>): Any {
        val mediaType = (args["type"] as? String)?.lowercase() ?: "all"
        val maxResults = ((args["max_results"] as? Number)?.toInt() ?: 50).coerceIn(1, 500)

        val extensions = when (mediaType) {
            "image" -> setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg")
            "video" -> setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm")
            "audio" -> setOf("mp3", "wav", "flac", "aac", "ogg", "wma", "m4a")
            else -> setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a")
        }

        val home = System.getProperty("user.home")
        val searchDirs = listOf(
            File(home, "Downloads"),
            File(home, "Desktop"),
            File(home, "Pictures"),
            File(home, "Videos"),
            File(home, "Music"),
            File(home, "Documents"),
        )

        val results = mutableListOf<Map<String, Any>>()

        try {
            for (dir in searchDirs) {
                if (!dir.isDirectory) continue
                dir.walkTopDown().maxDepth(3).forEach { file ->
                    if (results.size >= maxResults) return@forEach
                    if (file.isFile && file.extension.lowercase() in extensions) {
                        results.add(mapOf(
                            "name" to file.name,
                            "path" to file.absolutePath,
                            "size_bytes" to file.length(),
                            "last_modified" to file.lastModified(),
                            "extension" to file.extension,
                        ))
                    }
                }
                if (results.size >= maxResults) break
            }
        } catch (e: Exception) {
            return mapOf("success" to false, "error" to "Failed to scan media: ${e.message}")
        }

        return mapOf("success" to true, "count" to results.size, "files" to results)
    }
}
