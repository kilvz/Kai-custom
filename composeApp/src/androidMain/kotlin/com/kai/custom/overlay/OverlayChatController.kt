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
import kotlinx.coroutines.withContext
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

    val personaName: String get() = dataRepository.getPersonaName().ifBlank { "Kai" }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    // Preserve conversation context across exchanges
    private val conversationContext = mutableListOf<Pair<String, String>>()

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
                // Capture current screen content
                val screenText = withContext(Dispatchers.IO) {
                    ScreenReaderService.readScreenTextWithFallback()
                }

                // Build context-rich prompt: screen + history + question
                val prompt = buildString {
                    if (!screenText.isNullOrBlank()) {
                        appendLine("## Current Screen Content")
                        appendLine(screenText)
                        appendLine()
                    }
                    if (conversationContext.isNotEmpty()) {
                        appendLine("## Conversation History")
                        for ((user, assistant) in conversationContext) {
                            appendLine("User: $user")
                            appendLine("Assistant: $assistant")
                        }
                        appendLine()
                    }
                    appendLine("## Current Question")
                    appendLine(text)
                }

                // Use user-configured service first, falling back to default
                val instances = dataRepository.getConfiguredServiceInstances()
                var lastError: String? = null

                val response = if (instances.isNotEmpty()) {
                    var result: String? = null
                    for (instance in instances) {
                        try {
                            result = dataRepository.askWithTools(prompt, instance.instanceId)
                            if (result.isNotEmpty()) break
                        } catch (e: Exception) {
                            lastError = e.message ?: e.javaClass.simpleName
                            android.util.Log.w("Kai_Overlay", "Instance ${instance.instanceId} failed: $lastError")
                        }
                    }
                    result
                } else {
                    null
                }

                val finalResponse = response ?: run {
                    try {
                        dataRepository.askWithTools(prompt)
                    } catch (e: Exception) {
                        lastError = e.message ?: e.javaClass.simpleName
                        throw e
                    }
                }

                // Store in conversation context for future exchanges
                conversationContext.add(text to finalResponse)
                if (conversationContext.size > 10) {
                    conversationContext.removeAt(0)
                }

                val assistantMsg = ChatMessage(
                    id = Uuid.random().toString(),
                    role = "assistant",
                    content = finalResponse,
                )
                _messages.update { it + assistantMsg }
            } catch (e: Exception) {
                android.util.Log.e("Kai_Overlay", "askWithTools failed", e)
                val detail = e.message ?: e.javaClass.simpleName
                // Save to conversation context so "try again" retains history
                conversationContext.add(text to "[API ERROR] $detail")
                if (conversationContext.size > 10) {
                    conversationContext.removeAt(0)
                }
                val errorMsg = ChatMessage(
                    id = Uuid.random().toString(),
                    role = "assistant",
                    content = "Sorry, I couldn't process that: $detail",
                )
                _messages.update { it + errorMsg }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
        conversationContext.clear()
    }
}
