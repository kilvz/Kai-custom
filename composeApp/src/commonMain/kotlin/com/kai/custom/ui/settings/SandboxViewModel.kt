package com.kai.custom.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kai.custom.Platform
import com.kai.custom.SandboxController
import com.kai.custom.SandboxController.BackupResult
import com.kai.custom.SandboxStatus
import com.kai.custom.currentPlatform
import com.kai.custom.isRootAvailable
import com.kai.custom.data.DataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SandboxUiState(
    val showSandbox: Boolean = false,
    val sandboxInstalled: Boolean = false,
    val sandboxReady: Boolean = false,
    val sandboxProgress: Float? = null,
    val sandboxStatusText: String = "",
    val sandboxDiskUsageMB: Long = 0,
    val sandboxPackagesInstalled: Boolean = false,
    val isSandboxEnabled: Boolean = true,
    val isSandboxStorageMountEnabled: Boolean = false,
    val isSandboxRootEnabled: Boolean = false,
    val sandboxDistro: String = "alpine",
    val isWorking: Boolean = false,
    val hasError: Boolean = false,
    val rootErrorMessage: String? = null,
    val altMemoryInstalled: Boolean = false,
    val needsReset: Boolean = false,
    val backupExportBytes: ByteArray? = null,
)

class SandboxViewModel(
    private val dataRepository: DataRepository,
    private val sandboxController: SandboxController,
) : ViewModel() {

    // Seed synchronously from the controller's current status so the first
    // composition doesn't briefly render the install UI when the sandbox is
    // already ready. The controller mirrors LinuxSandboxManager's synchronous
    // installation check, so reading status.value here returns the real state.
    private val _state = MutableStateFlow(
        applyStatus(
            sandboxController.status.value,
            SandboxUiState(
                showSandbox = currentPlatform is Platform.Mobile.Android || currentPlatform is Platform.Desktop,
                isSandboxEnabled = dataRepository.isSandboxEnabled(),
                isSandboxStorageMountEnabled = dataRepository.isSandboxStorageMountEnabled(),
                isSandboxRootEnabled = dataRepository.isSandboxRootEnabled(),
                sandboxDistro = dataRepository.getSandboxDistro(),
                altMemoryInstalled = dataRepository.isAltMemoryInstalled(),
            ),
        ),
    )

    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sandboxController.status.collect { sandboxStatus ->
                _state.update { applyStatus(sandboxStatus, it) }
            }
        }
    }

    private fun applyStatus(status: SandboxStatus, base: SandboxUiState): SandboxUiState = base.copy(
        sandboxInstalled = status.installed,
        sandboxReady = status.ready,
        sandboxProgress = status.progress,
        sandboxStatusText = status.statusText,
        sandboxDiskUsageMB = status.diskUsageMB,
        sandboxPackagesInstalled = status.packagesInstalled,
        isWorking = status.working,
        hasError = status.error,
        sandboxDistro = dataRepository.getSandboxDistro(),
        altMemoryInstalled = dataRepository.isAltMemoryInstalled(),
        needsReset = status.needsReset,
    )

    fun onToggleSandbox(enabled: Boolean) {
        dataRepository.setSandboxEnabled(enabled)
        _state.update { it.copy(isSandboxEnabled = enabled) }
    }

    fun onToggleStorageMount(enabled: Boolean) {
        dataRepository.setSandboxStorageMountEnabled(enabled)
        _state.update { it.copy(isSandboxStorageMountEnabled = enabled) }
        sandboxController.closeSession(com.kai.custom.SandboxSessions.DEFAULT)
        sandboxController.closeSession(com.kai.custom.SandboxSessions.TERMINAL)
    }

    fun onToggleSandboxRoot(enabled: Boolean) {
        if (enabled && !isRootAvailable()) {
            _state.update { it.copy(rootErrorMessage = "Root not available") }
            return
        }
        dataRepository.setSandboxRootEnabled(enabled)
        _state.update { it.copy(isSandboxRootEnabled = enabled, rootErrorMessage = null) }
    }

    fun onSetupSandbox() {
        sandboxController.setup()
    }

    fun onCancelSandbox() {
        sandboxController.cancel()
    }

    fun onResetSandbox() {
        sandboxController.reset()
    }

    fun onInstallPackages() {
        sandboxController.installPackages()
    }

    fun onInstallAltMemory() {
        viewModelScope.launch {
            val installed = sandboxController.installAltMemoryPackage()
            dataRepository.setAltMemoryInstalled(installed)
            _state.update { it.copy(altMemoryInstalled = installed) }
        }
    }

    fun onUpdateAltMemory() {
        viewModelScope.launch {
            val installed = sandboxController.updateAltMemoryPackage()
            dataRepository.setAltMemoryInstalled(installed)
            _state.update { it.copy(altMemoryInstalled = installed) }
        }
    }

    fun onDistroChanged(distro: String) {
        dataRepository.setSandboxDistro(distro)
        _state.update { it.copy(sandboxDistro = distro) }
    }

    fun onBackupSandbox() {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, sandboxStatusText = "Creating backup...") }
            val result = sandboxController.backupSandbox()
            result.onSuccess { backup ->
                _state.update { it.copy(isWorking = false, backupExportBytes = backup.bytes, sandboxStatusText = "Choose where to save the backup") }
            }.onFailure { e ->
                _state.update { it.copy(isWorking = false, hasError = true, sandboxStatusText = "Backup failed: ${e.message}") }
            }
        }
    }

    fun onExportSaved() {
        _state.update { it.copy(backupExportBytes = null) }
    }

    fun onImportSandbox(data: ByteArray) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, sandboxStatusText = "Importing sandbox...") }
            val result = sandboxController.importSandbox(data)
            result.onSuccess {
                _state.update { it.copy(isWorking = false, sandboxStatusText = "Sandbox restored") }
            }.onFailure { e ->
                _state.update { it.copy(isWorking = false, hasError = true, sandboxStatusText = "Import failed: ${e.message}") }
            }
        }
    }
}
