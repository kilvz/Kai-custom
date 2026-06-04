package com.kai.custom.tools

import com.kai.custom.SandboxController
import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import org.koin.java.KoinJavaComponent.inject
import java.awt.Desktop
import java.io.File

private const val OPEN_FILE_DESCRIPTION = """Open a file from the sandbox in the user's default desktop application — browser for HTML, image viewer for PNG/JPG, PDF viewer for PDF, text editor for .md/.txt, etc. This is how you show finished work to the user.

Path is relative to /root. What the shell tool calls /root/page.html, this tool takes as path="page.html".

Write self-contained files — for HTML, inline all CSS and JavaScript in the same file (no external <link rel="stylesheet"> or <script src=...>), since the file is opened in isolation."""

object OpenFileTool : Tool {
    private val sandboxController: SandboxController by inject(SandboxController::class.java)

    override val schema = ToolSchema(
        name = "open_file",
        description = OPEN_FILE_DESCRIPTION,
        parameters = mapOf(
            "path" to ParameterSchema(
                "string",
                "Path relative to /root, e.g. site/index.html or notes.md",
                true,
            ),
        ),
    )

    val toolInfo = ToolInfo(
        id = "open_file",
        name = "Open File",
        description = "Open sandbox files in desktop applications",
        nameRes = null,
        descriptionRes = null,
        isEnabled = false,
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val path = (args["path"] as? String)?.trim()
            ?: return mapOf("success" to false, "error" to "path is required")

        if (!sandboxController.status.value.ready) {
            return mapOf("success" to false, "error" to "Sandbox is not ready.")
        }

        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            return mapOf("success" to false, "error" to "Desktop open action is not supported on this platform")
        }

        return try {
            val content = sandboxController.readTextFile("root/$path")
                ?: return mapOf("success" to false, "error" to "File not found: $path")

            val tempFile = File.createTempFile("kai_open_", path.substringAfterLast('.').let { ".$it" })
            tempFile.writeText(content)
            tempFile.deleteOnExit()

            Desktop.getDesktop().open(tempFile)

            mapOf(
                "success" to true,
                "path" to path,
                "local_path" to tempFile.absolutePath,
            )
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to open file: ${e.message}")
        }
    }
}
