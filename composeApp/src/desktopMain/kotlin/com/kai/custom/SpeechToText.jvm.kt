package com.kai.custom

import java.io.BufferedReader
import java.io.InputStreamReader

actual fun createSpeechToText(): SpeechToText = ProcessSpeechToText()

class ProcessSpeechToText : SpeechToText {
    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }
    override val isAvailable: Boolean = checkWhisperAvailable()
    private var process: Process? = null

    override fun startListening(onPartialResult: (String) -> Unit, onFinalResult: (String) -> Unit, onError: (String) -> Unit, language: String) {
        if (!isAvailable) {
            onError("Speech-to-text requires Python + openai-whisper. Install: pip install openai-whisper")
            return
        }
        try {
            val script = """
import sys, json, tempfile, wave, pyaudio
try:
    import whisper
except ImportError:
    print(json.dumps({"error": "whisper not installed"}))
    sys.exit(1)

model = whisper.load_model("base")
chunk = 1024
format = pyaudio.paInt16
channels = 1
rate = 16000

audio = pyaudio.PyAudio()
stream = audio.open(format=format, channels=channels, rate=rate,
                    input=True, frames_per_buffer=chunk)

frames = []
silent_chunks = 0
silent_limit = int(rate / chunk * 2.0)  # 2 seconds of silence

while True:
    data = stream.read(chunk, exception_on_overflow=False)
    frames.append(data)
    if max(data) < 10:
        silent_chunks += 1
    else:
        silent_chunks = 0
    if silent_chunks > silent_limit:
        break

stream.stop_stream()
stream.close()
audio.terminate()

with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
    wf = wave.open(f, "wb")
    wf.setnchannels(channels)
    wf.setsampwidth(audio.get_sample_size(format))
    wf.setframerate(rate)
    wf.writeframes(b"".join(frames))
    wf.close()
    result = model.transcribe(f.name, language="$language")
    print(json.dumps({"text": result["text"].strip()}))

import os
os.unlink(f.name)
            """.trimIndent()

            val pb = ProcessBuilder("python", "-c", script)
                .redirectErrorStream(true)
            process = pb.start()
            val reader = BufferedReader(InputStreamReader(process!!.inputStream))
            val output = reader.readText().trim()
            process?.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)

            if (output.isNotBlank()) {
                val result = json
                    .decodeFromString<Map<String, String>>(output)
                val text = result["text"]
                if (!text.isNullOrBlank()) {
                    onFinalResult(text)
                } else {
                    onError(result["error"] ?: "No speech detected")
                }
            } else {
                onError("No output from speech recognition")
            }
        } catch (e: Exception) {
            onError("Speech recognition error: ${e.message}")
        } finally {
            process?.destroyForcibly()
            process = null
        }
    }

    override fun stopListening() {
        process?.destroyForcibly()
        process = null
    }

    override fun cancel() {
        process?.destroyForcibly()
        process = null
    }

    private fun checkWhisperAvailable(): Boolean = try {
        val proc = ProcessBuilder("python", "-c", "import whisper; print('ok')")
            .redirectErrorStream(true)
            .start()
        val ok = proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
        ok && proc.exitValue() == 0
    } catch (_: Exception) {
        false
    }
}
