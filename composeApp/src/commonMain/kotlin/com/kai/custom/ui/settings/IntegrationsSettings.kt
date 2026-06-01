package com.kai.custom.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.custom.data.DataRepository
import com.kai.custom.ui.handCursor
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.settings_open_github_issue
import kai.composeapp.generated.resources.settings_request_integration_description
import kai.composeapp.generated.resources.settings_request_integration_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject

@Composable
internal fun IntegrationsContent(
    splinterlandsViewModel: SplinterlandsViewModel = koinViewModel(),
    dataRepository: DataRepository = koinInject(),
) {
    val splinterlandsState by splinterlandsViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { splinterlandsViewModel.onScreenVisible() }

    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (splinterlandsState.showSplinterlandsSection) {
            SettingsCard {
                SplinterlandsSection(
                    isEnabled = splinterlandsState.isSplinterlandsEnabled,
                    accounts = splinterlandsState.splinterlandsAccounts,
                    instanceIds = splinterlandsState.splinterlandsInstanceIds,
                    addStatus = splinterlandsState.splinterlandsAddStatus,
                    battleLog = splinterlandsState.splinterlandsBattleLog,
                    availableServices = splinterlandsState.splinterlandsAvailableServices,
                    onToggle = splinterlandsState.onToggleSplinterlands,
                    onTestAndAddAccount = splinterlandsState.onTestAndAddSplinterlandsAccount,
                    onRemoveAccount = splinterlandsState.onRemoveSplinterlandsAccount,
                    onAddService = splinterlandsState.onAddSplinterlandsService,
                    onRemoveService = splinterlandsState.onRemoveSplinterlandsService,
                    onReorderServices = splinterlandsState.onReorderSplinterlandsServices,
                    onStartBattle = splinterlandsState.onStartSplinterlandsBattle,
                    onStopBattle = splinterlandsState.onStopSplinterlandsBattle,
                    onClearBattleLog = splinterlandsState.onClearSplinterlandsBattleLog,
                )
            }
        }

        SettingsCard {
            TelegramSection(
                isEnabled = dataRepository.isTelegramEnabled(),
                onToggle = { dataRepository.setTelegramEnabled(it) },
            )
        }

        SettingsCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.settings_request_integration_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.settings_request_integration_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { uriHandler.openUri("https://github.com/kilvz/Kai-custom/issues/new?template=integration_request.yml") },
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_open_github_issue))
                }
            }
        }
    }
}

@Composable
private fun TelegramSection(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dataRepository: DataRepository = koinInject()
    var botToken by remember { mutableStateOf(dataRepository.getTelegramBotToken()) }
    var authorizedIds by remember { mutableStateOf(dataRepository.getTelegramAuthorizedChatIds().joinToString(", ")) }
    var showToken by remember { mutableStateOf(false) }
    val syncState = remember { mutableStateOf(dataRepository.getTelegramSyncState()) }
    var statusMessage by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = "Telegram Bot",
            description = "Talk to the AI via Telegram",
            checked = isEnabled,
            onCheckedChange = onToggle,
            actions = {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = "Experimental",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            },
        )

        if (isEnabled) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "This feature is experimental and may not work reliably.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Bot Token",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Get one from @BotFather on Telegram — create a bot and copy the HTTP API token",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = botToken,
                onValueChange = { botToken = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11") },
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    dataRepository.setTelegramBotToken(botToken)
                    statusMessage = "Token saved"
                }),
            )

            Row {
                OutlinedButton(
                    onClick = { showToken = !showToken },
                ) {
                    Text(if (showToken) "Hide" else "Show")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        dataRepository.setTelegramBotToken(botToken)
                        statusMessage = "Token saved"
                    },
                ) {
                    Text("Save")
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Authorized Chat IDs",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Comma-separated Telegram user IDs allowed to talk to this bot. Leave empty to allow all (not recommended). Find your ID by messaging @userinfobot on Telegram.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = authorizedIds,
                onValueChange = { authorizedIds = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("123456789, 987654321") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val ids = authorizedIds.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
                    dataRepository.setTelegramAuthorizedChatIds(ids)
                    statusMessage = "Authorized IDs saved"
                }),
            )
            OutlinedButton(
                onClick = {
                    val ids = authorizedIds.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
                    dataRepository.setTelegramAuthorizedChatIds(ids)
                    statusMessage = "Authorized IDs saved"
                },
            ) {
                Text("Save")
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            dataRepository.pollTelegram()
                            statusMessage = "Polled"
                        }
                    },
                ) {
                    Text("Test Connection")
                }
                Spacer(Modifier.width(8.dp))
                if (statusMessage.isNotEmpty()) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (syncState.value.lastError != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Last error: ${syncState.value.lastError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
