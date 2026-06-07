package com.kai.custom.ui.settings

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kai.custom.data.AppSettings
import com.kai.custom.data.BehaviorStyle
import com.kai.custom.data.CharacterType
import com.kai.custom.data.DataRepository
import com.kai.custom.data.LanguageStyle
import com.kai.custom.data.PersonaConfig
import com.kai.custom.data.PersonaManager
import com.kai.custom.data.RenderMode
import com.kai.custom.ui.handCursor
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.compose.koinInject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
fun PluginSettingsCard() {
    val appSettings: AppSettings = koinInject()
    val dataRepository: DataRepository = koinInject()
    val personaManager = remember { PersonaManager(appSettings) }
    val scope = rememberCoroutineScope()

    val currentServiceName = remember { dataRepository.currentService().displayName }

    var prompt by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var generatedConfig by remember { mutableStateOf<PersonaConfig?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var usingCondensed by remember { mutableStateOf(true) }
    var condensedSoul by remember { mutableStateOf("") }
    var fullSoul by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    var sentPrompt by remember { mutableStateOf("") }
    var step1Response by remember { mutableStateOf("") }
    var step2Response by remember { mutableStateOf("") }
    var currentStep by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Plugins",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            Text(
                text = "Persona Generator",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Describe a personality and the AI will generate it using your default service.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Text(
                text = "Using: $currentServiceName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Describe the persona") },
                placeholder = { Text("e.g. A sarcastic noir detective who talks like 1940s film noir") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
            )

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        if (prompt.isBlank()) return@Button
                        generating = true
                        error = null
                        generatedConfig = null
                        condensedSoul = ""
                        fullSoul = ""
                        statusText = "Starting..."
                        sentPrompt = ""
                        step1Response = ""
                        step2Response = ""
                        currentStep = 0
                        scope.launch {
                            try {
                                val description = prompt.trim()
                                val personaPrompt = buildString {
                                    append("Based on the following description, create a detailed user persona written in second person (\"You are...\"):\n\n")
                                    append("Persona: $description\n\n")
                                    append("Create BOTH a condensed version AND a full synthesized profile. Use the EXACT formats below.\n\n")
                                    append("=== CONDENSED VERSION ===\n")
                                    append("Use this exact structure:\n")
                                    append("---\n")
                                    append("**Identity**: You are [sentence describing who you are].\n\n")
                                    append("**Key Characteristics**:\n*   **[Trait]**: [detailed description]\n\n")
                                    append("**Communication Style**:\n[paragraphs describing speaking patterns, tone, mannerisms]\n\n")
                                    append("**Essential Knowledge**:\n[paragraph about areas of expertise]\n\n")
                                    append("**Specific Behaviors & Phrases**:\n*   [specific behavior or phrase]\n\n")
                                    append("**General Response Guidelines**:\n*   **[Guideline]**: [description]\n\n")
                                    append("400-800 words.\n\n")
                                    append("=== FULL SYNTHESIZED PROFILE ===\n")
                                    append("Separate with \"--- FULL PROFILE ---\" then write the full profile with these sections:\n\n")
                                    append("### Output Summary\n- **Section 0**: Core Essence (Priority Elements)\n- **Sections 1-10**: Core persona profile (3,500-4,500 words total)\n- **Section 11**: Platform Adaptation Bank (500-1,000 words)\n- **Total Length**: 4,500-5,500 words\n\n")
                                    append("### 0. Core Essence (Priority Elements)\n- **Identity in 25 words**: [capture their fundamental essence and role]\n- **Top 3 defining traits**: [most characteristic attributes that define them]\n- **Primary communication style**: [core approach to interacting with others]\n- **Essential behavioral markers**: [3-5 must-have behaviors for accurate portrayal]\n- **Must-have linguistic patterns**: [3-5 signature language elements]\n\n")
                                    append("### 1. Biographical Foundation and Personality\nLife story, formative experiences, personality characteristics, defining life events. Include specific incidents and context.\n\n")
                                    append("### 2. Voice/Communication Analysis\nSpeech pace, tonal qualities, accent, volume dynamics. How voice changes in different emotional states.\n\n")
                                    append("### 3. Signature Language Patterns\nCommon opening phrases, favorite words, rhetorical devices, linguistic evolution.\n\n")
                                    append("### 4. Narrative/Communication Structure\nHow they organize information, storytelling techniques, argument construction methods.\n\n")
                                    append("### 5. Subject Matter Expertise\nCore areas of knowledge, technical vocabulary, how they explain complex concepts.\n\n")
                                    append("### 6. Philosophical Framework\nCore beliefs, values, worldview, ethical stances, evolution of views.\n\n")
                                    append("### 7. Emotional Range and Expression\nHow they express emotions, humor style, handling of serious topics, emotional tells.\n\n")
                                    append("### 8. Distinctive Patterns and Quirks\nPhysical mannerisms, verbal tics, behavioral patterns, personal rituals, contradictory behaviors.\n\n")
                                    append("### 9. Evolution Over Time\nHow their style has changed, shifts in focus, what has remained constant.\n\n")
                                    append("### 10. Practical Application Guidelines\nKey elements for accurate emulation, common mistakes to avoid, context-specific adaptations.\n\n")
                                    append("### 10.5. Platform Adaptation Bank\nBehavioral Rules (If-Then format), Dialogue Examples Bank, Language Pattern Repository.\n\n")
                                    append("Full profile: 3,500-4,500 words. Be specific with examples and exact phrasing. Each section 300-500 words.")
                                }
                                statusText = "Generating persona..."
                                sentPrompt = personaPrompt

                                val response = dataRepository.askSilently(personaPrompt, 300_000L)

                                if (response.isBlank()) { error = "AI returned empty response."; return@launch }
                                step1Response = response.take(5000)
                                statusText = "Done (${response.length} chars)"

                                val name = description.split(" ").take(3).joinToString(" ").replaceFirstChar(Char::uppercase)
                                val id = "persona_${Uuid.random().toString().take(8)}"

                                condensedSoul = response.trim()
                                fullSoul = response.trim()
                                appSettings.settings.putString("persona_full_$id", response.trim())

                                val config = PersonaConfig(
                                    id = id,
                                    name = name.take(50),
                                    description = response.trim().substringBefore("\n").substringBefore(".").take(100).let {
                                        if (it.length < 10) description.take(100) else it
                                    },
                                    behaviorStyle = BehaviorStyle.CUSTOM,
                                    languageStyle = LanguageStyle.NONE,
                                    characterType = CharacterType.NONE,
                                    defaultSoul = response.trim(),
                                    renderMode = RenderMode.CHARACTER,
                                    isBuiltIn = false,
                                )
                                personaManager.savePersona(config)
                                personaManager.setActivePersonaId(config.id)
                                generatedConfig = config
                                usingCondensed = true
                            } catch (e: Exception) {
                                error = e.message ?: "Generation failed."
                            } finally {
                                generating = false
                            }
                        }
                    },
                    enabled = !generating && prompt.isNotBlank(),
                    modifier = Modifier.handCursor(),
                ) { Text("Generate Persona") }

                Spacer(Modifier.width(12.dp))
                if (generating) {
                    if (step1Response.isBlank()) {
                        CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                if (step1Response.isNotBlank()) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text("Raw AI Response:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SelectionContainer {
                            Text(
                                text = step1Response,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            if (generatedConfig != null) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Generated: ${generatedConfig!!.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)

                // Toggle between condensed and full
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            usingCondensed = !usingCondensed
                            val id = generatedConfig!!.id
                            val appSet: AppSettings = appSettings
                            if (usingCondensed) {
                                personaManager.savePersona(generatedConfig!!.copy(defaultSoul = condensedSoul))
                                personaManager.setActivePersonaId(id)
                            } else {
                                val full = appSet.settings.getString("persona_full_$id", fullSoul)
                                personaManager.savePersona(generatedConfig!!.copy(defaultSoul = full))
                                personaManager.setActivePersonaId(id)
                            }
                        },
                        modifier = Modifier.handCursor(),
                    ) { Text(if (usingCondensed) "Switch to Full Profile" else "Switch to Condensed") }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (usingCondensed) "Using: Condensed version" else "Using: Full profile",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { prompt = ""; generatedConfig = null },
                    modifier = Modifier.handCursor(),
                ) { Text("Generate Another") }
            }
        }
    }
}
