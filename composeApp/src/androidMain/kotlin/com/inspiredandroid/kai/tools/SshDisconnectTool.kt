package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.SshConnectionManager
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
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
