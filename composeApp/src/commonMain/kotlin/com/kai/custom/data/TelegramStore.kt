package com.kai.custom.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer

class TelegramStore(private val appSettings: AppSettings) {

    private val json = SharedJson
    private val mutex = Mutex()
    private val pendingQueue = PendingQueue<TelegramPendingMessage, String>(
        readJson = appSettings::getTelegramPendingJson,
        writeJson = appSettings::setTelegramPendingJson,
        serializer = ListSerializer(serializer<TelegramPendingMessage>()),
        keyOf = { "${it.chatId}:${it.messageId}" },
    )

    fun getBotToken(): String = appSettings.getTelegramBotToken()
    fun setBotToken(token: String) = appSettings.setTelegramBotToken(token)

    fun isTelegramEnabled(): Boolean = appSettings.isTelegramEnabled()
    fun setTelegramEnabled(enabled: Boolean) = appSettings.setTelegramEnabled(enabled)

    fun getSyncState(): TelegramSyncState {
        val raw = appSettings.getTelegramSyncStateJson()
        if (raw.isEmpty()) return TelegramSyncState()
        return try {
            json.decodeFromString(serializer<TelegramSyncState>(), raw)
        } catch (_: Exception) {
            TelegramSyncState()
        }
    }

    suspend fun updateSyncState(state: TelegramSyncState) = mutex.withLock {
        appSettings.setTelegramSyncStateJson(json.encodeToString(serializer<TelegramSyncState>(), state))
    }

    fun getPending(): List<TelegramPendingMessage> = pendingQueue.get()
    suspend fun addPending(messages: List<TelegramPendingMessage>) = pendingQueue.add(messages)
    suspend fun removePending(messages: List<TelegramPendingMessage>) = pendingQueue.remove(messages)

    fun getAuthorizedChatIds(): Set<Long> = appSettings.getTelegramAuthorizedChatIds()
    fun setAuthorizedChatIds(ids: Set<Long>) = appSettings.setTelegramAuthorizedChatIds(ids)
}
