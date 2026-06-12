package com.kai.custom.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.kai.custom.CalendarAccount
import com.kai.custom.SandboxController
import com.kai.custom.SandboxSessions
import com.kai.custom.data.DataRepository
import com.kai.custom.decodeToImageBitmap
import com.kai.custom.listCalendarAccounts
import com.kai.custom.ui.handCursor
import com.kai.custom.whatsapp.WhatsAppLifecycleManager
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.integrations_calendar_auto
import kai.composeapp.generated.resources.integrations_calendar_description
import kai.composeapp.generated.resources.integrations_calendar_label
import kai.composeapp.generated.resources.integrations_calendar_title
import kai.composeapp.generated.resources.integrations_telegram_authorized_ids
import kai.composeapp.generated.resources.integrations_telegram_authorized_ids_hint
import kai.composeapp.generated.resources.integrations_telegram_bot_token
import kai.composeapp.generated.resources.integrations_telegram_bot_token_hint
import kai.composeapp.generated.resources.integrations_telegram_description
import kai.composeapp.generated.resources.integrations_telegram_experimental
import kai.composeapp.generated.resources.integrations_telegram_experimental_warning
import kai.composeapp.generated.resources.integrations_telegram_hide
import kai.composeapp.generated.resources.integrations_telegram_ids_placeholder
import kai.composeapp.generated.resources.integrations_telegram_ids_saved
import kai.composeapp.generated.resources.integrations_telegram_last_error
import kai.composeapp.generated.resources.integrations_telegram_polled
import kai.composeapp.generated.resources.integrations_telegram_save
import kai.composeapp.generated.resources.integrations_telegram_show
import kai.composeapp.generated.resources.integrations_telegram_test_connection
import kai.composeapp.generated.resources.integrations_telegram_title
import kai.composeapp.generated.resources.integrations_telegram_token_placeholder
import kai.composeapp.generated.resources.integrations_telegram_token_saved
import kai.composeapp.generated.resources.integrations_whatsapp_allowed_contacts
import kai.composeapp.generated.resources.integrations_whatsapp_bridge_reset_done
import kai.composeapp.generated.resources.integrations_whatsapp_bridge_running
import kai.composeapp.generated.resources.integrations_whatsapp_bridge_status
import kai.composeapp.generated.resources.integrations_whatsapp_bridge_stopped
import kai.composeapp.generated.resources.integrations_whatsapp_connected
import kai.composeapp.generated.resources.integrations_whatsapp_description
import kai.composeapp.generated.resources.integrations_whatsapp_experimental
import kai.composeapp.generated.resources.integrations_whatsapp_experimental_warning
import kai.composeapp.generated.resources.integrations_whatsapp_forcing_qr
import kai.composeapp.generated.resources.integrations_whatsapp_install
import kai.composeapp.generated.resources.integrations_whatsapp_install_failed
import kai.composeapp.generated.resources.integrations_whatsapp_installed
import kai.composeapp.generated.resources.integrations_whatsapp_option_1
import kai.composeapp.generated.resources.integrations_whatsapp_option_2
import kai.composeapp.generated.resources.integrations_whatsapp_pairing_code_generated
import kai.composeapp.generated.resources.integrations_whatsapp_pairing_failed
import kai.composeapp.generated.resources.integrations_whatsapp_pairing_instructions
import kai.composeapp.generated.resources.integrations_whatsapp_phone_label
import kai.composeapp.generated.resources.integrations_whatsapp_phone_placeholder
import kai.composeapp.generated.resources.integrations_whatsapp_qr_description
import kai.composeapp.generated.resources.integrations_whatsapp_qr_refreshed
import kai.composeapp.generated.resources.integrations_whatsapp_read_only
import kai.composeapp.generated.resources.integrations_whatsapp_read_receipts
import kai.composeapp.generated.resources.integrations_whatsapp_refresh_qr
import kai.composeapp.generated.resources.integrations_whatsapp_reply_mode
import kai.composeapp.generated.resources.integrations_whatsapp_reply_mode_all
import kai.composeapp.generated.resources.integrations_whatsapp_reply_mode_selected
import kai.composeapp.generated.resources.integrations_whatsapp_reply_mode_self
import kai.composeapp.generated.resources.integrations_whatsapp_request_code
import kai.composeapp.generated.resources.integrations_whatsapp_requesting
import kai.composeapp.generated.resources.integrations_whatsapp_requires_sandbox
import kai.composeapp.generated.resources.integrations_whatsapp_reset_bridge
import kai.composeapp.generated.resources.integrations_whatsapp_resetting
import kai.composeapp.generated.resources.integrations_whatsapp_sync_history
import kai.composeapp.generated.resources.integrations_whatsapp_title
import kai.composeapp.generated.resources.settings_open_github_issue
import kai.composeapp.generated.resources.settings_request_integration_description
import kai.composeapp.generated.resources.settings_request_integration_title
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.io.encoding.Base64

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
            CalendarSection(dataRepository)
        }

        SettingsCard {
            WhatsAppSection(dataRepository, sandboxController, whatsAppLifecycleManager)
        }

        SettingsCard {
            ClawHubSearchSection(dataRepository)
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
private fun CalendarSection(dataRepository: DataRepository) {
    var accounts by remember { mutableStateOf(emptyList<CalendarAccount>()) }
    var selectedId by remember { mutableStateOf(dataRepository.getDefaultCalendarId()) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        accounts = try {
            listCalendarAccounts()
        } catch (_: SecurityException) {
            emptyList()
        }
        if (accounts.none { it.id == selectedId }) selectedId = -1L
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.integrations_calendar_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.integrations_calendar_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
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
                        text = if (selectedId > 0) {
                            accounts.find { it.id == selectedId }?.displayName ?: stringResource(Res.string.integrations_calendar_label, selectedId)
                        } else {
                            stringResource(Res.string.integrations_calendar_auto)
                        },
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
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.integrations_calendar_auto)) },
                    onClick = {
                        selectedId = -1L
                        dataRepository.setDefaultCalendarId(-1L)
                        expanded = false
                    },
                )
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.displayName) },
                        onClick = {
                            selectedId = account.id
                            dataRepository.setDefaultCalendarId(account.id)
                            expanded = false
                        },
                    )
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
            title = stringResource(Res.string.integrations_whatsapp_title),
            description = stringResource(Res.string.integrations_whatsapp_description),
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
                        text = stringResource(Res.string.integrations_whatsapp_experimental),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
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
                        text = stringResource(Res.string.integrations_whatsapp_experimental_warning),
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
                            text = stringResource(Res.string.integrations_whatsapp_requires_sandbox),
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
            // Default 20s matches Baileys QR refresh cycle.
            // Goes aggressive (1s) only after user presses Refresh QR, until QR appears.
            var qrUrgent by remember { mutableStateOf(false) }
            LaunchedEffect(isEnabled, bridgeRunning, qrUrgent) {
                while (isEnabled && bridgeRunning) {
                    delay(if (qrUrgent) 1000L else 20_000L)
                    if (!dataRepository.isWhatsAppAuthenticated()) {
                        whatsAppLifecycleManager.refreshQrCode()
                        if (qrUrgent && dataRepository.getWhatsAppQrCode().isNotBlank()) {
                            qrUrgent = false
                        }
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
                    val bridgeStatusText = if (bridgeRunning) stringResource(Res.string.integrations_whatsapp_bridge_running) else stringResource(Res.string.integrations_whatsapp_bridge_stopped)
                    Text(
                        text = stringResource(Res.string.integrations_whatsapp_bridge_status, bridgeStatusText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                    Button(
                        onClick = {
                            scope.launch {
                                statusMessage = getString(Res.string.integrations_whatsapp_resetting)
                                whatsAppLifecycleManager.resetBridge()
                                statusMessage = getString(Res.string.integrations_whatsapp_bridge_reset_done)
                            }
                        },
                        modifier = Modifier.handCursor().height(28.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    ) {
                        Text(stringResource(Res.string.integrations_whatsapp_reset_bridge), style = MaterialTheme.typography.labelSmall)
                    }
            }
            Spacer(Modifier.height(8.dp))

            // Install / Pairing Code / QR / Connected
            if (!dataRepository.isWhatsAppInstalled() && !whatsAppLifecycleManager.isConnected()) {
                val installedStr = stringResource(Res.string.integrations_whatsapp_installed)
                val installFailedStr = stringResource(Res.string.integrations_whatsapp_install_failed)
                Button(
                    onClick = {
                        scope.launch {
                            val installed = sandboxController.installWhatsAppBridge()
                            if (installed) {
                                dataRepository.setWhatsAppInstalled(true)
                                statusMessage = installedStr
                                whatsAppLifecycleManager.setupAndStart()
                            } else {
                                statusMessage = installFailedStr
                            }
                        }
                    },
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.integrations_whatsapp_install))
                }
            } else if (!dataRepository.isWhatsAppAuthenticated()) {
                // Pairing code section
                var phoneNumber by remember { mutableStateOf("") }
                var pairingCode by remember { mutableStateOf("") }
                var pairingLoading by remember { mutableStateOf(false) }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.integrations_whatsapp_option_1),
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
                        placeholder = { Text(stringResource(Res.string.integrations_whatsapp_phone_placeholder)) },
                        label = { Text(stringResource(Res.string.integrations_whatsapp_phone_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f).handCursor(),
                        enabled = !pairingLoading,
                    )
                    Spacer(Modifier.width(8.dp))
                    val pairingGeneratedStr = stringResource(Res.string.integrations_whatsapp_pairing_code_generated)
                    val pairingFailedStr = stringResource(Res.string.integrations_whatsapp_pairing_failed)
                    val requestingStr = stringResource(Res.string.integrations_whatsapp_requesting)
                    val requestCodeStr = stringResource(Res.string.integrations_whatsapp_request_code)
                    Button(
                        onClick = {
                            scope.launch {
                                pairingLoading = true
                                pairingCode = ""
                                val code = whatsAppLifecycleManager.requestPairingCode(phoneNumber.trim())
                                if (!code.isNullOrBlank()) {
                                    pairingCode = code
                                    statusMessage = pairingGeneratedStr
                                } else {
                                    statusMessage = pairingFailedStr
                                }
                                pairingLoading = false
                            }
                        },
                        modifier = Modifier.handCursor(),
                        enabled = phoneNumber.isNotBlank() && !pairingLoading,
                    ) {
                        Text(if (pairingLoading) requestingStr else requestCodeStr)
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
                                text = stringResource(Res.string.integrations_whatsapp_pairing_instructions),
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
                    text = stringResource(Res.string.integrations_whatsapp_option_2),
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
                            contentDescription = stringResource(Res.string.integrations_whatsapp_qr_description),
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
                val forcingQrStr = stringResource(Res.string.integrations_whatsapp_forcing_qr)
                val qrRefreshedStr = stringResource(Res.string.integrations_whatsapp_qr_refreshed)
                OutlinedButton(
                    onClick = {
                        qrUrgent = true
                        scope.launch {
                            statusMessage = forcingQrStr
                            whatsAppLifecycleManager.forceRefreshQr()
                            statusMessage = qrRefreshedStr
                        }
                    },
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.integrations_whatsapp_refresh_qr))
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
                        text = stringResource(Res.string.integrations_whatsapp_connected),
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
                    text = stringResource(Res.string.integrations_whatsapp_read_receipts),
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
                    text = stringResource(Res.string.integrations_whatsapp_sync_history),
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
                    text = stringResource(Res.string.integrations_whatsapp_read_only),
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
                val replyModes = listOf(
                    "all" to stringResource(Res.string.integrations_whatsapp_reply_mode_all),
                    "self" to stringResource(Res.string.integrations_whatsapp_reply_mode_self),
                    "selected" to stringResource(Res.string.integrations_whatsapp_reply_mode_selected),
                )
                var replyMode by remember { mutableStateOf(dataRepository.getWhatsAppReplyMode()) }
                var expanded by remember { mutableStateOf(false) }

                Text(
                    text = stringResource(Res.string.integrations_whatsapp_reply_mode),
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
                        label = { Text(stringResource(Res.string.integrations_whatsapp_allowed_contacts)) },
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
            title = stringResource(Res.string.integrations_telegram_title),
            description = stringResource(Res.string.integrations_telegram_description),
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
                        text = stringResource(Res.string.integrations_telegram_experimental),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
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
                        text = stringResource(Res.string.integrations_telegram_experimental_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(Res.string.integrations_telegram_bot_token),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(Res.string.integrations_telegram_bot_token_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val tokenSavedStr = stringResource(Res.string.integrations_telegram_token_saved)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = botToken,
                onValueChange = { botToken = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(Res.string.integrations_telegram_token_placeholder)) },
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    dataRepository.setTelegramBotToken(botToken)
                    statusMessage = tokenSavedStr
                }),
            )
            Row {
                OutlinedButton(
                    onClick = { showToken = !showToken },
                ) {
                    Text(if (showToken) stringResource(Res.string.integrations_telegram_hide) else stringResource(Res.string.integrations_telegram_show))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        dataRepository.setTelegramBotToken(botToken)
                        statusMessage = tokenSavedStr
                    },
                ) {
                    Text(stringResource(Res.string.integrations_telegram_save))
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(Res.string.integrations_telegram_authorized_ids),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(Res.string.integrations_telegram_authorized_ids_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val idsSavedStr = stringResource(Res.string.integrations_telegram_ids_saved)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = authorizedIds,
                onValueChange = { authorizedIds = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(Res.string.integrations_telegram_ids_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val ids = authorizedIds.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
                    dataRepository.setTelegramAuthorizedChatIds(ids)
                    statusMessage = idsSavedStr
                }),
            )
            OutlinedButton(
                onClick = {
                    val ids = authorizedIds.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
                    dataRepository.setTelegramAuthorizedChatIds(ids)
                    statusMessage = idsSavedStr
                },
            ) {
                Text(stringResource(Res.string.integrations_telegram_save))
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val polledStr = stringResource(Res.string.integrations_telegram_polled)
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            dataRepository.pollTelegram()
                            statusMessage = polledStr
                        }
                    },
                ) {
                    Text(stringResource(Res.string.integrations_telegram_test_connection))
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
                    text = stringResource(Res.string.integrations_telegram_last_error, syncState.value.lastError.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
