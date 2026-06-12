package com.kai.custom.wakeword

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.kai.custom.shared.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WakeWordService : Service() {

    companion object {
        private const val TAG = "WakeWordService"
        private const val CHANNEL_ID = "kai_wake_word_channel"
        private const val NOTIFICATION_ID = 9002
        private const val ANTI_FLAP_MS = 2000L
        private val _wakeWordDetected = MutableSharedFlow<String>(extraBufferCapacity = 4)
        val wakeWordDetected: SharedFlow<String> = _wakeWordDetected.asSharedFlow()

        @Volatile var isRunning = false
            private set

        @Volatile var lastStoppedMs: Long = 0L
            private set
    }

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var detectJob: Job? = null
    private var currentPhrase: String = "hey kai"
    private var currentMode: String = "GENERAL"
    private var currentTemplate: FloatArray? = null
    private var lastDetectionMs: Long = 0L
    private val detectionCooldownMs: Long = 3000L
    private var serviceStartMs: Long = 0L

    @Volatile private var audioRecord: AudioRecord? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate — stack trace follows")
        Log.d(TAG, Log.getStackTraceString(Exception("onCreate stack trace")))
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentPhrase = intent?.getStringExtra("WAKE_WORD_PHRASE") ?: "hey kai"
        currentMode = intent?.getStringExtra("WAKE_WORD_MODE") ?: "GENERAL"
        val templateStr = intent?.getStringExtra("WAKE_WORD_TEMPLATE") ?: ""
        currentTemplate = if (templateStr.isNotBlank()) {
            WakeWordMatcher.deserializeTemplate(templateStr)
        } else {
            null
        }
        Log.d(TAG, "onStartCommand phrase=$currentPhrase mode=$currentMode hasTemplate=${currentTemplate != null}")

        // Must call startForeground() before any early return — Android crashes with
        // RemoteServiceException if startForegroundService() was used but startForeground()
        // is never called (e.g. anti-flap, permission denied, missing template).
        val notification = buildNotification()
        try {
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "foreground notification posted")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: $e")
            stopSelf()
            return START_NOT_STICKY
        }

        // Anti-flap: if we were destroyed less than ANTI_FLAP_MS ago, reject immediately
        val sinceStop = System.currentTimeMillis() - lastStoppedMs
        if (sinceStop < ANTI_FLAP_MS) {
            Log.w(TAG, "anti-flap: stopped ${sinceStop}ms ago, rejecting restart")
            stopSelf()
            return START_NOT_STICKY
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO not granted")
            stopSelf()
            return START_NOT_STICKY
        }

        if (currentMode == "GENERAL") {
            WakeWordInterpreter.load(this)
            if (!WakeWordInterpreter.isLoaded) {
                Log.e(TAG, "model failed to load, falling back to personal mode")
                if (currentTemplate == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        } else if (currentTemplate == null) {
            Log.w(TAG, "personal mode but no template enrolled")
            stopSelf()
            return START_NOT_STICKY
        }

        // Recreate scope in case previous scope was cancelled in onDestroy
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        serviceStartMs = System.currentTimeMillis()
        isRunning = true
        detectJob = scope.launch {
            listenLoop()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        isRunning = false
        lastStoppedMs = System.currentTimeMillis()
        detectJob?.cancel()
        scope.cancel()
        // Release AudioRecord directly — scope.cancel() does NOT interrupt blocking read()
        val rec = audioRecord
        if (rec != null) {
            audioRecord = null
            try {
                rec.stop()
            } catch (_: Exception) {}
            rec.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private suspend fun listenLoop() {
        Log.d(TAG, "listenLoop starting")
        val sampleRate = 16000
        val bufferSize = sampleRate // 1 second
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val actualBufferSize = maxOf(bufferSize * 2, minBufferSize)
        Log.d(TAG, "bufferSize=$bufferSize minBufferSize=$minBufferSize actual=$actualBufferSize")

        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            actualBufferSize,
        )
        audioRecord = rec

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized, state=${rec.state}")
            audioRecord = null
            rec.release()
            return
        }
        Log.d(TAG, "AudioRecord initialized, starting recording")

        rec.startRecording()
        Log.d(TAG, "recording state=${rec.recordingState}")

        val buffer = ShortArray(bufferSize)
        val floatBuffer = FloatArray(bufferSize)
        val mfccProcessor = MfccProcessor()

        var framesProcessed = 0
        var readCount = 0
        var zeroCount = 0
        // Adaptive energy baseline — tracks ambient noise level (rain, fan, etc.)
        var energyBaseline = 2.0f
        val baselineDecay = 0.99f
        try {
            while (currentCoroutineContext().isActive) {
                val read = rec.read(buffer, 0, bufferSize)
                readCount++
                if (read > 0) {
                    for (i in 0 until read) {
                        floatBuffer[i] = buffer[i].toFloat() / 32768f
                    }

                    // Compute energy of this frame — skip silent/low frames
                    var energy = 0f
                    for (i in 0 until read) {
                        energy += floatBuffer[i] * floatBuffer[i]
                    }
                    // Decaying baseline of ambient energy; adapts to rain, fan, road noise
                    energyBaseline = baselineDecay * energyBaseline + (1f - baselineDecay) * energy
                    // Only process if energy significantly exceeds ambient (2x baseline or >3.0 raw)
                    val hasSignificantAudio = energy > maxOf(energyBaseline * 2f, 3.0f)

                    val features = mfccProcessor.compute(floatBuffer, read)
                    val score = detectWakeWord(features)
                    framesProcessed++
                    if (framesProcessed % 50 == 0) {
                        Log.d(TAG, "processed $framesProcessed frames, last score=$score mode=$currentMode energy=$energy hasSigAudio=$hasSignificantAudio reads=$readCount zeros=$zeroCount baseline=$energyBaseline")
                    }
                    val now = System.currentTimeMillis()
                    // Skip detection during startup grace period (3s) and silent/low frames
                    if (now - serviceStartMs < 3000 || !hasSignificantAudio) continue

                    val threshold = if (currentMode == "PERSONAL") 0.8f else WakeWordInterpreter.THRESHOLD
                    if (score > threshold) {
                        if (now - lastDetectionMs > detectionCooldownMs) {
                            lastDetectionMs = now
                            Log.i(TAG, "WAKE WORD DETECTED! score=$score mode=$currentMode threshold=$threshold energy=$energy")
                            _wakeWordDetected.tryEmit(currentPhrase)
                        } else {
                            Log.d(TAG, "suppressed (cooldown) score=$score")
                        }
                    }
                } else if (read == 0) {
                    zeroCount++
                    if (zeroCount == 1) Log.w(TAG, "read() returned 0 (no audio data)")
                } else {
                    Log.e(TAG, "AudioRecord read error: $read")
                }
                if (readCount == 1 && read <= 0) {
                    // first read gave no data, log and keep trying
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "listenLoop exception: $e")
        }
        Log.d(TAG, "listenLoop ending (processed=$framesProcessed reads=$readCount zeros=$zeroCount)")
        audioRecord = null
        try {
            rec.stop()
        } catch (_: Exception) { }
        rec.release()
    }

    private fun detectWakeWord(features: Array<FloatArray>): Float = if (currentMode == "PERSONAL" && currentTemplate != null) {
        WakeWordMatcher.cosineSimilarity(features, currentTemplate!!)
    } else {
        try {
            WakeWordInterpreter.run(features)
        } catch (_: Exception) {
            0f
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.wake_word_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.wake_word_channel_description)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.wake_word_notification_text))
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
