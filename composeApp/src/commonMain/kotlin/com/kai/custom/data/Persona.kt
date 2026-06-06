package com.kai.custom.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class PersonaPromptStyle { KAI, ALT, CUSTOM }

enum class BehaviorStyle(val displayName: String) {
    ASSISTANT("Assistant"),
    OPERATOR("Operator"),
    CUSTOM("Custom"),
}

enum class LanguageStyle(val displayName: String) {
    FORMAL("Formal"),
    CASUAL("Casual"),
    TECHNICAL("Technical"),
    CREATIVE("Creative"),
    MINIMAL("Minimal"),
}

enum class CharacterType(val displayName: String) {
    HELPER("Helper"),
    EXPERT("Expert"),
    COMPANION("Companion"),
    CRITIC("Critic"),
    CREATOR("Creator"),
}

@Serializable
data class PersonaConfig(
    val id: String,
    val name: String,
    val description: String = "",
    val behaviorStyle: BehaviorStyle = BehaviorStyle.ASSISTANT,
    val languageStyle: LanguageStyle = LanguageStyle.CASUAL,
    val characterType: CharacterType = CharacterType.HELPER,
    val skills: List<String> = emptyList(),
    val isBuiltIn: Boolean = false,
    val defaultSoul: String = "",
    val renderMode: RenderMode = RenderMode.FORK_ENHANCED,
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
        if (config?.isBuiltIn == true) return
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

    fun getSoulUserKey(personaId: String): String = "soul_user_$personaId"
    fun getSoulAutoKey(personaId: String): String = "soul_auto_$personaId"

    private fun saveAll(personas: List<PersonaConfig>) {
        appSettings.settings.putString(KEY_PERSONA_LIST, json.encodeToString(personas))
    }

    companion object {
        const val KEY_PERSONA_DESCRIPTORS = "persona_descriptors"
        const val KEY_PERSONA_LIST = "persona_list"
        const val KEY_ACTIVE_PERSONA_ID = "active_persona_id"

        val builtIns: List<PersonaConfig> = PersonaCatalog.all
    }
}

fun PersonaConfig.toBehaviorTraitBlock(): String = buildString {
    append("- Language: ")
    append(languageStyle.displayName)
    append('\n')
    append("- Role: ")
    append(behaviorStyle.displayName)
    append(" \u2014 ")
    append(
        when (behaviorStyle) {
            BehaviorStyle.ASSISTANT -> "offer help, confirm before acting"
            BehaviorStyle.OPERATOR -> "execute tasks silently, concise output"
            BehaviorStyle.CUSTOM -> "follow the instructions above"
        },
    )
    append('\n')
    append("- Character: ")
    append(characterType.displayName)
    append(" \u2014 ")
    append(
        when (characterType) {
            CharacterType.HELPER -> "patient, supportive tone"
            CharacterType.EXPERT -> "authoritative, knowledgeable tone"
            CharacterType.COMPANION -> "warm, friendly tone"
            CharacterType.CRITIC -> "analytical, constructive tone"
            CharacterType.CREATOR -> "innovative, proactive tone"
        },
    )
}
