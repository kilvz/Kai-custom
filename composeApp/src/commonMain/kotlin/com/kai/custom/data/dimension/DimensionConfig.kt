package com.kai.custom.data.dimension

object DimensionConfig {
    const val REALM_AGENT = "agent"
    const val REALM_USER = "user"
    const val REALM_PROJECT = "project"
    const val REALM_SYSTEM = "system"
    const val DOMAIN_MEMORIES = "memories"
    const val DOMAIN_PREFERENCES = "preferences"
    const val DOMAIN_LEARNINGS = "learnings"
    const val DOMAIN_ERRORS = "errors"
    const val DOMAIN_PROJECT = "project"

    data class DefaultRealm(
        val id: String,
        val name: String,
        val description: String,
        val defaultDomain: String,
    )

    val defaultRealms = listOf(
        DefaultRealm(REALM_AGENT, "Agent", "Agent's learned memories and knowledge", DOMAIN_MEMORIES),
        DefaultRealm(REALM_USER, "User", "User preferences and profile", DOMAIN_PREFERENCES),
        DefaultRealm(REALM_PROJECT, "Project", "Project-specific context and decisions", DOMAIN_PROJECT),
        DefaultRealm(REALM_SYSTEM, "System", "System configuration and diagnostics", DOMAIN_MEMORIES),
    )

    val defaultDomains = mapOf(
        REALM_AGENT to listOf(
            DOMAIN_MEMORIES to "General memories and observations",
            DOMAIN_LEARNINGS to "Things that worked well",
            DOMAIN_ERRORS to "Error resolutions and known issues",
        ),
        REALM_USER to listOf(
            DOMAIN_PREFERENCES to "User preferences and corrections",
            DOMAIN_MEMORIES to "Facts about the user",
        ),
        REALM_PROJECT to listOf(
            DOMAIN_PROJECT to "Project decisions and context",
            DOMAIN_MEMORIES to "Project notes",
        ),
        REALM_SYSTEM to listOf(
            DOMAIN_MEMORIES to "System state",
            DOMAIN_ERRORS to "System errors",
        ),
    )
}
