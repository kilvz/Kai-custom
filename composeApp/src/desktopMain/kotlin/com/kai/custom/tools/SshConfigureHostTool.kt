package com.kai.custom.tools

import com.kai.custom.SandboxController
import com.kai.custom.data.AppSettings
import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import com.kai.custom.sandbox.SshConfigManager
import org.koin.java.KoinJavaComponent.inject
import java.io.File

object SshConfigureHostTool : Tool {
    private val sandboxController: SandboxController by inject(SandboxController::class.java)
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
                    "Hostname or IP address of the remote server.",
                    true,
                ),
                "user" to ParameterSchema(
                    "string",
                    "SSH username (default: root).",
                    false,
                ),
                "port" to ParameterSchema(
                    "integer",
                    "SSH port (default: 22).",
                    false,
                ),
            ),
        )

    private fun buildDescription(): String = buildString {
        appendLine("Configure the sandbox's SSH config so it can connect to a remote host via `ssh <alias>`.")
        appendLine("Steps the tool performs:")
        appendLine("1. Installs openssh-client (if missing) using the sandbox's package manager.")
        appendLine("2. Appends a Host alias block to ~/.ssh/config.")
        appendLine("3. Returns the alias and hostname on success.")
        appendLine()
        appendLine("After running this tool, the sandbox (or AI) can connect with `ssh <alias>`.")
        appendLine("However, you'll still need to provide authentication (password or SSH key).")
        append("Authentication is not handled by this tool.")
    }

    override suspend fun execute(args: Map<String, Any>): Any {
        val alias = (args["alias"] as? String)?.trim()
            ?: return mapOf("success" to false, "error" to "alias is required")
        if (alias.contains(Regex("\\s"))) {
            return mapOf("success" to false, "error" to "alias must not contain whitespace")
        }

        val hostname = (args["hostname"] as? String)?.trim()
            ?: return mapOf("success" to false, "error" to "hostname is required")
        val user = (args["user"] as? String)?.trim() ?: "root"
        val port = ((args["port"] as? Number)?.toInt() ?: 22).coerceIn(1, 65535)

        if (!sandboxController.status.value.ready) {
            return mapOf("success" to false, "error" to "Sandbox is not ready. Set it up in Settings > Sandbox.")
        }

        return try {
            val whichOutput = sandboxController.executeCommand("which ssh 2>/dev/null && echo FOUND || echo NOTFOUND", useRoot = true, timeoutSeconds = 5)
            if (!whichOutput.contains("FOUND")) {
                val installOutput = sandboxController.executeCommand("${installCmd()} openssh-client 2>&1 | tail -5", useRoot = true, timeoutSeconds = 120)
                if (installOutput.contains("ERROR") || installOutput.contains("Unable to locate package") || installOutput.contains("not found")) {
                    return mapOf(
                        "success" to false,
                        "error" to "Failed to install openssh-client. Install it manually: ${installCmd()} openssh-client",
                    )
                }
            }

            val homePath = "/root"
            val configManager = SshConfigManager(File(homePath))
            configManager.ensureDefaults()
            configManager.upsertHost(alias = alias, hostname = hostname, user = user, port = port)

            mapOf(
                "success" to true,
                "alias" to alias,
                "hostname" to hostname,
                "message" to "SSH config updated. Connect with: ssh $alias",
            )
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to configure SSH host: ${e.message}")
        }
    }

    val toolInfo = ToolInfo(
        id = "ssh_configure_host",
        name = "Configure SSH Host",
        description = "Configure sandbox SSH config with a remote host alias",
        nameRes = null,
        descriptionRes = null,
        isEnabled = false,
    )
}
