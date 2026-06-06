package com.kai.custom.overlay

import com.kai.custom.ScreenReaderService
import com.kai.custom.data.DataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
)

class OverlayChatController(
    private val dataRepository: DataRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    fun show() {
        _isVisible.value = true
    }

    fun hide() {
        _isVisible.value = false
    }

    @OptIn(ExperimentalUuidApi::class)
    fun sendMessage(text: String) {
        val userMsg = ChatMessage(
            id = Uuid.random().toString(),
            role = "user",
            content = text,
        )
        _messages.update { it + userMsg }
        _isLoading.value = true

        scope.launch {
            try {
                val screenText = ScreenReaderService.readScreenText()
                val prompt = buildString {
                    if (!screenText.isNullOrBlank()) {
                        appendLine("Current screen content:")
                        appendLine(screenText)
                        appendLine()
                    }
                    appendLine("User asks: $text")
                }
                val response = dataRepository.askSilently(prompt)
                val assistantMsg = ChatMessage(
                    id = Uuid.random().toString(),
                    role = "assistant",
                    content = response,
                )
                _messages.update { it + assistantMsg }
            } catch (e: Exception) {
                val errorMsg = ChatMessage(
                    id = Uuid.random().toString(),
                    role = "assistant",
                    content = "Sorry, I couldn't process that: ${e.message}",
                )
                _messages.update { it + errorMsg }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }
}
