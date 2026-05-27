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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class WakeWordService : Service() {

    companion object {
        private const val TAG = "WakeWordService"
        private const val CHANNEL_ID = "kai_wake_word_channel"
        private const val NOTIFICATION_ID = 9002
        private val _wakeWordDetected = MutableSharedFlow<String>(extraBufferCapacity = 4)
        val wakeWordDetected: SharedFlow<String> = _wakeWordDetected.asSharedFlow()
        @Volatile var isRunning = false
            private set
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var detectJob: Job? = null
    private var currentPhrase: String = "hey kai"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentPhrase = intent?.getStringExtra("WAKE_WORD_PHRASE") ?: "hey kai"
        Log.d(TAG, "onStartCommand phrase=$currentPhrase")

        val notification = buildNotification()
        try {
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "foreground notification posted")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: $e")
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

        WakeWordInterpreter.load(this)
        if (!WakeWordInterpreter.isLoaded) {
            Log.e(TAG, "model failed to load")
            stopSelf()
            return START_NOT_STICKY
        }
        Log.d(TAG, "model loaded successfully")

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
        detectJob?.cancel()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private suspend fun listenLoop() {
        Log.d(TAG, "listenLoop starting")
        val sampleRate = 16000
        val bufferSize = sampleRate // 1 second
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val actualBufferSize = maxOf(bufferSize * 2, minBufferSize)
        Log.d(TAG, "bufferSize=$bufferSize minBufferSize=$minBufferSize actual=$actualBufferSize")

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            actualBufferSize,
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized, state=${audioRecord.state}")
            return
        }
        Log.d(TAG, "AudioRecord initialized, starting recording")

        audioRecord.startRecording()
        val buffer = ShortArray(bufferSize)
        val floatBuffer = FloatArray(bufferSize)
        val mfccProcessor = MfccProcessor()

        var framesProcessed = 0
        while (currentCoroutineContext().isActive) {
            val read = audioRecord.read(buffer, 0, bufferSize)
            if (read > 0) {
                for (i in 0 until read) {
                    floatBuffer[i] = buffer[i].toFloat() / 32768f
                }
                val features = mfccProcessor.compute(floatBuffer, read)
                val score = detectWakeWord(features)
                framesProcessed++
                if (framesProcessed % 50 == 0) {
                    Log.d(TAG, "processed $framesProcessed frames, last score=$score")
                }
                if (score > WakeWordInterpreter.THRESHOLD) {
                    Log.i(TAG, "WAKE WORD DETECTED! score=$score")
                    _wakeWordDetected.tryEmit(currentPhrase)
                }
            } else if (read < 0) {
                Log.e(TAG, "AudioRecord read error: $read")
            }
        }

        Log.d(TAG, "listenLoop ending")
        audioRecord.stop()
        audioRecord.release()
    }

    private fun detectWakeWord(features: Array<FloatArray>): Float {
        return try {
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
            this, 0, intent,
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
