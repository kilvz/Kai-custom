package com.kai.custom.tools

import com.kai.custom.SandboxSessions
import com.kai.custom.data.AppSettings
import com.kai.custom.data.Service
import com.kai.custom.data.currentConversationIdOrNull
import com.kai.custom.data.getApiKey
import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolSchema
import com.kai.custom.sandbox.LinuxSandboxManager
import com.kai.custom.sandbox.SandboxState
import org.koin.java.KoinJavaComponent.inject

object OpenCodeTool : Tool {
    private val sandboxManager: LinuxSandboxManager by inject(LinuxSandboxManager::class.java)
    private val appSettings: AppSettings by inject(AppSettings::class.java)

    override val schema = ToolSchema(
        name = "run_opencode",
        description = "Run opencode AI coding agent on a task inside the Linux sandbox. Delegates complex multi-step coding work like refactoring, implementing features, running tests, git operations, and file editing to opencode's autonomous agent. The sandbox must be installed and ready. Installs opencode via npm on first use.",
        parameters = mapOf(
            "task" to ParameterSchema(type = "string", description = "The coding task for opencode to execute", required = true),
            "directory" to ParameterSchema(type = "string", description = "Working directory inside the sandbox (default: /root/projects/opencode-task)", required = false),
            "agent" to ParameterSchema(type = "string", description = "Agent mode: 'build' (full access) or 'plan' (read-only analysis). Default: build", required = false),
            "timeout" to ParameterSchema(type = "integer", description = "Maximum execution time in seconds (default: 300, max: 600)", required = false),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val task = args["task"] as? String
            ?: return mapOf("success" to false, "error" to "task is required")

        if (sandboxManager.state.value !is SandboxState.Ready) {
            return mapOf("success" to false, "error" to "Linux sandbox is not installed. Set it up in Settings > Sandbox.")
        }

        val directory = args["directory"] as? String ?: "/root/projects/opencode-task"
        val agent = args["agent"] as? String ?: "build"
        val timeout = ((args["timeout"] as? Number)?.toInt() ?: 300).coerceIn(30, 600)

        val sessionId = currentConversationIdOrNull() ?: SandboxSessions.DEFAULT
        val shell = sandboxManager.shellFor(sessionId)

        val whichResult = shell.run(command = "which opencode 2>/dev/null && echo FOUND || echo NOTFOUND", timeoutSeconds = 10)
        val needsInstall = !((whichResult["stdout"] as? String)?.contains("FOUND") == true)

        if (needsInstall) {
            val installResult = shell.run(
                command = "npm install -g opencode-ai 2>&1 | tail -5",
                timeoutSeconds = 120,
            )
            if (installResult["success"] != true) {
                val stderr = (installResult["stderr"] as? String).orEmpty()
                val stdout = (installResult["stdout"] as? String).orEmpty()
                return mapOf(
                    "success" to false,
                    "error" to "Failed to install opencode: ${stderr.ifEmpty { stdout }}",
                )
            }
        }

        val envVars = buildApiKeyEnvVars()
        shell.run(command = "mkdir -p \"$directory\"", timeoutSeconds = 10)

        val envPrefix = if (envVars.isNotEmpty()) {
            envVars.entries.joinToString(" ") { "${it.key}=${shellQuote(it.value)}" } + " "
        } else ""

        val cmd = "cd \"$directory\" && ${envPrefix}opencode run --format json --agent \"$agent\" ${shellQuote(task)}"
        val result = shell.run(command = cmd, timeoutSeconds = timeout.toLong())

        if (result["timed_out"] == true) {
            return mapOf(
                "success" to false,
                "error" to "opencode task timed out after ${timeout}s. Try a simpler task or increase the timeout.",
                "partial_output" to (result["stdout"] as? String).orEmpty(),
            )
        }

        val stdout = (result["stdout"] as? String).orEmpty()
        val stderr = (result["stderr"] as? String).orEmpty()
        val exitCode = (result["exit_code"] as? Int) ?: -1

        if (result["success"] != true && exitCode != 0) {
            return mapOf(
                "success" to false,
                "stderr" to stderr,
                "stdout" to stdout,
                "exit_code" to exitCode,
                "error" to "opencode exited with code $exitCode",
            )
        }

        return mapOf(
            "success" to true,
            "task" to task,
            "directory" to directory,
            "stdout" to stdout,
            "stderr" to stderr,
            "message" to "opencode task completed. Review the output and files in $directory.",
        )
    }

    private fun buildApiKeyEnvVars(): Map<String, String> {
        val vars = mutableMapOf<String, String>()

        val opencodeKey = appSettings.getApiKey(Service.OpenCode)
        if (opencodeKey.isNotBlank()) vars["OPENCODE_API_KEY"] = opencodeKey

        val anthropicKey = appSettings.getApiKey(Service.Anthropic)
        if (anthropicKey.isNotBlank()) vars["ANTHROPIC_API_KEY"] = anthropicKey

        val openaiKey = appSettings.getApiKey(Service.OpenAI)
        if (openaiKey.isNotBlank()) vars["OPENAI_API_KEY"] = openaiKey

        val geminiKey = appSettings.getApiKey(Service.Gemini)
        if (geminiKey.isNotBlank()) vars["GEMINI_API_KEY"] = geminiKey

        return vars
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
