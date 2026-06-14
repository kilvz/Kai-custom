package com.kai.custom.network

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line

class SseParser(
    private val channel: ByteReadChannel,
    private val onEvent: suspend (event: String?, data: String) -> Unit,
) {
    private var currentEvent: String? = null
    private var dataBuffer = StringBuilder()

    suspend fun parse() {
        try {
            while (!channel.isClosedForRead) {
                @Suppress("DEPRECATION")
                val line = channel.readUTF8Line() ?: break
                if (line.isEmpty()) {
                    dispatch()
                } else if (line.startsWith(":")) {
                    continue
                } else if (line.startsWith("event:")) {
                    currentEvent = line.removePrefix("event:").trim()
                } else if (line.startsWith("data:")) {
                    val payload = line.removePrefix("data:").trim()
                    if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                    dataBuffer.append(payload)
                }
            }
            dispatch()
        } catch (_: Exception) {
            dispatch()
        }
    }

    private suspend fun dispatch() {
        val data = dataBuffer.toString()
        dataBuffer = StringBuilder()
        if (data.isNotEmpty()) {
            onEvent(currentEvent, data)
        }
        currentEvent = null
    }
}
