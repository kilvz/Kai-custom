package com.kai.custom.inference

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import androidx.core.content.ContextCompat
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.io.FileOutputStream
import kotlin.uuid.ExperimentalUuidApi

internal val context: Context by inject(Context::class.java)

actual fun getModelStorageDirectory(): String = context.filesDir.absolutePath + "/litert_models"
actual fun getModelCacheDirectory(): String = context.cacheDir.absolutePath

private fun getMemoryInfo(): ActivityManager.MemoryInfo {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
}
actual fun getAvailableMemoryBytes(): Long = getMemoryInfo().availMem
actual fun getTotalMemoryBytes(): Long = getMemoryInfo().totalMem
actual fun getAvailableDiskSpaceBytes(path: String): Long {
    java.io.File(path).mkdirs()
    return StatFs(path).availableBytes
}
actual fun startDownloadNotificationService() {
    try {
        ContextCompat.startForegroundService(context, Intent(context, ModelDownloadService::class.java))
    } catch (_: Exception) {}
}
actual fun stopDownloadNotificationService() {
    try {
        context.stopService(Intent(context, ModelDownloadService::class.java))
    } catch (_: Exception) {}
}
actual fun updateDownloadNotificationProgress(percent: Int) {
    try {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = android.app.Notification.Builder(context, "kai_model_download_channel")
            .setContentTitle(context.getString(com.kai.custom.shared.R.string.app_name))
            .setContentText(context.getString(com.kai.custom.shared.R.string.download_progress_percent, percent))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true).setProgress(100, percent, false).build()
        mgr.notify(ModelDownloadService.NOTIFICATION_ID, n)
    } catch (_: Exception) {}
}

@OptIn(ExperimentalUuidApi::class)
actual fun importPlatformFile(platformFile: PlatformFile, isGguf: Boolean): String? {
    return try {
        val uriField = platformFile::class.java.getDeclaredField("uri")
        uriField.isAccessible = true
        val uri = uriField.get(platformFile) as? Uri ?: return null
        val id = "imported_${kotlin.uuid.Uuid.random().toString().take(8)}"
        val modelsDir = if (isGguf) GgufInferenceEngine.getGgufModelsDir() else File(getModelStorageDirectory())
        modelsDir.mkdirs()
        val modelDir = File(modelsDir, id)
        modelDir.mkdirs()
        val ext = if (isGguf) "gguf" else "litertlm"
        val targetFile = File(modelDir, "model.$ext")
        val nameFile = File(modelDir, "name.txt")
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nIdx >= 0) nameFile.writeText(cursor.getString(nIdx) ?: id)
                }
            }
        } catch (_: Exception) {
            nameFile.writeText(id)
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buf = ByteArray(65536)
                var r: Int
                while (input.read(buf).also { r = it } != -1) output.write(buf, 0, r)
            }
        } ?: return null
        if (!targetFile.exists() || targetFile.length() < 1000) {
            modelDir.deleteRecursively()
            return null
        }
        id
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalUuidApi::class)
actual fun resolveSafUriToLocal(uri: String, localPath: String): String? {
    return try {
        val parsedUri = Uri.parse(uri)
        val targetFile = java.io.File(localPath)
        targetFile.parentFile?.mkdirs()
        if (targetFile.exists()) return targetFile.absolutePath
        context.contentResolver.openInputStream(parsedUri)?.use { input ->
            java.io.FileOutputStream(targetFile).use { output ->
                val buf = ByteArray(65536)
                var r: Int
                while (input.read(buf).also { r = it } != -1) output.write(buf, 0, r)
            }
        } ?: return null
        if (!targetFile.exists() || targetFile.length() < 1000) {
            targetFile.delete()
            return null
        }
        targetFile.absolutePath
    } catch (e: Exception) {
        android.util.Log.e("InfPlat", "resolveSafUriToLocal failed", e)
        null
    }
}

actual fun linkGgufExternal(uri: String, displayName: String, sizeBytes: Long): String? = try {
    val parsedUri = Uri.parse(uri)
    val id = "gguf_ext_" + displayName.removeSuffix(".gguf")
        .replace(Regex("[^a-zA-Z0-9_\\-]"), "").trim().take(80)
        .replace(" ", "_").ifEmpty { "external" }
    val modelsDir = GgufInferenceEngine.getGgufModelsDir()
    modelsDir.mkdirs()
    var modelDir = File(modelsDir, id)
    var suffix = 1
    while (modelDir.exists()) {
        modelDir = File(modelsDir, "${id}_$suffix")
        suffix++
    }
    modelDir.mkdirs()
    val safFile = File(modelDir, "$displayName.saf")
    safFile.writeText(parsedUri.toString())
    val nameFile = File(modelDir, "name.txt")
    nameFile.writeText(displayName.removeSuffix(".gguf"))
    if (sizeBytes > 0L) {
        File(modelDir, "model.size").writeText(sizeBytes.toString())
    }
    try {
        context.contentResolver.takePersistableUriPermission(parsedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: Exception) {}
    modelDir.name
} catch (e: Exception) {
    android.util.Log.e("InfPlat", "linkGgufExternal failed", e)
    null
}

actual fun importSafFile(uri: String, isGguf: Boolean): String? {
    return try {
        val parsedUri = Uri.parse(uri)
        var displayName = "model"
        try {
            context.contentResolver.query(parsedUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nIdx >= 0) displayName = cursor.getString(nIdx) ?: "model"
                }
            }
        } catch (_: Exception) {}
        val folderName = displayName.removeSuffix(".gguf").removeSuffix(".litertlm")
            .replace(Regex("[^a-zA-Z0-9_\\- ]"), "").trim().take(100)
            .replace(" ", "_").ifEmpty { "imported_model" }
        val id = folderName
        val modelsDir = if (isGguf) {
            GgufInferenceEngine.getGgufModelsDir()
        } else {
            java.io.File(getModelStorageDirectory())
        }
        modelsDir.mkdirs()
        // If folder already exists, append number
        var modelDir = java.io.File(modelsDir, id)
        var suffix = 1
        while (modelDir.exists()) {
            modelDir = java.io.File(modelsDir, "${id}_$suffix")
            suffix++
        }
        modelDir.mkdirs()

        val targetFile = java.io.File(modelDir, displayName)

        context.contentResolver.openInputStream(parsedUri)?.use { input ->
            java.io.FileOutputStream(targetFile).use { output ->
                val buf = ByteArray(65536)
                var r: Int
                while (input.read(buf).also { r = it } != -1) output.write(buf, 0, r)
            }
        } ?: return null
        if (!targetFile.exists() || targetFile.length() < 1000) {
            modelDir.deleteRecursively()
            return null
        }
        id
    } catch (e: Exception) {
        android.util.Log.e("InfPlat", "importSafFile failed", e)
        null
    }
}

actual class PlatformSafHandle(val pfd: android.os.ParcelFileDescriptor?)

actual fun openSafPath(path: String): PlatformSafHandle? {
    if (!path.startsWith("content://")) return null
    return try {
        val uri = Uri.parse(path)
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        PlatformSafHandle(pfd)
    } catch (e: Exception) {
        android.util.Log.e("InfPlat", "openSafPath failed", e)
        null
    }
}
actual fun getSafResolvedPath(handle: PlatformSafHandle): String = handle.pfd?.let { "/proc/self/fd/${it.fd}" } ?: ""
actual fun closeSafHandle(handle: PlatformSafHandle) {
    try {
        handle.pfd?.close()
    } catch (_: Exception) {}
}

actual fun resolveContentUriSize(uri: String): Long {
    return try {
        val parsedUri = android.net.Uri.parse(uri)
        var size = 0L
        // 1. Cursor query
        context.contentResolver.query(parsedUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sIdx >= 0) size = cursor.getLong(sIdx)
            }
        }
        // 2. ParcelFileDescriptor.statSize
        if (size <= 0L) {
            context.contentResolver.openFileDescriptor(parsedUri, "r")?.use { pfd ->
                val stat = pfd.statSize
                if (stat > 0L) size = stat
            }
        }
        // 3. AssetFileDescriptor.length
        if (size <= 0L) {
            context.contentResolver.openAssetFileDescriptor(parsedUri, "r")?.use { afd ->
                val len = afd.length
                if (len > 0L && len != android.content.res.AssetFileDescriptor.UNKNOWN_LENGTH) size = len
            }
        }
        size
    } catch (_: Exception) { 0L }
}

@androidx.compose.runtime.Composable
actual fun rememberSafFilePicker(
    extensions: List<String>,
    onResult: (uriOrPath: String?, displayName: String?, sizeBytes: Long) -> Unit,
): () -> Unit {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                var displayName = "model"
                var sizeBytes = 0L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (nIdx >= 0) displayName = cursor.getString(nIdx)
                        if (sIdx >= 0) sizeBytes = cursor.getLong(sIdx)
                    }
                }
                if (sizeBytes <= 0L) {
                    try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            val stat = pfd.statSize
                            if (stat > 0L) sizeBytes = stat
                        }
                    } catch (_: Exception) {}
                }
                onResult(uri.toString(), displayName, sizeBytes)
            } catch (e: Exception) {
                onResult(null, null, 0L)
            }
        } else {
            onResult(null, null, 0L)
        }
    }
    return { launcher.launch(arrayOf("application/octet-stream")) }
}

@androidx.compose.runtime.Composable
actual fun rememberSafDirectoryPicker(
    onResult: (uri: String?) -> Unit,
): () -> Unit {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                onResult(uri.toString())
            } catch (e: Exception) {
                onResult(null)
            }
        } else {
            onResult(null)
        }
    }
    return { launcher.launch(null) }
}
