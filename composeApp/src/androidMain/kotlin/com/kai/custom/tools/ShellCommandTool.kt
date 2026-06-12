package com.kai.custom.tools

import com.kai.custom.SandboxSessions
import com.kai.custom.data.AppSettings
import com.kai.custom.data.currentConversationIdOrNull
import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import com.kai.custom.sandbox.LinuxSandboxManager
import com.kai.custom.sandbox.SandboxState
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_execute_shell_command_description
import kai.composeapp.generated.resources.tool_execute_shell_command_name
import org.koin.java.KoinJavaComponent.inject

object ShellCommandTool : Tool {
    private val sandboxManager: LinuxSandboxManager by inject(LinuxSandboxManager::class.java)
    private val appSettings: AppSettings by inject(AppSettings::class.java)

    private fun distroName(): String = if (appSettings.getSandboxDistro() == "ubuntu") "Ubuntu" else "Alpine Linux"
    private fun pkgManager(): String = if (appSettings.getSandboxDistro() == "ubuntu") "apt" else "apk"
    private fun installCmd(): String = if (appSettings.getSandboxDistro() == "ubuntu") "apt-get install -y" else "apk add"

    override val schema: ToolSchema
        get() = ToolSchema(
            name = "execute_shell_command",
            description = buildDescription(),
            parameters = mapOf(
                "command" to ParameterSchema("string", "The shell command to execute", true),
                "timeout" to ParameterSchema("integer", "Timeout in seconds (default 30, max 60)", false),
                "working_dir" to ParameterSchema("string", "If set, run the command starting in this directory (cd <dir> && <command>). The cd persists for subsequent calls — same as if the user had run cd themselves.", false),
                "env" to ParameterSchema("object", "Per-command environment variable overrides. Scoped to this call only; does not persist (use 'export' inside the command if you want persistence).", false),
                "background" to ParameterSchema("boolean", "Run detached as a background job. Returns a session_id; use manage_process to check status. Does not share the persistent shell.", false),
                "fresh" to ParameterSchema("boolean", "If true, run in a one-shot isolated shell that does not share state with the persistent session. Default false.", false),
            ),
        )

    private fun buildDescription(): String = buildString {
        appendLine("Execute a shell command in a ${distroName()} sandbox and return stdout, stderr, exit code, and current working directory. The environment is a full ${distroName()} system running via proot.")
        appendLine()
        appendLine("IMPORTANT — Android system commands (pm, am, dumpsys, settings, wm, input, device_config, content, appops, cmd, and anything under /system, /vendor, /apex) do NOT exist in this sandbox. It is NOT the Android shell. For ADB-level system commands, use the separate run_adb tool instead — that tool goes through Shizuku and can access all Android system services.")
        appendLine()
        appendLine("Shell session is PERSISTENT across calls within THIS conversation: cwd, exported environment variables, and any in-shell state carry from one call to the next, just like a normal terminal. So \"cd /tmp\" in one call, then \"pwd\" in the next, returns \"/tmp\". You do NOT need to chain \"cd dir && command\" unless you want directory changes to be one-shot. Other conversations and the in-app Terminal tab each have their own isolated shells; the rootfs and /root are still shared on disk, so files persist across all of them.")
        appendLine()
        appendLine("This sandbox is for: running Python/Node scripts, git operations, curl/wget HTTP requests, file manipulation, ${pkgManager()} package management, and general Linux command-line work. For remote server access, use the dedicated ssh_connect/ssh_execute_command/ssh_disconnect tools instead.")
        appendLine()
        appendLine("Pre-installed: bash, python3 (pip), nodejs, git, curl, wget, jq, lftp (FTP/FTPS), rsync.")
        appendLine()
        appendLine("Limits and behavior:")
        appendLine("- Output is capped at 15000 characters per stream; for large output, pipe through head/tail.")
        appendLine("- Default timeout: 30s, max: 60s. Long-running interactive commands (e.g. ssh sessions held across messages) work because the shell is persistent — but a SINGLE call still hits the timeout if it doesn't return.")
        appendLine("- Fullscreen TUIs (top, htop, vim, less, nano, anything ncurses) WILL NOT WORK — the sandbox has no PTY. Use non-interactive variants: \"top -bn1\" for a one-shot snapshot, \"ps aux\" for processes, redirect editor output, etc.")
        appendLine("- Set background=true to run a long-lived process detached from the shell (writes to its own session_id). Use manage_process to check on it.")
        appendLine("- Set fresh=true to run in a one-shot isolated shell that doesn't share state with the persistent session. Useful when you specifically want isolation; rarely needed.")
        appendLine()
        append("Install extra packages with: ${installCmd()} <package>")
        appendLine()
        appendLine()
        append("To show a file you produced in /root to the user, call open_file with the path relative to /root (e.g. open_file path=\"page.html\"). File needs to be self-contained.")
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun execute(args: Map<String, Any>): Any {
        val command = args["command"] as? String
            ?: return mapOf("success" to false, "error" to "Command is required")

        if (sandboxManager.state.value !is SandboxState.Ready) {
            return mapOf("success" to false, "error" to "Linux sandbox is not installed. Set it up in Settings > Tools.")
        }

        val timeoutSeconds = ((args["timeout"] as? Number)?.toLong() ?: 30L)
            .coerceIn(1, 60L)
        val workingDir = args["working_dir"] as? String

        val envMap = (args["env"] as? Map<String, Any>)
            ?.mapValues { it.value.toString() }
            ?: emptyMap()

        val background = args["background"] as? Boolean ?: false
        if (background) {
            return ProcessManagerTool.processManager.startBackground(
                command,
                timeoutSeconds,
                workingDir ?: "/root",
                envMap,
            )
        }

        val fresh = args["fresh"] as? Boolean ?: false
        if (fresh) {
            val executor = sandboxManager.createProotExecutor()
            return executor.execute(command, timeoutSeconds, workingDir ?: "/root", envMap)
        }

        // Persistent shell path. Each conversation gets its own bash session so
        // state from one chat (cwd, exports, ssh-agent, &-jobs) doesn't leak into
        // another. Tools invoked outside a conversation context fall through to
        // a shared default session.
        val sessionId = currentConversationIdOrNull() ?: SandboxSessions.DEFAULT
        // Apply env as a per-command prefix (FOO=bar BAR=baz user_command) so
        // the env doesn't bleed into the session. cd is intentionally persistent:
        // the LLM is told that's the case in the tool description.
        val prefix = buildString {
            if (workingDir != null) {
                append("cd ").append(shellSingleQuote(workingDir)).append(" && ")
            }
            envMap.forEach { (k, v) ->
                append(shellSingleQuote(k)).append('=').append(shellSingleQuote(v)).append(' ')
            }
        }
        val wrapped = if (prefix.isEmpty()) command else "$prefix$command"
        // Pass the unwrapped command as displayCommand so the Terminal UI shows
        // what the agent asked for, not the cd/env scaffolding we add.
        return sandboxManager.shellFor(sessionId).run(
            command = wrapped,
            timeoutSeconds = timeoutSeconds,
            displayCommand = command,
        )
    }

    private fun shellSingleQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    val toolInfo = ToolInfo(
        id = "execute_shell_command",
        name = "Execute Shell Command",
        description = "Execute a shell command in the Linux sandbox",
        nameRes = Res.string.tool_execute_shell_command_name,
        descriptionRes = Res.string.tool_execute_shell_command_description,
        isEnabled = false,
    )
}
