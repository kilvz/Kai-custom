package com.kai.custom

actual fun createDaemonController(): DaemonController = NoOpDaemonController()

class NoOpDaemonController : DaemonController {
    override fun start() { /* No-op on web */ }
    override fun stop() { /* No-op on web */ }
    override fun startFloatingBall() = true
    override fun stopFloatingBall() {}
}
