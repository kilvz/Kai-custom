package com.kai.custom.data.palace

object PalaceConfig {
    const val WING_AGENT = "agent"
    const val WING_USER = "user"
    const val WING_PROJECT = "project"
    const val WING_SYSTEM = "system"
    const val ROOM_MEMORIES = "memories"
    const val ROOM_PREFERENCES = "preferences"
    const val ROOM_LEARNINGS = "learnings"
    const val ROOM_ERRORS = "errors"
    const val ROOM_PROJECT = "project"

    data class DefaultWing(
        val id: String,
        val name: String,
        val description: String,
        val defaultRoom: String,
    )

    val defaultWings = listOf(
        DefaultWing(WING_AGENT, "Agent", "Agent's learned memories and knowledge", ROOM_MEMORIES),
        DefaultWing(WING_USER, "User", "User preferences and profile", ROOM_PREFERENCES),
        DefaultWing(WING_PROJECT, "Project", "Project-specific context and decisions", ROOM_PROJECT),
        DefaultWing(WING_SYSTEM, "System", "System configuration and diagnostics", ROOM_MEMORIES),
    )

    val defaultRooms = mapOf(
        WING_AGENT to listOf(
            ROOM_MEMORIES to "General memories and observations",
            ROOM_LEARNINGS to "Things that worked well",
            ROOM_ERRORS to "Error resolutions and known issues",
        ),
        WING_USER to listOf(
            ROOM_PREFERENCES to "User preferences and corrections",
            ROOM_MEMORIES to "Facts about the user",
        ),
        WING_PROJECT to listOf(
            ROOM_PROJECT to "Project decisions and context",
            ROOM_MEMORIES to "Project notes",
        ),
        WING_SYSTEM to listOf(
            ROOM_MEMORIES to "System state",
            ROOM_ERRORS to "System errors",
        ),
    )
}
