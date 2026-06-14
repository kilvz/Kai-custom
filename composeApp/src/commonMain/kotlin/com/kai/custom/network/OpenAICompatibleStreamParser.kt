package com.kai.custom.network

import com.kai.custom.ui.chat.ToolCallInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OpenAICompatibleStreamParser(
    private val onEvent: suspend (StreamEvent) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private data class ToolCallBuf(
        var id: String? = null,
        var name: String? = null,
        val arguments: StringBuilder = StringBuilder(),
    )

    private val toolCallBufs = mutableMapOf<Int, ToolCallBuf>()

    suspend fun onData(data: String) {
        if (data == "[DONE]") {
            flushToolCalls()
            onEvent(StreamEvent.Finished(null))
            return
        }

        val chunk = try {
            json.parseToJsonElement(data).jsonObject
        } catch (_: Exception) {
            return
        }

        val choices = chunk["choices"]?.jsonArray ?: return
        for (choice in choices) {
            val obj = choice.jsonObject
            val delta = obj["delta"]?.jsonObject ?: continue
            val finishReason = obj["finish_reason"]?.jsonPrimitive?.content

            val content = delta["content"]?.jsonPrimitive?.content
            if (!content.isNullOrEmpty()) {
                onEvent(StreamEvent.Token(content))
            }

            val toolCalls = delta["tool_calls"]?.jsonArray
            if (toolCalls != null) {
                for (tc in toolCalls) {
                    val tcObj = tc.jsonObject
                    val index = tcObj["index"]?.jsonPrimitive?.int ?: continue
                    val buf = toolCallBufs.getOrPut(index) { ToolCallBuf() }

                    tcObj["id"]?.jsonPrimitive?.content?.let { buf.id = it }
                    val fn = tcObj["function"]?.jsonObject
                    fn?.get("name")?.jsonPrimitive?.content?.let { buf.name = it }
                    fn?.get("arguments")?.jsonPrimitive?.content?.let { buf.arguments.append(it) }
                }
            }

            if (!finishReason.isNullOrEmpty() && finishReason != "stop") {
                flushToolCalls()
            }
        }
    }

    private suspend fun flushToolCalls() {
        if (toolCallBufs.isEmpty()) return
        val calls = toolCallBufs.values.mapNotNull { buf ->
            val name = buf.name ?: return@mapNotNull null
            ToolCallInfo(
                id = buf.id ?: "stream-${name.hashCode()}",
                name = name,
                arguments = buf.arguments.toString(),
            )
        }
        toolCallBufs.clear()
        if (calls.isNotEmpty()) {
            onEvent(StreamEvent.ToolCalls(calls))
        }
    }

    fun reset() { toolCallBufs.clear() }
}
