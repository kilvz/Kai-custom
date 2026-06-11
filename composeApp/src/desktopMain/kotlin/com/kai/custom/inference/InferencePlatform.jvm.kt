package com.kai.custom.inference

import com.kai.custom.getAppFilesDirectory
import java.io.File
import java.lang.management.ManagementFactory

actual fun getModelStorageDirectory(): String = getAppFilesDirectory() + "/litert_models"

actual fun getModelCacheDirectory(): String = System.getProperty("java.io.tmpdir") ?: getAppFilesDirectory()

actual fun getAvailableMemoryBytes(): Long = Long.MAX_VALUE // Desktop OSes manage memory via swap and cache eviction; skip the check

actual fun getTotalMemoryBytes(): Long = Long.MAX_VALUE

actual fun getAvailableDiskSpaceBytes(path: String): Long {
    var dir = File(path)
    while (!dir.exists()) {
        dir = dir.parentFile ?: return 0L
    }
    return dir.usableSpace
}

actual fun startDownloadNotificationService() {
    // No foreground service needed on desktop
}

actual fun stopDownloadNotificationService() {
    // No foreground service needed on desktop
}

actual fun updateDownloadNotificationProgress(percent: Int) {
    // No notification on desktop
}

actual fun importPlatformFile(platformFile: io.github.vinceglb.filekit.PlatformFile, isGguf: Boolean): String? {
    return null
}

actual fun resolveSafUriToLocal(uri: String, localPath: String): String? = null

actual fun linkGgufExternal(uri: String, displayName: String, sizeBytes: Long): String? {
    return importSafFile(uri, true)
}

actual fun importSafFile(uri: String, isGguf: Boolean): String? {
    // Desktop will just use the file path directly or copy it.
    // For simplicity, we just save the path to the saf file.
    try {
        val baseId = "imported_" + java.util.UUID.randomUUID().toString().take(8)
        val id = if (isGguf) "gguf_$baseId" else baseId
        // Wait, GgufInferenceEngine isn't visible here? We can just use File(getModelStorageDirectory(), "../gguf_models")
        val modelsDir = if (isGguf) {
            java.io.File(java.io.File(getModelStorageDirectory()).parentFile, "gguf_models")
        } else {
            java.io.File(getModelStorageDirectory())
        }
        modelsDir.mkdirs()
        val modelDir = java.io.File(modelsDir, id)
        modelDir.mkdirs()
        
        val extension = if (isGguf) "gguf" else "litertlm"
        val safFile = java.io.File(modelDir, "model.$extension.saf")
        safFile.writeText(uri)
        return id
    } catch (e: Exception) {
        return null
    }
}

@androidx.compose.runtime.Composable
actual fun rememberSafFilePicker(
    extensions: List<String>,
    onResult: (uriOrPath: String?, displayName: String?, sizeBytes: Long) -> Unit
): () -> Unit {
    val filePicker = io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher(
        type = io.github.vinceglb.filekit.dialogs.FileKitType.File(extensions = extensions),
    ) { platformFile ->
        if (platformFile != null) {
            // JVM PlatformFile has file property which is a java.io.File
            val fileMethod = platformFile.javaClass.methods.firstOrNull { it.name == "getFile" }
            val file = fileMethod?.invoke(platformFile) as? java.io.File
            if (file != null) {
                onResult(file.absolutePath, file.name, file.length())
            } else {
                onResult(null, null, 0L)
            }
        } else {
            onResult(null, null, 0L)
        }
    }
    return { filePicker.launch() }
}

actual class PlatformSafHandle

actual fun openSafPath(path: String): PlatformSafHandle? = null

actual fun getSafResolvedPath(handle: PlatformSafHandle): String = ""

actual fun closeSafHandle(handle: PlatformSafHandle) {}

