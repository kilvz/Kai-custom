package com.kai.custom.skills

data class SkillManifest(
    val id: String,
    val displayName: String,
    val description: String,
    val body: String,
    val bundledFilePaths: List<String> = emptyList(),
    val isBuiltIn: Boolean = false,
)

sealed class SkillSource {
    data class GitHub(
        val owner: String,
        val repo: String,
        val ref: String = "main",
        val path: String,
    ) : SkillSource()

    data class ClawHub(
        val slug: String,
    ) : SkillSource()
}

data class DownloadedSkill(
    val id: String,
    val description: String,
    val rawSkillMd: String,
    val files: Map<String, String> = emptyMap(),
)

data class RegistrySkillEntry(
    val id: String,
    val description: String,
    val displayName: String = "",
    val owner: String = "",
    val repo: String = "",
    val ref: String = "",
    val skillPath: String = "",
    val ownerHandle: String = "",
    val requiresSandbox: Boolean = false,
    val sourceName: String = "",
    val slug: String? = null,
    val securityStatus: String? = null,
)
