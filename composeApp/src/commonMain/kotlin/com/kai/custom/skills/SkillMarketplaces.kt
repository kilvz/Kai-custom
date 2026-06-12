package com.kai.custom.skills

data class SkillMarketplace(
    val name: String,
    val owner: String,
    val repo: String,
    val ref: String = "main",
    val root: String = "skills",
    val skills: List<String>? = null,
    val exclude: Set<String> = emptySet(),
)

val curatedSkillMarketplaces: List<SkillMarketplace> = listOf(
    SkillMarketplace(
        name = "Anthropic",
        owner = "anthropics",
        repo = "skills",
        ref = "main",
        root = "skills",
        exclude = setOf(
            "mcp-builder",
            "skill-creator",
            "theme-factory",
            "web-artifacts-builder",
            "webapp-testing",
            "internal-comms",
            "frontend-design",
            "doc-coauthoring",
            "canvas-design",
            "brand-guidelines",
            "claude-api",
        ),
    ),
    SkillMarketplace(
        name = "Superpowers",
        owner = "obra",
        repo = "superpowers",
        ref = "main",
        skills = listOf(
            "skills/brainstorming",
            "skills/writing-plans",
        ),
    ),
)
