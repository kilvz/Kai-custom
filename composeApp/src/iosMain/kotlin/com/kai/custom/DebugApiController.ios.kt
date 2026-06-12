package com.kai.custom

actual fun createDebugApiController(): DebugApiController = NoOpDebugApiController()

class NoOpDebugApiController : DebugApiController {
    override fun start() {}
    override fun stop() {}
    override val isRunning: Boolean get() = false
    override val isTransitioning: Boolean get() = false
}
