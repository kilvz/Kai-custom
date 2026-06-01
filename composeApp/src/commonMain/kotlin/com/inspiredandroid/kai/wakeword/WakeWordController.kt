package com.inspiredandroid.kai.wakeword

import kotlinx.coroutines.flow.SharedFlow

interface WakeWordController {
    val wakeWordDetected: SharedFlow<String>
    val isListening: Boolean
    fun startListening(phrase: String, mode: WakeWordMode = WakeWordMode.GENERAL, template: String = "")
    fun stopListening()
    suspend fun enroll(phrase: String = "hey kai", onStatus: (String) -> Unit = {}): String?
}

expect fun createWakeWordController(): WakeWordController
