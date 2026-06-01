package com.kai.custom.data

import com.kai.custom.data.dimension.KGFact
import com.kai.custom.mcp.McpClient
import kotlin.concurrent.Volatile

class MemoryStoreProvider(private val sqliteStore: SqliteMemoryStore) : MemoryStore {

    @Volatile
    private var delegate: MemoryStore = sqliteStore

    fun useAltMemory(client: McpClient, appSettings: AppSettings) {
        delegate = AltMemoryClient(client, appSettings)
    }

    fun useLocal() {
        delegate = sqliteStore
    }

    val isUsingAltMemory: Boolean get() = delegate !is SqliteMemoryStore

    override suspend fun setPersona(personaId: String) {
        delegate.setPersona(personaId)
    }

    override suspend fun store(
        key: String,
        content: String,
        category: MemoryCategory,
        source: String?,
    ): MemoryEntry = delegate.store(key, content, category, source)

    override suspend fun updateContent(key: String, content: String): MemoryEntry? = delegate.updateContent(key, content)

    override suspend fun reinforceMemory(key: String): MemoryEntry? = delegate.reinforceMemory(key)

    override suspend fun forget(key: String): Boolean = delegate.forget(key)

    override suspend fun storeProtected(
        key: String,
        content: String,
        category: MemoryCategory,
        source: String?,
    ): MemoryEntry = delegate.storeProtected(key, content, category, source)

    override fun getUserMemories(max: Int): List<MemoryEntry> = delegate.getUserMemories(max)

    override fun getBehaviorMemories(): List<MemoryEntry> = delegate.getBehaviorMemories()

    override fun getAllMemories(max: Int): List<MemoryEntry> = delegate.getAllMemories(max)

    override fun searchMemories(query: String, limit: Int): List<MemoryEntry> = delegate.searchMemories(query, limit)

    override fun schemaResetMessage(): String? = delegate.schemaResetMessage()

    override fun getPromotionCandidates(minHits: Int, max: Int): List<MemoryEntry> = delegate.getPromotionCandidates(minHits, max)

    override fun exportDimension(): ByteArray = delegate.exportDimension()

    override fun importDimension(data: ByteArray) = delegate.importDimension(data)

    override suspend fun addFact(subject: String, predicate: String, `object`: String): KGFact = delegate.addFact(subject, predicate, `object`)

    override fun queryFacts(entity: String?, relation: String?, limit: Int): List<KGFact> = delegate.queryFacts(entity, relation, limit)

    override suspend fun invalidateFact(subject: String, predicate: String, `object`: String) = delegate.invalidateFact(subject, predicate, `object`)

    override suspend fun diaryWrite(agentName: String, content: String, topic: String) = delegate.diaryWrite(agentName, content, topic)

    override fun diaryRead(agentName: String, lastN: Int): List<DiaryEntry> = delegate.diaryRead(agentName, lastN)
}
