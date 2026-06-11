package com.kai.custom.tools

import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolSchema

object OpenCodeToolDesktop : Tool {
    override val schema = ToolSchema(
        name = "run_opencode",
        description = "Run opencode AI coding agent on a task on the host system. Delegates complex multi-step coding work to opencode's autonomous agent. Installs opencode via npm on first use.",
        parameters = mapOf(
            "task" to ParameterSchema(type = "string", description = "The coding task for opencode to execute", required = true),
            "directory" to ParameterSchema(type = "string", description = "Working directory on the host (default: current directory)", required = false),
            "agent" to ParameterSchema(type = "string", description = "Agent mode: 'build' (full access) or 'plan' (read-only analysis). Default: build", required = false),
            "timeout" to ParameterSchema(type = "integer", description = "Maximum execution time in seconds (default: 300, max: 600)", required = false),
        ),
    )

    val toolInfo = CommonTools.runOpenCodeToolInfo

    override suspend fun execute(args: Map<String, Any>): Any {
        val task = args["task"] as? String ?: return mapOf("success" to false, "error" to "task is required")
        val directory = args["directory"] as? String ?: System.getProperty("user.dir") ?: "."
        val agent = args["agent"] as? String ?: "build"
        val timeout = ((args["timeout"] as? Number)?.toInt() ?: 300).coerceIn(30, 600)

        return try {
            val whichProc = ProcessBuilder(
                if (System.getProperty("os.name").lowercase().contains("windows")) "where" else "which",
                "opencode",
            ).redirectErrorStream(true).start()
            val whichDone = whichProc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            val needsInstall = !whichDone || whichProc.exitValue() != 0

            if (needsInstall) {
                val installProc = ProcessBuilder(
                    "npm",
                    "install",
                    "-g",
                    "opencode-ai",
                ).redirectErrorStream(true).start()
                val installDone = installProc.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
                if (!installDone || installProc.exitValue() != 0) {
                    val err = installProc.inputStream.reader().readText()
                    mapOf("success" to false, "error" to "Failed to install opencode: $err")
                } else {
                    runOpenCodeTask(task, directory, agent, timeout)
                }
            } else {
                runOpenCodeTask(task, directory, agent, timeout)
            }
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "opencode execution failed: ${e.message}")
        }
    }

    private fun runOpenCodeTask(task: String, directory: String, agent: String, timeout: Int): Map<String, Any> {
        val escapedTask = task.replace("\"", "\\\"")
        val cmd = if (agent == "plan") {
            "opencode run --format json --agent plan \"$escapedTask\""
        } else {
            "opencode run --format json --agent build \"$escapedTask\""
        }

        val proc = ProcessBuilder(
            if (System.getProperty("os.name").lowercase().contains("windows")) listOf("cmd.exe", "/c", cmd) else listOf("bash", "-c", cmd),
        ).directory(java.io.File(directory)).redirectErrorStream(true).start()

        val completed = proc.waitFor(timeout.toLong(), java.util.concurrent.TimeUnit.SECONDS)
        if (!completed) {
            proc.destroyForcibly()
            return mapOf("success" to false, "error" to "opencode task timed out after ${timeout}s")
        }

        val stdout = proc.inputStream.reader().readText()
        val exitCode = proc.exitValue()

        return if (exitCode == 0) {
            mapOf("success" to true, "task" to task, "directory" to directory, "stdout" to stdout)
        } else {
            mapOf("success" to false, "stdout" to stdout, "exit_code" to exitCode, "error" to "opencode exited with code $exitCode")
        }
    }
}
