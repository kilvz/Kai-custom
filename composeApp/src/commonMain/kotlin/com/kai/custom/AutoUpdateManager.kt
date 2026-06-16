package com.kai.custom

import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val assets: List<GitHubAsset> = emptyList(),
    val html_url: String = "",
)

@Serializable
data class GitHubAsset(
    val name: String,
    val browser_download_url: String = "",
    val size: Long = 0L,
)

data class UpdateCheckResult(
    val updateAvailable: Boolean,
    val latestVersion: String,
    val downloadUrl: String?,
    val releaseUrl: String?,
    val error: String?,
)

data class DownloadResult(
    val success: Boolean,
    val filePath: String?,
    val error: String?,
)

fun compareVersions(current: String, latest: String): Boolean {
    val currentParts = current.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val latestParts = latest.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
        val c = currentParts.getOrElse(i) { 0 }
        val l = latestParts.getOrElse(i) { 0 }
        if (l > c) return true
        if (l < c) return false
    }
    return false
}

expect class AutoUpdateManager() {
    suspend fun checkForUpdate(): UpdateCheckResult
    suspend fun downloadApk(url: String, fileName: String, onProgress: (Float) -> Unit): DownloadResult
    fun getApkFileName(): String
    fun installApk(filePath: String)
}
