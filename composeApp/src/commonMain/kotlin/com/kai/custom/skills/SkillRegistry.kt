package com.kai.custom.skills

import com.kai.custom.data.SharedJson
import com.kai.custom.httpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SkillRegistry(
    private val client: HttpClient = httpClient(),
    private val json: Json = SharedJson,
) {

    private val sandboxExtensions = setOf("py", "sh", "js", "ts", "rb", "pl", "lua")

    suspend fun browseMarketplaces(marketplaces: List<SkillMarketplace>): Result<List<RegistrySkillEntry>> = runCatching {
        coroutineScope {
            marketplaces
                .map { async { browseMarketplace(it).getOrNull().orEmpty() } }
                .awaitAll()
                .flatten()
                .distinctBy { it.slug ?: "${it.owner}/${it.repo}/${it.skillPath}" }
        }
    }

    suspend fun browseMarketplace(marketplace: SkillMarketplace): Result<List<RegistrySkillEntry>> = runCatching {
        val (owner, repo, ref) = Triple(marketplace.owner, marketplace.repo, marketplace.ref)
        val treePaths = fetchRepoTree(owner, repo, ref)

        val manifest = if (marketplace.skills != null) {
            null
        } else {
            fetchRawFile(owner, repo, ref, MARKETPLACE_MANIFEST_PATH)
                ?.let { runCatching { parseMarketplaceManifest(it) }.getOrNull() }
        }

        val skillDirs = selectSkillDirs(treePaths, marketplace.skills, manifest, marketplace.root, marketplace.exclude)

        coroutineScope {
            skillDirs.map { dir ->
                async {
                    val md = fetchRawFile(owner, repo, ref, "$dir/SKILL.md") ?: return@async null
                    val parsed = SkillFrontmatterParser.parse(md)
                    if (parsed !is SkillFrontmatterParser.Result.Ok) return@async null
                    val requiresSandbox = treePaths.any {
                        it.startsWith("$dir/") && it.substringAfterLast('.', "").lowercase() in sandboxExtensions
                    }
                    RegistrySkillEntry(
                        id = parsed.id,
                        description = parsed.description,
                        owner = owner,
                        repo = repo,
                        ref = ref,
                        skillPath = dir,
                        requiresSandbox = requiresSandbox,
                        sourceName = marketplace.name,
                    )
                }
            }.awaitAll().filterNotNull().sortedBy { it.id }
        }
    }

    suspend fun fetchSkillFiles(source: SkillSource): Result<DownloadedSkill> = runCatching {
        when (source) {
            is SkillSource.GitHub -> fetchGitHubSkill(source.owner, source.repo, source.ref, source.path)
            is SkillSource.ClawHub -> fetchClawHubSkill(source.slug).getOrThrow()
        }
    }

    private suspend fun fetchGitHubSkill(owner: String, repo: String, ref: String, path: String): DownloadedSkill {
        val skillMd = fetchRawFile(owner, repo, ref, "$path/SKILL.md")
            ?: error("SKILL.md not found at $owner/$repo:$ref:$path/SKILL.md")
        val parsed = when (val r = SkillFrontmatterParser.parse(skillMd)) {
            is SkillFrontmatterParser.Result.Ok -> r
            is SkillFrontmatterParser.Result.Err -> error("Invalid SKILL.md frontmatter: ${r.reason}")
        }

        val tree = runCatching { fetchRepoTree(owner, repo, ref) }.getOrDefault(emptySet())
        val siblings = tree
            .filter { it.startsWith("$path/") && it != "$path/SKILL.md" }
            .map { it.removePrefix("$path/") }

        val files = coroutineScope {
            siblings
                .filter { it.substringAfterLast('.', "").lowercase() !in BINARY_EXTENSIONS }
                .map { file ->
                    async {
                        val content = fetchRawFile(owner, repo, ref, "$path/$file")
                        if (content != null && content.length <= MAX_BUNDLED_FILE_CHARS) file to content else null
                    }
                }
                .awaitAll()
                .filterNotNull()
                .toMap()
        }

        return DownloadedSkill(
            id = parsed.id,
            description = parsed.description,
            rawSkillMd = skillMd,
            files = files,
        )
    }

    suspend fun searchClawHub(query: String): Result<List<RegistrySkillEntry>> = runCatching {
        val response = client.get("$CLAWHUB_API_BASE/search") {
            parameter("q", query)
        }
        if (!response.status.isSuccess()) return@runCatching emptyList<RegistrySkillEntry>()
        val body = response.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject
        val results = root["results"]?.jsonArray ?: root["skills"]?.jsonArray ?: return@runCatching emptyList()
        results.mapNotNull { entry ->
            val obj = entry.jsonObject
            val slug = obj["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val displayName = obj["displayName"]?.jsonPrimitive?.contentOrNull ?: slug
            val summary = obj["summary"]?.jsonPrimitive?.contentOrNull ?: ""
            val ownerHandle = obj["ownerHandle"]?.jsonPrimitive?.contentOrNull ?: ""
            RegistrySkillEntry(
                id = slug,
                description = summary,
                displayName = displayName,
                slug = slug,
                ownerHandle = ownerHandle,
                sourceName = "ClawHub",
            )
        }
    }

    suspend fun fetchClawHubSkill(slug: String): Result<DownloadedSkill> = runCatching {
        val detailUrl = "$CLAWHUB_API_BASE/skills/$slug"
        val detailResponse = client.get(detailUrl)
        if (!detailResponse.status.isSuccess()) error("Skill '$slug' not found on ClawHub")
        val detailBody = detailResponse.bodyAsText()
        val detailRoot = json.parseToJsonElement(detailBody).jsonObject

        val version = detailRoot["latestVersion"]?.jsonObject
            ?.get("version")?.jsonPrimitive?.contentOrNull ?: "latest"

        val skillMd = fetchClawHubFile(slug, "SKILL.md", version)
            ?: error("Failed to download SKILL.md for '$slug'")
        val parsed = when (val r = SkillFrontmatterParser.parse(skillMd)) {
            is SkillFrontmatterParser.Result.Ok -> r
            is SkillFrontmatterParser.Result.Err -> error("Invalid SKILL.md frontmatter in '$slug': ${r.reason}")
        }

        DownloadedSkill(
            id = parsed.id,
            description = parsed.description,
            rawSkillMd = skillMd,
            files = emptyMap(),
        )
    }

    suspend fun fetchSecurityData(entries: List<RegistrySkillEntry>): List<RegistrySkillEntry> {
        if (entries.isEmpty()) return entries
        return coroutineScope {
            val versionMap = entries.map { entry ->
                async {
                    val slug = entry.slug ?: return@async null
                    slug to fetchClawHubVersion(slug)
                }
            }.awaitAll().filterNotNull().filter { it.second != null }.map { (slug, v) ->
                slug to v!!
            }.toMap()

            if (versionMap.isEmpty()) return@coroutineScope entries

            val verdicts = fetchSecurityVerdicts(versionMap)

            entries.map { entry ->
                val slug = entry.slug
                if (slug != null && verdicts.containsKey(slug)) {
                    entry.copy(securityStatus = verdicts[slug])
                } else {
                    entry
                }
            }
        }
    }

    private suspend fun fetchClawHubVersion(slug: String): String? {
        return try {
            val response = client.get("$CLAWHUB_API_BASE/skills/$slug")
            if (!response.status.isSuccess()) return null
            val body = response.bodyAsText()
            val skill = json.parseToJsonElement(body).jsonObject["skill"]?.jsonObject ?: return null
            skill["tags"]?.jsonObject?.get("latest")?.jsonPrimitive?.contentOrNull
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchSecurityVerdicts(versionMap: Map<String, String>): Map<String, String> {
        return try {
            val items = versionMap.entries.joinToString(",") { (slug, version) ->
                """{"slug":"$slug","version":"$version"}"""
            }
            val jsonBody = """{"items":[$items]}"""
            val response = client.post("$CLAWHUB_API_BASE/skills/-/security-verdicts") {
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
            }
            if (!response.status.isSuccess()) return emptyMap()
            val body = response.bodyAsText()
            val root = json.parseToJsonElement(body).jsonObject
            val itemsArray = root["items"]?.jsonArray ?: return emptyMap()
            val result = mutableMapOf<String, String>()
            for (item in itemsArray) {
                val obj = item.jsonObject
                val slug = obj["slug"]?.jsonPrimitive?.contentOrNull ?: continue
                val security = obj["security"]?.jsonObject
                val status = security?.get("status")?.jsonPrimitive?.contentOrNull
                if (status != null) result[slug] = status
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private suspend fun fetchClawHubFile(slug: String, path: String, version: String): String? {
        val response = client.get("$CLAWHUB_API_BASE/skills/$slug/file") {
            parameter("path", path)
            parameter("version", version)
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) return null
        return response.bodyAsText()
    }

    private suspend fun fetchRepoTree(owner: String, repo: String, ref: String): Set<String> {
        val url = "https://api.github.com/repos/$owner/$repo/git/trees/$ref?recursive=1"
        val response = client.get(url) {
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        if (!response.status.isSuccess()) return emptySet()
        val body = response.bodyAsText()
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptySet()
        val treeArray = root["tree"] as? JsonArray ?: return emptySet()
        return treeArray.mapNotNull { entry ->
            val obj = entry.jsonObject
            val type = obj["type"]?.jsonPrimitive?.contentOrNull
            val path = obj["path"]?.jsonPrimitive?.contentOrNull
            if (type == "blob" && path != null) path else null
        }.toSet()
    }

    private suspend fun fetchRawFile(owner: String, repo: String, ref: String, path: String): String? {
        val url = "https://raw.githubusercontent.com/$owner/$repo/$ref/$path"
        val response = client.get(url)
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) return null
        return response.bodyAsText()
    }

    companion object {
        private const val CLAWHUB_API_BASE = "https://clawhub.ai/api/v1"
        private const val MARKETPLACE_MANIFEST_PATH = ".claude-plugin/marketplace.json"
        private const val MAX_BUNDLED_FILE_CHARS = 256_000

        private val BINARY_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "svg",
            "zip", "tar", "gz", "bz2", "7z", "rar",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "mp3", "mp4", "wav", "ogg", "flac", "mov", "avi", "webm",
            "ttf", "otf", "woff", "woff2",
            "exe", "dll", "so", "dylib", "bin",
        )

        fun selectSkillDirs(
            treePaths: Set<String>,
            allowlist: List<String>?,
            manifestPaths: List<String>?,
            root: String,
            exclude: Set<String> = emptySet(),
        ): List<String> {
            val hasSkillMd = { dir: String -> "$dir/SKILL.md" in treePaths }
            val selected = when {
                allowlist != null -> allowlist.map { it.trim('/') }.filter(hasSkillMd)

                !manifestPaths.isNullOrEmpty() -> manifestPaths.filter(hasSkillMd)

                else -> {
                    val prefix = "$root/"
                    treePaths
                        .filter { it.startsWith(prefix) && it.endsWith("/SKILL.md") }
                        .map { it.removeSuffix("/SKILL.md") }
                        .filter { it.removePrefix(prefix).none { ch -> ch == '/' } }
                        .distinct()
                }
            }
            return if (exclude.isEmpty()) selected else selected.filter { it.substringAfterLast('/') !in exclude }
        }

        fun parseMarketplaceManifest(jsonText: String): List<String> {
            val root = runCatching { SharedJson.parseToJsonElement(jsonText).jsonObject }.getOrNull() ?: return emptyList()
            val plugins = root["plugins"] as? JsonArray ?: return emptyList()
            val out = mutableListOf<String>()
            for (plugin in plugins) {
                val obj = plugin.jsonObject
                val source = obj["source"]?.jsonPrimitive?.contentOrNull
                if (source != null && source != "./" && source != ".") continue
                val skills = obj["skills"] as? JsonArray ?: continue
                for (s in skills) {
                    val raw = s.jsonPrimitive.contentOrNull ?: continue
                    val normalized = raw.trim().removePrefix("./").trim('/')
                    if (normalized.isNotEmpty()) out.add(normalized)
                }
            }
            return out.distinct()
        }
    }
}
