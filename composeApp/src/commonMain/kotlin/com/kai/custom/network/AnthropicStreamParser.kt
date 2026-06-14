package com.kai.custom.network

import com.kai.custom.ui.chat.ToolCallInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AnthropicStreamParser(
    private val onEvent: suspend (StreamEvent) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private data class ToolUseBuf(
        var id: String? = null,
        var name: String? = null,
        val arguments: StringBuilder = StringBuilder(),
    )

    private val toolUseBufs = mutableMapOf<Int, ToolUseBuf>()

    suspend fun onEvent(event: String?, data: String) {
        try {
            when (event) {
                "content_block_start" -> handleContentBlockStart(data)
                "content_block_delta" -> handleContentBlockDelta(data)
                "content_block_stop" -> {}
                "message_delta" -> handleMessageDelta(data)
                "message_stop" -> {
                    flushToolUses()
                    onEvent(StreamEvent.Finished(null))
                }
                "ping" -> {}
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun handleContentBlockStart(data: String) {
        val obj = json.parseToJsonElement(data).jsonObject
        val contentBlock = obj["content_block"]?.jsonObject ?: return
        val type = contentBlock["type"]?.jsonPrimitive?.content ?: return
        val index = obj["index"]?.jsonPrimitive?.int ?: return

        when (type) {
            "text" -> {}
            "tool_use" -> {
                val buf = ToolUseBuf()
                buf.id = contentBlock["id"]?.jsonPrimitive?.content
                buf.name = contentBlock["name"]?.jsonPrimitive?.content
                toolUseBufs[index] = buf
            }
        }
    }

    private suspend fun handleContentBlockDelta(data: String) {
        val obj = json.parseToJsonElement(data).jsonObject
        val delta = obj["delta"]?.jsonObject ?: return
        val type = delta["type"]?.jsonPrimitive?.content ?: return
        val index = obj["index"]?.jsonPrimitive?.int ?: return

        when (type) {
            "text_delta" -> {
                val text = delta["text"]?.jsonPrimitive?.content ?: return
                onEvent(StreamEvent.Token(text))
            }
            "input_json_delta" -> {
                val partialJson = delta["partial_json"]?.jsonPrimitive?.content ?: return
                toolUseBufs[index]?.arguments?.append(partialJson)
            }
        }
    }

    private fun handleMessageDelta(data: String) {
        val obj = json.parseToJsonElement(data).jsonObject
        val delta = obj["delta"]?.jsonObject
        val stopReason = delta?.get("stop_reason")?.jsonPrimitive?.content
    }

    private suspend fun flushToolUses() {
        if (toolUseBufs.isEmpty()) return
        val calls = toolUseBufs.values.mapNotNull { buf ->
            val name = buf.name ?: return@mapNotNull null
            ToolCallInfo(
                id = buf.id ?: "anthropic-${name.hashCode()}",
                name = name,
                arguments = buf.arguments.toString().ifEmpty { "{}" },
            )
        }
        toolUseBufs.clear()
        if (calls.isNotEmpty()) {
            onEvent(StreamEvent.ToolCalls(calls))
        }
    }

    fun reset() { toolUseBufs.clear() }
}
