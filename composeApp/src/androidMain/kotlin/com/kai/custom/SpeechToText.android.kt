package com.kai.custom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import org.koin.java.KoinJavaComponent.inject

actual fun createSpeechToText(): SpeechToText = AndroidSpeechToText()

class AndroidSpeechToText : SpeechToText {
    private val context: Context by inject(Context::class.java)
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false

    override val isAvailable: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    override fun startListening(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit,
        language: String,
    ) {
        if (isListening) return
        if (!isAvailable) {
            onError("Microphone permission not granted. Grant it in Settings > Apps > Kai > Permissions.")
            return
        }
        recognizer?.destroy()
        recognizer = null
        isListening = true
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            private fun cleanup() {
                isListening = false
                recognizer?.destroy()
                recognizer = null
            }

            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                cleanup()
                if (text != null) {
                    onFinalResult(text)
                } else {
                    onError("No speech recognized")
                }
            }

            override fun onPartialResults(partialResults: Bundle) {
                val matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (text != null) onPartialResult(text)
            }

            override fun onError(error: Int) {
                cleanup()
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timed out"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission not granted"
                    SpeechRecognizer.ERROR_CLIENT -> "Recognition client error"
                    SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
                    else -> "Speech recognition error ($error)"
                }
                onError(message)
            }

            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onRmsChanged(rmsdB: Float) {}
        })

        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer?.startListening(intent)
    }

    override fun stopListening() {
        isListening = false
        recognizer?.stopListening()
    }

    override fun cancel() {
        isListening = false
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }
}
