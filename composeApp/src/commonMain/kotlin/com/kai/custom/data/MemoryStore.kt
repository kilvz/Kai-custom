package com.kai.custom.data

import androidx.compose.runtime.Immutable
import com.kai.custom.data.dimension.KGFact
import kotlinx.serialization.Serializable

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
    val protected: Boolean = false,
)

@Serializable
data class DiaryEntry(
    val id: String,
    val agentName: String,
    val topic: String = "general",
    val content: String,
    val createdAt: Long,
)

interface MemoryStore {
    suspend fun store(
        key: String,
        content: String,
        category: MemoryCategory = MemoryCategory.GENERAL,
        source: String? = null,
    ): MemoryEntry

    suspend fun updateContent(key: String, content: String): MemoryEntry?

    suspend fun reinforceMemory(key: String): MemoryEntry?

    suspend fun forget(key: String): Boolean

    suspend fun storeProtected(
        key: String,
        content: String,
        category: MemoryCategory = MemoryCategory.LEARNING,
        source: String? = null,
    ): MemoryEntry

    fun getUserMemories(max: Int = 1000): List<MemoryEntry>

    fun getBehaviorMemories(): List<MemoryEntry>

    fun getAllMemories(max: Int = 1000): List<MemoryEntry>

    fun searchMemories(query: String, limit: Int = 10): List<MemoryEntry>

    fun getPromotionCandidates(minHits: Int = 5, max: Int = 500): List<MemoryEntry>

    fun exportDimension(): ByteArray

    fun importDimension(data: ByteArray)

    // Knowledge graph
    suspend fun addFact(subject: String, predicate: String, `object`: String): KGFact
    fun queryFacts(entity: String? = null, relation: String? = null, limit: Int = 20): List<KGFact>
    suspend fun invalidateFact(subject: String, predicate: String, `object`: String)

    // Diary
    suspend fun diaryWrite(agentName: String, content: String, topic: String = "general")
    fun diaryRead(agentName: String, lastN: Int = 10): List<DiaryEntry>
}
