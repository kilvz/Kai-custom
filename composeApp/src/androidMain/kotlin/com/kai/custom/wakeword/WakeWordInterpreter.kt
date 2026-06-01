package com.kai.custom.wakeword

import android.content.Context
import android.util.Log

object WakeWordInterpreter {
    private const val TAG = "WakeWordInterpreter"
    const val THRESHOLD = 0.3f
    var isLoaded = false
        private set

    private var interpreter: org.tensorflow.lite.Interpreter? = null

    fun load(context: Context) {
        if (isLoaded) return
        try {
            val startMs = System.currentTimeMillis()
            val stream = context.assets.open("hey_kai.tflite")
            Log.d(TAG, "model file opened from assets")
            val bytes = stream.readBytes()
            Log.d(TAG, "model size: ${bytes.size} bytes")
            stream.close()
            val buffer = java.nio.ByteBuffer.allocateDirect(bytes.size)
            buffer.order(java.nio.ByteOrder.nativeOrder())
            buffer.put(bytes)
            buffer.rewind()
            interpreter = org.tensorflow.lite.Interpreter(buffer)
            isLoaded = true
            val elapsed = System.currentTimeMillis() - startMs
            Log.d(TAG, "model loaded in ${elapsed}ms")
        } catch (e: Exception) {
            Log.e(TAG, "model load failed: $e")
            isLoaded = false
            interpreter = null
        }
    }

    fun run(features: Array<FloatArray>): Float {
        val interpreter = interpreter ?: return 0f
        val input = Array(1) {
            Array(49) { f ->
                FloatArray(13) { c -> features[f][c] }
            }
        }
        val output = Array(1) { FloatArray(2) }
        interpreter.run(input, output)
        val kaiProb = output[0][1]
        if (kaiProb > 0.1f) {
            Log.d(TAG, "run() -> kai=$kaiProb, other=${output[0][0]}")
        }
        return kaiProb
    }

    fun unload() {
        interpreter?.close()
        interpreter = null
        isLoaded = false
    }
}
