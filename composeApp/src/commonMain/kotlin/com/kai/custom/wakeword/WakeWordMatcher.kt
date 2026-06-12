package com.kai.custom.wakeword

import kotlin.math.sqrt

enum class WakeWordMode { GENERAL, PERSONAL }

object WakeWordMatcher {
    private const val TEMPLATE_SIZE = 49 * 13 // NUM_FRAMES * NUM_MFCC

    fun cosineSimilarity(features: Array<FloatArray>, template: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        var tIdx = 0
        for (f in features) {
            // Mean-center live frame to remove background/ambient
            val liveMean = f.sum() / f.size
            // Compute mean of corresponding template frame
            var tSum = 0f
            for (j in f.indices) {
                if (tIdx + j < template.size) tSum += template[tIdx + j]
            }
            val tMean = tSum / f.size
            for (v in f) {
                val centered = v - liveMean
                val tVal = template.getOrElse(tIdx) { 0f } - tMean
                dot += centered * tVal
                normA += centered * centered
                normB += tVal * tVal
                tIdx++
                if (tIdx >= TEMPLATE_SIZE) break
            }
            if (tIdx >= TEMPLATE_SIZE) break
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 1e-10f) dot / denom else 0f
    }

    fun serializeTemplate(features: Array<FloatArray>): String {
        val sb = StringBuilder(TEMPLATE_SIZE * 10)
        var count = 0
        for (f in features) {
            for (v in f) {
                if (count > 0) sb.append(',')
                sb.append(v)
                count++
                if (count >= TEMPLATE_SIZE) break
            }
            if (count >= TEMPLATE_SIZE) break
        }
        return sb.toString()
    }

    fun deserializeTemplate(s: String): FloatArray? {
        if (s.isBlank()) return null
        val parts = s.split(',')
        if (parts.size < TEMPLATE_SIZE) return null
        return FloatArray(TEMPLATE_SIZE) { i -> parts[i].toFloatOrNull() ?: return null }
    }

    fun averageTemplates(templates: List<FloatArray>): FloatArray {
        val result = FloatArray(TEMPLATE_SIZE)
        for (t in templates) {
            for (i in result.indices) result[i] += t[i]
        }
        for (i in result.indices) result[i] /= templates.size
        return result
    }
}
