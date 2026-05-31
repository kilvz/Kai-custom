package com.kai.custom.data.dimension

import com.kai.custom.mcp.McpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

data class SyncResult(
    val localPushed: Int = 0,
    val remotePulled: Int = 0,
    val conflicts: Int = 0,
    val errors: List<String> = emptyList(),
)

interface SyncSource {
    suspend fun exportAll(): ByteArray
    suspend fun importAll(data: ByteArray): Boolean
    fun getName(): String
}

class DimensionSyncSource(private val store: DimensionStore) : SyncSource {
    override suspend fun exportAll(): ByteArray = store.getExportData()
    override suspend fun importAll(data: ByteArray): Boolean {
        return try {
            store.importFromData(data)
            true
        } catch (_: Exception) {
            false
        }
    }
    override fun getName(): String = "local"
}

class McpSyncSource(private val client: McpClient) : SyncSource {
    override suspend fun exportAll(): ByteArray {
        val jsonStr = client.callTool("dimension_export", buildJsonObject { })
        return jsonStr.encodeToByteArray()
    }
    override suspend fun importAll(data: ByteArray): Boolean {
        return try {
            client.callTool("dimension_import", buildJsonObject {
                put("data", JsonPrimitive(data.decodeToString()))
            })
            true
        } catch (_: Exception) {
            false
        }
    }
    override fun getName(): String = "alt-memory"

    private fun String.encodeToByteArray(): ByteArray = this.toByteArray(Charsets.UTF_8)
}

class SyncEngine(
    private val local: SyncSource,
    private val remote: SyncSource?,
) {
    private var lastSyncAt: Long = 0
    private val syncInterval = 5.minutes.inWholeMilliseconds

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun isSyncDue(): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()
        return (now - lastSyncAt) >= syncInterval
    }

    fun markSynced() {
        lastSyncAt = Clock.System.now().toEpochMilliseconds()
    }

    suspend fun sync(): SyncResult {
        val target = remote ?: return SyncResult()
        val errors = mutableListOf<String>()
        var localPushed = 0
        var remotePulled = 0

        try {
            val localData = local.exportAll()
            if (localData.isNotEmpty()) {
                target.importAll(localData)
                localPushed = countEntities(localData)
            }
        } catch (e: Exception) {
            errors.add("Push to ${target.getName()}: ${e.message}")
        }

        try {
            val remoteData = target.exportAll()
            if (remoteData.isNotEmpty()) {
                local.importAll(remoteData)
                remotePulled = countEntities(remoteData)
            }
        } catch (e: Exception) {
            errors.add("Pull from ${target.getName()}: ${e.message}")
        }

        markSynced()
        return SyncResult(
            localPushed = localPushed,
            remotePulled = remotePulled,
            errors = errors,
        )
    }

    private fun countEntities(data: ByteArray): Int {
        return try {
            val text = data.decodeToString()
            json.parseToJsonElement(text).jsonObject["entities"]?.jsonArray?.size ?: 0
        } catch (_: Exception) {
            0
        }
    }
}
