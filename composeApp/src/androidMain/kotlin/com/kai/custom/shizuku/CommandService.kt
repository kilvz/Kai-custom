package com.kai.custom.shizuku

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

class CommandService : ICommandService {

    private val tag = "CommandService"

    @Keep
    constructor() : super()

    @Keep
    constructor(context: Context) : super()

    override fun executeCommand(command: String, timeoutMs: Long): String {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        try {
            var stdout = ""
            var stderr = ""
            val stdoutThread = Thread { stdout = readStream(process.inputStream) }
            val stderrThread = Thread { stderr = readStream(process.errorStream) }
            stdoutThread.start()
            stderrThread.start()

            val deadline = System.currentTimeMillis() + timeoutMs
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

            return if (timedOut) {
                process.destroy()
                Json.encodeToString(
                    CommandResultDto(
                        exitCode = -1,
                        stdout = stdout,
                        stderr = stderr,
                        timedOut = true,
                        error = "Command timed out after ${timeoutMs}ms",
                    )
                )
            } else {
                Json.encodeToString(
                    CommandResultDto(
                        exitCode = process.exitValue(),
                        stdout = stdout,
                        stderr = stderr,
                        timedOut = false,
                    )
                )
            }
        } catch (e: Throwable) {
            process.destroy()
            return Json.encodeToString(
                CommandResultDto(
                    exitCode = -1,
                    timedOut = false,
                    error = e.message ?: "Unknown error",
                )
            )
        }
    }

    override fun destroy() {
        Log.d(tag, "destroy() called — exiting process")
        System.exit(0)
    }

    private fun readStream(stream: java.io.InputStream): String {
        return BufferedReader(InputStreamReader(stream)).use { it.readText() }
    }
}
