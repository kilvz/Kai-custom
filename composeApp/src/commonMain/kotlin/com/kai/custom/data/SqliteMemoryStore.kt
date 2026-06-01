package com.kai.custom.data

import com.kai.custom.data.dimension.DimensionConfig
import com.kai.custom.data.dimension.DimensionStore
import com.kai.custom.data.dimension.EntityData
import com.kai.custom.data.dimension.KGFact
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)
class SqliteMemoryStore(private val dimension: DimensionStore) : MemoryStore {

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
            protected = e.protected,
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

    private fun allEntities(max: Int = Int.MAX_VALUE): List<EntityData> = dimension.getAllEntities().let { if (it.size <= max) it else it.take(max) }

    override suspend fun store(
        key: String,
        content: String,
        category: MemoryCategory,
        source: String?,
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

    override suspend fun updateContent(key: String, content: String): MemoryEntry? = mutex.withLock {
        val existing = findEntryByKey(key) ?: return@withLock null
        val updated = existing.copy(content = content, updatedAt = Clock.System.now().toEpochMilliseconds())
        dimension.putEntity(entryToEntity(updated))
        updated
    }

    override suspend fun reinforceMemory(key: String): MemoryEntry? = mutex.withLock {
        val existing = findEntryByKey(key) ?: return@withLock null
        val updated = existing.copy(hitCount = existing.hitCount + 1, updatedAt = Clock.System.now().toEpochMilliseconds())
        dimension.putEntity(entryToEntity(updated))
        updated
    }

    override fun schemaResetMessage(): String? = dimension.schemaResetMessage()

    override fun getPromotionCandidates(minHits: Int, max: Int): List<MemoryEntry> = allEntities(max).mapNotNull { entityToEntry(it) }.filter { it.hitCount >= minHits }

    override suspend fun storeProtected(
        key: String,
        content: String,
        category: MemoryCategory,
        source: String?,
    ): MemoryEntry = mutex.withLock {
        val now = Clock.System.now().toEpochMilliseconds()
        val existing = findEntryByKey(key)
        val entry = existing?.copy(
            content = content,
            updatedAt = now,
            category = category,
            source = source ?: existing.source,
            protected = true,
        ) ?: MemoryEntry(
            key = key,
            content = content,
            createdAt = now,
            updatedAt = now,
            category = category,
            source = source,
            protected = true,
        )
        dimension.putEntity(entryToEntity(entry))
        entry
    }

    override suspend fun forget(key: String): Boolean = mutex.withLock {
        val entity = dimension.getEntityByMetadataKey("memory_key", key) ?: return@withLock false
        val entry = entityToEntry(entity)
        if (entry?.protected == true) return@withLock false
        dimension.deleteEntity(entity.id)
        true
    }

    override fun getUserMemories(max: Int): List<MemoryEntry> = allEntities(Int.MAX_VALUE).mapNotNull { entityToEntry(it) }
        .filter { !it.protected }
        .take(max)

    override fun getBehaviorMemories(): List<MemoryEntry> = allEntities(Int.MAX_VALUE).mapNotNull { entityToEntry(it) }
        .filter { it.protected }

    override fun getAllMemories(max: Int): List<MemoryEntry> = allEntities(max).mapNotNull { entityToEntry(it) }

    override fun searchMemories(query: String, limit: Int): List<MemoryEntry> {
        if (query.isBlank()) return emptyList()
        return dimension.searchEntities(query, limit).mapNotNull { entityToEntry(it.entity) }
    }

    private fun findEntryByKey(key: String): MemoryEntry? {
        val entity = dimension.getEntityByMetadataKey("memory_key", key)
            ?: return null
        return entityToEntry(entity)
    }

    override fun exportDimension(): ByteArray = dimension.getExportData()

    override fun importDimension(data: ByteArray) = dimension.importFromData(data)

    // Knowledge graph

    override suspend fun addFact(subject: String, predicate: String, `object`: String): KGFact {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = "kg_${subject.hashCode().toUInt().toString(16)}_${predicate.hashCode().toUInt().toString(16)}_$now"
        val fact = KGFact(id = id, subject = subject, predicate = predicate, `object` = `object`, createdAt = now)
        return dimension.putFact(fact)
    }

    override fun queryFacts(entity: String?, relation: String?, limit: Int): List<KGFact> {
        if (entity != null) {
            val bySubject = dimension.getFactsBySubject(entity)
            val byObject = dimension.getFactsByObject(entity)
            val all = (bySubject + byObject).distinct().sortedByDescending { it.createdAt }
            return if (relation != null) all.filter { it.predicate == relation }.take(limit) else all.take(limit)
        }
        return dimension.queryKGE(relation, limit)
    }

    override suspend fun invalidateFact(subject: String, predicate: String, `object`: String) {
        val facts = dimension.getFactsBySubject(subject).filter { it.predicate == predicate && it.`object` == `object` }
        for (fact in facts) {
            dimension.putFact(fact.copy(validTo = Clock.System.now().toEpochMilliseconds()))
        }
    }

    // Diary

    override suspend fun diaryWrite(agentName: String, content: String, topic: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = "diary_${agentName}_$now"
        val entity = EntityData(
            id = id,
            realm = DimensionConfig.REALM_AGENT,
            domain = DimensionConfig.DOMAIN_DIARY,
            content = content,
            metadata = mapOf("agent_name" to agentName, "topic" to topic, "type" to "diary_entry"),
            createdAt = now,
            updatedAt = now,
        )
        dimension.putEntity(entity)
    }

    override fun diaryRead(agentName: String, lastN: Int): List<DiaryEntry> {
        val all = dimension.getEntitiesByDomain(DimensionConfig.REALM_AGENT, DimensionConfig.DOMAIN_DIARY)
            .filter { it.metadata["agent_name"] == agentName }
            .sortedByDescending { it.createdAt }
            .take(lastN)
        return all.map { entity ->
            DiaryEntry(
                id = entity.id,
                agentName = agentName,
                topic = entity.metadata["topic"] ?: "general",
                content = entity.content,
                createdAt = entity.createdAt,
            )
        }
    }
}
