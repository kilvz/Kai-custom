package com.kai.custom.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kai.custom.formatFileSize
import com.kai.custom.saveFileToDevice
import com.kai.custom.ui.handCursor
import com.kai.custom.ui.sandbox.SandboxProgressRow
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import com.kai.custom.linux.LinuxDistro
import com.kai.custom.ui.sandbox.sandboxStatusText
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.sandbox_settings_alt_memory
import kai.composeapp.generated.resources.sandbox_settings_distribution
import kai.composeapp.generated.resources.sandbox_settings_export
import kai.composeapp.generated.resources.sandbox_settings_import
import kai.composeapp.generated.resources.sandbox_settings_reset_dpkg
import kai.composeapp.generated.resources.sandbox_settings_root_warning
import kai.composeapp.generated.resources.sandbox_settings_run_as_root
import kai.composeapp.generated.resources.sandbox_settings_run_as_root_desc
import kai.composeapp.generated.resources.sandbox_settings_storage_mount
import kai.composeapp.generated.resources.sandbox_settings_storage_mount_desc
import kai.composeapp.generated.resources.sandbox_settings_version_info
import kai.composeapp.generated.resources.settings_sandbox_cancel
import kai.composeapp.generated.resources.settings_sandbox_description
import kai.composeapp.generated.resources.settings_sandbox_disk_usage
import kai.composeapp.generated.resources.settings_sandbox_distro_alpine
import kai.composeapp.generated.resources.settings_sandbox_distro_alpine_detail
import kai.composeapp.generated.resources.settings_sandbox_distro_alpine_installed
import kai.composeapp.generated.resources.settings_sandbox_distro_debian
import kai.composeapp.generated.resources.settings_sandbox_distro_debian_detail
import kai.composeapp.generated.resources.settings_sandbox_distro_debian_installed
import kai.composeapp.generated.resources.settings_sandbox_distro_title
import kai.composeapp.generated.resources.settings_sandbox_distro_ubuntu
import kai.composeapp.generated.resources.settings_sandbox_distro_ubuntu_detail
import kai.composeapp.generated.resources.settings_sandbox_distro_ubuntu_installed
import kai.composeapp.generated.resources.settings_sandbox_install
import kai.composeapp.generated.resources.settings_sandbox_install_packages
import kai.composeapp.generated.resources.settings_sandbox_migrate_action
import kai.composeapp.generated.resources.settings_sandbox_migrate_detail
import kai.composeapp.generated.resources.settings_sandbox_migrate_title
import kai.composeapp.generated.resources.settings_sandbox_uninstall
import kai.composeapp.generated.resources.settings_sandbox_uninstall_confirm
import kotlinx.coroutines.launch
import kai.composeapp.generated.resources.settings_sandbox_uninstall_confirm_shared
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SandboxSettingsCard(
    sandboxState: SandboxUiState,
    onToggleSandbox: (Boolean) -> Unit,
    onToggleStorageMount: (Boolean) -> Unit,
    onToggleSandboxRoot: (Boolean) -> Unit = {},
    onSelectDistro: (LinuxDistro) -> Unit,
    onSetupSandbox: () -> Unit,
    onCancelSandbox: () -> Unit,
    onResetSandbox: () -> Unit,
    onInstallPackages: () -> Unit,
    onInstallAltMemory: () -> Unit = {},
    onUpdateAltMemory: () -> Unit = {},
    onBackupSandbox: () -> Unit = {},
    onImportSandbox: (ByteArray) -> Unit = {},
    onExportSaved: () -> Unit = {},
    onMigrateHome: () -> Unit,
) {
    var showResetDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val importFilePicker = rememberFilePickerLauncher(
        type = FileKitType.File(extensions = listOf("tar.gz", "tgz", "tar")),
    ) { file ->
        if (file != null) {
            scope.launch {
                val bytes = file.readBytes()
                onImportSandbox(bytes)
            }
        }
    }
    LaunchedEffect(sandboxState.backupExportPath) {
        val path = sandboxState.backupExportPath ?: return@LaunchedEffect
        saveFileToDevice(path, "sandbox-rootfs", "tar.gz")
        onExportSaved()
    }
    val statusText = sandboxStatusText(sandboxState.sandboxStatusLabel)
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // Before install this names what would be installed; after, what is.
                    text = sandboxState.distro.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (sandboxState.sandboxReady || sandboxState.sandboxInstalled) {
                    if (sandboxState.sandboxDiskUsageMB > 0) {
                        Text(
                            text = stringResource(Res.string.settings_sandbox_disk_usage, sandboxState.sandboxDiskUsageMB),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.settings_sandbox_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (sandboxState.sandboxReady || sandboxState.sandboxInstalled) {
                Switch(
                    checked = sandboxState.isSandboxEnabled,
                    onCheckedChange = onToggleSandbox,
                )
            }
        }

        // Offered at any time: each distribution keeps its own install, so this
        // picks which one the shell integration runs in rather than replacing it.
        if (!sandboxState.isWorking) {
            DistroPicker(
                selected = sandboxState.distro,
                installed = sandboxState.installedDistros,
                onSelect = onSelectDistro,
            )
        }

        if (sandboxState.sandboxProgress != null) {
            SandboxProgressRow(sandboxState.sandboxProgress, statusText, onCancelSandbox)
        } else if (sandboxState.isWorking) {
            SandboxProgressRow(null, statusText, onCancelSandbox)
        } else if (sandboxState.hasError) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // Offered whenever the distribution being left still holds files this one
        // does not, so switching never has to mean abandoning them. It stops
        // being shown once the copy is done, because then there is nothing left.
        val migration = sandboxState.migration
        if (migration != null && sandboxState.sandboxReady && !sandboxState.isWorking) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    Res.string.settings_sandbox_migrate_title,
                    migration.from.displayName,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(
                    Res.string.settings_sandbox_migrate_detail,
                    migration.fileCount,
                    formatFileSize(migration.bytes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            // Its own button rather than a third one in the action row below:
            // that row is already two wide on a phone, and this belongs to the
            // sentence above it.
            OutlinedButton(onClick = onMigrateHome, modifier = Modifier.handCursor()) {
                Text(stringResource(Res.string.settings_sandbox_migrate_action))
            }
        }

        if (!sandboxState.isWorking) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!sandboxState.sandboxInstalled) {
                    Button(onClick = onSetupSandbox, modifier = Modifier.handCursor()) {
                        Text(stringResource(Res.string.settings_sandbox_install))
                    }
                } else {
                    if (sandboxState.needsReset) {
                        Button(onClick = { showResetDialog = true }, modifier = Modifier.handCursor()) {
                            Text(stringResource(Res.string.sandbox_settings_reset_dpkg))
                        }
                    } else if (!sandboxState.sandboxPackagesInstalled) {
                        OutlinedButton(onClick = onInstallPackages, modifier = Modifier.handCursor()) {
                            Text(stringResource(Res.string.settings_sandbox_install_packages))
                        }
                    }
                    if (!sandboxState.altMemoryInstalled) {
                        OutlinedButton(onClick = onInstallAltMemory, modifier = Modifier.handCursor()) {
                            Text(stringResource(Res.string.sandbox_settings_alt_memory))
                        }
                    } else {
                        val cv = sandboxState.altMemoryCurrentVersion
                        val lv = sandboxState.altMemoryLatestVersion
                        val isUpToDate = cv != null && lv != null && cv == lv
                        val label = if (isUpToDate) "Reinstall Alt-Memory" else "Update Alt-Memory"
                        OutlinedButton(onClick = onUpdateAltMemory, modifier = Modifier.handCursor()) {
                            Text(label)
                        }
                        if (cv != null && lv != null && !isUpToDate) {
                            Text(
                                text = stringResource(Res.string.sandbox_settings_version_info, cv, lv),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    OutlinedButton(onClick = { showResetDialog = true }, modifier = Modifier.handCursor()) {
                        Text(stringResource(Res.string.settings_sandbox_uninstall))
                    }
                }
            }
        }

        if (sandboxState.sandboxReady || sandboxState.sandboxInstalled) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.sandbox_settings_storage_mount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(Res.string.sandbox_settings_storage_mount_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = sandboxState.isSandboxStorageMountEnabled,
                    onCheckedChange = onToggleStorageMount,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.sandbox_settings_run_as_root),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(Res.string.sandbox_settings_run_as_root_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = sandboxState.isSandboxRootEnabled,
                    onCheckedChange = onToggleSandboxRoot,
                )
            }

            sandboxState.rootErrorMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (sandboxState.isSandboxRootEnabled) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFFFF3E0),
                    shape = CardDefaults.shape,
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            text = stringResource(Res.string.sandbox_settings_root_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF424242),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBackupSandbox, modifier = Modifier.handCursor()) {
                    Text(stringResource(Res.string.sandbox_settings_export))
                }
                OutlinedButton(onClick = { importFilePicker.launch() }, modifier = Modifier.handCursor()) {
                    Text(stringResource(Res.string.sandbox_settings_import))
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(Res.string.settings_sandbox_uninstall)) },
            text = {
                // A Debian sandbox is the same install Kai Build works in, so
                // uninstalling here takes Kai Build's Linux with it.
                val confirm = if (sandboxState.distro == LinuxDistro.DEBIAN) {
                    Res.string.settings_sandbox_uninstall_confirm_shared
                } else {
                    Res.string.settings_sandbox_uninstall_confirm
                }
                Text(stringResource(confirm))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onResetSandbox()
                    },
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_sandbox_uninstall))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_sandbox_cancel))
                }
            },
        )
    }
}

@Composable
private fun DistroPicker(
    selected: LinuxDistro,
    installed: Set<LinuxDistro>,
    onSelect: (LinuxDistro) -> Unit,
) {
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(Res.string.settings_sandbox_distro_title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(Modifier.selectableGroup()) {
        DistroOption(
            distro = LinuxDistro.DEBIAN,
            title = stringResource(Res.string.settings_sandbox_distro_debian),
            // An install already on disk is a switch, not a download — the size
            // estimate would be the wrong thing to lead with.
            detail = if (LinuxDistro.DEBIAN in installed) {
                stringResource(Res.string.settings_sandbox_distro_debian_installed)
            } else {
                stringResource(Res.string.settings_sandbox_distro_debian_detail)
            },
            selected = selected == LinuxDistro.DEBIAN,
            onSelect = onSelect,
        )
        DistroOption(
            distro = LinuxDistro.ALPINE,
            title = stringResource(Res.string.settings_sandbox_distro_alpine),
            detail = if (LinuxDistro.ALPINE in installed) {
                stringResource(Res.string.settings_sandbox_distro_alpine_installed)
            } else {
                stringResource(Res.string.settings_sandbox_distro_alpine_detail)
            },
            selected = selected == LinuxDistro.ALPINE,
            onSelect = onSelect,
        )
        DistroOption(
            distro = LinuxDistro.UBUNTU,
            title = stringResource(Res.string.settings_sandbox_distro_ubuntu),
            detail = if (LinuxDistro.UBUNTU in installed) {
                stringResource(Res.string.settings_sandbox_distro_ubuntu_installed)
            } else {
                stringResource(Res.string.settings_sandbox_distro_ubuntu_detail)
            },
            selected = selected == LinuxDistro.UBUNTU,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun DistroOption(
    distro: LinuxDistro,
    title: String,
    detail: String,
    selected: Boolean,
    onSelect: (LinuxDistro) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { onSelect(distro) },
            )
            .handCursor(),
        verticalAlignment = CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
