package com.kai.custom.wakeword

import kotlinx.coroutines.flow.SharedFlow

interface WakeWordController {
    val wakeWordDetected: SharedFlow<String>
    val isListening: Boolean
    fun startListening(phrase: String)
    fun stopListening()
}

expect fun createWakeWordController(): WakeWordController
