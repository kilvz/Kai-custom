package com.kai.custom.shizuku

import kotlinx.serialization.Serializable

@Serializable
data class CommandResultDto(
    val exitCode: Int,
    val stdout: String = "",
    val stderr: String = "",
    val timedOut: Boolean = false,
    val error: String? = null,
) {
    fun toMap(): Map<String, Any> = mapOf(
        "success" to (error == null && exitCode == 0),
        "exit_code" to exitCode,
        "stdout" to stdout,
        "stderr" to stderr,
        "timed_out" to timedOut,
        "error" to (error ?: ""),
    )
}
