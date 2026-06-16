package com.kai.custom.wakeword

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

actual fun createWakeWordController(): WakeWordController = PorcupineWakeWordController()

class PorcupineWakeWordController : WakeWordController {
    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }
    private val _wakeWordDetected = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val wakeWordDetected: SharedFlow<String> = _wakeWordDetected.asSharedFlow()
    override val isListening: Boolean get() = process != null

    private var process: Process? = null
    private var listenerThread: Thread? = null

    override fun startListening(phrase: String, mode: WakeWordMode, template: String) {
        stopListening()
        val keyword = if (phrase.isBlank()) "hey kai" else phrase.lowercase().replace(" ", "_")
        try {
            val script = """
import sys, json, struct, pyaudio
try:
    import pvporcupine
except ImportError:
    print(json.dumps({"error": "pvporcupine not installed. pip install pvporcupine"}))
    sys.exit(1)

try:
    porcupine = pvporcupine.create(keyword_paths=["$keyword.ppn"] if "$keyword.ppn" else None, keywords=["$keyword"])
except Exception as e:
    porcupine = pvporcupine.create(keywords=["$keyword"])

audio = pyaudio.PyAudio()
stream = audio.open(
    rate=porcupine.sample_rate,
    channels=1,
    format=pyaudio.paInt16,
    input=True,
    frames_per_buffer=porcupine.frame_length
)
print(json.dumps({"status": "listening", "keyword": "$keyword"}))
sys.stdout.flush()

while True:
    pcm = stream.read(porcupine.frame_length, exception_on_overflow=False)
    pcm_unpacked = struct.unpack_from("h" * porcupine.frame_length, pcm)
    result = porcupine.process(pcm_unpacked)
    if result >= 0:
        print(json.dumps({"detected": "$keyword"}))
        sys.stdout.flush()
            """.trimIndent()

            val pb = ProcessBuilder("python", "-c", script)
                .redirectErrorStream(true)
            process = pb.start()

            listenerThread = Thread {
                try {
                    val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                    var line: String? = ""
                    while (process?.isAlive == true && reader.readLine().also { line = it } != null) {
                        val msg = try {
                            json
                                .decodeFromString<Map<String, String>>(line!!)
                        } catch (_: Exception) {
                            null
                        }
                        if (msg?.containsKey("detected") == true) {
                            _wakeWordDetected.tryEmit(msg["detected"] ?: phrase)
                        }
                    }
                } catch (_: Exception) {
                }
            }.also {
                it.isDaemon = true
                it.start()
            }
        } catch (e: Exception) {
            println("[WakeWord] Failed to start: ${e.message}")
        }
    }

    override fun stopListening() {
        process?.destroyForcibly()
        process = null
        listenerThread?.interrupt()
        listenerThread = null
    }

    override suspend fun enroll(phrase: String, onStatus: (String) -> Unit): String? = withContext(Dispatchers.IO) {
        onStatus("Enrollment not supported in Python mode — use Porcupine CLI: pvporcupine_recorder --keyword $phrase")
        null
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> MutableSharedFlow<T>.asSharedFlow(): SharedFlow<T> = this as SharedFlow<T>
}
