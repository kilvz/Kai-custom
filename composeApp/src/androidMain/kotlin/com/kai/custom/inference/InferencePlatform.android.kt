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
