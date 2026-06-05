package com.kai.custom.tools

import com.kai.custom.data.AppSettings
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

fun getWhatsAppAdminTools(
    appSettings: AppSettings,
    restartBridge: suspend () -> Unit,
    updateBridgeConfig: suspend () -> Unit,
): List<Tool> = listOf(
    object : Tool {
        override val schema = ToolSchema(
            name = "restart_whatsapp_bridge",
            description = "Restart the WhatsApp bridge process. Use this if the bridge is " +
                "unresponsive, disconnected, or after changing configuration.",
            parameters = emptyMap(),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            try {
                restartBridge()
                return mapOf("success" to true, "message" to "WhatsApp bridge restarted")
            } catch (e: Exception) {
                return mapOf("success" to false, "error" to "Failed to restart: ${e.message}")
            }
        }
    },

    object : Tool {
        override val schema = ToolSchema(
            name = "update_baileys_config",
            description = "Update the Baileys WhatsApp library configuration, including browser " +
                "identification (name and version), mark-online behavior, sync-history, and " +
                "link-preview settings. The bridge will be automatically restarted with the new values.",
            parameters = mapOf(
                "browser_name" to ParameterSchema(
                    type = "string",
                    description = "Browser name for WhatsApp identification (e.g. Windows, Chrome, Safari)",
                    required = false,
                ),
                "browser_version" to ParameterSchema(
                    type = "string",
                    description = "Browser version for WhatsApp identification (e.g. 130.0.0.0)",
                    required = false,
                ),
                "mark_online" to ParameterSchema(
                    type = "boolean",
                    description = "Whether to mark the account as online on connect",
                    required = false,
                ),
                "sync_history" to ParameterSchema(
                    type = "boolean",
                    description = "Whether to sync full message history on connect",
                    required = false,
                ),
                "link_previews" to ParameterSchema(
                    type = "boolean",
                    description = "Whether to generate high-quality link previews",
                    required = false,
                ),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            try {
                args["browser_name"]?.toString()?.takeIf { it.isNotBlank() }?.let {
                    appSettings.setBaileysBrowserName(it)
                }
                args["browser_version"]?.toString()?.takeIf { it.isNotBlank() }?.let {
                    appSettings.setBaileysBrowserVersion(it)
                }
                args["mark_online"]?.let {
                    appSettings.setBaileysMarkOnline(it.toString().toBooleanStrictOrNull() ?: true)
                }
                args["sync_history"]?.let {
                    appSettings.setBaileysSyncHistory(it.toString().toBooleanStrictOrNull() ?: false)
                }
                args["link_previews"]?.let {
                    appSettings.setBaileysLinkPreviews(it.toString().toBooleanStrictOrNull() ?: true)
                }
                updateBridgeConfig()
                return mapOf(
                    "success" to true,
                    "message" to "Baileys config updated and bridge restarting",
                    "browser_name" to appSettings.getBaileysBrowserName(),
                    "browser_version" to appSettings.getBaileysBrowserVersion(),
                    "mark_online" to appSettings.getBaileysMarkOnline(),
                    "sync_history" to appSettings.getBaileysSyncHistory(),
                    "link_previews" to appSettings.getBaileysLinkPreviews(),
                )
            } catch (e: Exception) {
                return mapOf("success" to false, "error" to "Failed to update config: ${e.message}")
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
    ToolInfo(
        id = "restart_whatsapp_bridge",
        name = "Restart WhatsApp Bridge",
        description = "Restart the WhatsApp bridge process",
    ),
    ToolInfo(
        id = "update_baileys_config",
        name = "Update Baileys Config",
        description = "Update WhatsApp library configuration and restart bridge",
    ),
)
