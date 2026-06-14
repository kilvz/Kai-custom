package com.kai.custom.network

import com.kai.custom.ui.chat.ToolCallInfo

sealed class StreamEvent {
    data class Token(val text: String) : StreamEvent()
    data class ToolCalls(val calls: List<ToolCallInfo>) : StreamEvent()
    data class Finished(val stopReason: String? = null) : StreamEvent()
    data class Error(val throwable: Throwable) : StreamEvent()
}

class StreamAccumulator {
    val textBuilder = StringBuilder()
    val toolCalls = mutableListOf<ToolCallInfo>()

    fun appendToken(text: String) { textBuilder.append(text) }
    fun appendToolCalls(calls: List<ToolCallInfo>) { toolCalls.addAll(calls) }
    fun build(): StreamResult = StreamResult(text = textBuilder.toString(), toolCalls = toolCalls.toList())
}

data class StreamResult(
    val text: String,
    val toolCalls: List<ToolCallInfo>,
)
