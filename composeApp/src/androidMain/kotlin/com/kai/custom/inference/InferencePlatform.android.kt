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

private val context: Context by inject(Context::class.java)

actual fun getModelStorageDirectory(): String = context.filesDir.absolutePath + "/litert_models"

actual fun getModelCacheDirectory(): String = context.cacheDir.absolutePath

private fun getMemoryInfo(): ActivityManager.MemoryInfo {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
}

actual fun getAvailableMemoryBytes(): Long = getMemoryInfo().availMem

actual fun getTotalMemoryBytes(): Long = getMemoryInfo().totalMem

actual fun getAvailableDiskSpaceBytes(path: String): Long {
    java.io.File(path).mkdirs()
    return StatFs(path).availableBytes
}

actual fun startDownloadNotificationService() {
    try {
        val intent = Intent(context, ModelDownloadService::class.java)
        ContextCompat.startForegroundService(context, intent)
    } catch (_: Exception) { }
}

actual fun stopDownloadNotificationService() {
    try {
        context.stopService(Intent(context, ModelDownloadService::class.java))
    } catch (_: Exception) { }
}

actual fun updateDownloadNotificationProgress(percent: Int) {
    try {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = android.app.Notification.Builder(context, "kai_model_download_channel")
        val notification = builder
            .setContentTitle(context.getString(com.kai.custom.shared.R.string.app_name))
            .setContentText("$percent%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, percent, false)
            .build()
        manager.notify(ModelDownloadService.NOTIFICATION_ID, notification)
    } catch (_: Exception) { }
}

@OptIn(ExperimentalUuidApi::class)
actual fun importPlatformFile(platformFile: PlatformFile, isGguf: Boolean): String? {
    return try {
        // In filekit-core 0.14.1, PlatformFile for Android exposes `uri`.
        // We attempt to access it via property or fallback to reflection if obscured.
        val uri: Uri = try {
            var extracted: Uri? = null

            // First try direct property access if it was added
            val getUriMethod = platformFile.javaClass.methods.firstOrNull { it.name.endsWith("Uri") && it.returnType == Uri::class.java }
            if (getUriMethod != null) {
                getUriMethod.isAccessible = true
                extracted = getUriMethod.invoke(platformFile) as? Uri
            }

            // FileKit 0.14.1 has `getAndroidFile()` and `androidFile` which holds `UriWrapper(uri)`
            if (extracted == null) {
                val getAndroidFileMethod = platformFile.javaClass.methods.firstOrNull { it.name == "getAndroidFile" }
                val innerObj = if (getAndroidFileMethod != null) {
                    getAndroidFileMethod.isAccessible = true
                    getAndroidFileMethod.invoke(platformFile)
                } else {
                    val wrappedField = platformFile.javaClass.declaredFields.firstOrNull { 
                        it.name == "androidFile" || it.name == "platformFile" || it.name == "file" || it.name == "wrapper" 
                    }
                    wrappedField?.isAccessible = true
                    wrappedField?.get(platformFile)
                }

                if (innerObj != null) {
                    val innerGetUri = innerObj.javaClass.methods.firstOrNull { it.name == "getUri" && it.returnType == Uri::class.java }
                    if (innerGetUri != null) {
                        innerGetUri.isAccessible = true
                        extracted = innerGetUri.invoke(innerObj) as? Uri
                    } else {
                        val innerUriField = innerObj.javaClass.declaredFields.firstOrNull { it.type == Uri::class.java || it.name == "uri" }
                        if (innerUriField != null) {
                            innerUriField.isAccessible = true
                            extracted = innerUriField.get(innerObj) as? Uri
                        }
                    }
                }
            }

            // Fallback for direct field
            if (extracted == null) {
                val uriField = platformFile.javaClass.declaredFields.firstOrNull { it.name == "uri" || it.type == Uri::class.java }
                if (uriField != null) {
                    uriField.isAccessible = true
                    extracted = uriField.get(platformFile) as? Uri
                }
            }

            extracted ?: throw Exception("Could not find Uri")
        } catch (e: Exception) {
            android.util.Log.e("InferencePlatform", "Failed to extract Uri from PlatformFile: ${platformFile.javaClass.name}", e)
            return null
        }

        val id = "imported_${kotlin.uuid.Uuid.random().toString().take(8)}"
        val modelsDir = if (isGguf) {
            com.kai.custom.inference.GgufInferenceEngine.getGgufModelsDir()
        } else {
            File(getModelStorageDirectory())
        }
        modelsDir.mkdirs()
        val modelDir = File(modelsDir, id)
        modelDir.mkdirs()

        val name = try { platformFile.name } catch (_: Exception) { "model" }
        val extension = if (isGguf) "gguf" else "litertlm"
        val targetFile = File(modelDir, "model.$extension")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        } ?: return null

        id
    } catch (e: Exception) {
        null
    }
}

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
actual fun handleImportedSafFile(uriOrPath: String, isGguf: Boolean): String? {
    try {
        var actualIsGguf = isGguf
        if (uriOrPath.startsWith("content://")) {
            val uri = android.net.Uri.parse(uriOrPath)
            context.contentResolver.openInputStream(uri)?.use { input ->
                val magic = ByteArray(4)
                if (input.read(magic) == 4) {
                    val magicStr = String(magic, kotlin.text.Charsets.US_ASCII)
                    if (magicStr == "GGUF") {
                        actualIsGguf = true
                    }
                }
            }
        }

        val baseId = "imported_${kotlin.uuid.Uuid.random().toString().take(8)}"
        val id = if (actualIsGguf) "gguf_$baseId" else baseId
        val modelsDir = if (actualIsGguf) {
            com.kai.custom.inference.GgufInferenceEngine.getGgufModelsDir()
        } else {
            File(getModelStorageDirectory())
        }
        modelsDir.mkdirs()
        val modelDir = File(modelsDir, id)
        modelDir.mkdirs()
        
        val extension = if (actualIsGguf) "gguf" else "litertlm"
        val safFile = File(modelDir, "model.$extension.saf")
        safFile.writeText(uriOrPath)
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
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                // Try to persist permission
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                
                // Get display name and size
                var displayName = "model"
                var sizeBytes = 0L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
                        if (sizeIndex >= 0) sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
                
                onResult(uri.toString(), displayName, sizeBytes)
            } catch (e: Exception) {
                android.util.Log.e("InferencePlatform", "SAF error", e)
                onResult(null, null, 0L)
            }
        } else {
            onResult(null, null, 0L)
        }
    }
    return { launcher.launch(arrayOf("*/*")) }
}

actual class PlatformSafHandle(val pfd: android.os.ParcelFileDescriptor?)

actual fun openSafPath(path: String): PlatformSafHandle? {
    if (!path.startsWith("content://")) return null
    return try {
        val uri = Uri.parse(path)
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        PlatformSafHandle(pfd)
    } catch(e: Exception) { 
        android.util.Log.e("InferencePlatform", "Failed to open SAF path", e)
        null 
    }
}

actual fun getSafResolvedPath(handle: PlatformSafHandle): String {
    return handle.pfd?.let { "/proc/self/fd/${it.fd}" } ?: ""
}

actual fun closeSafHandle(handle: PlatformSafHandle) {
    try {
        handle.pfd?.close()
    } catch(e: Exception) {}
}
