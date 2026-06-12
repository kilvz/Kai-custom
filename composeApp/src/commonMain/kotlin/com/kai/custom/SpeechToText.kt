package com.kai.custom

interface SpeechToText {
    fun startListening(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit,
        language: String = "en",
    )
    fun stopListening()
    fun cancel()
    val isAvailable: Boolean
}

expect fun createSpeechToText(): SpeechToText
