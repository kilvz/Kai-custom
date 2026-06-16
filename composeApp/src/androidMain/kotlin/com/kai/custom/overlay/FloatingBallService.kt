package com.kai.custom.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.kai.custom.data.DataRepository
import com.kai.custom.shared.R
import org.koin.android.ext.android.inject

class FloatingBallService : Service() {

    companion object {
        private const val CHANNEL_ID = "kai_overlay_channel"
        private const val NOTIFICATION_ID = 9002
        private const val KEY_EVENT = "floating_ball_event"
        private const val EVENT_STOP = "stop"
    }

    private val dataRepository: DataRepository by inject()

    private var overlayLayout: FloatingBallLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var chatController: OverlayChatController? = null

    override fun onCreate() {
        super.onCreate()
        chatController = OverlayChatController(dataRepository)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("Kai_Ball", "onStartCommand flags=$flags startId=$startId")
        if (intent?.getStringExtra(KEY_EVENT) == EVENT_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()

        if (!OverlayPermissionHelper.canDrawOverlays(this)) {
            Log.d("Kai_Ball", "overlay permission not granted")
            startForeground(NOTIFICATION_ID, buildPermissionNotification())
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("Kai_Ball", "startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }

        if (overlayLayout == null) {
            showOverlay()
        }

        return START_STICKY
    }

    private fun showOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val controller = chatController ?: run {
            Log.d("Kai_Ball", "showOverlay: no controller")
            return
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        val layout = FloatingBallLayout(this).apply {
            init(controller, params)
        }

        try {
            wm.addView(layout, params)
            overlayLayout = layout
            overlayParams = params
            Log.d("Kai_Ball", "overlay added")
        } catch (e: SecurityException) {
            Log.e("Kai_Ball", "addView SecurityException", e)
            stopSelf()
        }
    }

    private fun hideOverlay() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayLayout?.let { layout ->
            try {
                windowManager.removeView(layout)
            } catch (_: Exception) {
            }
        }
        overlayLayout = null
        overlayParams = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Floating Assistant",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Notification for the floating assistant overlay"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildPermissionNotification(): Notification {
        val settingsIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val settingsPendingIntent = PendingIntent.getActivity(
            this,
            2,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val launchPendingIntent = PendingIntent.getActivity(
            this,
            1,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.floating_overlay_title))
            .setContentText(getString(R.string.floating_overlay_text))
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(settingsPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, FloatingBallService::class.java).apply {
            putExtra(KEY_EVENT, EVENT_STOP)
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val launchPendingIntent = PendingIntent.getActivity(
            this,
            1,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.floating_assistant_title))
            .setContentText(getString(R.string.floating_open_text))
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(launchPendingIntent)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "Stop",
                    stopPendingIntent,
                ).build(),
            )
            .setOngoing(true)
            .build()
    }
}
