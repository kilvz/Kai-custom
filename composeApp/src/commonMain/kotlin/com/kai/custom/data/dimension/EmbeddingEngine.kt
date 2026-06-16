package com.kai.custom.data.dimension

import kotlin.math.min
import kotlin.math.sqrt

class EmbeddingEngine {

    private var ready = false
    private var modelPath: String? = null

    suspend fun load(context: Any, modelAssetPath: String = "use_lite_model.tflite") {
        modelPath = modelAssetPath
        ready = true
    }

    fun isAvailable(): Boolean = ready

    suspend fun embed(text: String): List<Float> = computeFallbackEmbedding(text)

    suspend fun embedBatch(texts: List<String>): List<List<Float>> = texts.map { computeFallbackEmbedding(it) }

    private fun computeFallbackEmbedding(text: String): List<Float> {
        if (text.isBlank()) return List(128) { 0f }
        val chars = text.lowercase().toCharArray()
        val vec = FloatArray(128)
        for (i in chars.indices) {
            val idx = i % 128
            vec[idx] += chars[i].code.toFloat() / 256f
        }
        val norm = sqrt(vec.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) {
            for (i in vec.indices) vec[i] /= norm
        }
        return vec.toList()
    }

    companion object {
        fun cosineSimilarity(a: List<Float>, b: List<Float>): Double {
            if (a.size != b.size || a.isEmpty()) return 0.0
            var dot = 0.0
            var normA = 0.0
            var normB = 0.0
            for (i in a.indices) {
                dot += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            val denom = sqrt(normA) * sqrt(normB)
            return if (denom == 0.0) 0.0 else dot / denom
        }
    }
}
