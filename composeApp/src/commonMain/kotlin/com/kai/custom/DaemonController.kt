package com.kai.custom

interface DaemonController {
    fun start()
    fun stop()
    fun startFloatingBall(): Boolean
    fun stopFloatingBall()
}

/** Background daemon mode is Android-only; every other target gets this. */
class NoOpDaemonController : DaemonController {
    override fun start() {}
    override fun stop() {}
    override fun startFloatingBall() = true
    override fun stopFloatingBall() {}
}

expect fun createDaemonController(): DaemonController
