package com.kai.custom.tools

import com.kai.custom.data.WhatsAppStore
import com.kai.custom.network.tools.ParameterSchema
import com.kai.custom.network.tools.Tool
import com.kai.custom.network.tools.ToolInfo
import com.kai.custom.network.tools.ToolSchema
import com.kai.custom.whatsapp.WhatsAppPoller

fun getWhatsAppTools(
    whatsAppStore: WhatsAppStore,
    whatsAppPoller: WhatsAppPoller,
): List<Tool> = listOf(
    object : Tool {
        override val schema = ToolSchema(
            name = "check_whatsapp",
            description = "Check for new WhatsApp messages and process them. The AI will be prompted " +
                "with each message and can respond. Use this to manually trigger a WhatsApp poll.",
            parameters = emptyMap(),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val before = whatsAppStore.getPending().size
            whatsAppPoller.poll()
            val after = whatsAppStore.getPending().size
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
            name = "send_whatsapp_message",
            description = "Send a WhatsApp message to a phone number or chat ID. Use the chat_id from a " +
                "previously received message. If you don't know the chat_id, check recent messages first.",
            parameters = mapOf(
                "chat_id" to ParameterSchema(
                    type = "string",
                    description = "The WhatsApp chat ID (phone number with country code, e.g. 628123456789)",
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
            val chatId = args["chat_id"]?.toString()
                ?: return mapOf("success" to false, "error" to "chat_id is required")
            val text = args["text"]?.toString()
                ?: return mapOf("success" to false, "error" to "text is required")
            return try {
                whatsAppPoller.sendProactiveMessage(chatId, text)
                mapOf("success" to true, "message" to "Message sent to $chatId")
            } catch (e: Exception) {
                mapOf("success" to false, "error" to "Failed to send: ${e.message}")
            }
        }
    },
)

val whatsAppToolDefinitions: List<ToolInfo> = listOf(
    ToolInfo(
        id = "check_whatsapp",
        name = "Check WhatsApp",
        description = "Poll WhatsApp for new messages",
    ),
    ToolInfo(
        id = "send_whatsapp_message",
        name = "Send WhatsApp Message",
        description = "Send a proactive message to a WhatsApp chat",
    ),
)
