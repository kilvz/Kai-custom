package com.kai.custom.overlay

import android.content.Context
import android.content.Intent
import com.kai.custom.data.AppSettings

class FloatingBallController(
    private val context: Context,
    private val appSettings: AppSettings,
) {
    fun shouldAutoStart(): Boolean = appSettings.isFloatingBallEnabled()

    fun start() {
        if (!appSettings.isFloatingBallEnabled()) return
        if (!OverlayPermissionHelper.canDrawOverlays(context)) return
        val intent = Intent(context, FloatingBallService::class.java)
        context.startForegroundService(intent)
    }

    fun stop() {
        val intent = Intent(context, FloatingBallService::class.java)
        context.stopService(intent)
    }

    fun restart() {
        stop()
        start()
    }
}
