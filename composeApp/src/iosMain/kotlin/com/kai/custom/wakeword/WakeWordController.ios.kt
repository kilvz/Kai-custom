package com.kai.custom.wakeword

actual fun createWakeWordController(): WakeWordController = NoOpWakeWordController()

class NoOpWakeWordController : WakeWordController {
    override val wakeWordDetected: kotlinx.coroutines.flow.SharedFlow<String>
        get() = kotlinx.coroutines.flow.MutableSharedFlow<String>().asSharedFlow()
    override val isListening: Boolean = false
    override fun startListening(phrase: String, mode: WakeWordMode, template: String) {}
    override fun stopListening() {}
    override suspend fun enroll(phrase: String, onStatus: (String) -> Unit): String? = null

    private fun kotlinx.coroutines.flow.MutableSharedFlow<String>.asSharedFlow() = this as kotlinx.coroutines.flow.SharedFlow<String>
}
