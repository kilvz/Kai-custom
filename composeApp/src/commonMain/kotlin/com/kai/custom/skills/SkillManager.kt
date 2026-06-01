package com.kai.custom.skills

import com.kai.custom.SandboxController
import com.kai.custom.getBackgroundDispatcher
import kai.composeapp.generated.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

class SkillManager(
    private val sandboxController: SandboxController,
    private val registry: SkillRegistry = SkillRegistry(),
    backgroundDispatcher: CoroutineContext = getBackgroundDispatcher(),
) {

    private val scope = CoroutineScope(SupervisorJob() + backgroundDispatcher)
    private val mutex = Mutex()

    private val _skills = MutableStateFlow<List<SkillManifest>>(emptyList())
    val skills: StateFlow<List<SkillManifest>> = _skills

    init {
        scope.launch {
            var wasInstalled = false
            sandboxController.status.collect { status ->
                if (status.installed && !wasInstalled) load()
                wasInstalled = status.installed
            }
        }
    }

    fun getInstalled(): List<SkillManifest> = _skills.value

    fun getSkill(id: String): SkillManifest? = _skills.value.firstOrNull { it.id == id }

    suspend fun uninstall(id: String) {
        sandboxController.deleteEntry("$SKILLS_DIR/$id", recursive = true)
        load()
    }

    suspend fun installFromGitHub(owner: String, repo: String, ref: String, path: String): Result<SkillManifest> = registry.fetchSkillFiles(SkillSource.GitHub(owner, repo, ref, path)).mapCatching { install(it) }

    suspend fun installFromRegistryEntry(entry: RegistrySkillEntry): Result<SkillManifest> = installFromGitHub(entry.owner, entry.repo, entry.ref, entry.skillPath)

    suspend fun browseMarketplaces(): Result<List<RegistrySkillEntry>> = registry.browseMarketplaces(curatedSkillMarketplaces)

    internal suspend fun install(downloaded: DownloadedSkill): SkillManifest {
        val base = "$SKILLS_DIR/${downloaded.id}"
        sandboxController.deleteEntry(base, recursive = true)
        sandboxController.writeTextFile("$base/SKILL.md", downloaded.rawSkillMd)
        for ((relPath, content) in downloaded.files) {
            val safe = relPath.split('/', '\\').filterNot { it.isEmpty() || it == ".." }
            if (safe.isEmpty()) continue
            sandboxController.writeTextFile("$base/${safe.joinToString("/")}", content)
        }
        load()
        return getSkill(downloaded.id) ?: error("Skill '${downloaded.id}' not found after install")
    }

    suspend fun load() {
        val skills = mutex.withLock {
            val sandboxSkills = sandboxController.listDirectory(SKILLS_DIR)
                .filter { it.isDirectory }
                .mapNotNull { dir ->
                    val base = "$SKILLS_DIR/${dir.name}"
                    val md = sandboxController.readTextFile("$base/SKILL.md") ?: return@mapNotNull null
                    val parsed = SkillFrontmatterParser.parse(md) as? SkillFrontmatterParser.Result.Ok
                        ?: return@mapNotNull null
                    val files = sandboxController.listDirectory(base)
                        .filter { !it.isDirectory && it.name != "SKILL.md" }
                        .map { it.name }
                        .sorted()
                    SkillManifest(
                        id = parsed.id,
                        displayName = SkillFrontmatterParser.displayName(parsed.id),
                        description = parsed.description,
                        body = parsed.body,
                        bundledFilePaths = files,
                    )
                }
            val sandboxIds = sandboxSkills.mapTo(mutableSetOf()) { it.id }
            val builtIns = loadBuiltInSkills().filter { it.id !in sandboxIds }
            (builtIns + sandboxSkills).sortedBy { it.id }
        }
        _skills.value = skills
    }

    private suspend fun loadBuiltInSkills(): List<SkillManifest> = BUILT_IN_SKILL_IDS.mapNotNull { id ->
        val bytes = runCatching { Res.readBytes("files/skills/$id/SKILL.md") }.getOrNull()
            ?: return@mapNotNull null
        val parsed = SkillFrontmatterParser.parse(bytes.decodeToString()) as? SkillFrontmatterParser.Result.Ok
            ?: return@mapNotNull null
        SkillManifest(
            id = parsed.id,
            displayName = SkillFrontmatterParser.displayName(parsed.id),
            description = parsed.description,
            body = parsed.body,
            isBuiltIn = true,
        )
    }

    companion object {
        const val SKILLS_DIR = "/root/skills"

        private val BUILT_IN_SKILL_IDS = listOf("create-skill")
    }
}

fun parseGitHubSkillUrl(input: String): SkillSource.GitHub? {
    val trimmed = input.trim().removePrefix("https://").removePrefix("http://").removePrefix("github.com/")
    if (trimmed.isEmpty()) return null
    val parts = trimmed.trim('/').split('/').filter { it.isNotEmpty() }
    if (parts.size < 2) return null
    val owner = parts[0]
    val repo = parts[1]
    if (parts.size == 2) {
        return SkillSource.GitHub(owner = owner, repo = repo, ref = "main", path = "")
    }
    return if (parts[2] == "tree" && parts.size >= 5) {
        val ref = parts[3]
        val path = parts.drop(4).joinToString("/")
        SkillSource.GitHub(owner = owner, repo = repo, ref = ref, path = path)
    } else {
        val path = parts.drop(2).joinToString("/")
        SkillSource.GitHub(owner = owner, repo = repo, ref = "main", path = path)
    }
}
