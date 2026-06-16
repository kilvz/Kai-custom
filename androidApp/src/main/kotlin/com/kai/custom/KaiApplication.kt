package com.kai.custom

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.kai.custom.data.AppSettings
import com.kai.custom.data.TaskScheduler
import com.kai.custom.data.dimension.dimensionModule
import com.kai.custom.overlay.OverlayPermissionHelper
import com.kai.custom.sandbox.sandboxModule
import com.kai.custom.whatsapp.WhatsAppLifecycleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class KaiApplication : Application() {

    private val taskScheduler: TaskScheduler by inject()
    private val debugApiController: DebugApiController by inject()
    private val whatsAppLifecycleManager: WhatsAppLifecycleManager by inject()
    private val appSettings: AppSettings by inject()
    private val daemonController: DaemonController by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@KaiApplication)
            modules(appModule, sandboxModule, dimensionModule)
        }
        // Auto-start debug API server on debug builds
        if (isDebugBuild && !debugApiController.isRunning) {
            debugApiController.start()
        }
        // Track app foreground state so the scheduler only pushes a heartbeat notification
        // when the in-app banner isn't visible. ViewModel lifecycle is the wrong signal —
        // it survives backgrounding and only clears on Activity destruction.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                taskScheduler.appInForeground = true
                // Recover sandbox bridge after process death in background
                CoroutineScope(Dispatchers.Default).launch {
                    whatsAppLifecycleManager.setupAndStart()
                }
                // Retry floating ball if permission was just granted
                if (appSettings.isFloatingBallEnabled() &&
                    OverlayPermissionHelper.canDrawOverlays(this@KaiApplication)
                ) {
                    daemonController.startFloatingBall()
                }
            }
            override fun onStop(owner: LifecycleOwner) {
                taskScheduler.appInForeground = false
            }
        })
    }
}
