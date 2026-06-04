package com.kai.custom.sandbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AltMemoryDockerManager(
    private val dockerManager: DockerManager = DockerManager(),
) {
    companion object {
        const val CONTAINER_NAME = "kai-alt-memory"
        const val IMAGE_NAME = "kilv/alt-memory"
        const val IMAGE_TAG = "full"
        const val PORT = 8316
        val IMAGE_REF: String get() = "$IMAGE_NAME:$IMAGE_TAG"
    }

    data class AltMemoryStatus(
        val imagePulled: Boolean = false,
        val containerRunning: Boolean = false,
        val healthy: Boolean = false,
        val error: String = "",
    )

    suspend fun pullImage(): Boolean = withContext(Dispatchers.IO) {
        dockerManager.pullImage(IMAGE_REF)
    }

    suspend fun startContainer(): Boolean = withContext(Dispatchers.IO) {
        try {
            removeContainerIfExists()
            val args = arrayOf(
                "docker", "run", "-d",
                "--name", CONTAINER_NAME,
                "--restart", "unless-stopped",
                "-p", "$PORT:$PORT",
                "-v", "alt-memory-data:/root/.alt-memory",
                IMAGE_REF,
                "alt-memory", "mcp", "--host", "0.0.0.0", "--port", "$PORT", "--transport", "sse",
            )
            val proc = Runtime.getRuntime().exec(args)
            val output = if (proc.waitFor(30, TimeUnit.SECONDS) && proc.exitValue() == 0) {
                proc.inputStream.bufferedReader().readText().trim()
            } else null
            output != null && output.length == 64
        } catch (_: Exception) { false }
    }

    suspend fun stopContainer(): Boolean = withContext(Dispatchers.IO) {
        try {
            val stop = Runtime.getRuntime().exec(arrayOf("docker", "stop", CONTAINER_NAME))
            stop.waitFor(15, TimeUnit.SECONDS)
            removeContainerIfExists()
            true
        } catch (_: Exception) { false }
    }

    suspend fun isContainerRunning(): Boolean = withContext(Dispatchers.IO) {
        try {
            val proc = Runtime.getRuntime().exec(
                arrayOf("docker", "inspect", "--format", "{{.State.Running}}", CONTAINER_NAME)
            )
            if (proc.waitFor(5, TimeUnit.SECONDS) && proc.exitValue() == 0) {
                proc.inputStream.bufferedReader().readText().trim() == "true"
            } else false
        } catch (_: Exception) { false }
    }

    suspend fun isImagePulled(): Boolean = withContext(Dispatchers.IO) {
        try {
            val proc = Runtime.getRuntime().exec(
                arrayOf("docker", "image", "inspect", IMAGE_REF)
            )
            proc.waitFor(5, TimeUnit.SECONDS) && proc.exitValue() == 0
        } catch (_: Exception) { false }
    }

    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val proc = Runtime.getRuntime().exec(
                arrayOf("curl.exe", "-s", "-o", "NUL", "-w", "%{http_code}", "http://127.0.0.1:$PORT/health")
            )
            if (proc.waitFor(5, TimeUnit.SECONDS) && proc.exitValue() == 0) {
                proc.inputStream.bufferedReader().readText().trim() == "200"
            } else false
        } catch (_: Exception) { false }
    }

    suspend fun restartContainer(): Boolean = withContext(Dispatchers.IO) {
        stopContainer() && startContainer()
    }

    suspend fun pullAndRestart(): Boolean = withContext(Dispatchers.IO) {
        stopContainer() && pullImage() && startContainer()
    }

    suspend fun getStatus(): AltMemoryStatus = withContext(Dispatchers.IO) {
        try {
            val imagePulled = isImagePulled()
            val containerRunning = if (imagePulled) isContainerRunning() else false
            val healthy = if (containerRunning) checkHealth() else false
            AltMemoryStatus(
                imagePulled = imagePulled,
                containerRunning = containerRunning,
                healthy = healthy,
            )
        } catch (e: Exception) {
            AltMemoryStatus(error = e.message ?: "Unknown error")
        }
    }

    private suspend fun removeContainerIfExists() {
        try {
            val rm = Runtime.getRuntime().exec(arrayOf("docker", "rm", "-f", CONTAINER_NAME))
            rm.waitFor(10, TimeUnit.SECONDS)
        } catch (_: Exception) {}
    }
}
