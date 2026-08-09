package com.kai.custom.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kai.custom.Platform
import com.kai.custom.SandboxController
import com.kai.custom.SandboxController.BackupResult
import com.kai.custom.SandboxMigration
import com.kai.custom.SandboxStatus
import com.kai.custom.currentPlatform
import com.kai.custom.data.DataRepository
import com.kai.custom.isRootAvailable
import com.kai.custom.linux.LinuxDistro
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
    /**
     * The installed distro once [sandboxInstalled], the pending choice before
     * that. The controller reports both through the same field because nothing
     * downstream should ever prefer the setting over what is on disk.
     */
    val distro: LinuxDistro = LinuxDistro.DEFAULT,
    val isWorking: Boolean = false,
    val hasError: Boolean = false,
    val rootErrorMessage: String? = null,
    val altMemoryInstalled: Boolean = false,
    val altMemoryCurrentVersion: String? = null,
    val altMemoryLatestVersion: String? = null,
    val altMemoryVersionChecking: Boolean = false,
    val needsReset: Boolean = false,
    val backupExportPath: String? = null,
    /** Distributions already on disk, so the picker can say which is a download. */
    val installedDistros: Set<LinuxDistro> = emptySet(),
    /** Files the other install has and this one does not; null when there are none. */
    val migration: SandboxMigration? = null,
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
            // No distro seed: the controller is the only thing that knows which
            // install the sandbox is actually pointed at, and a sandbox older
            // than the picker has one the stored setting does not name.
            SandboxUiState(
                showSandbox = currentPlatform is Platform.Mobile.Android || currentPlatform is Platform.Desktop,
                isSandboxEnabled = dataRepository.isSandboxEnabled(),
                isSandboxStorageMountEnabled = dataRepository.isSandboxStorageMountEnabled(),
                isSandboxRootEnabled = dataRepository.isSandboxRootEnabled(),
                altMemoryInstalled = dataRepository.isAltMemoryInstalled(),
            ),
        ),
    )

    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sandboxController.status.collect { sandboxStatus ->
                _state.update { applyStatus(sandboxStatus, it) }
                if (sandboxStatus.ready && _state.value.altMemoryInstalled) {
                    checkAltMemoryVersions()
                }
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
        distro = status.distro,
        altMemoryInstalled = dataRepository.isAltMemoryInstalled(),
        needsReset = status.needsReset,
        installedDistros = status.installedDistros,
        migration = status.migration,
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

    /**
     * Points the shell integration at another distribution. Nothing is removed:
     * each keeps its own install, so this is a switch when the target is already
     * there and a plain "Install" offer when it is not.
     *
     * The state is updated ahead of the controller so the radio button answers
     * the tap; the controller's status then confirms it along with what the new
     * install's card should say.
     */
    fun onSelectDistro(distro: LinuxDistro) {
        if (_state.value.distro == distro || _state.value.isWorking) return
        dataRepository.setSandboxDistro(distro)
        _state.update { it.copy(distro = distro) }
        sandboxController.selectDistro(distro)
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
            checkAltMemoryVersions()
        }
    }

    fun checkAltMemoryVersions() {
        viewModelScope.launch {
            if (_state.value.altMemoryVersionChecking) return@launch
            _state.update { it.copy(altMemoryVersionChecking = true) }
            val (current, latest) = sandboxController.getAltMemoryVersions()
            _state.update {
                it.copy(
                    altMemoryCurrentVersion = current,
                    altMemoryLatestVersion = latest,
                    altMemoryVersionChecking = false,
                )
            }
        }
    }

    fun onBackupSandbox() {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, sandboxStatusText = "Creating backup...") }
            val result = sandboxController.backupSandbox()
            result.onSuccess { backup ->
                _state.update { it.copy(isWorking = false, backupExportPath = backup.path, sandboxStatusText = "Choose where to save the backup") }
            }.onFailure { e ->
                _state.update { it.copy(isWorking = false, hasError = true, sandboxStatusText = "Backup failed: ${e.message}") }
            }
        }
    }

    fun onExportSaved() {
        _state.update { it.copy(backupExportPath = null) }
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

    /**
     * Copies the other distribution's home in. Nothing is removed by it — the
     * distribution it came from is still installed afterwards, and removing that
     * stays the separate, deliberate Uninstall action.
     */
    fun onMigrateHome() {
        sandboxController.migrateHome()
    }
}
