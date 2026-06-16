package com.kai.custom.ui.chat.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kai.custom.skills.SkillManifest
import com.kai.custom.ui.handCursor
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.skill_autocomplete_deactivate
import kai.composeapp.generated.resources.skill_autocomplete_stop
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SkillAutocomplete(
    skills: ImmutableList<SkillManifest>,
    query: String,
    activeSkill: SkillManifest? = null,
    onSelect: (SkillManifest?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = remember(skills, query, activeSkill) {
        val q = query.lowercase()
        val matched = if (q.isEmpty()) {
            skills
        } else {
            skills.filter { it.id.startsWith(q) || it.id.contains(q) }
        }
        val stopEntry = if (activeSkill != null && ("stop".startsWith(q) || q.isEmpty())) {
            listOf<SkillManifest?>(null)
        } else {
            emptyList()
        }
        stopEntry + matched
    }
    if (filtered.isEmpty()) return

    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            )
            .heightIn(max = 200.dp)
            .verticalScroll(scrollState),
    ) {
        for (entry in filtered) {
            if (entry == null) {
                StopRow(onClick = { onSelect(null) })
            } else {
                SkillRow(
                    skill = entry,
                    onClick = { onSelect(entry) },
                )
            }
        }
    }
}

@Composable
private fun StopRow(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .handCursor()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(Res.string.skill_autocomplete_stop),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(Res.string.skill_autocomplete_deactivate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SkillRow(skill: SkillManifest, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .handCursor()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = "/${skill.id}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
        if (skill.description.isNotEmpty()) {
            Text(
                text = skill.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}
