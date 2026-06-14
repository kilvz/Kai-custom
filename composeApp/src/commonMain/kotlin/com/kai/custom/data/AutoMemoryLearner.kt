package com.kai.custom.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Batched inline memory extraction: every ~N exchanges, calls [askSilently] with a compact
 * prompt to extract user facts/preferences from recent conversation, then stores them as
 * unprotected memories via [memoryStore].
 *
 * Design:
 * - Counter increments on every exchange (user message + AI response pair).
 * - When counter reaches [EXTRACTION_INTERVAL], runs extraction asynchronously.
 * - Extraction prompt asks for JSON array of {key, content, category} objects.
 * - Each extracted item is stored via [memoryStore.store()] (unprotected, user-facing).
 * - Protected (behavior) memories are NOT written here — that's the heartbeat's job.
 */
class AutoMemoryLearner(
    private val memoryStore: MemoryStore,
    private val dataRepository: DataRepository,
    private val scope: CoroutineScope,
    private val appSettings: AppSettings,
) {
    @Volatile
    private var exchangeCount = 0

    /** Call after every successful AI response. Triggers extraction when interval reached. */
    fun onExchangeComplete() {
        if (!appSettings.isMemoryEnabled()) {
            exchangeCount = 0
            return
        }
        exchangeCount++
        if (exchangeCount >= EXTRACTION_INTERVAL) {
            exchangeCount = 0
            triggerExtraction()
        }
    }

    private fun triggerExtraction() {
        scope.launch {
            try {
                val recentHistory = dataRepository.getRecentExchanges(PAIR_COUNT)
                if (recentHistory.isEmpty()) return@launch

                val prompt = buildExtractionPrompt(recentHistory)
                val response = dataRepository.askSilently(prompt)
                if (response.isBlank()) return@launch

                val extracted = parseExtraction(response)
                for (item in extracted) {
                    if (memoryStore.containsKey(item.key)) continue
                    memoryStore.store(
                        key = item.key,
                        content = item.content,
                        category = item.category,
                        source = "auto_learner",
                    )
                }
            } catch (_: Exception) {
                // Silently fail — extraction is best-effort
            }
        }
    }

    private fun buildExtractionPrompt(recentHistory: String): String =
        """
Extract user facts and preferences from this conversation. Return ONLY a JSON array.
Each element: {"key": "short_unique_key", "content": "value", "category": "GENERAL|PREFERENCE|LEARNING|ERROR"}

Only extract:
- Named entities (name, job, location, etc.)
- Explicit preferences stated by user
- Facts the user shared about themselves
- Errors/resolutions mentioned

Do NOT extract:
- Transient chat topics
- General knowledge
- Already-known information

Conversation:
$recentHistory

JSON:
        """.trimIndent()

    private data class ExtractedItem(
        val key: String,
        val content: String,
        val category: MemoryCategory,
    )

    private fun parseExtraction(response: String): List<ExtractedItem> {
        val arrayStart = response.indexOf('[')
        val arrayEnd = response.lastIndexOf(']')
        if (arrayStart < 0 || arrayEnd <= arrayStart) return emptyList()
        val jsonStr = response.substring(arrayStart, arrayEnd + 1)
        return try {
            val elements = SharedJson.parseToJsonElement(jsonStr) as? JsonArray ?: return emptyList()
            elements.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val key = (obj["key"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val content = (obj["content"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val catStr = (obj["category"] as? JsonPrimitive)?.content ?: "GENERAL"
                val category = try {
                    MemoryCategory.valueOf(catStr)
                } catch (_: Exception) {
                    MemoryCategory.GENERAL
                }
                ExtractedItem(key, content, category)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        /** Extract every N exchanges (user message + AI response = 1 pair). */
        const val EXTRACTION_INTERVAL = 5

        /** Number of recent exchange pairs to include in the extraction prompt. */
        private const val PAIR_COUNT = 3
    }
}
