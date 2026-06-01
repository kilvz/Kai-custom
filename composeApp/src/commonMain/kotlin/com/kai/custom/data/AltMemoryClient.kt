package com.kai.custom.data

import com.kai.custom.mcp.McpClient
import com.kai.custom.runBlockingCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

import com.kai.custom.data.dimension.KGFact

class AltMemoryClient(
    private val client: McpClient,
    private val appSettings: AppSettings,
) : MemoryStore {

    override suspend fun store(
        key: String,
        content: String,
        category: MemoryCategory,
        source: String?,
    ): MemoryEntry {
        val now = Clock.System.now().toEpochMilliseconds()
        val args = buildJsonObject {
            put("key", JsonPrimitive(key))
            put("content", JsonPrimitive(content))
            put("category", JsonPrimitive(category.name))
            if (source != null) {
                put("source", JsonPrimitive(source))
            }
        }
        client.callTool("memory_store", args)
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
            "memory_store",
            buildJsonObject {
                put("key", JsonPrimitive(key))
                put("content", JsonPrimitive(content))
                put("category", JsonPrimitive(existing.category.name))
            },
        )
        return existing.copy(content = content, updatedAt = now)
    }

    override suspend fun reinforceMemory(key: String): MemoryEntry? {
        val existing = findEntryByKey(key) ?: return null
        val now = Clock.System.now().toEpochMilliseconds()
        client.callTool(
            "memory_store",
            buildJsonObject {
                put("key", JsonPrimitive(key))
                put("content", JsonPrimitive(existing.content))
                put("category", JsonPrimitive(existing.category.name))
                put("hit_count", JsonPrimitive(existing.hitCount + 1))
            },
        )
        return existing.copy(hitCount = existing.hitCount + 1, updatedAt = now)
    }

    override suspend fun storeProtected(
        key: String,
        content: String,
        category: MemoryCategory,
        source: String?,
    ): MemoryEntry {
        val now = Clock.System.now().toEpochMilliseconds()
        val args = buildJsonObject {
            put("key", JsonPrimitive(key))
            put("content", JsonPrimitive(content))
            put("category", JsonPrimitive(category.name))
            put("protected", JsonPrimitive("true"))
            if (source != null) put("source", JsonPrimitive(source))
        }
        client.callTool("memory_store", args)
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
            client.callTool("memory_forget", buildJsonObject {
                put("key", JsonPrimitive(key))
            })
            return true
        } catch (_: Exception) {
            return false
        }
    }

    override fun getUserMemories(max: Int): List<MemoryEntry> {
        return getAllMemories(max).filter { !it.protected }
    }

    override fun getBehaviorMemories(): List<MemoryEntry> {
        return getAllMemories().filter { it.protected }
    }

    override fun getAllMemories(max: Int): List<MemoryEntry> {
        return searchMemories("", max)
    }

    override fun searchMemories(query: String, limit: Int): List<MemoryEntry> = try {
        val response = runBlockingCompat {
            client.callTool(
                "memory_search",
                buildJsonObject {
                    put("query", JsonPrimitive(query))
                    put("n_results", JsonPrimitive(limit))
                },
            )
        }
        parseMemorySearchResponse(response)
    } catch (_: Exception) {
        emptyList()
    }

    override fun getPromotionCandidates(minHits: Int, max: Int): List<MemoryEntry> {
        val all = getAllMemories(max)
        return all.filter { it.hitCount >= minHits }
    }

    override fun exportDimension(): ByteArray = try {
        val json = runBlockingCompat {
            client.callTool("dimension_export", buildJsonObject { })
        }
        json.encodeToByteArray()
    } catch (_: Exception) {
        ByteArray(0)
    }

    override fun importDimension(data: ByteArray) {
        try {
            runBlockingCompat {
                client.callTool(
                    "dimension_import",
                    buildJsonObject {
                        put("data", JsonPrimitive(data.decodeToString()))
                    },
                )
            }
        } catch (_: Exception) {
        }
    }

    // Knowledge graph

    override suspend fun addFact(subject: String, predicate: String, `object`: String): KGFact {
        val now = Clock.System.now().toEpochMilliseconds()
        val response = client.callTool("dimension_kg_add", buildJsonObject {
            put("subject", JsonPrimitive(subject))
            put("predicate", JsonPrimitive(predicate))
            put("object", JsonPrimitive(`object`))
        })
        return parseKGFact(response) ?: KGFact(
            id = "kg_${subject.hashCode().toUInt().toString(16)}_${predicate.hashCode().toUInt().toString(16)}_$now",
            subject = subject,
            predicate = predicate,
            `object` = `object`,
            createdAt = now,
        )
    }

    override fun queryFacts(entity: String?, relation: String?, limit: Int): List<KGFact> {
        return try {
            val response = runBlocking {
                client.callTool("dimension_kg_query", buildJsonObject {
                    entity?.let { put("entity", JsonPrimitive(it)) }
                    relation?.let { put("relation", JsonPrimitive(it)) }
                    put("limit", JsonPrimitive(limit.toString()))
                })
            }
            parseKGFactList(response)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun invalidateFact(subject: String, predicate: String, `object`: String) {
        try {
            client.callTool("dimension_kg_invalidate", buildJsonObject {
                put("subject", JsonPrimitive(subject))
                put("predicate", JsonPrimitive(predicate))
                put("object", JsonPrimitive(`object`))
            })
        } catch (_: Exception) {
        }
    }

    // Diary

    override suspend fun diaryWrite(agentName: String, content: String, topic: String) {
        client.callTool("dimension_diary_write", buildJsonObject {
            put("agent_name", JsonPrimitive(agentName))
            put("content", JsonPrimitive(content))
            put("topic", JsonPrimitive(topic))
        })
    }

    override fun diaryRead(agentName: String, lastN: Int): List<DiaryEntry> {
        return try {
            val response = runBlocking {
                client.callTool("dimension_diary_read", buildJsonObject {
                    put("agent_name", JsonPrimitive(agentName))
                    put("last_n", JsonPrimitive(lastN.toString()))
                })
            }
            parseDiaryEntryList(response)
        } catch (_: Exception) {
            emptyList()
        }
    }

    // KG parsing helpers

    private fun parseKGFact(response: String): KGFact? {
        return try {
            val json = parseJsonElement(response)
            val obj = if (json is kotlinx.serialization.json.JsonObject) json else json.jsonObject
            KGFact(
                id = obj["id"]?.jsonPrimitive?.content ?: return null,
                subject = obj["subject"]?.jsonPrimitive?.content ?: "",
                predicate = obj["predicate"]?.jsonPrimitive?.content ?: "",
                `object` = obj["object"]?.jsonPrimitive?.content ?: "",
                createdAt = obj["created_at"]?.jsonPrimitive?.content?.toLongOrNull() ?: obj["createdAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: Clock.System.now().toEpochMilliseconds(),
                validFrom = obj["valid_from"]?.jsonPrimitive?.content?.toLongOrNull() ?: obj["validFrom"]?.jsonPrimitive?.content?.toLongOrNull(),
                validTo = obj["valid_to"]?.jsonPrimitive?.content?.toLongOrNull() ?: obj["validTo"]?.jsonPrimitive?.content?.toLongOrNull(),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseKGFactList(response: String): List<KGFact> {
        if (response.isBlank()) return emptyList()
        return try {
            val json = parseJsonElement(response)
            val arr = json.jsonObject["facts"]?.jsonArray ?: json.jsonObject["results"]?.jsonArray
            arr?.mapNotNull { parseKGFact(it.toString()) } ?: listOfNotNull(parseKGFact(response))
        } catch (_: Exception) {
            listOfNotNull(parseKGFact(response))
        }
    }

    private fun parseDiaryEntryList(response: String): List<DiaryEntry> {
        if (response.isBlank()) return emptyList()
        return try {
            val json = parseJsonElement(response)
            val arr = json.jsonObject["entries"]?.jsonArray ?: json.jsonObject["results"]?.jsonArray ?: json.jsonArray
            arr.mapNotNull { elem ->
                val obj = if (elem is kotlinx.serialization.json.JsonObject) elem else elem.jsonObject
                DiaryEntry(
                    id = obj["id"]?.jsonPrimitive?.content ?: "",
                    agentName = obj["agent_name"]?.jsonPrimitive?.content ?: obj["agentName"]?.jsonPrimitive?.content ?: "",
                    topic = obj["topic"]?.jsonPrimitive?.content ?: "general",
                    content = obj["content"]?.jsonPrimitive?.content ?: "",
                    createdAt = obj["created_at"]?.jsonPrimitive?.content?.toLongOrNull() ?: obj["createdAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: Clock.System.now().toEpochMilliseconds(),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun findEntryByKey(key: String): MemoryEntry? {
        return try {
            val response = runBlockingCompat {
                client.callTool(
                    "memory_retrieve",
                    buildJsonObject {
                        put("key", JsonPrimitive(key))
                    },
                )
            }
            if (response.isBlank()) return null
            parseMemoryEntry(response)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMemorySearchResponse(response: String): List<MemoryEntry> {
        if (response.isBlank()) return emptyList()
        return try {
            val json = parseJsonElement(response)
            val results = json.jsonObject["results"]?.jsonArray ?: json.jsonObject["memories"]?.jsonArray
            if (results == null) {
                listOfNotNull(parseMemoryEntry(response))
            } else {
                results.mapNotNull { parseMemoryEntry(it.toString()) }
            }
        } catch (_: Exception) {
            listOfNotNull(parseMemoryEntry(response))
        }
    }

    private fun parseMemoryEntry(response: String): MemoryEntry? {
        return try {
            val json = parseJsonElement(response)
            val obj = if (json is kotlinx.serialization.json.JsonObject) {
                json
            } else {
                json.jsonObject
            }
            val key = obj["key"]?.jsonPrimitive?.content ?: return null
            val content = obj["content"]?.jsonPrimitive?.content ?: ""
            val cat = try {
                val catStr = obj["category"]?.jsonPrimitive?.content ?: "GENERAL"
                MemoryCategory.valueOf(catStr)
            } catch (_: Exception) {
                MemoryCategory.GENERAL
            }
            val hitCount = obj["hit_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
            val createdAt = obj["created_at"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: obj["createdAt"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: Clock.System.now().toEpochMilliseconds()
            val updatedAt = obj["updated_at"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: obj["updatedAt"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: createdAt
            val source = obj["source"]?.jsonPrimitive?.contentOrNull
            MemoryEntry(
                key = key,
                content = content,
                createdAt = createdAt,
                updatedAt = updatedAt,
                category = cat,
                hitCount = hitCount,
                source = source,
            )
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val jsonElementParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private fun parseJsonElement(response: String): kotlinx.serialization.json.JsonElement = jsonElementParser.parseToJsonElement(response)
    }
}
