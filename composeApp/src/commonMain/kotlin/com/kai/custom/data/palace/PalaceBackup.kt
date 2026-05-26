package com.kai.custom.data.palace

import kotlinx.serialization.Serializable

@Serializable
data class PalaceBackupMeta(
    val version: Int = 1,
    val exportedAt: Long,
    val drawerCount: Int,
    val kgFactCount: Int,
    val appVersion: String = "",
)

sealed interface BackupResult {
    data object Success : BackupResult
    data class Error(val message: String) : BackupResult
}

interface PalaceBackupManager {
    suspend fun exportBackup(store: PalaceStore): ByteArray
    suspend fun importBackup(store: PalaceStore, data: ByteArray): BackupResult
    fun getBackupMeta(data: ByteArray): PalaceBackupMeta?
}
