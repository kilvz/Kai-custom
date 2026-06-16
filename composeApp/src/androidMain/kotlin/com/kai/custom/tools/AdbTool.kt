package com.kai.custom.tools

import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolSchema
import com.kai.custom.shizuku.ShizukuManager

object AdbTool : Tool {
    override val schema = ToolSchema(
        name = "run_adb",
        description = "Run shell commands with ADB-level privileges on the Android device via Shizuku. Access system services outside the sandbox — pm, am, dumpsys, settings, wm, input, device_config, content, appops, cmd, and any other shell-level commands. Requires Shizuku installed and permission granted. If Shizuku is not running, it must be started first via 'adb shell sh /data/local/tmp/shizuku start' on a computer, or Wireless Debugging on Android 11+.",
        parameters = mapOf(
            "command" to ParameterSchema(type = "string", description = "Shell command to execute with ADB privileges (e.g. 'pm list packages | grep kai', 'dumpsys battery', 'settings get global airplane_mode_on')", required = true),
            "timeout" to ParameterSchema(type = "integer", description = "Maximum execution time in seconds (default: 30, max: 60)", required = false),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val command = args["command"] as? String
            ?: return mapOf("success" to false, "error" to "command is required")

        if (!ShizukuManager.isAvailable) {
            return mapOf(
                "success" to false,
                "error" to "Shizuku is not available. Install Shizuku from https://shizuku.rikka.app, then start it via: adb shell sh /data/local/tmp/shizuku start",
            )
        }

        if (!ShizukuManager.hasPermission) {
            ShizukuManager.requestPermission()
            return mapOf(
                "success" to false,
                "error" to "Shizuku permission not granted. Accept the permission prompt on your device, then try again.",
            )
        }

        val timeout = ((args["timeout"] as? Number)?.toInt() ?: 30).coerceIn(5, 60)

        return ShizukuManager.runCommand(command, timeout.toLong())
    }
}
