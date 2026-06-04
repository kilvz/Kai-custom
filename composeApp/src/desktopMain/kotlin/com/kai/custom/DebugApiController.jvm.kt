package com.kai.custom

import com.kai.custom.data.AppSettings
import com.kai.custom.data.DataRepository
import com.kai.custom.data.MemoryStore
import com.kai.custom.data.ToolExecutor
import com.kai.custom.debug.DebugApiControllerDesktop
import org.koin.java.KoinJavaComponent.inject

actual fun createDebugApiController(): DebugApiController {
    val dataRepository: DataRepository by inject(DataRepository::class.java)
    val memoryStore: MemoryStore by inject(MemoryStore::class.java)
    val appSettings: AppSettings by inject(AppSettings::class.java)
    val toolExecutor: ToolExecutor by inject(ToolExecutor::class.java)
    val sandboxController: SandboxController by inject(SandboxController::class.java)
    return DebugApiControllerDesktop(
        dataRepository = dataRepository,
        memoryStore = memoryStore,
        appSettings = appSettings,
        toolExecutor = toolExecutor,
        sandboxController = sandboxController,
    )
}

class NoOpDebugApiController : DebugApiController {
    override fun start() {}
    override fun stop() {}
    override val isRunning: Boolean get() = false
    override val isTransitioning: Boolean get() = false
}
