package com.kai.custom.tools

import com.kai.custom.SshConnectionManager
import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import org.koin.java.KoinJavaComponent.inject

private const val TOOL_DESCRIPTION = """Disconnect from the current SSH server.

Use this when you are done working on the remote server or need to reconnect with different credentials."""

object SshDisconnectTool : Tool {
    private val sshManager: SshConnectionManager by inject(SshConnectionManager::class.java)

    override val schema = ToolSchema(
        name = "ssh_disconnect",
        description = TOOL_DESCRIPTION,
        parameters = emptyMap(),
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        sshManager.disconnect()
        return mapOf("success" to true as Any, "message" to "Disconnected from SSH server" as Any)
    }

    val toolInfo = ToolInfo(
        id = "ssh_disconnect",
        name = "Disconnect SSH",
        description = "Disconnect from the current SSH server",
        nameRes = null,
        descriptionRes = null,
        isEnabled = false,
    )
}
