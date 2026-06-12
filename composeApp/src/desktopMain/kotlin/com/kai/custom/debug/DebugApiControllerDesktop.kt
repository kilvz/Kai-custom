package com.kai.custom.debug

import com.kai.custom.DebugApiController
import com.kai.custom.SandboxController
import com.kai.custom.data.AppSettings
import com.kai.custom.data.DataRepository
import com.kai.custom.data.MemoryStore
import com.kai.custom.data.ToolExecutor
import com.kai.custom.mcp.McpServerManager

class DebugApiControllerDesktop(
    private val dataRepository: DataRepository,
    private val memoryStore: MemoryStore,
    private val appSettings: AppSettings,
    private val toolExecutor: ToolExecutor,
    private val mcpServerManager: McpServerManager,
    private val sandboxController: SandboxController,
) : DebugApiController {

    private var server: DebugServerDesktop? = null
    override var isRunning: Boolean = false
    override var isTransitioning: Boolean = false

    override fun start() {
        if (isRunning || isTransitioning) return
        isTransitioning = true
        try {
            server = DebugServerDesktop(
                dataRepository = dataRepository,
                memoryStore = memoryStore,
                appSettings = appSettings,
                toolExecutor = toolExecutor,
                mcpServerManager = mcpServerManager,
                sandboxController = sandboxController,
            )
            server!!.start()
            isRunning = true
        } finally {
            isTransitioning = false
        }
    }

    override fun stop() {
        isTransitioning = true
        try {
            server?.stop()
            server = null
            isRunning = false
        } finally {
            isTransitioning = false
        }
    }
}
