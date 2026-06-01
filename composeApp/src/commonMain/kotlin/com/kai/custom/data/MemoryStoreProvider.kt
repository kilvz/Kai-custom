package com.kai.custom.data

import com.kai.custom.mcp.McpClient

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

    override suspend fun store(
        key: String,
        content: String,
        category: MemoryCategory,
        source: String?,
    ): MemoryEntry = delegate.store(key, content, category, source)

    override suspend fun updateContent(key: String, content: String): MemoryEntry? =
        delegate.updateContent(key, content)

    override suspend fun reinforceMemory(key: String): MemoryEntry? =
        delegate.reinforceMemory(key)

    override suspend fun forget(key: String): Boolean = delegate.forget(key)

    override fun getAllMemories(max: Int): List<MemoryEntry> = delegate.getAllMemories(max)

    override fun searchMemories(query: String, limit: Int): List<MemoryEntry> =
        delegate.searchMemories(query, limit)

    override fun schemaResetMessage(): String? = delegate.schemaResetMessage()

    override fun getPromotionCandidates(minHits: Int, max: Int): List<MemoryEntry> =
        delegate.getPromotionCandidates(minHits, max)

    override fun exportDimension(): ByteArray = delegate.exportDimension()

    override fun importDimension(data: ByteArray) = delegate.importDimension(data)
}
