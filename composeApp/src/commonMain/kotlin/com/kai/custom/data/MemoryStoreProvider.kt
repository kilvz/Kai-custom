package com.kai.custom.data

import com.kai.custom.data.dimension.KGFact
import com.kai.custom.mcp.McpClient
import kotlin.concurrent.Volatile

class MemoryStoreProvider(private val sqliteStore: SqliteMemoryStore) : MemoryStore {

    @Volatile
    private var delegate: MemoryStore = sqliteStore
    private var appSettings: AppSettings? = null
    private var personaManager: PersonaManager? = null

    fun useAltMemory(client: McpClient, appSettings: AppSettings) {
        this.appSettings = appSettings
        this.personaManager = PersonaManager(appSettings)
        delegate = AltMemoryClient(client, appSettings)
    }

    fun useLocal() {
        delegate = sqliteStore
    }

    val isUsingAltMemory: Boolean get() = delegate !is SqliteMemoryStore

    private val isCharacterMode: Boolean
        get() = personaManager?.getActivePersona()?.renderMode == RenderMode.CHARACTER

    // ── Unprotected writes ──

    override suspend fun store(
        key: String,
        content: String,
        category: MemoryCategory,
        source: String?,
    ): MemoryEntry {
        if (isCharacterMode && isUsingAltMemory) {
            return delegate.store(key, content, category, source)
        }
        val entry = sqliteStore.store(key, content, category, source)
        if (isUsingAltMemory) {
            try {
                delegate.store(key, content, category, source)
            } catch (_: Exception) { }
        }
        return entry
    }

    override suspend fun updateContent(key: String, content: String): MemoryEntry? {
        if (isCharacterMode && isUsingAltMemory) {
            return delegate.updateContent(key, content)
        }
        val entry = sqliteStore.updateContent(key, content)
        if (isUsingAltMemory && entry != null) {
            try {
                delegate.updateContent(key, content)
            } catch (_: Exception) { }
        }
        return entry
    }

    override suspend fun reinforceMemory(key: String): MemoryEntry? {
        if (isCharacterMode && isUsingAltMemory) {
            return delegate.reinforceMemory(key)
        }
        val entry = sqliteStore.reinforceMemory(key)
        if (isUsingAltMemory && entry != null) {
            try {
                delegate.reinforceMemory(key)
            } catch (_: Exception) { }
        }
        return entry
    }

    override suspend fun forget(key: String): Boolean {
        if (isCharacterMode && isUsingAltMemory) {
            return delegate.forget(key)
        }
        val ok = sqliteStore.forget(key)
        if (isUsingAltMemory) {
            try {
                delegate.forget(key)
            } catch (_: Exception) { }
        }
        return ok
    }

    override suspend fun deleteAllMemories(force: Boolean) {
        if (isCharacterMode && isUsingAltMemory) {
            delegate.deleteAllMemories(force)
            return
        }
        sqliteStore.deleteAllMemories(force)
        if (isUsingAltMemory) {
            try {
                delegate.deleteAllMemories(force)
            } catch (_: Exception) { }
        }
    }

    // ── Protected writes ──

    override suspend fun storeProtected(
        key: String,
        content: String,
        category: MemoryCategory,
        source: String?,
    ): MemoryEntry {
        if (isCharacterMode && isUsingAltMemory) {
            return delegate.storeProtected(key, content, category, source)
        }
        if (isUsingAltMemory) {
            return delegate.storeProtected(key, content, category, source)
        }
        return sqliteStore.storeProtected(key, content, category, source)
    }

    // ── Unprotected reads ──

    override fun getUserMemories(max: Int): List<MemoryEntry> {
        if (isCharacterMode && isUsingAltMemory) {
            return try {
                delegate.getUserMemories(max)
            } catch (_: Exception) {
                emptyList()
            }
        }
        return sqliteStore.getUserMemories(max)
    }

    override fun searchMemories(query: String, limit: Int, mode: String): List<MemoryEntry> {
        if (isCharacterMode && isUsingAltMemory) {
            return try {
                delegate.searchMemories(query, limit, mode)
            } catch (_: Exception) {
                emptyList()
            }
        }
        return sqliteStore.searchMemories(query, limit, mode)
    }

    // ── Protected reads ──

    override fun getBehaviorMemories(): List<MemoryEntry> {
        if (isCharacterMode && isUsingAltMemory) {
            return try {
                val alt = delegate.getBehaviorMemories()
                if (alt.isNotEmpty()) alt else sqliteStore.getBehaviorMemories()
            } catch (_: Exception) {
                sqliteStore.getBehaviorMemories()
            }
        }
        if (!isUsingAltMemory) return sqliteStore.getBehaviorMemories()
        return try {
            val alt = delegate.getBehaviorMemories()
            if (alt.isNotEmpty()) alt else sqliteStore.getBehaviorMemories()
        } catch (_: Exception) {
            sqliteStore.getBehaviorMemories()
        }
    }

    override fun getPromotionCandidates(minHits: Int, max: Int): List<MemoryEntry> {
        if (isCharacterMode && isUsingAltMemory) {
            return try {
                val alt = delegate.getPromotionCandidates(minHits, max)
                if (alt.isNotEmpty()) alt else sqliteStore.getPromotionCandidates(minHits, max)
            } catch (_: Exception) {
                sqliteStore.getPromotionCandidates(minHits, max)
            }
        }
        if (!isUsingAltMemory) return sqliteStore.getPromotionCandidates(minHits, max)
        return try {
            val alt = delegate.getPromotionCandidates(minHits, max)
            if (alt.isNotEmpty()) alt else sqliteStore.getPromotionCandidates(minHits, max)
        } catch (_: Exception) {
            sqliteStore.getPromotionCandidates(minHits, max)
        }
    }

    // ── Combined reads ──

    override fun getAllMemories(max: Int): List<MemoryEntry> {
        if (isCharacterMode && isUsingAltMemory) {
            return try {
                val alt = delegate.getAllMemories(max)
                val altIds = alt.map { it.key }.toSet()
                val sql = sqliteStore.getAllMemories(max)
                (alt + sql.filter { it.key !in altIds }).take(max)
            } catch (_: Exception) {
                emptyList()
            }
        }
        if (!isUsingAltMemory) return sqliteStore.getAllMemories(max)
        val sql = sqliteStore.getAllMemories(max)
        return try {
            val alt = delegate.getAllMemories(max)
            val altIds = alt.map { it.key }.toSet()
            (alt + sql.filter { it.key !in altIds }).take(max)
        } catch (_: Exception) {
            sql
        }
    }

    // ── Passthrough ──

    override suspend fun setPersona(personaId: String) {
        try {
            delegate.setPersona(personaId)
        } catch (_: Exception) { }
    }

    override suspend fun fetchRemotePersonas(): List<PersonaConfig> = try {
        delegate.fetchRemotePersonas()
    } catch (_: Exception) {
        emptyList()
    }

    override suspend fun syncPersonaToRemote(config: PersonaConfig) {
        try {
            delegate.syncPersonaToRemote(config)
        } catch (_: Exception) { }
    }

    override suspend fun deleteRemotePersona(id: String) {
        try {
            delegate.deleteRemotePersona(id)
        } catch (_: Exception) { }
    }

    override fun schemaResetMessage(): String? = try {
        delegate.schemaResetMessage()
    } catch (_: Exception) {
        sqliteStore.schemaResetMessage()
    }

    override fun exportDimension(): ByteArray = try {
        delegate.exportDimension()
    } catch (_: Exception) {
        sqliteStore.exportDimension()
    }

    override fun importDimension(data: ByteArray) {
        try {
            delegate.importDimension(data)
        } catch (_: Exception) { }
        sqliteStore.importDimension(data)
    }

    // ── Knowledge graph ──

    override suspend fun addFact(subject: String, predicate: String, `object`: String): KGFact {
        if (isCharacterMode && isUsingAltMemory) {
            return delegate.addFact(subject, predicate, `object`)
        }
        val fact = sqliteStore.addFact(subject, predicate, `object`)
        if (isUsingAltMemory) {
            try {
                delegate.addFact(subject, predicate, `object`)
            } catch (_: Exception) { }
        }
        return fact
    }

    override fun queryFacts(entity: String?, relation: String?, limit: Int): List<KGFact> {
        if (isCharacterMode && isUsingAltMemory) {
            return try {
                delegate.queryFacts(entity, relation, limit)
            } catch (_: Exception) {
                emptyList()
            }
        }
        if (!isUsingAltMemory) return sqliteStore.queryFacts(entity, relation, limit)
        return try {
            val alt = delegate.queryFacts(entity, relation, limit)
            if (alt.isNotEmpty()) alt else sqliteStore.queryFacts(entity, relation, limit)
        } catch (_: Exception) {
            sqliteStore.queryFacts(entity, relation, limit)
        }
    }

    override suspend fun invalidateFact(subject: String, predicate: String, `object`: String) {
        if (isCharacterMode && isUsingAltMemory) {
            delegate.invalidateFact(subject, predicate, `object`)
            return
        }
        sqliteStore.invalidateFact(subject, predicate, `object`)
        if (isUsingAltMemory) {
            try {
                delegate.invalidateFact(subject, predicate, `object`)
            } catch (_: Exception) { }
        }
    }

    // ── Diary ──

    override suspend fun diaryWrite(agentName: String, content: String, topic: String) {
        if (isCharacterMode && isUsingAltMemory) {
            delegate.diaryWrite(agentName, content, topic)
            return
        }
        sqliteStore.diaryWrite(agentName, content, topic)
        if (isUsingAltMemory) {
            try {
                delegate.diaryWrite(agentName, content, topic)
            } catch (_: Exception) { }
        }
    }

    override suspend fun diaryDelete(id: String): Boolean = sqliteStore.diaryDelete(id)

    override fun diaryRead(agentName: String, lastN: Int): List<DiaryEntry> {
        if (isCharacterMode && isUsingAltMemory) {
            return try {
                delegate.diaryRead(agentName, lastN)
            } catch (_: Exception) {
                emptyList()
            }
        }
        if (!isUsingAltMemory) return sqliteStore.diaryRead(agentName, lastN)
        return try {
            val alt = delegate.diaryRead(agentName, lastN)
            if (alt.isNotEmpty()) alt else sqliteStore.diaryRead(agentName, lastN)
        } catch (_: Exception) {
            sqliteStore.diaryRead(agentName, lastN)
        }
    }
}
