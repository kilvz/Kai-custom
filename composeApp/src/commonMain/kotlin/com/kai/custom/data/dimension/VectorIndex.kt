package com.kai.custom.data.dimension

import kotlin.math.sqrt

class VectorIndex {

    data class IndexEntry(
        val entityId: String,
        val embedding: List<Float>,
    )

    @Volatile
    private var entries: List<IndexEntry> = emptyList()
    private val lock = Any()

    fun size(): Int = entries.size

    fun upsert(entityId: String, embedding: List<Float>) {
        synchronized(lock) {
            val existing = entries.toMutableList()
            existing.removeAll { it.entityId == entityId }
            existing.add(IndexEntry(entityId, embedding))
            entries = existing
        }
    }

    fun remove(entityId: String) {
        synchronized(lock) {
            entries = entries.filter { it.entityId != entityId }
        }
    }

    fun rebuild(all: List<IndexEntry>) {
        synchronized(lock) {
            entries = all.toList()
        }
    }

    fun search(queryEmbedding: List<Float>, limit: Int = 10, minScore: Double = 0.0): List<Pair<String, Double>> {
        if (queryEmbedding.isEmpty()) return emptyList()
        val current = entries
        if (current.isEmpty()) return emptyList()

        return current.map { entry ->
            entry.entityId to EmbeddingEngine.cosineSimilarity(queryEmbedding, entry.embedding)
        }
            .filter { it.second >= minScore }
            .sortedByDescending { it.second }
            .take(limit)
    }
}
