package com.kai.custom.wakeword

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.SharedFlow
import org.koin.java.KoinJavaComponent.inject

actual fun createWakeWordController(): WakeWordController = AndroidWakeWordController()

class AndroidWakeWordController : WakeWordController {

    private val context: Context by inject(Context::class.java)

    override val wakeWordDetected: SharedFlow<String>
        get() = WakeWordService.wakeWordDetected

    override val isListening: Boolean
        get() = WakeWordService.isRunning

    override fun startListening(phrase: String) {
        val intent = Intent(context, WakeWordService::class.java).apply {
            putExtra("WAKE_WORD_PHRASE", phrase)
        }
        context.startForegroundService(intent)
    }

    override fun stopListening() {
        val intent = Intent(context, WakeWordService::class.java)
        context.stopService(intent)
    }
}
