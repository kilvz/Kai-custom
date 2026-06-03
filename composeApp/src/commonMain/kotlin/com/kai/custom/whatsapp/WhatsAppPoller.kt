package com.kai.custom.whatsapp

import com.kai.custom.data.DataRepository
import com.kai.custom.data.MemoryCategory
import com.kai.custom.data.MemoryStore
import com.kai.custom.data.SharedJson
import com.kai.custom.data.WhatsAppPendingMessage
import com.kai.custom.data.WhatsAppStore
import com.kai.custom.mcp.McpServerManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class WhatsAppPoller(
    private val whatsAppStore: WhatsAppStore,
    private val dataRepository: Lazy<DataRepository>,
    private val mcpServerManager: McpServerManager,
    private val memoryStore: MemoryStore,
) {
    private val json = SharedJson

    suspend fun poll() {
        if (!whatsAppStore.isWhatsAppEnabled()) return
        if (!whatsAppStore.isWhatsAppInstalled()) return
        if (!whatsAppStore.isWhatsAppAuthenticated()) return

        val client = mcpServerManager.getClient("whatsapp") ?: return

        try {
            val resultStr = client.callTool("get_unread_messages", buildJsonObject { })
            if (resultStr.isBlank() || resultStr == "[]") return

            val messages = try {
                json.decodeFromString<List<WhatsAppPendingMessage>>(resultStr)
            } catch (_: Exception) {
                return
            }

            if (messages.isEmpty()) return

            val existing = whatsAppStore.getPending().toMutableList()
            val existingKeys = existing.map { "${it.chatId}:${it.messageId}" }.toSet()
            val newMessages = messages.filter { "${it.chatId}:${it.messageId}" !in existingKeys }

            if (newMessages.isEmpty()) return

            whatsAppStore.setPending(existing + newMessages)

            for (msg in newMessages) {
                storeMessageAsMemory(msg)
                handleMessage(msg)
            }

            client.callTool("clear_unread_messages", buildJsonObject { })
        } catch (_: Exception) {
        }
    }

    private suspend fun handleMessage(msg: WhatsAppPendingMessage) {
        if (whatsAppStore.isWhatsAppReadOnly()) return
        try {
            val response = dataRepository.value.askSilently(msg.text)
            if (response.isNotBlank()) {
                sendMessage(msg.chatId, response)
            }
        } catch (_: Exception) {
        }
    }

    suspend fun sendMessage(chatId: String, text: String) {
        val client = mcpServerManager.getClient("whatsapp") ?: return
        try {
            client.callTool(
                "send_message",
                buildJsonObject {
                    put("phone", JsonPrimitive(chatId))
                    put("text", JsonPrimitive(text))
                },
            )
        } catch (_: Exception) {
        }
    }

    suspend fun sendProactiveMessage(chatId: String, text: String) {
        sendMessage(chatId, text)
    }

    private suspend fun storeMessageAsMemory(msg: WhatsAppPendingMessage) {
        try {
            memoryStore.store(
                key = "whatsapp_msg_${msg.chatId}_${msg.messageId}",
                content = "WhatsApp message from ${msg.fromName} (${msg.chatId}): ${msg.text}",
                category = MemoryCategory.GENERAL,
                source = "whatsapp",
            )
        } catch (_: Exception) {
        }
    }
}
