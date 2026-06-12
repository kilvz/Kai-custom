package com.kai.custom

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PttEvent { DOWN, UP }

object PttTriggerManager {
    private val _events = MutableSharedFlow<PttEvent>(extraBufferCapacity = 1, replay = 0)
    val events: SharedFlow<PttEvent> = _events.asSharedFlow()

    private val _captureMode = MutableStateFlow(false)
    val captureMode: StateFlow<Boolean> = _captureMode.asStateFlow()

    private val _capturedKeyCode = MutableSharedFlow<Int>(extraBufferCapacity = 1, replay = 0)
    val capturedKeyCode: SharedFlow<Int> = _capturedKeyCode.asSharedFlow()

    fun triggerDown() {
        _events.tryEmit(PttEvent.DOWN)
    }
    fun triggerUp() {
        _events.tryEmit(PttEvent.UP)
    }

    fun startCapture() {
        _captureMode.value = true
    }
    fun reportCapturedKey(keyCode: Int) {
        _capturedKeyCode.tryEmit(keyCode)
        _captureMode.value = false
    }
    fun cancelCapture() {
        _captureMode.value = false
    }
}
