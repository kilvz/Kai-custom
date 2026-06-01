package com.kai.custom

actual fun createSpeechToText(): SpeechToText = NoOpSpeechToText()

class NoOpSpeechToText : SpeechToText {
    override val isAvailable: Boolean = false
    override fun startListening(onPartialResult: (String) -> Unit, onFinalResult: (String) -> Unit, onError: (String) -> Unit, language: String) {
        onError("Speech-to-text is not available on this platform")
    }
    override fun stopListening() {}
    override fun cancel() {}
}
