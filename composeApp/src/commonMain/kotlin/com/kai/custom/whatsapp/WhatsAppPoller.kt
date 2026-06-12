package com.kai.custom.whatsapp

import com.kai.custom.data.DataRepository
import com.kai.custom.data.SharedJson
import com.kai.custom.data.WhatsAppPendingMessage
import com.kai.custom.data.WhatsAppStore
import com.kai.custom.mcp.McpServerManager
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class WhatsAppPoller(
    private val whatsAppStore: WhatsAppStore,
    private val dataRepository: Lazy<DataRepository>,
    private val mcpServerManager: McpServerManager,
) {
    private val json = SharedJson

    companion object {
        private const val TAG = "[WhatsAppPoller]"

        /** Max pending messages kept in SharedPreferences to prevent unbounded growth. */
        private const val MAX_PENDING = 200
    }

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
            } catch (e: Exception) {
                println("$TAG Failed to parse unread messages: ${e.message}")
                return
            }

            if (messages.isEmpty()) return

            val existing = whatsAppStore.getPending().toMutableList()
            val existingKeys = existing.map { "${it.chatId}:${it.messageId}" }.toSet()
            val newMessages = messages.filter { "${it.chatId}:${it.messageId}" !in existingKeys }

            if (newMessages.isEmpty()) return

            // Cap pending list to prevent unbounded SharedPreferences growth
            val updated = (existing + newMessages).takeLast(MAX_PENDING)
            whatsAppStore.setPending(updated)

            for (msg in newMessages) {
                handleMessage(msg)
            }

            val markRead = whatsAppStore.isWhatsAppReadReceipt()
            client.callTool("clear_unread_messages", buildJsonObject { put("markRead", JsonPrimitive(markRead)) })
        } catch (e: Exception) {
            println("$TAG Poll error: ${e.message}")
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
        } catch (e: Exception) {
            println("$TAG Reply error for ${msg.chatId}: ${e.message}")
        }
    }

    private fun shouldReply(msg: WhatsAppPendingMessage): Boolean {
        return when (whatsAppStore.getWhatsAppReplyMode()) {
            "self" -> msg.fromMe

            "selected" -> {
                val contacts = whatsAppStore.getWhatsAppAllowedContacts()
                    .split(",")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                if (contacts.isEmpty()) return false
                // Extract phone from JID (e.g. "628123456789@s.whatsapp.net" → "628123456789")
                val phone = msg.chatId.substringBefore("@").lowercase()
                val name = msg.fromName.lowercase()
                contacts.any { filter -> phone == filter || phone.endsWith(filter) || name == filter }
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
                    put("text", JsonPrimitive(text))
                },
            )
        } catch (e: Exception) {
            println("$TAG Send error to $chatId: ${e.message}")
        }
    }

    suspend fun sendProactiveMessage(chatId: String, text: String) {
        sendMessage(chatId, sanitizeForWhatsApp(text))
    }
}
