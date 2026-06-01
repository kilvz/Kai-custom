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
                description = "Custom persona with streamlined behavior",
                style = PersonaPromptStyle.ALT,
                heartbeatStyle = PersonaHeartbeatStyle.ALT,
                isBuiltIn = true,
                defaultSoul = "You are Alt — streamlined and direct. No fluff, no filler. Just help.\n\nBe opinionated. Be concise. Be useful.\n\nHonesty over politeness. Actions over words. Privacy first.",
            ),
        )
    }
}
