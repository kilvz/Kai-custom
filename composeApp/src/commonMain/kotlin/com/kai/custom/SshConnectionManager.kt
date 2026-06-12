package com.kai.custom

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
enum class SshAuthMethod { PASSWORD, KEY }

@Serializable
data class SshProfile(
    val name: String,
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val authMethod: SshAuthMethod = SshAuthMethod.PASSWORD,
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
)

data class SshConfig(
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val authMethod: SshAuthMethod = SshAuthMethod.PASSWORD,
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
)

data class SshConnectionState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val error: String? = null,
)

interface SshConnectionManager {
    val connectionState: StateFlow<SshConnectionState>
    val transcript: StateFlow<List<TerminalLine>>
    suspend fun connect(config: SshConfig): Result<Unit>
    suspend fun disconnect()
    suspend fun executeCommand(command: String, timeoutSeconds: Long = 30L): Result<SshCommandResult>
    fun clearTranscript()
}

data class SshCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
)

expect fun createSshConnectionManager(): SshConnectionManager
