package com.kai.custom.wakeword

actual fun createWakeWordController(): WakeWordController = NoOpWakeWordController()

class NoOpWakeWordController : WakeWordController {
    override val wakeWordDetected: kotlinx.coroutines.flow.SharedFlow<String>
        get() = kotlinx.coroutines.flow.MutableSharedFlow<String>().asSharedFlow()
    override val isListening: Boolean = false
    override fun startListening(phrase: String) {}
    override fun stopListening() {}

    private fun kotlinx.coroutines.flow.MutableSharedFlow<String>.asSharedFlow() =
        this as kotlinx.coroutines.flow.SharedFlow<String>
}
