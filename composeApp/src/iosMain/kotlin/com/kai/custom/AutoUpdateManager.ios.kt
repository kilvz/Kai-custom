package com.kai.custom

actual class AutoUpdateManager actual constructor() {
    actual suspend fun checkForUpdate(): UpdateCheckResult = UpdateCheckResult(
        updateAvailable = false, latestVersion = "", downloadUrl = null, releaseUrl = null,
        error = "Auto-update not supported on this platform",
    )

    actual suspend fun downloadApk(
        url: String, fileName: String, onProgress: (Float) -> Unit,
    ): DownloadResult = DownloadResult(
        success = false, filePath = null, error = "Auto-update not supported on this platform",
    )

    actual fun getApkFileName(): String = ""
    actual fun installApk(filePath: String) {}
}
