package com.kai.custom.sandbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

class NativeAltMemoryManager {

    companion object {
        private const val PORT = 8316
        private const val HEALTH_URL = "http://127.0.0.1:$PORT/health"
        private const val TIMEOUT_MS = 5000
    }

    private var process: Process? = null

    suspend fun install(): Boolean = withContext(Dispatchers.IO) {
        try {
            val python = findPython()
            val pb = ProcessBuilder(
                python,
                "-m",
                "pip",
                "install",
                "alt-memory",
                "--break-system-packages",
            ).redirectErrorStream(true)
            val proc = pb.start()
            val exited = proc.waitFor(120, TimeUnit.SECONDS)
            if (!exited) {
                proc.destroyForcibly()
                return@withContext false
            }
            proc.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext true
        try {
            val python = findPython()
            val pb = ProcessBuilder(
                python,
                "-m",
                "alt_memory",
                "mcp",
                "--transport",
                "sse",
                "--port",
                PORT.toString(),
            ).redirectErrorStream(true)
            val proc = pb.start()
            process = proc
            proc.isAlive
        } catch (_: Exception) {
            false
        }
    }

    fun stop() {
        process?.destroyForcibly()
        process = null
    }

    fun isAvailable(): Boolean {
        return try {
            val python = findPython()
            val pb = ProcessBuilder(python, "-c", "import alt_memory")
                .redirectErrorStream(true)
            val proc = pb.start()
            val exited = proc.waitFor(5, TimeUnit.SECONDS)
            if (!exited) {
                proc.destroyForcibly()
                return false
            }
            proc.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    val isRunning: Boolean get() = process?.isAlive == true

    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URI(HEALTH_URL).toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            val code = conn.responseCode
            conn.disconnect()
            code == 200
        } catch (_: Exception) {
            false
        }
    }

    private fun findPython(): String {
        val os = System.getProperty("os.name", "").lowercase()
        return if (os.contains("win")) {
            findWhere("python") ?: "python"
        } else {
            findWhich("python3") ?: findWhich("python") ?: "python3"
        }
    }

    private fun findWhich(name: String): String? = try {
        val pb = ProcessBuilder("which", name).redirectErrorStream(true)
        val proc = pb.start()
        val line = BufferedReader(InputStreamReader(proc.inputStream)).readLine()
        proc.waitFor(3, TimeUnit.SECONDS)
        if (proc.exitValue() == 0 && !line.isNullOrBlank()) line.trim() else null
    } catch (_: Exception) {
        null
    }

    private fun findWhere(name: String): String? = try {
        val pb = ProcessBuilder("where", "$name.exe").redirectErrorStream(true)
        val proc = pb.start()
        val line = BufferedReader(InputStreamReader(proc.inputStream)).readLine()
        proc.waitFor(3, TimeUnit.SECONDS)
        if (proc.exitValue() == 0 && !line.isNullOrBlank()) line.trim() else null
    } catch (_: Exception) {
        null
    }
}
