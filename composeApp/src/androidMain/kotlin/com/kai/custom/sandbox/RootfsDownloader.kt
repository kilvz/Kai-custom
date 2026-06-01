package com.kai.custom.sandbox

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

private const val ALPINE_VERSION = "3.21.3"
private const val ALPINE_BRANCH = "v3.21"
private const val BUFFER_SIZE = 8192

private val ALPINE_MIRRORS = listOf(
    "https://dl-cdn.alpinelinux.org/alpine",
)

private const val UBUNTU_VERSION = "24.04"
private const val UBUNTU_CODENAME = "noble"

private val UBUNTU_MIRRORS = listOf(
    "https://cdimage.ubuntu.com/ubuntu-base/releases",
)

private val UBUNTU_APT_MIRRORS = listOf(
    "http://archive.ubuntu.com/ubuntu/",
    "http://ports.ubuntu.com/ubuntu-ports/",
)
private const val TAR_BLOCK_SIZE = 512
private const val TAR_NAME_OFFSET = 0
private const val TAR_MODE_OFFSET = 100
private const val TAR_SIZE_OFFSET = 124
private const val TAR_TYPE_OFFSET = 156
private const val TAR_LINK_OFFSET = 157
private const val TAR_PREFIX_OFFSET = 345

class RootfsDownloader(private val httpClient: HttpClient) {

    fun getMirrors(distro: String): List<String> = when (distro) {
        "ubuntu" -> UBUNTU_APT_MIRRORS
        else -> ALPINE_MIRRORS
    }

    fun getDownloadUrls(arch: String, distro: String = "alpine"): List<String> = when (distro) {
        "ubuntu" -> UBUNTU_MIRRORS.map { "$it/$UBUNTU_VERSION/release/" }

        else -> ALPINE_MIRRORS.map { base ->
            "$base/$ALPINE_BRANCH/releases/$arch/alpine-minirootfs-$ALPINE_VERSION-$arch.tar.gz"
        }
    }

    private fun toUbuntuArch(arch: String): String = when (arch) {
        "aarch64" -> "arm64"
        "armhf" -> "armhf"
        "x86_64" -> "amd64"
        "x86" -> "i386"
        else -> "arm64"
    }

    suspend fun download(
        arch: String,
        targetFile: File,
        distro: String = "alpine",
        onProgress: (Float) -> Unit,
    ) {
        val urls = if (distro == "ubuntu") {
            resolveUbuntuDownloadUrls(arch)
        } else {
            getDownloadUrls(arch, "alpine")
        }
        var lastError: Exception? = null
        for ((index, url) in urls.withIndex()) {
            try {
                downloadFrom(url, targetFile, onProgress)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (targetFile.exists()) targetFile.delete()
                if (index < urls.lastIndex) onProgress(0f)
            }
        }
        throw IOException("All mirrors failed", lastError)
    }

    private suspend fun resolveUbuntuDownloadUrls(arch: String): List<String> {
        val ubuntuArch = toUbuntuArch(arch)
        val dirUrl = "https://cdimage.ubuntu.com/ubuntu-base/releases/$UBUNTU_VERSION/release/"
        val filename = fetchLatestUbuntuRootfs(dirUrl, ubuntuArch)
        return UBUNTU_MIRRORS.map { "$it/$UBUNTU_VERSION/release/$filename" }
    }

    private suspend fun fetchLatestUbuntuRootfs(dirUrl: String, arch: String): String {
        val html = httpClient.prepareGet(dirUrl).execute { response ->
            val channel = response.bodyAsChannel()
            val baos = java.io.ByteArrayOutputStream()
            val buf = ByteArray(BUFFER_SIZE)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buf)
                if (read <= 0) break
                baos.write(buf, 0, read)
            }
            baos.toString("UTF-8")
        }
        val regex = """ubuntu-base-\d+\.\d+\.\d+-base-$arch\.tar\.gz""".toRegex()
        return regex.findAll(html).map { it.value }.lastOrNull()
            ?: throw IOException("No Ubuntu base rootfs found for $arch at $dirUrl")
    }

    private suspend fun downloadFrom(
        url: String,
        targetFile: File,
        onProgress: (Float) -> Unit,
    ) {
        httpClient.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) {
                throw IOException("HTTP ${response.status.value} from $url")
            }
            val totalBytes = response.contentLength() ?: -1L
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(BUFFER_SIZE)
            var downloadedBytes = 0L

            FileOutputStream(targetFile).use { output ->
                while (!channel.isClosedForRead) {
                    val bytesRead = channel.readAvailable(buffer)
                    if (bytesRead <= 0) break
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        onProgress(downloadedBytes.toFloat() / totalBytes)
                    }
                }
            }
        }
    }

    fun extractTarGz(tarGzFile: File, targetDir: File) {
        targetDir.mkdirs()
        GZIPInputStream(BufferedInputStream(FileInputStream(tarGzFile))).use { gzipStream ->
            extractTar(gzipStream, targetDir)
        }
    }

    private fun extractTar(inputStream: java.io.InputStream, targetDir: File) {
        val headerBuffer = ByteArray(TAR_BLOCK_SIZE)
        val dataBuffer = ByteArray(BUFFER_SIZE)

        while (true) {
            val headerBytesRead = readFully(inputStream, headerBuffer)
            if (headerBytesRead < TAR_BLOCK_SIZE) break

            val name = readTarString(headerBuffer, TAR_NAME_OFFSET, 100)
            if (name.isEmpty()) break

            val prefix = readTarString(headerBuffer, TAR_PREFIX_OFFSET, 155)
            val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name

            val sizeStr = readTarString(headerBuffer, TAR_SIZE_OFFSET, 12)
            val size = if (sizeStr.isNotEmpty()) sizeStr.toLong(8) else 0L

            val modeStr = readTarString(headerBuffer, TAR_MODE_OFFSET, 8)
            val mode = if (modeStr.isNotEmpty()) modeStr.toInt(8) else 0
            val typeFlag = headerBuffer[TAR_TYPE_OFFSET]
            val linkName = readTarString(headerBuffer, TAR_LINK_OFFSET, 100)

            val outFile = File(targetDir, fullName)

            if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                skipBytes(inputStream, alignToBlock(size))
                continue
            }

            when (typeFlag.toInt().toChar()) {
                '5', 'D' -> outFile.mkdirs()

                '2' -> {
                    outFile.parentFile?.mkdirs()
                    try {
                        if (outFile.exists()) outFile.delete()
                        java.nio.file.Files.createSymbolicLink(
                            outFile.toPath(),
                            java.nio.file.Paths.get(linkName),
                        )
                    } catch (_: Exception) {
                    }
                }

                '1' -> {
                    val linkTarget = File(targetDir, linkName)
                    outFile.parentFile?.mkdirs()
                    if (linkTarget.exists()) {
                        linkTarget.copyTo(outFile, overwrite = true)
                    }
                }

                '0', '\u0000' -> {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output ->
                        var remaining = size
                        while (remaining > 0) {
                            val toRead = minOf(remaining, dataBuffer.size.toLong()).toInt()
                            val bytesRead = inputStream.read(dataBuffer, 0, toRead)
                            if (bytesRead <= 0) break
                            output.write(dataBuffer, 0, bytesRead)
                            remaining -= bytesRead
                        }
                    }
                    if (mode and 0b001_001_001 != 0) {
                        outFile.setExecutable(true, false)
                    }
                    val padding = alignToBlock(size) - size
                    if (padding > 0) skipBytes(inputStream, padding)
                    continue
                }

                else -> {}
            }

            if (size > 0 && typeFlag.toInt().toChar() != '0' && typeFlag.toInt().toChar() != '\u0000') {
                skipBytes(inputStream, alignToBlock(size))
            }
        }
    }

    private fun readTarString(buffer: ByteArray, offset: Int, length: Int): String {
        val end = minOf(offset + length, buffer.size)
        val nullIndex = (offset until end).firstOrNull { buffer[it] == 0.toByte() } ?: end
        return String(buffer, offset, nullIndex - offset, Charsets.US_ASCII).trim()
    }

    private fun readFully(inputStream: java.io.InputStream, buffer: ByteArray): Int {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val bytesRead = inputStream.read(buffer, totalRead, buffer.size - totalRead)
            if (bytesRead <= 0) break
            totalRead += bytesRead
        }
        return totalRead
    }

    private fun skipBytes(inputStream: java.io.InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = inputStream.skip(remaining)
            if (skipped <= 0) {
                if (inputStream.read() < 0) break
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }

    private fun alignToBlock(size: Long): Long {
        val remainder = size % TAR_BLOCK_SIZE
        return if (remainder == 0L) size else size + (TAR_BLOCK_SIZE - remainder)
    }

    fun makeWritable(rootfsDir: File) {
        rootfsDir.walkTopDown().forEach { file ->
            if (file.isDirectory && !file.canWrite()) {
                file.setWritable(true, true)
            }
        }
    }

    fun writeResolvConf(rootfsDir: File) {
        val etcDir = File(rootfsDir, "etc")
        etcDir.mkdirs()
        File(etcDir, "resolv.conf").writeText(
            "nameserver 8.8.8.8\nnameserver 8.8.4.4\n",
        )
    }

    fun writeRepositories(rootfsDir: File, mirrorBase: String, distro: String = "alpine") {
        when (distro) {
            "ubuntu" -> {
                val sourcesDir = File(rootfsDir, "etc/apt")
                sourcesDir.mkdirs()
                File(sourcesDir, "sources.list").writeText(
                    "deb $mirrorBase $UBUNTU_CODENAME main restricted universe multiverse\n" +
                        "deb $mirrorBase ${UBUNTU_CODENAME}-updates main restricted universe multiverse\n" +
                        "deb $mirrorBase ${UBUNTU_CODENAME}-security main restricted universe multiverse\n",
                )
                // Remove default ubuntu.sources to avoid duplicate multiverse entries
                val defaultSources = File(sourcesDir, "sources.list.d/ubuntu.sources")
                if (defaultSources.exists()) defaultSources.delete()
                val defaultSourcesList = File(sourcesDir, "sources.list.d/ubuntu.list")
                if (defaultSourcesList.exists()) defaultSourcesList.delete()
            }

            else -> {
                val apkDir = File(rootfsDir, "etc/apk")
                apkDir.mkdirs()
                File(apkDir, "repositories").writeText(
                    "$mirrorBase/$ALPINE_BRANCH/main\n$mirrorBase/$ALPINE_BRANCH/community\n",
                )
            }
        }
    }
}
