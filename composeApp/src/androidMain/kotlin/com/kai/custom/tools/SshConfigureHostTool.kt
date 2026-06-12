package com.kai.custom.tools

import com.kai.custom.data.AppSettings
import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import com.kai.custom.sandbox.LinuxSandboxManager
import com.kai.custom.sandbox.SandboxState
import com.kai.custom.sandbox.SshConfigManager
import org.koin.java.KoinJavaComponent.inject
import java.io.File

object SshConfigureHostTool : Tool {
    private val sandboxManager: LinuxSandboxManager by inject(LinuxSandboxManager::class.java)
    private val appSettings: AppSettings by inject(AppSettings::class.java)

    private fun installCmd(): String = if (appSettings.getSandboxDistro() == "ubuntu") "apt-get install -y" else "apk add"

    override val schema: ToolSchema
        get() = ToolSchema(
            name = "ssh_configure_host",
            description = buildDescription(),
            parameters = mapOf(
                "alias" to ParameterSchema(
                    "string",
                    "Short name used to invoke this host (e.g. 'prod', 'my-vps'). Must contain no whitespace.",
                    true,
                ),
                "hostname" to ParameterSchema(
                    "string",
                    "Hostname or IP address of the remote server (e.g. '192.168.1.100' or 'myserver.example.com').",
                    true,
                ),
                "user" to ParameterSchema(
                    "string",
                    "SSH login username. Defaults to the sandbox user if omitted.",
                    false,
                ),
                "port" to ParameterSchema(
                    "number",
                    "SSH port. Defaults to 22 if omitted.",
                    false,
                ),
                "identity_file" to ParameterSchema(
                    "string",
                    "Path to the private key file inside the sandbox (e.g. '/home/user/.ssh/id_ed25519').",
                    false,
                ),
                "known_host_line" to ParameterSchema(
                    "string",
                    "A line to append to ~/.ssh/known_hosts to pre-accept the host key (TOFU skip). Format: 'hostname key-type base64-key'.",
                    false,
                ),
            ),
        )

    val toolInfo = ToolInfo(
        id = "ssh_configure_host",
        name = "Configure SSH Host",
        description = "Register a named SSH host for the Linux sandbox",
        nameRes = null,
        descriptionRes = null,
        isEnabled = false,
    )

    private fun buildDescription(): String = buildString {
        appendLine("Register a named SSH host alias in the Linux sandbox so subsequent execute_shell_command calls can run `ssh <alias>` instead of repeating user/host/port/identity flags every time.")
        appendLine()
        appendLine("What this writes inside the sandbox:")
        appendLine("- ~/.ssh/config: a Host block for the alias. Calling again with the same alias replaces the previous block (idempotent).")
        appendLine("- Defaults block at the top of the config on first use: ServerAliveInterval + ServerAliveCountMax (keep idle TCP connections alive through NAT) and StrictHostKeyChecking=accept-new (auto-accept new host keys into ~/.ssh/known_hosts on first connect, but still reject changed keys — sane TOFU without an interactive prompt this shell can't answer).")
        appendLine("- Optionally appends a line to ~/.ssh/known_hosts to skip the first-connect TOFU step entirely.")
        appendLine()
        appendLine("This tool does NOT create or upload private keys. To make a key usable, the user must place it under ~/.ssh in the sandbox separately. Be aware that any key text passed through chat (including via execute_shell_command's `cat > ~/.ssh/id_x <<EOF ...`) goes to the model provider in cleartext — ask the user before doing that.")
        appendLine()
        append("Password-only remotes: openssh inside this sandbox can't field interactive password prompts on its own (no PTY; ssh reads from /dev/tty, not stdin, so heredoc fallback does not work). Install sshpass once (`${installCmd()} sshpass` via execute_shell_command) and invoke as `sshpass -p '<password>' ssh <alias> '<remote-cmd>'`, or `sshpass -f <file> ssh <alias>` to keep the password out of the command line. sshpass fakes a PTY internally, which is the only path that actually delivers a password.")
        appendLine()
        appendLine("Connection persistence (\"held connections\") is NOT available — openssh's ControlMaster multiplexing requires the link() syscall to create its control socket, and Android blocks link() for app processes regardless of file ownership. Each ssh call does a full handshake. Don't fight this; don't try to seed your own ControlPath.")
        appendLine()
        appendLine("After configuring, drive ssh from execute_shell_command:")
        appendLine("- `ssh myalias 'remote cmd'`")
        appendLine("- `scp file myalias:`")
        appendLine("- `sftp myalias`")
        append("Auth, port, identity all come from the config block — no flags needed. ALWAYS invoke by the alias, never `user@hostname`; bypassing the alias bypasses every setting this tool just wrote.")
    }

    override suspend fun execute(args: Map<String, Any>): Any {
        if (sandboxManager.state.value !is SandboxState.Ready) {
            return mapOf(
                "success" to false,
                "error" to "Linux sandbox is not installed. Set it up in Settings > Tools.",
            )
        }
        val alias = (args["alias"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return mapOf("success" to false, "error" to "alias is required")
        val hostname = (args["hostname"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return mapOf("success" to false, "error" to "hostname is required")
        val user = (args["user"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val port = (args["port"] as? Number)?.toInt()
        val identityFile = (args["identity_file"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val knownHostLine = (args["known_host_line"] as? String)?.trim()?.takeIf { it.isNotEmpty() }

        val manager = SshConfigManager(File(sandboxManager.homePath))
        return try {
            val configChanged = manager.upsertHost(alias, hostname, user, port, identityFile)
            val knownHostsChanged = knownHostLine?.let { manager.appendKnownHostLine(it) } ?: false
            mapOf(
                "success" to true,
                "alias" to alias,
                "config_changed" to configChanged,
                "known_hosts_changed" to knownHostsChanged,
                "example" to "ssh $alias",
            )
        } catch (e: IllegalArgumentException) {
            mapOf("success" to false, "error" to (e.message ?: "Invalid argument"))
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to write ssh config: ${e.message}")
        }
    }
}
