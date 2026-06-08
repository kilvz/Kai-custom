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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.compose.koinInject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object PersonaGeneratorState {
    var prompt by mutableStateOf("")
    var generating by mutableStateOf(false)
    var generatedConfig by mutableStateOf<PersonaConfig?>(null)
    var error by mutableStateOf<String?>(null)
    var usingCondensed by mutableStateOf(true)
    var condensedSoul by mutableStateOf("")
    var fullSoul by mutableStateOf("")
    var statusText by mutableStateOf("")
    var sentPrompt by mutableStateOf("")
    var step1Response by mutableStateOf("")
    var step2Response by mutableStateOf("")
    var currentStep by mutableStateOf(0)

    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    fun reset() {
        prompt = ""
        generating = false
        generatedConfig = null
        error = null
        usingCondensed = true
        condensedSoul = ""
        fullSoul = ""
        statusText = ""
        sentPrompt = ""
        step1Response = ""
        step2Response = ""
        currentStep = 0
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
fun PluginSettingsCard() {
    val appSettings: AppSettings = koinInject()
    val dataRepository: DataRepository = koinInject()
    val personaManager = remember { PersonaManager(appSettings) }
    val scope = PersonaGeneratorState.scope

    val currentServiceName = remember { dataRepository.currentService().displayName }

    var prompt by PersonaGeneratorState::prompt
    var generating by PersonaGeneratorState::generating
    var generatedConfig by PersonaGeneratorState::generatedConfig
    var error by PersonaGeneratorState::error
    var usingCondensed by PersonaGeneratorState::usingCondensed
    var condensedSoul by PersonaGeneratorState::condensedSoul
    var fullSoul by PersonaGeneratorState::fullSoul
    var statusText by PersonaGeneratorState::statusText
    var sentPrompt by PersonaGeneratorState::sentPrompt
    var step1Response by PersonaGeneratorState::step1Response
    var step2Response by PersonaGeneratorState::step2Response
    var currentStep by PersonaGeneratorState::currentStep

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
                if (generatedConfig != null) {
                    Button(
                        onClick = { PersonaGeneratorState.reset() },
                        modifier = Modifier.handCursor(),
                    ) { Text("Generate Another") }

                    Spacer(Modifier.width(12.dp))

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

                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (usingCondensed) "Using: Condensed version" else "Using: Full profile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
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
                                        append("Description: $description\n\n")
                                        append("Output TWO sections with EXACTLY this format:\n\n")
                                        append("SECTION 1 — Full synthesized profile (3,500-4,500 words):\n")
                                        append("Include all sections: ### 0. Core Essence, ### 1. Biographical Foundation and Personality, ### 2. Voice/Communication Analysis, ### 3. Signature Language Patterns, ### 4. Psychological Profile, ### 5. Interaction Dynamics, ### 6. Value System and Beliefs, ### 7. Physicality and Habits, ### 8. Knowledge Base and Blind Spots, ### 9. Evolution Over Time, ### 10. Practical Application Guidelines, ### 10.5. Platform Adaptation Bank.\n\n")
                                        append("---FULL---\n")
                                        append("SECTION 2 — Condensed version (400-800 words):\n")
                                        append("- Start with: **Identity**: You are [one-sentence description]\n")
                                        append("- Then **Key Characteristics**: with bullet points\n")
                                        append("- Then **Communication Style**: paragraph\n")
                                        append("- Then **Essential Knowledge**: paragraph\n")
                                        append("- Then **Specific Behaviors & Phrases**: bullet points\n")
                                        append("- Then **General Response Guidelines**: bullet points\n\n")
                                        append("Output only the content, no extra section headers or formatting markers like \"=== CONDENSED VERSION ===\".")
                                    }
                                    statusText = "Generating persona..."
                                    sentPrompt = personaPrompt

                                    val timerJob = launch {
                                        var seconds = 0
                                        while (true) {
                                            delay(1000)
                                            seconds++
                                            statusText = "Generating persona... (${seconds}s) — This takes 2-4 minutes."
                                        }
                                    }

                                    val response = try {
                                        dataRepository.askSilently(personaPrompt, 300_000L)
                                    } finally {
                                        timerJob.cancel()
                                    }

                                    if (response.isBlank()) { error = "AI returned empty response."; return@launch }
                                    step1Response = response.take(5000)
                                    statusText = "Done (${response.length} chars)"

                                    val name = description.split(" ").take(3).joinToString(" ").replaceFirstChar(Char::uppercase)
                                    val id = "persona_${Uuid.random().toString().take(8)}"

                                    val parts = response.split("\n---FULL---\n", limit = 2)
                                    val cleanFull = parts[0].trim()
                                    val cleanCondensed = parts.getOrElse(1) { "" }.trim().ifBlank { response.trim() }

                                    condensedSoul = cleanCondensed
                                    fullSoul = cleanFull
                                    if (cleanFull.isNotBlank()) {
                                        appSettings.settings.putString("persona_full_$id", cleanFull)
                                    }
                                    appSettings.settings.putString("persona_condensed_$id", cleanCondensed)

                                    val shortDesc = cleanCondensed.lineSequence()
                                        .firstOrNull { it.startsWith("**Identity**:") }
                                        ?.removePrefix("**Identity**: You are ")
                                        ?.removePrefix("**Identity**:")
                                        ?.trim()
                                        ?.substringBefore(".")
                                        ?.trim()
                                        ?.let { it.take(100) }
                                    val config = PersonaConfig(
                                        id = id,
                                        name = name.take(50),
                                        description = shortDesc ?: name.take(100),
                                        behaviorStyle = BehaviorStyle.CUSTOM,
                                        languageStyle = LanguageStyle.NONE,
                                        characterType = CharacterType.NONE,
                                        defaultSoul = cleanCondensed,
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
    }
}
