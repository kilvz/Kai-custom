package com.kai.custom.tools

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
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

    private val HOST_STORAGE_PREFIXES = arrayOf("/sdcard", "/storage/emulated", "/storage/self", "/data/media")

    override val schema = ToolSchema(
        name = "sandbox_file_transfer",
        description = "Copy files between the Linux sandbox and Android phone storage. Use this when standard shell cp/mv commands fail (e.g. permission errors on /sdcard, or cross-filesystem issues). " +
            "This tool uses base64 encoding through the sandbox shell to bypass bind-mount and permission problems. " +
            "It can copy both text and binary files (images, PDFs, zips, etc.). " +
            "android_path can be an absolute path like /sdcard/Download/report.pdf (copies directly to that location). " +
            "Relative paths go to Android/data/com.kai.custom/files/transfer/. " +
            "Sandbox paths use the standard format: /root/... for sandbox home, /sdcard/... for phone storage.",
        parameters = mapOf(
            "action" to ParameterSchema(
                type = "string",
                description = "\"to_sandbox\" to copy FROM Android TO sandbox, \"to_android\" to copy FROM sandbox TO Android",
                required = true,
            ),
            "android_path" to ParameterSchema(
                type = "string",
                description = "Path on the Android side. Can be absolute (/sdcard/Download/file.pdf), " +
                    "or relative to SAF work dir if configured, or relative to Android/data/com.kai.custom/files/transfer/. " +
                    "For absolute paths, Kai needs 'All files access' permission on Android 11+. " +
                    "Grant it in Settings > Apps > Special app access > All files access.",
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
        val androidPath = (args["android_path"] as? String)?.trim()
            ?: return mapOf("success" to false, "error" to "android_path is required")
        val sandboxPath = (args["sandbox_path"] as? String)?.trim()
            ?: return mapOf("success" to false, "error" to "sandbox_path is required")

        val maxBytes = ((args["max_bytes"] as? Number)?.toInt() ?: 52_428_800).coerceIn(1, 524_288_000)

        try {
            if (isAbsoluteHostPath(androidPath)) {
                return transferViaHostPath(action, androidPath, sandboxPath, maxBytes)
            }
            val safWorkDir = appSettings.getSandboxWorkDir()
            return if (safWorkDir.isNotEmpty()) {
                transferViaSaf(action, androidPath, sandboxPath, maxBytes, safWorkDir)
            } else {
                transferViaPrivateDir(action, androidPath, sandboxPath, maxBytes)
            }
        } catch (e: Exception) {
            return mapOf("success" to false, "error" to "File transfer failed: ${e.message}")
        }
    }

    private fun isAbsoluteHostPath(path: String): Boolean =
        HOST_STORAGE_PREFIXES.any { path.startsWith(it) }

    private fun resolveHostPath(path: String): String {
        if (path.startsWith("/sdcard/")) {
            val sdcardRoot = when {
                File("/storage/emulated/0").exists() -> "/storage/emulated/0"
                File("/storage/self/primary").exists() -> "/storage/self/primary"
                else -> "/storage/emulated/0"
            }
            return path.replaceFirst("/sdcard", sdcardRoot)
        }
        return path
    }

    private fun hasAllFilesAccess(): Boolean {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager()
        }
        return true
    }

    private fun getFileName(path: String): String {
        val clean = path.trimEnd('/')
        return clean.substringAfterLast('/')
    }

    private fun getParentDir(path: String): String {
        val clean = path.trimEnd('/')
        val idx = clean.lastIndexOf('/')
        return if (idx > 0) clean.substring(0, idx) else ""
    }

    private fun getMimeType(fileName: String): String = when {
        fileName.endsWith(".txt") -> "text/plain"
        fileName.endsWith(".pdf") -> "application/pdf"
        fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") -> "image/jpeg"
        fileName.endsWith(".png") -> "image/png"
        fileName.endsWith(".gif") -> "image/gif"
        fileName.endsWith(".webp") -> "image/webp"
        fileName.endsWith(".zip") -> "application/zip"
        fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz") -> "application/gzip"
        fileName.endsWith(".json") -> "application/json"
        fileName.endsWith(".md") -> "text/markdown"
        fileName.endsWith(".csv") -> "text/csv"
        fileName.endsWith(".xml") -> "application/xml"
        fileName.endsWith(".html") || fileName.endsWith(".htm") -> "text/html"
        fileName.endsWith(".py") -> "text/x-python"
        fileName.endsWith(".sh") -> "text/x-shellscript"
        fileName.endsWith(".mp4") -> "video/mp4"
        fileName.endsWith(".mp3") -> "audio/mpeg"
        fileName.endsWith(".wav") -> "audio/wav"
        fileName.endsWith(".apk") -> "application/vnd.android.package-archive"
        else -> "application/octet-stream"
    }

    private suspend fun transferViaHostPath(
        action: String,
        androidPath: String,
        sandboxPath: String,
        maxBytes: Int,
    ): Any {
        return when (action) {
            "to_sandbox" -> {
                val data = readFromHostPath(androidPath, maxBytes)
                if (data == null) {
                    val apiHint = if (Build.VERSION.SDK_INT >= 30 && !hasAllFilesAccess()) {
                        " Grant 'All files access' to Kai in Settings > Apps > Special app access > All files access."
                    } else ""
                    return mapOf("success" to false, "error" to "Cannot read file at $androidPath. File not found or permission denied.$apiHint")
                }
                val ok = sandboxController.writeBinaryFile(sandboxPath, data)
                if (ok) {
                    mapOf(
                        "success" to true,
                        "direction" to "Android → Sandbox",
                        "android_path" to androidPath,
                        "sandbox_path" to sandboxPath,
                        "bytes" to data.size,
                        "message" to "Copied ${data.size} bytes from $androidPath to sandbox:$sandboxPath",
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
                val writeError = writeToHostPath(androidPath, rawBytes)
                if (writeError == null) {
                    mapOf(
                        "success" to true,
                        "direction" to "Sandbox → Android",
                        "android_path" to androidPath,
                        "sandbox_path" to sandboxPath,
                        "bytes" to rawBytes.size,
                        "message" to "Copied ${rawBytes.size} bytes from sandbox to $androidPath",
                    )
                } else {
                    mapOf("success" to false, "error" to writeError)
                }
            }
            else -> mapOf("success" to false, "error" to "action must be 'to_sandbox' or 'to_android'")
        }
    }

    private fun readFromHostPath(androidPath: String, maxBytes: Int): ByteArray? {
        val resolved = resolveHostPath(androidPath)
        val file = File(resolved)

        if (file.exists() && file.isFile && file.canRead()) {
            if (file.length() > maxBytes) return null
            return try { file.readBytes() } catch (_: Exception) { null }
        }

        return tryReadViaMediaStore(resolved, maxBytes)
    }

    private fun writeToHostPath(androidPath: String, data: ByteArray): String? {
        val resolved = resolveHostPath(androidPath)

        val directResult = tryWriteDirectFile(resolved, data)
        if (directResult) return null

        val mediaStoreResult = tryWriteViaMediaStore(androidPath, data)
        if (mediaStoreResult) return null

        val apiLevel = Build.VERSION.SDK_INT
        val hasAccess = hasAllFilesAccess()
        return when {
            apiLevel >= 30 && !hasAccess ->
                "Cannot write to $androidPath. Grant 'All files access' to Kai: open phone Settings > Apps > Special app access > All files access > enable for Kai. Then retry."
            apiLevel >= 30 ->
                "Cannot write to $androidPath. Even with all-files-access granted, this path is not writable. Ensure the parent directory exists."
            else -> "Cannot write to $androidPath. Check that parent directory exists and WRITE_EXTERNAL_STORAGE is granted."
        }
    }

    private fun tryWriteDirectFile(path: String, data: ByteArray): Boolean {
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeBytes(data)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun tryWriteViaMediaStore(androidPath: String, data: ByteArray): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val resolved = resolveHostPath(androidPath)
        val fileName = getFileName(androidPath)
        val mimeType = getMimeType(fileName)

        val relativePath = extractMediaStoreRelativePath(resolved) ?: return false

        return try {
            val volume = if (Build.VERSION.SDK_INT >= 30) MediaStore.VOLUME_EXTERNAL_PRIMARY else "external"
            val uri = if (relativePath.startsWith("Download/")) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri(volume)
            }
            val contentValues = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath)
                if (relativePath.startsWith("Download/")) {
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }
            val insertedUri = context.contentResolver.insert(uri, contentValues)
            if (insertedUri == null) return false

            context.contentResolver.openOutputStream(insertedUri)?.use { out ->
                out.write(data)
                out.flush()
            } ?: return false

            if (relativePath.startsWith("Download/")) {
                val pendingValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                context.contentResolver.update(insertedUri, pendingValues, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun extractMediaStoreRelativePath(absolutePath: String): String? {
        val sdcardRoots = listOf(
            "/storage/emulated/0/",
            "/storage/self/primary/",
            "/data/media/0/",
        )
        for (root in sdcardRoots) {
            if (absolutePath.startsWith(root)) {
                val relative = absolutePath.removePrefix(root)
                if (relative.isNotEmpty() && !relative.startsWith("Android/")) {
                    return relative.substringBeforeLast('/')
                }
            }
        }
        return null
    }

    private fun tryReadViaMediaStore(absolutePath: String, maxBytes: Int): ByteArray? {
        if (Build.VERSION.SDK_INT < 29) return null
        val fileName = getFileName(absolutePath)
        val relativeDir = extractMediaStoreRelativePath(absolutePath) ?: return null

        return try {
            val volume = if (Build.VERSION.SDK_INT >= 30) MediaStore.VOLUME_EXTERNAL_PRIMARY else "external"
            val uri = MediaStore.Files.getContentUri(volume)
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.SIZE,
            )
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?"
            val selectionArgs = arrayOf(fileName, "$relativeDir/")
            val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val size = c.getLong(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE))
                    if (size > maxBytes || size <= 0) return@use null
                    val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                    val fileUri = Uri.withAppendedPath(uri, id.toString())
                    context.contentResolver.openInputStream(fileUri)?.use { input ->
                        input.readBytes().takeIf { it.size.toLong() <= maxBytes }
                    }
                } else null
            }
        } catch (_: Exception) {
            null
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
        val mimeType = getMimeType(fileName)
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
