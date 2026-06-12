package com.kai.custom.shizuku

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuManager {
    private const val TAG = "ShizukuManager"
    private const val MAX_OUTPUT_LENGTH = 15_000

    private val permissionListeners = mutableListOf<(Boolean) -> Unit>()

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        synchronized(permissionListeners) {
            val granted = grantResult == 0
            permissionListeners.toList().forEach { it(granted) }
        }
    }

    init {
        try {
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to register permission listener", e)
        }
    }

    val isAvailable: Boolean
        get() = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }

    val hasPermission: Boolean
        get() = try {
            Shizuku.checkSelfPermission() == 0
        } catch (_: Throwable) {
            false
        }

    fun requestPermission(onResult: ((Boolean) -> Unit)? = null) {
        if (onResult != null) {
            synchronized(permissionListeners) {
                permissionListeners.add(onResult)
            }
        }
        try {
            Shizuku.requestPermission(10001)
        } catch (e: Throwable) {
            Log.e(TAG, "requestPermission failed", e)
        }
    }

    suspend fun runCommand(
        command: String,
        timeoutSeconds: Long = 30,
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        Log.d(TAG, "runCommand: $command")
        if (!isAvailable) {
            return@withContext mapOf(
                "success" to false,
                "error" to "Shizuku is not available. Install Shizuku from https://shizuku.rikka.app and start it via ADB.",
            )
        }
        if (!hasPermission) {
            return@withContext mapOf(
                "success" to false,
                "error" to "Shizuku permission not granted. The system will prompt for permission — accept it.",
            )
        }

        try {
            val process = newProcess(arrayOf("sh", "-c", command))

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
                } catch (_: IllegalArgumentException) {
                    Thread.sleep(50)
                }
            }

            stdoutThread.join(5000)
            stderrThread.join(5000)

            val result = if (timedOut) {
                process.destroy()
                CommandResultDto(
                    exitCode = -1,
                    stdout = stdout,
                    stderr = stderr,
                    timedOut = true,
                    error = "Command timed out after ${timeoutSeconds}s",
                )
            } else {
                CommandResultDto(
                    exitCode = process.exitValue(),
                    stdout = stdout,
                    stderr = stderr,
                    timedOut = false,
                )
            }

            val map = result.toMap().toMutableMap()
            map["stdout"] = (map["stdout"] as? String)?.take(MAX_OUTPUT_LENGTH) ?: ""
            map["stderr"] = (map["stderr"] as? String)?.take(MAX_OUTPUT_LENGTH) ?: ""
            map.toMap()
        } catch (e: Throwable) {
            Log.e(TAG, "Command execution failed", e)
            mapOf(
                "success" to false,
                "error" to "Command execution failed: ${e.message}",
            )
        }
    }

    private fun readStream(stream: java.io.InputStream): String = BufferedReader(InputStreamReader(stream)).use { it.readText() }

    private fun newProcess(cmd: Array<String>): Process {
        val clazz = Class.forName("rikka.shizuku.Shizuku")
        val method = clazz.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        method.isAccessible = true
        return method.invoke(null, cmd, null, null) as Process
    }
}
