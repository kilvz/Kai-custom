package com.kai.custom

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.kai.custom.ScreenReaderService
import com.kai.custom.data.AppSettings
import com.kai.custom.overlay.FloatingBallService
import com.kai.custom.overlay.OverlayPermissionHelper
import com.kai.custom.shizuku.ShizukuManager
import org.koin.java.KoinJavaComponent.inject

actual fun createDaemonController(): DaemonController = AndroidDaemonController()

class AndroidDaemonController : DaemonController {

    private val context: Context by inject(Context::class.java)
    private val appSettings: AppSettings by inject(AppSettings::class.java)

    fun shouldAutoStart(): Boolean = appSettings.isDaemonEnabled()

    override fun start() {
        try {
            val intent = Intent(context, DaemonService::class.java)
            context.startForegroundService(intent)
        } catch (_: Exception) {
        }
        if (appSettings.isFloatingBallEnabled() && OverlayPermissionHelper.canDrawOverlays(context)) {
            try {
                val fbIntent = Intent(context, FloatingBallService::class.java)
                context.startForegroundService(fbIntent)
            } catch (_: Exception) {
            }
        }
    }

    override fun stop() {
        val fbIntent = Intent(context, FloatingBallService::class.java)
        context.stopService(fbIntent)
        val intent = Intent(context, DaemonService::class.java)
        context.stopService(intent)
    }

    override fun startFloatingBall() {
        // 1. Accessibility service — required for screen reading + gestures
        if (!ScreenReaderService.isConnected()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.w("Kai_Ball", "Accessibility service not enabled — opened settings")
        }

        // 2. Overlay permission — required for the ball window
        if (!OverlayPermissionHelper.canDrawOverlays(context)) {
            OverlayPermissionHelper.openOverlaySettings(context)
            Log.w("Kai_Ball", "Overlay permission not granted — opened settings")
            return
        }

        // 3. Shizuku recommendation (non-blocking)
        if (!ShizukuManager.isAvailable || !ShizukuManager.hasPermission) {
            Log.w("Kai_Ball", "Shizuku not available — uiautomator dump fallback will be used for screen reading")
        }

        try {
            val intent = Intent(context, FloatingBallService::class.java)
            context.startForegroundService(intent)
        } catch (_: Exception) {
        }
    }

    override fun stopFloatingBall() {
        val intent = Intent(context, FloatingBallService::class.java)
        context.stopService(intent)
    }
}
