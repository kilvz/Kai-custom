package com.kai.custom.sandbox

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.kai.custom.CommandHandle
import com.kai.custom.TerminalLine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream

class DockerContainerSession(
    private val containerName: String,
    private val sessionId: String,
    private val transcript: SnapshotStateList<TerminalLine> = SnapshotStateList(),
) : CommandHandle {

    private var process: Process? = null
    private var stdin: OutputStream? = null
    private var cancelled = false
    private val exitDeferred = CompletableDeferred<Int>()

    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        try {
            val pb = ProcessBuilder(
                "docker",
                "exec",
                "-i",
                containerName,
                "sh",
                "-c",
                "PS1='[KAI_SHELL] ' sh",
            ).redirectErrorStream(true)
            val p = pb.start()
            process = p
            stdin = p.outputStream

            val reader = BufferedReader(InputStreamReader(p.inputStream))
            Thread {
                try {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (cancelled) break
                        val l = line!!
                        kotlinx.coroutines.runBlocking {
                            withContext(Dispatchers.Main) {
                                if (transcript.isEmpty() || transcript.last().text != l) {
                                    transcript.add(TerminalLine.Output(l))
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }.apply {
                isDaemon = true
                name = "docker-session-$sessionId"
                start()
            }

            Thread {
                val exit = p.waitFor()
                exitDeferred.complete(exit)
            }.apply {
                isDaemon = true
                name = "docker-exit-$sessionId"
                start()
            }

            true
        } catch (_: Exception) {
            false
        }
    }

    override fun cancel() {
        cancelled = true
        process?.destroyForcibly()
    }

    override fun isCancelled(): Boolean = cancelled

    override suspend fun writeInput(line: String) {
        withContext(Dispatchers.IO) {
            try {
                stdin?.write((line + "\n").toByteArray())
                stdin?.flush()
                withContext(Dispatchers.Main) {
                    transcript.add(TerminalLine.Command(line))
                }
            } catch (_: Exception) {}
        }
    }

    override suspend fun awaitExit(): Int = exitDeferred.await()
}
