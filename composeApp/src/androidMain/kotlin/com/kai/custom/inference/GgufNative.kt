package com.kai.custom.inference

class GgufNative {
    private var nativePtr: Long = 0

    external fun nativeInit(modelPath: String, nCtx: Int): Boolean
    external fun nativeChat(systemPrompt: String?, messages: Array<String>, topK: Int, topP: Float, temperature: Float, maxTokens: Int): String
    external fun nativeRelease()

    /** Reads only GGUF header metadata KV pairs (no weights loaded).
     * Returns a JSON string with keys like general.architecture, general.name,
     * general.file_type, general.size_label, <arch>.context_length, etc. */
    external fun nativeGetModelInfo(modelPath: String): String
}
