package com.kai.custom

import com.kai.custom.data.AppSettings
import com.kai.custom.data.DataRepository
import com.kai.custom.data.MemoryStore
import com.kai.custom.data.ToolExecutor
import com.kai.custom.debug.DebugServer
import com.kai.custom.mcp.McpServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

actual fun createDebugApiController(): DebugApiController = AndroidDebugApiController()

class AndroidDebugApiController : DebugApiController {

    private val dataRepository: DataRepository by inject(DataRepository::class.java)
    private val memoryStore: MemoryStore by inject(MemoryStore::class.java)
    private val appSettings: AppSettings by inject(AppSettings::class.java)
    private val toolExecutor: ToolExecutor by inject(ToolExecutor::class.java)
    private val mcpServerManager: McpServerManager by inject(McpServerManager::class.java)

    private var server: DebugServer? = null
    private var transitioning = false
    private val transitionLock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun start() {
        synchronized(transitionLock) {
            if (transitioning) return
            if (!appSettings.isDebugApiEnabled()) return
            server?.let { if (it.isRunning) return }
            transitioning = true
        }
        scope.launch {
            try {
                val sandboxController = try {
                    org.koin.java.KoinJavaComponent.inject<SandboxController>(SandboxController::class.java).value
                } catch (_: Exception) {
                    return@launch
                }
                val s = DebugServer(dataRepository, memoryStore, appSettings, toolExecutor, mcpServerManager, sandboxController)
                s.start()
                server = s
            } finally {
                synchronized(transitionLock) { transitioning = false }
            }
        }
    }

    override fun stop() {
        synchronized(transitionLock) {
            if (transitioning) return
            transitioning = true
        }
        scope.launch {
            try {
                server?.stop()
                server = null
            } finally {
                synchronized(transitionLock) { transitioning = false }
            }
        }
    }

    override val isRunning: Boolean get() = server?.isRunning ?: false
    override val isTransitioning: Boolean get() = synchronized(transitionLock) { transitioning }
}
