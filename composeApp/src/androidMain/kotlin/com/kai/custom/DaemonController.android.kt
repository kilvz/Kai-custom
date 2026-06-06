package com.kai.custom

import android.content.Context
import android.content.Intent
import com.kai.custom.data.AppSettings
import com.kai.custom.overlay.FloatingBallService
import com.kai.custom.overlay.OverlayPermissionHelper
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
        if (!OverlayPermissionHelper.canDrawOverlays(context)) {
            OverlayPermissionHelper.openOverlaySettings(context)
            return
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
