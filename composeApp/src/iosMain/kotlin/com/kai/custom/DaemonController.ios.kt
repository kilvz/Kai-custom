package com.kai.custom

actual fun createDaemonController(): DaemonController = NoOpDaemonController()

class NoOpDaemonController : DaemonController {
    override fun start() { /* No-op on iOS */ }
    override fun stop() { /* No-op on iOS */ }
}
