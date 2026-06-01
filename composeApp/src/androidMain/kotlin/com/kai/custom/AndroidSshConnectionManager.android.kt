package com.kai.custom

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

class AndroidSshConnectionManager : SshConnectionManager {

    private val jsch = JSch()
    private var session: Session? = null
    private val transcriptLines = mutableListOf<TerminalLine>()

    private val _connectionState = MutableStateFlow(SshConnectionState())
    override val connectionState = _connectionState.asStateFlow()

    private val _transcript = MutableStateFlow<List<TerminalLine>>(emptyList())
    override val transcript = _transcript.asStateFlow()

    override suspend fun connect(config: SshConfig): Result<Unit> = withContext(Dispatchers.IO) {
        _connectionState.value = SshConnectionState(connecting = true)
        try {
            session?.disconnect()

            if (config.authMethod == SshAuthMethod.KEY && config.privateKey.isNotBlank()) {
                val passphraseBytes = if (config.passphrase.isNotBlank()) config.passphrase.toByteArray() else null
                jsch.addIdentity("kai_ssh_key", config.privateKey.toByteArray(), null, passphraseBytes)
            }

            val s = jsch.getSession(config.username, config.host, config.port).apply {
                if (config.authMethod == SshAuthMethod.PASSWORD && config.password.isNotBlank()) {
                    setPassword(config.password)
                }
                setConfig("StrictHostKeyChecking", "no")
                setServerAliveInterval(15000)
                connect(30000)
            }
            session = s
            _connectionState.value = SshConnectionState(connected = true)
            Result.success(Unit)
        } catch (e: Exception) {
            _connectionState.value = SshConnectionState(error = e.message ?: "Connection failed")
            Result.failure(e)
        }
    }

    override suspend fun disconnect(): Unit = withContext(Dispatchers.IO) {
        try {
            session?.disconnect()
        } catch (_: Exception) { }
        session = null
        _connectionState.value = SshConnectionState()
    }

    override suspend fun executeCommand(command: String, timeoutSeconds: Long): Result<SshCommandResult> = withContext(Dispatchers.IO) {
        val s = session
        if (s == null || !s.isConnected) {
            _connectionState.value = SshConnectionState()
            return@withContext Result.failure(Exception("SSH not connected"))
        }
        addTranscript(TerminalLine.Command(command))
        try {
            val channel = s.openChannel("exec") as ChannelExec
            channel.setCommand(command)

            val stdout = ByteArrayOutputStream(MAX_OUTPUT_BYTES)
            val stderr = ByteArrayOutputStream(MAX_OUTPUT_BYTES)

            val inputStream: InputStream = channel.getInputStream()
            val errStream: InputStream = channel.getErrStream()

            channel.connect(timeoutSeconds.toInt() * 1000)

            coroutineScope {
                launch(Dispatchers.IO) {
                    val buf = ByteArray(4096)
                    while (isActive) {
                        val len = inputStream.read(buf)
                        if (len <= 0) break
                        val remaining = MAX_OUTPUT_BYTES - stdout.size()
                        if (remaining > 0) {
                            stdout.write(buf, 0, minOf(len, remaining))
                        }
                    }
                }
                launch(Dispatchers.IO) {
                    val buf = ByteArray(4096)
                    while (isActive) {
                        val len = errStream.read(buf)
                        if (len <= 0) break
                        val remaining = MAX_OUTPUT_BYTES - stderr.size()
                        if (remaining > 0) {
                            stderr.write(buf, 0, minOf(len, remaining))
                        }
                    }
                }

                while (!channel.isClosed && isActive) {
                    delay(100)
                }
            }

            channel.disconnect()

            val exitCode = channel.exitStatus
            val outText = stdout.toString("UTF-8")
            val errText = stderr.toString("UTF-8")

            if (outText.isNotBlank()) addTranscript(TerminalLine.Output(outText))
            if (errText.isNotBlank()) addTranscript(TerminalLine.Error(errText))

            Result.success(
                SshCommandResult(
                    stdout = outText,
                    stderr = errText,
                    exitCode = exitCode,
                ),
            )
        } catch (e: Exception) {
            addTranscript(TerminalLine.Error(e.message ?: "Command failed"))
            Result.failure(e)
        }
    }

    override fun clearTranscript() {
        transcriptLines.clear()
        _transcript.value = emptyList()
    }

    private fun addTranscript(line: TerminalLine) {
        transcriptLines.add(line)
        if (transcriptLines.size > MAX_TRANSCRIPT_LINES) {
            transcriptLines.removeAt(0)
        }
        _transcript.value = transcriptLines.toList()
    }

    companion object {
        private const val MAX_TRANSCRIPT_LINES = 500
        private const val MAX_OUTPUT_BYTES = 5 * 1024 * 1024 // 5 MB per stream
    }
}
