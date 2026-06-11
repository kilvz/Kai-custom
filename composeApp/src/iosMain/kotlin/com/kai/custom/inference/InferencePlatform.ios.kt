package com.kai.custom.inference

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
private fun getDirectory(directory: platform.Foundation.NSSearchPathDirectory): String {
    val paths = NSFileManager.defaultManager.URLsForDirectory(directory, NSUserDomainMask)
    return paths.first().toString().removePrefix("file://")
}

actual fun getModelStorageDirectory(): String = getDirectory(NSDocumentDirectory) + "/litert_models"

actual fun getModelCacheDirectory(): String = getDirectory(NSCachesDirectory)

actual fun getAvailableMemoryBytes(): Long = Long.MAX_VALUE // iOS memory management is aggressive; assume it's available and let the OS kill us if needed

actual fun getTotalMemoryBytes(): Long = Long.MAX_VALUE

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
actual fun getAvailableDiskSpaceBytes(path: String): Long {
    val fileManager = NSFileManager.defaultManager
    val attributes = fileManager.attributesOfFileSystemForPath(path, null)
    return (attributes?.get(platform.Foundation.NSFileSystemFreeSize) as? NSNumber)?.unsignedLongLongValue?.toLong() ?: 0L
}

// iOS has no foreground-service equivalent. Download progress is surfaced in-app; if a
// user-visible notification is needed later, wire it through UNUserNotificationCenter.
actual fun startDownloadNotificationService() {}
actual fun stopDownloadNotificationService() {}
actual fun updateDownloadNotificationProgress(percent: Int) {}

actual fun importPlatformFile(platformFile: io.github.vinceglb.filekit.PlatformFile, isGguf: Boolean): String? {
    return null
}

actual fun handleImportedSafFile(uriOrPath: String, isGguf: Boolean): String? {
    return null
}

@androidx.compose.runtime.Composable
actual fun rememberSafFilePicker(
    extensions: List<String>,
    onResult: (uriOrPath: String?, displayName: String?, sizeBytes: Long) -> Unit
): () -> Unit {
    return {}
}

actual class PlatformSafHandle

actual fun openSafPath(path: String): PlatformSafHandle? = null

actual fun getSafResolvedPath(handle: PlatformSafHandle): String = ""

actual fun closeSafHandle(handle: PlatformSafHandle) {}
