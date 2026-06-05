package com.kai.custom.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.custom.SandboxController
import com.kai.custom.SandboxSessions
import com.kai.custom.data.DataRepository
import com.kai.custom.decodeToImageBitmap
import kotlin.io.encoding.Base64
import com.kai.custom.ui.handCursor
import com.kai.custom.whatsapp.WhatsAppLifecycleManager
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.settings_open_github_issue
import kai.composeapp.generated.resources.settings_request_integration_description
import kai.composeapp.generated.resources.settings_request_integration_title
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun IntegrationsContent(
    splinterlandsViewModel: SplinterlandsViewModel = koinViewModel(),
    dataRepository: DataRepository = koinInject(),
    sandboxController: SandboxController = koinInject(),
    whatsAppLifecycleManager: WhatsAppLifecycleManager = koinInject(),
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
            TelegramSection(dataRepository)
        }

        SettingsCard {
            WhatsAppSection(dataRepository, sandboxController, whatsAppLifecycleManager)
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
private fun WhatsAppSection(
    dataRepository: DataRepository,
    sandboxController: SandboxController,
    whatsAppLifecycleManager: WhatsAppLifecycleManager,
) {
    val scope = rememberCoroutineScope()
    var isEnabled by remember { mutableStateOf(dataRepository.isWhatsAppEnabled()) }
    var isReadOnly by remember { mutableStateOf(dataRepository.isWhatsAppReadOnly()) }
    val sandboxStatus by sandboxController.status.collectAsStateWithLifecycle()
    var statusMessage by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = "WhatsApp",
            description = "Talk to the AI via WhatsApp",
            checked = isEnabled,
            onCheckedChange = {
                isEnabled = it
                dataRepository.setWhatsAppEnabled(it)
                if (it) {
                    scope.launch {
                        whatsAppLifecycleManager.setupAndStart()
                    }
                } else {
                    scope.launch {
                        whatsAppLifecycleManager.stop()
                    }
                }
            },
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

            if (!sandboxStatus.ready) {
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
                            text = "Requires sandbox — enable the sandbox first in System Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                return@Column
            }

            // Live progress during install
            if (sandboxStatus.working) {
                Text(
                    text = sandboxStatus.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }

            // Bridge status
            var bridgeRunning by remember { mutableStateOf(false) }
            LaunchedEffect(isEnabled) {
                while (isEnabled) {
                    try {
                        val check = sandboxController.executeCommand(
                            command = "pgrep -f 'node.*bridge\\.js' 2>/dev/null || echo DEAD",
                            sessionId = SandboxSessions.SYSTEM,
                            useRoot = false,
                            timeoutSeconds = 5,
                        )
                        bridgeRunning = check.trim() != "DEAD"
                    } catch (_: Exception) {
                        bridgeRunning = false
                    }
                    kotlinx.coroutines.delay(5000)
                }
            }

            // QR poller — keeps the displayed QR in sync with the bridge
            LaunchedEffect(isEnabled, bridgeRunning) {
                while (isEnabled && bridgeRunning) {
                    delay(1000)
                    if (!dataRepository.isWhatsAppAuthenticated()) {
                        whatsAppLifecycleManager.refreshQrCode()
                    }
                }
            }

            // Manual bridge controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (bridgeRunning) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (bridgeRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Bridge: ${if (bridgeRunning) "Running" else "Stopped"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = {
                        scope.launch {
                            statusMessage = "Resetting..."
                            whatsAppLifecycleManager.resetBridge()
                            statusMessage = "Bridge reset done"
                        }
                    },
                    modifier = Modifier.handCursor().height(28.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Text("Reset Bridge", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(8.dp))

            // Install / Pairing Code / QR / Connected
            if (!dataRepository.isWhatsAppInstalled() && !whatsAppLifecycleManager.isConnected()) {
                Button(
                    onClick = {
                        scope.launch {
                            val installed = sandboxController.installWhatsAppBridge()
                            if (installed) {
                                dataRepository.setWhatsAppInstalled(true)
                                statusMessage = "Installed"
                                whatsAppLifecycleManager.setupAndStart()
                            } else {
                                statusMessage = "Install failed"
                            }
                        }
                    },
                    modifier = Modifier.handCursor(),
                ) {
                    Text("Install")
                }
            } else if (!dataRepository.isWhatsAppAuthenticated()) {
                // Pairing code section
                var phoneNumber by remember { mutableStateOf("") }
                var pairingCode by remember { mutableStateOf("") }
                var pairingLoading by remember { mutableStateOf(false) }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Option 1: Enter phone number for pairing code (easier):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        placeholder = { Text("e.g. 628123456789") },
                        label = { Text("Phone number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f).handCursor(),
                        enabled = !pairingLoading,
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                pairingLoading = true
                                pairingCode = ""
                                val code = whatsAppLifecycleManager.requestPairingCode(phoneNumber.trim())
                                if (code != null) {
                                    pairingCode = code
                                    statusMessage = "Pairing code generated"
                                } else {
                                    statusMessage = "Failed to get pairing code"
                                }
                                pairingLoading = false
                            }
                        },
                        modifier = Modifier.handCursor(),
                        enabled = phoneNumber.isNotBlank() && !pairingLoading,
                    ) {
                        Text(if (pairingLoading) "Requesting..." else "Request Code")
                    }
                }
                if (pairingCode.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Enter this code in WhatsApp → Linked Devices → Link a Device:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = pairingCode,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Option 2: Scan QR code with WhatsApp on your phone:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                val qrCode = dataRepository.getWhatsAppQrCode()
                if (qrCode.isNotBlank()) {
                    val qrBitmap = remember(qrCode) {
                        try {
                            decodeToImageBitmap(Base64.decode(qrCode))
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "WhatsApp QR code",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            statusMessage = "Forcing new QR..."
                            whatsAppLifecycleManager.forceRefreshQr()
                            statusMessage = "QR refreshed"
                        }
                    },
                    modifier = Modifier.handCursor(),
                ) {
                    Text("Refresh QR")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Read receipt toggle
            var readReceipt by remember { mutableStateOf(dataRepository.isWhatsAppReadReceipt()) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Send read receipts (blue ticks)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Switch(
                    checked = readReceipt,
                    onCheckedChange = {
                        readReceipt = it
                        dataRepository.setWhatsAppReadReceipt(it)
                    },
                )
            }

            Spacer(Modifier.height(8.dp))

            // Sync full history toggle
            var syncHistory by remember { mutableStateOf(dataRepository.getBaileysSyncHistory()) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Sync full history",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Switch(
                    checked = syncHistory,
                    onCheckedChange = {
                        syncHistory = it
                        dataRepository.setBaileysSyncHistory(it)
                    },
                )
            }

            Spacer(Modifier.height(8.dp))

            // Read only toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Read only (AI reads but does not reply)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Switch(
                    checked = isReadOnly,
                    onCheckedChange = {
                        isReadOnly = it
                        dataRepository.setWhatsAppReadOnly(it)
                    },
                )
            }

            if (!isReadOnly) {
                Spacer(Modifier.height(8.dp))

                // Reply mode selector
                val replyModes = listOf("all" to "Reply to all", "self" to "Reply to my messages only", "selected" to "Reply to selected contacts")
                var replyMode by remember { mutableStateOf(dataRepository.getWhatsAppReplyMode()) }
                var expanded by remember { mutableStateOf(false) }

                Text(
                    text = "Reply mode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().handCursor().clickable { expanded = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = replyModes.find { it.first == replyMode }?.second ?: replyMode,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = null,
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        replyModes.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    replyMode = value
                                    dataRepository.setWhatsAppReplyMode(value)
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                // Allowed contacts (only in "selected" mode)
                if (replyMode == "selected") {
                    Spacer(Modifier.height(8.dp))
                    var contacts by remember { mutableStateOf(dataRepository.getWhatsAppAllowedContacts()) }
                    OutlinedTextField(
                        value = contacts,
                        onValueChange = {
                            contacts = it
                            dataRepository.setWhatsAppAllowedContacts(it)
                        },
                        label = { Text("Allowed contacts (comma-separated phone numbers)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }

            if (statusMessage.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TelegramSection(dataRepository: DataRepository) {
    val scope = rememberCoroutineScope()
    var isEnabled by remember { mutableStateOf(dataRepository.isTelegramEnabled()) }
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
            onCheckedChange = {
                isEnabled = it
                dataRepository.setTelegramEnabled(it)
            },
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
