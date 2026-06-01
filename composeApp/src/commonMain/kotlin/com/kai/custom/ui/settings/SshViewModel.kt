package com.kai.custom.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kai.custom.SshAuthMethod
import com.kai.custom.SshConfig
import com.kai.custom.SshConnectionManager
import com.kai.custom.SshConnectionState
import com.kai.custom.SshProfile
import com.kai.custom.TerminalLine
import com.kai.custom.data.AppSettings
import com.kai.custom.runBlockingCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SshUiState(
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val authMethod: SshAuthMethod = SshAuthMethod.PASSWORD,
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
    val connectionState: SshConnectionState = SshConnectionState(),
    val transcript: List<TerminalLine> = emptyList(),
    val profiles: List<SshProfile> = emptyList(),
    val activeProfileName: String = "",
)

class SshViewModel(
    private val appSettings: AppSettings,
    private val sshConnectionManager: SshConnectionManager,
) : ViewModel() {

    private val configState = MutableStateFlow(
        SshUiState(
            host = appSettings.getSshHost(),
            port = appSettings.getSshPort().toString(),
            username = appSettings.getSshUsername(),
            authMethod = appSettings.getSshAuthMethod(),
            password = appSettings.getSshPassword(),
            privateKey = appSettings.getSshPrivateKey(),
            passphrase = appSettings.getSshPassphrase(),
            profiles = appSettings.getSshProfiles(),
            activeProfileName = appSettings.getActiveSshProfileName(),
        ),
    )

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    val state: StateFlow<SshUiState> = combine(
        configState,
        sshConnectionManager.connectionState,
        sshConnectionManager.transcript,
    ) { config, conn, transcript ->
        config.copy(connectionState = conn, transcript = transcript)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), configState.value)

    fun selectProfile(name: String) {
        if (name.isBlank()) {
            configState.value = configState.value.copy(activeProfileName = "")
            return
        }
        val profile = appSettings.getSshProfiles().find { it.name == name } ?: return
        appSettings.setActiveSshProfileName(name)
        configState.value = configState.value.copy(
            activeProfileName = name,
            host = profile.host,
            port = profile.port.toString(),
            username = profile.username,
            authMethod = profile.authMethod,
            password = profile.password,
            privateKey = profile.privateKey,
            passphrase = profile.passphrase,
            profiles = appSettings.getSshProfiles(),
        )
    }

    fun deleteProfile(name: String) {
        appSettings.deleteSshProfile(name)
        val profiles = appSettings.getSshProfiles()
        val activeName = if (appSettings.getActiveSshProfileName() == name) "" else appSettings.getActiveSshProfileName()
        configState.value = configState.value.copy(
            profiles = profiles,
            activeProfileName = activeName,
        )
    }

    fun saveCurrentAsProfile(name: String) {
        val s = configState.value
        val profile = SshProfile(
            name = name,
            host = s.host,
            port = s.port.toIntOrNull() ?: 22,
            username = s.username,
            authMethod = s.authMethod,
            password = s.password,
            privateKey = s.privateKey,
            passphrase = s.passphrase,
        )
        appSettings.saveSshProfile(profile)
        appSettings.setActiveSshProfileName(name)
        configState.value = configState.value.copy(
            profiles = appSettings.getSshProfiles(),
            activeProfileName = name,
        )
    }

    fun onHostChanged(host: String) {
        configState.value = configState.value.copy(host = host)
    }

    fun onPortChanged(port: String) {
        configState.value = configState.value.copy(port = port)
    }

    fun onUsernameChanged(username: String) {
        configState.value = configState.value.copy(username = username)
    }

    fun onAuthMethodChanged(method: SshAuthMethod) {
        configState.value = configState.value.copy(authMethod = method)
    }

    fun onPasswordChanged(password: String) {
        configState.value = configState.value.copy(password = password)
    }

    fun onPrivateKeyChanged(key: String) {
        configState.value = configState.value.copy(privateKey = key)
    }

    fun onPassphraseChanged(passphrase: String) {
        configState.value = configState.value.copy(passphrase = passphrase)
    }

    fun saveSettings() {
        val s = configState.value
        appSettings.setSshHost(s.host)
        appSettings.setSshPort(s.port.toIntOrNull() ?: 22)
        appSettings.setSshUsername(s.username)
        appSettings.setSshAuthMethod(s.authMethod)
        appSettings.setSshPassword(s.password)
        appSettings.setSshPrivateKey(s.privateKey)
        appSettings.setSshPassphrase(s.passphrase)
    }

    fun connect() {
        saveSettings()
        val s = configState.value
        val port = s.port.toIntOrNull() ?: 22
        viewModelScope.launch {
            sshConnectionManager.connect(
                SshConfig(
                    host = s.host,
                    port = port,
                    username = s.username,
                    authMethod = s.authMethod,
                    password = s.password,
                    privateKey = s.privateKey,
                    passphrase = s.passphrase,
                ),
            )
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            sshConnectionManager.disconnect()
        }
    }

    override fun onCleared() {
        super.onCleared()
        runBlockingCompat(Dispatchers.Default) { sshConnectionManager.disconnect() }
    }

    fun executeCommand(command: String) {
        if (command.isBlank()) return
        if (command == "clear") {
            clearTranscript()
            return
        }
        viewModelScope.launch {
            _isRunning.value = true
            sshConnectionManager.executeCommand(command)
            _isRunning.value = false
        }
    }

    fun clearTranscript() {
        sshConnectionManager.clearTranscript()
    }
}
