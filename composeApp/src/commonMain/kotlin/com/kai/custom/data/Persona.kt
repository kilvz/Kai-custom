package com.kai.custom.data

data class Persona(
    val name: String,
    val description: String,
) {
    /** Converts to a soul-like system prompt segment. */
    fun toSoulSegment(): String = "You are $name. $description"
}

class PersonaManager(private val appSettings: AppSettings) {

    fun getCurrentPersonaName(): String = appSettings.getPersonaName()

    fun setCurrentPersonaName(name: String) {
        appSettings.setPersonaName(name)
    }

    companion object {
        const val KEY_PERSONA_DESCRIPTORS = "persona_descriptors"
    }
}
