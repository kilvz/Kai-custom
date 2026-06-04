package com.kai.custom.tools

import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import com.kai.custom.root.AdminManager

object AdminTool : Tool {
    override val schema = ToolSchema(
        name = "run_admin",
        description = "Run shell commands with OS administrator/root privileges. On Windows: UAC elevation. On Linux: pkexec/sudo. On macOS: osascript with admin prompt. Full system access — install packages, modify system files, manage services. Requires admin credentials to be granted at runtime.",
        parameters = mapOf(
            "command" to ParameterSchema(
                type = "string",
                description = "Shell command to execute with admin privileges (e.g. 'netstat -ano', 'systemctl restart sshd', 'ipconfig /flushdns')",
                required = true,
            ),
            "timeout" to ParameterSchema(
                type = "integer",
                description = "Maximum execution time in seconds (default: 30, max: 120)",
                required = false,
            ),
        ),
    )

    val toolInfo = ToolInfo(
        id = "run_admin",
        name = "Run Admin Command",
        description = "Execute shell commands with OS administrator privileges",
        nameRes = null,
        descriptionRes = null,
        isEnabled = false,
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val command = (args["command"] as? String)?.trim()
            ?: return mapOf("success" to false, "error" to "command is required")

        if (!AdminManager.isAdmin()) {
            val elevationResult = AdminManager.runAsAdmin(command)
            return if (elevationResult.isSuccess) {
                mapOf("success" to true, "stdout" to elevationResult.getOrThrow(), "elevated" to true)
            } else {
                mapOf(
                    "success" to false,
                    "error" to "Not running with admin privileges and elevation failed: ${elevationResult.exceptionOrNull()?.message}. Use Settings to relaunch as administrator, or check that pkexec/sudo is available on Linux.",
                    "admin_available" to true,
                )
            }
        }

        val timeout = ((args["timeout"] as? Number)?.toInt() ?: 30).coerceIn(5, 120)

        return try {
            val proc = Runtime.getRuntime().exec(
                if (System.getProperty("os.name").lowercase().contains("win"))
                    arrayOf("cmd.exe", "/c", command)
                else
                    arrayOf("sh", "-c", command)
            )

            val stdout = proc.inputStream.bufferedReader()
            val stderr = proc.errorStream.bufferedReader()

            val finished = proc.waitFor(timeout.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return mapOf("success" to false, "error" to "Command timed out after ${timeout}s")
            }

            val outText = stdout.readText().trim()
            val errText = stderr.readText().trim()
            val exitCode = proc.exitValue()

            val result = mutableMapOf<String, Any>(
                "success" to (exitCode == 0),
                "exit_code" to exitCode,
            )
            if (outText.isNotEmpty()) result["stdout"] = outText
            if (errText.isNotEmpty()) result["stderr"] = errText
            result
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to execute admin command: ${e.message}")
        }
    }
}
