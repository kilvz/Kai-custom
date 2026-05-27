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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.custom.TerminalLine
import com.kai.custom.ui.handCursor
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.ssh_terminal_help_text
import kai.composeapp.generated.resources.ssh_terminal_input_placeholder
import kai.composeapp.generated.resources.ssh_terminal_not_connected
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.ssh_terminal_not_connected),
                    style = monoStyle(14.sp, SshDimText),
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
