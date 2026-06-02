package com.kai.custom.data

import com.kai.custom.data.dimension.DimensionConfig
import com.kai.custom.data.dimension.KGFact
import com.kai.custom.mcp.McpClient
import com.kai.custom.runBlockingCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

class AltMemoryClient(
    private val client: McpClient,
    private val appSettings: AppSettings,
) : MemoryStore {

    // Realm/domain mapping mirrors SqliteMemoryStore.DimensionConfig constants
    private fun realmForCategory(category: MemoryCategory): String = when (category) {
        MemoryCategory.GENERAL,
        MemoryCategory.LEARNING,
        MemoryCategory.ERROR,
        -> DimensionConfig.REALM_AGENT

        MemoryCategory.PREFERENCE -> DimensionConfig.REALM_USER
    }

    private fun domainForCategory(category: MemoryCategory): String = when (category) {
        MemoryCategory.GENERAL -> DimensionConfig.DOMAIN_MEMORIES
        MemoryCategory.PREFERENCE -> DimensionConfig.DOMAIN_PREFERENCES
        MemoryCategory.LEARNING -> DimensionConfig.DOMAIN_LEARNINGS
        MemoryCategory.ERROR -> DimensionConfig.DOMAIN_ERRORS
    }

    private fun buildMetadata(
        key: String,
        category: MemoryCategory,
        source: String?,
        hitCount: Int = 1,
        protected: Boolean = false,
    ): JsonObject = buildJsonObject {
        put("memory_key", JsonPrimitive(key))
        put("category", JsonPrimitive(category.name))
        put("hit_count", JsonPrimitive(hitCount.toString()))
        put("type", JsonPrimitive("memory_entry"))
        if (source != null) put("source", JsonPrimitive(source))
        if (protected) put("protected", JsonPrimitive("true"))
    }

    override suspend fun setPersona(personaId: String) {
        try {
            client.callTool(
                "set_persona",
                buildJsonObject { put("name", JsonPrimitive(personaId)) },
            )
        } catch (_: Exception) { }
    }

    override suspend fun fetchRemotePersonas(): List<PersonaConfig> = try {
        val response = client.callTool("list_personas", buildJsonObject { })
        val parsed = parseJsonElement(response)
        val arr = parsed.jsonObject["personas"]?.jsonArray ?: return emptyList()
        arr.mapNotNull { elem ->
            val obj = elem.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val desc = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
            PersonaConfig(
                id = name,
                name = name,
                description = desc,
                style = PersonaPromptStyle.ALT,
                heartbeatStyle = PersonaHeartbeatStyle.ALT,
                isBuiltIn = false,
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    override suspend fun syncPersonaToRemote(config: PersonaConfig) {
        try {
            client.callTool(
                "set_persona",
                buildJsonObject {
                    put("name", JsonPrimitive(config.id))
                    put("description", JsonPrimitive(config.description))
                },
            )
        } catch (_: Exception) { }
    }

    override suspend fun deleteRemotePersona(id: String) {
        try {
            client.callTool(
                "delete_persona",
                buildJsonObject { put("name", JsonPrimitive(id)) },
            )
        } catch (_: Exception) { }
    }

    override suspend fun store(
        key: String,
        content: String,
        category: MemoryCategory,
        source: String?,
    ): MemoryEntry {
        val now = Clock.System.now().toEpochMilliseconds()
        val args = buildJsonObject {
            put("entity_id", JsonPrimitive(key))
            put("realm", JsonPrimitive(realmForCategory(category)))
            put("domain", JsonPrimitive(domainForCategory(category)))
            put("content", JsonPrimitive(content))
            put("metadata", buildMetadata(key, category, source))
        }
        client.callTool("add_entity", args)
        return MemoryEntry(
            key = key,
            content = content,
            createdAt = now,
            updatedAt = now,
            category = category,
            source = source,
        )
    }

    override suspend fun updateContent(key: String, content: String): MemoryEntry? {
        val existing = findEntryByKey(key) ?: return null
        val now = Clock.System.now().toEpochMilliseconds()
        client.callTool(
            "update_entity",
            buildJsonObject {
                put("entity_id", JsonPrimitive(key))
                put("content", JsonPrimitive(content))
                put(
                    "metadata",
                    buildMetadata(
                        key = key,
                        category = existing.category,
                        source = existing.source,
                        hitCount = existing.hitCount,
                        protected = existing.protected,
                    ),
                )
            },
        )
        return existing.copy(content = content, updatedAt = now)
    }

    override suspend fun reinforceMemory(key: String): MemoryEntry? {
        val existing = findEntryByKey(key) ?: return null
        val now = Clock.System.now().toEpochMilliseconds()
        val newHitCount = existing.hitCount + 1
        client.callTool(
            "update_entity",
            buildJsonObject {
                put("entity_id", JsonPrimitive(key))
                put(
                    "metadata",
                    buildMetadata(
                        key = key,
                        category = existing.category,
                        source = existing.source,
                        hitCount = newHitCount,
                        protected = existing.protected,
                    ),
                )
            },
        )
        return existing.copy(hitCount = newHitCount, updatedAt = now)
    }

    override suspend fun storeProtected(
        key: String,
        content: String,
        category: MemoryCategory,
        source: String?,
    ): MemoryEntry {
        val now = Clock.System.now().toEpochMilliseconds()
        val args = buildJsonObject {
            put("entity_id", JsonPrimitive(key))
            put("realm", JsonPrimitive(realmForCategory(category)))
            put("domain", JsonPrimitive(domainForCategory(category)))
            put("content", JsonPrimitive(content))
            put("metadata", buildMetadata(key, category, source, protected = true))
        }
        client.callTool("add_entity", args)
        return MemoryEntry(
            key = key,
            content = content,
            createdAt = now,
            updatedAt = now,
            category = category,
            source = source,
            protected = true,
        )
    }

    override suspend fun forget(key: String): Boolean {
        try {
            val existing = findEntryByKey(key)
            if (existing?.protected == true) return false
            client.callTool(
                "delete_entity",
                buildJsonObject { put("entity_id", JsonPrimitive(key)) },
            )
            return true
        } catch (_: Exception) {
            return false
        }
    }

    override fun getUserMemories(max: Int): List<MemoryEntry> = getAllMemories(max).filter { !it.protected }

    override fun getBehaviorMemories(): List<MemoryEntry> = getAllMemories().filter { it.protected }

    override fun getAllMemories(max: Int): List<MemoryEntry> = try {
        val response = runBlockingCompat {
            client.callTool("export_collection", buildJsonObject { })
        }
        parseEntityList(response).mapNotNull { altEntityToEntry(it) }.take(max)
    } catch (_: Exception) {
        emptyList()
    }

    override fun searchMemories(query: String, limit: Int, mode: String): List<MemoryEntry> {
        if (query.isBlank()) return emptyList()
        return try {
            val response = runBlockingCompat {
                client.callTool(
                    "search",
                    buildJsonObject {
                        put("query", JsonPrimitive(query))
                        put("n_results", JsonPrimitive(limit))
                        put("mode", JsonPrimitive(mode))
                    },
                )
            }
            parseSearchResponse(response)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun getPromotionCandidates(minHits: Int, max: Int): List<MemoryEntry> {
        val all = getAllMemories(max)
        return all.filter { it.hitCount >= minHits }
    }

    override fun exportDimension(): ByteArray = try {
        val json = runBlockingCompat {
            client.callTool("export_collection", buildJsonObject { })
        }
        json.encodeToByteArray()
    } catch (_: Exception) {
        ByteArray(0)
    }

    override fun importDimension(data: ByteArray) {
        try {
            val jsonStr = data.decodeToString()
            val entities = parseJsonElement(jsonStr)
            val arr = when (entities) {
                is JsonArray -> entities
                is JsonObject -> entities["entities"]?.jsonArray ?: entities["results"]?.jsonArray ?: return
                else -> return
            }
            runBlockingCompat {
                client.callTool(
                    "import_entities",
                    buildJsonObject { put("entities", arr) },
                )
            }
        } catch (_: Exception) { }
    }

    // Knowledge graph

    override suspend fun addFact(subject: String, predicate: String, `object`: String): KGFact {
        val now = Clock.System.now().toEpochMilliseconds()
        val response = client.callTool(
            "kg_add",
            buildJsonObject {
                put("subject", JsonPrimitive(subject))
                put("predicate", JsonPrimitive(predicate))
                put("object", JsonPrimitive(`object`))
            },
        )
        return parseKGFact(response) ?: KGFact(
            id = "kg_${subject.hashCode().toUInt().toString(16)}_${predicate.hashCode().toUInt().toString(16)}_$now",
            subject = subject,
            predicate = predicate,
            `object` = `object`,
            createdAt = now,
        )
    }

    override fun queryFacts(entity: String?, relation: String?, limit: Int): List<KGFact> = try {
        val response = runBlockingCompat {
            client.callTool(
                "kg_query",
                buildJsonObject {
                    if (entity != null) put("entity", JsonPrimitive(entity))
                    if (relation != null) put("predicate", JsonPrimitive(relation))
                    put("all", JsonPrimitive(true))
                },
            )
        }
        parseKGFactList(response)
    } catch (_: Exception) {
        emptyList()
    }

    override suspend fun invalidateFact(subject: String, predicate: String, `object`: String) {
        try {
            client.callTool(
                "kg_invalidate",
                buildJsonObject {
                    put("subject", JsonPrimitive(subject))
                    put("predicate", JsonPrimitive(predicate))
                    put("object", JsonPrimitive(`object`))
                },
            )
        } catch (_: Exception) { }
    }

    // Diary

    override suspend fun diaryWrite(agentName: String, content: String, topic: String) {
        client.callTool(
            "record_write",
            buildJsonObject {
                put("agent", JsonPrimitive(agentName))
                put("entry", JsonPrimitive(content))
                put("topic", JsonPrimitive(topic))
            },
        )
    }

    override fun diaryRead(agentName: String, lastN: Int): List<DiaryEntry> = try {
        val response = runBlockingCompat {
            client.callTool(
                "record_read",
                buildJsonObject {
                    put("agent", JsonPrimitive(agentName))
                    put("last_n", JsonPrimitive(lastN.toString()))
                },
            )
        }
        parseDiaryEntryList(response)
    } catch (_: Exception) {
        emptyList()
    }

    // Internal helpers

    private fun findEntryByKey(key: String): MemoryEntry? {
        return try {
            val response = runBlockingCompat {
                client.callTool(
                    "get_entity",
                    buildJsonObject { put("entity_id", JsonPrimitive(key)) },
                )
            }
            if (response.isBlank()) return null
            val parsed = parseJsonElement(response)
            val obj = if (parsed is JsonObject) parsed else return null
            if (obj["found"]?.jsonPrimitive?.content == "false") return null
            altEntityToEntry(obj)
        } catch (_: Exception) {
            null
        }
    }

    // Parse alt-memory search response
    private fun parseSearchResponse(response: String): List<MemoryEntry> {
        if (response.isBlank()) return emptyList()
        return try {
            val parsed = parseJsonElement(response)
            // search returns JSON array directly (from MCP text content)
            val arr = when (parsed) {
                is JsonArray -> parsed

                is JsonObject -> {
                    // Could be wrapped in {results: [...]}
                    parsed["results"]?.jsonArray ?: parsed["memories"]?.jsonArray ?: return emptyList()
                }

                else -> return emptyList()
            }
            arr.mapNotNull { element ->
                val obj = element.jsonObject
                val key = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val content = obj["text"]?.jsonPrimitive?.contentOrNull ?: ""
                val meta = obj["metadata"]?.jsonObject ?: buildJsonObject { }
                parseMemoryEntryFromAlt(key, content, meta)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Parse alt-memory entity representation (from export_collection or get_entity)
    private fun parseEntityList(response: String): List<JsonObject> {
        if (response.isBlank()) return emptyList()
        return try {
            val parsed = parseJsonElement(response)
            when (parsed) {
                is JsonArray -> parsed.mapNotNull { it.jsonObject }

                is JsonObject -> {
                    parsed["results"]?.jsonArray?.mapNotNull { it.jsonObject }
                        ?: parsed["entities"]?.jsonArray?.mapNotNull { it.jsonObject }
                        ?: listOfNotNull(parsed)
                }

                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun altEntityToEntry(obj: JsonObject): MemoryEntry? {
        val key = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: ""
        val meta = obj["metadata"]?.jsonObject ?: buildJsonObject { }
        return parseMemoryEntryFromAlt(key, content, meta)
    }

    private fun parseMemoryEntryFromAlt(key: String, content: String, meta: JsonObject): MemoryEntry? {
        val catStr = meta["category"]?.jsonPrimitive?.contentOrNull ?: "GENERAL"
        val category = try {
            MemoryCategory.valueOf(catStr)
        } catch (_: Exception) {
            MemoryCategory.GENERAL
        }
        val hitCount = meta["hit_count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
        val source = meta["source"]?.jsonPrimitive?.contentOrNull
        val protected = meta["protected"]?.jsonPrimitive?.contentOrNull == "true"
        val createdAt = Clock.System.now().toEpochMilliseconds()
        val updatedAt = createdAt
        return MemoryEntry(
            key = key,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt,
            category = category,
            hitCount = hitCount,
            source = source,
            protected = protected,
        )
    }

    // KG parsing (response format is the same — json array or object with 'facts' key)

    private fun parseKGFact(response: String): KGFact? {
        return try {
            val json = parseJsonElement(response)
            val obj = if (json is JsonObject) json else json.jsonObject
            KGFact(
                id = obj["fact_id"]?.jsonPrimitive?.content
                    ?: obj["id"]?.jsonPrimitive?.content ?: return null,
                subject = obj["subject"]?.jsonPrimitive?.content ?: "",
                predicate = obj["predicate"]?.jsonPrimitive?.content ?: "",
                `object` = obj["object"]?.jsonPrimitive?.content ?: "",
                createdAt = obj["created_at"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: obj["createdAt"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: Clock.System.now().toEpochMilliseconds(),
                validFrom = obj["valid_from"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: obj["validFrom"]?.jsonPrimitive?.content?.toLongOrNull(),
                validTo = obj["valid_to"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: obj["validTo"]?.jsonPrimitive?.content?.toLongOrNull(),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseKGFactList(response: String): List<KGFact> {
        if (response.isBlank()) return emptyList()
        return try {
            val json = parseJsonElement(response)
            val arr = json.jsonObject["facts"]?.jsonArray
                ?: json.jsonObject["results"]?.jsonArray
            arr?.mapNotNull { parseKGFact(it.toString()) }
                ?: listOfNotNull(parseKGFact(response))
        } catch (_: Exception) {
            listOfNotNull(parseKGFact(response))
        }
    }

    private fun parseDiaryEntryList(response: String): List<DiaryEntry> {
        if (response.isBlank()) return emptyList()
        return try {
            val json = parseJsonElement(response)
            val arr = json.jsonObject["entries"]?.jsonArray
                ?: json.jsonObject["results"]?.jsonArray
                ?: json.jsonArray
            arr.mapNotNull { elem ->
                val obj = if (elem is JsonObject) elem else elem.jsonObject
                DiaryEntry(
                    id = obj["id"]?.jsonPrimitive?.content ?: "",
                    agentName = obj["agent"]?.jsonPrimitive?.content
                        ?: obj["agent_name"]?.jsonPrimitive?.content
                        ?: obj["agentName"]?.jsonPrimitive?.content ?: "",
                    topic = obj["topic"]?.jsonPrimitive?.content ?: "general",
                    content = obj["content"]?.jsonPrimitive?.content
                        ?: obj["entry"]?.jsonPrimitive?.content ?: "",
                    createdAt = obj["created_at"]?.jsonPrimitive?.content?.toLongOrNull()
                        ?: obj["createdAt"]?.jsonPrimitive?.content?.toLongOrNull()
                        ?: Clock.System.now().toEpochMilliseconds(),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun schemaResetMessage(): String? = null

    companion object {
        private val jsonElementParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private fun parseJsonElement(response: String): JsonElement = jsonElementParser.parseToJsonElement(response)
    }
}
