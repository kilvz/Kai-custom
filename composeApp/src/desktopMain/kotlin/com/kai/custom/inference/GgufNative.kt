package com.kai.custom.inference

/** Desktop stub — GGUF inference is not available on desktop. */
class GgufNative {
    private var nativePtr: Long = 0

    fun nativeInit(modelPath: String, nCtx: Int): Boolean = false
    fun nativeChat(systemPrompt: String?, messages: Array<String>, topK: Int, topP: Float, temperature: Float, maxTokens: Int): String = ""
    fun nativeRelease() { /* no-op */ }
    fun nativeGetModelInfo(modelPath: String): String = """{"error":"not available on desktop"}"""
}
