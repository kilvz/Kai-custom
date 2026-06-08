package com.kai.custom.tools

import com.kai.custom.SandboxController
import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import org.koin.java.KoinJavaComponent.inject

private val sandboxController: SandboxController by inject(SandboxController::class.java)

private fun sandboxReady(): Boolean = try {
    sandboxController.status.value.ready
} catch (_: Exception) {
    false
}

object WebFetchTool : Tool {
    override val schema = ToolSchema(
        name = "webfetch",
        description = "Fetch content from a URL by making an HTTP request from within the sandbox. Uses curl. Returns the raw response body. Useful for looking up documentation, checking APIs, or downloading text content.",
        parameters = mapOf(
            "url" to ParameterSchema(
                type = "string",
                description = "The full URL to fetch (including https://). Supports HTTP and HTTPS.",
                required = true,
            ),
            "timeout" to ParameterSchema(
                type = "integer",
                description = "Timeout in seconds (default: 30, max: 120)",
                required = false,
            ),
        ),
    )

    val toolInfo = ToolInfo(
        id = "webfetch",
        name = "Web Fetch",
        description = "Fetch web URLs from the sandbox",
        nameRes = null,
        descriptionRes = null,
        isEnabled = false,
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        if (!sandboxReady()) {
            return mapOf("success" to false, "error" to "Sandbox is not ready. Set it up in Settings > Sandbox.")
        }

        val url = (args["url"] as? String)?.trim()
            ?: return mapOf("success" to false, "error" to "url is required")

        val timeout = ((args["timeout"] as? Number)?.toInt() ?: 30).coerceIn(1, 120)

        return try {
            val escapedUrl = shellEscape(url)
            val output = sandboxController.executeCommand(
                "curl -sL -m $timeout '$escapedUrl' 2>/dev/null | head -c 512000",
                useRoot = false,
                timeoutSeconds = timeout.toLong() + 10,
            )

            if (output.isBlank()) {
                val errOutput = sandboxController.executeCommand(
                    "curl -sL -m $timeout '$escapedUrl' 2>&1 || true",
                    useRoot = false,
                    timeoutSeconds = timeout.toLong() + 10,
                )
                val errorMsg = if (errOutput.isBlank()) "Empty response" else errOutput.take(500)
                mapOf("success" to false, "error" to errorMsg, "url" to url)
            } else {
                mapOf(
                    "success" to true,
                    "url" to url,
                    "content" to output,
                    "bytes" to output.length,
                )
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to fetch URL: ${e.message}", "url" to url)
        }
    }
}

object InternetSearchTool : Tool {
    override val schema = ToolSchema(
        name = "internet_search",
        description = "Search the web from within the sandbox. Uses DuckDuckGo Lite (no API key needed). Returns titles, URLs, and snippets. For looking up current information, news, documentation, or anything beyond the training data.",
        parameters = mapOf(
            "query" to ParameterSchema(
                type = "string",
                description = "Search query — keywords or a natural language question",
                required = true,
            ),
            "count" to ParameterSchema(
                type = "integer",
                description = "Number of results to return (default: 5, max: 20)",
                required = false,
            ),
        ),
    )

    val toolInfo = ToolInfo(
        id = "internet_search",
        name = "Internet Search",
        description = "Search the web from the sandbox (DuckDuckGo)",
        nameRes = null,
        descriptionRes = null,
        isEnabled = false,
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        if (!sandboxReady()) {
            return mapOf("success" to false, "error" to "Sandbox is not ready. Set it up in Settings > Sandbox.")
        }

        val query = (args["query"] as? String)?.trim()
            ?: return mapOf("success" to false, "error" to "query is required")

        val count = ((args["count"] as? Number)?.toInt() ?: 5).coerceIn(1, 20)

        return try {
            val encoded = urlEncode(query)
            val html = sandboxController.executeCommand(
                "curl -sL -m 15 'https://lite.duckduckgo.com/lite/?q=$encoded' 2>/dev/null || true",
                useRoot = false,
                timeoutSeconds = 25,
            )

            if (html.isBlank() || html.contains("No results")) {
                return mapOf("success" to true, "query" to query, "results" to emptyList<Any>(), "message" to "No results found")
            }

            val results = parseDdgLiteResults(html, count)

            mapOf(
                "success" to true,
                "query" to query,
                "results" to results,
                "count" to results.size,
            )
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Search failed: ${e.message}", "query" to query)
        }
    }
}

private val linkRegex = Regex("""<a[^>]+class=['"]result-link['"][^>]*>([\s\S]*?)</a>""")
private val snippetRegex = Regex("""<td[^>]+class=['"]result-snippet['"][^>]*>([\s\S]*?)</td>""")
private val fullLinkRegex = Regex("""<a\s[^>]*class=['"]result-link['"][^>]*>""")
private val uddgRegex = Regex("""uddg=([^&]+)""")
private val htmlTagRegex = Regex("<[^>]*>")

private fun parseDdgLiteResults(html: String, maxResults: Int): List<Map<String, String>> {
    val results = mutableListOf<Map<String, String>>()
    val linkTags = fullLinkRegex.findAll(html).toList()
    val links = linkRegex.findAll(html).toList()
    val snippets = snippetRegex.findAll(html).toList()

    for (i in links.indices) {
        if (results.size >= maxResults) break
        val linkTag = linkTags.getOrNull(i)?.value ?: continue
        val href = hrefRegex.find(linkTag)?.groupValues?.get(1) ?: continue
        val title = links[i].groupValues[1].stripHtml().trim()
        val snippet = snippets.getOrNull(i)?.groupValues?.get(1)?.stripHtml()?.trim() ?: ""

        val url = extractDdgUrl(href)

        if (url.isNotBlank() && title.isNotBlank()) {
            results.add(mapOf("title" to title, "url" to url, "snippet" to snippet))
        }
    }

    return results
}

private val hrefRegex = Regex("""href=['"]([^'"]*?)['"]""")

private fun extractDdgUrl(href: String): String {
    val uddgParam = uddgRegex.find(href)?.groupValues?.get(1)
    if (uddgParam != null) return urlDecode(uddgParam)
    return if (href.startsWith("//")) "https:$href" else href
}

private fun urlDecode(encoded: String): String = buildString {
    var i = 0
    while (i < encoded.length) {
        when {
            encoded[i] == '%' && i + 2 < encoded.length -> {
                val hex = encoded.substring(i + 1, i + 3)
                val byte = hex.toIntOrNull(16)
                if (byte != null) {
                    append(byte.toChar())
                    i += 3
                } else {
                    append(encoded[i])
                    i++
                }
            }

            encoded[i] == '+' -> {
                append(' ')
                i++
            }

            else -> {
                append(encoded[i])
                i++
            }
        }
    }
}

private fun String.stripHtml(): String = replace(htmlTagRegex, "")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#x27;", "'")
    .replace("&#39;", "'")
    .replace("&nbsp;", " ")

private fun urlEncode(s: String): String = s.encodeToByteArray().joinToString("") { b ->
    val c = b.toInt() and 0xFF
    when {
        c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code ||
            c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code -> c.toChar().toString()

        c == ' '.code -> "+"

        else -> "%${c.toString(16).uppercase().padStart(2, '0')}"
    }
}

private fun shellEscape(value: String): String = value.replace("'", "'\\''")
