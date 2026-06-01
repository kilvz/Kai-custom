package com.inspiredandroid.kai.data

import androidx.compose.runtime.Immutable
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

    fun getAllMemories(max: Int = 1000): List<MemoryEntry>

    fun searchMemories(query: String, limit: Int = 10): List<MemoryEntry>

    fun getPromotionCandidates(minHits: Int = 5, max: Int = 500): List<MemoryEntry>

    fun schemaResetMessage(): String? = null

    fun exportDimension(): ByteArray

    fun importDimension(data: ByteArray)
}
