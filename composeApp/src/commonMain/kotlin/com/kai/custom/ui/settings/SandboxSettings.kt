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
import androidx.compose.ui.unit.dp
import com.kai.custom.saveFileToDevice
import com.kai.custom.ui.handCursor
import com.kai.custom.ui.sandbox.SandboxProgressRow
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.settings_sandbox_cancel
import kai.composeapp.generated.resources.settings_sandbox_description
import kai.composeapp.generated.resources.settings_sandbox_disk_usage
import kai.composeapp.generated.resources.settings_sandbox_install
import kai.composeapp.generated.resources.settings_sandbox_install_packages
import kai.composeapp.generated.resources.settings_sandbox_uninstall
import kai.composeapp.generated.resources.settings_sandbox_uninstall_confirm
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SandboxSettingsCard(
    sandboxState: SandboxUiState,
    onToggleSandbox: (Boolean) -> Unit,
    onToggleStorageMount: (Boolean) -> Unit,
    onToggleSandboxRoot: (Boolean) -> Unit = {},
    onSetupSandbox: () -> Unit,
    onCancelSandbox: () -> Unit,
    onResetSandbox: () -> Unit,
    onInstallPackages: () -> Unit,
    onInstallAltMemory: () -> Unit = {},
    onUpdateAltMemory: () -> Unit = {},
    onBackupSandbox: () -> Unit = {},
    onImportSandbox: (ByteArray) -> Unit = {},
    onExportSaved: () -> Unit = {},
    onDistroChanged: (String) -> Unit = {},
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
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sandboxState.sandboxDistro.replaceFirstChar { it.uppercase() },
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

        if (sandboxState.sandboxProgress != null) {
            SandboxProgressRow(sandboxState.sandboxProgress, sandboxState.sandboxStatusText, onCancelSandbox)
        } else if (sandboxState.isWorking) {
            SandboxProgressRow(null, sandboxState.sandboxStatusText, onCancelSandbox)
        } else if (sandboxState.hasError) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = sandboxState.sandboxStatusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (!sandboxState.sandboxInstalled && !sandboxState.isWorking) {
            Spacer(Modifier.height(8.dp))
            DistroSelector(
                distro = sandboxState.sandboxDistro,
                onDistroChanged = onDistroChanged,
            )
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
                            Text("Reset — dpkg broken")
                        }
                    } else if (!sandboxState.sandboxPackagesInstalled) {
                        OutlinedButton(onClick = onInstallPackages, modifier = Modifier.handCursor()) {
                            Text(stringResource(Res.string.settings_sandbox_install_packages))
                        }
                    }
                    if (!sandboxState.altMemoryInstalled) {
                        OutlinedButton(onClick = onInstallAltMemory, modifier = Modifier.handCursor()) {
                            Text("Alt-Memory")
                        }
                    } else {
                        OutlinedButton(onClick = onUpdateAltMemory, modifier = Modifier.handCursor()) {
                            Text("Update Alt-Memory")
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
                        text = "User storage mount (/sdcard)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Let the AI access files on your device storage",
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
                        text = "Run sandbox as root",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Real root via su instead of proot-faked root. Bypasses sandbox isolation.",
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
                            text = "Only enable if you trust the AI. Real root bypasses sandbox isolation and gives full system access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF424242),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBackupSandbox, modifier = Modifier.handCursor()) {
                    Text("Export")
                }
                OutlinedButton(onClick = { importFilePicker.launch() }, modifier = Modifier.handCursor()) {
                    Text("Import")
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(Res.string.settings_sandbox_uninstall)) },
            text = { Text(stringResource(Res.string.settings_sandbox_uninstall_confirm)) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DistroSelector(
    distro: String,
    onDistroChanged: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val distros = listOf("alpine" to "Alpine Linux", "ubuntu" to "Ubuntu")
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = distros.first { it.first == distro }.second,
            onValueChange = {},
            readOnly = true,
            label = { Text("Distribution") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .width(240.dp)
                .handCursor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            distros.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onDistroChanged(value)
                        expanded = false
                    },
                )
            }
        }
    }
}
