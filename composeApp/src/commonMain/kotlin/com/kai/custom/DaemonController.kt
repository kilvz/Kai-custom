package com.kai.custom

interface DaemonController {
    fun start()
    fun stop()
    fun startFloatingBall()
    fun stopFloatingBall()
}

expect fun createDaemonController(): DaemonController
