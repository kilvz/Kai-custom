package com.kai.custom.tools

import com.kai.custom.data.DiaryEntry
import com.kai.custom.data.MemoryCategory
import com.kai.custom.data.MemoryEntry
import com.kai.custom.data.MemoryStore
import com.kai.custom.data.dimension.KGFact
import com.kai.custom.network.tools.Tool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the pairing between a tool's executable schema and the [com.kai.custom.network.tools.ToolInfo]
 * the UI resolves its display name from.
 *
 * A tool that runs but has no definition falls back to showing its raw id in chat, which is how
 * the notification tools regressed: `notificationToolDefinitions` existed but nothing referenced it.
 */
class ToolDefinitionsTest {

    private val definitionIds = CommonTools.commonToolDefinitions.map { it.id }

    @Test
    fun everyCommonToolHasADefinition() {
        // Tools with no collaborators, gated by per-tool switches.
        val stateless: List<Tool> = listOf(
            CommonTools.localTimeTool,
            CommonTools.ipLocationTool,
            CommonTools.openUrlTool,
            WebSearchTool,
            FetchUrlTool,
        )
        val missing = stateless.map { it.schema.name }.filterNot { it in definitionIds }
        assertTrue(missing.isEmpty(), "Executable tools with no ToolInfo (chat shows the raw id): $missing")
    }

    /**
     * MemoryStore is an interface now; the memory tools only need an instance to
     * build their schemas — no store method is invoked by this test.
     */
    private val memoryStore = object : MemoryStore {
        override suspend fun store(key: String, content: String, category: MemoryCategory, source: String?): MemoryEntry = MemoryEntry(key, content, 0, 0)

        override suspend fun updateContent(key: String, content: String): MemoryEntry? = null
        override suspend fun reinforceMemory(key: String): MemoryEntry? = null
        override suspend fun forget(key: String): Boolean = false
        override suspend fun deleteAllMemories(force: Boolean) {}
        override suspend fun storeProtected(key: String, content: String, category: MemoryCategory, source: String?): MemoryEntry = MemoryEntry(key, content, 0, 0)

        override fun getUserMemories(max: Int): List<MemoryEntry> = emptyList()
        override fun getBehaviorMemories(): List<MemoryEntry> = emptyList()
        override fun getAllMemories(max: Int): List<MemoryEntry> = emptyList()
        override fun searchMemories(query: String, limit: Int, mode: String): List<MemoryEntry> = emptyList()
        override fun getPromotionCandidates(minHits: Int, max: Int): List<MemoryEntry> = emptyList()
        override fun exportDimension(): ByteArray = ByteArray(0)
        override fun importDimension(data: ByteArray) {}
        override suspend fun addFact(subject: String, predicate: String, `object`: String): KGFact = KGFact(id = "", subject = subject, predicate = predicate, `object` = `object`, createdAt = 0)

        override fun queryFacts(entity: String?, relation: String?, limit: Int): List<KGFact> = emptyList()
        override suspend fun invalidateFact(subject: String, predicate: String, `object`: String) {}
        override suspend fun diaryWrite(agentName: String, content: String, topic: String) {}
        override fun diaryRead(agentName: String, lastN: Int): List<DiaryEntry> = emptyList()
        override suspend fun diaryDelete(id: String): Boolean = false
    }

    @Test
    fun everyMemoryToolHasADefinition() {
        val missing = CommonTools.getMemoryTools(memoryStore)
            .map { it.schema.name }
            .filterNot { it in definitionIds }
        assertTrue(missing.isEmpty(), "Memory tools with no ToolInfo: $missing")
    }

    @Test
    fun notificationToolDefinitionsAreRegistered() {
        val missing = NotificationTools.notificationToolDefinitions
            .map { it.id }
            .filterNot { it in definitionIds }
        assertTrue(missing.isEmpty(), "Notification tools missing from commonToolDefinitions: $missing")
    }

    @Test
    fun definitionIdsAreUnique() {
        val duplicates = definitionIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertEquals(emptySet(), duplicates, "Duplicate tool definition ids")
    }

    @Test
    fun masterToggleControlledToolsAreNotUserToggleable() {
        // These are gated by a single switch in Settings → Agent, so the Tools tab must not
        // offer per-tool switches for them — nothing would read the value back.
        val masterToggled = SchedulingTools.schedulingToolDefinitions +
            HeartbeatTools.heartbeatToolDefinitions +
            EmailTools.emailToolDefinitions +
            SmsTools.smsToolDefinitions +
            NotificationTools.notificationToolDefinitions
        val leaked = masterToggled.filter { it.userToggleable }.map { it.id }
        assertTrue(leaked.isEmpty(), "Master-toggle-controlled tools exposed as per-tool switches: $leaked")
    }
}
