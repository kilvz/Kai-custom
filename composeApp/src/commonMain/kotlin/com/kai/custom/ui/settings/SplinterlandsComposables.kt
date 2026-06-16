package com.kai.custom.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kai.custom.splinterlands.BattleLogEntry
import com.kai.custom.splinterlands.BattlePhase
import com.kai.custom.splinterlands.LlmServiceStatus
import com.kai.custom.splinterlands.ModelStats
import com.kai.custom.splinterlands.computeModelStats
import com.kai.custom.ui.KaiOutlinedTextField
import com.kai.custom.ui.components.VerticalScrollbarForScroll
import com.kai.custom.ui.handCursor
import com.kai.custom.ui.icons.DragIndicator
import com.kai.custom.ui.icons.Visibility
import com.kai.custom.ui.icons.VisibilityOff
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.splinterlands_add_account
import kai.composeapp.generated.resources.splinterlands_add_another_service
import kai.composeapp.generated.resources.splinterlands_add_service
import kai.composeapp.generated.resources.splinterlands_auto_label
import kai.composeapp.generated.resources.splinterlands_battle_activity
import kai.composeapp.generated.resources.splinterlands_cancel
import kai.composeapp.generated.resources.splinterlands_checking_energy
import kai.composeapp.generated.resources.splinterlands_clear
import kai.composeapp.generated.resources.splinterlands_close
import kai.composeapp.generated.resources.splinterlands_defeat
import kai.composeapp.generated.resources.splinterlands_description
import kai.composeapp.generated.resources.splinterlands_error
import kai.composeapp.generated.resources.splinterlands_fetching_cards
import kai.composeapp.generated.resources.splinterlands_finding_match
import kai.composeapp.generated.resources.splinterlands_finished
import kai.composeapp.generated.resources.splinterlands_hide
import kai.composeapp.generated.resources.splinterlands_hive_username
import kai.composeapp.generated.resources.splinterlands_llm_label
import kai.composeapp.generated.resources.splinterlands_llm_services
import kai.composeapp.generated.resources.splinterlands_logging_in
import kai.composeapp.generated.resources.splinterlands_mana
import kai.composeapp.generated.resources.splinterlands_model_rankings
import kai.composeapp.generated.resources.splinterlands_none_label
import kai.composeapp.generated.resources.splinterlands_picking_team
import kai.composeapp.generated.resources.splinterlands_posting_key
import kai.composeapp.generated.resources.splinterlands_recent_battles
import kai.composeapp.generated.resources.splinterlands_remove
import kai.composeapp.generated.resources.splinterlands_remove_account
import kai.composeapp.generated.resources.splinterlands_remove_confirm
import kai.composeapp.generated.resources.splinterlands_reorder
import kai.composeapp.generated.resources.splinterlands_secure_description
import kai.composeapp.generated.resources.splinterlands_show
import kai.composeapp.generated.resources.splinterlands_show_more
import kai.composeapp.generated.resources.splinterlands_start
import kai.composeapp.generated.resources.splinterlands_stop
import kai.composeapp.generated.resources.splinterlands_stopping
import kai.composeapp.generated.resources.splinterlands_submitting_team
import kai.composeapp.generated.resources.splinterlands_test_and_add
import kai.composeapp.generated.resources.splinterlands_testing
import kai.composeapp.generated.resources.splinterlands_time_1_day
import kai.composeapp.generated.resources.splinterlands_time_1_hour
import kai.composeapp.generated.resources.splinterlands_time_1_min
import kai.composeapp.generated.resources.splinterlands_time_just_now
import kai.composeapp.generated.resources.splinterlands_time_n_days
import kai.composeapp.generated.resources.splinterlands_time_n_hours
import kai.composeapp.generated.resources.splinterlands_time_n_min
import kai.composeapp.generated.resources.splinterlands_title
import kai.composeapp.generated.resources.splinterlands_victory
import kai.composeapp.generated.resources.splinterlands_view_battle
import kai.composeapp.generated.resources.splinterlands_vs
import kai.composeapp.generated.resources.splinterlands_waiting_for_opponent
import kai.composeapp.generated.resources.splinterlands_waiting_for_result
import kai.composeapp.generated.resources.splinterlands_wins_losses
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableColumn
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun SplinterlandsSection(
    isEnabled: Boolean,
    accounts: ImmutableList<SplinterlandsAccountUiState>,
    instanceIds: ImmutableList<String>,
    addStatus: SplinterlandsAddStatus,
    battleLog: ImmutableList<BattleLogEntry>,
    availableServices: ImmutableList<com.kai.custom.data.ServiceEntry>,
    onToggle: (Boolean) -> Unit,
    onTestAndAddAccount: (String, String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onAddService: (String) -> Unit,
    onRemoveService: (String) -> Unit,
    onReorderServices: (List<String>) -> Unit,
    onStartBattle: (String) -> Unit,
    onStopBattle: (String) -> Unit,
    onClearBattleLog: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.splinterlands_title),
            description = stringResource(Res.string.splinterlands_description),
            checked = isEnabled,
            onCheckedChange = onToggle,
        )

        if (isEnabled) {
            Spacer(Modifier.height(12.dp))

            // Multi-service list
            SplinterlandsServiceList(
                instanceIds = instanceIds,
                availableServices = availableServices,
                onAddService = onAddService,
                onRemoveService = onRemoveService,
                onReorderServices = onReorderServices,
            )

            // Model Rankings (below services)
            val modelStats = remember(battleLog) { computeModelStats(battleLog).toImmutableList() }
            if (modelStats.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SplinterlandsModelRankings(modelStats)
            }

            // Account list
            if (accounts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                for (account in accounts) {
                    SplinterlandsAccountRow(
                        account = account,
                        hasServices = instanceIds.isNotEmpty(),
                        onRemove = { onRemoveAccount(account.accountId) },
                        onStartBattle = { onStartBattle(account.accountId) },
                        onStopBattle = { onStopBattle(account.accountId) },
                    )
                }
            }

            // Battle log
            if (battleLog.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.splinterlands_recent_battles),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onClearBattleLog,
                        modifier = Modifier.handCursor(),
                    ) {
                        Text(stringResource(Res.string.splinterlands_clear), style = MaterialTheme.typography.labelSmall)
                    }
                }
                var showAll by remember { mutableStateOf(false) }
                val visibleEntries = if (showAll) battleLog else battleLog.take(5)
                for (entry in visibleEntries) {
                    SplinterlandsBattleLogRow(entry)
                }
                if (battleLog.size > 5 && !showAll) {
                    TextButton(
                        onClick = { showAll = true },
                        modifier = Modifier.handCursor(),
                    ) {
                        Text(stringResource(Res.string.splinterlands_show_more, battleLog.size - 5))
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(12.dp))

            // Add account form
            SplinterlandsAddAccountForm(
                addStatus = addStatus,
                onTestAndAdd = onTestAndAddAccount,
            )
        }
    }
}

@Composable
private fun SplinterlandsServiceList(
    instanceIds: ImmutableList<String>,
    availableServices: ImmutableList<com.kai.custom.data.ServiceEntry>,
    onAddService: (String) -> Unit,
    onRemoveService: (String) -> Unit,
    onReorderServices: (List<String>) -> Unit,
) {
    val serviceMap = remember(availableServices) { availableServices.associateBy { it.instanceId } }

    if (instanceIds.isNotEmpty()) {
        Text(
            text = stringResource(Res.string.splinterlands_llm_services),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        ReorderableColumn(
            list = instanceIds,
            onSettle = { fromIndex, toIndex ->
                val reordered = instanceIds.toMutableList()
                reordered.add(toIndex, reordered.removeAt(fromIndex))
                onReorderServices(reordered)
            },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) { index, id, _ ->
            key(id) {
                ReorderableItem {
                    val entry = serviceMap[id]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Drag handle
                        if (instanceIds.size >= 2) {
                            Icon(
                                imageVector = Icons.Rounded.DragIndicator,
                                contentDescription = stringResource(Res.string.splinterlands_reorder),
                                modifier = Modifier.draggableHandle().handCursor(),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Priority number
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(20.dp),
                        )
                        // Service icon
                        if (entry != null) {
                            Icon(
                                imageVector = org.jetbrains.compose.resources.vectorResource(entry.icon),
                                contentDescription = entry.serviceName,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Name + model
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry?.serviceName ?: id,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (entry != null && entry.modelId.isNotBlank()) {
                                Text(
                                    text = entry.modelId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        // Remove button
                        IconButton(
                            onClick = { onRemoveService(id) },
                            modifier = Modifier.handCursor(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(Res.string.splinterlands_remove),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }

    // Add service dropdown
    val notYetAdded = remember(availableServices, instanceIds) {
        availableServices.filter { it.instanceId !in instanceIds }
    }
    if (notYetAdded.isNotEmpty()) {
        var showDropdown by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(
                onClick = { showDropdown = true },
                modifier = Modifier.handCursor(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (instanceIds.isEmpty()) stringResource(Res.string.splinterlands_add_service) else stringResource(Res.string.splinterlands_add_another_service))
            }
            androidx.compose.material3.DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
            ) {
                for (service in notYetAdded) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Column {
                                Text(service.serviceName, style = MaterialTheme.typography.bodyMedium)
                                if (service.modelId.isNotBlank()) {
                                    Text(service.modelId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = org.jetbrains.compose.resources.vectorResource(service.icon),
                                contentDescription = service.serviceName,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {
                            onAddService(service.instanceId)
                            showDropdown = false
                        },
                        modifier = Modifier.handCursor(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SplinterlandsModelRankings(modelStats: ImmutableList<ModelStats>) {
    Text(
        text = stringResource(Res.string.splinterlands_model_rankings),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(6.dp))
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            for ((index, stats) in modelStats.withIndex()) {
                if (index > 0) Spacer(Modifier.height(6.dp))
                SplinterlandsModelRow(index + 1, stats)
            }
        }
    }
}

@Composable
private fun SplinterlandsModelRow(rank: Int, stats: ModelStats) {
    val winPct = (stats.winRate * 100).toInt()
    val barColor = when {
        winPct >= 60 -> Color(0xFF4CAF50)
        winPct >= 40 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stats.modelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.splinterlands_wins_losses, stats.wins, stats.losses),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$winPct%",
                style = MaterialTheme.typography.labelMedium,
                color = barColor,
            )
        }
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
@Composable
private fun SplinterlandsBattleLogRow(entry: BattleLogEntry) {
    var showActivity by remember { mutableStateOf(false) }
    val bgColor = if (entry.won) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .then(
                if (entry.activity.isNotEmpty()) {
                    Modifier.clickable { showActivity = true }.handCursor()
                } else {
                    Modifier
                },
            ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // W/L badge
                Text(
                    text = if (entry.won) stringResource(Res.string.splinterlands_victory) else stringResource(Res.string.splinterlands_defeat),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (entry.won) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.splinterlands_vs, entry.opponent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (entry.timestampMs > 0) {
                    val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
                    val relTime = formatRelativeTime(entry.timestampMs, nowMs)
                    Text(
                        text = relTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Details row
            val details = buildList {
                if (entry.mana > 0) add(stringResource(Res.string.splinterlands_mana, entry.mana))
                if (entry.rulesets.isNotBlank()) add(entry.rulesets)
            }
            if (details.isNotEmpty() || entry.llmPicked != null || entry.account.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val infoItems = buildList {
                        if (entry.account.isNotBlank()) add(entry.account)
                        addAll(details)
                    }
                    if (infoItems.isNotEmpty()) {
                        Text(
                            text = infoItems.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (entry.llmPicked != null) {
                        val label = if (entry.llmPicked) entry.modelName.ifBlank { stringResource(Res.string.splinterlands_llm_label) } else stringResource(Res.string.splinterlands_none_label)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.llmPicked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
    if (showActivity) {
        SplinterlandsActivityDialog(entry) { showActivity = false }
    }
}

@Composable
private fun SplinterlandsActivityDialog(
    entry: BattleLogEntry,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.splinterlands_battle_activity)) },
        text = {
            val activityScrollState = rememberScrollState()
            Box {
                Column(modifier = Modifier.verticalScroll(activityScrollState)) {
                    for (line in entry.activity) {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
                VerticalScrollbarForScroll(
                    scrollState = activityScrollState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        },
        confirmButton = {
            if (entry.battleId.isNotBlank()) {
                TextButton(
                    onClick = {
                        uriHandler.openUri("https://splinterlands.com/battle/${entry.battleId}")
                    },
                    modifier = Modifier.handCursor(),
                ) { Text(stringResource(Res.string.splinterlands_view_battle)) }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.handCursor(),
            ) { Text(stringResource(Res.string.splinterlands_close)) }
        },
    )
}

@Composable
private fun formatRelativeTime(timestampMs: Long, nowMs: Long): String {
    val diffMs = nowMs - timestampMs
    val minutes = diffMs / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> stringResource(Res.string.splinterlands_time_just_now)
        minutes == 1L -> stringResource(Res.string.splinterlands_time_1_min)
        minutes < 60 -> stringResource(Res.string.splinterlands_time_n_min, minutes.toInt())
        hours == 1L -> stringResource(Res.string.splinterlands_time_1_hour)
        hours < 24 -> stringResource(Res.string.splinterlands_time_n_hours, hours.toInt())
        days == 1L -> stringResource(Res.string.splinterlands_time_1_day)
        else -> stringResource(Res.string.splinterlands_time_n_days, days.toInt())
    }
}

@Composable
private fun SplinterlandsAddAccountForm(
    addStatus: SplinterlandsAddStatus,
    onTestAndAdd: (String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var postingKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    val isTesting = addStatus is SplinterlandsAddStatus.Testing

    // Collapse form after successful add
    LaunchedEffect(addStatus) {
        if (addStatus is SplinterlandsAddStatus.Idle) {
            if (expanded && username.isEmpty() && postingKey.isEmpty()) {
                expanded = false
            }
        }
    }

    if (!expanded) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.handCursor(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.splinterlands_add_account))
        }
    } else {
        Text(
            text = stringResource(Res.string.splinterlands_secure_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        KaiOutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(Res.string.splinterlands_hive_username)) },
            singleLine = true,
            enabled = !isTesting,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        KaiOutlinedTextField(
            value = postingKey,
            onValueChange = { postingKey = it },
            label = { Text(stringResource(Res.string.splinterlands_posting_key)) },
            singleLine = true,
            enabled = !isTesting,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(
                    onClick = { showKey = !showKey },
                    modifier = Modifier.handCursor(),
                ) {
                    Icon(
                        imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showKey) stringResource(Res.string.splinterlands_hide) else stringResource(Res.string.splinterlands_show),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onTestAndAdd(username, postingKey)
                    username = ""
                    postingKey = ""
                },
                modifier = Modifier.handCursor(),
                enabled = username.isNotBlank() && postingKey.isNotBlank() && !isTesting,
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.splinterlands_testing))
                } else {
                    Text(stringResource(Res.string.splinterlands_test_and_add))
                }
            }
            OutlinedButton(
                onClick = {
                    expanded = false
                    username = ""
                    postingKey = ""
                },
                modifier = Modifier.handCursor(),
                enabled = !isTesting,
            ) {
                Text(stringResource(Res.string.splinterlands_cancel))
            }
        }

        if (addStatus is SplinterlandsAddStatus.Error) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = addStatus.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SplinterlandsAccountRow(
    account: SplinterlandsAccountUiState,
    hasServices: Boolean,
    onRemove: () -> Unit,
    onStartBattle: () -> Unit,
    onStopBattle: () -> Unit,
) {
    val bs = account.battleStatus
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Avatar
            if (account.avatarUrl.isNotBlank()) {
                coil3.compose.AsyncImage(
                    model = account.avatarUrl,
                    contentDescription = account.username,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = account.username.take(1).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            // Username + energy
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (account.energy >= 0) {
                    Text(
                        text = "\u26A1 ${account.energy}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Stats if any
            if (bs.wins > 0 || bs.losses > 0) {
                Text(
                    text = stringResource(Res.string.splinterlands_wins_losses, bs.wins, bs.losses),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Error indicator
            if (bs.phase == BattlePhase.Error) {
                Text(
                    text = stringResource(Res.string.splinterlands_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Start/Stop button
            if (bs.isRunning) {
                OutlinedButton(
                    onClick = onStopBattle,
                    modifier = Modifier.handCursor(),
                    enabled = !bs.isStopping,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(if (bs.isStopping) stringResource(Res.string.splinterlands_stopping) else stringResource(Res.string.splinterlands_stop), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Button(
                    onClick = onStartBattle,
                    modifier = Modifier.handCursor(),
                    enabled = hasServices,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(stringResource(Res.string.splinterlands_start), style = MaterialTheme.typography.bodySmall)
                }
            }

            // Remove button with confirmation
            var showConfirm by remember { mutableStateOf(false) }
            IconButton(
                onClick = { showConfirm = true },
                modifier = Modifier.handCursor(),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(Res.string.splinterlands_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showConfirm) {
                AlertDialog(
                    onDismissRequest = { showConfirm = false },
                    title = { Text(stringResource(Res.string.splinterlands_remove_account)) },
                    text = { Text(stringResource(Res.string.splinterlands_remove_confirm, account.username)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showConfirm = false
                                onRemove()
                            },
                            modifier = Modifier.handCursor(),
                        ) {
                            Text(stringResource(Res.string.splinterlands_remove), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showConfirm = false },
                            modifier = Modifier.handCursor(),
                        ) { Text(stringResource(Res.string.splinterlands_cancel)) }
                    },
                )
            }
        }

        // Battle details (below player row)
        if (bs.isRunning) {
            val phaseText = when (bs.phase) {
                BattlePhase.LoggingIn -> stringResource(Res.string.splinterlands_logging_in)
                BattlePhase.CheckingEnergy -> stringResource(Res.string.splinterlands_checking_energy)
                BattlePhase.FindingMatch -> stringResource(Res.string.splinterlands_finding_match)
                BattlePhase.WaitingForOpponent -> stringResource(Res.string.splinterlands_waiting_for_opponent)
                BattlePhase.FetchingCollection -> stringResource(Res.string.splinterlands_fetching_cards)
                BattlePhase.PickingTeam -> stringResource(Res.string.splinterlands_picking_team)
                BattlePhase.SubmittingTeam -> stringResource(Res.string.splinterlands_submitting_team)
                BattlePhase.WaitingForResult -> stringResource(Res.string.splinterlands_waiting_for_result)
                BattlePhase.Finished -> stringResource(Res.string.splinterlands_finished)
                else -> ""
            }

            // Match info row: opponent, mana, rulesets
            if (bs.currentOpponent.isNotBlank() || bs.currentMana > 0) {
                Row(
                    modifier = Modifier.padding(start = 40.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (bs.currentOpponent.isNotBlank()) {
                        Text(
                            text = stringResource(Res.string.splinterlands_vs, bs.currentOpponent),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    if (bs.currentMana > 0) {
                        Text(
                            text = stringResource(Res.string.splinterlands_mana, bs.currentMana),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (bs.currentRulesets.isNotBlank()) {
                        Text(
                            text = bs.currentRulesets,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Status row: phase + LLM indicator + timer
            Row(
                modifier = Modifier.padding(start = 40.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (phaseText.isNotBlank()) {
                    Text(
                        text = phaseText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val showLlmIndicator = bs.llmPickedTeam != null && bs.phase in setOf(
                    BattlePhase.SubmittingTeam,
                    BattlePhase.WaitingForResult,
                )
                if (showLlmIndicator) {
                    val label = if (bs.llmPickedTeam == true) bs.winningServiceName.ifBlank { stringResource(Res.string.splinterlands_llm_label) } else stringResource(Res.string.splinterlands_auto_label)
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (bs.llmPickedTeam == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val showTimer = bs.teamDeadlineMs > 0L && bs.phase in setOf(
                    BattlePhase.FetchingCollection,
                    BattlePhase.PickingTeam,
                )
                if (showTimer) {
                    SplinterlandsCountdown(bs.teamDeadlineMs)
                }
            }

            // Per-service status rows during PickingTeam
            if (bs.phase == BattlePhase.PickingTeam && bs.serviceStatuses.isNotEmpty()) {
                for ((serviceId, status) in bs.serviceStatuses) {
                    Row(
                        modifier = Modifier.padding(start = 48.dp, top = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Status indicator
                        when (status) {
                            LlmServiceStatus.Querying -> CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )

                            LlmServiceStatus.ValidResponse -> Text("\u2714", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))

                            LlmServiceStatus.InvalidResponse -> Text("\u2718", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)

                            LlmServiceStatus.Failed -> Text("\u2718", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)

                            LlmServiceStatus.Selected -> Text("\u2605", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                        Text(
                            text = serviceId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Error message
        if (bs.errorMessage.isNotBlank() && bs.phase == BattlePhase.Error) {
            Text(
                text = bs.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 40.dp),
            )
        }
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
@Composable
private fun SplinterlandsCountdown(deadlineMs: Long) {
    var remaining by remember { mutableStateOf(0L) }
    LaunchedEffect(deadlineMs) {
        if (deadlineMs > 0L) {
            while (true) {
                remaining = ((deadlineMs - kotlin.time.Clock.System.now().toEpochMilliseconds()) / 1000).coerceAtLeast(0L)
                kotlinx.coroutines.delay(1.seconds)
            }
        }
    }
    val mins = remaining / 60
    val secs = remaining % 60
    Text(
        text = "$mins:${secs.toString().padStart(2, '0')}",
        style = MaterialTheme.typography.labelMedium,
        color = if (remaining <= 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
