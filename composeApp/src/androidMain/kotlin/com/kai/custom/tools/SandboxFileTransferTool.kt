package com.kai.custom.tools

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.kai.custom.SandboxController
import com.kai.custom.data.AppSettings
import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import org.koin.java.KoinJavaComponent.inject
import java.io.File

object SandboxFileTransferTool : Tool {
    private val sandboxController: SandboxController by inject(SandboxController::class.java)
    private val context: Context by inject(Context::class.java)
    private val appSettings: AppSettings by inject(AppSettings::class.java)

    override val schema = ToolSchema(
        name = "sandbox_file_transfer",
        description = "Copy files between the Linux sandbox and Android phone storage. Use this when standard shell cp/mv commands fail (e.g. permission errors on /sdcard, or cross-filesystem issues). " +
            "This tool uses base64 encoding through the sandbox shell to bypass bind-mount and permission problems. " +
            "It can copy both text and binary files (images, PDFs, zips, etc.). " +
            "If a SAF work directory is configured in Settings, android_path is relative to that directory. " +
            "Otherwise it's relative to Android/data/com.kai.custom/files/. " +
            "Sandbox paths use the standard format: /root/... for sandbox home, /sdcard/... for phone storage.",
        parameters = mapOf(
            "action" to ParameterSchema(
                type = "string",
                description = "\"to_sandbox\" to copy FROM Android TO sandbox, \"to_android\" to copy FROM sandbox TO Android",
                required = true,
            ),
            "android_path" to ParameterSchema(
                type = "string",
                description = "Path on the Android side. If SAF work dir is set, relative to that dir (e.g. \"Download/report.pdf\"). " +
                    "Otherwise, relative to Android/data/com.kai.custom/files/.",
                required = true,
            ),
            "sandbox_path" to ParameterSchema(
                type = "string",
                description = "Path inside the sandbox filesystem (e.g. \"/root/output/report.pdf\" or \"/root/input/data.csv\")",
                required = true,
            ),
            "max_bytes" to ParameterSchema(
                type = "integer",
                description = "Maximum file size in bytes for transfer direction (default: 52428800 = 50MB, max: 524288000 = 500MB). Only applies to to_android direction (reading from sandbox).",
                required = false,
            ),
        ),
    )

    val toolInfo = ToolInfo(
        id = "sandbox_file_transfer",
        name = "Sandbox File Transfer",
        description = "Copy files between sandbox and Android storage",
        nameRes = null,
        descriptionRes = null,
        isEnabled = false,
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val action = (args["action"] as? String)?.trim()?.lowercase()
            ?: return mapOf("success" to false, "error" to "action is required (to_sandbox or to_android)")
        val androidRel = (args["android_path"] as? String)?.trim()
            ?: return mapOf("success" to false, "error" to "android_path is required")
        val sandboxPath = (args["sandbox_path"] as? String)?.trim()
            ?: return mapOf("success" to false, "error" to "sandbox_path is required")

        val maxBytes = ((args["max_bytes"] as? Number)?.toInt() ?: 52_428_800).coerceIn(1, 524_288_000)

        try {
            val safWorkDir = appSettings.getSandboxWorkDir()
            return if (safWorkDir.isNotEmpty()) {
                transferViaSaf(action, androidRel, sandboxPath, maxBytes, safWorkDir)
            } else {
                transferViaPrivateDir(action, androidRel, sandboxPath, maxBytes)
            }
        } catch (e: Exception) {
            return mapOf("success" to false, "error" to "File transfer failed: ${e.message}")
        }
    }

    private suspend fun transferViaSaf(
        action: String,
        androidRel: String,
        sandboxPath: String,
        maxBytes: Int,
        safWorkDir: String,
    ): Any {
        val treeUri = Uri.parse(safWorkDir)
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childDocId = "$treeDocId/$androidRel"
        val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)

        return when (action) {
            "to_sandbox" -> {
                val inputStream = context.contentResolver.openInputStream(childUri)
                    ?: return mapOf("success" to false, "error" to "File not found at $androidRel in SAF directory")
                val data = inputStream.use { it.readBytes() }
                val ok = sandboxController.writeBinaryFile(sandboxPath, data)
                if (ok) {
                    mapOf(
                        "success" to true,
                        "direction" to "Android → Sandbox",
                        "android_path" to childUri.toString(),
                        "sandbox_path" to sandboxPath,
                        "bytes" to data.size,
                        "message" to "Copied ${data.size} bytes to sandbox:$sandboxPath",
                    )
                } else {
                    mapOf("success" to false, "error" to "Failed to write file to sandbox at $sandboxPath")
                }
            }
            "to_android" -> {
                val content = sandboxController.readTextFile(sandboxPath, maxBytes)
                if (content == null) {
                    return mapOf("success" to false, "error" to "File not found in sandbox: $sandboxPath")
                }
                val rawBytes = try {
                    java.util.Base64.getDecoder().decode(content)
                } catch (_: Exception) {
                    content.toByteArray()
                }
                if (rawBytes.size.toLong() > maxBytes) {
                    return mapOf("success" to false, "error" to "File too large (${rawBytes.size} bytes > $maxBytes limit). Use a larger max_bytes value.")
                }
                writeViaSaf(childUri, treeUri, androidRel, rawBytes)
                mapOf(
                    "success" to true,
                    "direction" to "Sandbox → Android",
                    "android_path" to "SAF:$androidRel",
                    "sandbox_path" to sandboxPath,
                    "bytes" to rawBytes.size,
                    "message" to "Copied ${rawBytes.size} bytes from sandbox to $androidRel",
                )
            }
            else -> mapOf("success" to false, "error" to "action must be 'to_sandbox' or 'to_android'")
        }
    }

    private fun writeViaSaf(childUri: Uri, treeUri: Uri, fileName: String, data: ByteArray) {
        try {
            context.contentResolver.openOutputStream(childUri)?.use { it.write(data) }
            return
        } catch (_: Exception) { }
        val mimeType = when {
            fileName.endsWith(".txt") -> "text/plain"
            fileName.endsWith(".pdf") -> "application/pdf"
            fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") -> "image/jpeg"
            fileName.endsWith(".png") -> "image/png"
            fileName.endsWith(".zip") -> "application/zip"
            fileName.endsWith(".json") -> "application/json"
            fileName.endsWith(".md") -> "text/markdown"
            fileName.endsWith(".csv") -> "text/csv"
            else -> "application/octet-stream"
        }
        val createdUri = DocumentsContract.createDocument(
            context.contentResolver, treeUri, mimeType, fileName
        )
        if (createdUri != null) {
            context.contentResolver.openOutputStream(createdUri)?.use { it.write(data) }
        }
    }

    private suspend fun transferViaPrivateDir(
        action: String,
        androidRel: String,
        sandboxPath: String,
        maxBytes: Int,
    ): Any {
        val externalDir = context.getExternalFilesDir(null)
            ?: return mapOf("success" to false, "error" to "External storage directory not available")
        val baseDir = File(externalDir, "transfer")
        val androidFile = File(baseDir, androidRel)

        if (androidFile.exists() && !androidFile.isFile) {
            return mapOf("success" to false, "error" to "Not a file: ${androidFile.absolutePath}")
        }

        return when (action) {
            "to_sandbox" -> {
                if (!androidFile.exists()) {
                    mapOf(
                        "success" to false,
                        "error" to "Android file not found: ${androidFile.absolutePath}",
                        "hint" to "The android_path is relative to ${baseDir.absolutePath}. Use a file manager to place files there.",
                    )
                } else {
                    val size = androidFile.length()
                    val data = androidFile.readBytes()
                    val ok = sandboxController.writeBinaryFile(sandboxPath, data)
                    if (ok) {
                        mapOf(
                            "success" to true,
                            "direction" to "Android → Sandbox",
                            "android_path" to androidFile.absolutePath,
                            "sandbox_path" to sandboxPath,
                            "bytes" to size,
                            "message" to "Copied $size bytes to sandbox:$sandboxPath",
                        )
                    } else {
                        mapOf("success" to false, "error" to "Failed to write file to sandbox at $sandboxPath")
                    }
                }
            }
            "to_android" -> {
                androidFile.parentFile?.mkdirs()
                val content = sandboxController.readTextFile(sandboxPath, maxBytes)
                if (content == null) {
                    mapOf("success" to false, "error" to "File not found in sandbox: $sandboxPath")
                } else {
                    val rawBytes = try {
                        java.util.Base64.getDecoder().decode(content)
                    } catch (_: Exception) {
                        content.toByteArray()
                    }
                    if (rawBytes.size.toLong() > maxBytes) {
                        mapOf("success" to false, "error" to "File too large (${rawBytes.size} bytes > $maxBytes limit). Use a larger max_bytes value.")
                    } else {
                        androidFile.writeBytes(rawBytes)
                        mapOf(
                            "success" to true,
                            "direction" to "Sandbox → Android",
                            "android_path" to androidFile.absolutePath,
                            "sandbox_path" to sandboxPath,
                            "bytes" to rawBytes.size,
                            "message" to "Copied ${rawBytes.size} bytes from sandbox to $androidRel",
                        )
                    }
                }
            }
            else -> mapOf("success" to false, "error" to "action must be 'to_sandbox' or 'to_android'")
        }
    }
}
