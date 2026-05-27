package com.kai.custom.wakeword

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import kotlin.math.sqrt

actual fun createWakeWordController(): WakeWordController = AndroidWakeWordController()

class AndroidWakeWordController : WakeWordController {

    private val context: Context by inject(Context::class.java)
    private val tag = "WakeWordCtrl"

    override val wakeWordDetected: SharedFlow<String>
        get() = WakeWordService.wakeWordDetected

    override val isListening: Boolean
        get() = WakeWordService.isRunning

    override fun startListening(phrase: String, mode: WakeWordMode, template: String) {
        val intent = Intent(context, WakeWordService::class.java).apply {
            putExtra("WAKE_WORD_PHRASE", phrase)
            putExtra("WAKE_WORD_MODE", mode.name)
            putExtra("WAKE_WORD_TEMPLATE", template)
        }
        context.startForegroundService(intent)
    }

    override fun stopListening() {
        val intent = Intent(context, WakeWordService::class.java)
        context.stopService(intent)
    }

    override suspend fun enroll(phrase: String, onStatus: (String) -> Unit): String? = withContext(Dispatchers.IO) {
        Log.d(tag, "enrollment starting")
        val sampleRate = 16000
        val recordLen = (sampleRate * 1.8).toInt() // 1.8 seconds per sample
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val actualBuf = maxOf(recordLen * 2, minBuf)

        val mfccProcessor = MfccProcessor()
        val templates = mutableListOf<FloatArray>()
        val instructions = listOf(
            "Say \"$phrase\" now...",
            "Good! Say it again...",
            "Last time...",
        )

        var step = 0
        while (step < 3) {
            onStatus(instructions[step])
            Log.d(tag, "enroll step ${step + 1}: ${instructions[step]}")
            // Small delay so user can read the instruction
            kotlinx.coroutines.delay(800)

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, actualBuf,
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "AudioRecord not initialized at step $step")
                record.release()
                continue
            }
            record.startRecording()
            val buf = ShortArray(recordLen)
            val floatBuf = FloatArray(recordLen)
            val totalRead = record.read(buf, 0, recordLen)
            record.stop()
            record.release()

            if (totalRead <= 0) {
                Log.w(tag, "no audio for step $step")
                continue
            }
            var energy = 0f
            for (i in 0 until totalRead) {
                floatBuf[i] = buf[i].toFloat() / 32768f
                energy += floatBuf[i] * floatBuf[i]
            }
            if (energy < 0.01f) {
                Log.w(tag, "low energy at step $step: $energy — redoing")
                // Let user try again automatically
                onStatus("I didn't hear you. Say \"$phrase\"...")
                kotlinx.coroutines.delay(500)
                continue // stays on same step since step++ is skipped
            }
            // Crop to the loudest 1-second window to remove silence from template
            val windowLen = sampleRate
            val cropStart = if (totalRead > windowLen) {
                (0..totalRead - windowLen).maxByOrNull { offset ->
                    var e = 0f
                    for (i in offset until offset + windowLen) e += floatBuf[i] * floatBuf[i]
                    e
                } ?: 0
            } else 0
            val cropLen = minOf(windowLen, totalRead - cropStart)
            val cropped = if (cropStart > 0 || cropLen < totalRead) {
                floatBuf.copyOfRange(cropStart, cropStart + cropLen)
            } else floatBuf
            val features = mfccProcessor.compute(cropped, cropped.size)
            templates.add(WakeWordMatcher.serializeTemplate(features).let {
                WakeWordMatcher.deserializeTemplate(it)!!
            })
            Log.d(tag, "step $step recorded, energy=$energy cropOffset=$cropStart")
            step++
        }

        if (templates.size < 2) {
            Log.e(tag, "not enough good samples: ${templates.size}")
            onStatus("Not enough clear recordings. Try again.")
            return@withContext null
        }

        onStatus("Processing...")
        val averaged = WakeWordMatcher.averageTemplates(templates)
        val result = (0 until 49 * 13).joinToString(",") { i -> averaged.getOrElse(i) { 0f }.toString() }
        Log.d(tag, "enrollment complete, template size=${averaged.size}")
        onStatus("Enrolled! Voice is now personalized.")
        return@withContext result
    }
}
