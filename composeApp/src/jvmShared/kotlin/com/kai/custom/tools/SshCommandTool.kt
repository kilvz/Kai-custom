package com.kai.custom.tools

import com.kai.custom.SshConnectionManager
import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import org.koin.java.KoinJavaComponent.inject

private const val TOOL_DESCRIPTION = """Execute a shell command on a remote SSH server and return stdout, stderr, and exit code.

The SSH connection is persistent — managed by the user or set up via the ssh_connect/ssh_disconnect tools. The connection stays alive across commands so state (cwd, exports, etc.) carries forward from one call to the next, just like a normal terminal.

Use this tool when you need to run commands on a remote server. First connect using ssh_connect (which can also save credentials as a named profile for reuse), then use this tool to execute commands. When done, call ssh_disconnect.

Limits and behavior:
- Default timeout: 30s, max: 60s.
- Output is capped at 15000 characters per stream.
- Fullscreen TUIs will not work — use non-interactive variants."""

object SshCommandTool : Tool {
    private val sshManager: SshConnectionManager by inject(SshConnectionManager::class.java)

    override val schema = ToolSchema(
        name = "ssh_execute_command",
        description = TOOL_DESCRIPTION,
        parameters = mapOf(
            "command" to ParameterSchema("string", "The shell command to execute on the remote server", true),
            "timeout" to ParameterSchema("integer", "Timeout in seconds (default 30, max 60)", false),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val command = args["command"] as? String
            ?: return mapOf("success" to false, "error" to "Command is required")

        val timeoutSeconds = ((args["timeout"] as? Number)?.toLong() ?: 30L)
            .coerceIn(1, 60L)

        val result = sshManager.executeCommand(command, timeoutSeconds)
        return if (result.isSuccess) {
            val r = result.getOrThrow()
            @Suppress("UNCHECKED_CAST")
            mapOf(
                "success" to true as Any,
                "stdout" to r.stdout as Any,
                "stderr" to r.stderr as Any,
                "exit_code" to r.exitCode as Any,
            )
        } else {
            val err = result.exceptionOrNull()
            @Suppress("UNCHECKED_CAST")
            mapOf(
                "success" to false as Any,
                "error" to (err?.message ?: "SSH command failed") as Any,
            )
        }
    }

    val toolInfo = ToolInfo(
        id = "ssh_execute_command",
        name = "Execute SSH Command",
        description = "Execute a shell command on a remote SSH server",
        nameRes = null,
        descriptionRes = null,
        isEnabled = false,
    )
}
