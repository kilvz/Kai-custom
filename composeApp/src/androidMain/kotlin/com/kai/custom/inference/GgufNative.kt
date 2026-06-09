package com.kai.custom.inference

class GgufNative {
    private var nativePtr: Long = 0

    external fun nativeInit(modelPath: String, nCtx: Int): Boolean
    external fun nativeChat(systemPrompt: String?, messages: Array<String>, topK: Int, topP: Float, temperature: Float, maxTokens: Int): String
    external fun nativeRelease()
}
