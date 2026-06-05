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

    /**
     * Convert markdown formatting to WhatsApp-native formatting.
     *
     * WhatsApp supports:
     *   *bold*   _italic_   ~strikethrough~   ```monospace```
     *
     * But it does NOT support language identifiers on fenced code blocks
     * (e.g. ```python). Sending those causes the code to render as plain
     * text instead of monospace.
     */
    private fun sanitizeForWhatsApp(text: String): String {
        var result = text

        // 1. Strip language identifiers from fenced code blocks:
        //    ```python\n  →  ```\n
        //    ```js\n      →  ```\n
        //    ```kotlin\n  →  ```\n
        result = result.replace(Regex("```[a-zA-Z0-9_+#.-]+\\s*\\n"), "```\n")

        // 2. Convert markdown bold **text** → *text* (WhatsApp bold)
        //    But skip if already single-asterisk (avoid double-converting)
        result = result.replace(Regex("\\*\\*(.+?)\\*\\*"), "*$1*")

        // 3. Convert markdown links [text](url) → text (url)
        result = result.replace(Regex("\\[([^]]+)]\\(([^)]+)\\)"), "$1 ($2)")

        // 4. Convert markdown headers ## Title → *Title*
        result = result.replace(Regex("(?m)^#{1,6}\\s+(.+)$"), "*$1*")

        return result.trim()
    }

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

            val markRead = whatsAppStore.isWhatsAppReadReceipt()
            client.callTool("clear_unread_messages", buildJsonObject { put("markRead", JsonPrimitive(markRead)) })
        } catch (_: Exception) {
        }
    }

    private suspend fun handleMessage(msg: WhatsAppPendingMessage) {
        if (whatsAppStore.isWhatsAppReadOnly()) return
        if (!shouldReply(msg)) return
        try {
            val response = dataRepository.value.askSilently(msg.text)
            if (response.isNotBlank()) {
                sendMessage(msg.chatId, sanitizeForWhatsApp(response))
            }
        } catch (_: Exception) {
        }
    }

    private fun shouldReply(msg: WhatsAppPendingMessage): Boolean {
        return when (whatsAppStore.getWhatsAppReplyMode()) {
            "self" -> msg.fromMe
            "selected" -> {
                val contacts = whatsAppStore.getWhatsAppAllowedContacts()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                if (contacts.isEmpty()) return false
                contacts.any { msg.chatId.contains(it) || msg.fromName.contains(it) }
            }
            else -> !msg.fromMe
        }
    }

    suspend fun sendMessage(chatId: String, text: String) {
        val client = mcpServerManager.getClient("whatsapp") ?: return
        try {
            client.callTool(
                "send_message",
                buildJsonObject {
                    put("phone", JsonPrimitive(chatId))
                    put("text", JsonPrimitive(sanitizeForWhatsApp(text)))
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
            val phone = msg.sender.ifBlank { msg.chatId.split("@")[0] }
            memoryStore.store(
                key = "whatsapp_msg_${msg.chatId}_${msg.messageId}",
                content = "WhatsApp message from ${msg.fromName} (phone: $phone): ${msg.text}",
                category = MemoryCategory.GENERAL,
                source = "whatsapp",
            )
        } catch (_: Exception) {
        }
    }
}
