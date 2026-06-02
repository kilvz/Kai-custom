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

@Serializable
data class MemoryRequest(
    val key: String,
    val content: String,
    val category: String? = null,
)

@Serializable
data class SearchRequest(
    val query: String,
    val limit: Int? = null,
)

@Serializable
data class ToolCallRequest(
    val arguments: Map<String, String> = emptyMap(),
)

@Serializable
data class ToolCallResponse(
    val success: Boolean,
    val name: String,
    val result: String = "",
    val error: String? = null,
)

@Serializable
data class AltMemoryStatusResponse(
    val enabled: Boolean,
    val installed: Boolean,
    val connected: Boolean,
    val localMemoryCount: Int,
    val behaviorMemoryCount: Int,
    val migrationComplete: Boolean,
)
