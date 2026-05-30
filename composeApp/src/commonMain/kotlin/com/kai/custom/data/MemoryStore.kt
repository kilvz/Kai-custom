package com.kai.custom.data

import androidx.compose.runtime.Immutable
import com.kai.custom.data.dimension.DimensionConfig
import com.kai.custom.data.dimension.DimensionStore
import com.kai.custom.data.dimension.EntityData
import com.kai.custom.data.dimension.KGFact
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
enum class MemoryCategory {
    GENERAL,
    LEARNING,
    ERROR,
    PREFERENCE,
}

@Immutable
@Serializable
data class MemoryEntry(
    val key: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val category: MemoryCategory = MemoryCategory.GENERAL,
    val hitCount: Int = 1,
    val source: String? = null,
)

@OptIn(ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)
class MemoryStore(private val dimension: DimensionStore) {

    private val mutex = Mutex()

    private fun domainForCategory(category: MemoryCategory): String = when (category) {
        MemoryCategory.GENERAL -> DimensionConfig.DOMAIN_MEMORIES
        MemoryCategory.PREFERENCE -> DimensionConfig.DOMAIN_PREFERENCES
        MemoryCategory.LEARNING -> DimensionConfig.DOMAIN_LEARNINGS
        MemoryCategory.ERROR -> DimensionConfig.DOMAIN_ERRORS
    }

    private fun realmForCategory(category: MemoryCategory): String = when (category) {
        MemoryCategory.GENERAL,
        MemoryCategory.LEARNING,
        MemoryCategory.ERROR,
            -> DimensionConfig.REALM_AGENT
        MemoryCategory.PREFERENCE -> DimensionConfig.REALM_USER
    }

    private fun entityToEntry(e: EntityData): MemoryEntry? {
        val key = e.metadata["memory_key"] ?: e.id.takeLast(36)
        val category = try {
            val cat = e.metadata["category"] ?: "GENERAL"
            MemoryCategory.valueOf(cat)
        } catch (_: Exception) {
            MemoryCategory.GENERAL
        }
        val hitCount = e.metadata["hit_count"]?.toIntOrNull() ?: 1
        val source = e.metadata["source"]
        return MemoryEntry(
            key = key,
            content = e.content,
            createdAt = e.createdAt,
            updatedAt = e.updatedAt,
            category = category,
            hitCount = hitCount,
            source = source,
        )
    }

    private fun entryToEntity(entry: MemoryEntry): EntityData {
        val id = "mem_${entry.key.hashCode().toUInt().toString(16)}_${entry.createdAt}"
        return EntityData(
            id = id,
            realm = realmForCategory(entry.category),
            domain = domainForCategory(entry.category),
            content = entry.content,
            metadata = buildMap {
                put("memory_key", entry.key)
                put("category", entry.category.name)
                put("hit_count", entry.hitCount.toString())
                put("type", "memory_entry")
                entry.source?.let { put("source", it) }
            },
            createdAt = entry.createdAt,
            updatedAt = entry.updatedAt,
        )
    }

    private fun allEntities(max: Int = Int.MAX_VALUE): List<EntityData> =
        dimension.getAllEntities().let { if (it.size <= max) it else it.take(max) }

    suspend fun store(
        key: String,
        content: String,
        category: MemoryCategory = MemoryCategory.GENERAL,
        source: String? = null,
    ): MemoryEntry = mutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            val existing = findEntryByKey(key)
            val entry = existing?.copy(
                content = content,
                updatedAt = now,
                category = category,
                source = source ?: existing.source,
            ) ?: MemoryEntry(
                key = key,
                content = content,
                createdAt = now,
                updatedAt = now,
                category = category,
                source = source,
            )
        dimension.putEntity(entryToEntity(entry))
        entry
    }

    suspend fun updateContent(key: String, content: String): MemoryEntry? = mutex.withLock {
        val existing = findEntryByKey(key) ?: return@withLock null
        val updated = existing.copy(content = content, updatedAt = Clock.System.now().toEpochMilliseconds())
        dimension.putEntity(entryToEntity(updated))
        updated
    }

    suspend fun reinforceMemory(key: String): MemoryEntry? = mutex.withLock {
        val existing = findEntryByKey(key) ?: return@withLock null
        val updated = existing.copy(hitCount = existing.hitCount + 1, updatedAt = Clock.System.now().toEpochMilliseconds())
        dimension.putEntity(entryToEntity(updated))
        updated
    }

    fun getPromotionCandidates(minHits: Int = 5, max: Int = 500): List<MemoryEntry> =
        allEntities(max).mapNotNull { entityToEntry(it) }.filter { it.hitCount >= minHits }

    suspend fun forget(key: String): Boolean = mutex.withLock {
        val entity = dimension.getEntityByMetadataKey("memory_key", key) ?: return@withLock false
        dimension.deleteEntity(entity.id)
        true
    }

    fun getAllMemories(max: Int = 1000): List<MemoryEntry> =
        allEntities(max).mapNotNull { entityToEntry(it) }

    fun searchMemories(query: String, limit: Int = 10): List<MemoryEntry> {
        if (query.isBlank()) return emptyList()
        return dimension.searchEntities(query, limit).mapNotNull { entityToEntry(it.entity) }
    }

    private fun findEntryByKey(key: String): MemoryEntry? {
        val entity = dimension.getEntityByMetadataKey("memory_key", key)
            ?: return null
        return entityToEntry(entity)
    }

    fun exportDimension(): ByteArray = dimension.getExportData()

    fun importDimension(data: ByteArray) = dimension.importFromData(data)
}
