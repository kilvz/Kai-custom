@file:OptIn(ExperimentalMaterial3Api::class)

package com.kai.custom.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kai.custom.data.AppSettings
import com.kai.custom.data.BehaviorStyle
import com.kai.custom.data.CharacterType
import com.kai.custom.data.DataRepository
import com.kai.custom.data.EmailAccount
import com.kai.custom.data.EmailSyncState
import com.kai.custom.data.HeartbeatLogEntry
import com.kai.custom.data.LanguageStyle
import com.kai.custom.data.MemoryEntry
import com.kai.custom.data.PersonaConfig
import com.kai.custom.data.PersonaFormat
import com.kai.custom.data.RemotePersonaCatalog
import com.kai.custom.data.RemotePersonaEntry
import com.kai.custom.data.RenderMode
import com.kai.custom.data.ScheduledTask
import com.kai.custom.data.ServiceEntry
import com.kai.custom.data.SmsSyncState
import com.kai.custom.data.TaskTrigger
import com.kai.custom.saveFileToDevice
import com.kai.custom.ui.KaiOutlinedTextField
import com.kai.custom.ui.components.SettingsListItem
import com.kai.custom.ui.handCursor
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.settings_heartbeat_recent
import kai.composeapp.generated.resources.settings_memories
import kai.composeapp.generated.resources.settings_memories_all_title
import kai.composeapp.generated.resources.settings_memories_delete
import kai.composeapp.generated.resources.settings_memories_description
import kai.composeapp.generated.resources.settings_memories_edit_cancel
import kai.composeapp.generated.resources.settings_memories_edit_save
import kai.composeapp.generated.resources.settings_memories_edit_title
import kai.composeapp.generated.resources.settings_memories_export
import kai.composeapp.generated.resources.settings_memories_export_error
import kai.composeapp.generated.resources.settings_memories_export_success
import kai.composeapp.generated.resources.settings_memories_import
import kai.composeapp.generated.resources.settings_memories_import_error
import kai.composeapp.generated.resources.settings_memories_import_success
import kai.composeapp.generated.resources.settings_memories_show_all
import kai.composeapp.generated.resources.settings_scheduled_tasks
import kai.composeapp.generated.resources.settings_scheduled_tasks_cancel
import kai.composeapp.generated.resources.settings_scheduled_tasks_description
import kai.composeapp.generated.resources.settings_task_details_consecutive_failures
import kai.composeapp.generated.resources.settings_task_details_created
import kai.composeapp.generated.resources.settings_task_details_last_result
import kai.composeapp.generated.resources.settings_task_details_next_run
import kai.composeapp.generated.resources.settings_task_details_no_heartbeat_runs
import kai.composeapp.generated.resources.settings_task_details_no_runs
import kai.composeapp.generated.resources.settings_task_details_on_every_heartbeat
import kai.composeapp.generated.resources.settings_task_details_schedule
import kai.composeapp.generated.resources.settings_task_details_scheduled_for
import kai.composeapp.generated.resources.settings_task_details_status
import kai.composeapp.generated.resources.settings_task_details_trigger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Instant

@Composable
internal fun AgentContent(
    actions: SettingsActions,
    soulText: String,
    soulAuto: String = "",
    personaName: String,
    personas: ImmutableList<PersonaConfig>,
    activePersonaId: String,
    memories: ImmutableList<MemoryEntry>,
    isMemoryEnabled: Boolean,
    isAltMemoryEnabled: Boolean,
    altMemoryInstalled: Boolean,
    altMemoryConnected: Boolean,
    altMemoryBackend: String?,
    altMemoryEmbedder: String?,
    sandboxReady: Boolean,
    onToggleAltMemory: (Boolean) -> Unit,
    scheduledTasks: ImmutableList<ScheduledTask>,
    isSchedulingEnabled: Boolean,
    isHeartbeatEnabled: Boolean,
    heartbeatIntervalMinutes: Int,
    heartbeatActiveHoursStart: Int,
    heartbeatActiveHoursEnd: Int,
    heartbeatPrompt: String,
    heartbeatLog: ImmutableList<HeartbeatLogEntry>,
    heartbeatServiceEntries: ImmutableList<ServiceEntry>,
    heartbeatSelectedInstanceId: String?,
    isRefreshingHeartbeat: Boolean,
    showEmailToggle: Boolean,
    isEmailEnabled: Boolean,
    emailAccounts: ImmutableList<EmailAccount>,
    emailPollIntervalMinutes: Int,
    emailPendingCount: Int,
    emailSyncStates: ImmutableMap<String, EmailSyncState>,
    refreshingEmailAccountIds: ImmutableSet<String>,
    showSmsSection: Boolean,
    isSmsEnabled: Boolean,
    smsPermissionGranted: Boolean,
    smsPollIntervalMinutes: Int,
    smsPendingCount: Int,
    smsSyncState: SmsSyncState,
    isRefreshingSms: Boolean,
    isSmsSendEnabled: Boolean,
    smsSendPermissionGranted: Boolean,
    showNotificationsSection: Boolean,
    isNotificationsEnabled: Boolean,
    notificationListenerAccessGranted: Boolean,
    notificationListenerBound: Boolean,
    notificationPendingCount: Int,
    showShizukuSection: Boolean,
    isShizukuEnabled: Boolean,
    shizukuPermissionGranted: Boolean,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useStaggered = maxWidth >= 600.dp
        if (useStaggered) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SettingsCard {
                        SoulEditor(
                            soulText = soulText,
                            soulAuto = soulAuto,
                            personaName = personaName,
                            onSaveSoul = actions.onSaveSoul,
                            onChangePersonaName = actions.onChangePersonaName,
                            personas = personas,
                            activePersonaId = activePersonaId,
                            onSwitchPersona = actions.onSwitchPersona,
                            onSavePersona = actions.onSavePersona,
                            onDeletePersona = actions.onDeletePersona,
                            onCreatePersona = actions.onCreatePersona,
                        )
                    }
                    SettingsCard {
                        MemoryList(
                            memories = memories,
                            onDeleteMemory = actions.onDeleteMemory,
                            onUpdateMemory = actions.onUpdateMemory,
                            isMemoryEnabled = isMemoryEnabled,
                            isAltMemoryEnabled = isAltMemoryEnabled,
                            altMemoryInstalled = altMemoryInstalled,
                            altMemoryConnected = altMemoryConnected,
                            altMemoryBackend = altMemoryBackend,
                            altMemoryEmbedder = altMemoryEmbedder,
                            sandboxReady = sandboxReady,
                            onToggleMemory = actions.onToggleMemory,
                            onToggleAltMemory = onToggleAltMemory,
                            onExportDimension = actions.onExportDimension,
                            onImportDimension = actions.onImportDimension,
                        )
                    }
                    SettingsCard {
                        ScheduledTaskList(
                            tasks = scheduledTasks,
                            heartbeatLog = heartbeatLog,
                            onCancelTask = actions.onCancelTask,
                            isSchedulingEnabled = isSchedulingEnabled,
                            onToggleScheduling = actions.onToggleScheduling,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SettingsCard {
                        HeartbeatSection(
                            isHeartbeatEnabled = isHeartbeatEnabled,
                            heartbeatIntervalMinutes = heartbeatIntervalMinutes,
                            activeHoursStart = heartbeatActiveHoursStart,
                            activeHoursEnd = heartbeatActiveHoursEnd,
                            heartbeatPrompt = heartbeatPrompt,
                            heartbeatLog = heartbeatLog,
                            heartbeatServiceEntries = heartbeatServiceEntries,
                            heartbeatSelectedInstanceId = heartbeatSelectedInstanceId,
                            isRefreshing = isRefreshingHeartbeat,
                            onToggleHeartbeat = actions.onToggleHeartbeat,
                            onChangeInterval = actions.onChangeHeartbeatInterval,
                            onChangeActiveHours = actions.onChangeHeartbeatActiveHours,
                            onSaveHeartbeatPrompt = actions.onSaveHeartbeatPrompt,
                            onChangeHeartbeatService = actions.onChangeHeartbeatService,
                            onRefresh = actions.onRefreshHeartbeat,
                        )
                    }
                    if (showEmailToggle) {
                        SettingsCard {
                            EmailSection(
                                isEmailEnabled = isEmailEnabled,
                                emailAccounts = emailAccounts,
                                pollIntervalMinutes = emailPollIntervalMinutes,
                                pendingCount = emailPendingCount,
                                syncStates = emailSyncStates,
                                refreshingAccountIds = refreshingEmailAccountIds,
                                onToggleEmail = actions.onToggleEmail,
                                onRemoveAccount = actions.onRemoveEmailAccount,
                                onChangePollInterval = actions.onChangeEmailPollInterval,
                                onRefreshAccount = actions.onRefreshEmailAccount,
                            )
                        }
                    }
                    if (showSmsSection) {
                        SettingsCard {
                            SmsSection(
                                isSmsEnabled = isSmsEnabled,
                                permissionGranted = smsPermissionGranted,
                                pollIntervalMinutes = smsPollIntervalMinutes,
                                pendingCount = smsPendingCount,
                                syncState = smsSyncState,
                                isRefreshing = isRefreshingSms,
                                isSmsSendEnabled = isSmsSendEnabled,
                                sendPermissionGranted = smsSendPermissionGranted,
                                onToggleSms = actions.onToggleSms,
                                onChangePollInterval = actions.onChangeSmsPollInterval,
                                onRefresh = actions.onRefreshSms,
                                onToggleSmsSend = actions.onToggleSmsSend,
                            )
                        }
                    }
                    if (showNotificationsSection) {
                        SettingsCard {
                            NotificationsSection(
                                isEnabled = isNotificationsEnabled,
                                accessGranted = notificationListenerAccessGranted,
                                listenerBound = notificationListenerBound,
                                pendingCount = notificationPendingCount,
                                onToggle = actions.onToggleNotifications,
                                onOpenAccessSettings = actions.onOpenNotificationListenerSettings,
                                onClearPending = actions.onClearPendingNotifications,
                            )
                        }
                    }
                    if (showShizukuSection) {
                        SettingsCard {
                            ShizukuSection(
                                isEnabled = isShizukuEnabled,
                                permissionGranted = shizukuPermissionGranted,
                                onToggle = actions.onToggleShizuku,
                                onOpenPermission = actions.onOpenShizukuPermission,
                            )
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsCard {
                    SoulEditor(
                        soulText = soulText,
                        soulAuto = soulAuto,
                        personaName = personaName,
                        onSaveSoul = actions.onSaveSoul,
                        onChangePersonaName = actions.onChangePersonaName,
                        personas = personas,
                        activePersonaId = activePersonaId,
                        onSwitchPersona = actions.onSwitchPersona,
                        onSavePersona = actions.onSavePersona,
                        onDeletePersona = actions.onDeletePersona,
                        onCreatePersona = actions.onCreatePersona,
                    )
                }
                SettingsCard {
                    MemoryList(
                        memories = memories,
                        onDeleteMemory = actions.onDeleteMemory,
                        onUpdateMemory = actions.onUpdateMemory,
                        isMemoryEnabled = isMemoryEnabled,
                        isAltMemoryEnabled = isAltMemoryEnabled,
                        altMemoryInstalled = altMemoryInstalled,
                        altMemoryConnected = altMemoryConnected,
                        altMemoryBackend = altMemoryBackend,
                        altMemoryEmbedder = altMemoryEmbedder,
                        sandboxReady = sandboxReady,
                        onToggleMemory = actions.onToggleMemory,
                        onToggleAltMemory = onToggleAltMemory,
                        onExportDimension = actions.onExportDimension,
                        onImportDimension = actions.onImportDimension,
                    )
                }
                SettingsCard {
                    HeartbeatSection(
                        isHeartbeatEnabled = isHeartbeatEnabled,
                        heartbeatIntervalMinutes = heartbeatIntervalMinutes,
                        activeHoursStart = heartbeatActiveHoursStart,
                        activeHoursEnd = heartbeatActiveHoursEnd,
                        heartbeatPrompt = heartbeatPrompt,
                        heartbeatLog = heartbeatLog,
                        heartbeatServiceEntries = heartbeatServiceEntries,
                        heartbeatSelectedInstanceId = heartbeatSelectedInstanceId,
                        isRefreshing = isRefreshingHeartbeat,
                        onToggleHeartbeat = actions.onToggleHeartbeat,
                        onChangeInterval = actions.onChangeHeartbeatInterval,
                        onChangeActiveHours = actions.onChangeHeartbeatActiveHours,
                        onSaveHeartbeatPrompt = actions.onSaveHeartbeatPrompt,
                        onChangeHeartbeatService = actions.onChangeHeartbeatService,
                        onRefresh = actions.onRefreshHeartbeat,
                    )
                }
                if (showEmailToggle) {
                    SettingsCard {
                        EmailSection(
                            isEmailEnabled = isEmailEnabled,
                            emailAccounts = emailAccounts,
                            pollIntervalMinutes = emailPollIntervalMinutes,
                            pendingCount = emailPendingCount,
                            syncStates = emailSyncStates,
                            refreshingAccountIds = refreshingEmailAccountIds,
                            onToggleEmail = actions.onToggleEmail,
                            onRemoveAccount = actions.onRemoveEmailAccount,
                            onChangePollInterval = actions.onChangeEmailPollInterval,
                            onRefreshAccount = actions.onRefreshEmailAccount,
                        )
                    }
                }
                if (showSmsSection) {
                    SettingsCard {
                        SmsSection(
                            isSmsEnabled = isSmsEnabled,
                            permissionGranted = smsPermissionGranted,
                            pollIntervalMinutes = smsPollIntervalMinutes,
                            pendingCount = smsPendingCount,
                            syncState = smsSyncState,
                            isRefreshing = isRefreshingSms,
                            isSmsSendEnabled = isSmsSendEnabled,
                            sendPermissionGranted = smsSendPermissionGranted,
                            onToggleSms = actions.onToggleSms,
                            onChangePollInterval = actions.onChangeSmsPollInterval,
                            onRefresh = actions.onRefreshSms,
                            onToggleSmsSend = actions.onToggleSmsSend,
                        )
                    }
                }
                if (showNotificationsSection) {
                    SettingsCard {
                        NotificationsSection(
                            isEnabled = isNotificationsEnabled,
                            accessGranted = notificationListenerAccessGranted,
                            listenerBound = notificationListenerBound,
                            pendingCount = notificationPendingCount,
                            onToggle = actions.onToggleNotifications,
                            onOpenAccessSettings = actions.onOpenNotificationListenerSettings,
                            onClearPending = actions.onClearPendingNotifications,
                        )
                    }
                }
                if (showShizukuSection) {
                    SettingsCard {
                        ShizukuSection(
                            isEnabled = isShizukuEnabled,
                            permissionGranted = shizukuPermissionGranted,
                            onToggle = actions.onToggleShizuku,
                            onOpenPermission = actions.onOpenShizukuPermission,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SoulEditor(
    soulText: String,
    soulAuto: String = "",
    personaName: String,
    onSaveSoul: (String) -> Unit,
    onChangePersonaName: (String) -> Unit,
    personas: ImmutableList<PersonaConfig> = persistentListOf(),
    activePersonaId: String = "kai",
    onSwitchPersona: (String) -> Unit = {},
    onSavePersona: (PersonaConfig) -> Unit = {},
    onDeletePersona: (String) -> Unit = {},
    onCreatePersona: (String, BehaviorStyle, LanguageStyle, CharacterType) -> Unit = { _, _, _, _ -> },
) {
    val appSettings: AppSettings = koinInject()
    var editedSoul by remember(soulText) { mutableStateOf(soulText) }
    val hasChanges = editedSoul != soulText
    val maxChars = 4000
    var showResetDialog by remember { mutableStateOf(false) }
    var showPersonaSelector by remember { mutableStateOf(false) }

    val activePersona = personas.find { it.id == activePersonaId }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Active Persona ──
        Text(
            text = "Persona",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))

        // Active persona card — tappable to open selector
        OutlinedButton(
            onClick = { showPersonaSelector = true },
            modifier = Modifier
                .fillMaxWidth()
                .handCursor(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activePersona?.name.orEmpty(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (activePersona?.description?.isNotEmpty() == true) {
                    Text(
                        text = activePersona.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (activePersona != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (activePersona.languageStyle != LanguageStyle.NONE) {
                            TraitBadge(activePersona.languageStyle.displayName)
                        }
                        if (activePersona.characterType != CharacterType.NONE) {
                            TraitBadge(activePersona.characterType.displayName)
                        }
                        if (activePersona.renderMode == RenderMode.UPSTREAM_COMPAT) TraitBadge("Compat")
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Change",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ── Thinking Display ──
        val isCustomBehavior = activePersona?.behaviorStyle != BehaviorStyle.ASSISTANT &&
            activePersona?.behaviorStyle != BehaviorStyle.OPERATOR
        val effectiveHideDesc = if (isCustomBehavior) {
            "Auto-hidden (non-assistant/operator persona)"
        } else {
            "Hide the AI's chain-of-thought reasoning"
        }
        var hideThinking by remember { mutableStateOf(appSettings.getHideThinking()) }
        var expandThinking by remember { mutableStateOf(appSettings.getExpandThinking()) }
        LaunchedEffect(isCustomBehavior) {
            hideThinking = appSettings.getHideThinking()
        }
        ToggleableHeadline(
            title = "Hide Thinking",
            description = effectiveHideDesc,
            checked = hideThinking || isCustomBehavior,
            enabled = !isCustomBehavior,
            onCheckedChange = {
                hideThinking = it
                appSettings.setHideThinking(it)
            },
        )
        ToggleableHeadline(
            title = "Expand Thinking",
            description = "Always expand the thinking section by default",
            checked = expandThinking,
            enabled = !isCustomBehavior && !hideThinking,
            onCheckedChange = {
                expandThinking = it
                appSettings.setExpandThinking(it)
            },
        )
        val enterToSend = appSettings.isEnterToSend()
        ToggleableHeadline(
            title = "Enter to Send",
            description = if (enterToSend) "Enter sends message; Shift+Enter inserts newline" else "Enter inserts newline (desktop: sends without Shift)",
            checked = enterToSend,
            onCheckedChange = { appSettings.setEnterToSend(it) },
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ── Character Definition (persona defaultSoul) ──
        if (activePersona != null && !activePersona.id.startsWith("community_")) {
            val isCustom = !activePersona.isBuiltIn
            var editedDefaultSoul by remember(activePersona.id, activePersona.defaultSoul) {
                mutableStateOf(activePersona.defaultSoul)
            }
            val hasDefChanges = editedDefaultSoul != activePersona.defaultSoul
            Text(
                text = "Character Definition",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (isCustom) "Define the persona's core personality, behavior, and character." else "Built-in persona — read-only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            KaiOutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = editedDefaultSoul,
                onValueChange = { if (it.length <= maxChars) editedDefaultSoul = it },
                minLines = 6,
                maxLines = 8,
                readOnly = !isCustom,
                label = { Text("Character Definition") },
            )
            Text(
                text = "${editedDefaultSoul.length}/$maxChars",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
            if (isCustom && hasDefChanges) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        onSavePersona(activePersona.copy(defaultSoul = editedDefaultSoul.trim()))
                    },
                    modifier = Modifier.align(CenterHorizontally).handCursor(),
                ) { Text("Save Character Definition") }
            }
            // Toggle between condensed and full profile
            val fullProfileKey = "persona_full_${activePersona.id}"
            val hasFullProfile = appSettings.settings.getStringOrNull(fullProfileKey) != null
            var isFullProfileActive by remember(activePersona.id) {
                mutableStateOf(activePersona.defaultSoul.length > 10000 || appSettings.settings.getStringOrNull("profile_version_${activePersona.id}") == "full")
            }
            if (hasFullProfile) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            val fullText = appSettings.settings.getString(fullProfileKey, "")
                            if (isFullProfileActive) {
                                // Switch to condensed — restore the original condensed from the full key prefix
                                val condensed = appSettings.settings.getString("persona_condensed_${activePersona.id}", activePersona.defaultSoul)
                                onSavePersona(activePersona.copy(defaultSoul = condensed))
                                appSettings.settings.putString("profile_version_${activePersona.id}", "condensed")
                            } else {
                                // Switch to full
                                appSettings.settings.putString("persona_condensed_${activePersona.id}", activePersona.defaultSoul)
                                onSavePersona(activePersona.copy(defaultSoul = fullText))
                                appSettings.settings.putString("profile_version_${activePersona.id}", "full")
                            }
                            isFullProfileActive = !isFullProfileActive
                        },
                        modifier = Modifier.handCursor(),
                    ) {
                        Text(if (isFullProfileActive) "Switch to Condensed" else "Switch to Full Profile")
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isFullProfileActive) "Full profile active" else "Condensed active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.handCursor(),
                ) { Text("Full profile not available") }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
        }

        // ── Custom Soul (Layer 3 — user-specific) ──
        Text(
            text = "Custom Soul",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Your preferences, dislikes, and personal notes. Not character definition or behavior rules.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        KaiOutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = editedSoul,
            onValueChange = { if (it.length <= maxChars) editedSoul = it },
            minLines = 6,
            maxLines = 8,
            label = { Text("Custom Soul") },
        )
        Text(
            text = "${editedSoul.length}/$maxChars",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )
        if (hasChanges) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onSaveSoul(editedSoul.trim()) },
                modifier = Modifier.align(CenterHorizontally).handCursor(),
            ) { Text("Save Custom Soul") }
        }
        if (soulText.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.handCursor(),
            ) { Text("Reset to empty") }
        }

        // ── Behavior Notes (Layer 3 — auto-generated, read-only) ──
        if (soulAuto.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Behavior Notes",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Auto-generated behavior patterns (read-only). Updated by the system over time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = soulAuto,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Custom Soul") },
            text = { Text("Clear your custom soul text? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onSaveSoul("")
                        editedSoul = ""
                    },
                    modifier = Modifier.handCursor(),
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    modifier = Modifier.handCursor(),
                ) { Text("Cancel") }
            },
        )
    }

    if (showPersonaSelector) {
        PersonaSelectorDialog(
            personas = personas,
            activePersonaId = activePersonaId,
            onSwitchPersona = onSwitchPersona,
            onCreatePersona = onCreatePersona,
            onDeletePersona = onDeletePersona,
            onSavePersona = onSavePersona,
            onDismiss = { showPersonaSelector = false },
        )
    }
}

@Composable
private fun PersonaSelectorDialog(
    personas: ImmutableList<PersonaConfig>,
    activePersonaId: String,
    onSwitchPersona: (String) -> Unit,
    onCreatePersona: (String, BehaviorStyle, LanguageStyle, CharacterType) -> Unit,
    onDeletePersona: (String) -> Unit,
    onSavePersona: (PersonaConfig) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val categories = listOf(
        BehaviorStyle.ASSISTANT to "Assistant",
        BehaviorStyle.OPERATOR to "Operator",
        BehaviorStyle.CUSTOM to "Custom",
    )
    var selectedCategory by remember { mutableStateOf<BehaviorStyle>(BehaviorStyle.ASSISTANT) }
    var showCreateDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Persona") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Category tabs (fixed, not scrollable)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.forEach { (style, label) ->
                        val isSelected = selectedCategory == style
                        TextButton(
                            onClick = { selectedCategory = style },
                            modifier = Modifier.handCursor(),
                        ) {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Persona cards (scrollable area)
                val filtered = personas.filter { it.behaviorStyle == selectedCategory }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    filtered.forEach { p ->
                        val isActive = p.id == activePersonaId
                        OutlinedButton(
                            onClick = {
                                if (!isActive) {
                                    onSwitchPersona(p.id)
                                    onDismiss()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .handCursor(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = p.name,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                )
                                if (p.description.isNotEmpty()) {
                                    Text(
                                        text = p.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (p.languageStyle != LanguageStyle.NONE) {
                                        TraitBadge(p.languageStyle.displayName)
                                    }
                                    if (p.characterType != CharacterType.NONE) {
                                        TraitBadge(p.characterType.displayName)
                                    }
                                    if (p.renderMode == RenderMode.UPSTREAM_COMPAT) TraitBadge("Compat")
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            if (isActive) {
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                // Buttons (fixed, not scrollable)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.handCursor(),
                    ) { Text("+ Custom") }
                    var showCommunity by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { showCommunity = true },
                        modifier = Modifier.handCursor(),
                    ) { Text("Community") }
                    if (showCommunity) {
                        CommunityPersonaBrowseDialog(
                            onDismiss = { showCommunity = false },
                            onSavePersona = onSavePersona,
                            onSwitchPersona = onSwitchPersona,
                            onCloseAll = {
                                showCommunity = false
                                onDismiss()
                            },
                        )
                    }
                    val activePersona = personas.find { it.id == activePersonaId }
                    if (activePersona?.isBuiltIn == false) {
                        OutlinedButton(
                            onClick = {
                                onDeletePersona(activePersonaId)
                                onDismiss()
                            },
                            modifier = Modifier.handCursor(),
                        ) { Text("Delete") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.handCursor(),
            ) { Text("Done") }
        },
    )

    if (showCreateDialog) {
        CreatePersonaDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, style, lang, char ->
                showCreateDialog = false
                onCreatePersona(name, style, lang, char)
            },
        )
    }
}

@Composable
private fun TraitBadge(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun CreatePersonaDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, behaviorStyle: BehaviorStyle, languageStyle: LanguageStyle, characterType: CharacterType) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf(BehaviorStyle.ASSISTANT) }
    var selectedLang by remember { mutableStateOf(LanguageStyle.NONE) }
    var selectedChar by remember { mutableStateOf(CharacterType.NONE) }
    var expandedLang by remember { mutableStateOf(false) }
    var expandedChar by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Custom Persona") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                KaiOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Persona name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Category", style = MaterialTheme.typography.bodySmall)
                BehaviorStyle.entries.forEach { style ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(
                            selected = selectedStyle == style,
                            onClick = { selectedStyle = style },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(style.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Language Style", style = MaterialTheme.typography.bodySmall)
                Box {
                    OutlinedButton(
                        onClick = { expandedLang = true },
                        modifier = Modifier.fillMaxWidth().handCursor(),
                    ) { Text(selectedLang.displayName) }
                    DropdownMenu(
                        expanded = expandedLang,
                        onDismissRequest = { expandedLang = false },
                    ) {
                        LanguageStyle.entries.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang.displayName) },
                                onClick = {
                                    selectedLang = lang
                                    expandedLang = false
                                },
                            )
                        }
                    }
                }
                Text("Character Type", style = MaterialTheme.typography.bodySmall)
                Box {
                    OutlinedButton(
                        onClick = { expandedChar = true },
                        modifier = Modifier.fillMaxWidth().handCursor(),
                    ) { Text(selectedChar.displayName) }
                    DropdownMenu(
                        expanded = expandedChar,
                        onDismissRequest = { expandedChar = false },
                    ) {
                        CharacterType.entries.forEach { char ->
                            DropdownMenuItem(
                                text = { Text(char.displayName) },
                                onClick = {
                                    selectedChar = char
                                    expandedChar = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) onCreate(name.trim(), selectedStyle, selectedLang, selectedChar)
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.handCursor(),
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.handCursor(),
            ) { Text("Cancel") }
        },
    )
}

@Composable
private fun CommunityPersonaBrowseDialog(
    onDismiss: () -> Unit,
    onSavePersona: (PersonaConfig) -> Unit,
    onSwitchPersona: (String) -> Unit,
    onCloseAll: () -> Unit,
) {
    val catalog = remember { RemotePersonaCatalog() }
    val scope = rememberCoroutineScope()
    val appSettings: AppSettings = koinInject()
    val dataRepository: DataRepository = koinInject()
    var personas by remember { mutableStateOf<List<RemotePersonaEntry>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importedSuccess by remember { mutableStateOf(false) }
    var importedName by remember { mutableStateOf("") }
    var importFailed by remember { mutableStateOf(false) }
    var selectedFormat by remember { mutableStateOf(PersonaFormat.AUTO) }
    var formatExpanded by remember { mutableStateOf(false) }
    val configuredServices = remember { dataRepository.getConfiguredServiceInstances() }

    LaunchedEffect(Unit) {
        val list = catalog.listPersonas()
        if (list.isNotEmpty()) {
            personas = list
        }
        loading = false
        error = list.isEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (importing) "Importing" else "Community Personas") },
        text = {
            when {
                importing -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        if (importedSuccess) {
                            Text("Imported: $importedName")
                            Spacer(Modifier.height(8.dp))
                            Text("Persona added and activated.", style = MaterialTheme.typography.bodySmall)
                        } else if (importFailed) {
                            Text("Failed to download persona.")
                        } else {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Downloading persona...")
                        }
                    }
                }

                loading -> Text("Loading...")

                error -> Text("Could not load community personas.")

                personas.isNullOrEmpty() -> Text("No personas available.")

                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "From kilvz/personas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        // Format selector
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Format: ", style = MaterialTheme.typography.bodySmall)
                            Box {
                                OutlinedButton(
                                    onClick = { formatExpanded = true },
                                    modifier = Modifier.handCursor(),
                                ) { Text(selectedFormat.displayName, maxLines = 1) }
                                DropdownMenu(
                                    expanded = formatExpanded,
                                    onDismissRequest = { formatExpanded = false },
                                ) {
                                    PersonaFormat.entries.forEach { fmt ->
                                        DropdownMenuItem(
                                            text = { Text(fmt.displayName) },
                                            onClick = {
                                                selectedFormat = fmt
                                                formatExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val list = personas ?: emptyList()
                        list.forEach { entry ->
                            var downloading by remember { mutableStateOf(false) }
                            OutlinedButton(
                                onClick = {
                                    downloading = true
                                    importing = true
                                    importFailed = false
                                    importedSuccess = false
                                    scope.launch {
                                        val config = catalog.downloadPersona(entry.id, selectedFormat, configuredServices)
                                        if (config != null) {
                                            onSavePersona(config)
                                            onSwitchPersona(config.id)
                                            importedName = config.name
                                            importedSuccess = true
                                        } else {
                                            importFailed = true
                                        }
                                        downloading = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().handCursor(),
                                enabled = !downloading,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                                if (downloading) {
                                    Text("Downloading...")
                                } else {
                                    Text("Import")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (importing && (importedSuccess || importFailed)) onCloseAll() else onDismiss() },
                modifier = Modifier.handCursor(),
                enabled = !importing || importedSuccess || importFailed,
            ) { Text("Done") }
        },
    )
}

@Composable
private fun MemoryList(
    memories: ImmutableList<MemoryEntry>,
    onDeleteMemory: (String) -> Unit,
    onUpdateMemory: (String, String) -> Unit,
    isMemoryEnabled: Boolean,
    isAltMemoryEnabled: Boolean,
    altMemoryInstalled: Boolean,
    altMemoryConnected: Boolean,
    altMemoryBackend: String?,
    altMemoryEmbedder: String?,
    sandboxReady: Boolean,
    onToggleMemory: (Boolean) -> Unit,
    onToggleAltMemory: (Boolean) -> Unit,
    onExportDimension: suspend () -> ByteArray,
    onImportDimension: (ByteArray) -> Unit,
) {
    var showAllDialog by remember { mutableStateOf(false) }
    var showMemoryManager by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryEntry?>(null) }
    var importResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val sortedMemories = remember(memories) {
        memories.sortedByDescending { it.updatedAt }.toImmutableList()
    }
    val previewMemories = remember(sortedMemories) { sortedMemories.take(5).toImmutableList() }

    val filePickerLauncher = rememberFilePickerLauncher(
        type = FileKitType.File(extensions = listOf("kai-dimension")),
    ) { file ->
        if (file != null) {
            scope.launch {
                try {
                    onImportDimension(file.readBytes())
                    importResult = "import_success"
                } catch (_: Exception) {
                    importResult = "import_error"
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_memories),
            description = stringResource(Res.string.settings_memories_description),
            checked = isMemoryEnabled,
            onCheckedChange = onToggleMemory,
        )
        Spacer(Modifier.height(4.dp))
        ToggleableHeadline(
            title = "Alt-memory",
            description = when {
                !sandboxReady -> "Requires sandbox to be installed first"
                !altMemoryInstalled -> "Not installed — tap to install"
                isAltMemoryEnabled && altMemoryConnected -> "Connected"
                isAltMemoryEnabled -> "Connecting..."
                else -> "Vector memory with semantic search"
            },
            checked = isAltMemoryEnabled,
            enabled = sandboxReady,
            onCheckedChange = onToggleAltMemory,
        )
        if (altMemoryConnected && (altMemoryBackend != null || altMemoryEmbedder != null)) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildList {
                    altMemoryBackend?.let { add("Backend: $it") }
                    altMemoryEmbedder?.let { add("Embedder: $it") }
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp),
            )
            if (altMemoryEmbedder != null && !isKnownSemanticEmbedder(altMemoryEmbedder)) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Ask your AI to set the default embedder to the best model for semantic search quality.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (isMemoryEnabled) {
            previewMemories.forEach { memory ->
                SettingsListItem(
                    title = memory.key,
                    subtitle = memory.content,
                    onDelete = { onDeleteMemory(memory.key) },
                    deleteContentDescription = stringResource(Res.string.settings_memories_delete),
                    subtitleMaxLines = 3,
                    onClick = { editingMemory = memory },
                )
                Spacer(Modifier.height(8.dp))
            }
            if (sortedMemories.size > previewMemories.size) {
                OutlinedButton(
                    onClick = { showAllDialog = true },
                    modifier = Modifier.align(CenterHorizontally).handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_memories_show_all, sortedMemories.size))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                val data = onExportDimension()
                                saveFileToDevice(data, "kai-memories", "kai-dimension")
                                importResult = "export_success"
                            } catch (_: Exception) {
                                importResult = "export_error"
                            }
                        }
                    },
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_memories_export))
                }
                OutlinedButton(
                    onClick = { filePickerLauncher.launch() },
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_memories_import))
                }
                OutlinedButton(
                    onClick = { showMemoryManager = true },
                    modifier = Modifier.handCursor(),
                ) {
                    Text("Manage")
                }
            }
            importResult?.let { result ->
                Spacer(Modifier.height(4.dp))
                val text = when (result) {
                    "export_success" -> stringResource(Res.string.settings_memories_export_success)
                    "import_success" -> stringResource(Res.string.settings_memories_import_success)
                    "import_error" -> stringResource(Res.string.settings_memories_import_error)
                    "export_error" -> stringResource(Res.string.settings_memories_export_error)
                    else -> null
                }
                if (text != null) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    if (showAllDialog) {
        AllMemoriesSheet(
            memories = sortedMemories,
            onDismiss = { showAllDialog = false },
            onDeleteMemory = onDeleteMemory,
            onEditMemory = { editingMemory = it },
        )
    }

    editingMemory?.let { memory ->
        EditMemorySheet(
            memory = memory,
            onDismiss = { editingMemory = null },
            onSave = { newContent ->
                onUpdateMemory(memory.key, newContent)
                editingMemory = null
            },
        )
    }

    if (showMemoryManager) {
        MemoryManagementSheet(
            onDismiss = { showMemoryManager = false },
            onDeleteMemory = onDeleteMemory,
        )
    }
}

@Composable
private fun AllMemoriesSheet(
    memories: ImmutableList<MemoryEntry>,
    onDismiss: () -> Unit,
    onDeleteMemory: (String) -> Unit,
    onEditMemory: (MemoryEntry) -> Unit,
) {
    val deleteContentDescription = stringResource(Res.string.settings_memories_delete)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_memories_all_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            memories.forEach { memory ->
                SettingsListItem(
                    title = memory.key,
                    subtitle = memory.content,
                    onDelete = { onDeleteMemory(memory.key) },
                    deleteContentDescription = deleteContentDescription,
                    subtitleMaxLines = 3,
                    onClick = { onEditMemory(memory) },
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EditMemorySheet(
    memory: MemoryEntry,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var content by remember(memory.key) { mutableStateOf(memory.content) }
    val hasChanges = content != memory.content && content.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_memories_edit_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = memory.key,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            KaiOutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = content,
                onValueChange = { content = it },
                minLines = 4,
                maxLines = 10,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_memories_edit_cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { onSave(content.trim()) },
                    enabled = hasChanges,
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_memories_edit_save))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ScheduledTaskList(
    tasks: ImmutableList<ScheduledTask>,
    heartbeatLog: ImmutableList<HeartbeatLogEntry>,
    onCancelTask: (String) -> Unit,
    isSchedulingEnabled: Boolean,
    onToggleScheduling: (Boolean) -> Unit,
) {
    var selectedTaskId by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_scheduled_tasks),
            description = stringResource(Res.string.settings_scheduled_tasks_description),
            checked = isSchedulingEnabled,
            onCheckedChange = onToggleScheduling,
        )
        Spacer(Modifier.height(12.dp))

        val onEveryHeartbeat = stringResource(Res.string.settings_task_details_on_every_heartbeat)
        if (isSchedulingEnabled && tasks.isNotEmpty()) {
            tasks.forEach { task ->
                val subtitle = when (task.trigger) {
                    TaskTrigger.HEARTBEAT -> "${task.status} - $onEveryHeartbeat"

                    TaskTrigger.CRON -> "${task.status} - ${task.cron?.let { describeCron(it) } ?: "cron"}"

                    TaskTrigger.TIME -> {
                        val instant = Instant.fromEpochMilliseconds(task.scheduledAtEpochMs)
                        val zone = TimeZone.currentSystemDefault()
                        val scheduledTime = instant.toLocalDateTime(zone)
                        val offset = zone.offsetAt(instant)
                        "${task.status} - $scheduledTime $offset"
                    }
                }
                SettingsListItem(
                    title = task.description,
                    subtitle = subtitle,
                    onClick = { selectedTaskId = task.id },
                    onDelete = { onCancelTask(task.id) },
                    deleteContentDescription = stringResource(Res.string.settings_scheduled_tasks_cancel),
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    val selectedTask = selectedTaskId?.let { id -> tasks.firstOrNull { it.id == id } }
    if (selectedTask != null) {
        TaskDetailsSheet(
            task = selectedTask,
            heartbeatLog = heartbeatLog,
            onDismiss = { selectedTaskId = null },
        )
    }
}

@Composable
private fun TaskDetailsSheet(
    task: ScheduledTask,
    heartbeatLog: ImmutableList<HeartbeatLogEntry>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = task.description,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))

            TaskDetailRow(
                label = stringResource(Res.string.settings_task_details_trigger),
                value = task.trigger.name,
            )
            TaskDetailRow(
                label = stringResource(Res.string.settings_task_details_status),
                value = task.status.name,
            )
            when (task.trigger) {
                TaskTrigger.TIME -> TaskDetailRow(
                    label = stringResource(Res.string.settings_task_details_scheduled_for),
                    value = formatTaskInstant(task.scheduledAtEpochMs),
                )

                TaskTrigger.CRON -> {
                    TaskDetailRow(
                        label = stringResource(Res.string.settings_task_details_schedule),
                        value = task.cron?.let { describeCron(it) } ?: "cron",
                    )
                    TaskDetailRow(
                        label = stringResource(Res.string.settings_task_details_next_run),
                        value = formatTaskInstant(task.scheduledAtEpochMs),
                    )
                }

                TaskTrigger.HEARTBEAT -> TaskDetailRow(
                    label = stringResource(Res.string.settings_task_details_schedule),
                    value = stringResource(Res.string.settings_task_details_on_every_heartbeat),
                )
            }
            TaskDetailRow(
                label = stringResource(Res.string.settings_task_details_created),
                value = formatTaskInstant(task.createdAtEpochMs),
            )
            if (task.consecutiveFailures > 0) {
                TaskDetailRow(
                    label = stringResource(Res.string.settings_task_details_consecutive_failures),
                    value = task.consecutiveFailures.toString(),
                )
            }
            // The scheduler stores its retry/backoff phrasing in `lastResult` ("Failed at ...:
            // ... (retry after 120s backoff)"). Surface it so the user can see what the
            // scheduler is going to do next, not just what already happened.
            task.lastResult?.takeIf { it.isNotBlank() }?.let { result ->
                TaskDetailRow(
                    label = stringResource(Res.string.settings_task_details_last_result),
                    value = result,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.settings_heartbeat_recent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))

            if (task.trigger == TaskTrigger.HEARTBEAT) {
                // Heartbeat additions don't carry their own log — they fire as part of every
                // heartbeat run, so the heartbeat-wide log is the right surface.
                if (heartbeatLog.isEmpty()) {
                    EmptyLogText(stringResource(Res.string.settings_task_details_no_heartbeat_runs))
                } else {
                    heartbeatLog.forEach { entry ->
                        ExecutionLogRow(
                            success = entry.success,
                            timestampEpochMs = entry.timestampEpochMs,
                            message = entry.error,
                        )
                    }
                }
            } else {
                if (task.recentExecutions.isEmpty()) {
                    EmptyLogText(stringResource(Res.string.settings_task_details_no_runs))
                } else {
                    task.recentExecutions.forEach { entry ->
                        ExecutionLogRow(
                            success = entry.success,
                            timestampEpochMs = entry.timestampEpochMs,
                            message = entry.message,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TaskDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ExecutionLogRow(success: Boolean, timestampEpochMs: Long, message: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = if (success) "OK" else "FAIL",
            style = MaterialTheme.typography.labelSmall,
            color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.width(36.dp),
        )
        Column {
            Text(
                text = formatTaskInstant(timestampEpochMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (success) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyLogText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatTaskInstant(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    val instant = Instant.fromEpochMilliseconds(epochMs)
    val zone = TimeZone.currentSystemDefault()
    val local = instant.toLocalDateTime(zone)
    val month = local.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val minute = local.minute.toString().padStart(2, '0')
    return "${local.day} $month ${local.year} ${local.hour}:$minute"
}

private val nonSemanticEmbedders = setOf("numpy", "spacy")

private fun isKnownSemanticEmbedder(name: String): Boolean = name.lowercase() !in nonSemanticEmbedders
