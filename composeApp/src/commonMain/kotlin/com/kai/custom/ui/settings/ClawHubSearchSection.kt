package com.kai.custom.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kai.custom.data.DataRepository
import com.kai.custom.skills.RegistrySkillEntry
import com.kai.custom.skills.SkillRegistry
import com.kai.custom.ui.handCursor
import kotlinx.coroutines.launch

@Composable
internal fun ClawHubSearchSection(
    dataRepository: DataRepository,
) {
    val registry = remember { SkillRegistry() }
    val installScope = rememberCoroutineScope()
    var isExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<RegistrySkillEntry>>(emptyList()) }
    var installingSlug by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var lastSecurityQuery by remember { mutableStateOf("") }
    val installedSkills = dataRepository.getInstalledSkills()
    val installedIds = remember(installedSkills) { installedSkills.map { it.id }.toSet() }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            results = emptyList()
            statusMessage = null
            errorMessage = null
            return@LaunchedEffect
        }
        errorMessage = null
        statusMessage = null
        val result = registry.searchClawHub(searchQuery.trim())
        result.onSuccess { entries ->
            results = entries
            if (entries.isEmpty()) statusMessage = "No skills found"
        }.onFailure { e ->
            errorMessage = e.message ?: "Search failed"
        }
    }

    LaunchedEffect(results) {
        if (results.isEmpty()) return@LaunchedEffect
        val query = searchQuery.trim()
        if (lastSecurityQuery == query) return@LaunchedEffect
        lastSecurityQuery = query
        val updated = registry.fetchSecurityData(results)
        if (updated.any { it.securityStatus != null }) results = updated
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "ClawHub Skills",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Browse and install community skills from ClawHub (clawhub.ai)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        if (!isExpanded) {
            OutlinedButton(
                onClick = { isExpanded = true },
                modifier = Modifier.handCursor(),
            ) {
                Text("Browse ClawHub Skills")
            }
        }

        if (isExpanded) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search skills...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))

            if (errorMessage != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (statusMessage != null && results.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = statusMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (results.isNotEmpty()) {
                for (entry in results) {
                    ClawHubSkillRow(
                        entry = entry,
                        alreadyInstalled = entry.id in installedIds,
                        isInstalling = installingSlug == entry.slug,
                        onInstall = {
                            installScope.launch {
                                val slug = entry.slug ?: entry.id
                                installingSlug = slug
                                val label = entry.displayName.ifBlank { entry.id }
                                statusMessage = "Installing $label..."
                                errorMessage = null
                                val result = dataRepository.installSkillFromClawHub(slug)
                                result.onSuccess {
                                    statusMessage = "Installed $label"
                                }.onFailure { e ->
                                    errorMessage = e.message ?: "Install failed"
                                    statusMessage = null
                                }
                                installingSlug = null
                            }
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun ClawHubSkillRow(
    entry: RegistrySkillEntry,
    alreadyInstalled: Boolean,
    isInstalling: Boolean,
    onInstall: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.securityStatus != null) {
                Canvas(modifier = Modifier.size(8.dp)) {
                    drawCircle(color = securityColor(entry.securityStatus))
                }
                Spacer(Modifier.width(6.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayName.ifBlank { entry.id },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                if (entry.ownerHandle.isNotBlank()) {
                    Text(
                        text = "by ${entry.ownerHandle}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                if (entry.description.isNotBlank()) {
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (isInstalling) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (alreadyInstalled) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Button(
                    onClick = onInstall,
                    modifier = Modifier.handCursor(),
                ) {
                    Text("Install")
                }
            }
        }
    }
}

private fun securityColor(status: String?): Color = when (status) {
    "clean" -> Color(0xFF4CAF50)
    "suspicious" -> Color(0xFFFFC107)
    "malicious" -> Color(0xFFF44336)
    else -> Color.Transparent
}
