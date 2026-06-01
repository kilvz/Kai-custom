package com.kai.custom.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Post-heartbeat behavior extraction: after each heartbeat AI response, calls [askSilently]
 * to extract behavioral patterns from the conversation and stores them as protected memories
 * via [memoryStore.storeProtected].
 *
 * This is the heartbeat counterpart of [AutoMemoryLearner] (which extracts user-facing facts
 * from regular chat). Together they form the three-layer learning system:
 * 1. AI tools (on-demand via memory_store/memory_learn)
 * 2. AutoMemoryLearner (inline batch extraction every 5 exchanges — unprotected)
 * 3. HeartbeatMemoryExtractor (post-heartbeat extraction — protected)
 */
class HeartbeatMemoryExtractor(
    private val memoryStore: MemoryStore,
    private val dataRepository: DataRepository,
) {
    suspend fun extractFromHeartbeat(response: String) {
        if (response.isBlank() || "HEARTBEAT_OK" in response) return

        try {
            val context = dataRepository.getRecentExchanges(PAIR_COUNT)
            val prompt = buildExtractionPrompt(context)
            val extraction = dataRepository.askSilently(prompt)
            if (extraction.isBlank()) return

            val existingKeys = memoryStore.getAllMemories().map { it.key }.toSet()
            val items = parseExtraction(extraction)
            var extractedCount = 0
            for (item in items) {
                if (item.key in existingKeys) continue
                memoryStore.storeProtected(
                    key = item.key,
                    content = item.content,
                    category = item.category,
                    source = "heartbeat",
                )
                extractedCount++
            }

            // Condense all behavior memories into soul_auto for prompt visibility
            if (extractedCount > 0) {
                condenseToSoulAuto()
            }
        } catch (_: Exception) {
            // Silently fail — extraction is best-effort
        }
    }

    private suspend fun condenseToSoulAuto() {
        val behaviorMemories = memoryStore.getBehaviorMemories()
        if (behaviorMemories.isEmpty()) return

        val memoriesText = behaviorMemories.joinToString("\n") { "- ${it.content}" }
        val condensePrompt = """
Condense these behavioral observations into a compact 2-3 sentence summary of the user's patterns, preferences, and communication style. Return ONLY the summary text, no labels or prefixes.

Observations:
$memoriesText

Summary:
        """.trimIndent()

        val summary = dataRepository.askSilently(condensePrompt)
        if (summary.isNotBlank()) {
            dataRepository.setSoulAuto(summary)
        }
    }

    private fun buildExtractionPrompt(context: String): String =
        """
Extract behavioral patterns and recurring themes from this conversation.
Return ONLY a JSON array.
Each element: {"key": "short_unique_key", "content": "observation", "category": "LEARNING|GENERAL"}

Only extract:
- Repeated behavioral patterns (user's communication style, preferences)
- Recurring themes (topics the user engages with consistently)
- Behavior adjustments (how the AI's style should adapt)

Do NOT extract:
- Transient topics mentioned once
- General knowledge
- Facts already in memory

Conversation context:
$context

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
        return try {
            val jsonStr = response.substring(arrayStart, arrayEnd + 1)
            val elements = SharedJson.parseToJsonElement(jsonStr) as? JsonArray ?: return emptyList()
            elements.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val key = (obj["key"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val content = (obj["content"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val catStr = (obj["category"] as? JsonPrimitive)?.content ?: "LEARNING"
                val category = try {
                    MemoryCategory.valueOf(catStr)
                } catch (_: Exception) {
                    MemoryCategory.LEARNING
                }
                ExtractedItem(key, content, category)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PAIR_COUNT = 3
    }
}
