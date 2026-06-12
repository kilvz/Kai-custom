package com.kai.custom.tools

import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolSchema
import com.kai.custom.root.RootManager

object RootTool : Tool {
    override val schema = ToolSchema(
        name = "run_root",
        description = "Run shell commands with root privileges (UID 0) on the Android device via su. Full system access — mount, insmod, iptables, ptrace, and any other operation requiring real root. Requires a rooted device with su binary available. If su is not available, the tool returns an error.",
        parameters = mapOf(
            "command" to ParameterSchema(type = "string", description = "Shell command to execute with root privileges (e.g. 'mount -o remount,rw /system', 'iptables -L', 'cat /proc/1/maps')", required = true),
            "timeout" to ParameterSchema(type = "integer", description = "Maximum execution time in seconds (default: 30, max: 60)", required = false),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val command = args["command"] as? String
            ?: return mapOf("success" to false, "error" to "command is required")

        if (!RootManager.isAvailable) {
            return mapOf(
                "success" to false,
                "error" to "su is not available. Root this device or check your su installation.",
            )
        }

        val timeout = ((args["timeout"] as? Number)?.toInt() ?: 30).coerceIn(5, 60)

        return RootManager.runCommand(command, timeout.toLong())
    }
}
