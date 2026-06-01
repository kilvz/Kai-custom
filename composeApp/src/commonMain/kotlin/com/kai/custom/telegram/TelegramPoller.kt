package com.kai.custom.telegram

import com.kai.custom.data.DataRepository
import com.kai.custom.data.TelegramGetUpdatesResponse
import com.kai.custom.data.TelegramPendingMessage
import com.kai.custom.data.TelegramSendMessageResponse
import com.kai.custom.data.TelegramStore
import com.kai.custom.httpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class TelegramPoller(
    private val telegramStore: TelegramStore,
    private val dataRepository: DataRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = httpClient {}

    suspend fun poll() {
        val token = telegramStore.getBotToken()
        if (token.isBlank()) return
        if (!telegramStore.isTelegramEnabled()) return

        val syncState = telegramStore.getSyncState()
        val attemptAt = Clock.System.now().toEpochMilliseconds()

        try {
            val url = "https://api.telegram.org/bot$token/getUpdates"
            val offset = if (syncState.lastUpdateId > 0) syncState.lastUpdateId + 1 else null

            val responseText = client.get(url) {
                if (offset != null) parameter("offset", offset)
                parameter("timeout", 5)
            }.bodyAsText()

            val response = json.decodeFromString<TelegramGetUpdatesResponse>(responseText)
            if (!response.ok) {
                telegramStore.updateSyncState(syncState.copy(
                    lastAttemptEpochMs = attemptAt,
                    lastError = "API returned ok=false",
                ))
                return
            }

            if (response.result.isEmpty()) return

            val newMaxUpdateId = response.result.maxOf { it.updateId }

            val pending = response.result.mapNotNull { update ->
                val msg = update.message ?: return@mapNotNull null
                if (msg.text.isNullOrBlank()) return@mapNotNull null
                TelegramPendingMessage(
                    chatId = msg.chat.id,
                    messageId = msg.messageId,
                    text = msg.text,
                    fromName = msg.chat.firstName ?: msg.chat.username ?: "Unknown",
                    date = msg.date,
                )
            }

            if (pending.isNotEmpty()) {
                telegramStore.addPending(pending)
                for (entry in pending) {
                    handleMessage(entry)
                }
            }

            telegramStore.updateSyncState(syncState.copy(
                lastUpdateId = newMaxUpdateId,
                lastSyncEpochMs = attemptAt,
                lastAttemptEpochMs = attemptAt,
                lastError = null,
            ))
        } catch (e: Exception) {
            telegramStore.updateSyncState(syncState.copy(
                lastAttemptEpochMs = attemptAt,
                lastError = e.message ?: e::class.simpleName ?: "Poll failed",
            ))
        }
    }

    private suspend fun handleMessage(msg: TelegramPendingMessage) {
        val authorized = telegramStore.getAuthorizedChatIds()
        if (authorized.isNotEmpty() && msg.chatId !in authorized) return

        try {
            val response = dataRepository.askSilently(msg.text)
            if (response.isNotBlank()) {
                sendMessage(msg.chatId, response, msg.messageId)
            }
        } catch (_: Exception) {
        }
    }

    suspend fun sendMessage(chatId: Long, text: String, replyToMessageId: Long? = null) {
        val token = telegramStore.getBotToken()
        if (token.isBlank()) return
        try {
            val url = "https://api.telegram.org/bot$token/sendMessage"
            client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(mapOf(
                    "chat_id" to chatId,
                    "text" to text,
                    "reply_to_message_id" to replyToMessageId,
                ))
            }
        } catch (_: Exception) {
        }
    }

    suspend fun sendProactiveMessage(chatId: Long, text: String) {
        sendMessage(chatId, text)
    }
}
