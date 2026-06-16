package com.kai.custom

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import com.kai.custom.httpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

actual class AutoUpdateManager actual constructor() {
    private val context: Context by inject(Context::class.java)

    actual suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val client = httpClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 30.seconds.inWholeMilliseconds
                }
            }
            val response = client.get("https://api.github.com/repos/kilvz/Kai-custom/releases/latest")
            if (!response.status.isSuccess()) {
                return@withContext UpdateCheckResult(
                    updateAvailable = false, latestVersion = "", downloadUrl = null, releaseUrl = null,
                    error = "GitHub API returned ${response.status.value}",
                )
            }
            val release = response.body<GitHubRelease>()
            val latestVersion = release.tag_name
            val currentVersion = Version.appVersion
            val downloadUrl = release.assets.firstOrNull { asset ->
                asset.name == getApkFileName()
            }?.browser_download_url
            UpdateCheckResult(
                updateAvailable = compareVersions(currentVersion, latestVersion),
                latestVersion = latestVersion,
                downloadUrl = downloadUrl,
                releaseUrl = release.html_url,
                error = null,
            )
        } catch (e: Exception) {
            UpdateCheckResult(
                updateAvailable = false, latestVersion = "", downloadUrl = null, releaseUrl = null,
                error = e.message ?: "Unknown error",
            )
        }
    }

    actual suspend fun downloadApk(
        url: String,
        fileName: String,
        onProgress: (Float) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val downloadsDir = File(context.getExternalFilesDir(null), "downloads")
            downloadsDir.mkdirs()
            val file = File(downloadsDir, fileName)
            if (file.exists()) file.delete()

            val client = httpClient {
                install(HttpTimeout) {
                    requestTimeoutMillis = 10.minutes.inWholeMilliseconds
                    socketTimeoutMillis = 10.minutes.inWholeMilliseconds
                }
            }
            val response = client.get(url)
            val totalBytes = response.headers[HttpHeaders.ContentLength]?.toLong() ?: 0L
            val channel = response.bodyAsChannel()
            var downloadedBytes = 0L
            val buffer = ByteArray(8192)

            file.outputStream().use { output ->
                while (!channel.isClosedForRead) {
                    val bytesRead = channel.readAvailable(buffer)
                    if (bytesRead < 0) break
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        withContext(Dispatchers.Main) {
                            onProgress((downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            DownloadResult(success = true, filePath = file.absolutePath, error = null)
        } catch (e: Exception) {
            DownloadResult(success = false, filePath = null, error = e.message ?: "Download failed")
        }
    }

    actual fun getApkFileName(): String {
        val abi = getDeviceAbiSuffix()
        val version = Version.appVersion
        return "k.ai-$version-android-$abi.apk"
    }

    actual fun installApk(filePath: String) {
        try {
            val file = File(filePath)
            val apkUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            println("AutoUpdate: install failed: ${e.message}")
        }
    }

    companion object {
        fun getDeviceAbiSuffix(): String {
            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
            return when {
                abi.startsWith("arm64") -> "arm64-v8a"
                abi.startsWith("armeabi") -> "armeabi-v7a"
                abi.startsWith("x86_64") -> "x86_64"
                abi.startsWith("x86") -> "x86"
                else -> "arm64-v8a"
            }
        }
    }
}
