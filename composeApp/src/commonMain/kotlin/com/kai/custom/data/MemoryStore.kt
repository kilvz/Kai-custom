package com.kai.custom.data

import androidx.compose.runtime.Immutable
import com.kai.custom.data.palace.Drawer
import com.kai.custom.data.palace.KGFact
import com.kai.custom.data.palace.PalaceConfig
import com.kai.custom.data.palace.PalaceStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

@OptIn(ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)
class MemoryStore(private val palace: PalaceStore) {

    private val mutex = Mutex()

    private fun roomForCategory(category: MemoryCategory): String = when (category) {
        MemoryCategory.GENERAL -> PalaceConfig.ROOM_MEMORIES
        MemoryCategory.PREFERENCE -> PalaceConfig.ROOM_PREFERENCES
        MemoryCategory.LEARNING -> PalaceConfig.ROOM_LEARNINGS
        MemoryCategory.ERROR -> PalaceConfig.ROOM_ERRORS
    }

    private fun wingForCategory(category: MemoryCategory): String = when (category) {
        MemoryCategory.GENERAL,
        MemoryCategory.LEARNING,
        MemoryCategory.ERROR,
            -> PalaceConfig.WING_AGENT
        MemoryCategory.PREFERENCE -> PalaceConfig.WING_USER
    }

    private fun drawerToEntry(d: Drawer): MemoryEntry? {
        val key = d.metadata["memory_key"] ?: d.id.takeLast(36)
        val category = try {
            val cat = d.metadata["category"] ?: "GENERAL"
            MemoryCategory.valueOf(cat)
        } catch (_: Exception) {
            MemoryCategory.GENERAL
        }
        val hitCount = d.metadata["hit_count"]?.toIntOrNull() ?: 1
        val source = d.metadata["source"]
        return MemoryEntry(
            key = key,
            content = d.content,
            createdAt = d.createdAt,
            updatedAt = d.updatedAt,
            category = category,
            hitCount = hitCount,
            source = source,
        )
    }

    private fun entryToDrawer(entry: MemoryEntry): Drawer {
        val id = "mem_${entry.key.hashCode().toUInt().toString(16)}_${entry.createdAt}"
        return Drawer(
            id = id,
            wingId = wingForCategory(entry.category),
            roomId = roomForCategory(entry.category),
            content = entry.content,
            metadata = buildMap {
                put("memory_key", entry.key)
                put("category", entry.category.name)
                put("hit_count", entry.hitCount.toString())
                put("type", "memory_entry")
                entry.source?.let { put("source", it) }
            },
            createdAt = entry.createdAt,
            updatedAt = entry.updatedAt,
        )
    }

    private fun allDrawers(): List<Drawer> = palace.getAllDrawers()

    suspend fun store(
        key: String,
        content: String,
        category: MemoryCategory = MemoryCategory.GENERAL,
        source: String? = null,
    ): MemoryEntry = mutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            val existing = findEntryByKey(key)
            val entry = existing?.copy(
                content = content,
                updatedAt = now,
                category = category,
                source = source ?: existing.source,
            ) ?: MemoryEntry(
                key = key,
                content = content,
                createdAt = now,
                updatedAt = now,
                category = category,
                source = source,
            )
        palace.putDrawer(entryToDrawer(entry))
        entry
    }

    suspend fun updateContent(key: String, content: String): MemoryEntry? = mutex.withLock {
        val existing = findEntryByKey(key) ?: return@withLock null
        val updated = existing.copy(content = content, updatedAt = Clock.System.now().toEpochMilliseconds())
        palace.putDrawer(entryToDrawer(updated))
        updated
    }

    suspend fun reinforceMemory(key: String): MemoryEntry? = mutex.withLock {
        val existing = findEntryByKey(key) ?: return@withLock null
        val updated = existing.copy(hitCount = existing.hitCount + 1, updatedAt = Clock.System.now().toEpochMilliseconds())
        palace.putDrawer(entryToDrawer(updated))
        updated
    }

    fun getPromotionCandidates(minHits: Int = 5): List<MemoryEntry> =
        allDrawers().mapNotNull { drawerToEntry(it) }.filter { it.hitCount >= minHits }

    suspend fun forget(key: String): Boolean = mutex.withLock {
        val drawer = palace.getDrawerByMetadataKey("memory_key", key) ?: return@withLock false
        palace.deleteDrawer(drawer.id)
        true
    }

    fun getAllMemories(): List<MemoryEntry> =
        allDrawers().mapNotNull { drawerToEntry(it) }

    fun searchMemories(query: String, limit: Int = 10): List<MemoryEntry> {
        if (query.isBlank()) return emptyList()
        return palace.searchDrawers(query, limit).mapNotNull { drawerToEntry(it.drawer) }
    }

    private fun findEntryByKey(key: String): MemoryEntry? {
        val drawer = palace.getDrawerByMetadataKey("memory_key", key)
            ?: return null
        return drawerToEntry(drawer)
    }

    fun exportPalace(): ByteArray = palace.getExportData()

    fun importPalace(data: ByteArray) = palace.importFromData(data)
}
