package com.kai.custom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kai.custom.data.AppSettings
import org.koin.java.KoinJavaComponent.get

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val appSettings = get<AppSettings>(AppSettings::class.java)
            if (appSettings.isDaemonEnabled()) {
                try {
                    val serviceIntent = Intent(context, DaemonService::class.java)
                    context.startForegroundService(serviceIntent)
                } catch (_: Exception) {}
            }
        }
    }
}
