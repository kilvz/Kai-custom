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

object ReadFileTool : Tool {
    override val schema = ToolSchema(
        name = "read_file",
        description = "Read a text file from the sandbox filesystem. Path is relative to /root (e.g. myfile.txt or data/config.json). Phone storage is at /sdcard (e.g. /sdcard/Download/file.txt). Returns file contents as a string, or an error if the file is too large or doesn't exist.",
        parameters = mapOf(
            "path" to ParameterSchema(
                type = "string",
                description = "Path relative to /root (e.g. myfile.txt or data/config.json), or absolute under /sdcard for phone storage (e.g. /sdcard/Download/file.txt)",
                required = true,
            ),
            "max_bytes" to ParameterSchema(
                type = "integer",
                description = "Maximum bytes to read (default: 512000, max: 10485760)",
                required = false,
            ),
        ),
    )

    val toolInfo = ToolInfo(
        id = "read_file",
        name = "Read File",
        description = "Read text files from the sandbox",
        nameRes = null,
        descriptionRes = null,
        isEnabled = false,
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        if (!sandboxReady()) {
            return mapOf("success" to false, "error" to "Sandbox is not ready. Set it up in Settings > Sandbox.")
        }

        val path = (args["path"] as? String)?.trim()
            ?: return mapOf("success" to false, "error" to "path is required")

        val maxBytes = ((args["max_bytes"] as? Number)?.toInt() ?: 512_000).coerceIn(1, 10_485_760)

        return try {
            val resolvedPath = resolvePath(path)
            val content = sandboxController.readTextFile(resolvedPath, maxBytes)
            if (content != null) {
                mapOf("success" to true, "path" to path, "content" to content, "bytes" to content.length)
            } else {
                mapOf("success" to false, "error" to "File not found: $path")
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to read file: ${e.message}")
        }
    }
}

object WriteFileTool : Tool {
    override val schema = ToolSchema(
        name = "write_file",
        description = "Write content to a text file in the sandbox filesystem. Creates parent directories automatically. Path is relative to /root. Phone storage is at /sdcard (e.g. /sdcard/Download/file.txt). Overwrites existing files. Use this to create or modify code files, config files, scripts, etc.",
        parameters = mapOf(
            "path" to ParameterSchema(
                type = "string",
                description = "Path relative to /root (e.g. myapp/main.py or config.json), or absolute under /sdcard for phone storage (e.g. /sdcard/Download/output.txt)",
                required = true,
            ),
            "content" to ParameterSchema(
                type = "string",
                description = "Full file content to write. Use \\n for newlines.",
                required = true,
            ),
        ),
    )

    val toolInfo = ToolInfo(
        id = "write_file",
        name = "Write File",
        description = "Write files in the sandbox",
        nameRes = null,
        descriptionRes = null,
        isEnabled = false,
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        if (!sandboxReady()) {
            return mapOf("success" to false, "error" to "Sandbox is not ready. Set it up in Settings > Sandbox.")
        }

        val path = (args["path"] as? String)?.trim()
            ?: return mapOf("success" to false, "error" to "path is required")
        val content = args["content"] as? String
            ?: return mapOf("success" to false, "error" to "content is required")

        return try {
            val resolvedPath = resolvePath(path)
            ensureParentDir(resolvedPath)
            val ok = sandboxController.writeTextFile(resolvedPath, content)
            if (ok) {
                mapOf("success" to true, "path" to path, "bytes_written" to content.length)
            } else {
                mapOf("success" to false, "error" to "Failed to write file: $path")
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to write file: ${e.message}")
        }
    }
}

object EditFileTool : Tool {
    override val schema = ToolSchema(
        name = "edit_file",
        description = "Edit a text file by replacing existing text with new text. If old_string is omitted, content is appended to the file. If old_string appears multiple times, only the first occurrence is replaced. Path is relative to /root. Phone storage is at /sdcard (e.g. /sdcard/Download/file.txt). Use this for surgical edits without rewriting the entire file.",
        parameters = mapOf(
            "path" to ParameterSchema(
                type = "string",
                description = "Path relative to /root (e.g. myapp/main.py), or absolute under /sdcard for phone storage (e.g. /sdcard/Download/file.txt)",
                required = true,
            ),
            "old_string" to ParameterSchema(
                type = "string",
                description = "Text to find and replace. Omit to append new_string to the end of the file.",
                required = false,
            ),
            "new_string" to ParameterSchema(
                type = "string",
                description = "Replacement text, or text to append if old_string is omitted",
                required = true,
            ),
        ),
    )

    val toolInfo = ToolInfo(
        id = "edit_file",
        name = "Edit File",
        description = "Edit text files in the sandbox",
        nameRes = null,
        descriptionRes = null,
        isEnabled = false,
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        if (!sandboxReady()) {
            return mapOf("success" to false, "error" to "Sandbox is not ready. Set it up in Settings > Sandbox.")
        }

        val path = (args["path"] as? String)?.trim()
            ?: return mapOf("success" to false, "error" to "path is required")
        val newString = args["new_string"] as? String
            ?: return mapOf("success" to false, "error" to "new_string is required")
        val oldString = args["old_string"] as? String

        return try {
            val resolvedPath = resolvePath(path)

            if (oldString == null) {
                val existing = sandboxController.readTextFile(resolvedPath) ?: ""
                val ok = sandboxController.writeTextFile(resolvedPath, existing + newString)
                if (ok) {
                    mapOf("success" to true, "path" to path, "action" to "append", "bytes_written" to newString.length)
                } else {
                    mapOf("success" to false, "error" to "Failed to append to file: $path")
                }
            } else {
                val existing = sandboxController.readTextFile(resolvedPath)
                    ?: return mapOf("success" to false, "error" to "File not found: $path")

                if (!existing.contains(oldString)) {
                    return mapOf("success" to false, "error" to "old_string not found in file: $path")
                }

                val updated = existing.replaceFirst(oldString, newString)
                if (updated == existing) {
                    return mapOf("success" to false, "error" to "old_string not found in file: $path")
                }

                val ok = sandboxController.writeTextFile(resolvedPath, updated)
                if (ok) {
                    mapOf("success" to true, "path" to path, "action" to "replace", "replaced" to oldString, "with" to newString)
                } else {
                    mapOf("success" to false, "error" to "Failed to write edited file: $path")
                }
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to edit file: ${e.message}")
        }
    }
}

object GlobTool : Tool {
    override val schema = ToolSchema(
        name = "glob",
        description = "List files and directories matching a glob pattern in the sandbox. Uses the shell 'find' command internally. Patterns: use * for single-level matching, ** for recursive matching, e.g. *.kt, src/**/*.js, data/*.json. Phone storage is at /sdcard (e.g. /sdcard/Download). Results are capped at 200 entries.",
        parameters = mapOf(
            "pattern" to ParameterSchema(
                type = "string",
                description = "Glob pattern to match (e.g. *.txt, **/*.kt, src/**/*.js, data/*.csv)",
                required = true,
            ),
            "directory" to ParameterSchema(
                type = "string",
                description = "Directory to search in (default: /root, use /sdcard for phone storage)",
                required = false,
            ),
        ),
    )

    val toolInfo = ToolInfo(
        id = "glob",
        name = "Glob (List Files)",
        description = "List files matching a pattern in the sandbox",
        nameRes = null,
        descriptionRes = null,
        isEnabled = false,
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        if (!sandboxReady()) {
            return mapOf("success" to false, "error" to "Sandbox is not ready. Set it up in Settings > Sandbox.")
        }

        val pattern = (args["pattern"] as? String)?.trim()
            ?: return mapOf("success" to false, "error" to "pattern is required")
        val directory = (args["directory"] as? String)?.trim() ?: "/root"

        return try {
            val escapedDir = shellEscape(directory)
            val cmd = translateGlobToFind(escapedDir, pattern)
            val output = sandboxController.executeCommand(cmd, useRoot = true, timeoutSeconds = 15)
            val lines = output.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(200)

            mapOf(
                "success" to true,
                "pattern" to pattern,
                "directory" to directory,
                "files" to lines,
                "count" to lines.size,
                "truncated" to (lines.size >= 200),
            )
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Glob failed: ${e.message}")
        }
    }
}

object GrepTool : Tool {
    override val schema = ToolSchema(
        name = "grep",
        description = "Search for a text pattern in files within the sandbox. Uses the shell 'grep' command with recursive search. Returns matching lines with file paths and line numbers. Results capped at 200 matches. Use for searching code, configs, logs, etc. Phone storage is at /sdcard.",
        parameters = mapOf(
            "pattern" to ParameterSchema(
                type = "string",
                description = "Regex or literal text pattern to search for",
                required = true,
            ),
            "path" to ParameterSchema(
                type = "string",
                description = "File path or directory to search in (e.g. /root/myfile.txt, /root/src, or /sdcard/Download)",
                required = true,
            ),
            "include" to ParameterSchema(
                type = "string",
                description = "Glob pattern for files to include (e.g. *.kt, *.json, *.py). Only files matching this glob are searched.",
                required = false,
            ),
            "literal" to ParameterSchema(
                type = "boolean",
                description = "Treat pattern as literal string instead of regex (default: false)",
                required = false,
            ),
        ),
    )

    val toolInfo = ToolInfo(
        id = "grep",
        name = "Grep (Search Files)",
        description = "Search for text patterns in sandbox files",
        nameRes = null,
        descriptionRes = null,
        isEnabled = false,
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        if (!sandboxReady()) {
            return mapOf("success" to false, "error" to "Sandbox is not ready. Set it up in Settings > Sandbox.")
        }

        val pattern = (args["pattern"] as? String)?.trim()
            ?: return mapOf("success" to false, "error" to "pattern is required")
        val path = (args["path"] as? String)?.trim()
            ?: return mapOf("success" to false, "error" to "path is required")
        val include = (args["include"] as? String)?.trim()
        val literal = args["literal"] as? Boolean == true

        return try {
            val escapedPath = shellEscape(path)
            val escapedPattern = shellEscape(pattern)
            val flags = "-rn${if (literal) "F" else ""}"
            val includeFlag = if (include != null) " --include=${shellEscape(include)}" else ""

            val cmd = "grep $flags '$escapedPattern' '$escapedPath'$includeFlag 2>/dev/null | head -200"
            val output = sandboxController.executeCommand(cmd, useRoot = false, timeoutSeconds = 15)

            val lines = output.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val results = lines.map { line ->
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val filePath = line.substring(0, colonIdx)
                    val rest = line.substring(colonIdx + 1)
                    val secondColon = rest.indexOf(':')
                    if (secondColon > 0) {
                        val lineNum = rest.substring(0, secondColon)
                        val content = rest.substring(secondColon + 1)
                        mapOf("file" to filePath, "line" to lineNum, "content" to content)
                    } else {
                        mapOf("file" to filePath, "line" to "", "content" to rest)
                    }
                } else {
                    mapOf("line" to line)
                }
            }

            mapOf(
                "success" to true,
                "pattern" to pattern,
                "path" to path,
                "matches" to results,
                "count" to results.size,
                "truncated" to (results.size >= 200),
            )
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Grep failed: ${e.message}")
        }
    }
}

private fun resolvePath(path: String): String {
    val clean = path.trimStart('/')
    return when {
        clean.startsWith("sdcard/") || clean == "sdcard" -> "/$clean"
        clean.startsWith("root/") -> "/$clean"
        else -> "/root/$clean"
    }
}

private suspend fun ensureParentDir(filePath: String) {
    val parts = filePath.split('/')
    if (parts.size <= 1) return
    val dir = parts.dropLast(1).joinToString("/")
    sandboxController.executeCommand("mkdir -p '$dir'", useRoot = true, timeoutSeconds = 5)
}

private fun translateGlobToFind(directory: String, pattern: String): String {
    return if (pattern.contains("**/")) {
        val suffix = pattern.substringAfter("**/")
        "find $directory -type f -name '${shellEscape(suffix)}' 2>/dev/null | head -200"
    } else if (pattern.contains('*') || pattern.contains('?')) {
        "find $directory -type f -name '${shellEscape(pattern)}' 2>/dev/null | head -200"
    } else {
        "find $directory -type f -name '${shellEscape(pattern)}' 2>/dev/null | head -200"
    }
}

private fun shellEscape(value: String): String = value.replace("'", "'\\''")

object ApplyPatchTool : Tool {
    override val schema = ToolSchema(
        name = "apply_patch",
        description = "Apply a unified diff (patch) to files in the sandbox. Uses the shell 'patch' command internally (tries -p1 first, then -p0 as fallback). The patch text should follow standard unified diff format with ---/+++ file headers. If 'patch' is not installed, returns instructions to install it.",
        parameters = mapOf(
            "patch" to ParameterSchema(
                type = "string",
                description = "The unified diff text to apply. Must include ---/+++ headers indicating which files to modify.",
                required = true,
            ),
        ),
    )

    val toolInfo = ToolInfo(
        id = "apply_patch",
        name = "Apply Patch",
        description = "Apply unified diffs in the sandbox",
        nameRes = null,
        descriptionRes = null,
        isEnabled = false,
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        if (!sandboxReady()) {
            return mapOf("success" to false, "error" to "Sandbox is not ready. Set it up in Settings > Sandbox.")
        }

        val patchText = (args["patch"] as? String)?.trim()
            ?: return mapOf("success" to false, "error" to "patch is required")

        return try {
            val whichPatch = sandboxController.executeCommand("which patch 2>/dev/null && echo FOUND || echo NOTFOUND", useRoot = false, timeoutSeconds = 5)
            if (!whichPatch.contains("FOUND")) {
                return mapOf(
                    "success" to false,
                    "error" to "The 'patch' command is not available in the sandbox. Install it with: apk add patch (Alpine) or apt-get install patch (Ubuntu)",
                )
            }

            val escapedPatch = patchText.replace("'", "'\\''")
            sandboxController.executeCommand("mkdir -p /root/.kai", useRoot = true, timeoutSeconds = 5)
            sandboxController.executeCommand("cat > /root/.kai/_pending_patch.diff << 'PATCHEOF'\n$escapedPatch\nPATCHEOF", useRoot = true, timeoutSeconds = 10)

            val resultP1 = sandboxController.executeCommand("patch -p1 < /root/.kai/_pending_patch.diff 2>&1", useRoot = true, timeoutSeconds = 30)
            if (!resultP1.contains("FAILED") && !resultP1.contains("No such file") && resultP1.isNotBlank()) {
                sandboxController.executeCommand("rm -f /root/.kai/_pending_patch.diff", useRoot = true, timeoutSeconds = 5)
                val patchedFiles = parsePatchFiles(patchText)
                return mapOf("success" to true, "method" to "patch -p1", "output" to resultP1.trim(), "files" to patchedFiles)
            }

            val resultP0 = sandboxController.executeCommand("patch -p0 < /root/.kai/_pending_patch.diff 2>&1", useRoot = true, timeoutSeconds = 30)
            val wasBlank = resultP1.isBlank()
            sandboxController.executeCommand("rm -f /root/.kai/_pending_patch.diff", useRoot = true, timeoutSeconds = 5)

            if (wasBlank || (!resultP0.contains("FAILED") && !resultP0.contains("No such file") && resultP0.isNotBlank())) {
                val effective = if (wasBlank) resultP0 else resultP1
                if (effective.contains("Hunk #1 succeeded") || effective.contains("patching file")) {
                    val patchedFiles = parsePatchFiles(patchText)
                    return mapOf("success" to true, "method" to if (wasBlank) "patch -p1" else "patch -p0", "output" to effective.trim(), "files" to patchedFiles)
                }
            }

            mapOf("success" to false, "error" to "Patch failed: ${resultP1.take(500).ifBlank { resultP0.take(500) }}")
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to apply patch: ${e.message}")
        }
    }
}

object TodoWriteTool : Tool {
    override val schema = ToolSchema(
        name = "todowrite",
        description = "Maintain a structured task list within the sandbox. Each call replaces the entire task list. Use to track progress across multi-step operations — mark items as they complete. Stored at /root/.kai/todos.json.",
        parameters = mapOf(
            "todos" to ParameterSchema(
                type = "array",
                description = "List of task objects. Each must have 'content' (string), 'status' (pending|in_progress|completed|cancelled), and optional 'priority' (high|medium|low). Exactly one task should have status 'in_progress'.",
                required = true,
            ),
        ),
    )

    val toolInfo = ToolInfo(
        id = "todowrite",
        name = "Todo List",
        description = "Track task progress in the sandbox",
        nameRes = null,
        descriptionRes = null,
        isEnabled = false,
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        if (!sandboxReady()) {
            return mapOf("success" to false, "error" to "Sandbox is not ready. Set it up in Settings > Sandbox.")
        }

        @Suppress("UNCHECKED_CAST")
        val todos = args["todos"] as? List<Map<String, Any>>
            ?: return mapOf("success" to false, "error" to "todos is required (array of {content, status})")

        return try {
            sandboxController.executeCommand("mkdir -p /root/.kai", useRoot = true, timeoutSeconds = 5)

            val jsonContent = buildJsonString(todos)
            val escaped = jsonContent.replace("'", "'\\''")
            sandboxController.executeCommand("cat > /root/.kai/todos.json << 'TODOEOF'\n$escaped\nTODOEOF", useRoot = true, timeoutSeconds = 10)

            val inProgress = todos.count { it["status"] == "in_progress" }
            val completed = todos.count { it["status"] == "completed" }
            val total = todos.size

            mapOf(
                "success" to true,
                "total" to total,
                "in_progress" to inProgress,
                "completed" to completed,
                "remaining" to (total - completed),
                "todos" to todos,
            )
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to update todos: ${e.message}")
        }
    }
}

private fun parsePatchFiles(patchText: String): List<String> {
    return patchText.lines()
        .filter { it.startsWith("+++ ") || it.startsWith("--- ") }
        .mapNotNull { line ->
            val path = line.removePrefix("+++ ").removePrefix("--- ").trim()
            path.removePrefix("a/").removePrefix("b/").takeIf { it.isNotBlank() }
        }
        .distinct()
}

private fun buildJsonString(todos: List<Map<String, Any>>): String {
    val items = todos.joinToString(",\n    ") { todo ->
        val content = (todo["content"] as? String)?.let { escapeJson(it) } ?: "\"\""
        val status = (todo["status"] as? String)?.let { escapeJson(it) } ?: "\"pending\""
        val priority = (todo["priority"] as? String)?.let { ", \"priority\": ${escapeJson(it)}" } ?: ""
        """    { "content": $content, "status": $status$priority }"""
    }
    return "[\n$items\n]"
}

private fun escapeJson(s: String): String {
    val escaped = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    return "\"$escaped\""
}
