package com.kai.custom.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kai.custom.SshAuthMethod
import com.kai.custom.SshProfile
import com.kai.custom.TerminalLine
import com.kai.custom.ui.handCursor

@Composable
internal fun SshSettingsCard(
    sshState: SshUiState,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onAuthMethodChanged: (SshAuthMethod) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPrivateKeyChanged: (String) -> Unit,
    onPassphraseChanged: (String) -> Unit = {},
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onClearTranscript: () -> Unit = {},
    onSelectProfile: (String) -> Unit = {},
    onDeleteProfile: (String) -> Unit = {},
    onSaveProfile: (String) -> Unit = {},
) {
    SettingsCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "SSH Connection",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Persistent SSH server for the AI to execute commands on",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            if (sshState.profiles.isNotEmpty()) {
                ProfileSelector(
                    profiles = sshState.profiles,
                    activeProfileName = sshState.activeProfileName,
                    onSelectProfile = onSelectProfile,
                    onDeleteProfile = onDeleteProfile,
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = sshState.host,
                onValueChange = onHostChanged,
                label = { Text("Host") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = sshState.port,
                    onValueChange = onPortChanged,
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = sshState.username,
                    onValueChange = onUsernameChanged,
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Authentication",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Column(Modifier.selectableGroup()) {
                SshAuthOption(
                    label = "Password",
                    selected = sshState.authMethod == SshAuthMethod.PASSWORD,
                    onClick = { onAuthMethodChanged(SshAuthMethod.PASSWORD) },
                )
                SshAuthOption(
                    label = "Private Key",
                    selected = sshState.authMethod == SshAuthMethod.KEY,
                    onClick = { onAuthMethodChanged(SshAuthMethod.KEY) },
                )
            }

            Spacer(Modifier.height(8.dp))

            if (sshState.authMethod == SshAuthMethod.PASSWORD) {
                OutlinedTextField(
                    value = sshState.password,
                    onValueChange = onPasswordChanged,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = sshState.privateKey,
                    onValueChange = onPrivateKeyChanged,
                    label = { Text("Private Key (PEM, OpenSSH, PPK)") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sshState.passphrase,
                    onValueChange = onPassphraseChanged,
                    label = { Text("Passphrase (optional)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))

            val conn = sshState.connectionState
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (conn.connected) {
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.handCursor()) {
                        Text("Disconnect")
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        enabled = !conn.connecting && sshState.host.isNotBlank() && sshState.username.isNotBlank(),
                        modifier = Modifier.handCursor(),
                    ) {
                        Text(if (conn.connecting) "Connecting..." else "Connect")
                    }
                }

                when {
                    conn.connected -> {
                        Text(
                            text = "Connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    conn.error != null -> {
                        Text(
                            text = conn.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            var showSaveProfileDialog by remember { mutableStateOf(false) }
            if (showSaveProfileDialog) {
                SaveProfileDialog(
                    initialName = sshState.activeProfileName,
                    onDismiss = { showSaveProfileDialog = false },
                    onSave = { name ->
                        onSaveProfile(name)
                        showSaveProfileDialog = false
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { showSaveProfileDialog = true },
                    enabled = sshState.host.isNotBlank() && sshState.username.isNotBlank(),
                    modifier = Modifier.handCursor(),
                ) {
                    Text("Save as Profile")
                }
                if (sshState.activeProfileName.isNotBlank()) {
                    OutlinedButton(
                        onClick = { onDeleteProfile(sshState.activeProfileName) },
                        modifier = Modifier.handCursor(),
                    ) {
                        Text("Delete Profile")
                    }
                }
            }

            if (sshState.transcript.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Command Log",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    OutlinedButton(
                        onClick = onClearTranscript,
                        modifier = Modifier.handCursor(),
                    ) {
                        Text("Clear", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E1E), shape = MaterialTheme.shapes.small)
                        .padding(8.dp),
                ) {
                    val transcript = sshState.transcript
                    SelectionContainer {
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(transcript) { line ->
                                when (line) {
                                    is TerminalLine.Command -> {
                                        Text(
                                            text = "$ ${line.text}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = Color(0xFF6CB6FF),
                                        )
                                    }
                                    is TerminalLine.Output -> {
                                        Text(
                                            text = line.text,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = Color(0xFFD4D4D4),
                                        )
                                    }
                                    is TerminalLine.Error -> {
                                        Text(
                                            text = line.text,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = Color(0xFFF48771),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSelector(
    profiles: List<SshProfile>,
    activeProfileName: String,
    onSelectProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val activeLabel = if (activeProfileName.isNotBlank()) activeProfileName else "Select profile..."
    Column {
        Text(
            text = "Saved Profiles",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().handCursor(),
            ) {
                Text(activeLabel, maxLines = 1)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f),
            ) {
                if (activeProfileName.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text("None (manual entry)") },
                        onClick = {
                            onSelectProfile("")
                            expanded = false
                        },
                    )
                }
                profiles.forEach { profile ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(profile.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${profile.username}@${profile.host}:${profile.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            onSelectProfile(profile.name)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveProfileDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save SSH Profile") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.handCursor(),
            ) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SshAuthOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
