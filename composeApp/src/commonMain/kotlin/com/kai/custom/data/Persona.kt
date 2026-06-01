package com.kai.custom.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class PersonaPromptStyle { KAI, ALT, CUSTOM }

enum class PersonaHeartbeatStyle { KAI, ALT }

@Serializable
data class PersonaConfig(
    val id: String,
    val name: String,
    val description: String = "",
    val style: PersonaPromptStyle = PersonaPromptStyle.KAI,
    val heartbeatStyle: PersonaHeartbeatStyle = PersonaHeartbeatStyle.KAI,
    val isBuiltIn: Boolean = false,
    val defaultSoul: String = "",
)

class PersonaManager(private val appSettings: AppSettings) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun getAllPersonas(): List<PersonaConfig> {
        val raw = appSettings.settings.getStringOrNull(KEY_PERSONA_LIST) ?: return builtIns
        return try {
            val stored = json.decodeFromString<List<PersonaConfig>>(raw)
            // built-ins first (canonical names), then custom stored entries
            val builtInIds = builtIns.map { it.id }.toSet()
            val merged = builtIns + stored.filter { it.id !in builtInIds }
            merged.distinctBy { it.id }
        } catch (_: Exception) {
            builtIns
        }
    }

    fun getPersona(id: String): PersonaConfig? = getAllPersonas().find { it.id == id }

    fun savePersona(config: PersonaConfig) {
        val all = getAllPersonas().toMutableList()
        val idx = all.indexOfFirst { it.id == config.id }
        if (idx >= 0) all[idx] = config else all.add(config)
        saveAll(all)
    }

    fun deletePersona(id: String) {
        val config = getPersona(id)
        if (config?.isBuiltIn == true) return // cannot delete built-ins
        val all = getAllPersonas().toMutableList()
        all.removeAll { it.id == id }
        saveAll(all)
        if (getActivePersonaId() == id) setActivePersonaId(builtIns.first().id)
    }

    fun getActivePersonaId(): String = appSettings.settings.getString(KEY_ACTIVE_PERSONA_ID, builtIns.first().id)

    fun setActivePersonaId(id: String) {
        appSettings.settings.putString(KEY_ACTIVE_PERSONA_ID, id)
    }

    fun getActivePersona(): PersonaConfig = getPersona(getActivePersonaId()) ?: builtIns.first()

    /** Soul key helpers — per-persona storage in AppSettings. */
    fun getSoulUserKey(personaId: String): String = "soul_user_$personaId"
    fun getSoulAutoKey(personaId: String): String = "soul_auto_$personaId"

    private fun saveAll(personas: List<PersonaConfig>) {
        appSettings.settings.putString(KEY_PERSONA_LIST, json.encodeToString(personas))
    }

    companion object {
        const val KEY_PERSONA_DESCRIPTORS = "persona_descriptors"
        const val KEY_PERSONA_LIST = "persona_list"
        const val KEY_ACTIVE_PERSONA_ID = "active_persona_id"

        val builtIns: List<PersonaConfig> = listOf(
            PersonaConfig(
                id = "kai",
                name = "Kai",
                description = "Default persona with full upstream behavioral guardrails",
                style = PersonaPromptStyle.KAI,
                heartbeatStyle = PersonaHeartbeatStyle.KAI,
                isBuiltIn = true,
            ),
            PersonaConfig(
                id = "alt",
                name = "Alt",
                description = "Pragmatic operator persona with strong tool use, memory, and follow-through",
                style = PersonaPromptStyle.ALT,
                heartbeatStyle = PersonaHeartbeatStyle.ALT,
                isBuiltIn = true,
                defaultSoul = """
                    You are Alt — a pragmatic, direct, tool-using operator.

                    Core behavior:
                    - Be useful first. No fluff, no filler, no performative politeness.
                    - Inspect before assuming. Use available tools to verify facts, files, settings, logs, and current state.
                    - Prefer action over explanation when the user asks for work to be done.
                    - Persist until the task is handled end-to-end, or clearly state the blocker and what was verified.
                    - Never fabricate tool outputs, file contents, command results, citations, or completed work.
                    - Preserve user state. Do not undo, overwrite, delete, or reset user work unless explicitly asked.
                    - When you make changes, keep them minimal, targeted, and easy to review.
                    - Communicate directly: what changed, what was verified, and what remains.

                    Memory behavior:
                    - Treat memory as part of your working context when it is enabled.
                    - Search memory before re-solving recurring problems or asking the user to repeat known facts.
                    - Store durable user preferences, corrections, project facts, decisions, fixes that worked, and error resolutions.
                    - Reinforce memories that successfully guide later work.
                    - Do not store transient chatter, guesses, secrets, or one-off noise.
                    - If memory conflicts with current evidence or user correction, trust the current evidence/user and update memory.

                    Operating style:
                    - Be concise, but not vague.
                    - Be honest over agreeable.
                    - Be opinionated when the best path is clear.
                    - Ask only when genuinely blocked or when a choice changes the outcome.
                    - If a first attempt fails, diagnose and try the next reasonable path.
                    - Summarize noisy output instead of dumping logs.
                    - Privacy first.
                """.trimIndent(),
            ),
        )
    }
}
