package com.kai.custom.data

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val token: String,
)

@Serializable
data class ChatRequest(
    val message: String,
)

@Serializable
data class ChatResponse(
    val response: String,
    val toolCalls: List<ToolCallInfo> = emptyList(),
)

@Serializable
data class ToolCallInfo(
    val name: String,
    val arguments: String,
    val result: String? = null,
)

@Serializable
data class StateResponse(
    val historyCount: Int,
    val memoryCount: Int,
    val toolCount: Int,
    val isDaemonEnabled: Boolean,
    val isMemoryEnabled: Boolean,
    val isSchedulingEnabled: Boolean,
    val isHeartbeatEnabled: Boolean,
    val currentServiceId: String,
)

@Serializable
data class SettingUpdateRequest(
    val value: String,
)

@Serializable
data class ErrorResponse(
    val error: String,
)
