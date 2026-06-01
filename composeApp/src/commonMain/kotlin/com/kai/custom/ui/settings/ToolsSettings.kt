package com.kai.custom.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kai.custom.mcp.PopularMcpServer
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.skills.RegistrySkillEntry
import com.kai.custom.skills.SkillManifest
import com.kai.custom.ui.handCursor
import com.kai.custom.ui.kaiAdaptiveCardBorder
import com.kai.custom.ui.kaiAdaptiveCardColors
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.settings_tools_description
import kai.composeapp.generated.resources.settings_tools_none_available
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ToolsContent(
    tools: ImmutableList<ToolInfo>,
    onToggleTool: (String, Boolean) -> Unit,
    mcpServers: ImmutableList<McpServerUiState>,
    onAddMcpServer: (String, String, Map<String, String>) -> Unit,
    onRemoveMcpServer: (String) -> Unit,
    onToggleMcpServer: (String, Boolean) -> Unit,
    onRefreshMcpServer: (String) -> Unit,
    showAddMcpServerDialog: Boolean,
    onShowAddMcpServerDialog: (Boolean) -> Unit,
    onAddPopularMcpServer: (PopularMcpServer) -> Unit,
    showRootSection: Boolean = false,
    isRootEnabled: Boolean = false,
    rootAvailable: Boolean = false,
    onToggleRoot: (Boolean) -> Unit = {},
    skills: ImmutableList<SkillManifest> = persistentListOf(),
    activeSkill: SkillManifest? = null,
    browsableSkills: ImmutableList<RegistrySkillEntry> = persistentListOf(),
    isBrowsing: Boolean = false,
    browseFailed: Boolean = false,
    onInstallGitHub: (String) -> Unit = {},
    onInstallBrowsed: (RegistrySkillEntry) -> Unit = {},
    onUninstallSkill: (String) -> Unit = {},
    isSandboxInstalled: Boolean = false,
    onNavigateToSandbox: () -> Unit = {},
    onBrowseMarketplaceSkills: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Root section
        if (showRootSection) {
            RootSection(
                isRootEnabled = isRootEnabled,
                rootAvailable = rootAvailable,
                onToggleRoot = onToggleRoot,
            )
            Spacer(Modifier.height(24.dp))
        }

        // MCP Servers section
        McpServersSection(
            mcpServers = mcpServers,
            onAddMcpServer = onAddMcpServer,
            onRemoveMcpServer = onRemoveMcpServer,
            onToggleMcpServer = onToggleMcpServer,
            onRefreshMcpServer = onRefreshMcpServer,
            onToggleTool = onToggleTool,
            showAddDialog = showAddMcpServerDialog,
            onShowAddDialog = onShowAddMcpServerDialog,
            onAddPopularMcpServer = onAddPopularMcpServer,
        )

        Spacer(Modifier.height(24.dp))

        // Skills section — wrapped with sandbox check and state wiring
        SkillsSection(
            skills = skills,
            onUninstallSkill = onUninstallSkill,
            showAddDialog = false,
            onShowAddDialog = {},
            onInstallGitHub = onInstallGitHub,
            onInstallBrowsed = onInstallBrowsed,
            isInstalling = false,
            installError = null,
            browsableSkills = browsableSkills,
            isBrowsing = isBrowsing,
            browseFailed = browseFailed,
            isSandboxInstalled = isSandboxInstalled,
            onNavigateToSandbox = onNavigateToSandbox,
            onBrowseMarketplaceSkills = onBrowseMarketplaceSkills,
        )

        Spacer(Modifier.height(24.dp))

        // Native tools section
        Text(
            text = stringResource(Res.string.settings_tools_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        if (tools.isEmpty()) {
            Text(
                text = stringResource(Res.string.settings_tools_none_available),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val categories = mapOf(
                "Device Info" to listOf("get_device_info", "get_battery_info", "get_phone_state"),
                "Network & Location" to listOf("get_network_info", "get_wifi_info", "get_gps_location", "web_search", "get_location_from_ip"),
                "Contacts & Calendar" to listOf("read_contacts", "write_contact", "read_calendar_events", "create_calendar_event"),
                "Media & Clipboard" to listOf("list_media", "read_clipboard"),
                "Apps & System" to listOf("list_installed_apps", "read_device_logs", "open_file", "open_url", "fetch_url"),
                "Bluetooth" to listOf("scan_bluetooth_devices"),
                "Notifications & Alarms" to listOf("send_notification", "set_alarm"),
                "Shell & Automation" to listOf("execute_shell_command", "run_root", "run_adb", "run_opencode", "speak_text", "manage_process"),
                "SSH" to listOf("ssh_command", "ssh_configure_host", "ssh_connect", "ssh_disconnect"),
                "Email" to listOf("email_send", "email_search", "email_get", "email_list", "email_draft", "email_delete"),
                "SMS" to listOf("sms_read", "sms_send", "sms_delete", "sms_list"),
                "Scheduling" to listOf("task_create", "task_list", "task_delete"),
                "Heartbeat" to listOf("promote_learning"),
                "Memory" to listOf("memory_store", "memory_forget", "memory_reinforce", "search_memories", "memory_learn"),
                "Telegram" to listOf("telegram_send"),
            )
            val grouped = mutableMapOf<String, MutableList<ToolInfo>>()
            val uncategorized = mutableListOf<ToolInfo>()
            for (tool in tools) {
                var placed = false
                for ((cat, ids) in categories) {
                    if (tool.id in ids) {
                        grouped.getOrPut(cat) { mutableListOf() }.add(tool)
                        placed = true
                        break
                    }
                }
                if (!placed) uncategorized.add(tool)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((category, catTools) in grouped.toSortedMap()) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    ToolGrid(catTools, onToggleTool)
                }
                if (uncategorized.isNotEmpty()) {
                    Text(
                        text = "Other",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    ToolGrid(uncategorized, onToggleTool)
                }
            }
        }
    }
}

@Composable
internal fun RootSection(
    isRootEnabled: Boolean,
    rootAvailable: Boolean,
    onToggleRoot: (Boolean) -> Unit,
) {
    Column {
        ToggleableHeadline(
            title = "Root Shell",
            description = "Run shell commands with root privileges (UID 0) on this device",
            checked = isRootEnabled,
            onCheckedChange = onToggleRoot,
        )

        if (isRootEnabled) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = Color(0xFFFFF3E0),
                shape = CardDefaults.shape,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Column {
                        Text(
                            text = "Root access gives full system control. Misuse can damage your device or void warranty.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF424242),
                        )
                        if (rootAvailable) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "✓ su binary detected",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2E7D32),
                            )
                        } else {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "✗ su not found — device may not be rooted",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFC62828),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolGrid(
    catTools: List<ToolInfo>,
    onToggleTool: (String, Boolean) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 800.dp -> 3
            maxWidth >= 500.dp -> 2
            else -> 1
        }
        val rows = catTools.chunked(columns)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { rowTools ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowTools.forEach { tool ->
                        ToolItem(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            tool = tool,
                            onToggle = { enabled -> onToggleTool(tool.id, enabled) },
                        )
                    }
                    repeat(columns - rowTools.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolItem(
    tool: ToolInfo,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clip(CardDefaults.shape)
            .clickable { onToggle(!tool.isEnabled) }
            .handCursor(),
        colors = kaiAdaptiveCardColors(),
        border = kaiAdaptiveCardBorder(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.nameRes?.let { stringResource(it) } ?: tool.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = tool.descriptionRes?.let { stringResource(it) } ?: tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(16.dp))

            Switch(
                checked = tool.isEnabled,
                onCheckedChange = onToggle,
            )
        }
    }
}
