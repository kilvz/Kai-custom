@file:OptIn(ExperimentalMaterial3Api::class)

package com.kai.custom.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kai.custom.data.DataRepository
import com.kai.custom.data.MemoryEntry
import com.kai.custom.data.dimension.KGFact
import com.kai.custom.ui.components.SettingsListItem
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.memory_management_expired
import kai.composeapp.generated.resources.memory_management_no_kg_facts
import kai.composeapp.generated.resources.memory_management_no_memories
import kai.composeapp.generated.resources.memory_management_realms
import kai.composeapp.generated.resources.memory_management_show_protected
import kai.composeapp.generated.resources.memory_management_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun MemoryManagementSheet(
    onDismiss: () -> Unit,
    onDeleteMemory: (String) -> Unit,
) {
    val dataRepository: DataRepository = koinInject()
    var entityCount by remember { mutableStateOf(0L) }
    var realms by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var memories by remember { mutableStateOf<List<MemoryEntry>>(emptyList()) }
    var kgFacts by remember { mutableStateOf<List<KGFact>>(emptyList()) }

    var showProtected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        entityCount = dataRepository.countDimensionEntities()
        kgFacts = dataRepository.queryKgFacts()
        memories = dataRepository.getMemories()
    }

    // Refresh when a memory is deleted
    val onDelete = remember(dataRepository) {
        { key: String ->
            onDeleteMemory(key)
            entityCount = dataRepository.countDimensionEntities()
            kgFacts = dataRepository.queryKgFacts()
            memories = dataRepository.getMemories()
        }
    }

    val displayedMemories = if (showProtected) memories else memories.filter { !it.protected }

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Stats", "Memories", "KG Facts")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(Res.string.memory_management_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))

            PrimaryScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            when (selectedTab) {
                0 -> StatsTab(entityCount, realms)
                1 -> MemoriesTab(displayedMemories, showProtected, onShowProtectedToggle = { showProtected = it }, onDeleteMemory = onDelete)
                2 -> KgFactsTab(kgFacts)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatsTab(entityCount: Long, realms: Map<String, List<String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        StatRow("Total Entities", entityCount.toString())
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.memory_management_realms),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        realms.forEach { (realm, domains) ->
            Text(
                text = realm.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            domains.forEach { domain ->
                Text(
                    text = "  $domain",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MemoriesTab(
    memories: List<MemoryEntry>,
    showProtected: Boolean,
    onShowProtectedToggle: (Boolean) -> Unit,
    onDeleteMemory: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(
            checked = showProtected,
            onCheckedChange = onShowProtectedToggle,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.memory_management_show_protected),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(8.dp))

    if (memories.isEmpty()) {
        Text(
            text = stringResource(Res.string.memory_management_no_memories),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn {
        items(memories) { memory ->
            SettingsListItem(
                title = memory.key,
                subtitle = memory.content,
                onDelete = { onDeleteMemory(memory.key) },
                deleteContentDescription = "Delete",
                subtitleMaxLines = 3,
                onClick = {},
            )
        }
    }
}

@Composable
private fun KgFactsTab(facts: List<KGFact>) {
    if (facts.isEmpty()) {
        Text(
            text = stringResource(Res.string.memory_management_no_kg_facts),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn {
        items(facts) { fact ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row {
                    Text(
                        text = fact.subject,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = " — ${fact.predicate} — ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = fact.`object`,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                if (fact.validTo != null) {
                    Text(
                        text = stringResource(Res.string.memory_management_expired),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
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
