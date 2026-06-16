package com.kai.custom.testutil

import com.kai.custom.SpeechToText
import com.kai.custom.wakeword.WakeWordController
import com.kai.custom.wakeword.WakeWordMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class TestSpeechToText : SpeechToText {
    override fun startListening(onPartialResult: (String) -> Unit, onFinalResult: (String) -> Unit, onError: (String) -> Unit, language: String) {}
    override fun stopListening() {}
    override fun cancel() {}
    override val isAvailable: Boolean get() = false
}

class TestWakeWordController : WakeWordController {
    override val wakeWordDetected: SharedFlow<String> = MutableSharedFlow()
    override val isListening: Boolean = false
    override fun startListening(phrase: String, mode: WakeWordMode, template: String) {}
    override fun stopListening() {}
    override suspend fun enroll(phrase: String, onStatus: (String) -> Unit): String? = null
}
