package com.kai.custom

import com.kai.custom.data.AppSettings
import com.kai.custom.data.DataRepository
import com.kai.custom.data.MemoryStore
import com.kai.custom.data.ToolExecutor
import com.kai.custom.debug.DebugServer
import org.koin.java.KoinJavaComponent.inject

actual fun createDebugApiController(): DebugApiController = AndroidDebugApiController()

class AndroidDebugApiController : DebugApiController {

    private val dataRepository: DataRepository by inject(DataRepository::class.java)
    private val memoryStore: MemoryStore by inject(MemoryStore::class.java)
    private val appSettings: AppSettings by inject(AppSettings::class.java)
    private val toolExecutor: ToolExecutor by inject(ToolExecutor::class.java)

    private var server: DebugServer? = null

    override fun start() {
        if (!appSettings.isDebugApiEnabled()) return
        if (server?.isRunning == true) return
        val sandboxController = try {
            org.koin.java.KoinJavaComponent.inject<SandboxController>(SandboxController::class.java).value
        } catch (_: Exception) { null }
        val s = DebugServer(dataRepository, memoryStore, appSettings, toolExecutor, sandboxController)
        s.start()
        server = s
    }

    override fun stop() {
        server?.stop()
        server = null
    }

    override val isRunning: Boolean get() = server?.isRunning ?: false
}
