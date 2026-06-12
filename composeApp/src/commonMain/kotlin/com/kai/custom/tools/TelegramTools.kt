package com.kai.custom.tools

import com.kai.custom.data.TelegramPendingMessage
import com.kai.custom.data.TelegramStore
import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import com.kai.custom.telegram.TelegramPoller

fun getTelegramTools(
    telegramStore: TelegramStore,
    telegramPoller: TelegramPoller,
): List<Tool> = listOf(
    object : Tool {
        override val schema = ToolSchema(
            name = "check_telegram",
            description = "Check for new Telegram messages and process them. The AI will be prompted " +
                "with each message and can respond. Use this to manually trigger a Telegram poll.",
            parameters = emptyMap(),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val before = telegramStore.getPending().size
            telegramPoller.poll()
            val after = telegramStore.getPending().size
            val newCount = (after - before).coerceAtLeast(0)
            return mapOf(
                "success" to true,
                "new_messages" to newCount,
                "message" to if (newCount > 0) "Found $newCount new message(s)" else "No new messages",
            )
        }
    },

    object : Tool {
        override val schema = ToolSchema(
            name = "send_telegram_message",
            description = "Send a proactive message to a Telegram chat. Use the chat_id from a " +
                "previously received message. If you don't know the chat_id, check recent messages first.",
            parameters = mapOf(
                "chat_id" to ParameterSchema(
                    type = "number",
                    description = "The Telegram chat ID to send the message to",
                    required = true,
                ),
                "text" to ParameterSchema(
                    type = "string",
                    description = "The text of the message to send",
                    required = true,
                ),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val chatId = (args["chat_id"] as? Number)?.toLong()
                ?: return mapOf("success" to false, "error" to "chat_id is required and must be a number")
            val text = args["text"]?.toString()
                ?: return mapOf("success" to false, "error" to "text is required")
            return try {
                telegramPoller.sendProactiveMessage(chatId, text)
                mapOf("success" to true, "message" to "Message sent to chat $chatId")
            } catch (e: Exception) {
                mapOf("success" to false, "error" to "Failed to send: ${e.message}")
            }
        }
    },
)

val telegramToolDefinitions: List<ToolInfo> = listOf(
    ToolInfo(
        id = "check_telegram",
        name = "Check Telegram",
        description = "Poll Telegram for new messages",
    ),
    ToolInfo(
        id = "send_telegram_message",
        name = "Send Telegram Message",
        description = "Send a proactive message to a Telegram chat",
    ),
)
