@file:OptIn(ExperimentalMaterial3Api::class)

package com.kai.custom.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.custom.SshAuthMethod
import com.kai.custom.SshProfile
import com.kai.custom.TerminalLine
import com.kai.custom.isBatteryOptimizationDisabled
import com.kai.custom.openBatteryOptimizationSettings
import com.kai.custom.ui.handCursor
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.ssh_terminal_help_text
import kai.composeapp.generated.resources.ssh_terminal_input_placeholder
import kai.composeapp.generated.resources.ssh_terminal_title
import kai.composeapp.generated.resources.terminal_run_content_description
import org.jetbrains.compose.resources.stringResource

private val SshTerminalBg = Color(0xFF1E1E1E)
private val SshInputBg = Color(0xFF252525)
private val SshText = Color(0xFFD4D4D4)
private val SshPrompt = Color(0xFF6CB6FF)
private val SshError = Color(0xFFF48771)
private val SshDimText = Color(0xFF666666)

private fun monoStyle(size: TextUnit, color: Color = Color.Unspecified) = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = size,
    color = color,
)

@Composable
internal fun SshTerminalContent(
    sshViewModel: SshViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    val state by sshViewModel.state.collectAsStateWithLifecycle()
    val isRunning by sshViewModel.isRunning.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val outputLines = state.transcript
    var inputText by remember { mutableStateOf("") }
    val isConnected = state.connectionState.connected
    val canSubmit = isConnected && inputText.isNotBlank() && !isRunning

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(isRunning, outputLines) {
        snapshotFlow { outputLines.size }.collect { size ->
            if (size > 0 && isRunning) {
                listState.scrollToItem(size - 1)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        if (!isConnected) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                DisconnectedSshContent(
                    state = state,
                    onSelectProfile = sshViewModel::selectProfile,
                    onConnect = sshViewModel::connect,
                    onOpenSettings = onOpenSettings,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                state = listState,
            ) {
                if (outputLines.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.ssh_terminal_help_text),
                            style = monoStyle(13.sp, SshDimText),
                        )
                    }
                }
                items(
                    items = outputLines,
                    contentType = { it::class },
                ) { line ->
                    when (line) {
                        is TerminalLine.Command -> {
                            Spacer(Modifier.height(4.dp))
                            SelectionContainer {
                                Text(
                                    text = "$ ${line.text}",
                                    style = monoStyle(13.sp, SshPrompt),
                                )
                            }
                        }
                        is TerminalLine.Output -> {
                            SelectionContainer {
                                Text(
                                    text = line.text,
                                    style = monoStyle(13.sp),
                                    color = SshText,
                                )
                            }
                        }
                        is TerminalLine.Error -> {
                            SelectionContainer {
                                Text(
                                    text = line.text,
                                    style = monoStyle(13.sp, SshError),
                                )
                            }
                        }
                    }
                }
                if (isRunning) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = SshPrompt,
                        )
                    }
                }
            }
        }

        if (isConnected) {
            androidx.compose.material3.HorizontalDivider(
                color = SshDimText.copy(alpha = 0.2f),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$",
                    style = monoStyle(14.sp, SshPrompt),
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    enabled = isConnected,
                    textStyle = monoStyle(14.sp, SshText),
                    placeholder = {
                        Text(
                            text = stringResource(Res.string.ssh_terminal_input_placeholder),
                            style = monoStyle(14.sp, SshDimText),
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = SshPrompt,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            if (canSubmit) {
                                sshViewModel.executeCommand(inputText.trim())
                                inputText = ""
                            }
                        },
                    ),
                    singleLine = true,
                )
                IconButton(
                    onClick = {
                        if (canSubmit) {
                            sshViewModel.executeCommand(inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = canSubmit,
                    modifier = Modifier.handCursor(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(Res.string.terminal_run_content_description),
                        tint = if (canSubmit) SshPrompt else SshDimText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DisconnectedSshContent(
    state: SshUiState,
    onSelectProfile: (String) -> Unit,
    onConnect: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
) {
    val scrollState = rememberScrollState()
    val batteryOk = isBatteryOptimizationDisabled()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "SSH Terminal",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Connect to a remote server to execute commands",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (state.profiles.isNotEmpty()) {
            Text(
                text = "Saved Servers",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))

            state.profiles.forEach { profile ->
                val isActive = profile.name == state.activeProfileName
                val conn = state.connectionState

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = "${profile.username}@${profile.host}:${profile.port}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Button(
                            onClick = {
                                onSelectProfile(profile.name)
                                onConnect()
                            },
                            enabled = !conn.connecting,
                            modifier = Modifier.handCursor(),
                        ) {
                            Text(
                                if (isActive && conn.connecting) "Connecting..."
                                else if (isActive && conn.error != null) "Retry"
                                else "Connect"
                            )
                        }
                    }

                    if (isActive && conn.error != null) {
                        Text(
                            text = conn.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                if (profile != state.profiles.last()) {
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        if (state.profiles.isEmpty()) {
            Text(
                text = "No saved servers. Add one in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }

        if (onOpenSettings != null) {
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth().handCursor(),
            ) {
                Text("Add Server")
            }
            Spacer(Modifier.height(12.dp))
        }

        if (!batteryOk) {
            Text(
                text = "SSH connections may disconnect if battery optimization is enabled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { openBatteryOptimizationSettings() },
                modifier = Modifier.handCursor(),
            ) {
                Text("Battery Optimization Settings", style = MaterialTheme.typography.bodySmall)
            }

            if (onOpenSettings != null) {
                Spacer(Modifier.height(12.dp))
            }
        }

        if (onOpenSettings != null && batteryOk) {
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TerminalProfileSelector(
    profiles: List<SshProfile>,
    activeProfileName: String,
    onSelectProfile: (String) -> Unit,
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
