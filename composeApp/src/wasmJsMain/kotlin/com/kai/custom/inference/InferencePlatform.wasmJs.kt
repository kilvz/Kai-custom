package com.kai.custom.inference

actual fun getModelStorageDirectory(): String = "/models" // Virtual filesystem in browser

actual fun getModelCacheDirectory(): String = "/tmp"

actual fun getAvailableMemoryBytes(): Long = Long.MAX_VALUE // Browser handles memory limits

actual fun getTotalMemoryBytes(): Long = Long.MAX_VALUE

actual fun getAvailableDiskSpaceBytes(path: String): Long = 0L

actual fun startDownloadNotificationService() {}
actual fun stopDownloadNotificationService() {}
actual fun updateDownloadNotificationProgress(percent: Int) {}

actual fun resolveContentUriSize(uri: String): Long = 0L

actual fun importPlatformFile(platformFile: io.github.vinceglb.filekit.PlatformFile, isGguf: Boolean): String? = null

actual fun resolveSafUriToLocal(uri: String, localPath: String): String? = null

actual fun linkGgufExternal(uri: String, displayName: String, sizeBytes: Long): String? = null

actual fun importSafFile(uriOrPath: String, isGguf: Boolean): String? = null

@androidx.compose.runtime.Composable
actual fun rememberSafFilePicker(
    extensions: List<String>,
    onResult: (uriOrPath: String?, displayName: String?, sizeBytes: Long) -> Unit,
): () -> Unit = {}

@androidx.compose.runtime.Composable
actual fun rememberSafDirectoryPicker(
    onResult: (uri: String?) -> Unit,
): () -> Unit = {}

actual class PlatformSafHandle

actual fun openSafPath(path: String): PlatformSafHandle? = null

actual fun getSafResolvedPath(handle: PlatformSafHandle): String = ""

actual fun closeSafHandle(handle: PlatformSafHandle) {}
