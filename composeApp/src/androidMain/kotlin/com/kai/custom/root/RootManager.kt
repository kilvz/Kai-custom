package com.kai.custom.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object RootManager {
    private const val TAG = "RootManager"
    private const val MAX_OUTPUT_LENGTH = 15_000

    private var _isAvailable: Boolean? = null

    val isAvailable: Boolean
        get() {
            if (_isAvailable == null) {
                _isAvailable = try {
                    val process = ProcessBuilder("which", "su").start()
                    val exitCode = process.waitFor()
                    exitCode == 0
                } catch (_: Throwable) {
                    false
                }
            }
            return _isAvailable!!
        }

    suspend fun runCommand(
        command: String,
        timeoutSeconds: Long = 30,
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        Log.d(TAG, "runCommand: $command")
        if (!isAvailable) {
            return@withContext mapOf(
                "success" to false,
                "error" to "su is not available. Your device must be rooted to use this tool.",
            )
        }

        try {
            val process = ProcessBuilder("su", "-c", command).start()

            var stdout = ""
            var stderr = ""
            val stdoutThread = Thread { stdout = readStream(process.inputStream) }
            val stderrThread = Thread { stderr = readStream(process.errorStream) }
            stdoutThread.start()
            stderrThread.start()

            val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L
            var timedOut = true
            while (System.currentTimeMillis() < deadline) {
                try {
                    process.exitValue()
                    timedOut = false
                    break
                } catch (_: IllegalThreadStateException) {
                    Thread.sleep(50)
                }
            }

            stdoutThread.join(5000)
            stderrThread.join(5000)

            val exitCode = if (timedOut) {
                process.destroy()
                -1
            } else {
                process.exitValue()
            }

            mapOf(
                "success" to (exitCode == 0 && !timedOut),
                "exit_code" to exitCode,
                "stdout" to stdout.take(MAX_OUTPUT_LENGTH),
                "stderr" to stderr.take(MAX_OUTPUT_LENGTH),
                "timed_out" to timedOut,
                "error" to if (timedOut) "Command timed out after ${timeoutSeconds}s" else "",
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Command execution failed", e)
            mapOf(
                "success" to false,
                "error" to "Command execution failed: ${e.message}",
            )
        }
    }

    private fun readStream(stream: java.io.InputStream): String = BufferedReader(InputStreamReader(stream)).use { it.readText() }
}
