package com.kai.custom.sandbox

import com.kai.custom.smartTruncate
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val MAX_OUTPUT_LENGTH = 15_000
private const val DEFAULT_TIMEOUT_SECONDS = 30L
private const val MAX_TIMEOUT_SECONDS = 600L

class ProotHandle internal constructor(
    private val process: Process,
    private val cancelled: AtomicBoolean,
    private val readerFutures: List<CompletableFuture<Void>>,
) {
    fun isCancelled(): Boolean = cancelled.get()

    fun cancel() {
        cancelled.set(true)
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        runCatching { process.outputStream.close() }
        process.destroyForcibly()
    }

    fun writeInput(line: String) {
        if (cancelled.get()) return
        runCatching {
            val bytes = (line + "\n").toByteArray()
            process.outputStream.write(bytes)
            process.outputStream.flush()
        }
    }

    fun awaitExit(): Int {
        // Poll so a cancel() from another thread can short-circuit the wait.
        // On Linux, close(fd) does NOT unblock a thread already inside read(fd),
        // so reader futures can sit waiting on a tracee pipe even after SIGKILL.
        while (!cancelled.get() && process.isAlive) {
            runCatching { process.waitFor(200, TimeUnit.MILLISECONDS) }
        }
        if (cancelled.get()) return -1
        readerFutures.forEach { runCatching { it.get(500, TimeUnit.MILLISECONDS) } }
        return runCatching { process.exitValue() }.getOrDefault(-1)
    }
}

class ProotExecutor(
    private val prootPath: String,
    private val libDir: String,
    private val rootfsPath: String,
    private val homePath: String,
    private val tmpPath: String,
) {
    /** When false, the /sdcard bind mount is omitted from proot args. */
    var sandboxStorageMountEnabled: Boolean = true

    /**
     * When true and su is available, the proot command is wrapped in `su -c` for real root.
     * When false, proot runs as the app user (no su wrapper).
     */
    var sandboxRootEnabled: Boolean = false

    fun execute(
        command: String,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
        workingDir: String = "/root",
        extraEnv: Map<String, String> = emptyMap(),
    ): Map<String, Any> {
        val effectiveTimeout = timeoutSeconds.coerceIn(1, MAX_TIMEOUT_SECONDS)

        return try {
            val envVars = buildEnvVars(extraEnv)
            val process = Runtime.getRuntime().exec(
                buildCommandArray(command, workingDir, envVars),
                envVars,
                File(rootfsPath).parentFile,
            )

            // Drain stdout/stderr concurrently to avoid pipe buffer deadlock
            val stdoutFuture = CompletableFuture.supplyAsync {
                readBounded(process.inputStream.bufferedReader())
            }
            val stderrFuture = CompletableFuture.supplyAsync {
                readBounded(process.errorStream.bufferedReader())
            }

            val completed = process.waitFor(effectiveTimeout, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                return mapOf(
                    "success" to false,
                    "stdout" to stdoutFuture.get(1, TimeUnit.SECONDS).smartTruncate(MAX_OUTPUT_LENGTH),
                    "stderr" to stderrFuture.get(1, TimeUnit.SECONDS).smartTruncate(MAX_OUTPUT_LENGTH),
                    "exit_code" to -1,
                    "timed_out" to true,
                )
            }

            mapOf(
                "success" to (process.exitValue() == 0),
                "stdout" to stdoutFuture.get().smartTruncate(MAX_OUTPUT_LENGTH),
                "stderr" to stderrFuture.get().smartTruncate(MAX_OUTPUT_LENGTH),
                "exit_code" to process.exitValue(),
                "timed_out" to false,
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "error" to (e.message ?: "Failed to execute command in sandbox"),
            )
        }
    }

    fun executeStreaming(
        command: String,
        workingDir: String = "/root",
        extraEnv: Map<String, String> = emptyMap(),
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
    ): ProotHandle {
        val envVars = buildEnvVars(extraEnv)
        val process = Runtime.getRuntime().exec(
            buildCommandArray(command, workingDir, envVars),
            envVars,
            File(rootfsPath).parentFile,
        )
        val cancelled = AtomicBoolean(false)
        val stdoutFuture = CompletableFuture.runAsync {
            streamLines(process.inputStream.bufferedReader(), cancelled, onStdout)
        }
        val stderrFuture = CompletableFuture.runAsync {
            streamLines(process.errorStream.bufferedReader(), cancelled, onStderr)
        }
        return ProotHandle(process, cancelled, listOf(stdoutFuture, stderrFuture))
    }

    private fun storageBind(): String? {
        if (!sandboxStorageMountEnabled) return null
        return when {
            File("/storage/emulated/0").exists() -> "--bind=/storage/emulated/0:/sdcard"
            File("/storage/self/primary").exists() -> "--bind=/storage/self/primary:/sdcard"
            else -> null
        }
    }

    private fun buildCommandArray(
        command: String,
        workingDir: String,
        envVars: Array<String> = emptyArray(),
    ): Array<String> {
        // Prolog up to but not including the command — reused in both paths.
        val prologParts = listOf(
            prootPath,
            "--rootfs=$rootfsPath",
            "--bind=/dev",
            "--bind=/proc",
            "--bind=/sys",
            "--bind=$homePath:/root",
            "--bind=$tmpPath:/tmp",
        ) + listOfNotNull(storageBind()) + listOfNotNull(
            "--sysvipc",
        ) + listOf(
            "-0",
            "-w",
            workingDir,
            "/bin/sh",
            "-c",
        )

        return if (sandboxRootEnabled) {
            // `su` strips environment — prefix env vars using POSIX shell
            // syntax so LD_LIBRARY_PATH, PROOT_LOADER etc reach proot.
            // The command must be shell-quoted so multi-word commands
            // survive the outer sh -c as a single argument.
            val envPrefix = envVars.joinToString(" ") { "$it" }
            val prolog = prologParts.joinToString(" ")
            val quotedCmd = "'${command.replace("'", "'\\''")}'"
            arrayOf("su", "-c", "$envPrefix $prolog $quotedCmd")
        } else {
            (prologParts + command).toTypedArray()
        }
    }

    private fun buildEnvVars(extraEnv: Map<String, String>): Array<String> {
        val loaderPath = File(prootPath).parent.orEmpty() + "/libproot-loader.so"
        val baseEnv = arrayOf(
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "LD_LIBRARY_PATH=$libDir",
            "PROOT_TMP_DIR=$tmpPath",
            "PROOT_LOADER=$loaderPath",
            "DEBIAN_FRONTEND=noninteractive",
        )
        return baseEnv + extraEnv.map { (k, v) -> "$k=$v" }.toTypedArray()
    }

    private fun readBounded(reader: BufferedReader): String {
        val sb = StringBuilder()
        val buf = CharArray(8192)
        try {
            var read: Int
            while (reader.read(buf).also { read = it } != -1) {
                sb.append(buf, 0, read)
                if (sb.length >= MAX_OUTPUT_LENGTH) break
            }
            if (sb.length >= MAX_OUTPUT_LENGTH) {
                while (reader.read(buf) != -1) { /* discard */ }
            }
        } catch (_: IOException) {
            // Stream closed under us (typically destroyForcibly on timeout).
            // Return what we have so the timed_out path can surface a clean result.
        }
        return sb.toString()
    }

    private fun streamLines(
        reader: BufferedReader,
        cancelled: AtomicBoolean,
        onLine: (String) -> Unit,
    ) {
        try {
            while (!cancelled.get()) {
                val line = try {
                    reader.readLine()
                } catch (e: IOException) {
                    if (cancelled.get()) break
                    throw e
                } ?: break
                onLine(line)
            }
        } finally {
            runCatching { reader.close() }
        }
    }
}
