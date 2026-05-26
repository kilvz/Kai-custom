package com.kai.custom.data.palace

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private val json = Json { ignoreUnknownKeys = true }

class AndroidPalaceBackup(private val context: Context) : PalaceBackupManager {

    override suspend fun exportBackup(store: PalaceStore): ByteArray = withContext(Dispatchers.IO) {
        store.getExportData()
    }

    override suspend fun importBackup(store: PalaceStore, data: ByteArray): BackupResult = withContext(Dispatchers.IO) {
        try {
            store.importFromData(data)
            BackupResult.Success
        } catch (e: Exception) {
            BackupResult.Error(e.message ?: "Import failed")
        }
    }

    override fun getBackupMeta(data: ByteArray): PalaceBackupMeta? {
        return try {
            val decompressed = GZIPInputStream(ByteArrayInputStream(data)).readBytes()
            val raw = json.decodeFromStream<Map<String, Any?>>(ByteArrayInputStream(decompressed))
            val drawerCount = (raw["drawers"] as? List<*>)?.size ?: 0
            val kgCount = (raw["kg_facts"] as? List<*>)?.size ?: 0
            val exportedAt = (raw["exported_at"] as? Number)?.toLong() ?: 0L
            PalaceBackupMeta(
                exportedAt = exportedAt,
                drawerCount = drawerCount,
                kgFactCount = kgCount,
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun exportToUri(store: PalaceStore, uri: Uri) = withContext(Dispatchers.IO) {
        val data = store.getExportData()
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(data)
        } ?: throw IllegalStateException("Cannot open output stream for $uri")
    }

    suspend fun importFromUri(store: PalaceStore, uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val data = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            } ?: return@withContext BackupResult.Error("Cannot open input stream")
            store.importFromData(data)
            BackupResult.Success
        } catch (e: Exception) {
            BackupResult.Error(e.message ?: "Import failed")
        }
    }
}
