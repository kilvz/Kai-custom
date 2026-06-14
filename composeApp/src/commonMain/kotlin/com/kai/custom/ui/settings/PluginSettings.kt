package com.kai.custom.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
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
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.plugin_persona_generator_description
import kai.composeapp.generated.resources.plugin_persona_generator_error_empty
import kai.composeapp.generated.resources.plugin_persona_generator_error_failed
import kai.composeapp.generated.resources.plugin_persona_generator_generate
import kai.composeapp.generated.resources.plugin_persona_generator_generate_another
import kai.composeapp.generated.resources.plugin_persona_generator_generated
import kai.composeapp.generated.resources.plugin_persona_generator_plugins
import kai.composeapp.generated.resources.plugin_persona_generator_prompt_label
import kai.composeapp.generated.resources.plugin_persona_generator_prompt_placeholder
import kai.composeapp.generated.resources.plugin_persona_generator_raw_response
import kai.composeapp.generated.resources.plugin_persona_generator_starting
import kai.composeapp.generated.resources.plugin_persona_generator_status_done
import kai.composeapp.generated.resources.plugin_persona_generator_status_generating
import kai.composeapp.generated.resources.plugin_persona_generator_status_generating_seconds
import kai.composeapp.generated.resources.plugin_persona_generator_switch_condensed
import kai.composeapp.generated.resources.plugin_persona_generator_switch_full
import kai.composeapp.generated.resources.plugin_persona_generator_title
import kai.composeapp.generated.resources.plugin_persona_generator_using
import kai.composeapp.generated.resources.plugin_persona_generator_using_condensed
import kai.composeapp.generated.resources.plugin_persona_generator_using_full
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class PlatformType {
    GENERIC,
    CLAUDE,
    GEMINI,
    CHATGPT,
}

fun platformForService(serviceName: String): PlatformType = when {
    serviceName.contains("Anthropic", ignoreCase = true) ||
        serviceName.contains("Claude", ignoreCase = true) -> PlatformType.CLAUDE

    serviceName.contains("Gemini", ignoreCase = true) -> PlatformType.GEMINI

    serviceName.contains("OpenAI", ignoreCase = true) ||
        serviceName.contains("ChatGPT", ignoreCase = true) ||
        serviceName.contains("GPT", ignoreCase = true) -> PlatformType.CHATGPT

    else -> PlatformType.GENERIC
}

fun buildFullPersonaPrompt(description: String): String = buildString {
    append("Based on the following description, create a detailed user persona written in second person (\"You are...\"):\n\n")
    append("Description: $description\n\n")
    append("Create a comprehensive persona profile following this exact template structure. Write in second person (\"You are...\"):\n\n")
    append("### 0. Core Essence (Priority Elements)\n")
    append("- **Identity in 25 words**: [capture fundamental essence]\n")
    append("- **Top 3 defining traits**: [most characteristic attributes]\n")
    append("- **Primary communication style**: [core approach]\n")
    append("- **Essential behavioral markers**: [3-5 must-have behaviors]\n")
    append("- **Must-have linguistic patterns**: [3-5 signature language elements]\n\n")
    append("### 1. Biographical Foundation and Personality\n")
    append("[Early life, formative experiences, education, career milestones, personality traits, daily habits, character contradictions, key relationships]\n\n")
    append("### 2. Voice/Communication Analysis\n")
    append("[Speaking pace, tonal qualities, accent, volume dynamics, breathing patterns, written vs spoken style differences]\n\n")
    append("### 3. Signature Language Patterns\n")
    append("[Opening phrases, transitional expressions, closing techniques, favorite words, rhetorical devices, grammatical preferences]\n\n")
    append("### 4. Narrative/Communication Structure\n")
    append("[Information organization, storytelling techniques, argument construction, use of examples and analogies, audience engagement]\n\n")
    append("### 5. Subject Matter Expertise\n")
    append("[Core knowledge areas, technical vocabulary, explanation style, balance between expertise and accessibility]\n\n")
    append("### 6. Philosophical Framework\n")
    append("[Core beliefs, worldview, ethical stances, key messages, vision for the future]\n\n")
    append("### 7. Emotional Range and Expression\n")
    append("[Emotion expression, situational tone variations, humor style, handling serious topics, empathy and connection methods]\n\n")
    append("### 8. Distinctive Patterns and Quirks\n")
    append("[Physical mannerisms, verbal tics, behavioral patterns, personal rituals, contradictory behaviors]\n\n")
    append("### 9. Evolution Over Time\n")
    append("[Style changes, shifts in focus, adaptation to different contexts, response to criticism, what remained constant]\n\n")
    append("### 10. Practical Application Guidelines\n")
    append("[Key elements for accurate emulation, common mistakes to avoid, context-specific adaptations]\n\n")
    append("### 10.5. Platform Adaptation Bank\n")
    append("[Behavioral rules (If-Then format), dialogue examples, language pattern repository]\n\n")
    append("Total length: 3,500-4,500 words for all sections 0-10.5.\n\n")
    append("IMPORTANT: Output ONLY the persona content starting with \"### 0. Core Essence\". No preamble, no introduction, no explanations.")
}

fun buildCondensedPrompt(fullPersona: String, platform: PlatformType): String = buildString {
    when (platform) {
        PlatformType.CLAUDE -> {
            append("You are tasked with creating a Claude-optimized prompt based on a comprehensive persona. The goal is to transform the detailed persona into a system prompt that leverages Claude's analytical capabilities, ethical reasoning, and nuanced understanding.\n\n")
            append("CRITICAL OUTPUT INSTRUCTION: Begin your response directly without any preamble, introduction, or explanatory text.\n\n")
            append("INPUT PERSONA:\n$fullPersona\n\n")
            append("INSTRUCTIONS:\n")
            append("Create a Claude system prompt that establishes clear persona identity with depth and authenticity. Capture the persona's reasoning style, communication patterns, ethical framework, and how they connect ideas across domains. Leverage Claude's strengths in analysis, nuance, and helpfulness.\n\n")
            append("FORMAT REQUIREMENTS:\n")
            append("- Begin with a thoughtful character introduction\n")
            append("- Use structured thinking approach\n")
            append("- Include examples of the persona's reasoning process\n")
            append("- Emphasize ethical considerations and thoughtful responses\n")
            append("- Aim for 800-1500 words\n\n")
            append("Create a sophisticated system prompt that captures the full depth and nuance of this persona.")
        }

        PlatformType.GEMINI -> {
            append("You are tasked with creating a Gemini-optimized prompt based on a comprehensive persona. The goal is to transform the detailed persona into a system prompt that leverages Gemini's multimodal capabilities, reasoning skills, and practical problem-solving approach.\n\n")
            append("CRITICAL OUTPUT INSTRUCTION: Begin your response directly without any preamble, introduction, or explanatory text.\n\n")
            append("INPUT PERSONA:\n$fullPersona\n\n")
            append("INSTRUCTIONS:\n")
            append("Create a Gemini system prompt that establishes clear identity with practical, actionable characteristics. Capture the persona's problem-solving style, information processing approach, and engagement methods. Leverage Gemini's strengths in reasoning, analysis, and practical task orientation.\n\n")
            append("FORMAT REQUIREMENTS:\n")
            append("- Start with clear role and capability definition\n")
            append("- Use structured approach to complex tasks\n")
            append("- Include examples of step-by-step reasoning\n")
            append("- Specify output formats and organization preferences\n")
            append("- Target 600-1200 words\n\n")
            append("Create a practical and effective system prompt that enables this persona to work efficiently across various tasks.")
        }

        PlatformType.CHATGPT -> {
            append("You are tasked with creating a ChatGPT-optimized prompt based on a comprehensive persona. The goal is to transform the detailed persona into a system prompt that works effectively with ChatGPT's conversational interface and capabilities.\n\n")
            append("CRITICAL OUTPUT INSTRUCTION: Begin your response directly without any preamble, introduction, or explanatory text.\n\n")
            append("INPUT PERSONA:\n$fullPersona\n\n")
            append("INSTRUCTIONS:\n")
            append("Create a ChatGPT system prompt that clearly establishes the persona's identity and expertise. Capture the unique voice, tone, and speaking patterns. Define knowledge scope and boundaries. Include specific behavioral traits and characteristic expressions.\n\n")
            append("FORMAT REQUIREMENTS:\n")
            append("- Start with \"You are [persona]...\"\n")
            append("- Use clear, directive language\n")
            append("- Include specific examples of how the persona would respond\n")
            append("- Keep under 2000 tokens\n")
            append("- Structure with clear sections\n\n")
            append("Create a comprehensive yet concise system prompt that embodies this persona effectively.")
        }

        PlatformType.GENERIC -> {
            append("You are an expert AI persona designer. Your task is to create a condensed, prompt-ready version of a comprehensive persona. Distill the essential characteristics into a concise format optimized for immediate use in AI prompts while preserving the core identity and most important traits.\n\n")
            append("CRITICAL OUTPUT INSTRUCTION: Begin your response directly without any preamble, introduction, or explanatory text.\n\n")
            append("INPUT PERSONA:\n$fullPersona\n\n")
            append("INSTRUCTIONS:\n")
            append("Create a condensed persona version that includes:\n\n")
            append("1. **Core Identity**: Capture the most essential aspects of role, expertise, and worldview\n")
            append("2. **Key Characteristics**: Include the 5-7 most important personality traits and behaviors\n")
            append("3. **Communication Style**: Distill the unique voice, tone, and expression patterns\n")
            append("4. **Essential Knowledge**: Include only the most critical areas of expertise\n")
            append("5. **Specific Behaviors & Phrases**: 3-5 specific behavioral examples or phrases\n\n")
            append("FORMAT REQUIREMENTS:\n")
            append("- Start with: **Identity**: You are [one-sentence description]\n")
            append("- Then **Key Characteristics**: with bullet points\n")
            append("- Then **Communication Style**: paragraph\n")
            append("- Then **Essential Knowledge**: paragraph\n")
            append("- Then **Specific Behaviors & Phrases**: bullet points\n")
            append("- Then **General Response Guidelines**: bullet points\n")
            append("- Target 300-600 words total\n")
            append("- No preamble, no introduction")
        }
    }
}

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
fun PluginSettingsCard(onSwitchPersona: (String) -> Unit = {}, onPersonaSelected: () -> Unit = {}) {
    val appSettings: AppSettings = koinInject()
    val dataRepository: DataRepository = koinInject()
    val personaManager = remember { PersonaManager(appSettings) }
    val scope = PersonaGeneratorState.scope

    val currentServiceName = remember { dataRepository.currentService().displayName }

    val startingText = stringResource(Res.string.plugin_persona_generator_starting)
    val generatingText = stringResource(Res.string.plugin_persona_generator_status_generating)
    val generatingSecondsFormat = stringResource(Res.string.plugin_persona_generator_status_generating_seconds)
    val errorEmptyText = stringResource(Res.string.plugin_persona_generator_error_empty)
    val doneFormat = stringResource(Res.string.plugin_persona_generator_status_done)
    val errorFailedText = stringResource(Res.string.plugin_persona_generator_error_failed)

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
            text = stringResource(Res.string.plugin_persona_generator_plugins),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            Text(
                text = stringResource(Res.string.plugin_persona_generator_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.plugin_persona_generator_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(Res.string.plugin_persona_generator_using, currentServiceName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text(stringResource(Res.string.plugin_persona_generator_prompt_label)) },
                placeholder = { Text(stringResource(Res.string.plugin_persona_generator_prompt_placeholder)) },
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
                    ) { Text(stringResource(Res.string.plugin_persona_generator_generate_another)) }

                    Spacer(Modifier.width(12.dp))

                    OutlinedButton(
                        onClick = {
                            usingCondensed = !usingCondensed
                            val id = generatedConfig!!.id
                            if (usingCondensed) {
                                personaManager.savePersona(generatedConfig!!.copy(defaultSoul = condensedSoul))
                                personaManager.setActivePersonaId(id)
                            } else {
                                val full = appSettings.settings.getString("persona_full_$id", fullSoul)
                                personaManager.savePersona(generatedConfig!!.copy(defaultSoul = full))
                                personaManager.setActivePersonaId(id)
                            }
                            onPersonaSelected()
                        },
                        modifier = Modifier.handCursor(),
                    ) { Text(if (usingCondensed) stringResource(Res.string.plugin_persona_generator_switch_full) else stringResource(Res.string.plugin_persona_generator_switch_condensed)) }

                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (usingCondensed) stringResource(Res.string.plugin_persona_generator_using_condensed) else stringResource(Res.string.plugin_persona_generator_using_full),
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
                            statusText = startingText
                            sentPrompt = ""
                            step1Response = ""
                            step2Response = ""
                            currentStep = 0
                            scope.launch {
                                try {
                                    val description = prompt.trim()
                                    val platform = platformForService(currentServiceName)

                                    // Step 1: generate full persona
                                    currentStep = 1
                                    statusText = generatingText
                                    val step1Prompt = buildFullPersonaPrompt(description)
                                    sentPrompt = step1Prompt

                                    val timerJob = launch {
                                        var seconds = 0
                                        while (true) {
                                            delay(1000)
                                            seconds++
                                            statusText = "[Step 1/2] ${generatingSecondsFormat.format(seconds)}"
                                        }
                                    }

                                    val fullResponse = try {
                                        dataRepository.askSilently(step1Prompt, 300_000L)
                                    } finally {
                                        timerJob.cancel()
                                    }

                                    if (fullResponse.isBlank()) {
                                        error = errorEmptyText
                                        return@launch
                                    }
                                    step1Response = fullResponse.take(5000)
                                    fullSoul = fullResponse.trim()
                                    statusText = "[Step 1/2] ${doneFormat.format(fullResponse.length)}"

                                    // Step 2: generate platform-adapted condensed version
                                    currentStep = 2
                                    val step2Prompt = buildCondensedPrompt(fullSoul, platform)

                                    val timerJob2 = launch {
                                        var seconds = 0
                                        while (true) {
                                            delay(1000)
                                            seconds++
                                            statusText = "[Step 2/2] ${generatingSecondsFormat.format(seconds)}"
                                        }
                                    }

                                    val condensedResponse = try {
                                        dataRepository.askSilently(step2Prompt, 300_000L)
                                    } finally {
                                        timerJob2.cancel()
                                    }

                                    if (condensedResponse.isBlank()) {
                                        condensedSoul = fullSoul
                                    } else {
                                        condensedSoul = condensedResponse.trim()
                                    }
                                    step2Response = condensedSoul.take(5000)
                                    statusText = "Full: ${doneFormat.format(fullSoul.length)} | Condensed: ${doneFormat.format(condensedSoul.length)}"

                                    val name = description.split(" ").take(3).joinToString(" ").replaceFirstChar(Char::uppercase)
                                    val id = "persona_${Uuid.random().toString().take(8)}"

                                    appSettings.settings.putString("persona_full_$id", fullSoul)
                                    appSettings.settings.putString("persona_condensed_$id", condensedSoul)

                                    val shortDesc = condensedSoul.lineSequence()
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
                                        defaultSoul = condensedSoul,
                                        renderMode = RenderMode.CHARACTER,
                                        isBuiltIn = false,
                                    )
                                    dataRepository.savePersona(config)
                                    onSwitchPersona(config.id)
                                    generatedConfig = config
                                    onPersonaSelected()
                                    usingCondensed = true
                                } catch (e: Exception) {
                                    error = e.message ?: errorFailedText
                                } finally {
                                    generating = false
                                }
                            }
                        },
                        enabled = !generating && prompt.isNotBlank(),
                        modifier = Modifier.handCursor(),
                    ) { Text(stringResource(Res.string.plugin_persona_generator_generate)) }

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
                Text(stringResource(Res.string.plugin_persona_generator_generated, generatedConfig!!.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
            }

            if (step1Response.isNotBlank()) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(Res.string.plugin_persona_generator_raw_response), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
